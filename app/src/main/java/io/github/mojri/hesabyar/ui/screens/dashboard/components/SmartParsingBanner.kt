package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

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
        IconCircle(
          icon = Icons.Filled.AutoAwesome,
          tint = MaterialTheme.colorScheme.onPrimary,
          backgroundColor = MaterialTheme.colorScheme.primary,
          backgroundAlpha = 1.0f,
          iconSize = 20.dp,
          containerSize = 40.dp
        )
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
