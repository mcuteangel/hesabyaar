package io.github.mojri.hesabyar.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
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
 * Compose tests for [AddPersonLoanDialog] (plans/011 Phase 3).
 *
 * Verifies numeric input validation, Persian digit normalization,
 * confirm/dismiss callbacks, and error message rendering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AddPersonLoanDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

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
  fun emptyAmountKeepsConfirmButtonDisabled() {
    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = NAME_ALI,
        onConfirm = { _, _ -> },
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText(BTN_SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun validPersianDigitsEnableConfirmAndPassConvertedRial() {
    var confirmedRial: Long? = null
    var confirmedDesc: String? = null

    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = NAME_ALI,
        onConfirm = { amount, desc ->
          confirmedRial = amount
          confirmedDesc = desc
        },
        onDismiss = {}
      )
    }

    // Type 50,000 in Persian digits: ۵۰۰۰۰
    composeRule
      .onNode(hasText(amountInputLabel()).and(hasSetTextAction()))
      .performTextInput("۵۰۰۰۰")

    composeRule
      .onNode(hasText(LABEL_DESC).and(hasSetTextAction()))
      .performTextInput(TEST_DESC)

    composeRule.onNodeWithText(BTN_SUBMIT).assertIsEnabled()
    composeRule.onNodeWithText(BTN_SUBMIT).performClick()

    // 50,000 Toman -> 500,000 Rial
    assertEquals("Amount in Rial", 500_000L, confirmedRial)
    assertEquals("Description passed", TEST_DESC, confirmedDesc)
  }

  @Test
  fun dismissButtonInvokesDismissCallback() {
    var dismissed = false

    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.CREDITOR,
        personName = NAME_REZA,
        onConfirm = { _, _ -> },
        onDismiss = { dismissed = true }
      )
    }

    composeRule.onNodeWithText(BTN_CANCEL).performClick()
    assertTrue("Dismiss must be called", dismissed)
  }

  @Test
  fun errorMessageIsDisplayedWhenProvided() {
    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = NAME_ALI,
        errorMessage = TEST_ERROR_MSG,
        onConfirm = { _, _ -> },
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText(TEST_ERROR_MSG).assertIsDisplayed()
  }

  @Test
  fun confirmButtonDisablesWhileSubmitting() {
    val submittingState = mutableStateOf(false)
    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = NAME_ALI,
        isSubmitting = submittingState.value,
        onConfirm = { _, _ -> },
        onDismiss = {}
      )
    }

    composeRule
      .onNode(hasText(amountInputLabel()).and(hasSetTextAction()))
      .performTextInput(TEST_AMOUNT_1000)

    composeRule.onNodeWithText(BTN_SUBMIT).assertIsEnabled()

    submittingState.value = true
    composeRule.waitForIdle()

    // Button is disabled when isSubmitting is true
    composeRule.onNodeWithText(BTN_SUBMIT).assertIsNotEnabled()

    submittingState.value = false
    composeRule.waitForIdle()

    // Button is re-enabled when submission completes or fails
    composeRule.onNodeWithText(BTN_SUBMIT).assertIsEnabled()
  }

  @Test
  fun descriptionInputIsCappedAt500Characters() {
    var confirmedDesc: String? = null

    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = NAME_ALI,
        onConfirm = { _, desc -> confirmedDesc = desc },
        onDismiss = {}
      )
    }

    val longText = CHAR_A.repeat(550)
    composeRule
      .onNode(hasText(amountInputLabel()).and(hasSetTextAction()))
      .performTextInput(TEST_AMOUNT_1000)

    composeRule
      .onNode(hasText(LABEL_DESC).and(hasSetTextAction()))
      .performTextInput(longText)

    composeRule.onNodeWithText(DESC_COUNTER_500).assertIsDisplayed()
    composeRule.onNodeWithText(BTN_SUBMIT).assertIsEnabled().performClick()

    assertEquals("Description should be capped at 500 characters", 500, confirmedDesc?.length)
    assertEquals("Description matches first 500 characters", CHAR_A.repeat(500), confirmedDesc)
  }

  @Test
  fun cancelButtonDisablesWhileSubmitting() {
    val submittingState = mutableStateOf(false)
    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = NAME_ALI,
        isSubmitting = submittingState.value,
        onConfirm = { _, _ -> },
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText(BTN_CANCEL).assertIsEnabled()

    submittingState.value = true
    composeRule.waitForIdle()

    composeRule.onNodeWithText(BTN_CANCEL).assertIsNotEnabled()
  }

  @Test
  fun dismissRequestIsIgnoredWhileSubmitting() {
    var dismissed = false
    val submittingState = mutableStateOf(false)
    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = NAME_ALI,
        isSubmitting = submittingState.value,
        onConfirm = { _, _ -> },
        onDismiss = { dismissed = true }
      )
    }

    submittingState.value = true
    composeRule.waitForIdle()

    // Back out of the dialog while the write is in flight.
    composeRule.onNodeWithText(BTN_CANCEL).assertIsNotEnabled()
    composeRule.onNodeWithText(BTN_CANCEL).performClick()
    composeRule.waitForIdle()

    assertFalse("Dismissal must be ignored while submitting", dismissed)

    submittingState.value = false
    composeRule.waitForIdle()

    composeRule.onNodeWithText(BTN_CANCEL).performClick()
    composeRule.waitForIdle()

    assertTrue("Dismissal allowed once the write completes", dismissed)
  }

  private companion object {
    const val NAME_ALI = "Ali"
    const val NAME_REZA = "Reza"
    const val BTN_SUBMIT = "ثبت"
    const val BTN_CANCEL = "انصراف"
    const val TEST_AMOUNT_1000 = "1000"
    const val TEST_ERROR_MSG = "خطای آزمایشی"
    const val TEST_DESC = "بابت قرض"
    const val LABEL_DESC = "توضیحات (اختیاری)"
    const val DESC_COUNTER_500 = "500/500"
    const val CHAR_A = "a"

    fun amountInputLabel() = "مبلغ (${CurrencyFormatter.unitLabel})"
  }
}
