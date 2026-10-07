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
  @After
  fun reset() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @Test
  fun tomanInputIsStoredAsRial() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(1_000_000L, DisplayAmountInput.toRialOrNull("100000"))
  }

  @Test
  fun rialInputIsStoredUnchanged() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(100_000L, DisplayAmountInput.toRialOrNull("100000"))
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
    assertEquals("100000", DisplayAmountInput.toDisplayText(1_000_000L))
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
}
