package io.github.mojri.hesabyar

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mojri.hesabyar.ui.*
import io.github.mojri.hesabyar.ui.screens.*
import io.github.mojri.hesabyar.ui.theme.HesabyarTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: HesabyarViewModel by viewModels()
    private val financeViewModel: FinanceViewModel by viewModels()
    private val aiConfigViewModel: AiConfigViewModel by viewModels()
    private val aiServiceViewModel: AiServiceViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiMessage.collectLatest { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        setContent {
            val isDark by viewModel.isDarkMode
            HesabyarTheme(darkTheme = isDark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var currentTab by remember { mutableStateOf("DASHBOARD") }

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
                                financeViewModel = financeViewModel,
                                aiServiceViewModel = aiServiceViewModel,
                                aiConfigViewModel = aiConfigViewModel,
                                viewModel = viewModel,
                                onNavigateToAssistant = { currentTab = "ASSISTANT" },
                                modifier = modifier
                            )
                            "ASSISTANT" -> SmartAssistantScreen(
                                financeViewModel = financeViewModel,
                                aiServiceViewModel = aiServiceViewModel,
                                aiConfigViewModel = aiConfigViewModel,
                                viewModel = viewModel,
                                modifier = modifier
                            )
                            "LOANS" -> LoanManagementScreen(
                                financeViewModel = financeViewModel,
                                viewModel = viewModel,
                                modifier = modifier
                            )
                            "INSTALLMENTS" -> InstallmentScreen(
                                financeViewModel = financeViewModel,
                                viewModel = viewModel,
                                modifier = modifier
                            )
                            "REPORTS" -> ReportsScreen(
                                financeViewModel = financeViewModel,
                                aiServiceViewModel = aiServiceViewModel,
                                aiConfigViewModel = aiConfigViewModel,
                                modifier = modifier
                            )
                            "SETTINGS" -> SettingsScreen(
                                aiConfigViewModel = aiConfigViewModel,
                                aiServiceViewModel = aiServiceViewModel,
                                backupViewModel = backupViewModel,
                                viewModel = viewModel,
                                modifier = modifier
                            )
                        }
                    }
                }
            }
        }
    }
}
