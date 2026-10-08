package io.github.mojri.hesabyar

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [TabBackHandler] and [ExitConfirmDialog] wiring.
 *
 * Verifies system back walking tab history, resetting person search on restoring
 * the debts tab, showing the exit dialog on dashboard, and confirm/dismiss exit behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MainActivityBackNavigationTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun backWalksHistoryWhenMultipleTabsVisited() {
    var history by mutableStateOf(listOf(TAB_DASHBOARD, TAB_DEBTS, TAB_REPORTS))
    var exitConfirmed = false
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      TabBackHandler(
        tabHistoryProvider = { history },
        onHistoryChange = { history = it },
        onExitConfirmed = { exitConfirmed = true }
      )
    }

    assertNotNull("OnBackPressedDispatcher should be provided", dispatcher)
    composeRule.runOnUiThread { dispatcher?.onBackPressed() }
    composeRule.waitForIdle()

    assertEquals(
      "History dropped top tab and returned to DEBTS",
      listOf(TAB_DASHBOARD, TAB_DEBTS),
      history
    )
    assertFalse("Exit should not be confirmed", exitConfirmed)
  }

  @Test
  fun backResetsPersonSearchWhenRestoringDebtsTab() {
    var history by mutableStateOf(listOf(TAB_DASHBOARD, TAB_DEBTS, TAB_REPORTS))
    var searchResetCalled = false
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      TabBackHandler(
        tabHistoryProvider = { history },
        onHistoryChange = { history = it },
        onExitConfirmed = {},
        onResetPersonSearch = { searchResetCalled = true }
      )
    }

    assertNotNull("OnBackPressedDispatcher should be provided", dispatcher)
    composeRule.runOnUiThread { dispatcher?.onBackPressed() }
    composeRule.waitForIdle()

    assertTrue("Search query reset when returning to DEBTS", searchResetCalled)
  }

  @Test
  fun backOnDashboardShowsExitDialog() {
    var history by mutableStateOf(listOf(TAB_DASHBOARD))
    var exitConfirmed = false
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      TabBackHandler(
        tabHistoryProvider = { history },
        onHistoryChange = { history = it },
        onExitConfirmed = { exitConfirmed = true }
      )
    }

    assertNotNull("OnBackPressedDispatcher should be provided", dispatcher)
    composeRule.runOnUiThread { dispatcher?.onBackPressed() }
    composeRule.waitForIdle()

    composeRule.onNodeWithText("خروج از حسابیار").assertIsDisplayed()
    composeRule.onNodeWithText("می‌خواهید از برنامه خارج شوید؟").assertIsDisplayed()
    assertFalse("Exit not confirmed before user action", exitConfirmed)
  }

  @Test
  fun confirmingExitDialogInvokesExitCallback() {
    var history by mutableStateOf(listOf(TAB_DASHBOARD))
    var exitConfirmed = false
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      TabBackHandler(
        tabHistoryProvider = { history },
        onHistoryChange = { history = it },
        onExitConfirmed = { exitConfirmed = true }
      )
    }

    assertNotNull("OnBackPressedDispatcher should be provided", dispatcher)
    composeRule.runOnUiThread { dispatcher?.onBackPressed() }
    composeRule.waitForIdle()

    composeRule.onNodeWithText("خروج").performClick()
    composeRule.waitForIdle()

    assertTrue("Exit callback invoked after clicking confirm", exitConfirmed)
  }

  @Test
  fun dismissingExitDialogHidesItWithoutExiting() {
    var history by mutableStateOf(listOf(TAB_DASHBOARD))
    var exitConfirmed = false
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      TabBackHandler(
        tabHistoryProvider = { history },
        onHistoryChange = { history = it },
        onExitConfirmed = { exitConfirmed = true }
      )
    }

    assertNotNull("OnBackPressedDispatcher should be provided", dispatcher)
    composeRule.runOnUiThread { dispatcher?.onBackPressed() }
    composeRule.waitForIdle()

    composeRule.onNodeWithText("انصراف").performClick()
    composeRule.waitForIdle()

    assertFalse("Exit callback should not be invoked on dismiss", exitConfirmed)
    composeRule.onNodeWithText("خروج از حسابیار").assertDoesNotExist()
  }

  @Test
  fun standaloneExitConfirmDialogTriggersConfirm() {
    var confirmed = false

    composeRule.setContent {
      ExitConfirmDialog(
        onConfirm = { confirmed = true },
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText("خروج").performClick()
    composeRule.waitForIdle()
    assertTrue("Confirm callback called", confirmed)
  }

  @Test
  fun standaloneExitConfirmDialogTriggersDismiss() {
    var dismissed = false

    composeRule.setContent {
      ExitConfirmDialog(
        onConfirm = {},
        onDismiss = { dismissed = true }
      )
    }

    composeRule.onNodeWithText("انصراف").performClick()
    composeRule.waitForIdle()
    assertTrue("Dismiss callback called", dismissed)
  }
}
