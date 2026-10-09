package io.github.mojri.hesabyar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.screens.DebtSection
import io.github.mojri.hesabyar.ui.screens.LoanDirectionFilter

internal data class DebtsTabState(
  val section: DebtSection,
  val filter: LoanDirectionFilter,
)

internal val TabHistorySaver: Saver<List<String>, Any> =
  listSaver(
    save = { it.toList() },
    restore = { it.filterIsInstance<String>().ifEmpty { listOf(TAB_DASHBOARD) } }
  )

internal val DebtsTabStateSaver: Saver<DebtsTabState, Any> =
  listSaver(
    save = { listOf(it.section.name, it.filter.name) },
    restore = {
      val rawSection = it.getOrNull(0) ?: ""
      val rawFilter = it.getOrNull(1) ?: ""
      val section =
        runCatching {
          DebtSection.valueOf(rawSection)
        }.getOrDefault(DebtSection.INSTALLMENTS)
      val filter =
        runCatching {
          LoanDirectionFilter.valueOf(rawFilter)
        }.getOrDefault(LoanDirectionFilter.ALL)
      DebtsTabState(section = section, filter = filter)
    }
  )

internal fun resolveInitialNavigation(openTab: String?): Pair<String, DebtSection> {
  val startTab =
    when (openTab) {
      DEEP_LINK_LOANS,
      DEEP_LINK_INSTALLMENTS,
      DEEP_LINK_BANK_LOANS,
      DEEP_LINK_DEBTS,
      DEEP_LINK_PERSONS -> TAB_DEBTS

      else -> TAB_DASHBOARD
    }
  val startDebtSection =
    when (openTab) {
      DEEP_LINK_LOANS, DEEP_LINK_PERSONS -> DebtSection.PERSONS
      DEEP_LINK_BANK_LOANS -> DebtSection.BANK_LOANS
      else -> DebtSection.INSTALLMENTS
    }
  return startTab to startDebtSection
}

@Composable
internal fun ExitConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  ConfirmDialog(
    title = stringResource(R.string.exit_dialog_title),
    message = stringResource(R.string.exit_dialog_message),
    confirmText = stringResource(R.string.exit_dialog_confirm),
    dismissText = stringResource(R.string.cancel_label),
    onConfirm = onConfirm,
    onDismiss = onDismiss
  )
}

/**
 * System back walks the visited tabs, then returns to the dashboard, and
 * only there asks before leaving the app.
 */
@Composable
internal fun TabBackHandler(
  tabHistoryProvider: () -> List<String>,
  onHistoryChange: (List<String>) -> Unit,
  onExitConfirmed: () -> Unit,
  onResetPersonSearch: () -> Unit = {},
) {
  // Preserved across configuration changes so an in-flight exit dialog is not lost on rotation.
  var showExitDialog by rememberSaveable { mutableStateOf(false) }

  BackHandler {
    val current = tabHistoryProvider()
    val previous = current.popTab(home = TAB_DASHBOARD)
    if (previous != null) {
      if (previous.lastOrNull() == TAB_DEBTS) {
        onResetPersonSearch()
      }
      onHistoryChange(previous)
    } else {
      showExitDialog = true
    }
  }

  if (showExitDialog) {
    ExitConfirmDialog(
      onConfirm = {
        showExitDialog = false
        onExitConfirmed()
      },
      onDismiss = { showExitDialog = false }
    )
  }
}

internal data class MainNavCallbacks(
  val onTabSelected: (String) -> Unit,
  val onNavigateToAssistant: () -> Unit,
  val onNavigateToCategories: () -> Unit,
  val onNavigateToAccounts: () -> Unit,
  val onShowDebtors: () -> Unit,
  val onShowCreditors: () -> Unit,
  val onDebtsStateChange: (DebtsTabState) -> Unit,
)

/**
 * Owns the visited-tab back history and the overlay (category/account
 * management) flags above the screens. Slots make a test drive the back and
 * tab-switch wiring with lightweight stubs instead of real screens.
 */
@Composable
internal fun MainNavigationCoordinator(
  startTab: String,
  startDebtSection: DebtSection,
  onExitConfirmed: () -> Unit,
  onResetPersonSearch: () -> Unit = {},
  categoryContent: @Composable (onBack: () -> Unit) -> Unit = {},
  accountContent: @Composable (onBack: () -> Unit) -> Unit = {},
  mainContent:
    @Composable (currentTab: String, debtsState: DebtsTabState, callbacks: MainNavCallbacks) -> Unit =
    { _, _, _ -> },
) {
  // Tabs the user visited, newest last. System back walks this history
  // instead of closing the app; rememberSaveable keeps it across rotation.
  var tabHistory by rememberSaveable(startTab, stateSaver = TabHistorySaver) {
    mutableStateOf(listOf(startTab))
  }
  val currentTab = tabHistory.last()
  var debtsState by rememberSaveable(stateSaver = DebtsTabStateSaver) {
    mutableStateOf(DebtsTabState(section = startDebtSection, filter = LoanDirectionFilter.ALL))
  }
  var showCategoryManagement by rememberSaveable { mutableStateOf(false) }
  var showAccountManagement by rememberSaveable { mutableStateOf(false) }

  // Called before the screens below, so their own back handlers (sheets,
  // overlays, management screens) take precedence over tab history.
  TabBackHandler(
    tabHistoryProvider = { tabHistory },
    onHistoryChange = { tabHistory = it },
    onExitConfirmed = onExitConfirmed,
    onResetPersonSearch = onResetPersonSearch
  )

  when {
    showCategoryManagement -> {
      BackHandler { showCategoryManagement = false }
      categoryContent { showCategoryManagement = false }
    }

    showAccountManagement -> {
      BackHandler { showAccountManagement = false }
      accountContent { showAccountManagement = false }
    }

    else -> {
      val debtsNav =
        createDebtsNavActions(
          currentTabProvider = { currentTab },
          onCurrentTabChange = { tabHistory = tabHistory.pushTab(it) },
          onDebtsStateChange = { debtsState = it },
          onResetPersonSearch = onResetPersonSearch
        )
      mainContent(
        currentTab,
        debtsState,
        MainNavCallbacks(
          onTabSelected = debtsNav.onTabSelected,
          onNavigateToAssistant = { tabHistory = tabHistory.pushTab(TAB_ASSISTANT) },
          onNavigateToCategories = { showCategoryManagement = true },
          onNavigateToAccounts = { showAccountManagement = true },
          onShowDebtors = debtsNav.onShowDebtors,
          onShowCreditors = debtsNav.onShowCreditors,
          onDebtsStateChange = { debtsState = it }
        )
      )
    }
  }
}

/**
 * Moves [tab] to the top of the visited-tabs history. A tab appears at most
 * once, so back never cycles between two tabs the user bounced between.
 */
internal fun List<String>.pushTab(tab: String): List<String> =
  if (lastOrNull() == tab) this else filterNot { it == tab } + tab

/**
 * The history after one system back press, or `null` when back should ask
 * to leave the app: previous tab first, then [home], then exit.
 */
internal fun List<String>.popTab(home: String): List<String>? =
  when {
    size > 1 -> dropLast(1)
    lastOrNull() != home -> listOf(home)
    else -> null
  }

internal data class DebtsNavActions(
  val onTabSelected: (String) -> Unit,
  val onShowDebtors: () -> Unit,
  val onShowCreditors: () -> Unit,
)

internal fun createDebtsNavActions(
  currentTabProvider: () -> String,
  onCurrentTabChange: (String) -> Unit,
  onDebtsStateChange: (DebtsTabState) -> Unit,
  onResetPersonSearch: () -> Unit,
): DebtsNavActions =
  DebtsNavActions(
    onTabSelected = { newTab ->
      // Entering DEBTS from another tab starts a fresh search: the query is
      // Activity-scoped and would otherwise keep narrowing the persons list.
      if (newTab == TAB_DEBTS && currentTabProvider() != TAB_DEBTS) {
        onResetPersonSearch()
      }
      onCurrentTabChange(newTab)
    },
    onShowDebtors = {
      onResetPersonSearch()
      onDebtsStateChange(DebtsTabState(DebtSection.PERSONS, LoanDirectionFilter.DEBTOR))
      onCurrentTabChange(TAB_DEBTS)
    },
    onShowCreditors = {
      onResetPersonSearch()
      onDebtsStateChange(DebtsTabState(DebtSection.PERSONS, LoanDirectionFilter.CREDITOR))
      onCurrentTabChange(TAB_DEBTS)
    }
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreMenuSheet(
  show: Boolean,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
  onSelectAccounts: () -> Unit,
) {
  if (!show) return
  ModalBottomSheet(onDismissRequest = onDismiss) {
    ListItem(
      headlineContent = { Text(stringResource(R.string.nav_tab_analytics)) },
      leadingContent = { Icon(Icons.Filled.BarChart, contentDescription = null) },
      modifier = Modifier.clickable { onSelect(TAB_ANALYTICS) }
    )
    ListItem(
      headlineContent = { Text(stringResource(R.string.nav_tab_reports)) },
      leadingContent = { Icon(Icons.Filled.Analytics, contentDescription = null) },
      modifier = Modifier.clickable { onSelect(TAB_REPORTS) }
    )
    ListItem(
      headlineContent = { Text(stringResource(R.string.nav_tab_accounts)) },
      leadingContent = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
      modifier = Modifier.clickable { onSelectAccounts() }
    )
    ListItem(
      headlineContent = { Text(stringResource(R.string.nav_tab_settings)) },
      leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
      modifier = Modifier.clickable { onSelect(TAB_SETTINGS) }
    )
    Spacer(modifier = Modifier.navigationBarsPadding())
  }
}
