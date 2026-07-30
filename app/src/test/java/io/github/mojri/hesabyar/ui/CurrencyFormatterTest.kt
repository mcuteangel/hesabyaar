package io.github.mojri.hesabyar.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyFormatterTest {
  /** Strip LRM prefix so tests focus on formatting logic, not BIDI control chars. */
  private fun String.stripLrm(): String = removePrefix("\u200E")

  @After
  fun reset() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  // --- toRial ---

  @Test
  fun `toRial toman multiplies by 10`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(100_000L, CurrencyFormatter.toRial(10_000L))
  }

  @Test
  fun `toRial rial passes through`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(10_000L, CurrencyFormatter.toRial(10_000L))
  }

  @Test
  fun `toRial zero`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(0L, CurrencyFormatter.toRial(0L))
  }

  // --- fromRial ---

  @Test
  fun `fromRial toman divides by 10`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(10_000L, CurrencyFormatter.fromRial(100_000L))
  }

  @Test
  fun `fromRial rial passes through`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(100_000L, CurrencyFormatter.fromRial(100_000L))
  }

  @Test
  fun `fromRial truncates on odd rial`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(5L, CurrencyFormatter.fromRial(55L))
  }

  // --- setUnit state ---

  @Test
  fun `setUnit updates currentUnit`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(CurrencyUnit.RIAL, CurrencyFormatter.currentUnit)
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(CurrencyUnit.TOMAN, CurrencyFormatter.currentUnit)
  }

  // --- round-trip consistency ---

  @Test
  fun `toRial then fromRial round-trips for toman`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val original = 5_500_000L
    val rial = CurrencyFormatter.toRial(original)
    val back = CurrencyFormatter.fromRial(rial)
    assertEquals(original, back)
  }

  @Test
  fun `toRial then fromRial round-trips for rial`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val original = 5_500_000L
    val rial = CurrencyFormatter.toRial(original)
    val back = CurrencyFormatter.fromRial(rial)
    assertEquals(original, back)
  }

  // --- unitLabel ---

  @Test
  fun `unitLabel matches enum label`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(CurrencyUnit.RIAL.label, CurrencyFormatter.unitLabel)
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(CurrencyUnit.TOMAN.label, CurrencyFormatter.unitLabel)
  }

  // --- format includes unit ---

  @Test
  fun `format includes rial unit`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val result = CurrencyFormatter.format(1_000_000L).stripLrm()
    assertTrue(result.endsWith("ریال"))
  }

  @Test
  fun `format includes toman unit`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(10_000_000L).stripLrm()
    assertTrue(result.endsWith("تومان"))
  }

  // --- fromKey ---

  @Test
  fun `fromKey returns correct unit`() {
    assertEquals(CurrencyUnit.RIAL, CurrencyUnit.fromKey("rial"))
    assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey("toman"))
  }

  @Test
  fun `fromKey falls back to TOMAN`() {
    assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey("unknown"))
    assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey(""))
  }

  // --- format / formatNumber fallback + sign handling ---

  @Test
  fun `format divides rial by 10 in toman mode`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(10_000_000L).stripLrm()
    assertTrue(result.contains("۱٬۰۰۰٬۰۰۰"))
    assertTrue(result.endsWith("تومان"))
  }

  @Test
  fun `format shows raw value in rial mode`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val result = CurrencyFormatter.format(10_000_000L).stripLrm()
    assertTrue(result.contains("۱۰٬۰۰۰٬۰۰۰"))
    assertTrue(result.endsWith("ریال"))
  }

  @Test
  fun `format negative value keeps sign before unit`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(-10_000_000L).stripLrm()
    assertTrue(result.startsWith("-"))
    assertTrue(result.endsWith("تومان"))
  }

  // --- Kotlin fallback branch (Rust unavailable in unit tests) ---

  @Test
  fun `formatNumber falls back to persian digits for positive`() {
    val result = CurrencyFormatter.formatNumber(1_234_567L).stripLrm()
    assertEquals("۱٬۲۳۴٬۵۶۷", result)
  }

  @Test
  fun `formatNumber falls back to persian digits with sign for negative`() {
    val result = CurrencyFormatter.formatNumber(-1_234_567L).stripLrm()
    assertEquals("-۱٬۲۳۴٬۵۶۷", result)
  }

  @Test
  fun `format falls back to persian digits with toman label`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(10_000_000L).stripLrm()
    assertEquals("۱٬۰۰۰٬۰۰۰ تومان", result)
  }

  @Test
  fun `format falls back to persian digits with sign and rial label`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val result = CurrencyFormatter.format(-10_000_000L).stripLrm()
    assertEquals("-۱۰٬۰۰۰٬۰۰۰ ریال", result)
  }

  @Test
  fun `format zero falls back without sign`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(0L).stripLrm()
    assertEquals("۰ تومان", result)
  }

  // --- LRM (U+200E) BIDI correctness ---

  @Test
  fun `format negative value starts with LRM before minus`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(-10_000_000L)
    // LRM (U+200E) must be the first character so the number block renders LTR in RTL layout
    assertTrue("must start with LRM", result.startsWith('\u200E'))
    assertTrue("LRM+minus must be followed by digits", result[1] == '-' || result[1] == '\u200E')
    // After LRM and minus, the next char must be a Persian digit (0x06F0–0x06F9)
    val digitIndex = result.indexOfFirst { it.code in 0x06F0..0x06F9 }
    assertFalse("must contain Persian digits", digitIndex == -1)
    assertTrue("Persian digits must come after LRM+minus", digitIndex >= 2)
  }

  @Test
  fun `format positive value starts with LRM`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(10_000_000L)
    assertTrue("positive must start with LRM", result.startsWith('\u200E'))
  }

  @Test
  fun `formatNumber negative value starts with LRM before minus`() {
    val result = CurrencyFormatter.formatNumber(-500_000L)
    assertTrue("must start with LRM", result.startsWith('\u200E'))
    assertTrue("second char must be minus", result[1] == '-')
  }

  // --- formatSigned ---

  @Test
  fun `formatSigned positive prepends plus`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.formatSigned(1_000_000L)
    assertTrue("must start with +", result.startsWith("+"))
  }

  @Test
  fun `formatSigned negative prepends minus`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.formatSigned(-1_000_000L)
    assertTrue("must start with -", result.startsWith("-"))
  }

  @Test
  fun `formatSigned zero prepends plus`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.formatSigned(0L)
    assertTrue("zero must start with +", result.startsWith("+"))
  }

  // --- formatSignedParts ---

  @Test
  fun `formatSignedParts positive returns plus sign`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val (sign, amount) = CurrencyFormatter.formatSignedParts(1_000_000L)
    assertEquals("+", sign)
    assertTrue("amount must contain toman", amount.stripLrm().endsWith("تومان"))
  }

  @Test
  fun `formatSignedParts negative returns minus sign`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val (sign, amount) = CurrencyFormatter.formatSignedParts(-1_000_000L)
    assertEquals("-", sign)
    assertTrue("amount must contain toman", amount.stripLrm().endsWith("تومان"))
  }

  @Test
  fun `formatSignedParts zero returns plus sign`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val (sign, _) = CurrencyFormatter.formatSignedParts(0L)
    assertEquals("+", sign)
  }
}
