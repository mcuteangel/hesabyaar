package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.DashboardData
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
internal fun KpiCards(dashboardData: DashboardData) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md),
    maxItemsInEachRow = 2
  ) {
    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconCircle(
            icon = Icons.Filled.Savings,
            tint = FinancialColors.IncomeGreen,
            backgroundColor = FinancialColors.IncomeGreen,
            containerSize = 28.dp
          )
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
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconCircle(
            icon = Icons.Filled.AccountBalance,
            tint = FinancialColors.InfoBlue,
            backgroundColor = FinancialColors.InfoBlue,
            containerSize = 28.dp
          )
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
