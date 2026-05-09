package com.kemtdm.mt01.utils

import android.util.Log
import java.sql.Connection
import java.sql.ResultSet

inline fun <T> Connection.execQuery(
    sql: String,
    params: List<Any?> = emptyList(),
    block: (ResultSet) -> T
): T {

    val startTime = System.currentTimeMillis()

    this.prepareStatement(sql).use { ps ->

        // ✅ bind parameter
        params.forEachIndexed { index, param ->
            if (param == null) {
                ps.setNull(index + 1, java.sql.Types.VARCHAR)
            } else {
                ps.setObject(index + 1, param)
            }
        }


        // ✅ interceptor log
        SqlInterceptor.log(sql, params, startTime)

        ps.executeQuery().use { rs ->
            return block(rs)
        }
    }
}

fun Connection.execUpdate(
    sql: String,
    params: List<Any?> = emptyList()
): Int {

    val startTime = System.currentTimeMillis()

    this.prepareStatement(sql).use { ps ->


        params.forEachIndexed { index, param ->
            if (param == null) {
                ps.setNull(index + 1, java.sql.Types.VARCHAR)
            } else {
                ps.setObject(index + 1, param)
            }
        }


        SqlInterceptor.log(sql, params, startTime)

        return ps.executeUpdate()
    }
}

inline fun <T> Connection.execInsert(
    sql: String,
    params: List<Any?>,
    block: (java.sql.ResultSet?) -> T
): T {

    val startTime = System.currentTimeMillis()

    this.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->

        // ✅ FIXED binding
        params.forEachIndexed { index, param ->
            if (param == null) {
                ps.setNull(index + 1, java.sql.Types.VARCHAR)
            } else {
                ps.setObject(index + 1, param)
            }
        }

        SqlInterceptor.log(sql, params, startTime)


        val affected = ps.executeUpdate()

        if (affected == 0) {
            Log.w("SQL", "No rows affected")
        }

        val keys = ps.generatedKeys

        return block(keys)
    }
}
