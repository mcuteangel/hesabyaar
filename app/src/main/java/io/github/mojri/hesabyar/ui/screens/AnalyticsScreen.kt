package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.rust.BankLoanSummary
import io.github.mojri.hesabyar.ui.*
import io.github.mojri.hesabyar.ui.charts.BarEntry
import io.github.mojri.hesabyar.ui.charts.ChartPoint
import io.github.mojri.hesabyar.ui.charts.ChartSeries
import io.github.mojri.hesabyar.ui.charts.ChartTokens
import io.github.mojri.hesabyar.ui.charts.DonutSlice
import io.github.mojri.hesabyar.ui.charts.HesabyarBarChart
import io.github.mojri.hesabyar.ui.charts.HesabyarDonutChart
import io.github.mojri.hesabyar.ui.charts.HesabyarLineChart
import io.github.mojri.hesabyar.ui.charts.HesabyarProgressRing
import io.github.mojri.hesabyar.ui.components.AccountSelector
import io.github.mojri.hesabyar.ui.components.CardEmptyHint
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor
import io.github.mojri.hesabyar.ui.screens.dashboard.components.AccountBreakdownCard
import io.github.mojri.hesabyar.ui.utils.partyColor
import io.github.mojri.hesabyar.ui.utils.partyLabelRes

@Composable
fun AnalyticsScreen(
  analyticsViewModel: AnalyticsViewModel,
  modifier: Modifier = Modifier
) {
  val analyticsData by analyticsViewModel.analyticsData.collectAsState()
  val accounts by analyticsViewModel.accounts.collectAsState()
  val selectedAccountId by analyticsViewModel.selectedAccountId.collectAsState()

  LazyColumn(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = SpacingTokens.lg),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
    contentPadding = PaddingValues(top = SpacingTokens.sm, bottom = SpacingTokens.xl)
  ) {
    // Title
    item {
      Text(
        text = "تحلیل و آمار پیشرفته",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
      )
    }

    // Account Selector
    if (accounts.isNotEmpty()) {
      item {
        AccountSelector(
          accounts = accounts,
          selectedAccountId = selectedAccountId,
          onAccountSelected = { analyticsViewModel.selectAccount(it) }
        )
      }
    }

    // Monthly Spending Trend Chart
    item {
      MonthlyTrendCard(
        title = "روند هزینه‌های ماهانه",
        data = analyticsData.monthlySpending,
        getValue = { it.expense },
        color = MaterialTheme.colorScheme.error
      )
    }

    // Monthly Income Trend Chart
    item {
      MonthlyTrendCard(
        title = "روند درآمدهای ماهانه",
        data = analyticsData.monthlyIncome,
        getValue = { it.income },
        color = MaterialTheme.colorScheme.primary
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

    // Account Breakdown (Donut Chart — per-account expense distribution)
    if (analyticsData.accountBreakdown.isNotEmpty()) {
      item {
        AccountBreakdownCard(accountBreakdown = analyticsData.accountBreakdown)
      }
    }

    // Debt Summary
    item {
      DebtCreditSummaryCard(
        title = "خلاصه بدهکاران",
        items = analyticsData.debtors,
        totalAmount = analyticsData.totalDebt,
        icon = Icons.Filled.AccountBalance,
        emptyMessage = "بدهکاری ثبت نشده"
      )
    }

    // Creditor Summary
    item {
      DebtCreditSummaryCard(
        title = "خلاصه طلبکاران",
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

    // Bank Loan Status
    item {
      BankLoanStatusCard(
        bankLoans = analyticsData.bankLoans,
        totalDebt = analyticsData.bankLoansTotalDebt
      )
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
  HesabyarCard(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
      )

      if (data.isEmpty()) {
        CardEmptyHint(message = "داده‌ای موجود نیست")
      } else {
        // Bar chart
        HesabyarBarChart(
          entries = data.map { BarEntry(label = it.label, value = getValue(it)) },
          height = ChartTokens.DefaultHeight,
          defaultColor = color,
          labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier =
            Modifier
              .fillMaxWidth(),
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
                text = CurrencyFormatter.format(getValue(item)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
              )
              Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
  HesabyarCard(
    modifier = modifier.fillMaxWidth()
  ) {
    Column {
      Text(
        text = "مقایسه درآمد و هزینه",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
      )
      Spacer(modifier = Modifier.height(SpacingTokens.md))

      // Merge by (jalaliYear, jalaliMonth), since Rust emits different label formats
      // per series (spending labels append " (N days)", income labels do not), so
      // the label strings would not match while the year/month do.
      val spendPeriods = spending.map { it.jalaliYear to it.jalaliMonth }
      val incomePeriods = income.map { it.jalaliYear to it.jalaliMonth }
      val allPeriods = (spendPeriods + incomePeriods).distinct().takeLast(6)
      val spendByPeriod = spending.associateBy { it.jalaliYear to it.jalaliMonth }
      val incomeByPeriod = income.associateBy { it.jalaliYear to it.jalaliMonth }
      val incomeColor = MaterialTheme.colorScheme.primary
      val expenseColor = MaterialTheme.colorScheme.error

      fun resolvePeriodLabel(period: Pair<Int, Int>): String =
        spendByPeriod[period]?.label?.substringBefore(" (")
          ?: incomeByPeriod[period]?.label
          ?: "${period.first}/${period.second}"

      val chartPoints = allPeriods.map { period -> resolvePeriodLabel(period) }
      val spendSeries =
        ChartSeries(
          name = "هزینه",
          color = expenseColor,
          points =
            allPeriods.mapIndexed { index, period ->
              ChartPoint(
                label = chartPoints[index],
                value = spendByPeriod[period]?.expense ?: 0L,
              )
            },
        )
      val incomeSeries =
        ChartSeries(
          name = "درآمد",
          color = incomeColor,
          points =
            allPeriods.mapIndexed { index, period ->
              ChartPoint(
                label = chartPoints[index],
                value = incomeByPeriod[period]?.income ?: 0L,
              )
            },
        )

      val chartContentDescription =
        "مقایسه درآمد و هزینه در ۶ دوره اخیر: " +
          allPeriods.joinToString(separator = "، ") { period ->
            val label = resolvePeriodLabel(period)
            val inc = CurrencyFormatter.format(incomeByPeriod[period]?.income ?: 0L)
            val exp = CurrencyFormatter.format(spendByPeriod[period]?.expense ?: 0L)
            "$label: درآمد $inc، هزینه $exp"
          }

      HesabyarLineChart(
        series = listOf(incomeSeries, spendSeries),
        height = ChartTokens.LineChartHeight,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        contentDescriptionText = chartContentDescription,
        modifier =
          Modifier
            .fillMaxWidth()
            .height(160.dp),
      )

      // Legend
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(modifier = Modifier.width(SpacingTokens.xs))
        Text("درآمد", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(SpacingTokens.lg))
        Box(modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        Spacer(modifier = Modifier.width(SpacingTokens.xs))
        Text("هزینه", style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

@Composable
private fun CategoryBreakdownCard(categoryBreakdown: List<CategoryBreakdown>) {
  HesabyarCard(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Text(
        text = "توزیع هزینه‌ها بر اساس دسته‌بندی",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
      )

      if (categoryBreakdown.isEmpty()) {
        CardEmptyHint(message = "هزینه‌ای ثبت نشده")
      } else {
        // Donut chart
        HesabyarDonutChart(
          slices =
            categoryBreakdown.map { item ->
              DonutSlice(
                label = item.categoryName,
                value = item.total,
                color = item.color.toComposeColor(),
                percentage = item.percentage,
              )
            },
          height = ChartTokens.DonutChartHeight,
          modifier =
            Modifier
              .fillMaxWidth(),
        )

        // Legend
        categoryBreakdown.forEach { item ->
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(vertical = SpacingTokens.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
            ) {
              Box(
                modifier =
                  Modifier
                    .size(12.dp)
                    .background(item.color.toComposeColor(), CircleShape)
              )
              Text(
                text = item.categoryName,
                style = MaterialTheme.typography.bodySmall
              )
            }
            Text(
              text = "${(item.percentage * 100).toInt()}٪ | ${CurrencyFormatter.format(item.total)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = Dimens.SECONDARY_TEXT_ALPHA)
            )
          }
        }
      }
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
  HesabyarCard(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
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
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.IconSmall)
          )
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
          )
        }
        Text(
          text = CurrencyFormatter.format(totalAmount),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color =
            if (items.isEmpty()) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else if (items.first().type == LoanType.DEBTOR.name
            ) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.error
            }
        )
      }

      if (items.isEmpty()) {
        CardEmptyHint(message = emptyMessage)
      } else {
        items.forEach { item ->
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(ShapeTokens.Medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(SpacingTokens.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
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
                text = CurrencyFormatter.format(item.remainingAmount),
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (item.type == LoanType.DEBTOR.name
                  ) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.error
                  }
              )
            }

            LinearProgressIndicator(
              progress = { item.progress },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(ShapeTokens.Small),
              color =
                if (item.type == LoanType.DEBTOR.name
                ) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.error
                },
              trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "پرداخت شده: ${(item.progress * 100).toInt()}٪",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "اصل: ${CurrencyFormatter.format(item.originalAmount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
  HesabyarCard(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Text(
        text = "وضعیت وام‌ها",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
      )

      if (loans.isEmpty()) {
        CardEmptyHint(message = "وام فعالی ثبت نشده")
      } else {
        loans.forEach { loan ->
          val progress =
            if (loan.originalAmount > 0) {
              1f - (loan.remainingAmount.toFloat() / loan.originalAmount)
            } else {
              0f
            }

          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(ShapeTokens.Medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(SpacingTokens.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
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
                text = stringResource(loan.type.partyLabelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = loan.type.partyColor()
              )
            }

            LinearProgressIndicator(
              progress = { progress },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(ShapeTokens.Small),
              color = loan.type.partyColor(),
              trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "باقیمانده: ${CurrencyFormatter.format(loan.remainingAmount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "اصل: ${CurrencyFormatter.format(loan.originalAmount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BankLoanStatusCard(
  bankLoans: List<BankLoanSummary>,
  totalDebt: Long
) {
  HesabyarCard(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "وضعیت وام‌های بانکی",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.semantics { heading() },
        )
        Text(
          text = CurrencyFormatter.format(totalDebt),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.error
        )
      }

      if (bankLoans.isEmpty()) {
        CardEmptyHint(message = "وام بانکی ثبت نشده")
      } else {
        bankLoans.forEach { bl ->
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(ShapeTokens.Medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(SpacingTokens.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${bl.bankName} — ${bl.loanName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (bl.isSettled) {
                Text(
                  text = "تسویه شده",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "مانده: ${CurrencyFormatter.format(bl.remainingDebt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
  HesabyarCard(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Text(
        text = "پیشرفت اقساط",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )

      if (total == 0) {
        CardEmptyHint(message = "قسطی ثبت نشده")
      } else {
        // Progress ring
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          HesabyarProgressRing(
            progress = if (total > 0) paid.toFloat() / total else 0f,
            modifier = Modifier,
          )

          Column(
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
          ) {
            Text(
              text = "$paid از $total قسط پرداخت شده",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${if (total > 0) (paid * 100 / total) else 0}٪ تکمیل شده",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary
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
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(ShapeTokens.Small)
                  .background(MaterialTheme.colorScheme.surfaceContainerLow)
                  .padding(SpacingTokens.sm),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = inst.title,
                style = MaterialTheme.typography.bodySmall
              )
              Text(
                text = CurrencyFormatter.format(inst.amount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }
    }
  }
}
