package io.github.mojri.hesabyar

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MoreMenuSheet] covering item selection and visibility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MoreMenuSheetTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val labelAnalytics by lazy { context.getString(R.string.nav_tab_analytics) }
  private val labelReports by lazy { context.getString(R.string.nav_tab_reports) }
  private val labelAccounts by lazy { context.getString(R.string.nav_tab_accounts) }
  private val labelSettings by lazy { context.getString(R.string.nav_tab_settings) }

  @Test
  fun hiddenSheetDoesNotRenderItems() {
    composeRule.setContent {
      MoreMenuSheet(
        show = false,
        onDismiss = {},
        onSelect = {},
        onSelectAccounts = {}
      )
    }

    composeRule.onNodeWithText(labelAnalytics).assertDoesNotExist()
    composeRule.onNodeWithText(labelReports).assertDoesNotExist()
    composeRule.onNodeWithText(labelAccounts).assertDoesNotExist()
    composeRule.onNodeWithText(labelSettings).assertDoesNotExist()
  }

  @Test
  fun selectAnalyticsInvokesCallback() {
    var selectedTab = ""

    composeRule.setContent {
      MoreMenuSheet(
        show = true,
        onDismiss = {},
        onSelect = { selectedTab = it },
        onSelectAccounts = {}
      )
    }

    composeRule.onNodeWithText(labelAnalytics).assertIsDisplayed()
    composeRule.onNodeWithText(labelAnalytics).performClick()
    composeRule.waitForIdle()

    assertEquals("Selected tab is ANALYTICS", TAB_ANALYTICS, selectedTab)
  }

  @Test
  fun selectReportsInvokesCallback() {
    var selectedTab = ""

    composeRule.setContent {
      MoreMenuSheet(
        show = true,
        onDismiss = {},
        onSelect = { selectedTab = it },
        onSelectAccounts = {}
      )
    }

    composeRule.onNodeWithText(labelReports).assertIsDisplayed()
    composeRule.onNodeWithText(labelReports).performClick()
    composeRule.waitForIdle()

    assertEquals("Selected tab is REPORTS", TAB_REPORTS, selectedTab)
  }

  @Test
  fun selectAccountsInvokesAccountsCallback() {
    var accountsSelected = false

    composeRule.setContent {
      MoreMenuSheet(
        show = true,
        onDismiss = {},
        onSelect = {},
        onSelectAccounts = { accountsSelected = true }
      )
    }

    composeRule.onNodeWithText(labelAccounts).assertIsDisplayed()
    composeRule.onNodeWithText(labelAccounts).performClick()
    composeRule.waitForIdle()

    assertTrue("Accounts callback invoked", accountsSelected)
  }

  @Test
  fun selectSettingsInvokesCallback() {
    var selectedTab = ""

    composeRule.setContent {
      MoreMenuSheet(
        show = true,
        onDismiss = {},
        onSelect = { selectedTab = it },
        onSelectAccounts = {}
      )
    }

    composeRule.onNodeWithText(labelSettings).assertIsDisplayed()
    composeRule.onNodeWithText(labelSettings).performClick()
    composeRule.waitForIdle()

    assertEquals("Selected tab is SETTINGS", TAB_SETTINGS, selectedTab)
  }
}
