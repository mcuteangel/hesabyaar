package io.github.mojri.hesabyar.ui.utils

import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Amount fields that were hardcoded to Rial (bank loan form, account opening
 * balance) must read input in the user's display unit and store Rial.
 */
class DisplayAmountInputTest {
  private companion object {
    const val HUNDRED_THOUSAND = "100000"
  }

  @After
  fun reset() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @Test
  fun tomanInputIsStoredAsRial() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(1_000_000L, DisplayAmountInput.toRialOrNull(HUNDRED_THOUSAND))
  }

  @Test
  fun rialInputIsStoredUnchanged() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(100_000L, DisplayAmountInput.toRialOrNull(HUNDRED_THOUSAND))
  }

  @Test
  fun negativeOpeningBalanceKeepsSign() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(-50_000L, DisplayAmountInput.toRialOrNull("-5000"))
  }

  @Test
  fun zeroAndBlankAndInvalidInput() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(0L, DisplayAmountInput.toRialOrNull("0"))
    assertNull(DisplayAmountInput.toRialOrNull(""))
    assertNull(DisplayAmountInput.toRialOrNull("12a"))
  }

  @Test
  fun inputIsTrimmed() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(120L, DisplayAmountInput.toRialOrNull(" 12 "))
  }

  @Test
  fun displayTextConvertsRialToDisplayUnit() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(HUNDRED_THOUSAND, DisplayAmountInput.toDisplayText(1_000_000L))
    assertEquals("-5000", DisplayAmountInput.toDisplayText(-50_000L))
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals("1000000", DisplayAmountInput.toDisplayText(1_000_000L))
  }

  @Test
  fun untouchedEditFieldKeepsOriginalRialValue() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    // 12_345 Rial is shown as 1_234 Toman; saving without edits must not drop the 5.
    val prefilled = DisplayAmountInput.toDisplayText(12_345L)
    assertEquals("1234", prefilled)
    assertEquals(12_345L, DisplayAmountInput.resolveEditedRial(prefilled, prefilled, originalRial = 12_345L))
  }

  @Test
  fun editedFieldIsConvertedFromDisplayUnit() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(20_000L, DisplayAmountInput.resolveEditedRial("2000", "1234", originalRial = 12_345L))
  }

  @Test
  fun newRecordConvertsFromDisplayUnit() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(0L, DisplayAmountInput.resolveEditedRial("0", "0", originalRial = null))
    assertEquals(70L, DisplayAmountInput.resolveEditedRial("7", "0", originalRial = null))
  }

  @Test
  fun persianAndArabicIndicDigitsAreAccepted() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(1_000_000L, DisplayAmountInput.toRialOrNull("۱۰۰۰۰۰"))
    assertEquals(1_000_000L, DisplayAmountInput.toRialOrNull("١٠٠٠٠٠"))
    assertEquals(-50_000L, DisplayAmountInput.toRialOrNull("-۵۰۰۰"))
    // U+2212 MINUS SIGN, as some keyboards emit it.
    assertEquals(-50_000L, DisplayAmountInput.toRialOrNull("\u22125000"))
  }

  @Test
  fun thousandsSeparatorsAreIgnored() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(1_000_000L, DisplayAmountInput.toRialOrNull("100,000"))
    assertEquals(1_000_000L, DisplayAmountInput.toRialOrNull("۱۰۰٬۰۰۰"))
  }

  @Test
  fun rialModeAcceptsAmountsUpToLongMax() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(Long.MAX_VALUE, DisplayAmountInput.maxDisplayAmount())
    assertEquals(Long.MAX_VALUE, DisplayAmountInput.toRialOrNull(Long.MAX_VALUE.toString()))
    assertEquals(9_000_000_000_000_000_000L, DisplayAmountInput.toRialOrNull("9,000,000,000,000,000,000"))
    assertEquals(-Long.MAX_VALUE, DisplayAmountInput.toRialOrNull((-Long.MAX_VALUE).toString()))
    assertNull(DisplayAmountInput.toRialOrNull(Long.MIN_VALUE.toString()))
  }

  @Test
  fun overflowBoundFollowsInjectedConversion() {
    val hundredfold: (Long) -> Long = { it * 100 }
    val max = Long.MAX_VALUE / 100
    assertEquals(max, DisplayAmountInput.maxDisplayAmount(hundredfold))
    assertEquals(max * 100, DisplayAmountInput.toRialOrNull(max.toString(), hundredfold))
    assertNull(DisplayAmountInput.toRialOrNull((max + 1).toString(), hundredfold))
  }

  @Test
  fun amountsThatWouldOverflowAreRejected() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val max = DisplayAmountInput.maxDisplayAmount()
    assertEquals(Long.MAX_VALUE / 10, max)
    assertEquals(max * 10, DisplayAmountInput.toRialOrNull(max.toString()))
    assertNull(DisplayAmountInput.toRialOrNull((max + 1).toString()))
    assertEquals(-max * 10, DisplayAmountInput.toRialOrNull((-max).toString()))
    assertNull(DisplayAmountInput.toRialOrNull((-(max + 1)).toString()))
    assertNull(DisplayAmountInput.toRialOrNull(Long.MAX_VALUE.toString()))
    assertNull(DisplayAmountInput.toRialOrNull(Long.MIN_VALUE.toString()))
  }

  @Test
  fun untouchedPersianDigitPrefillKeepsOriginalRialValue() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(12_345L, DisplayAmountInput.resolveEditedRial("۱۲۳۴", "1234", originalRial = 12_345L))
  }

  @Test
  fun reformattedButEqualPrefillKeepsOriginalRialValue() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    // "01234" and "1,234" are the same 1_234 Toman; they must not round 12_345 Rial down to 12_340.
    assertEquals(12_345L, DisplayAmountInput.resolveEditedRial("01234", "1234", originalRial = 12_345L))
    assertEquals(12_345L, DisplayAmountInput.resolveEditedRial("1,234", "1234", originalRial = 12_345L))
  }
}
