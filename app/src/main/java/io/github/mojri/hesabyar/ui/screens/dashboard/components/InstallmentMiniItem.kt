package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.dashboard.utils.formatPersianDate

@Composable
internal fun InstallmentMiniItem(
  installment: Installment,
  onTogglePaid: () -> Unit
) {
  HesabyarCard(
    modifier = Modifier.fillMaxWidth(),
    shape = ShapeTokens.Large,
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
        IconCircle(
          icon = Icons.Filled.DateRange,
          tint = FinancialColors.WarningOrange,
          backgroundColor = FinancialColors.WarningOrange,
          iconSize = 18.dp
        )
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
        shape = ShapeTokens.Small,
        contentPadding = PaddingValues(horizontal = SpacingTokens.md, vertical = 2.dp)
      ) {
        Text("پرداخت", style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}
