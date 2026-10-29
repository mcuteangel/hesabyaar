package io.github.mojri.hesabyar.ui.screens

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.utils.DisplayAmountInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for the BankLoan form amount fields: invalid, zero, negative,
 * and overflow input must show the error message; valid input must clear it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BankLoanFormTest {
  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule
  val rustIsolationRule = RustIsolationRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val overflowAmount = (DisplayAmountInput.maxDisplayAmount() + 1).toString()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @After
  fun tearDown() {
    HesabyarApp.setRustInitializedForTesting(null)
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @Test
  fun isInvalidAmountRejectsZeroNegativeGarbageAndOverflow() {
    assertFalse("Blank input must not show an error", isInvalidAmount(""))
    assertFalse("Whitespace-only input must not show an error", isInvalidAmount("   "))
    assertTrue("Zero must be flagged", isInvalidAmount("0"))
    assertTrue("Negative must be flagged", isInvalidAmount("-5000"))
    assertTrue("Garbage must be flagged", isInvalidAmount("12a"))
    assertTrue("Overflow must be flagged", isInvalidAmount(overflowAmount))
    assertFalse("Valid positive must pass", isInvalidAmount("500000"))
    assertFalse("Persian digits must pass", isInvalidAmount("۵۰۰۰۰۰"))
  }

  @Test
  @Suppress("LongMethod")
  fun receivedFieldShowsErrorForInvalidInputAndClearsForValidInput() {
    val invalidErrorText = context.getString(R.string.balance_invalid_amount)
    val receivedLabel = context.getString(R.string.bank_loan_received_label, CurrencyFormatter.unitLabel)

    composeRule.setContent {
      var received by remember { mutableStateOf("") }
      var monthly by remember { mutableStateOf("") }

      BankLoanForm(
        bankName = "Melli",
        onBankName = {},
        loanName = "Resalat",
        onLoanName = {},
        received = received,
        onReceived = { received = it },
        monthly = monthly,
        onMonthly = { monthly = it },
        count = "12",
        onCount = {},
        description = "",
        onDescription = {},
        startDateLabel = "1403/01/01",
        onPickDate = {}
      )
    }

    val receivedNode = composeRule.onNode(hasText(receivedLabel).and(hasSetTextAction()))

    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)

    receivedNode.performTextInput("0")
    composeRule.onNodeWithText(invalidErrorText).assertIsDisplayed()

    receivedNode.performTextClearance()
    receivedNode.performTextInput("100000")
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)

    receivedNode.performTextClearance()
    receivedNode.performTextInput(overflowAmount)
    composeRule.onNodeWithText(invalidErrorText).assertIsDisplayed()

    receivedNode.performTextClearance()
    receivedNode.performTextInput("250000")
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)
  }

  @Test
  @Suppress("LongMethod")
  fun monthlyFieldFiltersNonDigitsAndClearsForValidInput() {
    val invalidErrorText = context.getString(R.string.balance_invalid_amount)
    val monthlyLabel = context.getString(R.string.bank_loan_monthly_label, CurrencyFormatter.unitLabel)
    val receivedState = mutableStateOf("100000")
    val monthlyState = mutableStateOf("")
    val countState = mutableStateOf("12")

    setBankLoanFormContent(receivedState, monthlyState, countState)

    val monthlyNode = composeRule.onNode(hasText(monthlyLabel).and(hasSetTextAction()))

    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)

    monthlyNode.performTextInput("5a0b0")
    composeRule.waitForIdle()
    assertEquals("Filtered state keeps digits only", "500", monthlyState.value)
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)

    monthlyNode.performTextClearance()
    monthlyNode.performTextInput("50000")
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)
  }

  @Test
  fun persianAndArabicDigitsAreNormalizedInAmountFields() {
    val invalidErrorText = context.getString(R.string.balance_invalid_amount)
    val receivedLabel = context.getString(R.string.bank_loan_received_label, CurrencyFormatter.unitLabel)
    val monthlyLabel = context.getString(R.string.bank_loan_monthly_label, CurrencyFormatter.unitLabel)
    val receivedState = mutableStateOf("")
    val monthlyState = mutableStateOf("")
    val countState = mutableStateOf("12")

    setBankLoanFormContent(receivedState, monthlyState, countState)

    composeRule
      .onNode(hasText(receivedLabel).and(hasSetTextAction()))
      .performTextInput("۵۰۰۰۰۰")
    composeRule.waitForIdle()
    assertEquals("Persian digits normalize to ASCII", "500000", receivedState.value)
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)

    composeRule
      .onNode(hasText(monthlyLabel).and(hasSetTextAction()))
      .performTextInput("٥٠٠٠٠")
    composeRule.waitForIdle()
    assertEquals("Arabic digits normalize to ASCII", "50000", monthlyState.value)
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)
  }

  @Test
  fun countFieldFiltersNonDigits() {
    val receivedState = mutableStateOf("100000")
    val monthlyState = mutableStateOf("50000")
    val countState = mutableStateOf("")

    setBankLoanFormContent(receivedState, monthlyState, countState)

    val countNode = composeRule.onNode(hasText("تعداد اقساط").and(hasSetTextAction()))

    countNode.performTextInput("1a2")
    composeRule.waitForIdle()
    assertEquals("Filtered count keeps digits only", "12", countState.value)

    countNode.performTextClearance()
    countNode.performTextInput("۱۲")
    composeRule.waitForIdle()
    assertEquals("Persian count digits normalize to ASCII", "12", countState.value)
  }

  @Test
  fun negativeAmountsKeepMinusSignAndShowError() {
    val invalidErrorText = context.getString(R.string.balance_invalid_amount)
    val receivedLabel = context.getString(R.string.bank_loan_received_label, CurrencyFormatter.unitLabel)
    val monthlyLabel = context.getString(R.string.bank_loan_monthly_label, CurrencyFormatter.unitLabel)
    val receivedState = mutableStateOf("")
    val monthlyState = mutableStateOf("")
    val countState = mutableStateOf("")

    setBankLoanFormContent(receivedState, monthlyState, countState)

    val receivedNode = composeRule.onNode(hasText(receivedLabel).and(hasSetTextAction()))

    receivedNode.performTextInput("-5000")
    composeRule.waitForIdle()
    assertEquals("Leading minus is preserved", "-5000", receivedState.value)
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(1)

    composeRule
      .onNode(hasText(monthlyLabel).and(hasSetTextAction()))
      .performTextInput("\u22125000")
    composeRule.waitForIdle()
    assertEquals("Unicode minus normalizes to ASCII minus", "-5000", monthlyState.value)
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(2)

    receivedNode.performTextClearance()
    receivedNode.performTextInput(" -5000")
    composeRule.waitForIdle()
    assertEquals("Whitespace before minus is dropped, sign kept", "-5000", receivedState.value)
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(2)

    composeRule
      .onNode(hasText("تعداد اقساط").and(hasSetTextAction()))
      .performTextInput("-12")
    composeRule.waitForIdle()
    assertEquals("Count drops the minus (positive-only)", "12", countState.value)
  }

  private fun setBankLoanFormContent(
    receivedState: MutableState<String>,
    monthlyState: MutableState<String>,
    countState: MutableState<String>
  ) {
    composeRule.setContent {
      BankLoanForm(
        bankName = "Melli",
        onBankName = {},
        loanName = "Resalat",
        onLoanName = {},
        received = receivedState.value,
        onReceived = { receivedState.value = it },
        monthly = monthlyState.value,
        onMonthly = { monthlyState.value = it },
        count = countState.value,
        onCount = { countState.value = it },
        description = "",
        onDescription = {},
        startDateLabel = "1403/01/01",
        onPickDate = {}
      )
    }
  }

  @Test
  fun addBankLoanFormStateValidationRejectsBlankBankOrZeroAmounts() {
    val state = AddBankLoanFormState()
    assertFalse("Empty state cannot confirm", state.canConfirm)

    state.bankName = "ملی"
    assertFalse("Zero received amount cannot confirm", state.canConfirm)

    state.received = "100000"
    assertFalse("Zero monthly amount cannot confirm", state.canConfirm)

    state.monthly = "10000"
    assertFalse("Zero count cannot confirm", state.canConfirm)

    state.count = "10"
    assertTrue("Valid state must be confirmable", state.canConfirm)
    assertEquals(10, state.countVal)
    assertEquals(1_000_000L, state.receivedRial)
    assertEquals(100_000L, state.monthlyRial)
  }

  @Test
  fun addBankLoanFormStateWhitespaceBankNameIsRejected() {
    val state =
      AddBankLoanFormState(
        bankName = "   ",
        received = "50000",
        monthly = "5000",
        count = "12"
      )
    assertFalse("Whitespace bank name must not be confirmable", state.canConfirm)
  }

  @Test
  fun isInvalidCountRejectsZeroOrNegativeOrOverflow() {
    assertFalse("Blank count does not show error", isInvalidCount(""))
    assertTrue("Zero count is invalid", isInvalidCount("0"))
    assertTrue("Negative count is invalid", isInvalidCount("-5"))
    assertTrue("Overflow count is invalid", isInvalidCount("99999999999999999999"))
    assertFalse("Positive count is valid", isInvalidCount("12"))
  }

  @Test
  fun countFieldShowsErrorWhenZero() {
    val receivedState = mutableStateOf("100000")
    val monthlyState = mutableStateOf("50000")
    val countState = mutableStateOf("")

    setBankLoanFormContent(receivedState, monthlyState, countState)

    val countNode = composeRule.onNode(hasText("تعداد اقساط").and(hasSetTextAction()))
    countNode.performTextInput("0")
    composeRule.waitForIdle()

    composeRule.onNodeWithText("تعداد اقساط باید بیشتر از صفر باشد").assertIsDisplayed()

    countNode.performTextClearance()
    countNode.performTextInput("12")
    composeRule.waitForIdle()

    composeRule.onAllNodesWithText("تعداد اقساط باید بیشتر از صفر باشد").assertCountEquals(0)
  }
}
