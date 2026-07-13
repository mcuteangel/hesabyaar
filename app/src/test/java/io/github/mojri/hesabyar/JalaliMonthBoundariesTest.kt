package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Locks down [JalaliCalendarHelper.getJalaliMonthBoundaries]: the exact
 * 00:00:00.000 of the 1st day and 23:59:59.999 of the last day of the Jalali
 * month containing a timestamp, including a regular 31-day month, a 30-day
 * Esfand (leap year), and a 29-day Esfand (non-leap year).
 *
 * The default timezone is pinned to Asia/Tehran so the produced timestamps are
 * deterministic; the helper computes boundaries in device-local time.
 */
class JalaliMonthBoundariesTest {
  private val tehran = TimeZone.getTimeZone("Asia/Tehran")
  private var originalDefaultTz: TimeZone? = null
  private var originalBridgeProvider = JalaliCalendarHelper.bridgeProvider

  @Before
  fun setUp() {
    originalDefaultTz = TimeZone.getDefault()
    TimeZone.setDefault(tehran)
    // Capture and later restore the bridge provider so tests that install a
    // native bridge are not perturbed; force the pure-Kotlin path for this test
    // so results are deterministic and independent of native-core loading.
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
  fun `regular Farvardin month boundaries (1403-01)`() {
    val t = tehranMillis(2024, 3, 20, 12) // midday of Farvardin 1403
    val (start, end) = JalaliCalendarHelper.getJalaliMonthBoundaries(t)

    // 00:00:00.000 of the 1st day, 23:59:59.999 of the last day (31-day month).
    assertEquals(tehranMillis(2024, 3, 20), start)
    assertEquals(tehranMillis(2024, 4, 19, 23, 59, 59, 999), end)
    assertTrue(start <= t && t <= end)

    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 1, 1), JalaliCalendarHelper.gregorianToJalali(start))
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 1, 31), JalaliCalendarHelper.gregorianToJalali(end))

    // Span covers exactly 31 days.
    assertEquals(31L * 24 * 60 * 60 * 1000, end - start + 1)
    // One ms before the start belongs to the previous (Esfand 1402) month.
    assertEquals(JalaliCalendarHelper.JalaliDate(1402, 12, 29), JalaliCalendarHelper.gregorianToJalali(start - 1))
    // One ms after the end is the 1st of the next month.
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 2, 1), JalaliCalendarHelper.gregorianToJalali(end + 1))
  }

  @Test
  fun `Esfand leap-year boundary (1403-12, 30 days)`() {
    val t = tehranMillis(2025, 2, 19, 12) // midday of Esfand 1403 (leap year)
    val (start, end) = JalaliCalendarHelper.getJalaliMonthBoundaries(t)

    assertEquals(tehranMillis(2025, 2, 19), start)
    assertEquals(tehranMillis(2025, 3, 20, 23, 59, 59, 999), end)
    assertTrue(start <= t && t <= end)

    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 12, 1), JalaliCalendarHelper.gregorianToJalali(start))
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 12, 30), JalaliCalendarHelper.gregorianToJalali(end))

    assertEquals(30L * 24 * 60 * 60 * 1000, end - start + 1)
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 11, 30), JalaliCalendarHelper.gregorianToJalali(start - 1))
    assertEquals(JalaliCalendarHelper.JalaliDate(1404, 1, 1), JalaliCalendarHelper.gregorianToJalali(end + 1))
  }

  @Test
  fun `Esfand non-leap-year boundary (1402-12, 29 days)`() {
    val t = tehranMillis(2024, 2, 20, 12) // midday of Esfand 1402 (non-leap year)
    val (start, end) = JalaliCalendarHelper.getJalaliMonthBoundaries(t)

    assertEquals(tehranMillis(2024, 2, 20), start)
    assertEquals(tehranMillis(2024, 3, 19, 23, 59, 59, 999), end)
    assertTrue(start <= t && t <= end)

    assertEquals(JalaliCalendarHelper.JalaliDate(1402, 12, 1), JalaliCalendarHelper.gregorianToJalali(start))
    assertEquals(JalaliCalendarHelper.JalaliDate(1402, 12, 29), JalaliCalendarHelper.gregorianToJalali(end))

    assertEquals(29L * 24 * 60 * 60 * 1000, end - start + 1)
    assertEquals(JalaliCalendarHelper.JalaliDate(1402, 11, 30), JalaliCalendarHelper.gregorianToJalali(start - 1))
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 1, 1), JalaliCalendarHelper.gregorianToJalali(end + 1))
  }

  // --- Saturating arithmetic used by the month-boundary fallbacks ------------
  // Extreme timestamps must NOT wrap the 30-day fallback window; the range must
  // stay ordered and bounded at Long.MIN_VALUE / Long.MAX_VALUE.

  private val windowMs = 30L * 24 * 60 * 60 * 1000

  @Test
  fun `saturatingAdd clamps at Long MAX instead of wrapping`() {
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingAdd(Long.MAX_VALUE, windowMs))
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingAdd(Long.MAX_VALUE - windowMs / 2, windowMs))
  }

  @Test
  fun `saturatingAdd clamps at Long MIN when subtracting past it`() {
    assertEquals(Long.MIN_VALUE, JalaliCalendarHelper.saturatingAdd(Long.MIN_VALUE, -windowMs))
  }

  @Test
  fun `saturatingSubtract clamps at Long MIN instead of wrapping`() {
    assertEquals(Long.MIN_VALUE, JalaliCalendarHelper.saturatingSubtract(Long.MIN_VALUE, windowMs))
  }

  @Test
  fun `saturatingSubtract clamps at Long MAX for negative subtrahend`() {
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingSubtract(Long.MAX_VALUE, -1))
  }

  @Test
  fun `saturating arithmetic matches plain arithmetic in safe range`() {
    assertEquals(1000L - windowMs, JalaliCalendarHelper.saturatingSubtract(1000L, windowMs))
    assertEquals(1000L + windowMs, JalaliCalendarHelper.saturatingAdd(1000L, windowMs))
    assertEquals(Long.MAX_VALUE - 1, JalaliCalendarHelper.saturatingSubtract(Long.MAX_VALUE, 1))
  }
}
