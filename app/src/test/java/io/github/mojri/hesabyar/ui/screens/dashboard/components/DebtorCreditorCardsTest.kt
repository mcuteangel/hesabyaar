package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.DashboardData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DebtorCreditorCardsTest {
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
  fun clickableCardsDispatchCallbacksAndExposeButtonRole() {
    var debtorsClicked = false
    var creditorsClicked = false
    val data =
      DashboardData(
        debtorsTotal = 1_000_000L,
        creditorsTotal = 2_000_000L,
      )

    composeRule.setContent {
      DebtorCreditorCards(
        dashboardData = data,
        onDebtorsClick = { debtorsClicked = true },
        onCreditorsClick = { creditorsClicked = true },
      )
    }

    composeRule.onNodeWithText("بدهکاران").assertIsDisplayed()
    composeRule.onNodeWithText("طلبکاران").assertIsDisplayed()

    // Assert cards expose Button role and clickable semantics
    val buttonNodes =
      composeRule
        .onAllNodes(
          SemanticsMatcher
            .expectValue(SemanticsProperties.Role, Role.Button)
            .and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        ).fetchSemanticsNodes()

    assertEquals("Expected 2 clickable cards with button role", 2, buttonNodes.size)

    // Click the first card (debtors)
    composeRule
      .onAllNodes(
        SemanticsMatcher
          .expectValue(SemanticsProperties.Role, Role.Button)
          .and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
      )[0]
      .performClick()

    assertTrue("Debtors callback was called", debtorsClicked)

    // Click the second card (creditors)
    composeRule
      .onAllNodes(
        SemanticsMatcher
          .expectValue(SemanticsProperties.Role, Role.Button)
          .and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
      )[1]
      .performClick()

    assertTrue("Creditors callback was called", creditorsClicked)
  }

  @Test
  fun nullCallbacksDoNotExposeClickAction() {
    val data =
      DashboardData(
        debtorsTotal = 500_000L,
        creditorsTotal = 500_000L,
      )

    composeRule.setContent {
      DebtorCreditorCards(
        dashboardData = data,
        onDebtorsClick = null,
        onCreditorsClick = null,
      )
    }

    composeRule.onNodeWithText("بدهکاران").assertIsDisplayed()
    composeRule.onNodeWithText("طلبکاران").assertIsDisplayed()

    // When callbacks are null, cards should not have Role.Button or OnClick
    val buttonNodes =
      composeRule
        .onAllNodes(
          SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        ).fetchSemanticsNodes()

    assertEquals("No button-role nodes when callbacks null", 0, buttonNodes.size)
  }
}
