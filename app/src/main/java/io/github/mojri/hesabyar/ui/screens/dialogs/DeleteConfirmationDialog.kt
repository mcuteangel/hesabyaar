package io.github.mojri.hesabyar.ui.screens.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors

@Composable
fun DeleteConfirmationDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("حذف تراکنش") },
    text = { Text("آیا از حذف این تراکنش اطمینان دارید؟") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("حذف", color = FinancialColors.ExpenseRed)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("لغو")
      }
    }
  )
}
