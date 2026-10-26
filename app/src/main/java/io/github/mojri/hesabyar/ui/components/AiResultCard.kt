package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.ui.UiResult
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Standard card container for displaying asynchronous AI operation results [UiResult].
 *
 * Handles [UiResult.Idle], [UiResult.Loading], [UiResult.Error], and [UiResult.Success]
 * states with Material 3 design tokens and theme-aware styling.
 */
@Composable
fun <T> AiResultCard(
  state: UiResult<T>,
  modifier: Modifier = Modifier,
  title: String? = null,
  subtitle: String? = null,
  icon: ImageVector = Icons.Filled.AutoAwesome,
  onRetry: (() -> Unit)? = null,
  onRefresh: (() -> Unit)? = null,
  onClose: (() -> Unit)? = null,
  onFetch: (() -> Unit)? = null,
  idleDescription: String? = null,
  idleActionText: String? = null,
  loadingText: String? = null,
  errorTitle: String? = null,
  idleContent: (@Composable () -> Unit)? = null,
  loadingContent: (@Composable () -> Unit)? = null,
  errorContent: (@Composable (String) -> Unit)? = null,
  content: @Composable (data: T) -> Unit
) {
  HesabyarCard(
    modifier = modifier.fillMaxWidth(),
    shape = ShapeTokens.XLarge,
    contentPadding = PaddingValues(SpacingTokens.lg)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      if (title != null) {
        AiCardHeader(
          title = title,
          subtitle = subtitle,
          icon = icon
        )
      }

      AiResultContent(
        state = state,
        onRetry = onRetry,
        onRefresh = onRefresh,
        onClose = onClose,
        onFetch = onFetch,
        idleDescription = idleDescription,
        idleActionText = idleActionText,
        loadingText = loadingText,
        errorTitle = errorTitle,
        idleContent = idleContent,
        loadingContent = loadingContent,
        errorContent = errorContent,
        content = content
      )
    }
  }
}

/**
 * Embeddable content component for rendering [UiResult] states without an outer card container.
 * Useful for dialogs (such as [ForecastDetailDialog]) or custom sheets.
 */
@Composable
fun <T> AiResultContent(
  state: UiResult<T>,
  modifier: Modifier = Modifier,
  onRetry: (() -> Unit)? = null,
  onRefresh: (() -> Unit)? = null,
  onClose: (() -> Unit)? = null,
  onFetch: (() -> Unit)? = null,
  idleDescription: String? = null,
  idleActionText: String? = null,
  loadingText: String? = null,
  errorTitle: String? = null,
  idleContent: (@Composable () -> Unit)? = null,
  loadingContent: (@Composable () -> Unit)? = null,
  errorContent: (@Composable (String) -> Unit)? = null,
  content: @Composable (data: T) -> Unit
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    when (state) {
      is UiResult.Idle -> {
        if (idleContent != null) {
          idleContent()
        } else {
          AiResultIdleContent(
            description = idleDescription,
            actionText = idleActionText,
            onFetch = onFetch
          )
        }
      }
      is UiResult.Loading -> {
        if (loadingContent != null) {
          loadingContent()
        } else {
          AiResultLoadingContent(loadingText = loadingText)
        }
      }
      is UiResult.Error -> {
        if (errorContent != null) {
          errorContent(state.message)
        } else {
          AiResultErrorContent(
            errorTitle = errorTitle,
            message = state.message,
            onRetry = onRetry
          )
        }
      }
      is UiResult.Success -> {
        content(state.data)

        if (onRefresh != null || onClose != null) {
          AiResultSuccessActions(
            onRefresh = onRefresh,
            onClose = onClose
          )
        }
      }
    }
  }
}

@Composable
private fun AiCardHeader(
  title: String,
  subtitle: String?,
  icon: ImageVector
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    modifier = Modifier.fillMaxWidth()
  ) {
    IconCircle(
      icon = icon,
      tint = MaterialTheme.colorScheme.primary,
      backgroundColor = MaterialTheme.colorScheme.primary,
      contentDescription = null
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() }
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun AiResultIdleContent(
  description: String?,
  actionText: String?,
  onFetch: (() -> Unit)?
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    horizontalAlignment = Alignment.Start,
    modifier = Modifier.fillMaxWidth()
  ) {
    if (description != null) {
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 18.sp
      )
    }
    if (onFetch != null) {
      HesabyarButton(
        onClick = onFetch,
        modifier = Modifier.fillMaxWidth().testTag("ai_result_fetch_button"),
        text = actionText ?: "تحلیل هوشمند و دریافت نتیجه",
        icon = Icons.Filled.AutoAwesome,
        variant = ButtonVariant.Filled
      )
    }
  }
}

@Composable
private fun AiResultLoadingContent(loadingText: String?) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = SpacingTokens.md)
        .testTag("ai_result_loading"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(Dimens.IconLarge),
      color = MaterialTheme.colorScheme.primary,
      strokeWidth = 2.5.dp
    )
    Text(
      text = loadingText ?: "درحال تحلیل و پردازش اطلاعات...",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.primary,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun AiResultErrorContent(
  errorTitle: String?,
  message: String,
  onRetry: (() -> Unit)?
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = SpacingTokens.xs)
        .testTag("ai_result_error"),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (errorTitle != null) {
      Text(
        text = errorTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      Icon(
        imageVector = Icons.Filled.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(Dimens.IconMenu)
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center
      )
    }
    if (onRetry != null) {
      Spacer(modifier = Modifier.height(SpacingTokens.xs))
      HesabyarButton(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().testTag("ai_result_retry_button"),
        text = "تلاش مجدد",
        icon = Icons.Filled.Refresh,
        variant = ButtonVariant.Outlined
      )
    }
  }
}

@Composable
private fun AiResultSuccessActions(
  onRefresh: (() -> Unit)?,
  onClose: (() -> Unit)?
) {
  if (onRefresh != null && onClose != null) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      HesabyarButton(
        onClick = onRefresh,
        modifier = Modifier.fillMaxWidth().testTag("ai_result_refresh_button"),
        text = "بروزرسانی تحلیل",
        icon = Icons.Filled.Refresh,
        variant = ButtonVariant.Outlined
      )
      HesabyarButton(
        onClick = onClose,
        modifier = Modifier.fillMaxWidth().testTag("ai_result_close_button"),
        text = "بستن",
        variant = ButtonVariant.Outlined
      )
    }
  } else if (onRefresh != null) {
    HesabyarButton(
      onClick = onRefresh,
      modifier = Modifier.fillMaxWidth().testTag("ai_result_refresh_button"),
      text = "بروزرسانی تحلیل",
      icon = Icons.Filled.Refresh,
      variant = ButtonVariant.Outlined
    )
  } else if (onClose != null) {
    HesabyarButton(
      onClick = onClose,
      modifier = Modifier.fillMaxWidth().testTag("ai_result_close_button"),
      text = "بستن",
      variant = ButtonVariant.Outlined
    )
  }
}
