package io.github.mojri.hesabyar.ui.utils

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import java.util.Calendar

/**
 * Inclusive `[start, end]` day and month bounds for date-range filters, in the
 * device's local timezone (the same zone [JalaliCalendarHelper] uses).
 *
 * A range filter must cover whole days: "from" starts at 00:00:00.000 and "to"
 * ends at 23:59:59.999, otherwise transactions later in the last day (or earlier
 * in the first day) than the picker's clock time are silently dropped.
 */
object DateRangeBounds {
  private const val DAY_MS = 24L * 60 * 60 * 1000

  /** 00:00:00.000 local time of the day containing [timestamp]. */
  fun startOfDay(timestamp: Long): Long =
    Calendar
      .getInstance()
      .apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }.timeInMillis

  /** 23:59:59.999 local time of the day containing [timestamp] (next day's start − 1 ms). */
  fun endOfDay(timestamp: Long): Long =
    Calendar
      .getInstance()
      .apply {
        timeInMillis = startOfDay(timestamp)
        add(Calendar.DAY_OF_MONTH, 1)
      }.timeInMillis - 1L

  /** Whole-day range covering the last [days] days, today included. */
  fun lastDays(
    now: Long,
    days: Int
  ): Pair<Long, Long> = startOfDay(now - (days - 1).coerceAtLeast(0) * DAY_MS) to endOfDay(now)

  /** The whole previous Jalali month, relative to the month containing [now]. */
  fun previousJalaliMonth(now: Long): Pair<Long, Long> {
    val (currentMonthStart, _) = JalaliCalendarHelper.getJalaliMonthBoundaries(now)
    return JalaliCalendarHelper.getJalaliMonthBoundaries(currentMonthStart - 1L)
  }
}
