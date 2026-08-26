package io.github.mojri.hesabyar.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonNameNormalizerTest {
  @Test
  fun normalizeFoldsArabicYehKafAndTehMarbutaToPersian() {
    assertEquals("علی", PersonNameNormalizer.normalize("علي"))
    assertEquals("عکبر", PersonNameNormalizer.normalize("عكبر"))
    assertEquals("حوزه", PersonNameNormalizer.normalize("حوزة"))
  }

  @Test
  fun normalizeTrimsAndCollapsesInternalWhitespace() {
    assertEquals("علی رضا", PersonNameNormalizer.normalize("  علی   رضا  "))
    assertEquals("علی رضا", PersonNameNormalizer.normalize("\tعلی\nرضا"))
  }

  @Test
  fun normalizeStripsZeroWidthCharacters() {
    assertEquals("علیرضا", PersonNameNormalizer.normalize("علی\u200Cرضا"))
    assertEquals("علیرضا", PersonNameNormalizer.normalize("علی\u200Bرضا"))
  }

  @Test
  fun normalizeLowercasesLatinPartOnly() {
    assertEquals("ali رضا", PersonNameNormalizer.normalize("ALI رضا"))
  }

  @Test
  fun normalizeReturnsEmptyStringForWhitespaceOnlyInput() {
    assertEquals("", PersonNameNormalizer.normalize("   "))
  }

  @Test
  fun normalizeReturnsEmptyStringForZeroWidthOnlyInput() {
    assertEquals("", PersonNameNormalizer.normalize("\u200B"))
    assertEquals("", PersonNameNormalizer.normalize("\u200C\u200D\uFEFF\u2060"))
  }

  @Test
  fun displayFormKeepsFirstTrimmedOriginalSpelling() {
    assertEquals("  علی  ".trim(), PersonNameNormalizer.displayForm("  علی  "))
    assertEquals("ALI", PersonNameNormalizer.displayForm("ALI"))
  }
}
