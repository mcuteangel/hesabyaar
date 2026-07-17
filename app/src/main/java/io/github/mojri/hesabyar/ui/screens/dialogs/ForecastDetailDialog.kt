package io.github.mojri.hesabyar.ui.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.ui.ForecastUIState
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.MarkdownText

@Composable
fun ForecastDetailDialog(
  forecastState: ForecastUIState,
  onDismiss: () -> Unit,
  onRefresh: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties =
      androidx.compose.ui.window
        .DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier =
        Modifier
          .fillMaxWidth(0.95f)
          .fillMaxHeight(0.85f),
      shape = ShapeTokens.XLarge,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = ElevationTokens.lg
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(SpacingTokens.xl)
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
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "پیش‌بینی وضعیت بودجه ماه آینده",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "بستن"
            )
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = SpacingTokens.sm))

        when (val state = forecastState) {
          is ForecastUIState.Loading -> {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(Dimens.IconLarge),
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "در حال تحلیل و پیش‌بینی وضعیت بودجه...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
              }
            }
          }
          is ForecastUIState.Error -> {
            Column(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "⚠️ خطا در دریافت پیش‌بینی",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
              )
              Spacer(modifier = Modifier.height(SpacingTokens.sm))
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
              )
              Spacer(modifier = Modifier.height(SpacingTokens.lg))
              Button(onClick = onRefresh) {
                Text("تلاش مجدد")
              }
            }
          }
          is ForecastUIState.Success -> {
            Column(modifier = Modifier.fillMaxSize()) {
              Column(
                modifier =
                  Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
              ) {
                MarkdownText(text = state.forecast)
              }
              Spacer(modifier = Modifier.height(SpacingTokens.md))
              Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.Medium
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(Dimens.IconSmall)
                )
                Spacer(modifier = Modifier.width(SpacingTokens.sm))
                Text("بروزرسانی پیش‌بینی", fontWeight = FontWeight.Bold)
              }
            }
          }
          is ForecastUIState.Idle -> {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              androidx.compose.material3.OutlinedButton(onClick = onRefresh) {
                Text("دریافت پیش‌بینی")
              }
            }
          }
        }
      }
    }
  }
}
