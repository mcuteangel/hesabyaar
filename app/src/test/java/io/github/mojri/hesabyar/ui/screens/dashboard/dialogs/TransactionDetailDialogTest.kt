package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TransactionDetailDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val sampleCategory =
    Category(
      id = 1L,
      name = "خوراکی",
      key = "Food",
      icon = "Restaurant",
      color = 0xFF4CAF50L,
      type = CategoryType.EXPENSE
    )

  private val sourceAccount =
    AccountEntity(
      id = 10L,
      name = "بانک ملی",
      type = AccountType.BANK,
      initialBalance = 1_000_000L
    )

  private val destAccount =
    AccountEntity(
      id = 20L,
      name = "کیف پول",
      type = AccountType.CASH_WALLET,
      initialBalance = 200_000L
    )

  @Test
  fun rendersExpenseTransactionWithCategoryAndInvokesActionCallbacks() {
    var editClicked = false
    var deleteClicked = false
    var dismissClicked = false

    val transaction =
      Transaction(
        id = 101L,
        type = TransactionType.EXPENSE,
        categoryId = sampleCategory.id,
        amount = 50_000L,
        description = "خرید سوپرمارکت",
        accountId = sourceAccount.id
      )

    composeRule.setContent {
      TransactionDetailDialog(
        transaction = transaction,
        categories = listOf(sampleCategory),
        accounts = listOf(sourceAccount, destAccount),
        onEdit = { editClicked = true },
        onDelete = { deleteClicked = true },
        onDismiss = { dismissClicked = true }
      )
    }

    composeRule.onNodeWithText("جزئیات تراکنش").assertIsDisplayed()
    composeRule.onNodeWithText("هزینه").assertIsDisplayed()
    composeRule.onNodeWithText("خوراکی").assertIsDisplayed()
    composeRule.onNodeWithText("خرید سوپرمارکت").assertIsDisplayed()
    composeRule.onNodeWithText("بانک ملی").assertIsDisplayed()

    composeRule.onNodeWithText("ویرایش").assertIsDisplayed().performClick()
    assertTrue("onEdit callback must be invoked", editClicked)

    composeRule.onNodeWithText("حذف").assertIsDisplayed().performClick()
    assertTrue("onDelete callback must be invoked", deleteClicked)

    composeRule.onNodeWithText("بستن").assertIsDisplayed().performClick()
    assertTrue("onDismiss callback must be invoked", dismissClicked)
  }

  @Test
  fun rendersIncomeTransactionWithFallbackCategoryWhenCategoryNotFound() {
    val transaction =
      Transaction(
        id = 102L,
        type = TransactionType.INCOME,
        categoryId = 999L,
        amount = 120_000L,
        description = "واریز حقوق",
        accountId = sourceAccount.id
      )

    composeRule.setContent {
      TransactionDetailDialog(
        transaction = transaction,
        categories = emptyList(),
        accounts = listOf(sourceAccount),
        onEdit = {},
        onDelete = {},
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText("درآمد").assertIsDisplayed()
    composeRule.onNodeWithText("سایر").assertIsDisplayed()
    composeRule.onNodeWithText("واریز حقوق").assertIsDisplayed()
  }

  @Test
  fun rendersTransferTransactionWithSourceAndDestinationAccountArrow() {
    val transaction =
      Transaction(
        id = 103L,
        type = TransactionType.TRANSFER,
        categoryId = 0L,
        amount = 300_000L,
        description = "انتقال داخلی",
        accountId = sourceAccount.id,
        destinationAccountId = destAccount.id
      )

    composeRule.setContent {
      TransactionDetailDialog(
        transaction = transaction,
        categories = emptyList(),
        accounts = listOf(sourceAccount, destAccount),
        onEdit = {},
        onDelete = {},
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText("انتقال وجه").assertIsDisplayed()
    composeRule.onNodeWithText("بانک ملی → کیف پول").assertIsDisplayed()
  }

  @Test
  fun rendersTransferTransactionWithMissingDestinationAccount() {
    val transaction =
      Transaction(
        id = 104L,
        type = TransactionType.TRANSFER,
        categoryId = 0L,
        amount = 40_000L,
        description = "انتقال نامشخص",
        accountId = sourceAccount.id,
        destinationAccountId = 888L
      )

    composeRule.setContent {
      TransactionDetailDialog(
        transaction = transaction,
        categories = emptyList(),
        accounts = listOf(sourceAccount),
        onEdit = {},
        onDelete = {},
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText("انتقال وجه").assertIsDisplayed()
    composeRule.onNodeWithText("بانک ملی → (نامشخص)").assertIsDisplayed()
  }
}
