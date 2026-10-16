package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests for [HesabyarTabRow].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarTabRowTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun fixedTabRowRendersTabsAndEmitsClick() {
    var clickedIndex = -1

    composeRule.setContent {
      HesabyarTabRow(selectedTabIndex = 0, scrollable = false) {
        Tab(
          selected = true,
          onClick = { clickedIndex = 0 },
          text = { Text(TAB_FIRST) }
        )
        Tab(
          selected = false,
          onClick = { clickedIndex = 1 },
          text = { Text(TAB_SECOND) }
        )
      }
    }

    composeRule.onNodeWithText(TAB_FIRST).assertIsDisplayed()
    composeRule.onNodeWithText(TAB_SECOND).assertIsDisplayed().performClick()

    assertEquals("Clicking second tab emits index 1", 1, clickedIndex)
  }

  @Test
  fun scrollableTabRowRendersTabsAndEmitsClick() {
    var clickedSection: String? = null

    composeRule.setContent {
      HesabyarTabRow(selectedTabIndex = 0, scrollable = true) {
        LeadingIconTab(
          selected = true,
          onClick = { clickedSection = TAB_FIRST },
          text = { Text(TAB_FIRST) },
          icon = {}
        )
        LeadingIconTab(
          selected = false,
          onClick = { clickedSection = TAB_SECOND },
          text = { Text(TAB_SECOND) },
          icon = {}
        )
      }
    }

    composeRule.onNodeWithText(TAB_FIRST).assertIsDisplayed()
    composeRule.onNodeWithText(TAB_SECOND).assertIsDisplayed().performClick()

    assertEquals("Clicking second scrollable tab emits its section", TAB_SECOND, clickedSection)
  }

  @Test
  fun selectedTabUpdatesSelectionState() {
    composeRule.setContent {
      var selectedIndex by remember { mutableIntStateOf(0) }
      HesabyarTabRow(selectedTabIndex = selectedIndex) {
        Tab(
          selected = selectedIndex == 0,
          onClick = { selectedIndex = 0 },
          text = { Text(TAB_FIRST) }
        )
        Tab(
          selected = selectedIndex == 1,
          onClick = { selectedIndex = 1 },
          text = { Text(TAB_SECOND) }
        )
      }
    }

    composeRule.onNodeWithText(TAB_FIRST).assertIsSelected()

    composeRule.onNodeWithText(TAB_SECOND).performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithText(TAB_SECOND).assertIsSelected()
  }

  private companion object {
    const val TAB_FIRST = "تب اول"
    const val TAB_SECOND = "تب دوم"
  }
}
