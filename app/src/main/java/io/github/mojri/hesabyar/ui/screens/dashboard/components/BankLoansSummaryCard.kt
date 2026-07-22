package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.DashboardData
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

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
      dashboardData.bankLoans.forEach { bl ->
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
