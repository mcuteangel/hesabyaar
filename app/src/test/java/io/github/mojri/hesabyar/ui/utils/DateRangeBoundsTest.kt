package io.github.mojri.hesabyar.ui.utils

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Report date-range filters must cover whole local days, and the "previous
 * month" preset must be the previous Jalali month, not a rolling 30 days.
 * Timezone is pinned to Asia/Tehran and the pure-Kotlin Jalali path is forced
 * so results are deterministic.
 */
class DateRangeBoundsTest {
  private val tehran = TimeZone.getTimeZone("Asia/Tehran")
  private var originalDefaultTz: TimeZone? = null
  private var originalBridgeProvider = JalaliCalendarHelper.bridgeProvider

  @Before
  fun setUp() {
    originalDefaultTz = TimeZone.getDefault()
    TimeZone.setDefault(tehran)
    originalBridgeProvider = JalaliCalendarHelper.bridgeProvider
    JalaliCalendarHelper.bridgeProvider = null
  }

  @After
  fun tearDown() {
    TimeZone.setDefault(originalDefaultTz)
    JalaliCalendarHelper.bridgeProvider = originalBridgeProvider
  }

  private fun tehranMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 0,
    min: Int = 0,
    sec: Int = 0,
    ms: Int = 0
  ): Long {
    val cal = Calendar.getInstance(tehran)
    cal.set(year, month - 1, day, hour, min, sec)
    cal.set(Calendar.MILLISECOND, ms)
    return cal.timeInMillis
  }

  @Test
  fun startOfDayDropsClockTime() {
    val picked = tehranMillis(2026, 10, 7, 19, 43, 12, 345)
    assertEquals(tehranMillis(2026, 10, 7), DateRangeBounds.startOfDay(picked))
  }

  @Test
  fun endOfDayIsLastMillisecondOfTheDay() {
    val picked = tehranMillis(2026, 10, 7, 9, 15)
    assertEquals(tehranMillis(2026, 10, 7, 23, 59, 59, 999), DateRangeBounds.endOfDay(picked))
  }

  @Test
  fun endOfDayIncludesTransactionLaterThanPickedClockTime() {
    val picked = tehranMillis(2026, 10, 7, 9, 0)
    val laterSameDay = tehranMillis(2026, 10, 7, 22, 30)
    val range = DateRangeBounds.startOfDay(picked)..DateRangeBounds.endOfDay(picked)
    assertTrue(laterSameDay in range)
  }

  @Test
  fun todayCoversWholeLocalDayEvenBeforeUtcMidnight() {
    // 02:00 Tehran is still the previous day in UTC; "today" must stay local.
    val now = tehranMillis(2026, 10, 7, 2, 0)
    val (start, end) = DateRangeBounds.lastDays(now, days = 1)
    assertEquals(tehranMillis(2026, 10, 7), start)
    assertEquals(tehranMillis(2026, 10, 7, 23, 59, 59, 999), end)
  }

  @Test
  fun lastSevenDaysIncludesToday() {
    val now = tehranMillis(2026, 10, 7, 12, 0)
    val (start, end) = DateRangeBounds.lastDays(now, days = 7)
    assertEquals(tehranMillis(2026, 10, 1), start)
    assertEquals(tehranMillis(2026, 10, 7, 23, 59, 59, 999), end)
  }

  @Test
  fun previousJalaliMonthFromMehrIsShahrivar() {
    // 2026-10-07 = 1405/07/15 (Mehr); previous month Shahrivar 1405 = 2026-08-23..2026-09-22.
    val now = tehranMillis(2026, 10, 7, 19, 43)
    val (start, end) = DateRangeBounds.previousJalaliMonth(now)
    assertEquals(tehranMillis(2026, 8, 23), start)
    assertEquals(tehranMillis(2026, 9, 22, 23, 59, 59, 999), end)
  }

  @Test
  fun previousJalaliMonthFromFarvardinIsEsfandOfPreviousYear() {
    // 2026-03-30 = 1405/01/10; previous month Esfand 1404 (29 days) = 2026-02-20..2026-03-20.
    val now = tehranMillis(2026, 3, 30, 8, 0)
    val (start, end) = DateRangeBounds.previousJalaliMonth(now)
    assertEquals(tehranMillis(2026, 2, 20), start)
    assertEquals(tehranMillis(2026, 3, 20, 23, 59, 59, 999), end)
  }
}
