package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.domain.utils.PersonBalanceCalculator
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [PersonRow] accessibility semantics (plans/011 Phase 3).
 *
 * Verifies that TalkBack and assistive technologies receive:
 * 1. Explicit directional `stateDescription` and `contentDescription`.
 * 2. Proper [Role.Button] and clickable action.
 * 3. Exact expected announcement text for DEBTOR, CREDITOR, and balanced rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PersonRowSemanticsTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Before
  fun setUp() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @Test
  fun debtorRowExposesPositiveBalanceAndDebtorSemantics() {
    var clicked = false
    val debtorBalance =
      PersonBalanceCalculator.PersonBalance(
        personId = 1L,
        personName = "علی رضایی",
        totalReceivables = 500_000L,
        totalDebts = 0L,
        netBalance = 500_000L,
        activeLoanCount = 1,
        settledLoanCount = 0,
      )

    composeRule.setContent {
      PersonRow(
        balance = debtorBalance,
        onClick = { clicked = true },
      )
    }

    val expectedContentDesc = "علی رضایی: بدهکار, موجودی ${CurrencyFormatter.format(debtorBalance.netBalance)}"
    val expectedStateDesc = "بدهکار — موجودی مثبت"

    val node =
      composeRule
        .onNodeWithContentDescription(expectedContentDesc)
        .assertIsDisplayed()

    node.assert(
      SemanticsMatcher.expectValue(
        SemanticsProperties.StateDescription,
        expectedStateDesc
      )
    )
    node.assert(
      SemanticsMatcher.expectValue(
        SemanticsProperties.Role,
        Role.Button
      )
    )
    node.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))

    node.performClick()
    assertTrue("Click action must be dispatched", clicked)
  }

  @Test
  fun creditorRowExposesNegativeBalanceAndCreditorSemantics() {
    var clickedCount = 0
    val creditorBalance =
      PersonBalanceCalculator.PersonBalance(
        personId = 2L,
        personName = "سارا محمدی",
        totalReceivables = 0L,
        totalDebts = 1_200_000L,
        netBalance = -1_200_000L,
        activeLoanCount = 1,
        settledLoanCount = 0,
      )

    composeRule.setContent {
      PersonRow(
        balance = creditorBalance,
        onClick = { clickedCount++ },
      )
    }

    val expectedContentDesc = "سارا محمدی: طلبکار, موجودی ${CurrencyFormatter.format(creditorBalance.netBalance)}"
    val expectedStateDesc = "طلبکار — موجودی منفی"

    val node =
      composeRule
        .onNodeWithContentDescription(expectedContentDesc)
        .assertIsDisplayed()

    node.assert(
      SemanticsMatcher.expectValue(
        SemanticsProperties.StateDescription,
        expectedStateDesc
      )
    )
    node.assert(
      SemanticsMatcher.expectValue(
        SemanticsProperties.Role,
        Role.Button
      )
    )
    node.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))

    node.performClick()
    assertEquals("Click action should execute once", 1, clickedCount)
  }

  @Test
  fun balancedRowExposesZeroBalanceAndBalancedSemantics() {
    val balancedPerson =
      PersonBalanceCalculator.PersonBalance(
        personId = 3L,
        personName = "مهدی کاظمی",
        totalReceivables = 0L,
        totalDebts = 0L,
        netBalance = 0L,
        activeLoanCount = 0,
        settledLoanCount = 2,
      )

    composeRule.setContent {
      PersonRow(
        balance = balancedPerson,
        onClick = {},
      )
    }

    val expectedContentDesc = "مهدی کاظمی: تعادل, موجودی ${CurrencyFormatter.format(balancedPerson.netBalance)}"
    val expectedStateDesc = "متعادل — موجودی صفر"

    val node =
      composeRule
        .onNodeWithContentDescription(expectedContentDesc)
        .assertIsDisplayed()

    node.assert(
      SemanticsMatcher.expectValue(
        SemanticsProperties.StateDescription,
        expectedStateDesc
      )
    )
    node.assert(
      SemanticsMatcher.expectValue(
        SemanticsProperties.Role,
        Role.Button
      )
    )
  }
}
