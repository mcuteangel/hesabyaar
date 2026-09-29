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
}
