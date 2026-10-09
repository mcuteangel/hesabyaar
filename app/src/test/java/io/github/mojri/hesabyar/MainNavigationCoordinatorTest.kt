package io.github.mojri.hesabyar

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.ui.screens.DebtSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives [MainNavigationCoordinator]'s back history and overlay flags without
 * the heavy Hilt ViewModel graph, covering the navigation wiring that the
 * lower-level [TabBackHandler] tests do not exercise end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MainNavigationCoordinatorTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val tagMain = "main_content"
  private val tagCategory = "category_content"
  private val tagAccount = "account_content"

  @Test
  fun backFromSecondTabReturnsToPreviousTab() {
    var observedTab = TAB_DASHBOARD
    var exitConfirmed = false
    lateinit var dispatcher: OnBackPressedDispatcher
    var navToAssistant: (() -> Unit)? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = { exitConfirmed = true },
        mainContent = { currentTab, _, callbacks ->
          observedTab = currentTab
          navToAssistant = callbacks.onNavigateToAssistant
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }
    composeRule.onNodeWithTag(tagMain).assertIsDisplayed()

    composeRule.runOnUiThread { navToAssistant!!() }
    composeRule.waitForIdle()
    assertEquals(TAB_ASSISTANT, observedTab)

    composeRule.runOnUiThread { dispatcher.onBackPressed() }
    composeRule.waitForIdle()
    assertEquals(TAB_DASHBOARD, observedTab)
    assertEquals(false, exitConfirmed)
  }

  @Test
  fun backOnDashboardShowsExitDialogAndConfirmsExit() {
    var exitConfirmed = false
    lateinit var dispatcher: OnBackPressedDispatcher

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = { exitConfirmed = true },
        mainContent = { _, _, _ ->
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }
    composeRule.onNodeWithTag(tagMain).assertIsDisplayed()

    composeRule.runOnUiThread { dispatcher.onBackPressed() }
    composeRule.waitForIdle()
    composeRule.onNodeWithText("خروج از حسابیار").assertIsDisplayed()

    composeRule.onNodeWithText("خروج").performClick()
    composeRule.waitForIdle()
    assertEquals(true, exitConfirmed)
  }

  @Test
  fun navigateToCategoriesOpensAndClosesOverlay() {
    var overlayDrawn = false
    lateinit var callbacks: MainNavCallbacks
    lateinit var dispatcher: OnBackPressedDispatcher

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        categoryContent = { onBack ->
          overlayDrawn = true
          Box(Modifier.fillMaxSize().testTag(tagCategory))
        },
        mainContent = { _, _, cbs ->
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    composeRule.onNodeWithTag(tagMain).assertIsDisplayed()

    composeRule.runOnUiThread { callbacks.onNavigateToCategories() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagCategory).assertIsDisplayed()

    // Back on the overlay should close it without exiting.
    var exitConfirmed = false
    composeRule.runOnUiThread { dispatcher.onBackPressed() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagCategory).assertDoesNotExist()
  }

  @Test
  fun navigateToAssistantAdvancesCurrentTab() {
    var observedTab = TAB_DASHBOARD
    lateinit var callbacks: MainNavCallbacks
    lateinit var dispatcher: OnBackPressedDispatcher

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        mainContent = { currentTab, _, cbs ->
          observedTab = currentTab
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    composeRule.runOnUiThread { callbacks.onNavigateToAssistant() }
    composeRule.waitForIdle()
    assertEquals(TAB_ASSISTANT, observedTab)

    composeRule.runOnUiThread { dispatcher.onBackPressed() }
    composeRule.waitForIdle()
    assertEquals(TAB_DASHBOARD, observedTab)
  }

  @Test
  fun tabSelectedResetsPersonSearchAndSwitchesTab() {
    var searchResetCount = 0
    var observedTab = TAB_DASHBOARD
    lateinit var callbacks: MainNavCallbacks

    composeRule.setContent {
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        onResetPersonSearch = { searchResetCount++ },
        mainContent = { currentTab, _, cbs ->
          observedTab = currentTab
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    composeRule.runOnUiThread { callbacks.onShowDebtors() }
    composeRule.waitForIdle()

    assertEquals(TAB_DEBTS, observedTab)
    assertEquals(1, searchResetCount)
  }
}
