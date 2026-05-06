package com.kemtdm.mt01.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit


object DateUtils {

    // Calculates the current YEARMONTH (YYYYMM format) and CHECKED_WEEK based on the current date.
    // Weeks are counted from Monday to Saturday.
    // If the current day is Sunday, it is considered part of the next week (Monday-Saturday).
    // The month for YEARMONTH is based on the month of the current day.


    fun getCurrentYearMonthAndCheckedWeek(): Pair<String, Int> {
        val today = Calendar.getInstance()
        // สำหรับ Test: ปลดคอมเมนต์ด้านล่างเพื่อลองเปลี่ยนวันที่
        // today.set(2026, Calendar.JUNE, 30)

        // ตั้งเวลาให้เป็น 0 เพื่อการคำนวณวันที่แม่นยำ
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        // --- 1. หาว่าสัปดาห์ปัจจุบัน (อาทิตย์-เสาร์) นี้ สังกัดเดือน/ปี อะไร ---
        val startOfWeek = today.clone() as Calendar
        startOfWeek.add(Calendar.DAY_OF_MONTH, -(today.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))

        val endOfWeek = startOfWeek.clone() as Calendar
        endOfWeek.add(Calendar.DAY_OF_MONTH, 6)

        val reportingMonth: Calendar

        if (startOfWeek.get(Calendar.MONTH) != endOfWeek.get(Calendar.MONTH)) {
            // ถ้าในสัปดาห์มี 2 เดือนคาบเกี่ยวกัน (มีวันที่ 1 อยู่ในสัปดาห์)
            val daysInStartMonth = startOfWeek.getActualMaximum(Calendar.DAY_OF_MONTH) - startOfWeek.get(Calendar.DAY_OF_MONTH) + 1

            reportingMonth = if (daysInStartMonth >= 4) {
                startOfWeek.clone() as Calendar
            } else {
                endOfWeek.clone() as Calendar
            }
        } else {
            // ถ้าทั้งสัปดาห์อยู่ในเดือนเดียวกัน
            reportingMonth = startOfWeek.clone() as Calendar
        }

        // --- 2. หาว่าสัปดาห์ที่ 1 (Week 1) ของเดือน reportingMonth นั้นเริ่มวันที่เท่าไหร่ ---
        val firstDayOfTargetMonth = reportingMonth.clone() as Calendar
        firstDayOfTargetMonth.set(Calendar.DAY_OF_MONTH, 1)

        // หาวันอาทิตย์แรกของสัปดาห์ที่มีวันที่ 1
        val firstSundayOfMonth = firstDayOfTargetMonth.clone() as Calendar
        firstSundayOfMonth.add(Calendar.DAY_OF_MONTH, -(firstDayOfTargetMonth.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))

        val week1Start: Calendar
        val daysInFirstMonthOfTarget = (7 - (firstDayOfTargetMonth.get(Calendar.DAY_OF_WEEK) - 1))

        week1Start = if (daysInFirstMonthOfTarget >= 4) {
            // ถ้าสัปดาห์ที่มีวันที่ 1 มีเดือนนั้นเกิน 4 วัน -> สัปดาห์นั้นคือ Week 1
            firstSundayOfMonth
        } else {
            // ถ้าไม่ถึง 4 วัน -> Week 1 คือวันอาทิตย์ถัดไป
            val nextSunday = firstSundayOfMonth.clone() as Calendar
            nextSunday.add(Calendar.DAY_OF_MONTH, 7)
            nextSunday
        }

        // --- 3. คำนวณหาเลขสัปดาห์ ---
        // ถ้า "วันนี้" ดันอยู่ก่อน Week 1 ของเดือนที่มันควรจะเป็น (เกิดในกรณีรอยต่อต้นเดือนที่ถูกปัดไปเดือนก่อน)
        if (today.before(week1Start)) {
            // ต้องถอยไปหา Week 1 ของเดือนก่อนหน้า
            val prevMonth = reportingMonth.clone() as Calendar
            prevMonth.add(Calendar.MONTH, -1)
            prevMonth.set(Calendar.DAY_OF_MONTH, 1)

            val firstSunPrev = prevMonth.clone() as Calendar
            firstSunPrev.add(Calendar.DAY_OF_MONTH, -(prevMonth.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))

            val daysInPrev = (7 - (prevMonth.get(Calendar.DAY_OF_WEEK) - 1))
            val prevWeek1Start = if (daysInPrev >= 4) firstSunPrev else firstSunPrev.apply { add(Calendar.DAY_OF_MONTH, 7) }

            val diff = TimeUnit.MILLISECONDS.toDays(today.timeInMillis - prevWeek1Start.timeInMillis)
            val weekNum = (diff / 7).toInt() + 1

            val fmt = SimpleDateFormat("yyyyMM", Locale.getDefault())
            return Pair(fmt.format(prevMonth.time), weekNum)
        }

        val diff = TimeUnit.MILLISECONDS.toDays(today.timeInMillis - week1Start.timeInMillis)
        val weekNumber = (diff / 7).toInt() + 1

        // --- 4. จัดรูปแบบผลลัพธ์ ---
        val yearMonthFormat = SimpleDateFormat("yyyyMM", Locale.getDefault())
        val yearMonth = yearMonthFormat.format(reportingMonth.time)

        return Pair(yearMonth, weekNumber)
    }


    fun getCurrentYearMonthWeek(): String {
        val (yearMonth, checkedWeek) = getCurrentYearMonthAndCheckedWeek()
        // นำค่า yearMonth และ checkedWeek มาต่อกันเป็น String
        return yearMonth + checkedWeek
    }

    /*fun getCurrentYearMonthAndCheckedWeek(): Pair<String, Int> {
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Adjust to make Monday the start of the week for calculations.
        // If today is Sunday (1), move to the Monday of the next week to count it as the next week.
        // Otherwise, set it to the Monday of the current week.
        if (currentDayOfWeek == Calendar.SUNDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Move to the Monday of the next week
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        } else {
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }

        // Get the year and month of the current day (when the app is opened).
        val yearMonthFormat = SimpleDateFormat("yyyyMM", Locale.getDefault())
        val yearMonth = yearMonthFormat.format(calendar.time)

        // Calculate the week number within the month (Monday-to-Saturday weeks).
        // This method calculates a general week number for the month, but may not align perfectly
        // with a "Monday-to-Saturday" definition that completely crosses months for "Week 1".
        // For simplicity and to match the "Week 1( )" format, we calculate based on the first Monday of the month.
        val firstDayOfMonth = Calendar.getInstance()
        firstDayOfMonth.time = calendar.time // Start with the month of the current date
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        firstDayOfMonth.set(Calendar.HOUR_OF_DAY, 0)
        firstDayOfMonth.set(Calendar.MINUTE, 0)
        firstDayOfMonth.set(Calendar.SECOND, 0)
        firstDayOfMonth.set(Calendar.MILLISECOND, 0)

        // Find the first Monday of the month.
        val firstMondayOfMonth = firstDayOfMonth.clone() as Calendar
        while (firstMondayOfMonth.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            firstMondayOfMonth.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Calculate the difference in days from the first Monday of the month to the current Monday.
        val diffMillis = calendar.timeInMillis - firstMondayOfMonth.timeInMillis
        val diffDays = diffMillis / (1000 * 60 * 60 * 24)

        // Calculate the week number (integer division, plus 1 because weeks start at 1).
        // If diffDays is negative (current Monday is before the first Monday of the month), it's week 1.
        val checkedWeek = if (diffDays < 0) 1 else (diffDays / 7).toInt() + 1

        return Pair(yearMonth, checkedWeek)
    }*/

    // Checks if the given week number is the first week of the month.
    fun isFirstWeekOfMonth(checkedWeek: Int): Boolean {
        return checkedWeek == 1
    }

}
