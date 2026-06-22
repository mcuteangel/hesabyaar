package io.github.mojri.hesabyar.ui

import java.util.Calendar

object JalaliCalendarHelper {
    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        override fun toString(): String {
            return String.format("%04d/%02d/%02d", year, month, day)
        }
    }

    fun isJalaliLeapYear(year: Int): Boolean {
        val r = year % 33
        return r == 1 || r == 5 || r == 9 || r == 13 || r == 17 || r == 22 || r == 26 || r == 30
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        if (month in 1..6) return 31
        if (month in 7..11) return 30
        return if (isJalaliLeapYear(year)) 30 else 29
    }

    fun gregorianToJalali(timestamp: Long): JalaliDate {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy = gYear - 1600
        val gm = gMonth - 1
        val gd = gDay - 1
        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        gDayNo += gDaysInMonth[gm]
        if (gm > 1 && ((gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        val jMonthsDays = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var i = 0
        while (i < 12 && jDayNo >= jMonthsDays[i]) {
            jDayNo -= jMonthsDays[i]
            i++
        }
        val jm = i + 1
        val jd = jDayNo + 1
        return JalaliDate(jy, jm, jd)
    }

    fun jalaliToGregorian(jYear: Int, jMonth: Int, jDay: Int): Calendar {
        val jy = jYear - 979
        val jm = jMonth - 1
        val jd = jDay - 1
        var jDayNo = 365 * jy + (jy / 33) * 8 + (jy % 33 + 3) / 4
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        for (i in 0 until jm) {
            jDayNo += jDaysInMonth[i]
        }
        jDayNo += jd
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) {
                gDayNo++
            } else {
                leap = false
            }
        }
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }
        var i = 0
        val gDaysInMonth = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        while (gDayNo >= gDaysInMonth[i]) {
            gDayNo -= gDaysInMonth[i]
            i++
        }
        return Calendar.getInstance().apply {
            clear()
            set(gy, i, gDayNo + 1)
        }
    }
}
