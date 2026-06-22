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
import androidx.compose.ui.text.input.KeyboardType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.HesabyarViewModel
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.theme.ExpenseRed
import io.github.mojri.hesabyar.ui.theme.IncomeGreen
import io.github.mojri.hesabyar.ui.theme.WarningOrange
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

fun formatToman(value: Double): String {
    val formatter = DecimalFormat("#,###")
    return "${formatter.format(value)} تومان"
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
    viewModel: HesabyarViewModel,
    onNavigateToAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dashboardData by viewModel.dashboardState.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val forecastState by viewModel.forecastState.collectAsState()
    var showManualAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = transactions) {
        viewModel.fetchBudgetForecast()
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
                    onClick = { viewModel.toggleDarkMode() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = if (viewModel.isDarkMode.value) Icons.Filled.LightMode else Icons.Filled.DarkMode,
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

        // Smart Forecast Alert Card (هشدار هوشمند پیش‌بینی بودجه ماه آینده)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_forecast_alert_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            Text(
                                text = "پیش‌بینی وضعیت بودجه ماه آینده",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "هشدار هوشمند",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))

                    when (val state = forecastState) {
                        is ForecastUIState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "در حال تحلیل و پیش‌بینی وضعیت بودجه...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        is ForecastUIState.Error -> {
                            Text(
                                text = "⚠️ متاسفانه خطایی در تخمین هوشمند بودجه رخ داد: ${state.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ExpenseRed
                            )
                        }
                        is ForecastUIState.Success -> {
                            MarkdownText(text = state.forecast)
                        }
                        is ForecastUIState.Idle -> {
                            Text(
                                text = "در حال بارگذاری...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
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

        // Debtors and Creditors summary Row
        item {
            val isDarkTheme = viewModel.isDarkMode.value
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
                    onTogglePaid = { viewModel.toggleInstallmentPaid(installment) }
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
                TransactionMiniItem(transaction = transaction)
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
        viewModel = viewModel,
        onDismiss = { showManualAddDialog = false }
    )
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
fun TransactionMiniItem(transaction: Transaction) {
    val isIncome = transaction.type == "INCOME"
    val icon = when (transaction.category) {
        "Food" -> Icons.Filled.Restaurant
        "Transportation" -> Icons.Filled.DirectionsCar
        "Shopping" -> Icons.Filled.ShoppingBag
        "Bills" -> Icons.Filled.ReceiptLong
        "Installments" -> Icons.Filled.CreditCard
        "Loans" -> Icons.Filled.HistoryEdu
        "Income" -> Icons.Filled.Paid
        else -> Icons.Filled.Paid
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
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
                        text = "${formatPersianDate(transaction.date)} | ${getPersianCategory(transaction.category)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Text(
                text = (if (isIncome) "+" else "-") + formatToman(transaction.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) IncomeGreen else ExpenseRed
            )
        }
    }
}

fun getPersianCategory(english: String): String {
    return when (english) {
        "Food" -> "خوراک"
        "Transportation" -> "حمل و نقل"
        "Shopping" -> "خرید"
        "Bills" -> "قبوض"
        "Installments" -> "اقساط"
        "Loans" -> "وام و قرض"
        "Income" -> "درآمد"
        else -> "سایر"
    }
}

@Composable
fun ManualTransactionDialog(
    viewModel: HesabyarViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf("EXPENSE") }
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var personNameText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var daysFromNowText by remember { mutableStateOf("30") }
    var customDate by remember { mutableStateOf(System.currentTimeMillis()) }

    val categoriesList = listOf("Food", "Transportation", "Shopping", "Bills", "Installments", "Loans", "Income", "Other")

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
                        text = "✍️ ثبت دستی تراکنش جدید",
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
                                            if (typeKey == "INCOME") selectedCategory = "Income"
                                            else if (typeKey == "LOAN_DEBTOR" || typeKey == "LOAN_CREDITOR") selectedCategory = "Loans"
                                            else if (typeKey == "INSTALLMENT") selectedCategory = "Installments"
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
                            value = amountText,
                            onValueChange = { amountText = it },
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
                        val amtDouble = amountText.toDoubleOrNull() ?: 0.0
                        if (amtDouble > 0.0) {
                            Text(
                                text = "معادل: ${formatToman(amtDouble)}",
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
                                categoriesList.forEach { cat ->
                                    val isSelected = selectedCategory == cat
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { selectedCategory = cat }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = getPersianCategory(cat),
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
                            val finalAmount = amountText.toDoubleOrNull() ?: 0.0
                            if (finalAmount <= 0.0) {
                                android.widget.Toast.makeText(context, "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            when (selectedType) {
                                "INCOME", "EXPENSE" -> {
                                    val desc = descriptionText.trim().ifEmpty { getPersianCategory(selectedCategory) }
                                    viewModel.addTransaction(
                                        type = selectedType,
                                        category = selectedCategory,
                                        amount = finalAmount,
                                        description = desc,
                                        customDate = customDate
                                    )
                                }
                                "LOAN_DEBTOR", "LOAN_CREDITOR" -> {
                                    val person = personNameText.trim()
                                    if (person.isEmpty()) {
                                        android.widget.Toast.makeText(context, "لطفا نام شخص مربوطه را وارد کنید", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val desc = descriptionText.trim().ifEmpty { if (selectedType == "LOAN_DEBTOR") "قرض دادن به $person" else "قرض گرفتن از $person" }
                                    viewModel.addLoan(
                                        personName = person,
                                        type = if (selectedType == "LOAN_DEBTOR") "DEBTOR" else "CREDITOR",
                                        amount = finalAmount,
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
                                    viewModel.addInstallment(
                                        title = title,
                                        amount = finalAmount,
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
                        Text("ثبت تراکنش", color = Color.White)
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
