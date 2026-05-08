package com.kemtdm.mt01.data

import android.util.Log
import com.kemtdm.mt01.sql.GetConnection
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
    suspend fun saveTxn(input: TxnInput): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext false
            connection.autoCommit = false

            // ✅ 1. ดึง stock ปัจจุบัน (ล็อก row กัน race condition)
            val (currentQty, currentLoc) = getCurrentStock(connection, input.partId)
                ?: run {
                    connection.rollback()
                    return@withContext false
                }

            // ✅ 2. คำนวณ QTY ใหม่
            val newQty = when (input.txnType) {
                "IN", "RET" -> currentQty + input.qty
                "OUT"       -> currentQty - input.qty
                else -> {
                    connection.rollback()
                    return@withContext false
                }
            }

            if (newQty < 0) {
                Log.e(TAG, "Stock not enough: current=$currentQty request=${input.qty}")
                connection.rollback()
                return@withContext false
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
                    return@withContext false
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
                return@withContext false
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
            true

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

            connection?.rollback()
            false

        } catch (e: Exception) {

            Log.e(TAG, "=== GENERAL ERROR === ${e.message}", e)

            connection?.rollback()
            false

        } finally {
            connection?.autoCommit = true
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

            connection.prepareStatement(sql).use { ps ->
                ps.setInt(1, limitDays)
                ps.setString(2, partId);  ps.setString(3, partId)
                ps.setString(4, createdBy); ps.setString(5, createdBy)
                ps.executeQuery().use { rs ->
                    while (rs.next()) result.add(rs.toTxnRecord())
                }
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

            if (!startDate.isNullOrEmpty()) sql.append(" AND T.TXN_DATE >= ?")
            if (!endDate.isNullOrEmpty()) sql.append(" AND T.TXN_DATE <= ?")
            if (!txnType.isNullOrEmpty()) sql.append(" AND T.TXN_TYPE = ?")
            if (!createdBy.isNullOrEmpty()) sql.append(" AND T.CREATED_BY = ?")

            sql.append(" ORDER BY T.CREATED_AT DESC")

            connection.prepareStatement(sql.toString()).use { ps ->
                var paramIdx = 1
                if (!startDate.isNullOrEmpty()) ps.setString(paramIdx++, startDate)
                if (!endDate.isNullOrEmpty()) ps.setString(paramIdx++, endDate)
                if (!txnType.isNullOrEmpty()) ps.setString(paramIdx++, txnType)
                if (!createdBy.isNullOrEmpty()) ps.setString(paramIdx++, createdBy)

                ps.executeQuery().use { rs ->
                    while (rs.next()) result.add(rs.toTxnRecord())
                }
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
        connection.prepareStatement(
            "SELECT QTY_ON_HAND, LOC_ID FROM MT_STOCK WITH (UPDLOCK) WHERE PART_ID = ?"
        ).use { ps ->
            ps.setString(1, partId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    return Pair(
                        rs.getDouble("QTY_ON_HAND"),
                        rs.getString("LOC_ID")
                    )
                }
            }
        }
        return null
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

        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->

            ps.setString(1, input.txnType)
            ps.setString(2, input.partId)
            ps.setString(3, input.locId)

            if (locFrom == null) ps.setNull(4, java.sql.Types.VARCHAR)
            else ps.setString(4, locFrom)

            if (locTo == null) ps.setNull(5, java.sql.Types.VARCHAR)
            else ps.setString(5, locTo)

            ps.setDouble(6, input.qty)
            ps.setString(7, input.txnDate)
            ps.setString(8, input.remark)
            ps.setString(9, input.createdBy)
            ps.setString(10, input.deviceInfo)

            val affected = ps.executeUpdate()

            if (affected == 0) {
                Log.e(TAG, "Insert failed: 0 rows affected")
                return null
            }


            val rs = ps.generatedKeys
            if (rs.next()) {
                return rs.getInt(1)
            }

            if (!rs.next()) {
                Log.e(TAG, "Insert ok but no generated key returned")
            }

        }


        return null
    }




    private fun updateStock(connection: Connection, partId: String, newQty: Double) {
        connection.prepareStatement(
            "UPDATE MT_STOCK SET QTY_ON_HAND = ?, LAST_UPDATED = GETDATE() WHERE PART_ID = ?"
        ).use { ps ->
            ps.setDouble(1, newQty)
            ps.setString(2, partId)
            ps.executeUpdate()
        }
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
        connection.prepareStatement(
            "INSERT INTO MT_AUDIT_LOG (TXN_ID, ACTION, CHANGED_BY, OLD_VALUE, NEW_VALUE, DEVICE_INFO) VALUES (?,?,?,?,?,?)"
        ).use { ps ->
            ps.setInt(1, txnId)
            ps.setString(2, action)
            ps.setString(3, changedBy)
            ps.setString(4, oldValue)
            ps.setString(5, newValue)
            ps.setString(6, deviceInfo)
            ps.executeUpdate()
        }
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
