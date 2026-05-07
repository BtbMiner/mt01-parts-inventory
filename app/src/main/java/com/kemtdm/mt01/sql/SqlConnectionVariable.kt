package com.kemtdm.mt01.sql

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.IOException

object SqlConnectionVariable {

    private const val TAG = "tagSqlConnectionVariable"
    private const val PREFS_NAME = "ConnectionSettings"

    var serverIp: String = "192.168.100.7"
    var serverPort: String = "1433"
    var databaseName: String = "V1_KCOP"
    var userName: String = "sa"
    var password: String = "wwwww"

    const val MSSQL_TRUST = "encrypt=false;trustServerCertificate=true;"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        if (!prefs.contains("server_ip")) {
            // Load defaults from config.json if not in SharedPreferences
            try {
                val jsonString = context.assets.open("config.json").bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                serverIp = jsonObject.optString("server_ip", serverIp)
                serverPort = jsonObject.optString("server_port", serverPort)
                databaseName = jsonObject.optString("database", databaseName)
                userName = jsonObject.optString("user", userName)
                password = jsonObject.optString("password", password)
                
                // Save these to prefs
                saveSettings(context, serverIp, serverPort, databaseName, userName, password)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading config.json", e)
            }
        } else {
            serverIp = prefs.getString("server_ip", serverIp) ?: serverIp
            serverPort = prefs.getString("server_port", serverPort) ?: serverPort
            databaseName = prefs.getString("database", databaseName) ?: databaseName
            userName = prefs.getString("user", userName) ?: userName
            password = prefs.getString("password", password) ?: password
        }
    }

    fun saveSettings(context: Context, ip: String, port: String, db: String, user: String, pass: String) {
        serverIp = ip
        serverPort = port
        databaseName = db
        userName = user
        password = pass

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("server_ip", ip)
            putString("server_port", port)
            putString("database", db)
            putString("user", user)
            putString("password", pass)
            apply()
        }
    }

    fun getMSSQLUrl(useTrust: Boolean = false): String {
        val trustPart = if (useTrust) ";$MSSQL_TRUST" else ""
        val url = "jdbc:jtds:sqlserver://$serverIp:$serverPort;databaseName=$databaseName;user=$userName;password=$password$trustPart"
        Log.d(TAG, "Generated Connection URL (useTrust=$useTrust): $url")
        return url
    }
}