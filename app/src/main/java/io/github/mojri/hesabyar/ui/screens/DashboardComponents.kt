package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.rust.BankLoanSummary
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.DashboardData
import io.github.mojri.hesabyar.ui.ForecastUIState
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import java.util.*

@Composable
internal fun DashboardHeader(settingsViewModel: SettingsViewModel) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = SpacingTokens.md),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Box(
        modifier =
          Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.AccountBalanceWallet,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(Dimens.IconMedium)
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
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    IconButton(
      onClick = { settingsViewModel.toggleDarkMode() },
      modifier =
        Modifier
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
          .size(Dimens.ButtonHeight)
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

@Composable
internal fun SmartForecastCard(
  forecastState: ForecastUIState,
  lastForecastFetchTime: Long,
  aiAssistantViewModel: AiAssistantViewModel,
  onShowForecast: () -> Unit
) {
  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .testTag("budget_forecast_alert_card")
        .clickable { onShowForecast() },
    shape = ShapeTokens.Large,
    cardColors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
      )
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Box(
        modifier =
          Modifier
            .size(Dimens.AvatarSmall)
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
              Spacer(modifier = Modifier.width(SpacingTokens.sm))
              Text(
                text = "در حال تحلیل...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          is ForecastUIState.Success -> {
            val preview = extractForecastPreview(state.forecast)
            Column {
              Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(SpacingTokens.xs))
              Text(
                text = "آخرین به‌روزرسانی: ${aiAssistantViewModel.formatLastFetchTime(
                  lastForecastFetchTime
                )}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
              color = MaterialTheme.colorScheme.onSurfaceVariant
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
      onClick = onShowForecast,
      modifier = Modifier.fillMaxWidth(),
      shape = ShapeTokens.Medium,
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
      Icon(
        imageVector = Icons.Filled.Assignment,
        contentDescription = null,
        modifier = Modifier.size(Dimens.IconSmall)
      )
      Spacer(modifier = Modifier.width(SpacingTokens.sm))
      Text("مشاهده گزارش کامل", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
internal fun IncomeExpenseCards(dashboardData: DashboardData) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md),
    maxItemsInEachRow = 2
  ) {
    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier
                .size(28.dp)
                .background(FinancialColors.IncomeGreen.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.TrendingUp,
              contentDescription = null,
              tint = FinancialColors.IncomeGreen,
              modifier = Modifier.size(Dimens.IconSmall)
            )
          }
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
          Text(
            text = "درآمد ۳۰ روزه",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          text = CurrencyFormatter.format(dashboardData.monthlyIncome),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = FinancialColors.IncomeGreen,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier
                .size(28.dp)
                .background(FinancialColors.ExpenseRed.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.TrendingDown,
              contentDescription = null,
              tint = FinancialColors.ExpenseRed,
              modifier = Modifier.size(Dimens.IconSmall)
            )
          }
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
          Text(
            text = "مخارج ۳۰ روزه",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          text = CurrencyFormatter.format(dashboardData.monthlyExpenses),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = FinancialColors.ExpenseRed,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
internal fun KpiCards(dashboardData: DashboardData) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md),
    maxItemsInEachRow = 2
  ) {
    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier
                .size(28.dp)
                .background(FinancialColors.IncomeGreen.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.Savings,
              contentDescription = null,
              tint = FinancialColors.IncomeGreen,
              modifier = Modifier.size(Dimens.IconSmall)
            )
          }
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
          Text(
            text = "نرخ پس‌انداز",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        val savingsPct = (dashboardData.savingsRate * 100).toInt()
        Text(
          text = "$savingsPct%",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color =
            when {
              savingsPct >= 20 -> FinancialColors.IncomeGreen
              savingsPct >= 0 -> FinancialColors.WarningOrange
              else -> FinancialColors.ExpenseRed
            }
        )
      }
    }

    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier
                .size(28.dp)
                .background(FinancialColors.InfoBlue.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.AccountBalance,
              contentDescription = null,
              tint = FinancialColors.InfoBlue,
              modifier = Modifier.size(Dimens.IconSmall)
            )
          }
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
          Text(
            text = "نسبت بدهی",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        val debtPct = (dashboardData.debtToIncomeRatio * 100).toInt()
        Text(
          text = "$debtPct%",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color =
            when {
              debtPct > 40 -> FinancialColors.ExpenseRed
              debtPct > 20 -> FinancialColors.WarningOrange
              else -> FinancialColors.InfoBlue
            }
        )
      }
    }
  }
}

@Composable
internal fun DebtorCreditorCards(dashboardData: DashboardData) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md),
    maxItemsInEachRow = 2
  ) {
    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
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
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(Dimens.IconMedium)
          )
          Box(
            modifier =
              Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(horizontal = SpacingTokens.sm, vertical = 2.dp)
          ) {
            Text(
              text = "بدهکاران",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold
            )
          }
        }
        Spacer(modifier = Modifier.height(SpacingTokens.lg))
        Text(
          text = CurrencyFormatter.format(dashboardData.debtorsTotal),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = FinancialColors.WarningOrange.copy(alpha = 0.15f)
        )
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
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
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(Dimens.IconMedium)
          )
          Box(
            modifier =
              Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(horizontal = SpacingTokens.sm, vertical = 2.dp)
          ) {
            Text(
              text = "طلبکاران",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold
            )
          }
        }
        Spacer(modifier = Modifier.height(SpacingTokens.lg))
        Text(
          text = CurrencyFormatter.format(dashboardData.creditorsTotal),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
internal fun SmartParsingBanner(onNavigateToAssistant: () -> Unit) {
  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onNavigateToAssistant() },
    shape = ShapeTokens.Large,
    cardColors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
      )
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
        Spacer(modifier = Modifier.width(SpacingTokens.md))
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
internal fun BankLoansSummaryCard(dashboardData: DashboardData) {
  if (dashboardData.bankLoans.isEmpty()) return
  HesabyarCard {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "بدهی وام‌های بانکی",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = CurrencyFormatter.format(dashboardData.bankLoansTotal),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = FinancialColors.ExpenseRed
        )
      }
      dashboardData.bankLoans.forEach { bl: BankLoanSummary ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "${bl.bankName} — ${bl.loanName}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = CurrencyFormatter.format(bl.remainingDebt),
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }
  }
}
