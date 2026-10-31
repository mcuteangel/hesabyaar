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

  @Test
  fun bankAccountTypePreservesBankDetails() {
    val state = AccountFormState(initialAccount = null)
    state.name = "حساب سپه"
    state.selectedType = AccountType.BANK
    state.bankName = "سپه"
    state.cardNumber = "589210..."
    state.accountNumber = "12345"
    state.iban = "IR123..."
    state.initialBalance = "0"

    val data = state.toFormData()
    assertEquals("سپه", data.bankName)
    assertEquals("589210...", data.cardNumber)
    assertEquals("12345", data.accountNumber)
    assertEquals("IR123...", data.iban)
  }

  @Test
  fun switchingTypeAwayFromBankClearsBankDetails() {
    val state = AccountFormState(initialAccount = null)
    state.name = "حساب سپه"
    state.onTypeSelected(AccountType.BANK)
    state.bankName = "سپه"
    state.cardNumber = "589210..."
    state.accountNumber = "12345"
    state.iban = "IR123..."
    state.initialBalance = "0"

    state.onTypeSelected(AccountType.CASH_WALLET)

    val data = state.toFormData()
    assertEquals(AccountType.CASH_WALLET, data.type)
    assertEquals("Switching away from BANK must clear bank details", null, data.bankName)
    assertEquals(null, data.cardNumber)
    assertEquals(null, data.accountNumber)
    assertEquals(null, data.iban)
  }

  @Test
  fun addModeWithNonBankAccountTypeLeavesBankDetailsNull() {
    val state = AccountFormState(initialAccount = null)
    state.name = "کیف پول جیبی"
    state.selectedType = AccountType.CASH_WALLET
    state.initialBalance = "0"

    val data = state.toFormData()
    assertEquals(AccountType.CASH_WALLET, data.type)
    assertEquals(null, data.bankName)
    assertEquals(null, data.cardNumber)
    assertEquals(null, data.accountNumber)
    assertEquals(null, data.iban)
  }

  @Test
  fun editModeWithLegacyCashAccountPreservesExistingBankDetails() {
    val legacyCashAccount =
      AccountEntity(
        id = 7,
        name = "کیف پول قدیمی",
        type = AccountType.CASH_WALLET,
        bankName = "تجارت",
        cardNumber = "6273...",
        accountNumber = "9876",
        iban = "IR987...",
        initialBalance = 100_000L
      )
    val state = AccountFormState(initialAccount = legacyCashAccount)
    state.name = "کیف پول به‌روز شده"

    val data = state.toFormData()
    assertEquals("کیف پول به‌روز شده", data.name)
    assertEquals(AccountType.CASH_WALLET, data.type)
    assertEquals("تجارت", data.bankName)
    assertEquals("6273...", data.cardNumber)
    assertEquals("9876", data.accountNumber)
    assertEquals("IR987...", data.iban)
  }
}
