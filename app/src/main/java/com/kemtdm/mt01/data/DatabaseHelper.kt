package com.kemtdm.mt01.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.kemtdm.mt01.sql.GetConnection
import com.kemtdm.mt01.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.SQLException

object DatabaseHelper {

    private const val TAG = "DatabaseHelper"

    // ไม่จำเป็นต้องใช้ getUnitName อีกต่อไปเนื่องจาก UNIT_NAME ถูกลบออก
    // private fun getUnitName(context: Context): String {
    //     val sharedPreferences = context.getSharedPreferences("LoginData", Context.MODE_PRIVATE)
    //     return sharedPreferences.getString("unitName", "SLT1") ?: "SLT1"
    // }

    // ปรับแก้: getLatestKtsNo
    // - เปลี่ยนชื่อตารางเป็น CM_CS_FP_KTS
    // - ลบการใช้ UNIT_NAME ออก
    // - เพิ่ม ESTABLISHED_WEEK ใน SELECT
    // - เปลี่ยน ORDER BY เป็น ESTABLISHED_WEEK DESC
    suspend fun getLatestKtsNo(context: Context): Pair<String?, String?> = withContext(Dispatchers.IO) {
        var ktsNo: String? = null
        var establishedWeek: String? = null
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for getLatestKtsNo.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext Pair(null, null)
            }

            val currentYearMonthWeek = DateUtils.getCurrentYearMonthWeek()
            // Query to get the latest KTS_NO and ESTABLISHED_WEEK
            val sql = "SELECT TOP 1 KTS_NO, ESTABLISHED_WEEK FROM CM_CS_FP_KTS WHERE ESTABLISHED_WEEK <= ? ORDER BY ESTABLISHED_WEEK DESC"
            preparedStatement = connection.prepareStatement(sql)
            var parameterIndex = 1
            preparedStatement.setString(parameterIndex, currentYearMonthWeek)
            resultSet = preparedStatement.executeQuery()

            if (resultSet.next()) {
                ktsNo = resultSet.getString("KTS_NO")
                establishedWeek = resultSet.getString("ESTABLISHED_WEEK")
                Log.d(TAG, "Found the latest KTS_NO: $ktsNo with ESTABLISHED_WEEK: $establishedWeek")
            } else {
                Log.w(TAG, "No KTS_NO found.")
                Toast.makeText(context, "ไม่พบ KTS_NO", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getLatestKtsNo: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLatestKtsNo: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการดึง KTS_NO", Toast.LENGTH_LONG).show()
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getLatestKtsNo: ${e.message}")
            }
        }
        return@withContext Pair(ktsNo, establishedWeek)
    }

    /*// ปรับแก้: getAllEquipment
    // - เปลี่ยนชื่อตารางเป็น CM_CS_FP_EQ
    // - ลบการใช้ UNIT_NAME ออก
    // - เปลี่ยน EQUIPMENT_NAME เป็น IsNullable = YES, จึงต้องเช็คค่า NULL
    suspend fun getAllEquipment(context: Context): List<Equipment> = withContext(Dispatchers.IO) {
        val equipmentList = mutableListOf<Equipment>()
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for getAllEquipment.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext emptyList()
            }
            val sql = "SELECT EQUIPMENT_ID, EQUIPMENT_NAME FROM CM_CS_FP_EQ ORDER BY EQUIPMENT_ID ASC"
            preparedStatement = connection.prepareStatement(sql)
            resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                val id = resultSet.getInt("EQUIPMENT_ID")
                val name = resultSet.getString("EQUIPMENT_NAME")
                equipmentList.add(Equipment(id, name))
            }
            Log.d(TAG, "Fetched ${equipmentList.size} Equipment items.")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getAllEquipment: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAllEquipment: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการดึงข้อมูลอุปกรณ์", Toast.LENGTH_LONG).show()
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getAllEquipment: ${e.message}")
            }
        }
        return@withContext equipmentList
    }*/

    suspend fun getLatestEstablishedWeekEquipment(context: Context, ktsNo: String, establishedWeek: String): List<Equipment> = withContext(Dispatchers.IO) {
        val equipmentList = mutableListOf<Equipment>()
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for getAllEquipment.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext emptyList()
            }

            val sql = "SELECT DISTINCT KL.EQUIPMENT_ID, EQ.EQUIPMENT_NAME FROM CM_CS_FP_KTS_LIST KL JOIN CM_CS_FP_EQ EQ ON KL.EQUIPMENT_ID = EQ.EQUIPMENT_ID WHERE KL.KTS_NO = ? AND KL.ESTABLISHED_WEEK = ? ORDER BY KL.EQUIPMENT_ID ASC"
            preparedStatement = connection.prepareStatement(sql)
            var parameterIndex = 1
            preparedStatement.setString(parameterIndex++, ktsNo)
            preparedStatement.setString(parameterIndex, establishedWeek)

            resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                val id = resultSet.getInt("EQUIPMENT_ID")
                val name = resultSet.getString("EQUIPMENT_NAME")
                equipmentList.add(Equipment(id, name))
            }
            Log.d(TAG, "Fetched ${equipmentList.size} Equipment items.")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getAllEquipment: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAllEquipment: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการดึงข้อมูลอุปกรณ์", Toast.LENGTH_LONG).show()
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getAllEquipment: ${e.message}")
            }
        }
        return@withContext equipmentList
    }

    // ปรับแก้: getChecklistItems
    // - เปลี่ยนชื่อตารางเป็น CM_CS_FP_KTS_LIST และ CS_FP_RECORDS
    // - ลบการใช้ UNIT_NAME ออกจาก SQL query และ WHERE clause
    // - เพิ่ม ESTABLISHED_WEEK ใน WHERE clause สำหรับ JOIN
    suspend fun getChecklistItems(context: Context, ktsNo: String, establishedWeek: String, equipmentId: Int, yearMonth: String, checkedWeek: Int): List<ChecklistItem> = withContext(Dispatchers.IO) {
        val checklistItems = mutableListOf<ChecklistItem>()
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for getChecklistItems.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext emptyList()
            }

            val sql = """
            DECLARE @KTS_NO VARCHAR(30) = ?
            DECLARE @ESTABLISHED_WEEK VARCHAR(7) = ?
            DECLARE @EQUIPMENT_ID INT = ?
            DECLARE @YEARMONTH VARCHAR(6) = ?
            DECLARE @CHECKED_WEEK INT = ?

            SELECT
                KL.KTS_NO,
                KL.ESTABLISHED_WEEK,
                KL.EQUIPMENT_ID,
                KL.CHECK_ID,
                KL.CHECK_POINT,
                KL.CHECK_PERIOD,
                KL.SELECTED_WEEK,
                COALESCE(PR.RESULT,0) RESULT,
                PR.ADDITIONAL_NOTE
            FROM (
                SELECT * FROM CM_CS_FP_KTS_LIST
                WHERE KTS_NO = @KTS_NO AND ESTABLISHED_WEEK = @ESTABLISHED_WEEK AND EQUIPMENT_ID = @EQUIPMENT_ID
            ) KL
            LEFT JOIN (
                SELECT * FROM CS_FP_RECORDS
                WHERE YEARMONTH = @YEARMONTH AND CHECKED_WEEK = @CHECKED_WEEK AND KTS_NO = @KTS_NO AND ESTABLISHED_WEEK = @ESTABLISHED_WEEK
            ) PR
            ON
                KL.KTS_NO = PR.KTS_NO AND
                KL.ESTABLISHED_WEEK = PR.ESTABLISHED_WEEK AND
                KL.EQUIPMENT_ID = PR.EQUIPMENT_ID AND
                KL.CHECK_ID = PR.CHECK_ID
            ORDER BY KL.CHECK_ID ASC
        """.trimIndent()
            preparedStatement = connection.prepareStatement(sql)

            var parameterIndex = 1

            preparedStatement.setString(parameterIndex++, ktsNo)
            preparedStatement.setString(parameterIndex++, establishedWeek)
            preparedStatement.setInt(parameterIndex++, equipmentId)
            preparedStatement.setString(parameterIndex++, yearMonth)
            preparedStatement.setInt(parameterIndex, checkedWeek)

            resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                val ktsNo = resultSet.getString("KTS_NO")
                val establishedWeek = resultSet.getString("ESTABLISHED_WEEK")
                val equipmentId = resultSet.getInt("EQUIPMENT_ID")
                val checkId = resultSet.getInt("CHECK_ID")
                val checkPoint = resultSet.getString("CHECK_POINT")
                val checkPeriod = resultSet.getInt("CHECK_PERIOD")
                val selectedWeek = resultSet.getInt("SELECTED_WEEK")
                val selectedResult = resultSet.getInt("RESULT")
                val additionalNote = resultSet.getString("ADDITIONAL_NOTE")
                checklistItems.add(ChecklistItem(ktsNo, establishedWeek, equipmentId, checkId, checkPoint, checkPeriod, selectedWeek,selectedResult, additionalNote))
            }
            Log.d(TAG, "Fetched ${checklistItems.size} checklist items for KTS_NO: $ktsNo, ESTABLISHED_WEEK: $establishedWeek, Equipment ID: $equipmentId")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getChecklistItems: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getChecklistItems: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการดึงรายการตรวจสอบ", Toast.LENGTH_LONG).show()
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getChecklistItems: ${e.message}")
            }
        }
        return@withContext checklistItems
    }

    // ปรับแก้: savePMHeader
    // - เปลี่ยนชื่อตารางเป็น CS_FP
    // - ลบการใช้ UNIT_NAME ออกจาก SQL query และ WHERE clause
    // - เพิ่ม ESTABLISHED_WEEK เป็นส่วนหนึ่งของ Primary Key ใน MERGE statement
    suspend fun savePMHeader(context: Context, yearMonth: String, checkedWeek: Int, ktsNo: String, establishedWeek: String, userId: String, pi: String, dv: String?) = withContext(Dispatchers.IO) {
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for savePMHeader.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext
            }

            val mergeSql = """
                DECLARE @NOW DATETIME = GETDATE()

                DECLARE @YEARMONTH VARCHAR(6) = ?
                DECLARE @CHECKED_WEEK INT = ?
                DECLARE @KTS_NO VARCHAR(30) = ?
                DECLARE @ESTABLISHED_WEEK VARCHAR(7) = ?
                DECLARE @UI VARCHAR(6) = ?
                DECLARE @PI VARCHAR(6) = ?
                DECLARE @DV VARCHAR(60) = ?

                MERGE INTO CS_FP AS Target
                USING (VALUES (@YEARMONTH, @CHECKED_WEEK, @KTS_NO, @ESTABLISHED_WEEK)) AS Source (YearMonth, CheckedWeek, KTS_NO, EstablishedWeek)
                ON Target.YEARMONTH = Source.YearMonth AND Target.CHECKED_WEEK = Source.CheckedWeek AND Target.KTS_NO = Source.KTS_NO AND Target.ESTABLISHED_WEEK = Source.EstablishedWeek
                WHEN MATCHED THEN
                    UPDATE SET
                        LATEST_CHECKED_BY = @UI,
                        LATEST_CHECKED_DATE = @NOW,
                        LPI = @PI,
                        LDV = @DV
                WHEN NOT MATCHED THEN
                    INSERT (YEARMONTH, CHECKED_WEEK, KTS_NO, ESTABLISHED_WEEK, CHECKED_BY, CHECKED_DATE, PI, DV)
                    VALUES (@YEARMONTH, @CHECKED_WEEK, @KTS_NO, @ESTABLISHED_WEEK, @UI, @NOW, @PI, @DV);
            """.trimIndent()

            preparedStatement = connection.prepareStatement(mergeSql)

            var parameterIndex = 1

            preparedStatement.setString(parameterIndex++, yearMonth)
            preparedStatement.setInt(parameterIndex++, checkedWeek)
            preparedStatement.setString(parameterIndex++, ktsNo)
            preparedStatement.setString(parameterIndex++, establishedWeek)
            preparedStatement.setString(parameterIndex++, userId)
            preparedStatement.setString(parameterIndex++, pi)
            preparedStatement.setString(parameterIndex, dv)

            preparedStatement.executeUpdate()
            Log.d(TAG, "Successfully executed 'upsert' on header data using MERGE statement: YEARMONTH=$yearMonth, CHECKED_WEEK=$checkedWeek, KTS_NO=$ktsNo, ESTABLISHED_WEEK=$establishedWeek")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in savePMHeader: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in savePMHeader: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการบันทึกข้อมูลส่วนหัว", Toast.LENGTH_LONG).show()
        } finally {
            try {
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in savePMHeader: ${e.message}")
            }
        }
    }

    // ปรับแก้: saveChecklistRecord
    // - เปลี่ยนชื่อตารางเป็น CS_FP_RECORDS
    // - ลบการใช้ UNIT_NAME ออก
    // - เพิ่ม ESTABLISHED_WEEK ใน MERGE statement และใน VALUES
    suspend fun saveChecklistRecord(context: Context, record: ChecklistItem, establishedWeek: String, userId: String, pi: String, dv: String?) = withContext(Dispatchers.IO) {
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for saveChecklistRecord.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext
            }

            val mergeSql = """
            DECLARE @NOW DATETIME = GETDATE()
            DECLARE @YEARMONTH VARCHAR(6) = ?
            DECLARE @CHECKED_WEEK INT = ?
            DECLARE @KTS_NO VARCHAR(30) = ?
            DECLARE @ESTABLISHED_WEEK VARCHAR(7) = ?
            DECLARE @EQUIPMENT_ID INT = ?
            DECLARE @CHECK_ID INT = ?
            DECLARE @RESULT INT = ?
            DECLARE @ADDITIONAL_NOTE NVARCHAR(200) = ?
            DECLARE @UI VARCHAR(6) = ?
            DECLARE @PI VARCHAR(6) = ?
            DECLARE @DV VARCHAR(60) = ?

            MERGE INTO CS_FP_RECORDS AS Target
            USING (VALUES (@YEARMONTH, @CHECKED_WEEK, @KTS_NO, @ESTABLISHED_WEEK, @EQUIPMENT_ID, @CHECK_ID)) AS Source (YearMonth, CheckedWeek, KTS_NO, EstablishedWeek, EquipmentId, CheckId)
            ON Target.YEARMONTH = Source.YearMonth AND Target.CHECKED_WEEK = Source.CheckedWeek AND Target.KTS_NO = Source.KTS_NO AND Target.ESTABLISHED_WEEK = Source.EstablishedWeek AND Target.EQUIPMENT_ID = Source.EquipmentId AND Target.CHECK_ID = Source.CheckId
            WHEN MATCHED THEN
                UPDATE SET
                    RESULT = @RESULT,
                    ADDITIONAL_NOTE = @ADDITIONAL_NOTE,
                    LUI = @UI,
                    LPI = @PI,
                    LDT = @NOW,
                    LDV = @DV
            WHEN NOT MATCHED THEN
                INSERT (YEARMONTH, CHECKED_WEEK, KTS_NO, ESTABLISHED_WEEK, EQUIPMENT_ID, CHECK_ID, CHECKED_DATE, RESULT, ADDITIONAL_NOTE, UI, PI, DT, DV)
                VALUES (@YEARMONTH, @CHECKED_WEEK, @KTS_NO, @ESTABLISHED_WEEK, @EQUIPMENT_ID, @CHECK_ID, @NOW, @RESULT, @ADDITIONAL_NOTE, @UI, @PI, @NOW, @DV);
        """.trimIndent()

            preparedStatement = connection.prepareStatement(mergeSql)

            var parameterIndex = 1

            preparedStatement.setString(parameterIndex++, record.yearMonth)
            preparedStatement.setInt(parameterIndex++, record.checkedWeek)
            preparedStatement.setString(parameterIndex++, record.ktsNo)
            preparedStatement.setString(parameterIndex++, establishedWeek)
            preparedStatement.setInt(parameterIndex++, record.equipmentId)
            preparedStatement.setInt(parameterIndex++, record.checkId)
            preparedStatement.setInt(parameterIndex++, record.selectedResult)
            preparedStatement.setString(parameterIndex++, record.additionalNote ?: "")
            preparedStatement.setString(parameterIndex++, userId)
            preparedStatement.setString(parameterIndex++, pi)
            preparedStatement.setString(parameterIndex, dv)

            preparedStatement.executeUpdate()
            Log.d(TAG, "ดำเนินการ 'upsert' รายการตรวจสอบสำเร็จด้วย MERGE statement: YEARMONTH=${record.yearMonth}, CHECKED_WEEK=${record.checkedWeek}, KTS_NO=${record.ktsNo}, ESTABLISHED_WEEK=$establishedWeek, EQUIPMENT_ID=${record.equipmentId}, CHECK_ID=${record.checkId}")

        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception ใน saveChecklistRecord: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "ข้อผิดพลาดในการปิด resources ใน saveChecklistRecord: ${e.message}")
            }
        }
    }

    // ปรับแก้: getSavedFormHeaders
    // - เปลี่ยนชื่อตารางเป็น CS_FP และ CM_CS_FP_KTS_LIST
    // - ลบการใช้ UNIT_NAME ออกจาก SQL query และ WHERE clause
    // - เพิ่ม ESTABLISHED_WEEK เป็นส่วนหนึ่งของ JOIN clause
    // - เพิ่ม ESTABLISHED_WEEK ใน SELECT และ GROUP BY
    // - ลบการ JOIN กับ CS_MC_PM_MANAGER_SIGNATURE ออก เพราะไม่ได้อยู่ในโครงสร้างใหม่
    suspend fun getSavedFormHeaders(context: Context): List<SavedFormHeader> = withContext(Dispatchers.IO) {
        val savedForms = mutableListOf<SavedFormHeader>()
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                //Log.e(TAG, "Database connection is null for getSavedFormHeaders.")
                return@withContext emptyList()
            }

            val sql = """
            WITH EMPLOYEE AS (
                SELECT
                    E.EMPLOYEE_ID,
                    E.EMPLOYEE_NAME
                FROM CM_EMPLOYEE E
            )

            SELECT          MP.KTS_NO,
                            MP.ESTABLISHED_WEEK,
                            FORMAT(CAST(MP.YEARMONTH + '01' AS DATE), 'MMM yyyy', 'en-us') AS YEARMONTH_FORMATTED,
                            MP.CHECKED_WEEK,
                            CF.EMPLOYEE_NAME + ' (' + MP.CHECKED_BY + ')' CHECKED_BY,
                            MP.CHECKED_DATE,
                            CL.EMPLOYEE_NAME + ' (' + MP.LATEST_CHECKED_BY + ')' LATEST_CHECKED_BY,
                            MP.LATEST_CHECKED_DATE,
                            CV.EMPLOYEE_NAME + ' (' + MP.VERIFIED_BY + ')' VERIFIED_BY,
                            MP.VERIFIED_DATE,
                            CM.EMPLOYEE_NAME + ' (' + MS.MANAGER_ID + ')' APPROVED_BY,
                            MS.APPROVED_DATE,
                            COUNT(PR.RESULT) AS CHECKED_ITEMS,
                            SUM(MP.ON_WEEK) AS TOTAL_ITEMS
            FROM            (
                                SELECT      MP.KTS_NO,
                                            MP.ESTABLISHED_WEEK,
                                            MP.YEARMONTH,
                                            MP.CHECKED_WEEK,
                                            MP.CHECKED_BY,
                                            MP.CHECKED_DATE,
                                            MP.LATEST_CHECKED_BY,
                                            MP.LATEST_CHECKED_DATE,
                                            KL.EQUIPMENT_ID,
                                            KL.CHECK_ID,
                                            MP.VERIFIED_BY,
                                            MP.VERIFIED_DATE,
                                            CASE WHEN KL.CHECK_PERIOD = 1 THEN 1 ELSE CASE WHEN KL.CHECK_PERIOD = 2 AND MP.CHECKED_WEEK = KL.SELECTED_WEEK THEN 1 ELSE 0 END END AS ON_WEEK
                                FROM        (   SELECT      TOP 54
                                                            KTS_NO, ESTABLISHED_WEEK, YEARMONTH, CHECKED_WEEK, CHECKED_BY, CHECKED_DATE, LATEST_CHECKED_BY, LATEST_CHECKED_DATE, VERIFIED_BY, VERIFIED_DATE
                                                FROM        CS_FP
                                                ORDER BY    YEARMONTH DESC, CHECKED_WEEK DESC
                                            ) MP
                                            JOIN
                                            CM_CS_FP_KTS_LIST KL ON MP.KTS_NO = KL.KTS_NO AND MP.ESTABLISHED_WEEK = KL.ESTABLISHED_WEEK
                            ) AS MP
                            LEFT JOIN
                            (SELECT * FROM CS_FP_RECORDS WHERE RESULT IS NOT NULL AND RESULT > 0) PR
                                ON MP.KTS_NO = PR.KTS_NO
                                AND MP.ESTABLISHED_WEEK = PR.ESTABLISHED_WEEK
                                AND MP.YEARMONTH = PR.YEARMONTH
                                AND MP.CHECKED_WEEK = PR.CHECKED_WEEK
                                AND MP.EQUIPMENT_ID = PR.EQUIPMENT_ID
                                AND MP.CHECK_ID = PR.CHECK_ID
                            LEFT JOIN
                            CS_FP_MANAGER_SIGNATURE MS
                                ON MP.KTS_NO = MS.KTS_NO
                                AND MP.ESTABLISHED_WEEK = MS.ESTABLISHED_WEEK
                                AND MP.YEARMONTH = MS.YEARMONTH
                            LEFT JOIN
                            EMPLOYEE CF
                                ON MP.CHECKED_BY = CF.EMPLOYEE_ID
                            LEFT JOIN
                            EMPLOYEE CL
                                ON MP.LATEST_CHECKED_BY = CL.EMPLOYEE_ID
                            LEFT JOIN
                            EMPLOYEE CV
                                ON MP.VERIFIED_BY = CV.EMPLOYEE_ID
                            LEFT JOIN
                            EMPLOYEE CM
                                ON MS.MANAGER_ID = CM.EMPLOYEE_ID
            GROUP BY
                        MP.KTS_NO,
                        MP.ESTABLISHED_WEEK,
                        MP.YEARMONTH,
                        MP.CHECKED_WEEK,
                        MP.CHECKED_BY,
                        CF.EMPLOYEE_NAME,
                        MP.CHECKED_DATE,
                        MP.LATEST_CHECKED_BY,
                        CL.EMPLOYEE_NAME,
                        MP.LATEST_CHECKED_DATE,
                        MP.VERIFIED_BY,
                        CV.EMPLOYEE_NAME,
                        MP.VERIFIED_DATE,
                        CM.EMPLOYEE_NAME,
                        MS.MANAGER_ID,
                        MS.APPROVED_DATE
            ORDER BY
                        MP.CHECKED_DATE DESC
            """.trimIndent()

            preparedStatement = connection.prepareStatement(sql)
            resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                val ktsNo = resultSet.getString("KTS_NO")
                val establishedWeek = resultSet.getString("ESTABLISHED_WEEK")
                val yearMonthFormatted = resultSet.getString("YEARMONTH_FORMATTED")
                val checkedWeek = resultSet.getInt("CHECKED_WEEK")
                val checkedBy = resultSet.getString("CHECKED_BY")
                val checkedDate = resultSet.getString("CHECKED_DATE")
                val latestCheckedBy = resultSet.getString("LATEST_CHECKED_BY")
                val latestCheckedDate = resultSet.getString("LATEST_CHECKED_DATE")
                val checkedItems = resultSet.getInt("CHECKED_ITEMS")
                val totalItems = resultSet.getInt("TOTAL_ITEMS")
                val verifiedBy = resultSet.getString("VERIFIED_BY")
                val verifiedDate = resultSet.getString("VERIFIED_DATE")
                val approvedBy = resultSet.getString("APPROVED_BY")
                val approvedDate = resultSet.getString("APPROVED_DATE")
                savedForms.add(SavedFormHeader(ktsNo, establishedWeek, yearMonthFormatted, checkedWeek, checkedBy, checkedDate, latestCheckedBy, latestCheckedDate, checkedItems, totalItems,verifiedBy,verifiedDate,approvedBy,approvedDate))
            }
            Log.d(TAG, "Fetched ${savedForms.size} saved form headers.")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getSavedFormHeaders: ${e.message}", e)
            //Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getSavedFormHeaders: ${e.message}")
            }
        }
        return@withContext savedForms
    }
}

/*
package com.kemtdm.mt01.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.kemtdm.mt01.sql.GetConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.SQLException

object DatabaseHelper {

    private const val TAG = "DatabaseHelper"

    private fun getUnitName(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("LoginData", Context.MODE_PRIVATE)
        return sharedPreferences.getString("unitName", "SLT1") ?: "SLT1"
    }
    suspend fun getLatestKtsNo(context: Context, yearMonth: String): String? = withContext(Dispatchers.IO) {
        var ktsNo: String? = null
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for getLatestKtsNo.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext null
            }

            val unitName = getUnitName(context)

            // Query to get the latest KTS_NO for the specified month
            val sql = "SELECT KTS_NO FROM CM_CS_PM_KTS WHERE UNIT_NAME = ? ORDER BY ESTABLISHED_DATE DESC"
            preparedStatement = connection.prepareStatement(sql)
            preparedStatement.setString(1, unitName)
            resultSet = preparedStatement.executeQuery()

            if (resultSet.next()) {
                ktsNo = resultSet.getString("KTS_NO")
                Log.d(TAG, "Found the latest KTS_NO: $ktsNo for $yearMonth of UNIT_NAME: $unitName")
            } else {
                Log.w(TAG, "No KTS_NO found for $yearMonth")
                Toast.makeText(context, "ไม่พบ KTS_NO สำหรับเดือนนี้", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getLatestKtsNo: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLatestKtsNo: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการดึง KTS_NO", Toast.LENGTH_LONG).show()
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getLatestKtsNo: ${e.message}")
            }
        }
        return@withContext ktsNo
    }

    suspend fun getAllEquipment(context: Context): List<Equipment> = withContext(Dispatchers.IO) {
        val equipmentList = mutableListOf<Equipment>()
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for getAllEquipment.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext emptyList()
            }
            val unitName = getUnitName(context)

            val sql = "SELECT EQUIPMENT_ID, EQUIPMENT_NAME FROM CM_CS_PM_EQ WHERE UNIT_NAME = ? ORDER BY EQUIPMENT_ID ASC"
            preparedStatement = connection.prepareStatement(sql)
            preparedStatement.setString(1, unitName)
            resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                val id = resultSet.getInt("EQUIPMENT_ID")
                val name = resultSet.getString("EQUIPMENT_NAME")
                equipmentList.add(Equipment(id, name))
            }
            Log.d(TAG, "Fetched ${equipmentList.size} Equipment items of UNIT_NAME: $unitName.")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getAllEquipment: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAllEquipment: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการดึงข้อมูลอุปกรณ์", Toast.LENGTH_LONG).show()
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getAllEquipment: ${e.message}")
            }
        }
        return@withContext equipmentList
    }

    suspend fun getChecklistItems(context: Context, ktsNo: String, equipmentId: Int, yearMonth: String, checkedWeek: Int): List<ChecklistItem> = withContext(Dispatchers.IO) {
        val checklistItems = mutableListOf<ChecklistItem>()
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for getChecklistItems.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext emptyList()
            }
            val unitName = getUnitName(context)

            val sql = """

            DECLARE @UNIT_NAME VARCHAR(10) = ?
            DECLARE @KTS_NO VARCHAR(30) = ?
            DECLARE @EQUIPMENT_ID INT = ?
            DECLARE @YEARMONTH VARCHAR(6) = ?
            DECLARE @CHECKED_WEEK INT = ?

            SELECT
                KL.KTS_NO,
                KL.EQUIPMENT_ID,
                KL.CHECK_ID,
                KL.CHECK_POINT,
                KL.CHECK_PERIOD,
                KL.SELECTED_WEEK,
                COALESCE(PR.RESULT,0) RESULT,
                ADDITIONAL_NOTE
            FROM (
                SELECT * FROM CM_CS_PM_KTS_LIST
                WHERE UNIT_NAME = @UNIT_NAME AND KTS_NO = @KTS_NO AND EQUIPMENT_ID = @EQUIPMENT_ID
            ) KL
            LEFT JOIN (
                SELECT * FROM CS_MC_PM_RECORDS
                WHERE UNIT_NAME = @UNIT_NAME AND YEARMONTH = @YEARMONTH AND CHECKED_WEEK = @CHECKED_WEEK
            ) PR
            ON
                KL.UNIT_NAME = PR.UNIT_NAME AND
                KL.KTS_NO = PR.KTS_NO AND
                KL.EQUIPMENT_ID = PR.EQUIPMENT_ID AND
                KL.CHECK_ID = PR.CHECK_ID
            ORDER BY KL.CHECK_ID ASC
        """.trimIndent()
            preparedStatement = connection.prepareStatement(sql)

            var parameterIndex =1

            preparedStatement.setString(parameterIndex++, unitName)
            preparedStatement.setString(parameterIndex++, ktsNo)
            preparedStatement.setInt(parameterIndex++, equipmentId)
            preparedStatement.setString(parameterIndex++, yearMonth)
            preparedStatement.setInt(parameterIndex, checkedWeek)

            resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                val kts = resultSet.getString("KTS_NO")
                val eqId = resultSet.getInt("EQUIPMENT_ID")
                val checkId = resultSet.getInt("CHECK_ID")
                val checkPoint = resultSet.getString("CHECK_POINT")
                val checkPeriod = resultSet.getInt("CHECK_PERIOD")
                val selectedWeek = resultSet.getInt("SELECTED_WEEK")
                val selectedResult = resultSet.getInt("RESULT")
                val additionalNote = resultSet.getString("ADDITIONAL_NOTE")
                checklistItems.add(ChecklistItem(kts, eqId, checkId, checkPoint, checkPeriod, selectedWeek,selectedResult, additionalNote))
            }
            Log.d(TAG, "Fetched ${checklistItems.size} checklist items for UNIT_NAME: $unitName, KTS_NO: $ktsNo, Equipment ID: $equipmentId")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getChecklistItems: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getChecklistItems: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการดึงรายการตรวจสอบ", Toast.LENGTH_LONG).show()
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getChecklistItems: ${e.message}")
            }
        }
        return@withContext checklistItems
    }

    suspend fun savePMHeader(context: Context, yearMonth: String, checkedWeek: Int, ktsNo: String, userId: String, pi: String, dv: String) = withContext(Dispatchers.IO) {
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for savePMHeader.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext
            }

            val unitName = getUnitName(context)

            // Use a MERGE statement to perform a single-query 'upsert' (update or insert).
            val mergeSql = """
                DECLARE @NOW DATETIME = GETDATE()

                DECLARE @YEARMONTH VARCHAR(6) = ?
                DECLARE @CHECKED_WEEK INT = ?
                DECLARE @KTS_NO VARCHAR(30) = ?
                DECLARE @UNIT_NAME VARCHAR(10) = ?
                DECLARE @UI VARCHAR(6) = ?
                DECLARE @PI VARCHAR(6) = ?
                DECLARE @DV VARCHAR(60) = ?

                MERGE INTO CS_MC_PM AS Target
                USING (VALUES (@YEARMONTH, @CHECKED_WEEK, @KTS_NO, @UNIT_NAME)) AS Source (YearMonth, CheckedWeek, KTS_NO, UNIT_NAME)
                ON Target.YEARMONTH = Source.YearMonth AND Target.CHECKED_WEEK = Source.CheckedWeek AND Target.KTS_NO = Source.KTS_NO AND Target.UNIT_NAME = Source.UNIT_NAME
                WHEN MATCHED THEN
                    UPDATE SET
                        LATEST_CHECKED_BY = @UI,
                        LATEST_CHECKED_DATE = @NOW,
                        LPI = @PI,
                        LDV = @DV
                WHEN NOT MATCHED THEN
                    INSERT (YEARMONTH, CHECKED_WEEK, KTS_NO, UNIT_NAME, CHECKED_BY, CHECKED_DATE, PI, DV)
                    VALUES (@YEARMONTH, @CHECKED_WEEK, @KTS_NO, @UNIT_NAME, @UI, @NOW, @PI, @DV);
            """.trimIndent()

            preparedStatement = connection.prepareStatement(mergeSql)

            var parameterIndex = 1

            preparedStatement.setString(parameterIndex++, yearMonth)
            preparedStatement.setInt(parameterIndex++, checkedWeek)
            preparedStatement.setString(parameterIndex++, ktsNo)
            preparedStatement.setString(parameterIndex++, unitName)
            preparedStatement.setString(parameterIndex++, userId)
            preparedStatement.setString(parameterIndex++, pi)
            preparedStatement.setString(parameterIndex, dv)

            preparedStatement.executeUpdate()
            Log.d(TAG, "Successfully executed 'upsert' on header data using MERGE statement: YEARMONTH=$yearMonth, CHECKED_WEEK=$checkedWeek, KTS_NO=$ktsNo, UNIT_NAME=$unitName")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in savePMHeader: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in savePMHeader: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดในการบันทึกข้อมูลส่วนหัว", Toast.LENGTH_LONG).show()
        } finally {
            try {
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in savePMHeader: ${e.message}")
            }
        }
    }

    suspend fun saveChecklistRecord(context: Context, record: ChecklistItem, userId: String, pi: String, dv: String) = withContext(Dispatchers.IO) {
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for saveChecklistRecord.")
                Toast.makeText(context, "การเชื่อมต่อฐานข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                return@withContext
            }
            val unitName = getUnitName(context)

            // Use a MERGE statement to perform a single-query 'upsert' (update or insert).
            // This statement checks for an existing record and either updates it or inserts a new one.
            val mergeSql = """
            DECLARE @NOW DATETIME = GETDATE()
            DECLARE @YEARMONTH VARCHAR(6) = ?
            DECLARE @CHECKED_WEEK INT = ?
            DECLARE @KTS_NO VARCHAR(30) = ?
            DECLARE @UNIT_NAME VARCHAR(10) = ?
            DECLARE @EQUIPMENT_ID INT = ?
            DECLARE @CHECK_ID INT = ?
            DECLARE @RESULT INT = ?
            DECLARE @ADDITIONAL_NOTE NVARCHAR(200) = ?
            DECLARE @UI VARCHAR(6) = ?
            DECLARE @PI VARCHAR(6) = ?
            DECLARE @DV VARCHAR(60) = ?

            MERGE INTO CS_MC_PM_RECORDS AS Target
            USING (VALUES (@YEARMONTH, @CHECKED_WEEK, @KTS_NO, @UNIT_NAME, @EQUIPMENT_ID, @CHECK_ID)) AS Source (YearMonth, CheckedWeek, KTS_NO, UNIT_NAME, EquipmentId, CheckId)
            ON Target.UNIT_NAME = Source.UNIT_NAME AND Target.YEARMONTH = Source.YearMonth AND Target.CHECKED_WEEK = Source.CheckedWeek AND Target.KTS_NO = Source.KTS_NO AND Target.EQUIPMENT_ID = Source.EquipmentId AND Target.CHECK_ID = Source.CheckId
            WHEN MATCHED THEN
                UPDATE SET
                    RESULT = @RESULT,
                    ADDITIONAL_NOTE = @ADDITIONAL_NOTE,
                    LUI = @UI,
                    LPI = @PI,
                    LDT = @NOW,
                    LDV = @DV
            WHEN NOT MATCHED THEN
                INSERT (UNIT_NAME, YEARMONTH, CHECKED_WEEK, KTS_NO, EQUIPMENT_ID, CHECK_ID, CHECKED_DATE, RESULT, ADDITIONAL_NOTE, UI, PI, DT, DV)
                VALUES (@UNIT_NAME, @YEARMONTH, @CHECKED_WEEK, @KTS_NO, @EQUIPMENT_ID, @CHECK_ID, @NOW, @RESULT, @ADDITIONAL_NOTE, @UI, @PI, @NOW, @DV);
        """.trimIndent()

            preparedStatement = connection.prepareStatement(mergeSql)

            // Use a variable to help count parameter order
            var parameterIndex = 1

            // Set parameters for the 'USING' clause (Source table)
            // These parameters are used to check if a matching record exists.
            preparedStatement.setString(parameterIndex++, record.yearMonth)
            preparedStatement.setInt(parameterIndex++, record.checkedWeek)
            preparedStatement.setString(parameterIndex++, record.ktsNo)
            preparedStatement.setString(parameterIndex++, unitName)
            preparedStatement.setInt(parameterIndex++, record.equipmentId)
            preparedStatement.setInt(parameterIndex++, record.checkId)
            preparedStatement.setInt(parameterIndex++, record.selectedResult)
            preparedStatement.setString(parameterIndex++, record.additionalNote ?: "")
            preparedStatement.setString(parameterIndex++, userId)
            preparedStatement.setString(parameterIndex++, pi)
            preparedStatement.setString(parameterIndex, dv)



            preparedStatement.executeUpdate()
            Log.d(TAG, "ดำเนินการ 'upsert' รายการตรวจสอบสำเร็จด้วย MERGE statement: UNIT_NAME=$unitName, YEARMONTH=${record.yearMonth}, CHECKED_WEEK=${record.checkedWeek}, KTS_NO=${record.ktsNo}, EQUIPMENT_ID=${record.equipmentId}, CHECK_ID=${record.checkId}")

        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception ใน saveChecklistRecord: ${e.message}", e)
            Toast.makeText(context, "เกิดข้อผิดพลาดทาง SQL: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "ข้อผิดพลาดในการปิด resources ใน saveChecklistRecord: ${e.message}")
            }
        }
    }

    */
/**
     * Fetches a list of saved PM checklist headers with their item counts.
     * This function uses a complex SQL query to get aggregated data.
     * @return A list of SavedFormHeader objects.
     *//*

    suspend fun getSavedFormHeaders(context: Context): List<SavedFormHeader> = withContext(Dispatchers.IO) {
        val savedForms = mutableListOf<SavedFormHeader>()
        var connection: java.sql.Connection?
        var preparedStatement: java.sql.PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(TAG, "Database connection is null for getSavedFormHeaders.")
                return@withContext emptyList()
            }
            val unitName = getUnitName(context)

            val sql = """
            DECLARE @UNIT_NAME VARCHAR(10) = ?;

            WITH EMPLOYEE AS (
                SELECT
                    E.EMPLOYEE_ID,
                    E.EMPLOYEE_NAME
                FROM CM_EMPLOYEE E
            )

            SELECT          MP.KTS_NO,
                            FORMAT(CAST(MP.YEARMONTH + '01' AS DATE), 'MMM yyyy', 'en-us') AS YEARMONTH_FORMATTED,
                            MP.CHECKED_WEEK,
                            CF.EMPLOYEE_NAME + ' (' + MP.CHECKED_BY + ')' CHECKED_BY,
                            MP.CHECKED_DATE,
                            CL.EMPLOYEE_NAME + ' (' + MP.LATEST_CHECKED_BY + ')' LATEST_CHECKED_BY,
                            MP.LATEST_CHECKED_DATE,
                            CV.EMPLOYEE_NAME + ' (' + MP.VERIFIED_BY + ')' VERIFIED_BY,
                            MP.VERIFIED_DATE,
                            CM.EMPLOYEE_NAME + ' (' + MS.MANAGER_ID + ')' APPROVED_BY,
                            MS.APPROVED_DATE,
                            COUNT(PR.RESULT) AS CHECKED_ITEMS,
                            SUM(MP.ON_WEEK) AS TOTAL_ITEMS
            FROM            (
                                SELECT      MP.UNIT_NAME,
                                            MP.KTS_NO,
                                            MP.YEARMONTH,
                                            MP.CHECKED_WEEK,
                                            MP.CHECKED_BY,
                                            MP.CHECKED_DATE,
                                            MP.LATEST_CHECKED_BY,
                                            MP.LATEST_CHECKED_DATE,
                                            KL.EQUIPMENT_ID,
                                            KL.CHECK_ID,
                                            MP.VERIFIED_BY,
                                            MP.VERIFIED_DATE,
                                            CASE WHEN KL.CHECK_PERIOD = 1 THEN 1 ELSE CASE WHEN KL.CHECK_PERIOD = 2 AND MP.CHECKED_WEEK = KL.SELECTED_WEEK THEN 1 ELSE 0 END END AS ON_WEEK
                                FROM        (   SELECT      TOP 54
                                                            UNIT_NAME, KTS_NO, YEARMONTH, CHECKED_WEEK, CHECKED_BY, CHECKED_DATE, LATEST_CHECKED_BY, LATEST_CHECKED_DATE, VERIFIED_BY, VERIFIED_DATE
                                                FROM        CS_MC_PM
                                                WHERE       UNIT_NAME = @UNIT_NAME
                                            ) MP
                                            JOIN
                                            CM_CS_PM_KTS_LIST KL ON MP.UNIT_NAME = KL.UNIT_NAME AND MP.KTS_NO = KL.KTS_NO
                            ) AS MP
                            LEFT JOIN
                            (SELECT * FROM CS_MC_PM_RECORDS WHERE UNIT_NAME = @UNIT_NAME AND RESULT IS NOT NULL AND RESULT > 0) PR
                                ON MP.UNIT_NAME = PR.UNIT_NAME
                                AND MP.KTS_NO = PR.KTS_NO
                                AND MP.YEARMONTH = PR.YEARMONTH
                                AND MP.CHECKED_WEEK = PR.CHECKED_WEEK
                                AND MP.EQUIPMENT_ID = PR.EQUIPMENT_ID
                                AND MP.CHECK_ID = PR.CHECK_ID
                            LEFT JOIN
                            CS_MC_PM_MANAGER_SIGNATURE MS
                                ON MP.UNIT_NAME = MS.UNIT_NAME
                                AND MP.YEARMONTH = MS.YEARMONTH
                                AND MP.KTS_NO = MS.KTS_NO
                            LEFT JOIN
                            EMPLOYEE CF
                                ON MP.CHECKED_BY = CF.EMPLOYEE_ID
                            LEFT JOIN
                            EMPLOYEE CL
                                ON MP.LATEST_CHECKED_BY = CL.EMPLOYEE_ID
                            LEFT JOIN
                            EMPLOYEE CV
                                ON MP.VERIFIED_BY = CV.EMPLOYEE_ID
                            LEFT JOIN
                            EMPLOYEE CM
                                ON MS.MANAGER_ID = CM.EMPLOYEE_ID
            GROUP BY
                        MP.KTS_NO,
                        MP.YEARMONTH,
                        MP.CHECKED_WEEK,
                        MP.CHECKED_BY,
                        CF.EMPLOYEE_NAME,
                        MP.CHECKED_DATE,
                        MP.LATEST_CHECKED_BY,
                        CL.EMPLOYEE_NAME,
                        MP.LATEST_CHECKED_DATE,
                        MP.VERIFIED_BY,
                        CV.EMPLOYEE_NAME,
                        MP.VERIFIED_DATE,
                        CM.EMPLOYEE_NAME,
                        MS.MANAGER_ID,
                        MS.APPROVED_DATE
            ORDER BY
                        MP.CHECKED_DATE DESC
            """.trimIndent()

            preparedStatement = connection.prepareStatement(sql)

            preparedStatement.setString(1, unitName)

            resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                val ktsNo = resultSet.getString("KTS_NO")
                val yearMonthFormatted = resultSet.getString("YEARMONTH_FORMATTED")
                val checkedWeek = resultSet.getInt("CHECKED_WEEK")
                val checkedBy = resultSet.getString("CHECKED_BY")
                val checkedDate = resultSet.getString("CHECKED_DATE") // ดึงเป็น String เพราะอาจเป็น NULL ได้
                val latestCheckedBy = resultSet.getString("LATEST_CHECKED_BY")
                val latestCheckedDate = resultSet.getString("LATEST_CHECKED_DATE") // ดึงเป็น String เพราะอาจเป็น NULL ได้
                val checkedItems = resultSet.getInt("CHECKED_ITEMS")
                val totalItems = resultSet.getInt("TOTAL_ITEMS")
                val verifiedBy = resultSet.getString("VERIFIED_BY")
                val verifiedDate = resultSet.getString("VERIFIED_DATE")
                val approvedBy = resultSet.getString("APPROVED_BY")
                val approvedDate = resultSet.getString("APPROVED_DATE")
                savedForms.add(SavedFormHeader(ktsNo, yearMonthFormatted, checkedWeek, checkedBy, checkedDate, latestCheckedBy, latestCheckedDate, checkedItems, totalItems,verifiedBy,verifiedDate,approvedBy,approvedDate))
            }
            Log.d(TAG, "Fetched ${savedForms.size} saved form headers with UNIT_NAME: $unitName.")
        } catch (e: SQLException) {
            Log.e(TAG, "SQL Exception in getSavedFormHeaders: ${e.message}", e)
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
            } catch (e: SQLException) {
                Log.e(TAG, "Error closing resources in getSavedFormHeaders: ${e.message}")
            }
        }
        return@withContext savedForms
    }
}

*/
