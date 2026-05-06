package com.kemtdm.mt01.database

import android.util.Log
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object MSSqlConnectionClass {

    private const val TAG = "tagMSSqlConnectionClass"

    fun mssqlConnection(connectionURL: String): Connection? {
        var connection: Connection? = null
        try {
            // เปลี่ยน Driver Class จาก mssql-jdbc เป็น jtds
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            Log.i(TAG, "Attempting to connect with URL: $connectionURL")
            connection = DriverManager.getConnection(connectionURL)
            Log.i(TAG, "Connection successful.")
        } catch (se: SQLException) {
            Log.e(TAG, "SQL Exception: ${se.message}", se)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Class Not Found Exception: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "General Exception: ${e.message}", e)
        }
        return connection
    }
}