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

  fun gregorianToJalali(timestamp: Long): JalaliDate {
    // Extract device-local Gregorian date first, then convert to Jalali.
    // Using the raw epoch timestamp directly would use UTC and can shift
    // dates by one day for users ahead of UTC (e.g. Iran UTC+3:30).
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = timestamp
    return gregorianToJalali(
      cal.get(java.util.Calendar.YEAR),
      cal.get(java.util.Calendar.MONTH) + 1,
      cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
  }

  private fun unpackJalaliDate(packed: Long): JalaliDate? {
    if (packed == PACKED_DATE_INVALID || packed == Long.MIN_VALUE) return null
    val year = (packed shr YEAR_SHIFT).toInt()
    val month = ((packed shr MONTH_SHIFT) and 0xFF).toInt()
    val day = (packed and 0xFF).toInt()
    return JalaliDate(year, month, day)
  }

  /**
   * Pure-Kotlin Gregorian→Jalali conversion, mirroring the Rust core
   * (calendar.rs: gregorian_to_jalali_date). Used when the Rust bridge is
   * unavailable so callers stay functional. Returns null for invalid input.
   */
  internal fun gregorianToJalaliLocal(
    gYear: Int,
    gMonth: Int,
    gDay: Int
  ): JalaliDate? {
    if (gMonth < 1 || gMonth > 12) return null
    val maxDay =
      when (gMonth) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 ->
          if ((gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0)) 29 else 28
        else -> return null
      }
    if (gDay < 1 || gDay > maxDay) return null

    val gMonthDayOffsets = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 0)
    val jMonthDays = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    val gy = gYear - 1600
    val gm = gMonth - 1
    val gd = gDay - 1
    val isLeap = (gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0)

    var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
    gDayNo += gMonthDayOffsets[gm]
    if (gm > 1 && isLeap) gDayNo += 1
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

    var i = 0
    while (i < 11 && jDayNo >= jMonthDays[i]) {
      jDayNo -= jMonthDays[i]
      i++
    }

    return JalaliDate(jy, i + 1, jDayNo + 1)
  }

  fun gregorianToJalali(
    gYear: Int,
    gMonth: Int,
    gDay: Int
  ): JalaliDate {
    // Encode the Gregorian Y/M/D as a UTC-midnight timestamp so the
    // Rust core (which interprets timestamps in UTC) returns the Jalali date
    // that corresponds to this *local* calendar day. This calls the Rust
    // bridge directly to avoid recursing back into the timestamp overload.
    val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    utcCal.set(gYear, gMonth - 1, gDay)
    val fromRust =
      unpackJalaliDate(
        io.github.mojri.hesabyar.rust.RustBridge
          .gregorianToJalaliSync(utcCal.timeInMillis)
      )
    return fromRust ?: gregorianToJalaliLocal(gYear, gMonth, gDay)
      ?: throw IllegalStateException(
        "Failed to convert Gregorian date ($gYear-$gMonth-$gDay) to Jalali: invalid date"
      )
  }

  fun jalaliToGregorian(
    jYear: Int,
    jMonth: Int,
    jDay: Int
  ): java.util.Calendar? {
    val timestampMs =
      io.github.mojri.hesabyar.rust.RustBridge
        .jalaliToGregorianSync(jYear, jMonth, jDay)
    if (timestampMs == Long.MIN_VALUE) return null
    // Rust returns UTC midnight for the given Jalali date. To avoid 1-day
    // shift in UTC-negative timezones, extract Y/M/D in UTC then build a
    // *local* Calendar from those fields (not from the raw timestamp).
    val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = timestampMs
    val cal = java.util.Calendar.getInstance()
    cal.set(
      utcCal.get(java.util.Calendar.YEAR),
      utcCal.get(java.util.Calendar.MONTH),
      utcCal.get(java.util.Calendar.DAY_OF_MONTH),
      0,
      0,
      0
    )
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal
  }
}
