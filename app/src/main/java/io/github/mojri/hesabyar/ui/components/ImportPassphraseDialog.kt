package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

internal const val CANCEL_LABEL = "انصراف"

/**
 * Passphrase entry dialog shown when an imported backup contains encrypted
 * fields. Passphrase and edit-state are local to this composable: after a
 * failed decrypt attempt the inline error stays visible until the user starts
 * editing the passphrase again, while a resubmission always surfaces the
 * fresh attempt's outcome instead of hiding it behind the stale edit flag.
 */
@Composable
fun ImportPassphraseDialog(
  errorMessage: String?,
  isCryptoInProgress: Boolean,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var passphrase by remember { mutableStateOf("") }
  // Suppresses the inline error while the user edits the passphrase after a
  // failed decrypt attempt. Reset on resubmit so a freshly-arrived error from
  // the ViewModel is not hidden by the stale edit flag.
  var userEditedSinceError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("رمزگشایی پشتیبان", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Text(
          text = "این پشتیبان رمزگذاری شده است. لطفاً رمز عبور را وارد کنید:",
          style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
          value = passphrase,
          onValueChange = {
            passphrase = it
            userEditedSinceError = true
          },
          label = { Text("رمز عبور") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          isError = errorMessage != null && !userEditedSinceError,
          modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage != null && !userEditedSinceError) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }
        if (isCryptoInProgress) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }
    },
    confirmButton = {
      HesabyarButton(
        onClick = {
          // A resubmission starts a fresh attempt, so show its outcome rather
          // than suppressing a new error with the stale edit flag.
          userEditedSinceError = false
          onConfirm(passphrase)
        },
        text = "رمزگشایی",
        enabled = passphrase.isNotEmpty() && !isCryptoInProgress
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        text = CANCEL_LABEL,
        variant = ButtonVariant.Text
      )
    }
  )
}
