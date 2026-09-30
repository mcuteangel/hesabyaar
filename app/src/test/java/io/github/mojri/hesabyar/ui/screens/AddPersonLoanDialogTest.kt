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
        personName = "Ali",
        onConfirm = { _, _ -> },
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText("ثبت").assertIsNotEnabled()
  }

  @Test
  fun validPersianDigitsEnableConfirmAndPassConvertedRial() {
    var confirmedRial: Long? = null
    var confirmedDesc: String? = null

    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = "Ali",
        onConfirm = { amount, desc ->
          confirmedRial = amount
          confirmedDesc = desc
        },
        onDismiss = {}
      )
    }

    // Type 50,000 in Persian digits: ۵۰۰۰۰
    composeRule
      .onNode(hasText("مبلغ (${CurrencyFormatter.unitLabel})").and(hasSetTextAction()))
      .performTextInput("۵۰۰۰۰")

    composeRule
      .onNode(hasText("توضیحات (اختیاری)").and(hasSetTextAction()))
      .performTextInput("بابت قرض")

    composeRule.onNodeWithText("ثبت").assertIsEnabled()
    composeRule.onNodeWithText("ثبت").performClick()

    // 50,000 Toman -> 500,000 Rial
    assertEquals("Amount in Rial", 500_000L, confirmedRial)
    assertEquals("Description passed", "بابت قرض", confirmedDesc)
  }

  @Test
  fun dismissButtonInvokesDismissCallback() {
    var dismissed = false

    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.CREDITOR,
        personName = "Reza",
        onConfirm = { _, _ -> },
        onDismiss = { dismissed = true }
      )
    }

    composeRule.onNodeWithText("انصراف").performClick()
    assertTrue("Dismiss must be called", dismissed)
  }

  @Test
  fun errorMessageIsDisplayedWhenProvided() {
    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = "Ali",
        errorMessage = "خطای آزمایشی",
        onConfirm = { _, _ -> },
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText("خطای آزمایشی").assertIsDisplayed()
  }

  @Test
  fun confirmButtonDisablesWhileSubmitting() {
    val submittingState = mutableStateOf(false)
    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = "Ali",
        isSubmitting = submittingState.value,
        onConfirm = { _, _ -> },
        onDismiss = {}
      )
    }

    composeRule
      .onNode(hasText("مبلغ (${CurrencyFormatter.unitLabel})").and(hasSetTextAction()))
      .performTextInput("1000")

    composeRule.onNodeWithText("ثبت").assertIsEnabled()

    submittingState.value = true
    composeRule.waitForIdle()

    // Button is disabled when isSubmitting is true
    composeRule.onNodeWithText("ثبت").assertIsNotEnabled()

    submittingState.value = false
    composeRule.waitForIdle()

    // Button is re-enabled when submission completes or fails
    composeRule.onNodeWithText("ثبت").assertIsEnabled()
  }

  @Test
  fun descriptionInputIsCappedAt500Characters() {
    var confirmedDesc: String? = null

    composeRule.setContent {
      AddPersonLoanDialog(
        type = LoanType.DEBTOR,
        personName = "Ali",
        onConfirm = { _, desc -> confirmedDesc = desc },
        onDismiss = {}
      )
    }

    val longText = "a".repeat(550)
    composeRule
      .onNode(hasText("مبلغ (${CurrencyFormatter.unitLabel})").and(hasSetTextAction()))
      .performTextInput("1000")

    composeRule
      .onNode(hasText("توضیحات (اختیاری)").and(hasSetTextAction()))
      .performTextInput(longText)

    composeRule.onNodeWithText("500/500").assertIsDisplayed()
    composeRule.onNodeWithText("ثبت").assertIsEnabled().performClick()

    assertEquals("Description should be capped at 500 characters", 500, confirmedDesc?.length)
    assertEquals("Description matches first 500 characters", "a".repeat(500), confirmedDesc)
  }
}
