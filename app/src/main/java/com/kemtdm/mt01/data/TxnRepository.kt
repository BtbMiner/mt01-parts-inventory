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
            connection.autoCommit = false   // เริ่ม transaction

            // 1. ดึงยอดปัจจุบันก่อน (ใช้ใน AUDIT OLD_VALUE ด้วย)
            val currentQty = getCurrentQty(connection, input.partId)
                ?: run {
                    connection.rollback()
                    return@withContext false
                }

            // 2. คำนวณยอดใหม่
            val newQty = when (input.txnType) {
                "IN"  -> currentQty + input.qty
                "OUT" -> currentQty - input.qty
                "RET" -> currentQty + input.qty
                else  -> {
                    connection.rollback()
                    return@withContext false
                }
            }

            if (newQty < 0) {
                // Stock ไม่พอ (OUT เกินยอด)
                connection.rollback()
                return@withContext false
            }

            // 3. Insert MT_TXN
            val txnId = insertTxn(connection, input) ?: run {
                connection.rollback()
                return@withContext false
            }

            // 4. Update MT_STOCK
            updateStock(connection, input.partId, newQty)

            // 5. Insert MT_AUDIT_LOG (ACTION = CREATE)
            insertAuditLog(
                connection  = connection,
                txnId       = txnId,
                action      = "CREATE",
                changedBy   = input.createdBy,
                oldValue    = null,
                newValue    = """{"TXN_TYPE":"${input.txnType}","QTY":${input.qty},"TXN_DATE":"${input.txnDate}","QTY_AFTER":$newQty}""",
                deviceInfo  = input.deviceInfo
            )

            connection.commit()
            return@withContext true

        } catch (e: SQLException) {
            Log.e(TAG, "saveTxn: ${e.message}", e)
            connection?.rollback()
            return@withContext false
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
                SELECT  T.TXN_ID, T.TXN_TYPE, T.PART_ID, T.LOC_ID,
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
    // Private helpers
    // -------------------------------------------------------

    private fun getCurrentQty(connection: Connection, partId: String): Double? {
        connection.prepareStatement(
            "SELECT QTY_ON_HAND FROM MT_STOCK WHERE PART_ID = ?"
        ).use { ps ->
            ps.setString(1, partId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getDouble("QTY_ON_HAND")
            }
        }
        Log.e(TAG, "getCurrentQty: PART_ID $partId not found in MT_STOCK")
        return null
    }

    private fun insertTxn(connection: Connection, input: TxnInput): Int? {
        val sql = """
            INSERT INTO MT_TXN
                (TXN_TYPE, PART_ID, LOC_ID, QTY, TXN_DATE, REMARK, CREATED_BY, DEVICE_INFO)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            SELECT SCOPE_IDENTITY() AS NEW_ID;
        """.trimIndent()

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, input.txnType)
            ps.setString(2, input.partId)
            ps.setString(3, input.locId)
            ps.setDouble(4, input.qty)
            ps.setString(5, input.txnDate)
            ps.setString(6, input.remark)
            ps.setString(7, input.createdBy)
            ps.setString(8, input.deviceInfo)
            ps.execute()
            // jTDS คืนผ่าน ResultSet หลัง execute
            if (ps.moreResults.not()) {
                val rs = ps.resultSet ?: ps.getResultSet()
                rs?.use { if (it.next()) return it.getInt("NEW_ID") }
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
