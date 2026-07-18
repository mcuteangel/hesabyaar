package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.AmountResolutionInput
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.DashboardViewModel
import io.github.mojri.hesabyar.ui.ForecastUIState
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.LoanViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.TransactionAmountResolver
import io.github.mojri.hesabyar.ui.TransactionViewModel
import io.github.mojri.hesabyar.ui.components.AmountQuickFillButtons
import io.github.mojri.hesabyar.ui.components.BalanceCard
import io.github.mojri.hesabyar.ui.components.EmptyState
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.SectionHeader
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.WindowSizeTokens
import kotlinx.coroutines.delay
import java.util.*

private const val DIALOG_EXIT_MS = 300L

@Composable
private fun entranceCard(content: @Composable () -> Unit) {
  AnimatedVisibility(
    visible = true,
    enter =
      fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
        slideInVertically(
          animationSpec = spring(stiffness = Spring.StiffnessMedium),
          initialOffsetY = { it / 12 }
        )
  ) {
    content()
  }
}

private val CATEGORY_ICONS_MAP =
  mapOf(
    "Restaurant" to Icons.Filled.Restaurant,
    "DirectionsCar" to Icons.Filled.DirectionsCar,
    "ShoppingBag" to Icons.Filled.ShoppingBag,
    "ReceiptLong" to Icons.Filled.ReceiptLong,
    "CreditCard" to Icons.Filled.CreditCard,
    "HistoryEdu" to Icons.Filled.HistoryEdu,
    "Paid" to Icons.Filled.Paid,
    "AttachMoney" to Icons.Filled.AttachMoney,
    "Home" to Icons.Filled.Home,
    "HealthAndSafety" to Icons.Filled.HealthAndSafety,
    "School" to Icons.Filled.School,
    "Flight" to Icons.Filled.Flight,
    "LocalCafe" to Icons.Filled.LocalCafe,
    "Pets" to Icons.Filled.Pets,
    "CardGiftcard" to Icons.Filled.CardGiftcard,
    "Work" to Icons.Filled.Work,
    "SportsEsports" to Icons.Filled.SportsEsports,
    "Checkroom" to Icons.Filled.Checkroom,
    "LocalGroceryStore" to Icons.Filled.LocalGroceryStore,
    "Savings" to Icons.Filled.Savings,
    "AccountBalance" to Icons.Filled.AccountBalance,
    "TrendingUp" to Icons.Filled.TrendingUp,
    "TrendingDown" to Icons.Filled.TrendingDown,
    "Build" to Icons.Filled.Build,
    "Phone" to Icons.Filled.Phone,
    "Wifi" to Icons.Filled.Wifi,
    "LocalHospital" to Icons.Filled.LocalHospital,
    "ChildCare" to Icons.Filled.ChildCare,
    "LocalDining" to Icons.Filled.LocalDining,
    "CleaningServices" to Icons.Filled.CleaningServices
  )

fun formatPersianDate(timestamp: Long): String {
  val jalali = JalaliCalendarHelper.gregorianToJalali(timestamp)
  val cal = Calendar.getInstance()
  cal.timeInMillis = timestamp
  val hour = cal.get(Calendar.HOUR_OF_DAY)
  val minute = cal.get(Calendar.MINUTE)
  return String.format("%s - %02d:%02d", jalali.toString(), hour, minute)
}

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

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier =
        Modifier
          .widthIn(max = WindowSizeTokens.ContentMaxWidth)
          .fillMaxWidth()
          .padding(horizontal = SpacingTokens.lg),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
      contentPadding = PaddingValues(top = SpacingTokens.sm, bottom = Dimens.BottomNavClearance)
    ) {
      item { entranceCard { DashboardHeader(settingsViewModel) } }

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

      item { entranceCard { IncomeExpenseCards(dashboardData) } }

      item { entranceCard { KpiCards(dashboardData) } }

      // Debtors and Creditors summary Row
      item { entranceCard { DebtorCreditorCards(dashboardData) } }

      item { entranceCard { SmartParsingBanner(onNavigateToAssistant) } }

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
      item { entranceCard { BankLoansSummaryCard(dashboardData) } }

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
    DeleteConfirmationDialog(
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

internal fun extractForecastPreview(forecast: String): String {
  val lines = forecast.lines()
  val contentLines =
    lines
      .filter { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#")
      }.map { line ->
        line
          .trim()
          .removePrefix("-")
          .removePrefix("*")
          .trim()
      }.filter { it.isNotEmpty() }

  if (contentLines.isEmpty()) return "گزارش آماده است"

  val preview =
    contentLines.take(3).joinToString(" | ") { line ->
      if (line.length > 60) line.substring(0, 60).substringBeforeLast(" ") + "..." else line
    }

  return if (preview.length > 150) {
    preview.substring(0, 150).substringBeforeLast(" ") + "..."
  } else {
    preview
  }
}

@Composable
fun InstallmentMiniItem(
  installment: Installment,
  onTogglePaid: () -> Unit
) {
  HesabyarCard(
    modifier = Modifier.fillMaxWidth(),
    cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier =
            Modifier
              .size(Dimens.AvatarSmall)
              .background(FinancialColors.WarningOrange.copy(alpha = 0.15f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = FinancialColors.WarningOrange,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(modifier = Modifier.width(SpacingTokens.md))
        Column {
          Text(
            text = installment.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "سررسید: ${formatPersianDate(
              installment.dueDate
            )} | ${CurrencyFormatter.format(installment.amount)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Button(
        onClick = onTogglePaid,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          ),
        shape = ShapeTokens.Full,
        contentPadding = PaddingValues(horizontal = SpacingTokens.md, vertical = SpacingTokens.xxs)
      ) {
        Text("پرداخت", style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

@Composable
fun TransactionMiniItem(
  transaction: Transaction,
  categories: List<Category> = emptyList(),
  onClick: () -> Unit = {},
  onDelete: () -> Unit = {}
) {
  val isIncome = transaction.type == TransactionType.INCOME
  val category = categories.find { it.id == transaction.categoryId }
  val categoryColor =
    category?.let { Color(it.color) } ?: if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
  val icon = CATEGORY_ICONS_MAP[category?.icon] ?: Icons.Filled.Paid

  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    shape = ShapeTokens.Medium,
    cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    contentPadding = PaddingValues(SpacingTokens.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier =
            Modifier
              .size(Dimens.AvatarSmall)
              .background(
                categoryColor.copy(alpha = 0.15f),
                CircleShape
              ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = categoryColor,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(modifier = Modifier.width(SpacingTokens.md))
        Column {
          Text(
            text = transaction.description,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "${formatPersianDate(transaction.date)} | ${category?.name ?: "سایر"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = (if (isIncome) "+" else "-") + CurrencyFormatter.format(transaction.amount),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "حذف تراکنش",
            tint = FinancialColors.ExpenseRed.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun ForecastDetailDialog(
  forecastState: ForecastUIState,
  onDismiss: () -> Unit,
  onRefresh: () -> Unit
) {
  var visible by remember { mutableStateOf(true) }
  Dialog(
    onDismissRequest = { visible = false },
    properties =
      androidx.compose.ui.window
        .DialogProperties(usePlatformDefaultWidth = false)
  ) {
    AnimatedVisibility(
      visible = visible,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut()
    ) {
      Surface(
        modifier =
          Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f),
        shape = ShapeTokens.XLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = ElevationTokens.Level3
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(SpacingTokens.xl)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
            ) {
              Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Text(
                text = "پیش‌بینی وضعیت بودجه ماه آینده",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
            IconButton(onClick = { visible = false }) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "بستن"
              )
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = SpacingTokens.sm))

          when (val state = forecastState) {
            is ForecastUIState.Loading -> {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.IconLarge),
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = "در حال تحلیل و پیش‌بینی وضعیت بودجه...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
            is ForecastUIState.Error -> {
              Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "⚠️ خطا در دریافت پیش‌بینی",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(SpacingTokens.sm))
                Text(
                  text = state.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(SpacingTokens.lg))
                Button(onClick = onRefresh) {
                  Text("تلاش مجدد")
                }
              }
            }
            is ForecastUIState.Success -> {
              Column(modifier = Modifier.fillMaxSize()) {
                Column(
                  modifier =
                    Modifier
                      .weight(1f)
                      .verticalScroll(rememberScrollState())
                ) {
                  MarkdownText(text = state.forecast)
                }
                Spacer(modifier = Modifier.height(SpacingTokens.md))
                Button(
                  onClick = onRefresh,
                  modifier = Modifier.fillMaxWidth(),
                  shape = ShapeTokens.Medium
                ) {
                  Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.IconSmall)
                  )
                  Spacer(modifier = Modifier.width(SpacingTokens.sm))
                  Text("بروزرسانی پیش‌بینی", fontWeight = FontWeight.Bold)
                }
              }
            }
            is ForecastUIState.Idle -> {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
              ) {
                HesabyarButton(onClick = onRefresh, text = "دریافت پیش‌بینی")
              }
            }
          }
        }
      }
    }
  }
  LaunchedEffect(visible) {
    if (!visible) {
      delay(DIALOG_EXIT_MS)
      onDismiss()
    }
  }
}

@Composable
fun TransactionDetailDialog(
  transaction: Transaction,
  categories: List<Category>,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit
) {
  val isIncome = transaction.type == TransactionType.INCOME
  val category = categories.find { it.id == transaction.categoryId }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "جزئیات تراکنش",
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Right
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "نوع:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = if (isIncome) "درآمد" else "هزینه",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "مبلغ:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = CurrencyFormatter.format(transaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "دسته‌بندی:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = category?.name ?: "سایر",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "تاریخ:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = formatPersianDate(transaction.date),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "توضیحات:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = transaction.description,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }
    },
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        Button(
          onClick = onEdit,
          modifier = Modifier.weight(1f),
          shape = ShapeTokens.Small
        ) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSmall)
          )
          Spacer(modifier = Modifier.width(SpacingTokens.xs))
          Text("ویرایش")
        }
        Button(
          onClick = onDelete,
          modifier = Modifier.weight(1f),
          shape = ShapeTokens.Small,
          colors = ButtonDefaults.buttonColors(containerColor = FinancialColors.ExpenseRed)
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSmall)
          )
          Spacer(modifier = Modifier.width(SpacingTokens.xs))
          Text("حذف")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("بستن")
      }
    }
  )
}

@Composable
fun DeleteConfirmationDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("حذف تراکنش") },
    text = { Text("آیا از حذف این تراکنش اطمینان دارید؟") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("حذف", color = FinancialColors.ExpenseRed)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("لغو")
      }
    }
  )
}

@Composable
fun ManualTransactionDialog(
  transactionViewModel: TransactionViewModel,
  loanViewModel: LoanViewModel,
  installmentViewModel: InstallmentViewModel,
  categories: List<Category>,
  transactionToEdit: Transaction? = null,
  onDismiss: () -> Unit
) {
  var visible by remember { mutableStateOf(true) }
  val context = LocalContext.current
  val isEditMode = transactionToEdit != null
  var selectedType by remember { mutableStateOf(transactionToEdit?.type?.name ?: TransactionType.EXPENSE.name) }
  val originalAmountRial by remember { mutableStateOf(transactionToEdit?.amount ?: 0L) }
  var amountValue by remember {
    mutableStateOf(
      TextFieldValue(
        if (isEditMode) {
          CurrencyFormatter
            .fromRial(
              transactionToEdit.amount
            ).toString()
        } else {
          ""
        }
      )
    )
  }
  var amountModified by remember { mutableStateOf(false) }
  var descriptionText by remember { mutableStateOf(transactionToEdit?.description.orEmpty()) }
  var selectedCategoryId by remember { mutableStateOf(transactionToEdit?.categoryId ?: 0L) }
  var personNameText by remember { mutableStateOf(transactionToEdit?.personName ?: "") }
  var titleText by remember { mutableStateOf(transactionToEdit?.description ?: "") }
  var daysFromNowText by remember { mutableStateOf("30") }
  var customDate by remember { mutableStateOf(transactionToEdit?.date ?: System.currentTimeMillis()) }

  val filteredCategories =
    categories.filter { cat ->
      when (selectedType) {
        TransactionType.INCOME.name -> cat.type == CategoryType.INCOME || cat.type == CategoryType.BOTH
        TransactionType.EXPENSE.name -> cat.type == CategoryType.EXPENSE || cat.type == CategoryType.BOTH
        else -> cat.key == "Loans" || cat.key == "Installments" || cat.key == "Other"
      }
    }

  val typeColor =
    when (selectedType) {
      "INCOME", "LOAN_DEBTOR" -> FinancialColors.IncomeGreen
      "EXPENSE", "LOAN_CREDITOR" -> FinancialColors.ExpenseRed
      else -> FinancialColors.WarningOrange
    }

  Dialog(
    onDismissRequest = { visible = false },
    properties =
      androidx.compose.ui.window
        .DialogProperties(usePlatformDefaultWidth = false)
  ) {
    AnimatedVisibility(
      visible = visible,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut()
    ) {
      Surface(
        modifier =
          Modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight()
            .padding(vertical = SpacingTokens.xl),
        shape = ShapeTokens.XLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = ElevationTokens.Level3
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(SpacingTokens.xl),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
        ) {
          // Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (isEditMode) "ویرایش تراکنش" else "ثبت دستی تراکنش جدید",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            IconButton(
              onClick = { visible = false },
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "بستن",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

          // Scrollable content
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
          ) {
            // Type selector
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
              Text(
                text = "نوع تراکنش / تعهد مالی:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
              ) {
                val types =
                  listOf(
                    Pair("EXPENSE", "هزینه"),
                    Pair("INCOME", "درآمد"),
                    Pair("LOAN_DEBTOR", "طلب (قرض دادم)"),
                    Pair("LOAN_CREDITOR", "بدهی (قرض گرفتم)"),
                    Pair("INSTALLMENT", "قسط")
                  )
                types.forEach { (typeKey, typeLabel) ->
                  val isSelected = selectedType == typeKey
                  val chipColor =
                    when (typeKey) {
                      "INCOME", "LOAN_DEBTOR" -> FinancialColors.IncomeGreen
                      "EXPENSE", "LOAN_CREDITOR" -> FinancialColors.ExpenseRed
                      else -> FinancialColors.WarningOrange
                    }
                  FilterChip(
                    selected = isSelected,
                    onClick = {
                      selectedType = typeKey
                      selectedCategoryId =
                        when (typeKey) {
                          "INCOME" -> categories.find { it.key == "Income" }?.id ?: 1L
                          "LOAN_DEBTOR", "LOAN_CREDITOR" ->
                            categories.find { it.key == "Loans" }?.id
                              ?: 1L
                          "INSTALLMENT" -> categories.find { it.key == "Installments" }?.id ?: 1L
                          else -> selectedCategoryId
                        }
                    },
                    label = {
                      Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                      )
                    },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                      )
                  )
                }
              }
            }

            // Amount input
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
              Text(
                text = "مبلغ (${CurrencyFormatter.unitLabel}):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              OutlinedTextField(
                value = amountValue,
                onValueChange = {
                  if (isEditMode && it.text != amountValue.text) {
                    amountModified = true
                  }
                  amountValue = it
                },
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .testTag("manual_amount_input"),
                shape = ShapeTokens.Medium,
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Paid,
                    contentDescription = null,
                    tint = typeColor
                  )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors =
                  OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = typeColor,
                    focusedLabelColor = typeColor
                  )
              )
              AmountQuickFillButtons(
                amountValue = amountValue,
                onValueChanged = {
                  amountValue = it
                  if (isEditMode) {
                    amountModified = true
                  }
                }
              )
              val amtDisplay = amountValue.text.toLongOrNull() ?: 0L
              if (amtDisplay > 0L) {
                val amtRial = CurrencyFormatter.toRial(amtDisplay)
                Text(
                  text = "معادل: ${CurrencyFormatter.format(amtRial)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = typeColor,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = SpacingTokens.xs)
                )
              }
            }

            // Category Selector
            if (selectedType == "EXPENSE" || selectedType == "INCOME") {
              Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
                Text(
                  text = "دسته‌بندی مربوطه:",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
                ) {
                  filteredCategories.forEach { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    FilterChip(
                      selected = isSelected,
                      onClick = { selectedCategoryId = cat.id },
                      label = {
                        Text(
                          text = cat.name,
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.Medium
                        )
                      },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = MaterialTheme.colorScheme.primary,
                          selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                  }
                }
              }
            }

            // Conditional Person Name for loans
            if (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") {
              Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
                Text(
                  text = "طرف حساب (شخص مربوطه):",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                  value = personNameText,
                  onValueChange = { personNameText = it },
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .testTag("manual_person_input"),
                  shape = ShapeTokens.Medium,
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Filled.Person,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  },
                  placeholder = { Text("مثلا: علی محمودی", style = MaterialTheme.typography.bodyMedium) },
                  singleLine = true
                )
              }
            }

            // Conditional Installment fields
            if (selectedType == "INSTALLMENT") {
              Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)) {
                Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
                  Text(
                    text = "عنوان قسط:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .testTag("manual_title_input"),
                    shape = ShapeTokens.Medium,
                    placeholder = {
                      Text(
                        "مثلا: قسط بانک مسکن",
                        style = MaterialTheme.typography.bodyMedium
                      )
                    },
                    singleLine = true
                  )
                }
                Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
                  Text(
                    text = "فاصله تا موعد پرداخت (روز):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  OutlinedTextField(
                    value = daysFromNowText,
                    onValueChange = { daysFromNowText = it },
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .testTag("manual_days_input"),
                    shape = ShapeTokens.Medium,
                    placeholder = { Text("مثلا: ۳۰", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                  )
                }
              }
            }

            // Shamsi Date & Time Picker
            JalaliDateTimePicker(
              initialTimestamp = customDate,
              onTimestampChanged = { customDate = it }
            )

            // Description text field
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
              Text(
                text = "شرح یا توضیح تراکنش:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .testTag("manual_description_input"),
                shape = ShapeTokens.Medium,
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                },
                singleLine = true
              )
            }
          }

          Spacer(modifier = Modifier.height(SpacingTokens.sm))

          // Actions block
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
          ) {
            OutlinedButton(
              onClick = { visible = false },
              modifier = Modifier.weight(1f),
              shape = ShapeTokens.Medium
            ) {
              Text("انصراف")
            }

            Button(
              onClick = {
                val finalAmountDisplay = amountValue.text.toLongOrNull() ?: 0L
                if (finalAmountDisplay <= 0L) {
                  android.widget.Toast
                    .makeText(
                      context,
                      "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید",
                      android.widget.Toast.LENGTH_SHORT
                    ).show()
                  return@Button
                }
                val resolutionResult =
                  TransactionAmountResolver.resolveAmount(
                    AmountResolutionInput(
                      displayedAmount = finalAmountDisplay,
                      isEditMode = isEditMode,
                      originalRialAmount = originalAmountRial,
                      userModifiedAmount = amountModified
                    )
                  )
                val finalAmountRial = resolutionResult.rialAmount

                if ((selectedType == "INCOME" || selectedType == "EXPENSE") && selectedCategoryId == 0L) {
                  android.widget.Toast
                    .makeText(
                      context,
                      "لطفا دسته‌بندی را انتخاب کنید",
                      android.widget.Toast.LENGTH_SHORT
                    ).show()
                  return@Button
                }

                when (selectedType) {
                  "INCOME", "EXPENSE" -> {
                    val selectedCategoryName =
                      categories.find { it.id == selectedCategoryId }?.name ?: "سایر"
                    val desc = descriptionText.trim().ifEmpty { selectedCategoryName }
                    if (isEditMode) {
                      val updatedTransaction =
                        transactionToEdit.copy(
                          type = TransactionType.valueOf(selectedType),
                          categoryId = selectedCategoryId,
                          amount = finalAmountRial,
                          description = desc,
                          date = customDate
                        )
                      transactionViewModel.updateTransaction(updatedTransaction)
                    } else {
                      transactionViewModel.addTransaction(
                        type = TransactionType.valueOf(selectedType),
                        categoryId = selectedCategoryId,
                        amount = finalAmountRial,
                        description = desc,
                        customDate = customDate
                      )
                    }
                  }
                  "LOAN_DEBTOR", "LOAN_CREDITOR" -> {
                    val person = personNameText.trim()
                    if (person.isEmpty()) {
                      android.widget.Toast
                        .makeText(
                          context,
                          "لطفا نام شخص مربوطه را وارد کنید",
                          android.widget.Toast.LENGTH_SHORT
                        ).show()
                      return@Button
                    }
                    val desc =
                      descriptionText.trim().ifEmpty {
                        if (selectedType ==
                          "LOAN_DEBTOR"
                        ) {
                          "قرض دادن به $person"
                        } else {
                          "قرض گرفتن از $person"
                        }
                      }
                    loanViewModel.addLoan(
                      personName = person,
                      type = if (selectedType == "LOAN_DEBTOR") LoanType.DEBTOR else LoanType.CREDITOR,
                      amount = finalAmountRial,
                      description = desc,
                      customDate = customDate
                    )
                  }
                  "INSTALLMENT" -> {
                    val title = titleText.trim()
                    if (title.isEmpty()) {
                      android.widget.Toast
                        .makeText(
                          context,
                          "لطفا عنوان قسط را وارد کنید",
                          android.widget.Toast.LENGTH_SHORT
                        ).show()
                      return@Button
                    }
                    val desc = descriptionText.trim()
                    installmentViewModel.addInstallment(
                      title = title,
                      amount = finalAmountRial,
                      dueDate = customDate,
                      reminderEnabled = true,
                      notes = desc
                    )
                  }
                }
                visible = false
              },
              modifier = Modifier.weight(1f),
              shape = ShapeTokens.Medium,
              colors = ButtonDefaults.buttonColors(containerColor = typeColor)
            ) {
              Text(
                if (isEditMode) "ذخیره تغییرات" else "ثبت تراکنش",
                color = MaterialTheme.colorScheme.onPrimary
              )
            }
          }
        }
      }
    }
  }
  LaunchedEffect(visible) {
    if (!visible) {
      delay(DIALOG_EXIT_MS)
      onDismiss()
    }
  }
}

@Composable
fun JalaliDateTimePicker(
  initialTimestamp: Long,
  onTimestampChanged: (Long) -> Unit
) {
  var showJalaliDatePicker by remember { mutableStateOf(false) }
  var showCustomTimePicker by remember { mutableStateOf(false) }

  val calendar =
    remember(initialTimestamp) {
      Calendar.getInstance().apply { timeInMillis = initialTimestamp }
    }

  val jalaliDate =
    remember(initialTimestamp) {
      JalaliCalendarHelper.gregorianToJalali(initialTimestamp)
    }
  val hour = calendar.get(Calendar.HOUR_OF_DAY)
  val minute = calendar.get(Calendar.MINUTE)

  if (showJalaliDatePicker) {
    JalaliDatePickerDialog(
      initialTimestamp = initialTimestamp,
      onDismissRequest = { showJalaliDatePicker = false },
      onDateSelected = onTimestampChanged
    )
  }

  if (showCustomTimePicker) {
    CustomTimePickerDialog(
      initialHour = hour,
      initialMinute = minute,
      onDismissRequest = { showCustomTimePicker = false },
      onTimeSelected = { selectedHour, selectedMinute ->
        val newCal =
          Calendar.getInstance().apply {
            timeInMillis = initialTimestamp
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
          }
        onTimestampChanged(newCal.timeInMillis)
      }
    )
  }

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(ShapeTokens.Large)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        .padding(SpacingTokens.md),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    Text(
      text = "📅 تنظیم تاریخ و ساعت (شمسی):",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      // Date picker button
      OutlinedButton(
        onClick = { showJalaliDatePicker = true },
        modifier =
          Modifier
            .weight(1.3f)
            .height(Dimens.ButtonHeight),
        shape = ShapeTokens.Medium,
        contentPadding = PaddingValues(horizontal = SpacingTokens.sm)
      ) {
        Icon(
          imageVector = Icons.Default.DateRange,
          contentDescription = null,
          modifier = Modifier.size(Dimens.IconSmall),
          tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(SpacingTokens.sm))
        Text(
          text = jalaliDate.toString(),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold
        )
      }

      // Time picker button
      OutlinedButton(
        onClick = { showCustomTimePicker = true },
        modifier =
          Modifier
            .weight(1f)
            .height(Dimens.ButtonHeight),
        shape = ShapeTokens.Medium,
        contentPadding = PaddingValues(horizontal = SpacingTokens.sm)
      ) {
        Icon(
          imageVector = Icons.Default.AccessTime,
          contentDescription = null,
          modifier = Modifier.size(Dimens.IconSmall),
          tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(SpacingTokens.sm))
        Text(
          text = String.format("%02d:%02d", hour, minute),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
