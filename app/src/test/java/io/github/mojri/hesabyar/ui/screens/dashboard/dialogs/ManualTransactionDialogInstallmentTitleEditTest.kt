package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Compose UI tests for the INSTALLMENT edit path in [ManualTransactionDialog]:
 * the title field must stay independent of the description field. Before the
 * fix, titleText was seeded from transactionToEdit?.description, the same
 * source as descriptionText, so an edit showed identical text in both fields
 * and could overwrite the installment title and notes identically. The title
 * now comes from the dedicated installmentTitle parameter instead.
 * The title field itself is not rendered in edit mode (the edit-mode type
 * selector excludes INSTALLMENT), so the assertions read the captured
 * submit request rather than a visible title node.
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
