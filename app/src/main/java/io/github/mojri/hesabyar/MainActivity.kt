package io.github.mojri.hesabyar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mojri.hesabyar.auth.AuthManager
import io.github.mojri.hesabyar.auth.LockScreen
import io.github.mojri.hesabyar.reminder.ReminderScheduler
import io.github.mojri.hesabyar.ui.AccountViewModel
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.AnalyticsViewModel
import io.github.mojri.hesabyar.ui.BackupViewModel
import io.github.mojri.hesabyar.ui.BankLoanViewModel
import io.github.mojri.hesabyar.ui.CategoryViewModel
import io.github.mojri.hesabyar.ui.DashboardViewModel
import io.github.mojri.hesabyar.ui.ExportViewModel
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.PersonViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.TransactionViewModel
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.screens.AnalyticsScreen
import io.github.mojri.hesabyar.ui.screens.CategoryManagementScreen
import io.github.mojri.hesabyar.ui.screens.DashboardScreen
import io.github.mojri.hesabyar.ui.screens.DebtHubScreen
import io.github.mojri.hesabyar.ui.screens.DebtSection
import io.github.mojri.hesabyar.ui.screens.ReportsScreen
import io.github.mojri.hesabyar.ui.screens.SettingsScreen
import io.github.mojri.hesabyar.ui.screens.SmartAssistantScreen
import io.github.mojri.hesabyar.ui.screens.account.AccountManagementScreen
import io.github.mojri.hesabyar.ui.theme.HesabyarTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
  @Inject
  lateinit var authManager: AuthManager

  private val settingsViewModel: SettingsViewModel by viewModels()
  private val dashboardViewModel: DashboardViewModel by viewModels()
  private val transactionViewModel: TransactionViewModel by viewModels()
  private val installmentViewModel: InstallmentViewModel by viewModels()
  private val categoryViewModel: CategoryViewModel by viewModels()
  private val aiAssistantViewModel: AiAssistantViewModel by viewModels()
  private val backupViewModel: BackupViewModel by viewModels()
  private val exportViewModel: ExportViewModel by viewModels()
  private val analyticsViewModel: AnalyticsViewModel by viewModels()
  private val bankLoanViewModel: BankLoanViewModel by viewModels()
  private val personViewModel: PersonViewModel by viewModels()
  private val accountViewModel: AccountViewModel by viewModels()

  private val notificationPermissionLauncher =
    registerForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { isGranted ->
      if (isGranted) {
        ReminderScheduler.scheduleReminders(this)
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    requestNotificationPermission()
    ReminderScheduler.scheduleReminders(this)
    observeUiMessages()

    val (startTab, startDebtSection) = resolveInitialNavigation(intent?.getStringExtra(OPEN_TAB_EXTRA))

    setContent {
      HesabyarAppRoot(startTab = startTab, startDebtSection = startDebtSection)
    }
  }

  private fun observeUiMessages() {
    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        settingsViewModel.uiMessage.collectLatest { msg ->
          Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  @Composable
  private fun HesabyarAppRoot(
    startTab: String,
    startDebtSection: DebtSection,
  ) {
    val isDark by settingsViewModel.isDarkMode
    val isLocked by authManager.isLocked.collectAsState()

    HesabyarTheme(darkTheme = isDark) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (isLocked && authManager.shouldShowAuth(this@MainActivity)) {
          LockScreen(
            authManager = authManager,
            onUnlocked = {}
          )
        } else {
          MainNavigationCoordinator(
            startTab = startTab,
            startDebtSection = startDebtSection,
            onExitConfirmed = { finish() },
            onResetPersonSearch = { personViewModel.setSearchQuery("") },
            categoryContent = { onBack ->
              CategoryManagementScreen(
                categoryViewModel = categoryViewModel,
                onBack = onBack,
                modifier = Modifier.fillMaxSize()
              )
            },
            accountContent = { onBack ->
              AccountManagementScreen(
                accountViewModel = accountViewModel,
                onBack = onBack,
                modifier = Modifier.fillMaxSize()
              )
            },
            mainContent = { currentTab, debtsState, callbacks ->
              MainScreenScaffold(
                currentTab = currentTab,
                debtsState = debtsState,
                callbacks = callbacks
              )
            }
          )
        }
      }
    }
  }

  @Composable
  private fun MainScreenScaffold(
    currentTab: String,
    debtsState: DebtsTabState,
    callbacks: MainNavCallbacks,
  ) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val isCompact = LocalConfiguration.current.screenWidthDp < 600

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
        if (isCompact) {
          MainBottomNavigation(
            currentTab = currentTab,
            tabs = MAIN_TABS,
            onTabSelected = callbacks.onTabSelected,
            onMoreClick = { showMoreMenu = true }
          )
        }
      }
    ) { innerPadding ->
      val activeScreen = @Composable {
        CurrentTabScreen(
          currentTab = currentTab,
          debtsState = debtsState,
          callbacks = callbacks,
          modifier = Modifier
        )
      }
      if (isCompact) {
        CompactMainContent(innerPadding = innerPadding, content = activeScreen)
      } else {
        ExpandedMainContent(
          innerPadding = innerPadding,
          currentTab = currentTab,
          onTabSelected = callbacks.onTabSelected,
          onMoreClick = { showMoreMenu = true },
          content = activeScreen
        )
      }

      MoreMenuSheet(
        show = showMoreMenu,
        onDismiss = { showMoreMenu = false },
        onSelect = { tab ->
          showMoreMenu = false
          callbacks.onTabSelected(tab)
        },
        onSelectAccounts = {
          showMoreMenu = false
          callbacks.onNavigateToAccounts()
        }
      )
    }
  }

  @Composable
  private fun CurrentTabScreen(
    currentTab: String,
    debtsState: DebtsTabState,
    callbacks: MainNavCallbacks,
    modifier: Modifier
  ) {
    when (currentTab) {
      TAB_DASHBOARD -> {
        DashboardScreen(
          dashboardViewModel = dashboardViewModel,
          transactionViewModel = transactionViewModel,
          installmentViewModel = installmentViewModel,
          aiAssistantViewModel = aiAssistantViewModel,
          settingsViewModel = settingsViewModel,
          onNavigateToAssistant = callbacks.onNavigateToAssistant,
          onShowDebtors = callbacks.onShowDebtors,
          onShowCreditors = callbacks.onShowCreditors,
          modifier = modifier
        )
      }

      TAB_ASSISTANT -> {
        SmartAssistantScreen(
          aiAssistantViewModel = aiAssistantViewModel,
          categoryViewModel = categoryViewModel,
          dashboardViewModel = dashboardViewModel,
          settingsViewModel = settingsViewModel,
          modifier = modifier
        )
      }

      TAB_DEBTS -> {
        DebtsTabContent(debtsState, callbacks, modifier)
      }

      TAB_ANALYTICS -> {
        AnalyticsScreen(
          analyticsViewModel = analyticsViewModel,
          modifier = modifier
        )
      }

      TAB_REPORTS -> {
        ReportsScreen(
          dashboardViewModel = dashboardViewModel,
          transactionViewModel = transactionViewModel,
          aiAssistantViewModel = aiAssistantViewModel,
          modifier = modifier
        )
      }

      TAB_SETTINGS -> {
        SettingsScreen(
          aiAssistantViewModel = aiAssistantViewModel,
          backupViewModel = backupViewModel,
          exportViewModel = exportViewModel,
          settingsViewModel = settingsViewModel,
          onNavigateToCategories = callbacks.onNavigateToCategories,
          modifier = modifier
        )
      }
    }
  }

  @Composable
  private fun DebtsTabContent(
    debtsState: DebtsTabState,
    callbacks: MainNavCallbacks,
    modifier: Modifier
  ) {
    DebtHubScreen(
      initialSection = debtsState.section,
      initialPersonsDirectionFilter = debtsState.filter,
      installmentViewModel = installmentViewModel,
      bankLoanViewModel = bankLoanViewModel,
      personViewModel = personViewModel,
      settingsViewModel = settingsViewModel,
      onSectionChange = {
        callbacks.onDebtsStateChange(debtsState.copy(section = it))
      },
      onPersonsDirectionFilterChange = {
        callbacks.onDebtsStateChange(debtsState.copy(section = DebtSection.PERSONS, filter = it))
      },
      modifier = modifier
    )
  }

  override fun onUserInteraction() {
    super.onUserInteraction()
    authManager.onUserInteraction()
  }

  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
}

@Composable
private fun CompactMainContent(
  innerPadding: PaddingValues,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(innerPadding),
    contentAlignment = Alignment.TopCenter
  ) {
    content()
  }
}

@Composable
private fun ExpandedMainContent(
  innerPadding: PaddingValues,
  currentTab: String,
  onTabSelected: (String) -> Unit,
  onMoreClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  Row(modifier = Modifier.fillMaxSize()) {
    MainNavigationRail(
      currentTab = currentTab,
      tabs = MAIN_TABS,
      onTabSelected = onTabSelected,
      onMoreClick = onMoreClick
    )
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).weight(1f),
      contentAlignment = Alignment.TopCenter
    ) {
      content()
    }
  }
}

@Composable
private fun MainBottomNavigation(
  currentTab: String,
  tabs: List<NavigationTabItem>,
  onTabSelected: (String) -> Unit,
  onMoreClick: () -> Unit,
) {
  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = ElevationTokens.Level4
  ) {
    tabs.forEach { item ->
      val label = stringResource(item.labelRes)
      NavigationBarItem(
        selected = currentTab == item.id,
        onClick = { onTabSelected(item.id) },
        icon = { Icon(imageVector = item.icon, contentDescription = label) },
        label = {
          Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
          )
        },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
          )
      )
    }
    val moreLabel = stringResource(R.string.nav_tab_more)
    NavigationBarItem(
      selected = currentTab in MORE_MENU_TABS,
      onClick = onMoreClick,
      icon = { Icon(imageVector = Icons.Filled.MoreHoriz, contentDescription = moreLabel) },
      label = {
        Text(
          moreLabel,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      },
      colors =
        NavigationBarItemDefaults.colors(
          selectedIconColor = MaterialTheme.colorScheme.primary,
          selectedTextColor = MaterialTheme.colorScheme.primary,
          indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    )
  }
}

@Composable
private fun MainNavigationRail(
  currentTab: String,
  tabs: List<NavigationTabItem>,
  onTabSelected: (String) -> Unit,
  onMoreClick: () -> Unit,
) {
  NavigationRail(
    modifier = Modifier.fillMaxHeight(),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    tabs.forEach { item ->
      val label = stringResource(item.labelRes)
      NavigationRailItem(
        selected = currentTab == item.id,
        onClick = { onTabSelected(item.id) },
        icon = { Icon(imageVector = item.icon, contentDescription = label) },
        label = {
          Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
          )
        },
        colors =
          NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
          )
      )
    }
    val moreLabel = stringResource(R.string.nav_tab_more)
    NavigationRailItem(
      selected = currentTab in MORE_MENU_TABS,
      onClick = onMoreClick,
      icon = { Icon(imageVector = Icons.Filled.MoreHoriz, contentDescription = moreLabel) },
      label = {
        Text(
          moreLabel,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      },
      colors =
        NavigationRailItemDefaults.colors(
          selectedIconColor = MaterialTheme.colorScheme.primary,
          selectedTextColor = MaterialTheme.colorScheme.primary,
          indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    )
  }
}

private data class NavigationTabItem(
  val id: String,
  @StringRes val labelRes: Int,
  val icon: ImageVector,
)

internal const val TAB_DASHBOARD = "DASHBOARD"
internal const val TAB_ASSISTANT = "ASSISTANT"
internal const val TAB_DEBTS = "DEBTS"
internal const val TAB_ANALYTICS = "ANALYTICS"
internal const val TAB_REPORTS = "REPORTS"
internal const val TAB_SETTINGS = "SETTINGS"

internal const val OPEN_TAB_EXTRA = "OPEN_TAB"
internal const val DEEP_LINK_LOANS = "LOANS"
internal const val DEEP_LINK_PERSONS = "PERSONS"
internal const val DEEP_LINK_INSTALLMENTS = "INSTALLMENTS"
internal const val DEEP_LINK_BANK_LOANS = "BANK_LOANS"
internal const val DEEP_LINK_DEBTS = TAB_DEBTS

private val MORE_MENU_TABS = listOf(TAB_ANALYTICS, TAB_REPORTS, TAB_SETTINGS)
private val MAIN_TABS =
  listOf(
    NavigationTabItem(TAB_DASHBOARD, R.string.nav_tab_dashboard, Icons.Filled.AccountBalanceWallet),
    NavigationTabItem(TAB_ASSISTANT, R.string.nav_tab_assistant, Icons.Filled.AutoAwesome),
    NavigationTabItem(TAB_DEBTS, R.string.nav_tab_debts, Icons.Filled.AccountBalance)
  )
