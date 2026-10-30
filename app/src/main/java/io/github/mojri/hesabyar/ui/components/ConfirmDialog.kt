package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.mojri.hesabyar.R

/**
 * Standardized confirmation / delete dialog used across the app for
 * "are you sure?" prompts (delete transaction, delete loan, delete category, etc.).
 *
 * Implemented as a thin wrapper over [HesabyarDialog] to enforce the single
 * dialog policy.
 */
@Composable
fun ConfirmDialog(
  title: String,
  message: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  confirmText: String =
    androidx.compose.ui.res
      .stringResource(R.string.action_confirm),
  dismissText: String =
    androidx.compose.ui.res
      .stringResource(R.string.action_cancel),
  confirmColor: Color = MaterialTheme.colorScheme.error
) {
  HesabyarDialog(
    title = title,
    onDismissRequest = onDismiss,
    modifier = modifier,
    confirmText = confirmText,
    onConfirm = onConfirm,
    confirmColor = confirmColor,
    dismissText = if (dismissText.isNotBlank()) dismissText else null,
    onDismiss = if (dismissText.isNotBlank()) onDismiss else null,
    content = {
      androidx.compose.material3.Text(text = message)
    }
  )
}
