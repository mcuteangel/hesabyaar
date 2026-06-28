package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import io.github.mojri.hesabyar.ui.ForecastUIState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.DashboardViewModel
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.LoanViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.TransactionViewModel
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.components.AmountQuickFillButtons
import io.github.mojri.hesabyar.ui.theme.ExpenseRed
import io.github.mojri.hesabyar.ui.theme.IncomeGreen
import io.github.mojri.hesabyar.ui.theme.WarningOrange
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

private val CATEGORY_ICONS_MAP = mapOf(
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

fun formatToman(value: Long): String {
    val tomanValue = value / 1000
    val formatter = DecimalFormat("#,###")
    return "${formatter.format(tomanValue)} تومان"
}

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
    val forecastState by aiAssistantViewModel.forecastState.collectAsState()
    val lastForecastFetchTime by aiAssistantViewModel.lastForecastFetchTime.collectAsState()
    var showManualAddDialog by remember { mutableStateOf(false) }
    var showFullForecast by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var deletingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showDetailTransaction by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(transactions, loans, installments, categories) {
        aiAssistantViewModel.onFinancialDataChanged(transactions, loans, installments, categories)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
        // Welcome and Custom Header with Logo from Design Spec
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "حسابیار هوشمند",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "دستیار مالی هوشمند شما",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                        )
                    }
                }

                IconButton(
                    onClick = { settingsViewModel.toggleDarkMode() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = if (settingsViewModel.isDarkMode.value) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "تغییر تم",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Wallet Balance Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("balance_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                                )
                            )
                        )
                        .fillMaxWidth()
                ) {
                    // Modern decorative abstract bubbles for premium "atmospheric" design
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-30).dp, y = (-30).dp)
                            .size(140.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (15).dp, y = (15).dp)
                            .size(90.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    )

                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "موجودی کل حساب‌ها",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "دستیار مالی هوشمند شما",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Text(
                            text = formatToman(dashboardData.currentBalance),
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Smart Forecast Alert Card (Compact)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_forecast_alert_card")
                    .clickable {
                        showFullForecast = true
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "پیش‌بینی بودجه ماه آینده",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        when (val state = forecastState) {
                            is ForecastUIState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "در حال تحلیل...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            is ForecastUIState.Success -> {
                                val preview = extractForecastPreview(state.forecast)
                                Column {
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "آخرین به‌روزرسانی: ${aiAssistantViewModel.formatLastFetchTime(lastForecastFetchTime)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            is ForecastUIState.Error -> {
                                Text(
                                    text = "خطا - برای تلاش مجدد کلیک کنید",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1
                                )
                            }
                            is ForecastUIState.Idle -> {
                                Text(
                                    text = "برای دریافت پیش‌بینی کلیک کنید",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = { showFullForecast = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مشاهده گزارش کامل", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Income vs Expense row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Monthly Income
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(IncomeGreen.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = IncomeGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "درآمد ۳۰ روزه",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = formatToman(dashboardData.monthlyIncome),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Monthly Expenses
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(ExpenseRed.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = ExpenseRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مخارج ۳۰ روزه",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = formatToman(dashboardData.monthlyExpenses),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // KPI Row: Savings Rate & Debt-to-Income
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isDarkTheme = settingsViewModel.isDarkMode.value
                val kpiBg = if (isDarkTheme) Color(0xFF1E2A3A) else Color(0xFFE8F0FE)

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = kpiBg)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Savings,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "نرخ پس‌انداز",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        val savingsPct = (dashboardData.savingsRate * 100).toInt()
                        Text(
                            text = "$savingsPct%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                savingsPct >= 20 -> Color(0xFF4CAF50)
                                savingsPct >= 0 -> Color(0xFFFF9800)
                                else -> ExpenseRed
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = kpiBg)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF2196F3).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBalance,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "نسبت بدهی",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        val debtPct = (dashboardData.debtToIncomeRatio * 100).toInt()
                        Text(
                            text = "$debtPct%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                debtPct > 40 -> ExpenseRed
                                debtPct > 20 -> Color(0xFFFF9800)
                                else -> Color(0xFF2196F3)
                            }
                        )
                    }
                }
            }
        }

        // Debtors and Creditors summary Row
        item {
            val isDarkTheme = settingsViewModel.isDarkMode.value
            val debtorsBg = if (isDarkTheme) Color(0xFF232530) else Color(0xFFE1E2EC)
            val debtorsOnBg = if (isDarkTheme) Color(0xFFD1E4FF) else Color(0xFF1B1B1F)

            val creditorsBg = if (isDarkTheme) Color(0xFF4C3E1A) else Color(0xFFFFE088)
            val creditorsOnBg = if (isDarkTheme) Color(0xFFFFECC1) else Color(0xFF1B1B1F)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Debtors (Other people owe me)
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = debtorsBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Group,
                                contentDescription = null,
                                tint = debtorsOnBg,
                                modifier = Modifier.size(24.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .background(debtorsOnBg.copy(alpha = 0.12f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "بدهکاران",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = debtorsOnBg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = formatToman(dashboardData.debtorsTotal),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = debtorsOnBg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Creditors (I owe other people)
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = creditorsBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Payments,
                                contentDescription = null,
                                tint = creditorsOnBg,
                                modifier = Modifier.size(24.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .background(creditorsOnBg.copy(alpha = 0.12f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "طلبکاران",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = creditorsOnBg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = formatToman(dashboardData.creditorsTotal),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = creditorsOnBg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Quick Smart Parsing Trigger Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAssistant() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "تحلیل هوشمند تراکنش",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "جمله بنویسید یا صحبت کنید تا خودکار ثبت شود!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Upcoming Installments Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "اقساط پیش‌رو",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (dashboardData.upcomingInstallments.isNotEmpty()) {
                    Text(
                        text = "باقی مانده: ${dashboardData.upcomingInstallments.size} مورد",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningOrange
                    )
                }
            }
        }

        // List of Upcoming Installments
        if (dashboardData.upcomingInstallments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "هیچ قسط پرداخت‌نشده پیش‌رویی ثبت نشده است.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            items(dashboardData.upcomingInstallments.take(3)) { installment ->
                InstallmentMiniItem(
                    installment = installment,
                    onTogglePaid = { installmentViewModel.toggleInstallmentPaid(installment) }
                )
            }
        }

        // Recent Activity Banner
        item {
            Text(
                text = "آخرین فعالیت‌ها",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "هنوز هیچ تراکنشی ثبت نشده است.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
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
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)
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
        onRefresh = { aiAssistantViewModel.fetchBudgetForecast(dashboardViewModel.transactions.value, dashboardViewModel.loans.value, dashboardViewModel.installments.value, dashboardViewModel.categories.value, aiAssistantViewModel.isOnlineMode.value, forceRefresh = true) }
    )
}
}

private fun extractForecastPreview(forecast: String): String {
    val lines = forecast.lines()
    val contentLines = lines.filter { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#")
    }.map { line ->
        line.trim().removePrefix("-").removePrefix("*").trim()
    }.filter { it.isNotEmpty() }

    if (contentLines.isEmpty()) return "گزارش آماده است"

    val preview = contentLines.take(3).joinToString(" | ") { line ->
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(WarningOrange.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = installment.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "سررسید: ${formatPersianDate(installment.dueDate)} | ${formatToman(installment.amount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Button(
                onClick = onTogglePaid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text("پرداخت", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun TransactionMiniItem(transaction: Transaction, categories: List<Category> = emptyList(), onClick: () -> Unit = {}, onDelete: () -> Unit = {}) {
    val isIncome = transaction.type == "INCOME"
    val category = categories.find { it.id == transaction.categoryId }
    val categoryColor = category?.let { Color(it.color) } ?: if (isIncome) IncomeGreen else ExpenseRed
    val icon = CATEGORY_ICONS_MAP[category?.icon] ?: Icons.Filled.Paid

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isIncome) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isIncome) IncomeGreen else ExpenseRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (if (isIncome) "+" else "-") + formatToman(transaction.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) IncomeGreen else ExpenseRed
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "حذف تراکنش",
                        tint = ExpenseRed.copy(alpha = 0.7f),
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "بستن"
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                when (val state = forecastState) {
                    is ForecastUIState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "در حال تحلیل و پیش‌بینی وضعیت بودجه...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRefresh) {
                                Text("تلاش مجدد")
                            }
                        }
                    }
                    is ForecastUIState.Success -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MarkdownText(text = state.forecast)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("بروزرسانی پیش‌بینی", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is ForecastUIState.Idle -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(onClick = onRefresh) {
                                Text("دریافت پیش‌بینی")
                            }
                        }
                    }
                }
            }
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
    val isIncome = transaction.type == "INCOME"
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نوع:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (isIncome) "درآمد" else "هزینه",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncome) IncomeGreen else ExpenseRed
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatToman(transaction.amount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncome) IncomeGreen else ExpenseRed
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ویرایش")
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
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
                Text("حذف", color = ExpenseRed)
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
    val context = LocalContext.current
    val isEditMode = transactionToEdit != null
    var selectedType by remember { mutableStateOf(transactionToEdit?.type ?: "EXPENSE") }
    var amountValue by remember { mutableStateOf(TextFieldValue(if (isEditMode) (transactionToEdit?.amount?.div(1000)?.toString() ?: "") else "")) }
    var descriptionText by remember { mutableStateOf(transactionToEdit?.description ?: "") }
    var selectedCategoryId by remember { mutableStateOf(transactionToEdit?.categoryId ?: 0L) }
    var personNameText by remember { mutableStateOf(transactionToEdit?.personName ?: "") }
    var titleText by remember { mutableStateOf(transactionToEdit?.description ?: "") }
    var daysFromNowText by remember { mutableStateOf("30") }
    var customDate by remember { mutableStateOf(transactionToEdit?.date ?: System.currentTimeMillis()) }

    val filteredCategories = categories.filter { cat ->
        when (selectedType) {
            "INCOME" -> cat.type == "INCOME" || cat.type == "BOTH"
            "EXPENSE" -> cat.type == "EXPENSE" || cat.type == "BOTH"
            else -> cat.key == "Loans" || cat.key == "Installments" || cat.key == "Other"
        }
    }

    val typeColor = when (selectedType) {
        "INCOME", "LOAN_DEBTOR" -> IncomeGreen
        "EXPENSE", "LOAN_CREDITOR" -> ExpenseRed
        else -> WarningOrange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Type selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "نوع تراکنش / تعهد مالی:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val types = listOf(
                                Pair("EXPENSE", "هزینه"),
                                Pair("INCOME", "درآمد"),
                                Pair("LOAN_DEBTOR", "طلب (قرض دادم)"),
                                Pair("LOAN_CREDITOR", "بدهی (قرض گرفتم)"),
                                Pair("INSTALLMENT", "قسط")
                            )
                            types.forEach { (typeKey, typeLabel) ->
                                val isSelected = selectedType == typeKey
                                val chipColor = when (typeKey) {
                                    "INCOME", "LOAN_DEBTOR" -> IncomeGreen
                                    "EXPENSE", "LOAN_CREDITOR" -> ExpenseRed
                                    else -> WarningOrange
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) chipColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable {
                                            selectedType = typeKey
                                            selectedCategoryId = when (typeKey) {
                                                "INCOME" -> categories.find { it.key == "Income" }?.id ?: 1L
                                                "LOAN_DEBTOR", "LOAN_CREDITOR" -> categories.find { it.key == "Loans" }?.id ?: 1L
                                                "INSTALLMENT" -> categories.find { it.key == "Installments" }?.id ?: 1L
                                                else -> selectedCategoryId
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Amount input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "مبلغ (تومان):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        OutlinedTextField(
                            value = amountValue,
                            onValueChange = { amountValue = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_amount_input"),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Paid,
                                    contentDescription = null,
                                    tint = typeColor
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = typeColor,
                                focusedLabelColor = typeColor
                            )
                        )
                        AmountQuickFillButtons(
                            amountValue = amountValue,
                            onValueChanged = { amountValue = it }
                        )
                        val amtToman = amountValue.text.toLongOrNull() ?: 0L
                        if (amtToman > 0L) {
                            val amtRial = amtToman * 1000L
                            Text(
                                text = "معادل: ${formatToman(amtRial)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = typeColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    // Category Selector
                    if (selectedType == "EXPENSE" || selectedType == "INCOME") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "دسته‌بندی مربوطه:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filteredCategories.forEach { cat ->
                                    val isSelected = selectedCategoryId == cat.id
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { selectedCategoryId = cat.id }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = cat.name,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Conditional Person Name for loans
                    if (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "طرف حساب (شخص مربوطه):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            OutlinedTextField(
                                value = personNameText,
                                onValueChange = { personNameText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_person_input"),
                                shape = RoundedCornerShape(12.dp),
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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "عنوان قسط:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                OutlinedTextField(
                                    value = titleText,
                                    onValueChange = { titleText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manual_title_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    placeholder = { Text("مثلا: قسط بانک مسکن", style = MaterialTheme.typography.bodyMedium) },
                                    singleLine = true
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "فاصله تا موعد پرداخت (روز):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                OutlinedTextField(
                                    value = daysFromNowText,
                                    onValueChange = { daysFromNowText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manual_days_input"),
                                    shape = RoundedCornerShape(12.dp),
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "شرح یا توضیح تراکنش:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        OutlinedTextField(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_description_input"),
                            shape = RoundedCornerShape(12.dp),
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

                Spacer(modifier = Modifier.height(8.dp))

                // Actions block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("انصراف")
                    }

                    Button(
                        onClick = {
                            val finalAmountToman = amountValue.text.toLongOrNull() ?: 0L
                            if (finalAmountToman <= 0L) {
                                android.widget.Toast.makeText(context, "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val finalAmountRial = finalAmountToman * 1000L

                            if (selectedType == "INCOME" || selectedType == "EXPENSE") {
                                if (selectedCategoryId == 0L) {
                                    android.widget.Toast.makeText(context, "لطفا دسته‌بندی را انتخاب کنید", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }

                            when (selectedType) {
                                "INCOME", "EXPENSE" -> {
                                    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "سایر"
                                    val desc = descriptionText.trim().ifEmpty { selectedCategoryName }
                                    if (isEditMode) {
                                        val updatedTransaction = transactionToEdit?.copy(
                                            type = selectedType,
                                            categoryId = selectedCategoryId,
                                            amount = finalAmountRial,
                                            description = desc,
                                            date = customDate
                                        ) ?: return@Button
                                        transactionViewModel.updateTransaction(updatedTransaction)
                                    } else {
                                        transactionViewModel.addTransaction(
                                            type = selectedType,
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
                                        android.widget.Toast.makeText(context, "لطفا نام شخص مربوطه را وارد کنید", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val desc = descriptionText.trim().ifEmpty { if (selectedType == "LOAN_DEBTOR") "قرض دادن به $person" else "قرض گرفتن از $person" }
                                    loanViewModel.addLoan(
                                        personName = person,
                                        type = if (selectedType == "LOAN_DEBTOR") "DEBTOR" else "CREDITOR",
                                        amount = finalAmountRial,
                                        description = desc,
                                        customDate = customDate
                                    )
                                }
                                "INSTALLMENT" -> {
                                    val title = titleText.trim()
                                    if (title.isEmpty()) {
                                        android.widget.Toast.makeText(context, "لطفا عنوان قسط را وارد کنید", android.widget.Toast.LENGTH_SHORT).show()
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
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = typeColor)
                    ) {
                        Text(if (isEditMode) "ذخیره تغییرات" else "ثبت تراکنش", color = Color.White)
                    }
                }
            }
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

    val calendar = remember(initialTimestamp) {
        Calendar.getInstance().apply { timeInMillis = initialTimestamp }
    }

    val jalaliDate = remember(initialTimestamp) {
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
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = initialTimestamp
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                }
                onTimestampChanged(newCal.timeInMillis)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "📅 تنظیم تاریخ و ساعت (شمسی):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date picker button
            OutlinedButton(
                onClick = { showJalaliDatePicker = true },
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = jalaliDate.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Time picker button
            OutlinedButton(
                onClick = { showCustomTimePicker = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format("%02d:%02d", hour, minute),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
