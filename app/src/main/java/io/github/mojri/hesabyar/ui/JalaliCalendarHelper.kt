package io.github.mojri.hesabyar.ui

import java.util.Calendar

object JalaliCalendarHelper {
  data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int
  ) {
    override fun toString(): String = String.format(java.util.Locale.US, "%04d/%02d/%02d", year, month, day)
  }

  fun isJalaliLeapYear(year: Int): Boolean =
    io.github.mojri.hesabyar.rust.RustBridge
      .isJalaliLeapYearSync(year)

  fun getDaysInMonth(
    year: Int,
    month: Int
  ): Int =
    io.github.mojri.hesabyar.rust.RustBridge
      .getJalaliDaysInMonthSync(year, month)

  fun gregorianToJalali(timestamp: Long): JalaliDate {
    val packed =
      io.github.mojri.hesabyar.rust.RustBridge
        .gregorianToJalaliSync(timestamp)
    if (packed == 0L) return JalaliDate(0, 0, 0)
    val year = (packed shr 16).toInt()
    val month = ((packed shr 8) and 0xFF).toInt()
    val day = (packed and 0xFF).toInt()
    return JalaliDate(year, month, day)
  }

  fun gregorianToJalali(
    gYear: Int,
    gMonth: Int,
    gDay: Int
  ): JalaliDate {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.set(gYear, gMonth - 1, gDay)
    return gregorianToJalali(cal.timeInMillis)
  }

  fun jalaliToGregorian(
    jYear: Int,
    jMonth: Int,
    jDay: Int
  ): java.util.Calendar {
    val timestampMs =
      io.github.mojri.hesabyar.rust.RustBridge
        .jalaliToGregorianSync(jYear, jMonth, jDay)
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = timestampMs
    return cal
  }
}
