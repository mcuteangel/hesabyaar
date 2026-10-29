package io.github.mojri.hesabyar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberTextTest {
  @Test
  fun asciiDigitsParseCorrectly() {
    assertEquals("Plain ASCII number", 123456L, "123456".toCleanLongOrNull())
  }

  @Test
  fun persianDigitsParseCorrectly() {
    // ۰۱۲۳۴۵۶۷۸۹ -> 0123456789
    assertEquals("Persian digits", 123456L, "۱۲۳۴۵۶".toCleanLongOrNull())
    assertEquals("Zero in Persian", 0L, "۰".toCleanLongOrNull())
  }

  @Test
  fun arabicIndicDigitsParseCorrectly() {
    // ٠١٢٣٤٥٦٧٨٩ -> 0123456789
    assertEquals("Arabic-Indic digits", 123456L, "١٢٣٤٥٦".toCleanLongOrNull())
  }

  @Test
  fun mixedDigitsAndPunctuationParseCorrectly() {
    assertEquals("Mixed Persian and ASCII", 120500L, "12۰50۰".toCleanLongOrNull())
    assertEquals("Ignores commas and spaces", 123456L, "۱2۳, 456".toCleanLongOrNull())
  }

  @Test
  fun emptyOrNonDigitInputReturnsNull() {
    assertNull("Empty string", "".toCleanLongOrNull())
    assertNull("Letters only", "abc".toCleanLongOrNull())
    assertNull("Punctuation only", ", - .".toCleanLongOrNull())
  }

  @Test
  fun overflowReturnsNull() {
    val overflowString = "999999999999999999999999"
    assertNull("Overflowing number returns null", overflowString.toCleanLongOrNull())
  }

  @Test
  fun filterDigitsPreservesOnlyDigitsAsAscii() {
    assertEquals("Filters to ASCII", "123456", "۱2۳, abc 456".filterDigits())
  }

  @Test
  fun filterDigitsPreservingLeadingMinusKeepsSign() {
    assertEquals("ASCII minus kept", "-5000", "-5000".filterDigitsPreservingLeadingMinus())
    assertEquals("Unicode minus becomes ASCII", "-5000", "\u22125000".filterDigitsPreservingLeadingMinus())
    assertEquals("Whitespace before minus dropped", "-5000", "  -5000".filterDigitsPreservingLeadingMinus())
    assertEquals("Persian digits after minus", "-5000", "-۵۰۰۰".filterDigitsPreservingLeadingMinus())
    assertEquals("Mid-string minus dropped", "50", "5-0".filterDigitsPreservingLeadingMinus())
    assertEquals("ALM before minus kept", "-5000", "\u061C-5000".filterDigitsPreservingLeadingMinus())
    assertEquals("ALM + Persian digits", "-5000", "\u061C-۵۰۰۰".filterDigitsPreservingLeadingMinus())
    assertEquals("LRM before minus kept", "-5000", "\u200E-5000".filterDigitsPreservingLeadingMinus())
    assertEquals("ALM before Unicode minus", "-5000", "\u061C\u22125000".filterDigitsPreservingLeadingMinus())
    assertEquals("Empty stays empty", "", "".filterDigitsPreservingLeadingMinus())
    assertEquals("Lone minus stays minus", "-", "-".filterDigitsPreservingLeadingMinus())
  }
}
