package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.ForecastUIState
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.dashboard.utils.extractForecastPreview

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
      IconCircle(
        icon = Icons.Filled.AutoAwesome,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        iconSize = 18.dp
      )
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
