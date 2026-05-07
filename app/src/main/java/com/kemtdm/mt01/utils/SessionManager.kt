package com.kemtdm.mt01.utils

import android.content.Context

object SessionManager {

    private const val PREFS_NAME = "LoginData"

    fun getUserId(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("UserID", "") ?: ""

    fun getUserName(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("UserName", "") ?: ""

    fun isAdmin(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("IsAdmin", false)

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
