package com.kemtdm.mt01.sql

import android.util.Log
import com.kemtdm.mt01.database.MSSqlConnectionClass
import java.sql.Connection

// Use 'object' to create a Singleton. This ensures there is only one instance of this class.
object GetConnection {
    // Define a tag for logging purposes, making it easy to filter logs from this class.
    private const val TAG = "tagGetConnection"

    // Use a 'val' property to get a database Connection instance.
    // The 'get()' block is a custom getter that is executed every time 'connection' is accessed.
    val connection: Connection?
        get() {
            // Log a message indicating the start of the connection attempt.
            Log.d(TAG, "Attempting to get database connection.")
            // Call the mssqlConnection method from MSSqlConnectionClass,
            // passing the full URL to establish a connection.
            val conn = MSSqlConnectionClass.mssqlConnection(SqlConnectionVariable.getMSSQLUrl(false))
            // Check if the returned connection is not null.
            if (conn != null) {
                // If the connection is successful, log a success message.
                Log.d(TAG, "Connection successful.")
            } else {
                // If the connection is null, it means the connection failed.
                // Log an error message to highlight the failure.
                Log.e(TAG, "Connection failed. Returned null.")
            }
            return conn
        }
}