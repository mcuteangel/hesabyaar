package io.github.mojri.hesabyar.ui.screens.account

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AccountFormState] validation, save-enabling logic, and balance fallback:
 * - In add mode, blank balance defaults to 0 and valid non-blank balance parses to Rial.
 * - In edit mode, blank or unchanged balance preserves the original Rial balance.
 * - Non-blank malformed balance marks [AccountFormState.balanceError] and disables saving.
 */
class AccountFormStateTest {
  private var previousUnit: CurrencyUnit? = null

  @Before
  fun setUp() {
    previousUnit = CurrencyFormatter.currentUnit
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @After
  fun tearDown() {
    previousUnit?.let { CurrencyFormatter.setUnit(it) }
  }

  @Test
  fun addModeWithBlankBalanceTriggersErrorAndDisablesSave() {
    val state = AccountFormState(initialAccount = null)
    state.name = "حساب جاری"
    state.initialBalance = ""

    assertTrue("Blank balance must trigger balance error", state.balanceError)
    assertFalse("Blank balance must not be savable", state.isSaveEnabled)
  }

  @Test
  fun addModeWithDefaultZeroBalanceAllowsSave() {
    val state = AccountFormState(initialAccount = null)
    state.name = "حساب جاری"
    state.initialBalance = "0"

    assertFalse("Zero balance must not trigger balance error", state.balanceError)
    assertTrue("Zero balance must be savable", state.isSaveEnabled)
    assertEquals(0L, state.toFormData().initialBalance)
  }

  @Test
  fun addModeWithValidBalanceParsesCorrectly() {
    val state = AccountFormState(initialAccount = null)
    state.name = "حساب جاری"
    state.initialBalance = "50000"

    assertFalse(state.balanceError)
    assertTrue(state.isSaveEnabled)
    assertEquals(500_000L, state.toFormData().initialBalance)
  }

  @Test
  fun addModeWithValidBalanceInRialUnitStoresExactAmount() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val state = AccountFormState(initialAccount = null)
    state.name = "حساب جاری"
    state.initialBalance = "50000"

    assertFalse(state.balanceError)
    assertTrue(state.isSaveEnabled)
    assertEquals(50_000L, state.toFormData().initialBalance)
  }

  @Test
  fun addModeWithMalformedBalanceDisablesSave() {
    val state = AccountFormState(initialAccount = null)
    state.name = "حساب جاری"
    state.initialBalance = "invalid_number"

    assertTrue("Malformed balance must set balanceError", state.balanceError)
    assertFalse("Malformed balance must disable save", state.isSaveEnabled)
  }

  @Test
  fun addModeWithBlankNameDisablesSave() {
    val state = AccountFormState(initialAccount = null)
    state.name = "   "
    state.initialBalance = "1000"

    assertFalse(state.balanceError)
    assertFalse("Blank name must disable save", state.isSaveEnabled)
  }

  @Test
  fun editModeWithPrefilledBalancePreservesOriginalRial() {
    val original =
      AccountEntity(
        id = 10,
        name = "حساب پس‌انداز",
        type = AccountType.BANK,
        initialBalance = 1_234_567L
      )
    val state = AccountFormState(initialAccount = original)

    assertFalse(state.balanceError)
    assertTrue(state.isSaveEnabled)
    assertEquals(
      "Unmodified balance must preserve original exact Rial value",
      1_234_567L,
      state.toFormData().initialBalance
    )
  }

  @Test
  fun editModeWithBlankBalanceTriggersErrorAndDisablesSave() {
    val original =
      AccountEntity(
        id = 10,
        name = "حساب پس‌انداز",
        type = AccountType.BANK,
        initialBalance = 750_000L
      )
    val state = AccountFormState(initialAccount = original)
    state.initialBalance = ""

    assertTrue("Blank balance in edit mode must trigger balanceError", state.balanceError)
    assertFalse("Clearing balance field in edit mode must disable saving", state.isSaveEnabled)
  }

  @Test
  fun editModeWithNewValidBalanceUpdatesBalance() {
    val original =
      AccountEntity(
        id = 10,
        name = "حساب پس‌انداز",
        type = AccountType.BANK,
        initialBalance = 100_000L
      )
    val state = AccountFormState(initialAccount = original)
    state.initialBalance = "25000"

    assertFalse(state.balanceError)
    assertTrue(state.isSaveEnabled)
    assertEquals(250_000L, state.toFormData().initialBalance)
  }

  @Test
  fun editModeWithMalformedBalanceDisablesSave() {
    val original =
      AccountEntity(
        id = 10,
        name = "حساب پس‌انداز",
        type = AccountType.BANK,
        initialBalance = 100_000L
      )
    val state = AccountFormState(initialAccount = original)
    state.initialBalance = "invalid_amount"

    assertTrue("Malformed balance in edit mode must set balanceError", state.balanceError)
    assertFalse("Malformed balance in edit mode must disable save", state.isSaveEnabled)
  }
}
