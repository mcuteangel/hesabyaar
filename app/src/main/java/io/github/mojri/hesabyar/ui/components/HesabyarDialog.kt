package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Standardized full-featured dialog scaffold built on [Dialog] + [Surface].
 *
 * Encapsulates the boilerplate that was repeated across the custom dialogs in the
 * project ([io.github.mojri.hesabyar.ui.screens.DashboardScreen.ForecastDetailDialog],
 * [io.github.mojri.hesabyar.ui.screens.DashboardScreen.ManualTransactionDialog],
 * [io.github.mojri.hesabyar.ui.screens.JalaliDatePickerDialog],
 * [io.github.mojri.hesabyar.ui.screens.CustomTimePickerDialog] and
 * [io.github.mojri.hesabyar.ui.screens.SettingsScreen.AiConfigDialog]):
 * a title bar with an optional leading icon and close button, a divider, a
 * scrollable body and a bottom action row.
 *
 * @param title Dialog heading shown in the header bar.
 * @param onDismissRequest Called when the dialog is dismissed (back press, scrim).
 * @param modifier Modifier applied to the inner [Surface].
 * @param widthFraction Fraction of the screen width the dialog occupies.
 * @param heightFraction Optional fraction of the screen height; when null the
 *   dialog sizes to its content and the body is not forced to fill height.
 * @param showCloseButton Whether to render the close [IconButton] in the header.
 * @param leadingIcon Optional icon rendered before the title in the header.
 * @param confirmButton Structured confirm button slot placed at the trailing edge.
 * @param dismissButton Structured dismiss button slot placed adjacent to confirm.
 * @param actions Optional fallback composable rendered as a bottom row when structured slots are not used.
 * @param content Scrollable body content of the dialog.
 */
@Composable
fun HesabyarDialog(
  title: String,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  widthFraction: Float = 0.92f,
  heightFraction: Float? = null,
  isFullScreen: Boolean = false,
  showCloseButton: Boolean = false,
  leadingIcon: ImageVector? = null,
  confirmText: String? = null,
  onConfirm: (() -> Unit)? = null,
  confirmEnabled: Boolean = true,
  confirmLoading: Boolean = false,
  confirmColor: Color? = null,
  dismissText: String? = null,
  onDismiss: (() -> Unit)? = null,
  confirmButton: (@Composable () -> Unit)? = null,
  dismissButton: (@Composable () -> Unit)? = null,
  actions: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier =
        modifier
          .fillMaxWidth(widthFraction)
          .then(
            if (heightFraction != null) {
              Modifier.fillMaxHeight(heightFraction)
            } else {
              Modifier
            }
          ),
      shape = ShapeTokens.XLarge,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = ElevationTokens.Level3
    ) {
      Column(
        modifier = Modifier.padding(SpacingTokens.xl)
      ) {
        HesabyarDialogHeader(
          title = title,
          leadingIcon = leadingIcon,
          showCloseButton = showCloseButton,
          isFullScreen = isFullScreen,
          onDismissRequest = onDismissRequest
        )

        HorizontalDivider(
          color = MaterialTheme.colorScheme.surfaceContainer
        )

        Column(
          modifier =
            Modifier
              .weight(1f, fill = false)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(vertical = SpacingTokens.lg),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
        ) {
          content()
        }

        HesabyarDialogActions(
          confirmText = confirmText,
          onConfirm = onConfirm,
          confirmEnabled = confirmEnabled,
          confirmLoading = confirmLoading,
          confirmColor = confirmColor,
          dismissText = dismissText,
          onDismiss = onDismiss,
          confirmButton = confirmButton,
          dismissButton = dismissButton,
          actions = actions
        )
      }
    }
  }
}

@Composable
private fun HesabyarDialogHeader(
  title: String,
  leadingIcon: ImageVector?,
  showCloseButton: Boolean,
  isFullScreen: Boolean,
  onDismissRequest: () -> Unit
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
      if (leadingIcon != null) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )
    }
    // Per M3 guidelines, close icons belong on bottom sheets/full-screen surfaces,
    // not on standard alert/confirm dialogs.
    if (showCloseButton && isFullScreen) {
      IconButton(onClick = onDismissRequest) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = stringResource(R.string.action_close),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun HesabyarDialogActions(
  confirmText: String?,
  onConfirm: (() -> Unit)?,
  confirmEnabled: Boolean,
  confirmLoading: Boolean,
  confirmColor: Color?,
  dismissText: String?,
  onDismiss: (() -> Unit)?,
  confirmButton: (@Composable () -> Unit)?,
  dismissButton: (@Composable () -> Unit)?,
  actions: (@Composable RowScope.() -> Unit)?
) {
  val resolvedConfirm: (@Composable () -> Unit)? =
    when {
      confirmText != null && onConfirm != null -> {
        {
          HesabyarButton(
            onClick = onConfirm,
            text = confirmText,
            variant = ButtonVariant.Filled,
            enabled = confirmEnabled,
            loading = confirmLoading,
            colors = confirmColor?.let { ButtonDefaults.buttonColors(containerColor = it) }
          )
        }
      }
      else -> confirmButton
    }

  val resolvedDismiss: (@Composable () -> Unit)? =
    when {
      dismissText != null && onDismiss != null -> {
        {
          HesabyarButton(
            onClick = onDismiss,
            text = dismissText,
            variant = ButtonVariant.Text
          )
        }
      }
      else -> dismissButton
    }

  if (resolvedConfirm != null || resolvedDismiss != null) {
    Spacer(modifier = Modifier.size(SpacingTokens.sm))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (resolvedDismiss != null) {
        resolvedDismiss()
        if (resolvedConfirm != null) {
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
        }
      }
      if (resolvedConfirm != null) {
        resolvedConfirm()
      }
    }
  } else if (actions != null) {
    Spacer(modifier = Modifier.size(SpacingTokens.sm))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      actions()
    }
  }
}
