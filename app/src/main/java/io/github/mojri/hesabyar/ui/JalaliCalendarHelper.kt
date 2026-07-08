package io.github.mojri.hesabyar.ui

import java.util.Calendar

object JalaliCalendarHelper {
  private const val PACKED_DATE_INVALID = 0L
  private const val YEAR_SHIFT = 16
  private const val MONTH_SHIFT = 8

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

  fun gregorianToJalali(timestamp: Long): JalaliDate =
    unpackJalaliDate(
      io.github.mojri.hesabyar.rust.RustBridge
        .gregorianToJalaliSync(timestamp)
    )

  private fun unpackJalaliDate(packed: Long): JalaliDate {
    if (packed == PACKED_DATE_INVALID || packed == Long.MIN_VALUE) return JalaliDate(0, 0, 0)
    val year = (packed shr YEAR_SHIFT).toInt()
    val month = ((packed shr MONTH_SHIFT) and 0xFF).toInt()
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
    if (timestampMs == Long.MIN_VALUE) {
      // Fallback: use Java calendar for invalid conversions
      cal.set(jYear, jMonth - 1, jDay)
    } else {
      cal.timeInMillis = timestampMs
    }
    return cal
  }
}
