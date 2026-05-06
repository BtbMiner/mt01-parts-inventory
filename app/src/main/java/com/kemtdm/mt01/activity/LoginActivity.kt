package com.kemtdm.mt01.activity

import android.content.Intent
import android.Manifest
import androidx.appcompat.app.AppCompatActivity
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
import java.sql.Connection

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val logTAG = "tagLoginActivity"

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
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
        
        // Initialize Connection Settings
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
                Toast.makeText(this@LoginActivity, "กรุณากรอกชื่อผู้ใช้และรหัสผ่าน", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(this@LoginActivity, "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                            }
                            AuthenticationResult.ConnectionError -> {
                                Toast.makeText(this@LoginActivity, "ไม่สามารถเชื่อมต่อฐานข้อมูลได้", Toast.LENGTH_LONG).show()
                            }
                            AuthenticationResult.Error -> {
                                Toast.makeText(this@LoginActivity, "เกิดข้อผิดพลาดในการตรวจสอบ", Toast.LENGTH_LONG).show()
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
        input.hint = "กรอกรหัสผ่านเพื่อเข้าถึงการตั้งค่า"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle("ยืนยันตัวตน")
            .setView(container)
            .setPositiveButton("ยืนยัน") { _, _ ->
                if (input.text.toString() == "pioflife") {
                    showConnectionSettingsDialog()
                } else {
                    Toast.makeText(this@LoginActivity, "รหัสผ่านไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }


    private fun showConnectionSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connection_settings, null)
        val ipEditText = dialogView.findViewById<TextInputEditText>(R.id.ip_edit_text)
        val portEditText = dialogView.findViewById<TextInputEditText>(R.id.port_edit_text)
        val dbEditText = dialogView.findViewById<TextInputEditText>(R.id.db_edit_text)
        val userEditText = dialogView.findViewById<TextInputEditText>(R.id.user_edit_text)
        val passEditText = dialogView.findViewById<TextInputEditText>(R.id.pass_edit_text)

        // Set current values
        ipEditText.setText(SqlConnectionVariable.serverIp)
        portEditText.setText(SqlConnectionVariable.serverPort)
        dbEditText.setText(SqlConnectionVariable.databaseName)
        userEditText.setText(SqlConnectionVariable.userName)
        passEditText.setText(SqlConnectionVariable.password)

        MaterialAlertDialogBuilder(this)
            .setTitle("ตั้งค่าการเชื่อมต่อ")
            .setView(dialogView)
            .setPositiveButton("บันทึก") { _, _ ->
                val ip = ipEditText.text.toString()
                val port = portEditText.text.toString()
                val db = dbEditText.text.toString()
                val user = userEditText.text.toString()
                val pass = passEditText.text.toString()

                if (ip.isNotEmpty() && port.isNotEmpty() && db.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                    SqlConnectionVariable.saveSettings(this, ip, port, db, user, pass)
                    updateConnectionLabels() // Update labels after saving
                    Toast.makeText(this@LoginActivity, "บันทึกการตั้งค่าแล้ว", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@LoginActivity, "กรุณากรอกข้อมูลให้ครบถ้วน", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun handleLoginSuccess() {
        Snackbar.make(binding.root,"เข้าสู่ระบบสำเร็จ",Snackbar.LENGTH_SHORT).show()
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
                "ต้องอนุญาตตำแหน่งเพื่อยืนยันจุดตรวจสอบ กรุณากด 'อนุญาต'",
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction("อนุญาต") {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                .show()
        } else {
            Snackbar.make(
                binding.root,
                "ไม่สามารถขอสิทธิ์ได้ กรุณาไปที่ตั้งค่าเพื่ออนุญาตสิทธิ์ตำแหน่งด้วยตนเอง",
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction("ตั้งค่า") {
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
            Toast.makeText(this@LoginActivity, "ไม่สามารถเข้าสู่ระบบได้! ต้องได้รับอนุญาตตำแหน่ง", Toast.LENGTH_LONG).show()
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
            val sql = "SELECT USER_ID FROM CM_USER WHERE USER_ID = ? AND PASSWORD = ?"
            preparedStatement = connection.prepareStatement(sql)
            preparedStatement.setString(1, userId)
            preparedStatement.setString(2, password)
            Log.d(logTAG, "Executing SQL Query for user: $userId")

            resultSet = preparedStatement.executeQuery()

            return if (resultSet.next()) {
                Log.d(logTAG, "Found user in database.")
                val user = resultSet.getString("USER_ID")
                saveUserId(user)
                AuthenticationResult.Success
            } else {
                Log.d(logTAG, "User not found in database.")
                AuthenticationResult.InvalidCredentials
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

    private fun saveUserId(userId: String) {
        val sharedPreferences = getSharedPreferences("LoginData", MODE_PRIVATE)
        sharedPreferences.edit {
            putString("UserID", userId)
        }
        Log.d(logTAG, "UserID '$userId' saved to SharedPreferences.")
    }

    sealed class AuthenticationResult {
        object Success : AuthenticationResult()
        object InvalidCredentials : AuthenticationResult()
        object ConnectionError : AuthenticationResult()
        object Error : AuthenticationResult()
    }
}