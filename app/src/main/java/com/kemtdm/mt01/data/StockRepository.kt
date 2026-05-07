package com.kemtdm.mt01.data

import android.util.Log
import com.kemtdm.mt01.sql.GetConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException

object StockRepository {

    private const val TAG = "StockRepository"

    // -------------------------------------------------------
    // getStockByLocId
    // scan QR (LOC_ID) → ดึง StockInfo 1 รายการ
    // -------------------------------------------------------
    suspend fun getStockByLocId(locId: String): StockInfo? = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext null

            val sql = """
                SELECT  S.STOCK_ID, S.PART_ID, S.LOC_ID, S.QTY_ON_HAND,
                        CONVERT(NVARCHAR(19), S.LAST_UPDATED, 120) AS LAST_UPDATED,
                        P.PART_CODE, P.PART_NAME, P.BRAND, P.MODEL,
                        P.CATEGORY, P.UNIT, P.MIN_STOCK,
                        L.DESCRIPTION AS LOC_DESCRIPTION
                FROM    MT_STOCK S
                JOIN    MT_PART_ITEM P ON S.PART_ID = P.PART_ID
                JOIN    MT_LOCATION  L ON S.LOC_ID  = L.LOC_ID
                WHERE   S.LOC_ID = ?
                AND     P.IS_ACTIVE = 1
            """.trimIndent()

            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, locId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) return@withContext rs.toStockInfo()
                }
            }
        } catch (e: SQLException) {
            Log.e(TAG, "getStockByLocId: ${e.message}", e)
        } finally {
            connection?.close()
        }
        return@withContext null
    }

    // -------------------------------------------------------
    // getStockByPartId
    // ค้นหาด้วย PART_ID (fallback กรณีค้นหาด้วยชื่อ/รหัส)
    // -------------------------------------------------------
    suspend fun getStockByPartId(partId: String): StockInfo? = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext null

            val sql = """
                SELECT  S.STOCK_ID, S.PART_ID, S.LOC_ID, S.QTY_ON_HAND,
                        CONVERT(NVARCHAR(19), S.LAST_UPDATED, 120) AS LAST_UPDATED,
                        P.PART_CODE, P.PART_NAME, P.BRAND, P.MODEL,
                        P.CATEGORY, P.UNIT, P.MIN_STOCK,
                        L.DESCRIPTION AS LOC_DESCRIPTION
                FROM    MT_STOCK S
                JOIN    MT_PART_ITEM P ON S.PART_ID = P.PART_ID
                JOIN    MT_LOCATION  L ON S.LOC_ID  = L.LOC_ID
                WHERE   S.PART_ID = ?
                AND     P.IS_ACTIVE = 1
            """.trimIndent()

            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, partId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) return@withContext rs.toStockInfo()
                }
            }
        } catch (e: SQLException) {
            Log.e(TAG, "getStockByPartId: ${e.message}", e)
        } finally {
            connection?.close()
        }
        return@withContext null
    }

    // -------------------------------------------------------
    // searchStock
    // ค้นหาจากชื่อหรือ PART_CODE (ใช้ใน search bar)
    // -------------------------------------------------------
    suspend fun searchStock(keyword: String): List<StockInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StockInfo>()
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext result

            val sql = """
                SELECT  S.STOCK_ID, S.PART_ID, S.LOC_ID, S.QTY_ON_HAND,
                        CONVERT(NVARCHAR(19), S.LAST_UPDATED, 120) AS LAST_UPDATED,
                        P.PART_CODE, P.PART_NAME, P.BRAND, P.MODEL,
                        P.CATEGORY, P.UNIT, P.MIN_STOCK,
                        L.DESCRIPTION AS LOC_DESCRIPTION
                FROM    MT_STOCK S
                JOIN    MT_PART_ITEM P ON S.PART_ID = P.PART_ID
                JOIN    MT_LOCATION  L ON S.LOC_ID  = L.LOC_ID
                WHERE   P.IS_ACTIVE = 1
                AND    (P.PART_NAME LIKE ? OR P.PART_CODE LIKE ?)
                ORDER BY P.PART_NAME
            """.trimIndent()

            connection.prepareStatement(sql).use { ps ->
                val kw = "%$keyword%"
                ps.setString(1, kw)
                ps.setString(2, kw)
                ps.executeQuery().use { rs ->
                    while (rs.next()) result.add(rs.toStockInfo())
                }
            }
        } catch (e: SQLException) {
            Log.e(TAG, "searchStock: ${e.message}", e)
        } finally {
            connection?.close()
        }
        return@withContext result
    }

    // -------------------------------------------------------
    // getLowStockList
    // ดึงรายการที่ QTY_ON_HAND < MIN_STOCK (สำหรับ Dashboard)
    // -------------------------------------------------------
    suspend fun getLowStockList(): List<StockInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StockInfo>()
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext result

            val sql = """
                SELECT  S.STOCK_ID, S.PART_ID, S.LOC_ID, S.QTY_ON_HAND,
                        CONVERT(NVARCHAR(19), S.LAST_UPDATED, 120) AS LAST_UPDATED,
                        P.PART_CODE, P.PART_NAME, P.BRAND, P.MODEL,
                        P.CATEGORY, P.UNIT, P.MIN_STOCK,
                        L.DESCRIPTION AS LOC_DESCRIPTION
                FROM    MT_STOCK S
                JOIN    MT_PART_ITEM P ON S.PART_ID = P.PART_ID
                JOIN    MT_LOCATION  L ON S.LOC_ID  = L.LOC_ID
                WHERE   P.IS_ACTIVE = 1
                AND     S.QTY_ON_HAND < P.MIN_STOCK
                ORDER BY S.QTY_ON_HAND ASC
            """.trimIndent()

            connection.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) result.add(rs.toStockInfo())
                }
            }
        } catch (e: SQLException) {
            Log.e(TAG, "getLowStockList: ${e.message}", e)
        } finally {
            connection?.close()
        }
        return@withContext result
    }

    // -------------------------------------------------------
    // Private extension: ResultSet → StockInfo
    // -------------------------------------------------------
    private fun java.sql.ResultSet.toStockInfo() = StockInfo(
        stockId      = getInt("STOCK_ID"),
        partId       = getString("PART_ID"),
        locId        = getString("LOC_ID"),
        qtyOnHand    = getDouble("QTY_ON_HAND"),
        lastUpdated  = getString("LAST_UPDATED") ?: "",
        partCode     = getString("PART_CODE"),
        partName     = getString("PART_NAME"),
        brand        = getString("BRAND"),
        model        = getString("MODEL"),
        category     = getString("CATEGORY"),
        unit         = getString("UNIT"),
        minStock     = getDouble("MIN_STOCK"),
        locDescription = getString("LOC_DESCRIPTION")
    )
}
