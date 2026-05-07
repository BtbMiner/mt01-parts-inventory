package com.kemtdm.mt01.sql

// ใช้ 'object' เพื่อให้เป็น Singleton
object GetSqlString {

    fun getLoginSql(username: String, password: String): String {
        // คำเตือน: วิธีการนี้ **ไม่ปลอดภัย** จาก SQL Injection
        // แต่เพื่อคง Logic เดิมไว้ จึงยังคงโค้ดส่วนนี้
        // คำแนะนำ: ควรใช้ PreparedStatement เพื่อป้องกัน SQL Injection
        // ตัวอย่าง PreparedStatement:
         val sql = "SELECT USER_ID, USER_NAME, ADMINISTRATOR_FLAG FROM CM_USER WHERE USER_NAME = ? AND PASSWORD = ? AND (DISABLED = 0 OR DISABLED IS NULL)"

//        val sql = "SELECT * FROM Users WHERE UserName = '$username' AND Password = '$password'"
        return sql
    }
}