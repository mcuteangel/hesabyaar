package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class JalaliCalendarTest {

    @Test
    fun `known date conversion - 1403-01-01`() {
        val jd = JalaliCalendarHelper.gregorianToJalali(2024, 3, 20)
        assertEquals(1403, jd.year)
        assertEquals(1, jd.month)
        assertEquals(1, jd.day)
    }

    @Test
    fun `known date conversion - 1403-07-01`() {
        val jd = JalaliCalendarHelper.gregorianToJalali(2024, 9, 22)
        assertEquals(1403, jd.year)
        assertEquals(7, jd.month)
        assertEquals(1, jd.day)
    }

    @Test
    fun `roundtrip gregorian to jalali and back`() {
        val gYear = 2024
        val gMonth = 6
        val gDay = 15
        val jd = JalaliCalendarHelper.gregorianToJalali(gYear, gMonth, gDay)
        val gc = JalaliCalendarHelper.jalaliToGregorian(jd.year, jd.month, jd.day)
        assertEquals(gYear, gc.get(Calendar.YEAR))
        assertEquals(gMonth - 1, gc.get(Calendar.MONTH))
        assertEquals(gDay, gc.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `roundtrip for multiple dates`() {
        val dates = listOf(
            Triple(2024, 1, 1),
            Triple(2024, 2, 29),
            Triple(2024, 6, 15),
            Triple(2024, 12, 25),
            Triple(2025, 1, 1),
            Triple(2025, 3, 20),
            Triple(2025, 8, 10),
            Triple(2023, 7, 1),
            Triple(2020, 3, 21),
        )
        for ((gYear, gMonth, gDay) in dates) {
            val jd = JalaliCalendarHelper.gregorianToJalali(gYear, gMonth, gDay)
            val gc = JalaliCalendarHelper.jalaliToGregorian(jd.year, jd.month, jd.day)
            assertEquals("Year mismatch for $gYear/$gMonth/$gDay", gYear, gc.get(Calendar.YEAR))
            assertEquals("Month mismatch for $gYear/$gMonth/$gDay", gMonth - 1, gc.get(Calendar.MONTH))
            assertEquals("Day mismatch for $gYear/$gMonth/$gDay", gDay, gc.get(Calendar.DAY_OF_MONTH))
        }
    }

    @Test
    fun `isJalaliLeapYear - known leap years`() {
        assertTrue(JalaliCalendarHelper.isJalaliLeapYear(1403))
        assertTrue(JalaliCalendarHelper.isJalaliLeapYear(1408))
        assertTrue(JalaliCalendarHelper.isJalaliLeapYear(1412))
        assertTrue(JalaliCalendarHelper.isJalaliLeapYear(1399))
    }

    @Test
    fun `isJalaliLeapYear - known non-leap years`() {
        assertFalse(JalaliCalendarHelper.isJalaliLeapYear(1400))
        assertFalse(JalaliCalendarHelper.isJalaliLeapYear(1401))
        assertFalse(JalaliCalendarHelper.isJalaliLeapYear(1402))
        assertFalse(JalaliCalendarHelper.isJalaliLeapYear(1404))
    }

    @Test
    fun `isJalaliLeapYear pattern repeats every 33 years`() {
        val leapYears = (1..33).filter { JalaliCalendarHelper.isJalaliLeapYear(1400 + it) }
        assertEquals(8, leapYears.size)
    }

    @Test
    fun `getDaysInMonth - first 6 months have 31 days`() {
        for (month in 1..6) {
            assertEquals(31, JalaliCalendarHelper.getDaysInMonth(1403, month))
        }
    }

    @Test
    fun `getDaysInMonth - months 7-11 have 30 days`() {
        for (month in 7..11) {
            assertEquals(30, JalaliCalendarHelper.getDaysInMonth(1403, month))
        }
    }

    @Test
    fun `getDaysInMonth - month 12 non-leap year has 29 days`() {
        assertEquals(29, JalaliCalendarHelper.getDaysInMonth(1400, 12))
    }

    @Test
    fun `getDaysInMonth - month 12 leap year has 30 days`() {
        assertEquals(30, JalaliCalendarHelper.getDaysInMonth(1403, 12))
    }

    @Test
    fun `jalaliToGregorian - known conversion`() {
        val gc = JalaliCalendarHelper.jalaliToGregorian(1403, 1, 1)
        assertEquals(2024, gc.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, gc.get(Calendar.MONTH))
        assertEquals(20, gc.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `gregorianToJalali from timestamp`() {
        val cal = Calendar.getInstance()
        cal.set(2024, Calendar.MARCH, 20, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val jd = JalaliCalendarHelper.gregorianToJalali(cal.timeInMillis)
        assertEquals(1403, jd.year)
        assertEquals(1, jd.month)
        assertEquals(1, jd.day)
    }

    @Test
    fun `JalaliDate toString format`() {
        val date = JalaliCalendarHelper.JalaliDate(1403, 1, 5)
        assertEquals("1403/01/05", date.toString())
    }

    @Test
    fun `JalaliDate toString zero-pads month and day`() {
        val date = JalaliCalendarHelper.JalaliDate(1403, 3, 9)
        assertEquals("1403/03/09", date.toString())
    }
}
