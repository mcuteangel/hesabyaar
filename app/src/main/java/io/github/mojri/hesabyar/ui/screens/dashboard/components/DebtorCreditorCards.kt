package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.DashboardData
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
internal fun DebtorCreditorCards(dashboardData: DashboardData) {
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
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = SpacingTokens.sm, vertical = 2.dp)
          ) {
            Text(
              text = "بدهکاران",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold
            )
          }
        }
        Spacer(modifier = Modifier.height(SpacingTokens.lg))
        Text(
          text = CurrencyFormatter.format(dashboardData.debtorsTotal),
          style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
          fontWeight = FontWeight.ExtraBold,
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
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = SpacingTokens.sm, vertical = 2.dp)
          ) {
            Text(
              text = "طلبکاران",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold
            )
          }
        }
        Spacer(modifier = Modifier.height(SpacingTokens.lg))
        Text(
          text = CurrencyFormatter.format(dashboardData.creditorsTotal),
          style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
