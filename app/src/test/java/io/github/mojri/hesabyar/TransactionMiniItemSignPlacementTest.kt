package io.github.mojri.hesabyar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.screens.dashboard.components.TransactionMiniItem
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
 * Compose UI tests for [TransactionMiniItem] signed-amount rendering.
 *
 * Migrated from [io.github.mojri.hesabyar.ui.TransactionItemSignPlacementTest]
 * as part of issue #340 dead code cleanup.
 *
 * Verifies that:
 * 1. The minus sign for negative amounts renders to the LEFT of the amount digits in RTL layout.
 * 2. The plus sign for positive amounts renders to the LEFT of the amount digits in RTL layout.
 * 3. Separate Text composables are maintained for the sign and digits to prevent RTL bidi reversal.
 * 4. Transfer transactions render positive (+) sign instead of negative expense treatment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TransactionMiniItemSignPlacementTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Before
  fun setUp() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @After
  fun tearDown() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  private fun setAmountUnderRtl(
    amount: Long,
    type: TransactionType
  ) {
    val transaction =
      Transaction(
        id = 42L,
        type = type,
        categoryId = 1L,
        amount = amount,
        description = "خرید نان",
        date = 1752580800000L,
        accountId = 1L,
      )

    composeRule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        TransactionMiniItem(
          transaction = transaction,
          categories = emptyList(),
          accounts = emptyList(),
        )
      }
    }
  }

  @Test
  fun negativeAmountRendersMinusSignToTheLeftOfAmount() {
    setAmountUnderRtl(amount = 1_000_000L, type = TransactionType.EXPENSE)

    val signNode =
      composeRule
        .onNodeWithText("-", useUnmergedTree = true)
        .fetchSemanticsNode()
    val amountNode =
      composeRule
        .onNodeWithText("۱۰۰٬۰۰۰", substring = true, useUnmergedTree = true)
        .fetchSemanticsNode()

    assertTrue(
      "minus sign must render LEFT of the amount digits in RTL layout, " +
        "but sign.left=${signNode.boundsInRoot.left} " +
        "amount.left=${amountNode.boundsInRoot.left}",
      signNode.boundsInRoot.left < amountNode.boundsInRoot.left
    )
  }

  @Test
  fun positiveAmountRendersPlusSignToTheLeftOfAmount() {
    setAmountUnderRtl(amount = 1_000_000L, type = TransactionType.INCOME)

    val signNode =
      composeRule
        .onNodeWithText("+", useUnmergedTree = true)
        .fetchSemanticsNode()
    val amountNode =
      composeRule
        .onNodeWithText("۱۰۰٬۰۰۰", substring = true, useUnmergedTree = true)
        .fetchSemanticsNode()

    assertTrue(
      "plus sign must render LEFT of the amount digits in RTL layout, " +
        "but sign.left=${signNode.boundsInRoot.left} " +
        "amount.left=${amountNode.boundsInRoot.left}",
      signNode.boundsInRoot.left < amountNode.boundsInRoot.left
    )
  }

  @Test
  fun amountRowMaintainsSeparateSignAndAmountNodes() {
    setAmountUnderRtl(amount = 1_000_000L, type = TransactionType.EXPENSE)

    // The sign and the amount stay separate Text nodes (the BIDI safeguard
    // from formatSignedParts) — not a single concatenated string.
    assertEquals(
      "sign must be its own text node in unmerged tree",
      1,
      composeRule.onAllNodesWithText("-", useUnmergedTree = true).fetchSemanticsNodes().size
    )
    assertEquals(
      "amount must be its own text node in unmerged tree",
      1,
      composeRule
        .onAllNodesWithText("۱۰۰٬۰۰۰", substring = true, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size
    )

    composeRule.onNodeWithText("-", useUnmergedTree = true).assertIsDisplayed()
    composeRule.onNodeWithText("۱۰۰٬۰۰۰", substring = true, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun transferRendersPositiveAmountNotAsExpense() {
    val transaction =
      Transaction(
        id = 42L,
        type = TransactionType.TRANSFER,
        categoryId = 1L,
        amount = 1_000_000L,
        description = "انتقال",
        date = 1752580800000L,
        accountId = 1L,
        destinationAccountId = 2L,
      )

    composeRule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        TransactionMiniItem(
          transaction = transaction,
          categories = emptyList(),
          accounts = emptyList(),
        )
      }
    }

    composeRule.onNodeWithText("+", useUnmergedTree = true).assertIsDisplayed()
    assertEquals(
      "transfer must not render a minus-expense sign",
      0,
      composeRule.onAllNodesWithText("-", useUnmergedTree = true).fetchSemanticsNodes().size
    )
  }
}
