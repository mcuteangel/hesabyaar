package io.github.mojri.hesabyar.ui.screens

import android.content.Context
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
    var monthly by mutableStateOf("")

    composeRule.setContent {
      var received by remember { mutableStateOf("100000") }

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

    val monthlyNode = composeRule.onNode(hasText(monthlyLabel).and(hasSetTextAction()))

    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)

    monthlyNode.performTextInput("5a0b0")
    composeRule.waitForIdle()
    assertEquals("Filtered state keeps digits only", "500", monthly)
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
    var received by mutableStateOf("")
    var monthly by mutableStateOf("")

    composeRule.setContent {
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

    composeRule
      .onNode(hasText(receivedLabel).and(hasSetTextAction()))
      .performTextInput("۵۰۰۰۰۰")
    composeRule.waitForIdle()
    assertEquals("Persian digits normalize to ASCII", "500000", received)
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)

    composeRule
      .onNode(hasText(monthlyLabel).and(hasSetTextAction()))
      .performTextInput("٥٠٠٠٠")
    composeRule.waitForIdle()
    assertEquals("Arabic digits normalize to ASCII", "50000", monthly)
    composeRule.onAllNodesWithText(invalidErrorText).assertCountEquals(0)
  }

  @Test
  fun countFieldFiltersNonDigits() {
    var count by mutableStateOf("")

    composeRule.setContent {
      BankLoanForm(
        bankName = "Melli",
        onBankName = {},
        loanName = "Resalat",
        onLoanName = {},
        received = "100000",
        onReceived = {},
        monthly = "50000",
        onMonthly = {},
        count = count,
        onCount = { count = it },
        description = "",
        onDescription = {},
        startDateLabel = "1403/01/01",
        onPickDate = {}
      )
    }

    val countNode = composeRule.onNode(hasText("تعداد اقساط").and(hasSetTextAction()))

    countNode.performTextInput("1a2")
    composeRule.waitForIdle()
    assertEquals("Filtered count keeps digits only", "12", count)

    countNode.performTextClearance()
    countNode.performTextInput("۱۲")
    composeRule.waitForIdle()
    assertEquals("Persian count digits normalize to ASCII", "12", count)
  }
}
