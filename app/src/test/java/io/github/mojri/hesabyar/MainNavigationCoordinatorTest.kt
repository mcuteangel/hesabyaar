package io.github.mojri.hesabyar

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.ui.screens.DebtSection
import io.github.mojri.hesabyar.ui.screens.LoanDirectionFilter
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
  companion object {
    private const val MSG_DISPATCHER_NOT_CAPTURED = "Dispatcher was not captured"
    private const val MSG_CALLBACKS_NOT_CAPTURED = "Callbacks were not captured"
    private const val MSG_NAV_NOT_CAPTURED = "Navigation action was not captured"
    private const val MSG_ON_BACK_NOT_CAPTURED = "onBack action was not captured"
  }

  @get:Rule
  val composeRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val dialogTitleExit by lazy { context.getString(R.string.exit_dialog_title) }
  private val dialogButtonConfirmExit by lazy { context.getString(R.string.exit_dialog_confirm) }

  private val tagMain = "main_content"
  private val tagCategory = "category_content"
  private val tagAccount = "account_content"

  @Test
  fun backFromSecondTabReturnsToPreviousTab() {
    var observedTab = TAB_DASHBOARD
    var exitConfirmed = false
    var dispatcher: OnBackPressedDispatcher? = null
    var navToAssistant: (() -> Unit)? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
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

    val safeNav = checkNotNull(navToAssistant) { MSG_NAV_NOT_CAPTURED }
    composeRule.runOnUiThread { safeNav() }
    composeRule.waitForIdle()
    assertEquals(TAB_ASSISTANT, observedTab)

    val safeDispatcher = checkNotNull(dispatcher) { MSG_DISPATCHER_NOT_CAPTURED }
    composeRule.runOnUiThread { safeDispatcher.onBackPressed() }
    composeRule.waitForIdle()
    assertEquals(TAB_DASHBOARD, observedTab)
    assertEquals(false, exitConfirmed)
  }

  @Test
  fun backOnDashboardShowsExitDialogAndConfirmsExit() {
    var exitConfirmed = false
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
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

    val safeDispatcher = checkNotNull(dispatcher) { MSG_DISPATCHER_NOT_CAPTURED }
    composeRule.runOnUiThread { safeDispatcher.onBackPressed() }
    composeRule.waitForIdle()
    composeRule.onNodeWithText(dialogTitleExit).assertIsDisplayed()

    composeRule.onNodeWithText(dialogButtonConfirmExit).performClick()
    composeRule.waitForIdle()
    assertEquals(true, exitConfirmed)
  }

  @Test
  fun navigateToCategoriesOpensAndClosesOverlay() {
    var callbacks: MainNavCallbacks? = null
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        categoryContent = {
          Box(Modifier.fillMaxSize().testTag(tagCategory))
        },
        mainContent = { _, _, cbs ->
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    composeRule.onNodeWithTag(tagMain).assertIsDisplayed()

    val safeCallbacks = checkNotNull(callbacks) { MSG_CALLBACKS_NOT_CAPTURED }
    composeRule.runOnUiThread { safeCallbacks.onNavigateToCategories() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagCategory).assertIsDisplayed()

    val safeDispatcher = checkNotNull(dispatcher) { MSG_DISPATCHER_NOT_CAPTURED }
    composeRule.runOnUiThread { safeDispatcher.onBackPressed() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagCategory).assertDoesNotExist()
  }

  @Test
  fun navigateToAssistantAdvancesCurrentTab() {
    var observedTab = TAB_DASHBOARD
    var callbacks: MainNavCallbacks? = null
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
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

    val safeCallbacks = checkNotNull(callbacks) { MSG_CALLBACKS_NOT_CAPTURED }
    composeRule.runOnUiThread { safeCallbacks.onNavigateToAssistant() }
    composeRule.waitForIdle()
    assertEquals(TAB_ASSISTANT, observedTab)

    val safeDispatcher = checkNotNull(dispatcher) { MSG_DISPATCHER_NOT_CAPTURED }
    composeRule.runOnUiThread { safeDispatcher.onBackPressed() }
    composeRule.waitForIdle()
    assertEquals(TAB_DASHBOARD, observedTab)
  }

  @Test
  fun tabSelectedResetsPersonSearchAndSwitchesTab() {
    var searchResetCount = 0
    var observedTab = TAB_DASHBOARD
    var callbacks: MainNavCallbacks? = null

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

    val safeCallbacks = checkNotNull(callbacks) { MSG_CALLBACKS_NOT_CAPTURED }
    composeRule.runOnUiThread { safeCallbacks.onShowDebtors() }
    composeRule.waitForIdle()

    assertEquals(TAB_DEBTS, observedTab)
    assertEquals(1, searchResetCount)
  }

  @Test
  fun navigateToAccountsOpensAndClosesOverlayViaBack() {
    var callbacks: MainNavCallbacks? = null
    var dispatcher: OnBackPressedDispatcher? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        accountContent = {
          Box(Modifier.fillMaxSize().testTag(tagAccount))
        },
        mainContent = { _, _, cbs ->
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    composeRule.onNodeWithTag(tagMain).assertIsDisplayed()

    val safeCallbacks = checkNotNull(callbacks) { MSG_CALLBACKS_NOT_CAPTURED }
    composeRule.runOnUiThread { safeCallbacks.onNavigateToAccounts() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagAccount).assertIsDisplayed()

    val safeDispatcher = checkNotNull(dispatcher) { MSG_DISPATCHER_NOT_CAPTURED }
    composeRule.runOnUiThread { safeDispatcher.onBackPressed() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagAccount).assertDoesNotExist()
  }

  @Test
  fun accountContentOnBackClosesOverlay() {
    var callbacks: MainNavCallbacks? = null
    var accountOnBack: (() -> Unit)? = null

    composeRule.setContent {
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        accountContent = { onBack ->
          accountOnBack = onBack
          Box(Modifier.fillMaxSize().testTag(tagAccount))
        },
        mainContent = { _, _, cbs ->
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    val safeCallbacks = checkNotNull(callbacks) { MSG_CALLBACKS_NOT_CAPTURED }
    composeRule.runOnUiThread { safeCallbacks.onNavigateToAccounts() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagAccount).assertIsDisplayed()

    val safeOnBack = checkNotNull(accountOnBack) { MSG_ON_BACK_NOT_CAPTURED }
    composeRule.runOnUiThread { safeOnBack() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagAccount).assertDoesNotExist()
  }

  @Test
  fun categoryContentOnBackClosesOverlay() {
    var callbacks: MainNavCallbacks? = null
    var categoryOnBack: (() -> Unit)? = null

    composeRule.setContent {
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        categoryContent = { onBack ->
          categoryOnBack = onBack
          Box(Modifier.fillMaxSize().testTag(tagCategory))
        },
        mainContent = { _, _, cbs ->
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    val safeCallbacks = checkNotNull(callbacks) { MSG_CALLBACKS_NOT_CAPTURED }
    composeRule.runOnUiThread { safeCallbacks.onNavigateToCategories() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagCategory).assertIsDisplayed()

    val safeOnBack = checkNotNull(categoryOnBack) { MSG_ON_BACK_NOT_CAPTURED }
    composeRule.runOnUiThread { safeOnBack() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag(tagCategory).assertDoesNotExist()
  }

  @Test
  fun onShowCreditorsAndDebtsStateChangeUpdateCoordinatorState() {
    var observedTab = TAB_DASHBOARD
    var observedDebtsState = DebtsTabState(DebtSection.INSTALLMENTS, LoanDirectionFilter.ALL)
    var callbacks: MainNavCallbacks? = null

    composeRule.setContent {
      MainNavigationCoordinator(
        startTab = TAB_DASHBOARD,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        mainContent = { currentTab, debtsState, cbs ->
          observedTab = currentTab
          observedDebtsState = debtsState
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    val safeCallbacks = checkNotNull(callbacks) { MSG_CALLBACKS_NOT_CAPTURED }
    composeRule.runOnUiThread { safeCallbacks.onShowCreditors() }
    composeRule.waitForIdle()

    assertEquals("Tab switched to DEBTS", TAB_DEBTS, observedTab)
    assertEquals("Section switched to PERSONS", DebtSection.PERSONS, observedDebtsState.section)
    assertEquals("Filter switched to CREDITOR", LoanDirectionFilter.CREDITOR, observedDebtsState.filter)

    val updatedState = DebtsTabState(DebtSection.BANK_LOANS, LoanDirectionFilter.ALL)
    composeRule.runOnUiThread { safeCallbacks.onDebtsStateChange(updatedState) }
    composeRule.waitForIdle()

    assertEquals("Debts state updated", updatedState, observedDebtsState)
  }

  @Test
  fun backReturningToDebtsTabResetsPersonSearch() {
    var searchResetCount = 0
    var dispatcher: OnBackPressedDispatcher? = null
    var callbacks: MainNavCallbacks? = null

    composeRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      MainNavigationCoordinator(
        startTab = TAB_DEBTS,
        startDebtSection = DebtSection.INSTALLMENTS,
        onExitConfirmed = {},
        onResetPersonSearch = { searchResetCount++ },
        mainContent = { _, _, cbs ->
          callbacks = cbs
          Box(Modifier.fillMaxSize().testTag(tagMain))
        }
      )
    }

    val safeCallbacks = checkNotNull(callbacks) { MSG_CALLBACKS_NOT_CAPTURED }
    composeRule.runOnUiThread { safeCallbacks.onNavigateToAssistant() }
    composeRule.waitForIdle()

    val countBeforeBack = searchResetCount
    val safeDispatcher = checkNotNull(dispatcher) { MSG_DISPATCHER_NOT_CAPTURED }
    composeRule.runOnUiThread { safeDispatcher.onBackPressed() }
    composeRule.waitForIdle()

    assertEquals("Search reset on returning to DEBTS tab", countBeforeBack + 1, searchResetCount)
  }
}
