package com.kemtdm.mt01.utils

import android.util.Log

object SqlInterceptor {

    private const val TAG = "Full SQL Query"

    fun log(sql: String, params: List<Any?>, startTime: Long? = null) {

        // ✅ หา caller ที่เป็น "ตัวจริง"
        val (className, methodName) = findRealCaller()

        // ✅ replace parameter ลงใน SQL
        var fullSql = sql

        params.forEach { param ->
            val value = when (param) {
                null -> "NULL"
                is String -> "'${param.replace("'", "''")}'"
                is Number, is Boolean -> param.toString()
                else -> "'$param'"
            }
            fullSql = fullSql.replaceFirst("?", value)
        }

        val header = "[$className.$methodName]"

        if (startTime != null) {
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "$header (${duration}ms)\n$fullSql")
        } else {
            Log.d(TAG, "$header\n$fullSql")
        }
    }

    /**
     * ✅ หา caller จริง (ข้าม internal class)
     */
    private fun findRealCaller(): Pair<String, String> {

        val stack = Throwable().stackTrace

        for (element in stack) {
            val className = element.className

            // ✅ filter ออก class ภายใน system / interceptor
            if (
                !className.contains("SqlInterceptor") &&
                !className.contains("ConnectionExtensions") &&
                !className.startsWith("java.") &&
                !className.startsWith("kotlin.")
            ) {
                val simpleClass = className.substringAfterLast(".")
                val line = element.lineNumber
                val method = "${element.methodName} (L:$line)"

                return Pair(simpleClass, method)
            }
        }

        return Pair("UnknownClass", "UnknownMethod")
    }
}
