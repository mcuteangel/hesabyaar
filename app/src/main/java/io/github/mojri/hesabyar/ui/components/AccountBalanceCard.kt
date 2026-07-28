package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.AccountDashboardSummary
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun AccountBalanceCard(
  summary: AccountDashboardSummary,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val accentColor = Color(summary.accountColor.toULong())

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .background(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = ShapeTokens.Medium,
        ).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    ) {
      // Accent bar at top
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(accentColor),
      )

      // Card content
      Row(
        modifier = Modifier.padding(SpacingTokens.md),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
        ) {
          Text(
            text = summary.accountName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Text(
            text = summary.accountType.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Text(
          text = CurrencyFormatter.format(summary.balance),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
