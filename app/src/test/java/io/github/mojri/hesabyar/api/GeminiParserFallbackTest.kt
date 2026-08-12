package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Independent coverage of the Kotlin fallback parser ([GeminiParser.kotlinFallbackParse]),
 * which runs only when the Rust core is unavailable. These do NOT rely on Rust being
 * loaded, so they verify the offline-resilience behavior in isolation.
 *
 * All date-sensitive tests pin a fixed "today" (1405/04/10 = 10 Tir 1405) so
 * results are deterministic regardless of the wall-clock date.
 */
class GeminiParserFallbackTest {
  /** Fixed "today" — 1405/04/10 (10 Tir 1405) — used by all date-sensitive
   *  tests to make them deterministic on any calendar day. */
  private val fixedToday = JalaliCalendarHelper.JalaliDate(1405, 4, 10)

  private fun parse(
    sentence: String,
    today: JalaliCalendarHelper.JalaliDate = fixedToday
  ) = GeminiParser.kotlinFallbackParse(sentence, today)

  private val monthName =
    mapOf(
      1 to "فروردین",
      2 to "اردیبهشت",
      3 to "خرداد",
      4 to "تیر",
      5 to "مرداد",
      6 to "شهریور",
      7 to "مهر",
      8 to "آبان",
      9 to "آذر",
      10 to "دی",
      11 to "بهمن",
      12 to "اسفند"
    )

  // --- success: amount extraction ---

  @Test
  fun `fallback extracts million amount`() {
    val result = parse("غذا خریدم 5 میلیون")
    assertEquals(50_000_000L, result!!.amount)
  }

  @Test
  fun `fallback extracts thousand amount`() {
    val result = parse("بنزین خریدم 450 هزار تومان")
    assertEquals(4_500_000L, result!!.amount)
  }

  @Test
  fun `fallback extracts plain comma amount`() {
    val result = parse("1,500,000 تومان خرج کردم")
    assertEquals(15_000_000L, result!!.amount)
  }

  @Test
  fun `fallback normalizes persian numerals to amount`() {
    val result = parse("بنزین زدم ۶۰۰ هزار تومان")
    assertEquals(6_000_000L, result!!.amount)
  }

  @Test
  fun `fallback overflowing million amount returns null instead of wrapping`() {
    // 9,999,999,999,999,999 * 1_000_000 * 10 overflows Long; must return null
    // rather than a wrapped/negative amount.
    assertNull(parse("9999999999999999 میلیون خرج کردم"))
  }

  // --- success: type detection ---

  @Test
  fun `fallback detects income via keyword`() {
    val result = parse("حقوق گرفتم 20 میلیون")
    assertEquals("INCOME", result!!.type)
    assertEquals("Income", result.category)
    assertEquals(200_000_000L, result.amount)
  }

  @Test
  fun `fallback detects installment via قسط keyword`() {
    val result = parse("قسط ماشین 3 میلیون")
    assertEquals("INSTALLMENT", result!!.type)
    assertEquals("Installments", result.category)
  }

  @Test
  fun `fallback does not mistake فروشگاه for sale income`() {
    val result = parse("فروشگاه رفتم 200 هزار")
    assertEquals("EXPENSE", result!!.type)
    assertEquals("Shopping", result.category)
  }

  @Test
  fun `fallback detects loan debtor via قرض دادم`() {
    val result = parse("به علی 5 میلیون قرض دادم")
    assertEquals("LOAN_DEBTOR", result!!.type)
    assertEquals("Loans", result.category)
    assertEquals("علی", result.personName)
  }

  @Test
  fun `fallback detects loan creditor via قرض گرفتم`() {
    val result = parse("از رضا 2 میلیون قرض گرفتم")
    assertEquals("LOAN_CREDITOR", result!!.type)
    assertEquals("Loans", result.category)
    assertEquals("رضا", result.personName)
  }

  @Test
  fun `fallback defaults to expense`() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals("EXPENSE", result!!.type)
  }

  // --- success: category detection ---

  @Test
  fun `fallback maps food keyword to Food`() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals("Food", result!!.category)
  }

  @Test
  fun `fallback maps bill keyword to Bills`() {
    val result = parse("قبض برق دادم 200 هزار")
    assertEquals("Bills", result!!.category)
  }

  @Test
  fun `fallback defaults unknown to Other`() {
    val result = parse("شیء عجیب 50 هزار")
    assertEquals("Other", result!!.category)
  }

  // --- success: person name ---

  @Test
  fun `fallback extracts person name after به`() {
    val result = parse("به رضا 2 میلیون قرض دادم")
    assertEquals("رضا", result!!.personName)
  }

  @Test
  fun `fallback extracts person name after از`() {
    val result = parse("از علی 5 میلیون قرض گرفتم")
    assertEquals("علی", result!!.personName)
  }

  @Test
  fun `fallback has no person name when absent`() {
    val result = parse("غذا خریدم 100 هزار")
    assertNull(result!!.personName)
  }

  // --- success: date offset ---

  @Test
  fun `fallback detects yesterday offset`() {
    val result = parse("دیروز 500 هزار خرج کردم")
    assertEquals(-1, result!!.dateOffsetDays)
  }

  @Test
  fun `explicit date does not match month name inside longer word like دیروز`() {
    // "5 دیروز" must not be read as "5 دی" (Dey 5); the month name requires a
    // letter boundary after it. The sentence still contains the relative word
    // "دیروز", so the offset should resolve to yesterday (-1), not a Dey offset.
    val result = parse("خریدم 5 دیروز 500 هزار")
    assertEquals(-1, result!!.dateOffsetDays)
  }

  @Test
  fun `fallback detects tomorrow offset`() {
    val result = parse("فردا 1 میلیون واریز می‌کنم")
    assertEquals(1, result!!.dateOffsetDays)
  }

  @Test
  fun `fallback defaults offset to zero`() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals(0, result!!.dateOffsetDays)
  }

  @Test
  fun `fallback parses explicit Jalali date to day offset`() {
    // Pinned today = 1405/04/10 (10 Tir). Future month (Mordad 5) →
    // positive offset; earlier month (Khordad 5) → next year → positive.
    val future = parse("خرج ۱۰۰ هزار مرداد ۵", fixedToday)
    assertTrue(future!!.dateOffsetDays!! > 0)

    val earlier = parse("خرج ۱۰۰ هزار خرداد ۵", fixedToday)
    assertTrue(earlier!!.dateOffsetDays!! > 0)
  }

  // --- explicit Jalali date: day-month format (e.g. "۲۵ تیر") ---

  @Test
  fun `explicit Jalali date day-month format parses correctly`() {
    // Pinned today = 1405/04/10. testDay = 11 → same month, 1 day ahead.
    val result = parse("خریدم ۱۰۰ هزار ۱۱ تیر", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
    assertEquals(1L, result.dateOffsetDays!!.toLong())
  }

  // --- explicit Jalali date: month-day format (e.g. "تیر ۲۵") ---

  @Test
  fun `explicit Jalali date month-day format parses correctly`() {
    // Pinned today = 1405/04/10. testDay = 11 → same month, 1 day ahead.
    val result = parse("خریدم ۱۰۰ هزار تیر ۱۱", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
    assertEquals(1L, result.dateOffsetDays!!.toLong())
  }

  // --- same-month zero offset ---

  @Test
  fun `explicit Jalali date same day yields zero offset`() {
    // Pinned today = 1405/04/10. Parse "10 Tir" → offset 0.
    val result = parse("خریدم ۱۰۰ هزار ۱۰ تیر", fixedToday)
    assertNotNull(result)
    assertEquals(0, result!!.dateOffsetDays)
  }

  // --- edge-case day numbers ---

  @Test
  fun `explicit Jalali date day 1 parses correctly`() {
    // Pinned today = 1405/04/10. Day 1 < 10 → past → next year → non-null.
    val result = parse("خریدم ۱۰۰ هزار ۱ تیر", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
  }

  @Test
  fun `explicit Jalali date day 31 parses correctly for 31-day months`() {
    // Farvardin (month 1) always has 31 days in Jalali. 31 Farvardin
    // is before 10 Tir → past → next year → non-null.
    val result = parse("خریدم ۱۰۰ هزار ۳۱ فروردین", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
  }

  // --- year-boundary: month < today.month means next year ---

  @Test
  fun `explicit Jalali date in earlier month uses next year`() {
    // Pinned today = 1405/04/10. Earlier month = Khordad (3).
    // 5 Khordad → next year → positive offset.
    val result = parse("خریدم ۱۰۰ هزار ۵ خرداد", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
    assertTrue(
      "Offset should be positive (future) for next-year date, was ${result.dateOffsetDays}",
      result.dateOffsetDays!! > 0
    )
  }

  @Test
  fun `explicit Jalali date in same or later month uses current year`() {
    // Pinned today = 1405/04/10. testDay = 11 → same month, 1 day ahead.
    val result = parse("خریدم ۱۰۰ هزار ۱۱ تیر", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
    assertEquals(1L, result.dateOffsetDays!!.toLong())
  }

  // --- no Jalali date found ---

  @Test
  fun `no Jalali date returns null offset via relative word fallback`() {
    val result = parse("خریدم ۱۰۰ هزار غذا")
    assertEquals(0, result!!.dateOffsetDays)
  }

  @Test
  fun `relative word still works when no explicit Jalali date present`() {
    val result = parse("دیروز ۱۰۰ هزار خرج کردم")
    assertEquals(-1, result!!.dateOffsetDays)
  }

  // --- success: fixed fallback metadata ---

  @Test
  fun `fallback uses raw sentence as description`() {
    val sentence = "غذا خریدم 100 هزار"
    val result = parse(sentence)
    assertEquals(sentence, result!!.description)
  }

  @Test
  fun `fallback sets fixed confidence and null rich fields`() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals(0.5f, result!!.confidence, 0.001f)
    assertNull(result.daysFromNow)
    assertNull(result.title)
    assertNull(result.hour)
    assertNull(result.minute)
    assertNull(result.notes)
  }

  // --- failure paths ---

  @Test
  fun `fallback returns null when no amount present`() {
    assertNull(parse("متن بدون پول"))
  }

  @Test
  fun `fallback returns null for empty string`() {
    assertNull(parse(""))
  }

  @Test
  fun `fallback returns null for blank text`() {
    assertNull(parse("   "))
  }

  @Test
  fun `fallback returns null when only zero amount`() {
    assertNull(parse("0 تومان"))
  }

  // ============================================================
  // parseJsonResultFallback tests — Toman-to-Rial conversion
  // ============================================================

  private fun parseJson(json: String) = GeminiParser.parseJsonResultFallback(json)

  @Test
  fun `json fallback converts toman to rial`() {
    val json = """{"type":"EXPENSE","amount":5000000,"category":"Food","description":"مرغ"}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(50_000_000L, result!!.amount) // 5M toman * 10 = 50M rial
  }

  @Test
  fun `json fallback large amount converts correctly`() {
    val json = """{"type":"INCOME","amount":20000000,"category":"Income","description":"حقوق"}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(200_000_000L, result!!.amount) // 20M toman * 10 = 200M rial
  }

  @Test
  fun `json fallback small amount converts correctly`() {
    val json = """{"type":"EXPENSE","amount":50000,"category":"Food","description":"نان"}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(500_000L, result!!.amount) // 50K toman * 10 = 500K rial
  }

  @Test
  fun `json fallback one toman converts to ten rial`() {
    val json = """{"type":"EXPENSE","amount":1,"category":"Other","description":"test"}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(10L, result!!.amount)
  }

  @Test
  fun `json fallback zero amount returns null`() {
    val json = """{"type":"EXPENSE","amount":0,"category":"Food","description":"test"}"""
    assertNull(parseJson(json))
  }

  @Test
  fun `json fallback negative amount returns null`() {
    val json = """{"type":"EXPENSE","amount":-5000,"category":"Food","description":"test"}"""
    assertNull(parseJson(json))
  }

  @Test
  fun `json fallback overflowing amount returns null`() {
    // Long.MAX_VALUE / 10 = 922337203685477580; anything above overflows
    val json = """{"type":"EXPENSE","amount":922337203685477581,"category":"Food","description":"test"}"""
    assertNull(parseJson(json))
  }

  @Test
  fun `json fallback missing amount returns null`() {
    val json = """{"type":"EXPENSE","category":"Food","description":"test"}"""
    assertNull(parseJson(json))
  }

  @Test
  fun `json fallback malformed json returns null`() {
    assertNull(parseJson("{invalid json"))
  }

  @Test
  fun `json fallback empty string returns null`() {
    assertNull(parseJson(""))
  }

  @Test
  fun `json fallback preserves all fields`() {
    val json =
      """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","""" +
        """description":"قسط ماشین","personName":"علی","dateOffsetDays":5,"""" +
        """daysFromNow":30,"title":"قسط ماشین","hour":14,"minute":30,"confidence":0.9}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(30_000_000L, result!!.amount)
    assertEquals("INSTALLMENT", result.type)
    assertEquals("علی", result.personName)
    assertEquals(5, result.dateOffsetDays)
    assertEquals(30, result.daysFromNow)
    assertEquals("قسط ماشین", result.title)
    assertEquals(14, result.hour)
    assertEquals(30, result.minute)
    assertEquals(0.9f, result.confidence, 0.01f)
  }
}
