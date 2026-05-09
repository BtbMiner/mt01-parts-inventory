package com.kemtdm.mt01.data

import android.util.Log
import com.kemtdm.mt01.sql.GetConnection
import com.kemtdm.mt01.utils.execInsert
import com.kemtdm.mt01.utils.execQuery
import com.kemtdm.mt01.utils.execUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException

object TxnRepository {

    private const val TAG = "TxnRepository"

    // -------------------------------------------------------
    // saveTxn
    // บันทึก Transaction + อัปเดต MT_STOCK + log AUDIT
    // คืนค่า true = สำเร็จ, false = ล้มเหลว
    // -------------------------------------------------------
    suspend fun saveTxn(input: TxnInput): Double? = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext null
            connection.autoCommit = false

            // ✅ 1. ดึง stock ปัจจุบัน (ล็อก row กัน race condition)
            val (currentQty, currentLoc) = getCurrentStock(connection, input.partId)
                ?: run {
                    connection.rollback()
                    return@withContext null
                }

            // ✅ 2. คำนวณ QTY ใหม่
            val newQty = when (input.txnType) {
                "IN", "RET" -> currentQty + input.qty
                "OUT"       -> currentQty - input.qty
                else -> {
                    connection.rollback()
                    return@withContext null
                }
            }

            if (newQty < 0) {
                Log.e(TAG, "Stock not enough: current=$currentQty request=${input.qty}")
                connection.rollback()
                return@withContext null
            }

            // ✅ 3. เตรียม LOC_FROM / LOC_TO ให้ตรง constraint
            val finalLocFrom: String?
            val finalLocTo: String?

            when (input.txnType) {
                "OUT" -> {
                    finalLocFrom = input.locId
                    finalLocTo   = null
                }
                "IN", "RET" -> {
                    finalLocFrom = null
                    finalLocTo   = input.locId
                }
                else -> {
                    connection.rollback()
                    return@withContext null
                }
            }

            // ✅ 4. INSERT MT_TXN
            val txnId = insertTxn(
                connection = connection,
                input = input,
                locFrom = finalLocFrom,
                locTo = finalLocTo
            ) ?: run {
                connection.rollback()
                return@withContext null
            }

            // ✅ 5. UPDATE MT_STOCK
            updateStock(connection, input.partId, newQty)

            // ✅ 6. AUDIT LOG (แบบ JSON snapshot)
            val oldJson = """{"PART_ID":"${input.partId}","LOC_ID":"$currentLoc","QTY_ON_HAND":$currentQty}"""
            val newJson = """{"PART_ID":"${input.partId}","LOC_ID":"${input.locId}","QTY_ON_HAND":$newQty}"""

            insertAuditLog(
                connection = connection,
                txnId = txnId,
                action = "CREATE",
                changedBy = input.createdBy,
                oldValue = oldJson,
                newValue = newJson,
                deviceInfo = input.deviceInfo
            )

            connection.commit()
            Log.d(TAG, "saveTxn SUCCESS: txnId=$txnId part=${input.partId}")
            newQty

        } catch (e: SQLException) {

            // ✅ ✅ ✅ DEBUG สำคัญมาก
            Log.e(TAG, "=== SQL ERROR ===")
            Log.e(TAG, "Message: ${e.message}")
            Log.e(TAG, "SQLState: ${e.sqlState}")
            Log.e(TAG, "ErrorCode: ${e.errorCode}")

            var next: SQLException? = e.nextException
            while (next != null) {
                Log.e(TAG, "NextException: ${next.message}")
                next = next.nextException
            }

            Log.w(TAG, "Transaction ROLLBACK (saveTxn)")
            connection?.rollback()
            null

        } catch (e: Exception) {

            Log.e(TAG, "=== GENERAL ERROR === ${e.message}", e)
            Log.w(TAG, "Transaction ROLLBACK (saveTxn)")
            connection?.rollback()
            null

        } finally {
            connection?.autoCommit = true
            connection?.close()
        }
    }

    suspend fun getTodayTxn(
        partId: String? = null,
        createdBy: String? = null
    ): List<TxnRecord> = withContext(Dispatchers.IO) {

        var connection: Connection? = null

        try {
            connection = GetConnection.connection ?: return@withContext emptyList()

            val sql = """
            SELECT  T.TXN_ID, T.TXN_TYPE, T.PART_ID, T.LOC_ID, T.LOC_FROM, T.LOC_TO,
                    T.QTY, CONVERT(NVARCHAR(10), T.TXN_DATE, 120) AS TXN_DATE,
                    T.REMARK, T.CREATED_BY,
                    CONVERT(NVARCHAR(19), T.CREATED_AT, 120) AS CREATED_AT,
                    T.DEVICE_INFO,
                    P.PART_NAME, P.PART_CODE, P.UNIT
            FROM    MT_TXN T
            JOIN    MT_PART_ITEM P ON T.PART_ID = P.PART_ID
            WHERE   T.TXN_DATE >= CAST(GETDATE() AS DATE)
            AND     (? IS NULL OR T.PART_ID = ?)
            AND     (? IS NULL OR T.CREATED_BY = ?)
            ORDER BY T.CREATED_AT DESC
        """.trimIndent()

            return@withContext connection.execQuery(
                sql,
                listOf(partId, partId, createdBy, createdBy)
            ) { rs ->
                val list = mutableListOf<TxnRecord>()
                while (rs.next()) list.add(rs.toTxnRecord())
                list
            }

        } finally {
            connection?.close()
        }
    }


    // -------------------------------------------------------
    // getRecentTxn
    // ดูประวัติ 30 วันล่าสุด — กรอง partId หรือ createdBy ได้
    // ทั้งสอง null = ดึงทั้งหมด (สำหรับ Dashboard)
    // -------------------------------------------------------
    suspend fun getRecentTxn(
        partId: String? = null,
        createdBy: String? = null,
        limitDays: Int = 30
    ): List<TxnRecord> = withContext(Dispatchers.IO) {
        val result = mutableListOf<TxnRecord>()
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext result

            val sql = """
                SELECT  T.TXN_ID, T.TXN_TYPE, T.PART_ID, T.LOC_ID, T.LOC_FROM, T.LOC_TO,
                        T.QTY, CONVERT(NVARCHAR(10), T.TXN_DATE, 120) AS TXN_DATE,
                        T.REMARK, T.CREATED_BY,
                        CONVERT(NVARCHAR(19), T.CREATED_AT, 120) AS CREATED_AT,
                        T.DEVICE_INFO,
                        P.PART_NAME, P.PART_CODE, P.UNIT
                FROM    MT_TXN T
                JOIN    MT_PART_ITEM P ON T.PART_ID = P.PART_ID
                WHERE   T.TXN_DATE >= DATEADD(DAY, -?, CAST(GETDATE() AS DATE))
                AND     (? IS NULL OR T.PART_ID   = ?)
                AND     (? IS NULL OR T.CREATED_BY = ?)
                ORDER BY T.CREATED_AT DESC
            """.trimIndent()


            return@withContext connection.execQuery(
                sql,
                listOf(limitDays, partId, partId, createdBy,createdBy)
            ) { rs ->

                val list = mutableListOf<TxnRecord>()
                while (rs.next()) list.add(rs.toTxnRecord())
                list
            }

        } catch (e: SQLException) {
            Log.e(TAG, "getRecentTxn: ${e.message}", e)
        } finally {
            connection?.close()
        }
        return@withContext result
    }

    // -------------------------------------------------------
    // getFilteredTxn
    // Advanced filtering for HistoryActivity
    // -------------------------------------------------------
    suspend fun getFilteredTxn(
        startDate: String? = null,
        endDate: String? = null,
        txnType: String? = null,
        createdBy: String? = null
    ): List<TxnRecord> = withContext(Dispatchers.IO) {
        val result = mutableListOf<TxnRecord>()
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext result

            val sql = StringBuilder("""
                SELECT  T.TXN_ID, T.TXN_TYPE, T.PART_ID, T.LOC_ID, T.LOC_FROM, T.LOC_TO,
                        T.QTY, CONVERT(NVARCHAR(10), T.TXN_DATE, 120) AS TXN_DATE,
                        T.REMARK, T.CREATED_BY,
                        CONVERT(NVARCHAR(19), T.CREATED_AT, 120) AS CREATED_AT,
                        T.DEVICE_INFO,
                        P.PART_NAME, P.PART_CODE, P.UNIT
                FROM    MT_TXN T
                JOIN    MT_PART_ITEM P ON T.PART_ID = P.PART_ID
                WHERE   1=1
            """.trimIndent())
            val params = mutableListOf<Any?>()

            if (!startDate.isNullOrEmpty()) {
                sql.append(" AND T.TXN_DATE >= ?")
                params.add(startDate)
            }

            if (!endDate.isNullOrEmpty()) {
                sql.append(" AND T.TXN_DATE <= ?")
                params.add(endDate)
            }

            if (!txnType.isNullOrEmpty()) {
                sql.append(" AND T.TXN_TYPE = ?")
                params.add(txnType)
            }

            if (!createdBy.isNullOrEmpty()) {
                sql.append(" AND T.CREATED_BY = ?")
                params.add(createdBy)
            }

            sql.append(" ORDER BY T.CREATED_AT DESC")

            return@withContext connection.execQuery(
                sql.toString(),
                params
            ) { rs ->
                val list = mutableListOf<TxnRecord>()
                while (rs.next()) list.add(rs.toTxnRecord())
                list
            }

        } catch (e: SQLException) {
            Log.e(TAG, "getFilteredTxn: ${e.message}", e)
        } finally {
            connection?.close()
        }
        return@withContext result
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    private fun getCurrentStock(connection: Connection, partId: String): Pair<Double, String>? {

        val sql = "SELECT QTY_ON_HAND, LOC_ID FROM MT_STOCK WITH (UPDLOCK) WHERE PART_ID = ?"

        return connection.execQuery(sql, listOf(partId)) { rs ->
            if (rs.next()) {
                Pair(
                    rs.getDouble("QTY_ON_HAND"),
                    rs.getString("LOC_ID")
                )
            } else null
        }
    }



    private fun insertTxn(
        connection: Connection,
        input: TxnInput,
        locFrom: String?,
        locTo: String?
    ): Int? {

        val sql = """
        INSERT INTO MT_TXN
        (TXN_TYPE, PART_ID, LOC_ID, LOC_FROM, LOC_TO, QTY, TXN_DATE, REMARK, CREATED_BY, DEVICE_INFO)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

        val params = listOf(
            input.txnType,
            input.partId,
            input.locId,
            locFrom,
            locTo,
            input.qty,
            input.txnDate,
            input.remark,
            input.createdBy,
            input.deviceInfo
        )

        return connection.execInsert(sql, params) { keys ->
            if (keys != null && keys.next()) {
                keys.getInt(1)
            } else {
                Log.e(TAG, "Insert ok but no generated key")
                null
            }
        }
    }

    private fun updateStock(connection: Connection, partId: String, newQty: Double) {

        val sql = """
        UPDATE MT_STOCK 
        SET QTY_ON_HAND = ?, LAST_UPDATED = GETDATE() 
        WHERE PART_ID = ?
    """.trimIndent()

        connection.execUpdate(sql, listOf(newQty, partId))
    }

    private fun insertAuditLog(
        connection: Connection,
        txnId: Int,
        action: String,
        changedBy: String,
        oldValue: String?,
        newValue: String?,
        deviceInfo: String?
    ) {

        val sql = """
        INSERT INTO MT_AUDIT_LOG 
        (TXN_ID, ACTION, CHANGED_BY, OLD_VALUE, NEW_VALUE, DEVICE_INFO)
        VALUES (?,?,?,?,?,?)
    """.trimIndent()

        connection.execUpdate(
            sql,
            listOf(txnId, action, changedBy, oldValue, newValue, deviceInfo)
        )
    }

    private fun java.sql.ResultSet.toTxnRecord() = TxnRecord(
        txnId      = getInt("TXN_ID"),
        txnType    = getString("TXN_TYPE"),
        partId     = getString("PART_ID"),
        locId      = getString("LOC_ID"),

        locFrom    = getString("LOC_FROM"),   // ✅ เพิ่ม
        locTo      = getString("LOC_TO"),     // ✅ เพิ่ม

        qty        = getDouble("QTY"),
        txnDate    = getString("TXN_DATE") ?: "",
        remark     = getString("REMARK"),
        createdBy  = getString("CREATED_BY"),
        createdAt  = getString("CREATED_AT") ?: "",
        deviceInfo = getString("DEVICE_INFO"),

        partName   = getString("PART_NAME"),
        partCode   = getString("PART_CODE"),
        unit       = getString("UNIT")
    )
}
