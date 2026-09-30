package io.github.mojri.hesabyar.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DebtHubTabBarTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun allSectionTabsAreDisplayed() {
    composeRule.setContent {
      DebtHubTabBar(
        section = DebtSection.INSTALLMENTS,
        onSectionChange = {}
      )
    }

    composeRule.onNodeWithText("اقساط").assertIsDisplayed()
    composeRule.onNodeWithText("وام بانکی").assertIsDisplayed()
    composeRule.onNodeWithText("اشخاص").assertIsDisplayed()
  }

  @Test
  fun clickingBankLoansTabEmitsBankLoansSection() {
    var selectedSection: DebtSection? = null

    composeRule.setContent {
      DebtHubTabBar(
        section = DebtSection.INSTALLMENTS,
        onSectionChange = { selectedSection = it }
      )
    }

    composeRule.onNodeWithText("وام بانکی").performClick()
    assertEquals("Selected section should be BANK_LOANS", DebtSection.BANK_LOANS, selectedSection)
  }

  @Test
  fun clickingPersonsTabEmitsPersonsSection() {
    var selectedSection: DebtSection? = null

    composeRule.setContent {
      DebtHubTabBar(
        section = DebtSection.INSTALLMENTS,
        onSectionChange = { selectedSection = it }
      )
    }

    composeRule.onNodeWithText("اشخاص").performClick()
    assertEquals("Selected section should be PERSONS", DebtSection.PERSONS, selectedSection)
  }

  @Test
  fun clickingInstallmentsTabEmitsInstallmentsSection() {
    var selectedSection: DebtSection? = null

    composeRule.setContent {
      DebtHubTabBar(
        section = DebtSection.PERSONS,
        onSectionChange = { selectedSection = it }
      )
    }

    composeRule.onNodeWithText("اقساط").performClick()
    assertEquals("Selected section should be INSTALLMENTS", DebtSection.INSTALLMENTS, selectedSection)
  }

  @Test
  fun selectedTabUpdatesWhenSectionChanges() {
    val sectionState = mutableStateOf(DebtSection.INSTALLMENTS)

    composeRule.setContent {
      DebtHubTabBar(
        section = sectionState.value,
        onSectionChange = { sectionState.value = it }
      )
    }

    composeRule.onNodeWithText("اشخاص").performClick()
    composeRule.waitForIdle()
    assertEquals("Active section is updated to PERSONS", DebtSection.PERSONS, sectionState.value)
  }
}
