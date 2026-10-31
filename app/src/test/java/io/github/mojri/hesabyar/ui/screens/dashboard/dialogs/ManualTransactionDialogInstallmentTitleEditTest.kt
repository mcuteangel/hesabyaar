package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.SubmitManualTransactionUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests title seeding on the edit path in [ManualTransactionDialog].
 * The dialog seeds title state from the installmentTitle parameter.
 * The dialog seeds description state from the transaction description.
 * The two seeds stay independent.
 * The tests assert the values in the captured submit request.
 * The title field does not render in edit mode.
 * Edit mode excludes INSTALLMENT from the type selector.
 * TransactionType has no INSTALLMENT value.
 * These tests do not cover installment edit dispatch.
 * One test covers INSTALLMENT submit in create mode.
 * SubmitManualTransactionUseCaseTest covers the installment write path.
 * A separate issue tracks a real installment edit path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ManualTransactionDialogInstallmentTitleEditTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val accounts =
    listOf(
      AccountEntity(id = 10L, name = "Bank A", type = AccountType.BANK),
      AccountEntity(id = 20L, name = "Cash B", type = AccountType.CASH_WALLET),
    )

  private val categories =
    listOf(
      Category(id = 1L, name = "Food", key = "Food", icon = "", color = 0L, type = CategoryType.EXPENSE),
    )

  private val original =
    Transaction(
      id = 1L,
      type = TransactionType.EXPENSE,
      categoryId = 1L,
      amount = 5000L,
      description = "Monthly notes",
      date = System.currentTimeMillis(),
      accountId = 10L,
    )

  private fun launchEditDialog(
    installmentTitle: String?,
    captured: androidx.compose.runtime.MutableState<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>
  ) {
    composeRule.setContent {
      ManualTransactionDialog(
        onSubmit = { request ->
          captured.value = request
          SubmitManualTransactionUseCase.SubmitResult(success = true)
        },
        categories = categories,
        transactionToEdit = original,
        installmentTitle = installmentTitle,
        accounts = accounts,
        selectedAccountId = 10L,
        onDismiss = {},
      )
    }
  }

  @Test
  fun editDialogWithInstallmentTitleKeepsTitleIndependentFromDescription() {
    val captured = mutableStateOf<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>(null)

    launchEditDialog(installmentTitle = "Car loan", captured = captured)

    composeRule.onNodeWithText("ذخیره تغییرات").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { captured.value != null }

    val request = captured.value
    assertNotNull("Submit must not be blocked", request)
    assertEquals("Edit must keep the dedicated installment title", "Car loan", request?.title)
    assertEquals("Edit must keep the original description", "Monthly notes", request?.descriptionText)
  }

  // -- INSTALLMENT submit in create mode (the only INSTALLMENT form path) ------

  @Test
  fun createDialogInstallmentSubmitKeepsTitleIndependentFromDescription() {
    val captured = mutableStateOf<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>(null)

    composeRule.setContent {
      ManualTransactionDialog(
        onSubmit = { request ->
          captured.value = request
          SubmitManualTransactionUseCase.SubmitResult(success = true)
        },
        categories = categories,
        accounts = accounts,
        selectedAccountId = 10L,
        onDismiss = {},
      )
    }

    composeRule.onNodeWithText("قسط").performScrollTo().performClick()
    composeRule.onNodeWithTag("manual_amount_input").performTextInput("3000")
    composeRule.onNodeWithTag("manual_title_input").performTextInput("Car loan")
    composeRule.onNodeWithTag("manual_description_input").performTextInput("Monthly notes")
    composeRule.onNodeWithText("ثبت تراکنش").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { captured.value != null }

    val request = captured.value
    assertNotNull("Submit must not be blocked", request)
    assertEquals("Create must submit as INSTALLMENT", "INSTALLMENT", request?.selectedType)
    assertEquals("Create must keep the installment title", "Car loan", request?.title)
    assertEquals("Create must keep the description independent", "Monthly notes", request?.descriptionText)
  }

  @Test
  fun editDialogWithoutInstallmentTitleDoesNotCopyDescriptionIntoTitle() {
    val captured = mutableStateOf<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>(null)

    launchEditDialog(installmentTitle = null, captured = captured)

    composeRule.onNodeWithText("ذخیره تغییرات").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { captured.value != null }

    val request = captured.value
    assertNotNull("Submit must not be blocked", request)
    assertEquals("Edit without a title must not copy the description into the title", "", request?.title)
    assertEquals("Edit must keep the original description", "Monthly notes", request?.descriptionText)
  }
}
