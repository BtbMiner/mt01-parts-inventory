package com.kemtdm.mt01.activity

import android.content.Context
import android.content.Intent
import android.Manifest
import android.os.Bundle
import android.util.Log
import com.kemtdm.mt01.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.google.android.material.snackbar.Snackbar
import android.provider.Settings
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kemtdm.mt01.R
import com.kemtdm.mt01.sql.GetConnection
import com.kemtdm.mt01.sql.SqlConnectionVariable
import com.kemtdm.mt01.utils.LanguageManager
import com.kemtdm.mt01.utils.PasswordEncryption
import java.sql.Connection

class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val logTAG = "tagLoginActivity"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLocale(newBase))
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            handlePermissionResult(isGranted)
        }

    private val settingsLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            checkAndRequestLocationPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Connection Settings (Decrypts stored passwords)
        SqlConnectionVariable.initialize(this)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set dynamic titles and info
        binding.appTitleTextView.text = getString(R.string.app_login_title)
        updateConnectionLabels()

        Log.d(logTAG, "LoginActivity created. Setting up click listener.")

        binding.loginButton.setOnClickListener {
            Log.d(logTAG, "Login button clicked.")
            val userId = binding.usernameEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (userId.isEmpty() || password.isEmpty()) {
                Toast.makeText(this@LoginActivity, R.string.login_error_empty, Toast.LENGTH_SHORT).show()
                Log.d(logTAG, "Username or password is empty.")
            } else {
                Log.d(logTAG, "Starting authentication for user: $userId")
                CoroutineScope(Dispatchers.IO).launch {
                    val authResult = authenticateUser(userId, password)

                    withContext(Dispatchers.Main) {
                        when (authResult) {
                            AuthenticationResult.Success -> {
                                handleLoginSuccess()
                            }
                            AuthenticationResult.InvalidCredentials -> {
                                Toast.makeText(this@LoginActivity, R.string.login_error_invalid, Toast.LENGTH_SHORT).show()
                            }
                            AuthenticationResult.ConnectionError -> {
                                Toast.makeText(this@LoginActivity, R.string.login_error_connection, Toast.LENGTH_LONG).show()
                            }
                            AuthenticationResult.Error -> {
                                Toast.makeText(this@LoginActivity, R.string.login_error_general, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        binding.connectionSettingsButton.setOnClickListener {
            showPasswordVerificationDialog()
        }
    }

    private fun updateConnectionLabels() {
        binding.serverIpTextView.text = getString(R.string.server_ip_format, SqlConnectionVariable.serverIp)
        binding.databaseNameTextView.text = getString(R.string.database_name_format, SqlConnectionVariable.databaseName)
    }

    private fun showPasswordVerificationDialog() {
        val input = TextInputEditText(this)
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val margin = (16 * resources.displayMetrics.density).toInt()
        layoutParams.setMargins(margin, 0, margin, 0)
        input.layoutParams = layoutParams
        input.hint = getString(R.string.verification_hint)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.verification_title)
            .setView(container)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (input.text.toString() == "pioflife") {
                    showConnectionSettingsDialog()
                } else {
                    Toast.makeText(this@LoginActivity, R.string.verification_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }


    private fun showConnectionSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connection_settings, null)
        val ipEditText = dialogView.findViewById<TextInputEditText>(R.id.ip_edit_text)
        val portEditText = dialogView.findViewById<TextInputEditText>(R.id.port_edit_text)
        val dbEditText = dialogView.findViewById<TextInputEditText>(R.id.db_edit_text)
        val userEditText = dialogView.findViewById<TextInputEditText>(R.id.user_edit_text)
        val passEditText = dialogView.findViewById<TextInputEditText>(R.id.pass_edit_text)
        val webhookEditText = dialogView.findViewById<TextInputEditText>(R.id.webhook_edit_text)

        val smtpServerEditText = dialogView.findViewById<TextInputEditText>(R.id.smtp_server_edit_text)
        val smtpPortEditText = dialogView.findViewById<TextInputEditText>(R.id.smtp_port_edit_text)
        val smtpSslSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.smtp_ssl_switch)
        val smtpUserEditText = dialogView.findViewById<TextInputEditText>(R.id.smtp_user_edit_text)
        val smtpPassEditText = dialogView.findViewById<TextInputEditText>(R.id.smtp_pass_edit_text)
        val notificationRecipientEditText = dialogView.findViewById<TextInputEditText>(R.id.notification_recipient_edit_text)

        // Phase 2 Audit: Load encrypted values from SharedPreferences and Decrypt
        val prefs = getSharedPreferences("ConnectionSettings", MODE_PRIVATE)
        val encryptedSqlPass = prefs.getString("password", "") ?: ""
        val encryptedSmtpPass = prefs.getString("smtp_password", "") ?: ""

        // Decrypt for UI display
        val decryptedSqlPass = PasswordEncryption.decryptPassword(encryptedSqlPass)
        val decryptedSmtpPass = PasswordEncryption.decryptPassword(encryptedSmtpPass)

        // Set current values to dialog fields
        ipEditText.setText(SqlConnectionVariable.serverIp)
        portEditText.setText(SqlConnectionVariable.serverPort)
        dbEditText.setText(SqlConnectionVariable.databaseName)
        userEditText.setText(SqlConnectionVariable.userName)
        passEditText.setText(decryptedSqlPass)
        webhookEditText.setText(SqlConnectionVariable.teamsWebhookUrl)

        smtpServerEditText.setText(SqlConnectionVariable.smtpServer)
        smtpPortEditText.setText(SqlConnectionVariable.smtpPort)
        smtpSslSwitch.isChecked = SqlConnectionVariable.smtpEnableSsl
        smtpUserEditText.setText(SqlConnectionVariable.smtpUser)
        smtpPassEditText.setText(decryptedSmtpPass)
        notificationRecipientEditText.setText(SqlConnectionVariable.notificationRecipient)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.connection_settings_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                Log.i("CryptoCheck", "Save Button Clicked")
                val ip = ipEditText.text.toString()
                val port = portEditText.text.toString()
                val db = dbEditText.text.toString()
                val user = userEditText.text.toString()
                val plainPass = passEditText.text.toString()
                val plainSmtpPass = smtpPassEditText.text.toString()
                
                Log.d("CryptoCheck", "Capturing plain text passwords from UI for Phase 1 Audit")

                if (ip.isNotEmpty() && port.isNotEmpty() && db.isNotEmpty() && user.isNotEmpty() && plainPass.isNotEmpty()) {
                    // Phase 1 Audit: saveSettings handles encryption before committing to SharedPreferences
                    SqlConnectionVariable.saveSettings(
                        this, ip, port, db, user, plainPass, 
                        webhookEditText.text.toString(),
                        smtpServerEditText.text.toString(),
                        smtpPortEditText.text.toString(),
                        smtpSslSwitch.isChecked,
                        smtpUserEditText.text.toString(),
                        plainSmtpPass,
                        notificationRecipientEditText.text.toString()
                    )
                    updateConnectionLabels() // Update labels after saving
                    Toast.makeText(this@LoginActivity, R.string.settings_saved_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@LoginActivity, R.string.settings_error_incomplete, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleLoginSuccess() {
        Snackbar.make(binding.root, R.string.login_success, Snackbar.LENGTH_SHORT).show()
        checkAndRequestLocationPermission()
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            navigateToMainActivity()
            return
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Snackbar.make(
                binding.root,
                R.string.permission_location_rationale,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.confirm) {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                .show()
        } else {
            Snackbar.make(
                binding.root,
                R.string.permission_location_manual,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.action_settings) {
                    openAppSettings()
                }
                .show()
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        settingsLauncher.launch(intent)
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            Log.d(logTAG, "Location permission granted. Login SUCCESS.")
            navigateToMainActivity()
        } else {
            Log.w(logTAG, "Location permission denied. Login FAILED.")
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return
            }
            Toast.makeText(this@LoginActivity, R.string.login_error_location_required, Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this@LoginActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun authenticateUser(userId: String, password: String): AuthenticationResult {
        Log.d(logTAG, "Running authenticateUser() function.")
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = GetConnection.connection
            if (connection == null) {
                Log.e(logTAG, "Connection is null. Returning ConnectionError.")
                return AuthenticationResult.ConnectionError
            }

            Log.d(logTAG, "Connection successful. Preparing statement.")
            val sql = "SELECT USER_ID, USER_NAME, ADMINISTRATOR_FLAG FROM CM_USER WHERE USER_ID = ? AND PASSWORD = ? AND (DISABLED = 0 OR DISABLED IS NULL)"
            preparedStatement = connection.prepareStatement(sql)
            preparedStatement.setString(1, userId)
            preparedStatement.setString(2, password)
            Log.d(logTAG, "Executing SQL Query for user: $userId")

            resultSet = preparedStatement.executeQuery()

            if (resultSet.next()) {
                Log.d(logTAG, "Found user in database. Checking permissions.")
                val user = resultSet.getString("USER_ID")
                val userName = resultSet.getString("USER_NAME")
                val isAdmin = resultSet.getBoolean("ADMINISTRATOR_FLAG")

                // Secondary check for program permission
                val programId = "MT01"
                val permSql = "SELECT AVAILABLE FROM CM_PERMISSION WHERE USER_ID = ? AND PROGRAM_ID = ? AND AVAILABLE = 1"
                
                val permPs = connection.prepareStatement(permSql)
                permPs.setString(1, user)
                permPs.setString(2, programId)
                
                val permRs = permPs.executeQuery()
                val hasPermission = permRs.next()
                
                permRs.close()
                permPs.close()

                return if (hasPermission) {
                    Log.d(logTAG, "Permission granted for $programId.")
                    saveUserPref(user, userName, isAdmin)
                    AuthenticationResult.Success
                } else {
                    Log.w(logTAG, "Permission denied for $programId.")
                    AuthenticationResult.InvalidCredentials
                }
            } else {
                Log.d(logTAG, "User not found or disabled.")
                return AuthenticationResult.InvalidCredentials
            }
        } catch (e: Exception) {
            Log.e(logTAG, "Exception during authentication: ${e.message}", e)
            return AuthenticationResult.Error
        } finally {
            try {
                resultSet?.close()
                preparedStatement?.close()
                connection?.close()
                Log.d(logTAG, "Resources closed.")
            } catch (e: Exception) {
                Log.e(logTAG, "Error closing resources: ${e.message}")
            }
        }
    }

    private fun saveUserPref(userId: String, userName: String, isAdmin: Boolean) {
        val sharedPreferences = getSharedPreferences("LoginData", MODE_PRIVATE)
        sharedPreferences.edit {
            putString("UserID", userId)
            putString("UserName",userName)
            putBoolean("IsAdmin",isAdmin)
        }
        Log.d(logTAG, "UserID '$userId' UserName '$userName' IsAdmin '$isAdmin' saved to SharedPreferences.")
    }

    sealed class AuthenticationResult {
        object Success : AuthenticationResult()
        object InvalidCredentials : AuthenticationResult()
        object ConnectionError : AuthenticationResult()
        object Error : AuthenticationResult()
    }
}
