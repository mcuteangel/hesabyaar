package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import io.github.mojri.hesabyar.ui.UiResult
import io.github.mojri.hesabyar.ui.components.AiResultContent
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.MarkdownText

@Composable
internal fun ForecastDetailDialog(
  forecastState: UiResult<String>,
  onDismiss: () -> Unit,
  onRefresh: () -> Unit
) {
  HesabyarDialog(
    title = "پیش‌بینی وضعیت بودجه ماه آینده",
    onDismissRequest = onDismiss,
    widthFraction = 0.95f,
    heightFraction = 0.85f,
    leadingIcon = Icons.Filled.AutoAwesome,
    showCloseButton = true
  ) {
    AiResultContent(
      state = forecastState,
      loadingText = "در حال تحلیل و پیش‌بینی وضعیت بودجه...",
      errorTitle = "خطا در دریافت پیش‌بینی",
      idleActionText = "دریافت پیش‌بینی",
      onFetch = onRefresh,
      onRefresh = onRefresh,
      onRetry = onRefresh
    ) { forecastText ->
      MarkdownText(text = forecastText)
    }
  }
}
