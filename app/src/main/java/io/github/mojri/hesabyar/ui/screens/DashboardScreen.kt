package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.DashboardViewModel
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.LoanViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.TransactionViewModel
import io.github.mojri.hesabyar.ui.components.BalanceCard
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.EmptyState
import io.github.mojri.hesabyar.ui.components.SectionHeader
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.dashboard.components.*
import io.github.mojri.hesabyar.ui.screens.dashboard.dialogs.*

@Composable
fun DashboardScreen(
  dashboardViewModel: DashboardViewModel,
  transactionViewModel: TransactionViewModel,
  loanViewModel: LoanViewModel,
  installmentViewModel: InstallmentViewModel,
  aiAssistantViewModel: AiAssistantViewModel,
  settingsViewModel: SettingsViewModel,
  onNavigateToAssistant: () -> Unit,
  modifier: Modifier = Modifier
) {
  val dashboardData by dashboardViewModel.dashboardState.collectAsState()
  val transactions by dashboardViewModel.transactions.collectAsState()
  val loans by dashboardViewModel.loans.collectAsState()
  val installments by dashboardViewModel.installments.collectAsState()
  val categories by dashboardViewModel.categories.collectAsState()
  val bankLoans by dashboardViewModel.bankLoans.collectAsState()
  val forecastState by aiAssistantViewModel.forecastState.collectAsState()
  val lastForecastFetchTime by aiAssistantViewModel.lastForecastFetchTime.collectAsState()

  var showManualAddDialog by remember { mutableStateOf(false) }
  var showFullForecast by remember { mutableStateOf(false) }
  var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
  var deletingTransaction by remember { mutableStateOf<Transaction?>(null) }
  var showDetailTransaction by remember { mutableStateOf<Transaction?>(null) }

  LaunchedEffect(transactions, loans, installments, categories, bankLoans) {
    aiAssistantViewModel.onFinancialDataChanged(transactions, loans, installments, categories, bankLoans)
  }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(horizontal = SpacingTokens.lg),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
      contentPadding = PaddingValues(top = SpacingTokens.sm, bottom = 80.dp)
    ) {
      item { DashboardHeader(settingsViewModel) }

      // Wallet Balance Card
      item {
        entranceCard {
          BalanceCard(
            balance = dashboardData.currentBalance,
            income = dashboardData.monthlyIncome,
            expense = dashboardData.monthlyExpenses,
            modifier = Modifier.testTag("balance_card")
          )
        }
      }

      item {
        entranceCard {
          SmartForecastCard(
            forecastState = forecastState,
            lastForecastFetchTime = lastForecastFetchTime,
            aiAssistantViewModel = aiAssistantViewModel,
            onShowForecast = { showFullForecast = true }
          )
        }
      }

      item {
        entranceCard {
          IncomeExpenseCards(dashboardData)
        }
      }

      item {
        entranceCard {
          KpiCards(dashboardData)
        }
      }

      // Debtors and Creditors summary Row
      item {
        entranceCard {
          DebtorCreditorCards(dashboardData)
        }
      }

      item {
        entranceCard {
          SmartParsingBanner(onNavigateToAssistant)
        }
      }

      // Upcoming Installments Header
      item {
        SectionHeader(
          title = "اقساط پیش‌رو",
          modifier = Modifier.padding(top = SpacingTokens.sm),
          action = {
            if (dashboardData.upcomingInstallments.isNotEmpty()) {
              Text(
                text = "باقی مانده: ${dashboardData.upcomingInstallments.size} مورد",
                style = MaterialTheme.typography.bodySmall,
                color = FinancialColors.WarningOrange
              )
            }
          }
        )
      }

      // List of Upcoming Installments
      if (dashboardData.upcomingInstallments.isEmpty()) {
        item {
          EmptyState(
            icon = Icons.Filled.DateRange,
            title = "هیچ قسط پرداخت‌نشده پیش‌رویی ثبت نشده است."
          )
        }
      } else {
        items(dashboardData.upcomingInstallments.take(3)) { installment ->
          InstallmentMiniItem(
            installment = installment,
            onTogglePaid = { installmentViewModel.toggleInstallmentPaid(installment) }
          )
        }
      }

      // Bank Loans Summary
      item { BankLoansSummaryCard(dashboardData) }

      // Recent Activity Banner
      item {
        SectionHeader(
          title = "آخرین فعالیت‌ها",
          modifier = Modifier.padding(top = SpacingTokens.sm)
        )
      }

      if (transactions.isEmpty()) {
        item {
          EmptyState(
            icon = Icons.Filled.ReceiptLong,
            title = "هنوز هیچ تراکنشی ثبت نشده است."
          )
        }
      } else {
        items(transactions.take(5)) { transaction ->
          TransactionMiniItem(
            transaction = transaction,
            categories = categories,
            onClick = { showDetailTransaction = transaction },
            onDelete = { deletingTransaction = transaction }
          )
        }
      }
    }

    FloatingActionButton(
      onClick = { showManualAddDialog = true },
      modifier =
        Modifier
          .align(Alignment.BottomEnd)
          .padding(SpacingTokens.lg)
          .testTag("add_transaction_fab"),
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = "ثبت تراکنش دستی"
      )
    }
  }

  if (showManualAddDialog) {
    ManualTransactionDialog(
      transactionViewModel = transactionViewModel,
      loanViewModel = loanViewModel,
      installmentViewModel = installmentViewModel,
      categories = categories,
      onDismiss = { showManualAddDialog = false }
    )
  }

  if (editingTransaction != null) {
    ManualTransactionDialog(
      transactionViewModel = transactionViewModel,
      loanViewModel = loanViewModel,
      installmentViewModel = installmentViewModel,
      categories = categories,
      transactionToEdit = editingTransaction,
      onDismiss = { editingTransaction = null }
    )
  }

  if (deletingTransaction != null) {
    ConfirmDialog(
      title = "حذف تراکنش",
      message = "آیا از حذف این تراکنش اطمینان دارید؟",
      confirmText = "حذف",
      onConfirm = {
        transactionViewModel.deleteTransaction(deletingTransaction!!)
        deletingTransaction = null
      },
      onDismiss = { deletingTransaction = null }
    )
  }

  if (showDetailTransaction != null) {
    TransactionDetailDialog(
      transaction = showDetailTransaction!!,
      categories = categories,
      onEdit = {
        editingTransaction = showDetailTransaction
        showDetailTransaction = null
      },
      onDelete = {
        deletingTransaction = showDetailTransaction
        showDetailTransaction = null
      },
      onDismiss = { showDetailTransaction = null }
    )
  }

  if (showFullForecast) {
    ForecastDetailDialog(
      forecastState = forecastState,
      onDismiss = { showFullForecast = false },
      onRefresh = {
        aiAssistantViewModel.fetchBudgetForecast(
          dashboardViewModel.transactions.value,
          dashboardViewModel.loans.value,
          dashboardViewModel.installments.value,
          dashboardViewModel.categories.value,
          aiAssistantViewModel.isOnlineMode.value,
          bankLoans = dashboardViewModel.bankLoans.value,
          forceRefresh = true
        )
      }
    )
  }
}
