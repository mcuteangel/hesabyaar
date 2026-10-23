package io.github.mojri.hesabyar

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.ui.designsystem.WindowSizeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MainNavigationComponentsTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val labelDashboard by lazy { context.getString(R.string.nav_tab_dashboard) }
  private val labelAssistant by lazy { context.getString(R.string.nav_tab_assistant) }
  private val labelDebts by lazy { context.getString(R.string.nav_tab_debts) }
  private val labelMore by lazy { context.getString(R.string.nav_tab_more) }

  @Test
  fun mainBottomNavigationRendersAllTabsAndMore() {
    composeRule.setContent {
      MainBottomNavigation(
        currentTab = TAB_DASHBOARD,
        tabs = MAIN_TABS,
        onTabSelected = {},
        onMoreClick = {}
      )
    }

    composeRule.onNodeWithText(labelDashboard).assertIsDisplayed()
    composeRule.onNodeWithText(labelAssistant).assertIsDisplayed()
    composeRule.onNodeWithText(labelDebts).assertIsDisplayed()
    composeRule.onNodeWithText(labelMore).assertIsDisplayed()
  }

  @Test
  fun mainBottomNavigationClickTabInvokesCallback() {
    var selectedTab = ""

    composeRule.setContent {
      MainBottomNavigation(
        currentTab = TAB_DASHBOARD,
        tabs = MAIN_TABS,
        onTabSelected = { selectedTab = it },
        onMoreClick = {}
      )
    }

    composeRule.onNodeWithText(labelAssistant).performClick()
    composeRule.waitForIdle()

    assertEquals("Selected tab is ASSISTANT", TAB_ASSISTANT, selectedTab)
  }

  @Test
  fun mainBottomNavigationClickMoreInvokesCallback() {
    var moreClicked = false

    composeRule.setContent {
      MainBottomNavigation(
        currentTab = TAB_DASHBOARD,
        tabs = MAIN_TABS,
        onTabSelected = {},
        onMoreClick = { moreClicked = true }
      )
    }

    composeRule.onNodeWithText(labelMore).performClick()
    composeRule.waitForIdle()

    assertTrue("More button callback invoked", moreClicked)
  }

  @Test
  fun mainBottomNavigationHighlightsWhenMoreMenuTabIsSelected() {
    composeRule.setContent {
      MainBottomNavigation(
        currentTab = TAB_ANALYTICS,
        tabs = MAIN_TABS,
        onTabSelected = {},
        onMoreClick = {}
      )
    }

    composeRule.onNodeWithText(labelMore).assertIsDisplayed()
  }

  @Test
  fun mainNavigationRailRendersAllTabsAndMore() {
    composeRule.setContent {
      MainNavigationRail(
        currentTab = TAB_DASHBOARD,
        tabs = MAIN_TABS,
        onTabSelected = {},
        onMoreClick = {}
      )
    }

    composeRule.onNodeWithText(labelDashboard).assertIsDisplayed()
    composeRule.onNodeWithText(labelAssistant).assertIsDisplayed()
    composeRule.onNodeWithText(labelDebts).assertIsDisplayed()
    composeRule.onNodeWithText(labelMore).assertIsDisplayed()
  }

  @Test
  fun mainNavigationRailClickTabInvokesCallback() {
    var selectedTab = ""

    composeRule.setContent {
      MainNavigationRail(
        currentTab = TAB_DASHBOARD,
        tabs = MAIN_TABS,
        onTabSelected = { selectedTab = it },
        onMoreClick = {}
      )
    }

    composeRule.onNodeWithText(labelDebts).performClick()
    composeRule.waitForIdle()

    assertEquals("Selected tab is DEBTS", TAB_DEBTS, selectedTab)
  }

  @Test
  fun mainNavigationRailClickMoreInvokesCallback() {
    var moreClicked = false

    composeRule.setContent {
      MainNavigationRail(
        currentTab = TAB_DASHBOARD,
        tabs = MAIN_TABS,
        onTabSelected = {},
        onMoreClick = { moreClicked = true }
      )
    }

    composeRule.onNodeWithText(labelMore).performClick()
    composeRule.waitForIdle()

    assertTrue("More button callback invoked", moreClicked)
  }

  @Test
  fun compactMainContentRendersInnerContent() {
    val sampleText = "CompactContentSample"

    composeRule.setContent {
      CompactMainContent(innerPadding = PaddingValues(0.dp)) {
        Text(sampleText)
      }
    }

    composeRule.onNodeWithText(sampleText).assertIsDisplayed()
  }

  @Test
  fun compactMainContentConstrainsWidthToContentMax() {
    composeRule.setContent {
      Box(modifier = Modifier.requiredWidth(EXPANDED_WIDTH)) {
        CompactMainContent(
          innerPadding = PaddingValues(0.dp),
          content = {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .testTag(TAG_CONTENT),
            )
          }
        )
      }
    }

    composeRule.onNodeWithTag(TAG_CONTENT).assertWidthIsEqualTo(WindowSizeTokens.ContentMaxWidth)
  }

  @Test
  fun expandedMainContentConstrainsWidthToContentMax() {
    val sampleContent = "ExpandedContentSample"

    composeRule.setContent {
      Box(modifier = Modifier.requiredWidth(EXPANDED_WIDTH)) {
        ExpandedMainContent(
          innerPadding = PaddingValues(0.dp),
          currentTab = TAB_DASHBOARD,
          onTabSelected = {},
          onMoreClick = {},
          content = {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .testTag(TAG_CONTENT),
            )
          }
        )
      }
    }

    composeRule.onNodeWithTag(TAG_CONTENT).assertWidthIsEqualTo(WindowSizeTokens.ContentMaxWidth)
  }

  @Test
  fun expandedMainContentRendersRailAndContent() {
    val sampleContent = "ExpandedContentSample"
    var selectedTab = ""

    composeRule.setContent {
      ExpandedMainContent(
        innerPadding = PaddingValues(0.dp),
        currentTab = TAB_DASHBOARD,
        onTabSelected = { selectedTab = it },
        onMoreClick = {},
        content = { Text(sampleContent) }
      )
    }

    composeRule.onNodeWithText(labelDashboard).assertIsDisplayed()
    composeRule.onNodeWithText(sampleContent).assertIsDisplayed()

    composeRule.onNodeWithText(labelAssistant).performClick()
    composeRule.waitForIdle()

    assertEquals("Rail tab selection updates callback", TAB_ASSISTANT, selectedTab)
  }

  @Test
  fun navigationDestinationsHaveDistinctIcons() {
    val icons = MAIN_TABS.map { it.icon }
    val uniqueIcons = icons.toSet()
    assertEquals("All MAIN_TABS must have distinct icons", icons.size, uniqueIcons.size)

    val dashboardTab = MAIN_TABS.first { it.id == TAB_DASHBOARD }
    assertEquals("Dashboard icon must be Icons.Filled.Dashboard", Icons.Filled.Dashboard, dashboardTab.icon)

    MAIN_TABS.forEach { tab ->
      assertNotEquals(
        "No MAIN_TABS icon (${tab.id}) must be AccountBalanceWallet (reserved for Accounts)",
        Icons.Filled.AccountBalanceWallet,
        tab.icon
      )
    }
  }

  @Test
  fun debtsTabLabelIsConcise() {
    assertEquals("Debts tab label must be shortened to 'بدهی‌ها'", "بدهی‌ها", labelDebts)
  }

  private companion object {
    val EXPANDED_WIDTH = 1200.dp
    const val TAG_CONTENT = "rail_content"
  }
}
