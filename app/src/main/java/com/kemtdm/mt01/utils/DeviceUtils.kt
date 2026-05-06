package com.kemtdm.mt01.utils

import android.content.Context
import java.util.*
import androidx.core.content.edit

object DeviceUtils {

    private const val PREFS_FILE = "app_prefs"
    private const val PREFS_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        var deviceId = sharedPreferences.getString(PREFS_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPreferences.edit { putString(PREFS_DEVICE_ID, deviceId) }
        }
        return deviceId
    }
}