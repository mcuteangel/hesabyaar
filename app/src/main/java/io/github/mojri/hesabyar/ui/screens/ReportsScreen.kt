package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.HesabyarViewModel
import io.github.mojri.hesabyar.ui.AdvisorUIState
import io.github.mojri.hesabyar.ui.theme.ExpenseRed
import io.github.mojri.hesabyar.ui.theme.IncomeGreen
import io.github.mojri.hesabyar.ui.theme.WarningOrange
import java.util.*

@Composable
fun ReportsScreen(
    viewModel: HesabyarViewModel,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.transactions.collectAsState()
    var filterSelection by remember { mutableStateOf("MONTHLY") } // "DAILY", "MONTHLY", "YEARLY"
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    // Calculate dynamic stats based on filter selection
    val now = System.currentTimeMillis()
    val filterDuration = when (filterSelection) {
        "DAILY" -> 24L * 60L * 60L * 1000L                   // 24 hours
        "MONTHLY" -> 30L * 24L * 60L * 60L * 1000L            // 30 days
        "YEARLY" -> 365L * 24L * 60L * 60L * 1000L           // 365 days
        else -> 30L * 24L * 60L * 60L * 1000L
    }

    val filteredList = transactions.filter { it.date >= (now - filterDuration) }
    val displayList = if (selectedCategoryFilter != null) {
        transactions.filter { it.category == selectedCategoryFilter }.sortedByDescending { it.date }
    } else {
        transactions.sortedByDescending { it.date }
    }

    var totalIncome = 0.0
    var totalExpense = 0.0
    val categoryTotals = HashMap<String, Double>()

    filteredList.forEach {
        if (it.type == "INCOME") {
            totalIncome += it.amount
        } else {
            totalExpense += it.amount
            val catTotal = categoryTotals[it.category] ?: 0.0
            categoryTotals[it.category] = catTotal + it.amount
        }
    }

    val balance = totalIncome - totalExpense

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        // Title
        item {
            Text(
                text = "کیف پول و گزارش‌های تحلیلی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Horizontal filter bar (Daily, Monthly, Yearly)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val reportPeriods = listOf(
                    "DAILY" to "روزانه (۲۴ ساعت)",
                    "MONTHLY" to "ماهانه (۳۰ روز)",
                    "YEARLY" to "سالانه (۳۶۵ روز)"
                )

                reportPeriods.forEach { (period, title) ->
                    Button(
                        onClick = { filterSelection = period },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (filterSelection == period) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (filterSelection == period) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = null,
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Text(title, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📈 تراز عملکرد سود و زیان دوره",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مجموع کل دریافتی‌ها (درآمد):", style = MaterialTheme.typography.bodyMedium)
                        Text(formatToman(totalIncome), color = IncomeGreen, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مجموع کل پرداختی‌ها (مخارج):", style = MaterialTheme.typography.bodyMedium)
                        Text(formatToman(totalExpense), color = ExpenseRed, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("پس‌انداز خالص دوره:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = (if (balance >= 0) "+" else "") + formatToman(balance),
                            color = if (balance >= 0) IncomeGreen else ExpenseRed,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // AI Budget Advisor Card
        item {
            val budgetState by viewModel.advisorState.collectAsState()
            val providerStatus = viewModel.getProviderStatusText()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "توصیه‌های هوشمند بودجه",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = providerStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        if (budgetState is AdvisorUIState.Success) {
                            IconButton(
                                onClick = { viewModel.clearAdvisorState() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "بستن",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    when (val state = budgetState) {
                        is AdvisorUIState.Idle -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "با تحلیل عمیق تراکنش‌ها، قسط‌ها و امور مالی ثبت شده، توصیه‌های اختصاصی جهت بهبود وضعیت بودجه خود را از هوش مصنوعی دریافت کنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                                Button(
                                    onClick = { viewModel.fetchBudgetAdvice() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تحلیل هوشمند و دریافت توصیه",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        is AdvisorUIState.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "درحال تحلیل تراکنش‌ها و دریافت راهکارها...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is AdvisorUIState.Success -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = state.advice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.fetchBudgetAdvice(forceRefresh = true) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("بروزرسانی تحلیل", style = MaterialTheme.typography.labelSmall)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.clearAdvisorState() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("بستن", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        is AdvisorUIState.Error -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    onClick = { viewModel.fetchBudgetAdvice(forceRefresh = true) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("تلاش مجدد", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category breakdown header
        item {
            Text(
                text = "📊 سهم دسته‌بندی‌ها از کل هزینه‌ها",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (categoryTotals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "در این بازه هزینه ثبت شده‌ای یافت نشد.",
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
            // Display visual progress bars for each expense category
            items(categoryTotals.toList().sortedByDescending { it.second }) { (category, total) ->
                val ratio = if (totalExpense > 0) (total / totalExpense).toFloat() else 0f
                val percent = (ratio * 100).toInt()
                val isSelected = selectedCategoryFilter == category

                val categoryColor = when (category) {
                    "Food" -> IncomeGreen
                    "Transportation" -> WarningOrange
                    "Shopping" -> MaterialTheme.colorScheme.primary
                    "Bills" -> MaterialTheme.colorScheme.tertiary
                    "Installments" -> ExpenseRed
                    "Loans" -> Color(0xFF9C27B0)
                    "Income" -> IncomeGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedCategoryFilter = if (isSelected) null else category },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(categoryColor, CircleShape)
                                )
                                Text(
                                    text = getPersianCategory(category),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "$percent٪ | " + formatToman(total),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = categoryColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Transactions listed for the targeted period
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "📜 تمامی تراکنش‌های ثبت شده (${displayList.size} مورد)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Horizontal category filter chips row
                val categoriesList = listOf(
                    null to "همه",
                    "Food" to "خوراک",
                    "Transportation" to "حمل و نقل",
                    "Shopping" to "خرید",
                    "Bills" to "قبوض",
                    "Installments" to "اقساط",
                    "Loans" to "وام و قرض",
                    "Income" to "درآمد",
                    "Other" to "سایر"
                )

                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(categoriesList) { (catKey, catValue) ->
                        val isSelected = selectedCategoryFilter == catKey
                        val chipColor = when (catKey) {
                            "Food" -> IncomeGreen
                            "Transportation" -> WarningOrange
                            "Shopping" -> MaterialTheme.colorScheme.primary
                            "Bills" -> MaterialTheme.colorScheme.tertiary
                            "Installments" -> ExpenseRed
                            "Loans" -> Color(0xFF9C27B0)
                            "Income" -> IncomeGreen
                            else -> MaterialTheme.colorScheme.primary
                        }

                        CategoryFilterChip(
                            text = catValue,
                            isSelected = isSelected,
                            onClick = { selectedCategoryFilter = catKey },
                            activeColor = chipColor
                        )
                    }
                }
            }
        }

        if (displayList.isEmpty()) {
            item {
                Text(
                    text = if (selectedCategoryFilter != null) "هیچ تراکنشی با این دسته‌بندی یافت نشد." else "تراکنشی یافت نشد.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            items(displayList) { transaction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = transaction.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${formatPersianDate(transaction.date)} | ${getPersianCategory(transaction.category)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = (if (transaction.type == "INCOME") "+" else "-") + formatToman(transaction.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == "INCOME") IncomeGreen else ExpenseRed
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    activeColor: Color
) {
    val bgColor = if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val borderStrokeModifier = if (isSelected) Modifier else Modifier.border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(12.dp)
    )

    Box(
        modifier = Modifier
            .then(borderStrokeModifier)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
