package com.kemtdm.mt01.sql

import android.content.Context
import android.util.Log
import com.kemtdm.mt01.utils.PasswordEncryption
import org.json.JSONObject

object SqlConnectionVariable {

    private const val TAG = "tagSqlConnectionVariable"
    private const val PREFS_NAME = "ConnectionSettings"

    // Database Settings
    var serverIp: String = "192.168.100.7"
    var serverPort: String = "1433"
    var databaseName: String = "V2_KCOP"
    var userName: String = "sa"
    var password: String = "Sql!@#$"

    // SMTP Settings
    var smtpServer: String = "smtpm.kobe-emt.co.th"
    var smtpPort: String = "25"
    var smtpEnableSsl: Boolean = false
    var smtpUser: String = "kcop@kobe-emt.co.th"
    var smtpPassword: String = "Kobelco.com2024"
    var notificationRecipient: String = "kirati.sitthiprasert@kobelco.com"

    var teamsWebhookUrl: String = ""
    const val MSSQL_TRUST = "encrypt=false;trustServerCertificate=true;"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        if (!prefs.contains("server_ip")) {
            try {
                val jsonString = context.assets.open("config.json").bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                serverIp = jsonObject.optString("server_ip", serverIp)
                serverPort = jsonObject.optString("server_port", serverPort)
                databaseName = jsonObject.optString("database", databaseName)
                userName = jsonObject.optString("user", userName)
                password = jsonObject.optString("password", password)
                teamsWebhookUrl = jsonObject.optString("teams_webhook_url", teamsWebhookUrl)
                
                smtpServer = jsonObject.optString("SmtpServer", smtpServer)
                smtpPort = jsonObject.optString("SmtpPort", smtpPort)
                smtpEnableSsl = jsonObject.optBoolean("EnableSsl", smtpEnableSsl)
                smtpUser = jsonObject.optString("SmtpClientCredentials", smtpUser)
                smtpPassword = jsonObject.optString("PasswordForAccount", smtpPassword)
                notificationRecipient = jsonObject.optString("NotificationRecipient", notificationRecipient)

                // Phase 1 Audit: Save defaults with encryption
                saveSettings(
                    context, serverIp, serverPort, databaseName, userName, password, 
                    teamsWebhookUrl, smtpServer, smtpPort, smtpEnableSsl, smtpUser, smtpPassword, notificationRecipient
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading config.json", e)
            }
        } else {
            serverIp = prefs.getString("server_ip", serverIp) ?: serverIp
            serverPort = prefs.getString("server_port", serverPort) ?: serverPort
            databaseName = prefs.getString("database", databaseName) ?: databaseName
            userName = prefs.getString("user", userName) ?: userName
            
            // Phase 2 Audit: Strict Decryption for SQL Password
            val encryptedSqlPass = prefs.getString("password", "") ?: ""
            password = if (encryptedSqlPass.isNotEmpty()) {
                PasswordEncryption.decryptPassword(encryptedSqlPass)
            } else {
                ""
            }

            teamsWebhookUrl = prefs.getString("teams_webhook_url", teamsWebhookUrl) ?: teamsWebhookUrl

            smtpServer = prefs.getString("smtp_server", smtpServer) ?: smtpServer
            smtpPort = prefs.getString("smtp_port", smtpPort) ?: smtpPort
            smtpEnableSsl = prefs.getBoolean("smtp_ssl", smtpEnableSsl)
            smtpUser = prefs.getString("smtp_user", smtpUser) ?: smtpUser
            notificationRecipient = prefs.getString("notification_recipient", notificationRecipient) ?: notificationRecipient

            // Phase 2 Audit: Strict Decryption for SMTP Password
            val encryptedSmtpPass = prefs.getString("smtp_password", "") ?: ""
            smtpPassword = if (encryptedSmtpPass.isNotEmpty()) {
                PasswordEncryption.decryptPassword(encryptedSmtpPass)
            } else {
                ""
            }
        }
    }

    fun saveSettings(
        context: Context, 
        ip: String, port: String, db: String, user: String, pass: String, 
        webhookUrl: String = teamsWebhookUrl,
        sServer: String = smtpServer,
        sPort: String = smtpPort,
        sSsl: Boolean = smtpEnableSsl,
        sUser: String = smtpUser,
        sPass: String = smtpPassword,
        nRecipient: String = notificationRecipient
    ) {
        serverIp = ip
        serverPort = port
        databaseName = db
        userName = user
        password = pass
        teamsWebhookUrl = webhookUrl
        smtpServer = sServer
        smtpPort = sPort
        smtpEnableSsl = sSsl
        smtpUser = sUser
        smtpPassword = sPass
        notificationRecipient = nRecipient

        // Phase 1 Audit: Encrypt before committing to SharedPreferences
        val encryptedSqlPass = PasswordEncryption.encryptPassword(pass)
        val encryptedSmtpPass = PasswordEncryption.encryptPassword(sPass)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("server_ip", ip)
            putString("server_port", port)
            putString("database", db)
            putString("user", user)
            putString("password", encryptedSqlPass)
            putString("teams_webhook_url", webhookUrl)
            putString("smtp_server", sServer)
            putString("smtp_port", sPort)
            putBoolean("smtp_ssl", sSsl)
            putString("smtp_user", sUser)
            putString("smtp_password", encryptedSmtpPass)
            putString("notification_recipient", nRecipient)
            apply()
        }
    }

    fun getMSSQLUrl(useTrust: Boolean = false): String {
        val trustPart = if (useTrust) ";$MSSQL_TRUST" else ""
        val url = "jdbc:jtds:sqlserver://$serverIp:$serverPort;databaseName=$databaseName;user=$userName;password=$password$trustPart"
        return url
    }
}
