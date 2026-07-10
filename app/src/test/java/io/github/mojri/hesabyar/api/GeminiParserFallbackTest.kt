package io.github.mojri.hesabyar.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Independent coverage of the Kotlin fallback parser ([GeminiParser.kotlinFallbackParse]),
 * which runs only when the Rust core is unavailable. These do NOT rely on Rust being
 * loaded, so they verify the offline-resilience behavior in isolation.
 */
class GeminiParserFallbackTest {
  private fun parse(sentence: String) = GeminiParser.kotlinFallbackParse(sentence)

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
  fun `fallback detects tomorrow offset`() {
    val result = parse("فردا 1 میلیون واریز می‌کنم")
    assertEquals(1, result!!.dateOffsetDays)
  }

  @Test
  fun `fallback defaults offset to zero`() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals(0, result!!.dateOffsetDays)
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
}
