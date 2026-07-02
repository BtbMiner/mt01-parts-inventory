package com.kemtdm.mt01.utils

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

/**
 * Utility class for AES/CBC/PKCS7Padding encryption and decryption.
 */
object PasswordEncryption {

    private const val TAG = "PasswordEncryption"
    private const val DEBUG_TAG = "CryptoCheck"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    private const val ALGORITHM = "AES"

    // Ensure these byte lengths match C# (.NET) configuration exactly
    // Key: 32 bytes (AES-256), IV: 16 bytes
    private val KEY_BYTES = "e3b0c44298fc1c149afbf4c8996fb924".toByteArray(StandardCharsets.UTF_8)
    private val IV_BYTES = "e8bd9258cfb7deac".toByteArray(StandardCharsets.UTF_8)

    /**
     * Encrypts a plain text password using AES/CBC/PKCS7Padding.
     */
    fun encryptPassword(password: String?): String {
        if (password.isNullOrEmpty()) return ""

        return try {
            val keySpec = SecretKeySpec(KEY_BYTES, ALGORITHM)
            val ivSpec = IvParameterSpec(IV_BYTES)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            
            val encryptedBytes = cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
            val base64Result = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            // Verification Log
            Log.d(DEBUG_TAG, "Plaintext (Before Encrypt): $password")
            Log.d(DEBUG_TAG, "Ciphertext (After Encrypt): $base64Result")

            base64Result
        } catch (e: Exception) {
            Log.d(DEBUG_TAG, "Encryption error caught: ${e.message}")
            Log.e(TAG, "Encryption error: ${e.message}", e)
            ""
        }
    }

    /**
     * Decrypts a Base64 encoded encrypted password.
     */
    fun decryptPassword(encryptedPassword: String?): String {
        if (encryptedPassword.isNullOrEmpty()) return ""

        return try {
            val keySpec = SecretKeySpec(KEY_BYTES, ALGORITHM)
            val ivSpec = IvParameterSpec(IV_BYTES)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            val decodedBytes = Base64.decode(encryptedPassword, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val decryptedResult = String(decryptedBytes, StandardCharsets.UTF_8)

            // Verification Log
            Log.d(DEBUG_TAG, "Ciphertext (Before Decrypt): $encryptedPassword")
            Log.d(DEBUG_TAG, "Decrypted Plaintext: $decryptedResult")

            decryptedResult
        } catch (e: Exception) {
            Log.d(DEBUG_TAG, "Decryption error caught: ${e.message}")
            Log.e(TAG, "Decryption error: ${e.message}", e)
            ""
        }
    }
}
