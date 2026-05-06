package com.kemtdm.mt01.utils // สมมติว่า package หลักของคุณคือ com.example.cs01

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Helper class สำหรับจัดการฟังก์ชันที่เกี่ยวข้องกับ Wi-Fi และตำแหน่งที่ตั้ง
 * โดยเฉพาะการดึงค่า BSSID ของ Access Point ที่เชื่อมต่ออยู่
 */
object WifiLocationHelper {

    private const val TAG = "WifiLocationHelper"

    /**
     * ดึงค่า BSSID ของ Access Point ที่อุปกรณ์กำลังเชื่อมต่ออยู่
     *
     * @param context Context ของแอปพลิเคชัน
     * @return BSSID ในรูปแบบ String (เช่น "aa:bb:cc:dd:ee:ff") หรือ null หากไม่สามารถดึงค่าได้
     */
    fun getCurrentBSSID(context: Context): String? {
        // 1. ตรวจสอบ Location Permission
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission is required to get BSSID on modern Android versions.")
            return null
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            // ตรวจสอบว่าได้ WifiManager หรือไม่ และเปิด Wi-Fi อยู่หรือไม่
            if (wifiManager == null || !wifiManager.isWifiEnabled) {
                Log.w(TAG, "Wi-Fi is not enabled or WifiManager is unavailable.")
                return null
            }

            // 2. ดึงข้อมูลการเชื่อมต่อ Wi-Fi
            val wifiInfo = wifiManager.connectionInfo
            val bssid: String? = wifiInfo?.bssid

            // 3. ตรวจสอบค่า BSSID ที่ได้
            // BSSID อาจเป็น null หรือ "02:00:00:00:00:00" หากไม่มีการเชื่อมต่อหรือตำแหน่งถูกปิด
            return if (bssid != null && bssid != "02:00:00:00:00:00") {
                // ค่าที่ได้มักจะเป็นตัวพิมพ์เล็ก ให้แปลงเป็นตัวพิมพ์ใหญ่เพื่อความสม่ำเสมอในการเปรียบเทียบ
                bssid.uppercase()
            } else {
                Log.i(TAG, "Device is not connected to Wi-Fi or cannot retrieve BSSID.")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving BSSID: ${e.message}", e)
            return null
        }
    }

    /**
     * ตรวจสอบว่าแอปมีสิทธิ์ ACCESS_FINE_LOCATION หรือไม่
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}