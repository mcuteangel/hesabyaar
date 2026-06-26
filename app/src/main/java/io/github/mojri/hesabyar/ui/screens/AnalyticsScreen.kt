package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.ui.*
import io.github.mojri.hesabyar.ui.theme.ExpenseRed
import io.github.mojri.hesabyar.ui.theme.IncomeGreen

@Composable
fun AnalyticsScreen(
    analyticsViewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val analyticsData by analyticsViewModel.analyticsData.collectAsState()

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
                text = "تحلیل و آمار پیشرفته",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Monthly Spending Trend Chart
        item {
            MonthlyTrendCard(
                title = "📈 روند هزینه‌های ماهانه",
                data = analyticsData.monthlySpending,
                getValue = { it.expense },
                color = ExpenseRed
            )
        }

        // Monthly Income Trend Chart
        item {
            MonthlyTrendCard(
                title = "💰 روند درآمدهای ماهانه",
                data = analyticsData.monthlyIncome,
                getValue = { it.income },
                color = IncomeGreen
            )
        }

        // Combined Income vs Expense Line Chart
        item {
            CombinedLineChartCard(
                spending = analyticsData.monthlySpending,
                income = analyticsData.monthlyIncome
            )
        }

        // Category Breakdown (Donut Chart)
        item {
            CategoryBreakdownCard(categoryBreakdown = analyticsData.categoryBreakdown)
        }

        // Debt Summary
        item {
            DebtCreditSummaryCard(
                title = "👥 خلاصه بدهکاران",
                items = analyticsData.debtors,
                totalAmount = analyticsData.totalDebt,
                icon = Icons.Filled.AccountBalance,
                emptyMessage = "بدهکاری ثبت نشده"
            )
        }

        // Creditor Summary
        item {
            DebtCreditSummaryCard(
                title = "🏦 خلاصه طلبکاران",
                items = analyticsData.creditors,
                totalAmount = analyticsData.totalCredit,
                icon = Icons.Filled.CreditCard,
                emptyMessage = "طلبکاری ثبت نشده"
            )
        }

        // Loan Status
        item {
            LoanStatusCard(loans = analyticsData.activeLoans)
        }

        // Installment Completion Progress
        item {
            InstallmentProgressCard(
                total = analyticsData.totalInstallments,
                paid = analyticsData.paidInstallments,
                installments = analyticsData.installmentProgress
            )
        }
    }
}

@Composable
private fun MonthlyTrendCard(
    title: String,
    data: List<MonthlyData>,
    getValue: (MonthlyData) -> Long,
    color: Color
) {
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
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (data.isEmpty()) {
                Text(
                    text = "داده‌ای موجود نیست",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                // Bar chart
                BarChart(
                    data = data.map { getValue(it) },
                    labels = data.map { it.label },
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    data.forEach { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = formatToman(getValue(item)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CombinedLineChartCard(
    spending: List<MonthlyData>,
    income: List<MonthlyData>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 مقایسه درآمد و هزینه",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Merge by label for combined view
            val allLabels = (spending.map { it.label } + income.map { it.label }).distinct().takeLast(6)
            val spendByLabel = spending.associateBy { it.label }
            val incomeByLabel = income.associateBy { it.label }

            val spendValues = allLabels.map { label -> spendByLabel[label]?.expense ?: 0L }
            val incomeValues = allLabels.map { label -> incomeByLabel[label]?.income ?: 0L }
            val allValues = spendValues + incomeValues
            val maxValue = allValues.maxOrNull()?.coerceAtLeast(1) ?: 1L

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val chartHeight = size.height - 40f
                val chartWidth = size.width - 40f
                val startX = 20f
                val spacing = if (allLabels.isNotEmpty()) chartWidth / (allLabels.size - 1).coerceAtLeast(1) else chartWidth

                // Draw income line (green)
                if (incomeValues.isNotEmpty()) {
                    val points = incomeValues.mapIndexed { idx, value ->
                        val x = startX + idx * spacing
                        val y = chartHeight - (if (maxValue > 0) (value.toFloat() / maxValue) * chartHeight else 0f)
                        Offset(x, y)
                    }
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = IncomeGreen,
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                    points.forEach { pt ->
                        drawCircle(color = IncomeGreen, radius = 6f, center = pt)
                    }
                }

                // Draw expense line (red)
                if (spendValues.isNotEmpty()) {
                    val points = spendValues.mapIndexed { idx, value ->
                        val x = startX + idx * spacing
                        val y = chartHeight - (if (maxValue > 0) (value.toFloat() / maxValue) * chartHeight else 0f)
                        Offset(x, y)
                    }
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = ExpenseRed,
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                    points.forEach { pt ->
                        drawCircle(color = ExpenseRed, radius = 6f, center = pt)
                    }
                }

                // Labels
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                        this.color = android.graphics.Color.GRAY
                    }
                    allLabels.forEachIndexed { idx, label ->
                        val x = startX + idx * spacing
                        drawText(label, x, size.height, paint)
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(12.dp).background(IncomeGreen, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("درآمد", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(12.dp).background(ExpenseRed, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("هزینه", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun BarChart(
    data: List<Long>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOrNull() ?: 1L
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val barWidth = size.width / (data.size * 2f)
        val chartHeight = size.height - 40f
        val spacing = barWidth

        data.forEachIndexed { index, value ->
            val barHeight = if (maxValue > 0) (value.toFloat() / maxValue) * chartHeight else 0f
            val x = index * (barWidth + spacing) + spacing / 2
            val y = chartHeight - barHeight

            // Bar
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )

            // Label
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                    this.color = android.graphics.Color.GRAY
                }
                drawText(labels[index], x + barWidth / 2, size.height, paint)
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(categoryBreakdown: List<CategoryBreakdown>) {
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
                text = "🍩 توزیع هزینه‌ها بر اساس دسته‌بندی",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (categoryBreakdown.isEmpty()) {
                Text(
                    text = "هزینه‌ای ثبت نشده",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                // Donut chart
                DonutChart(
                    data = categoryBreakdown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                // Legend
                categoryBreakdown.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(item.color), CircleShape)
                            )
                            Text(
                                text = item.categoryName,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "${(item.percentage * 100).toInt()}٪ | ${formatToman(item.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    data: List<CategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.total }
    var startAngle = -90f

    Canvas(modifier = modifier) {
        val strokeWidth = 40f
        val diameter = minOf(size.width, size.height) - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        data.forEach { item ->
            val sweepAngle = if (total > 0) (item.total.toFloat() / total) * 360f else 0f
            drawArc(
                color = Color(item.color),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun DebtCreditSummaryCard(
    title: String,
    items: List<DebtSummary>,
    totalAmount: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyMessage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = formatToman(totalAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (items.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else ExpenseRed
                )
            }

            if (items.isEmpty()) {
                Text(
                    text = emptyMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                items.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.personName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatToman(item.remainingAmount),
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseRed
                            )
                        }

                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = IncomeGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "پرداخت شده: ${(item.progress * 100).toInt()}٪",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "اصل: ${formatToman(item.originalAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanStatusCard(loans: List<Loan>) {
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
                text = "📊 وضعیت وام‌ها",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (loans.isEmpty()) {
                Text(
                    text = "وام فعالی ثبت نشده",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                loans.forEach { loan ->
                    val progress = if (loan.originalAmount > 0) {
                        1f - (loan.remainingAmount.toFloat() / loan.originalAmount)
                    } else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = loan.personName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (loan.type == "DEBTOR") "بدهکار" else "طلبکار",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (loan.type == "DEBTOR") ExpenseRed else IncomeGreen
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (loan.type == "DEBTOR") ExpenseRed else IncomeGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "باقیمانده: ${formatToman(loan.remainingAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "اصل: ${formatToman(loan.originalAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallmentProgressCard(
    total: Int,
    paid: Int,
    installments: List<InstallmentProgress>
) {
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
                text = "✅ پیشرفت اقساط",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (total == 0) {
                Text(
                    text = "قسطی ثبت نشده",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                // Progress ring
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgress(
                        progress = if (total > 0) paid.toFloat() / total else 0f,
                        modifier = Modifier.size(100.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$paid از $total قسط پرداخت شده",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${if (total > 0) (paid * 100 / total) else 0}٪ تکمیل شده",
                            style = MaterialTheme.typography.bodySmall,
                            color = IncomeGreen
                        )
                    }
                }

                // List of unpaid installments
                val unpaid = installments.filter { !it.isPaid }.take(5)
                if (unpaid.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(
                        text = "اقساط پرداخت نشده:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    unpaid.forEach { inst ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = inst.title,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = formatToman(inst.amount),
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = IncomeGreen,
    strokeWidth: Float = 12f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height) - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        // Background arc
        drawArc(
            color = Color.LightGray.copy(alpha = 0.3f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Progress arc
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = animatedProgress * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
