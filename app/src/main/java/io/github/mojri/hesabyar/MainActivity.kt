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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.mojri.hesabyar.auth.AuthManager
import io.github.mojri.hesabyar.auth.LockScreen
import io.github.mojri.hesabyar.reminder.ReminderScheduler
import io.github.mojri.hesabyar.ui.*
import io.github.mojri.hesabyar.ui.screens.*
import io.github.mojri.hesabyar.ui.theme.HesabyarTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var authManager: AuthManager

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val transactionViewModel: TransactionViewModel by viewModels()
    private val loanViewModel: LoanViewModel by viewModels()
    private val installmentViewModel: InstallmentViewModel by viewModels()
    private val categoryViewModel: CategoryViewModel by viewModels()
    private val aiAssistantViewModel: AiAssistantViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()
    private val exportViewModel: ExportViewModel by viewModels()
    private val analyticsViewModel: AnalyticsViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
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

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.uiMessage.collectLatest { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        setContent {
            val isDark by settingsViewModel.isDarkMode
            val isLocked by authManager.isLocked.collectAsState()

            HesabyarTheme(darkTheme = isDark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (isLocked && authManager.shouldShowAuth(this@MainActivity)) {
                        LockScreen(
                            authManager = authManager,
                            onUnlocked = { }
                        )
                    } else {
                        var currentTab by remember { mutableStateOf("DASHBOARD") }
                        var showCategoryManagement by remember { mutableStateOf(false) }

                        if (showCategoryManagement) {
                            CategoryManagementScreen(
                                categoryViewModel = categoryViewModel,
                                onBack = { showCategoryManagement = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 8.dp
                                ) {
                                    val tabs = listOf(
                                        Triple("DASHBOARD", "داشبورد", Icons.Filled.AccountBalanceWallet),
                                        Triple("ASSISTANT", "دستیار هوشمند", Icons.Filled.AutoAwesome),
                                        Triple("LOANS", "قرض و وام", Icons.Filled.HistoryEdu),
                                        Triple("INSTALLMENTS", "اقساط", Icons.Filled.CreditCard),
                                        Triple("ANALYTICS", "تحلیل و آمار", Icons.Filled.BarChart),
                                        Triple("REPORTS", "گزارش‌ها", Icons.Filled.Analytics),
                                        Triple("SETTINGS", "تنظیمات", Icons.Filled.Settings)
                                    )

                                    tabs.forEach { (tabId, label, icon) ->
                                        NavigationBarItem(
                                            selected = currentTab == tabId,
                                            onClick = { currentTab = tabId },
                                            icon = { Icon(imageVector = icon, contentDescription = label) },
                                            label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            val modifier = Modifier.padding(innerPadding)
                            when (currentTab) {
                                "DASHBOARD" -> DashboardScreen(
                                    dashboardViewModel = dashboardViewModel,
                                    transactionViewModel = transactionViewModel,
                                    loanViewModel = loanViewModel,
                                    installmentViewModel = installmentViewModel,
                                    aiAssistantViewModel = aiAssistantViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onNavigateToAssistant = { currentTab = "ASSISTANT" },
                                    modifier = modifier
                                )
                                "ASSISTANT" -> SmartAssistantScreen(
                                    aiAssistantViewModel = aiAssistantViewModel,
                                    categoryViewModel = categoryViewModel,
                                    dashboardViewModel = dashboardViewModel,
                                    settingsViewModel = settingsViewModel,
                                    modifier = modifier
                                )
                                "LOANS" -> LoanManagementScreen(
                                    loanViewModel = loanViewModel,
                                    settingsViewModel = settingsViewModel,
                                    modifier = modifier
                                )
                                "INSTALLMENTS" -> InstallmentScreen(
                                    installmentViewModel = installmentViewModel,
                                    settingsViewModel = settingsViewModel,
                                    modifier = modifier
                                )
                                "ANALYTICS" -> AnalyticsScreen(
                                    analyticsViewModel = analyticsViewModel,
                                    modifier = modifier
                                )
                                "REPORTS" -> ReportsScreen(
                                    dashboardViewModel = dashboardViewModel,
                                    transactionViewModel = transactionViewModel,
                                    loanViewModel = loanViewModel,
                                    installmentViewModel = installmentViewModel,
                                    aiAssistantViewModel = aiAssistantViewModel,
                                    modifier = modifier
                                )
                                "SETTINGS" -> SettingsScreen(
                                    aiAssistantViewModel = aiAssistantViewModel,
                                    backupViewModel = backupViewModel,
                                    exportViewModel = exportViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onNavigateToCategories = { showCategoryManagement = true },
                                    modifier = modifier
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        authManager.onUserInteraction()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
