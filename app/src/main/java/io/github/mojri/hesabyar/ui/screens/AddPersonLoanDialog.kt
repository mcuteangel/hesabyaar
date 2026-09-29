package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import io.github.mojri.hesabyar.core.filterDigits
import io.github.mojri.hesabyar.core.toCleanLongOrNull
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Dialog for adding a DEBTOR or CREDITOR loan linked to a person (plans/011 Phase 3).
 *
 * Enforces valid numeric input capped at [Long.MAX_VALUE] in Rial (or
 * `Long.MAX_VALUE / 10` in Toman) so multiplication does not overflow.
 */
@Composable
internal fun AddPersonLoanDialog(
  type: LoanType,
  personName: String,
  onConfirm: (amountRial: Long, description: String) -> Unit,
  onDismiss: () -> Unit,
  errorMessage: String? = null,
) {
  var amountText by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  val maxAllowedDisplay =
    if (CurrencyFormatter.currentUnit == CurrencyUnit.TOMAN) {
      Long.MAX_VALUE / 10L
    } else {
      Long.MAX_VALUE
    }
  val parsedAmount = amountText.toCleanLongOrNull() ?: 0L
  val isValidAmount = parsedAmount in 1L..maxAllowedDisplay
  val typeLabel = if (type == LoanType.DEBTOR) "طلب از" else "بدهی به"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "ثبت $typeLabel $personName",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      AddPersonLoanInputs(
        amountText = amountText,
        onAmountChange = { amountText = it },
        description = description,
        onDescriptionChange = { description = it },
        errorMessage = errorMessage
      )
    },
    confirmButton = {
      HesabyarButton(
        onClick = {
          if (isValidAmount) {
            onConfirm(CurrencyFormatter.toRial(parsedAmount), description)
          }
        },
        enabled = isValidAmount,
        text = "ثبت"
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        text = "انصراف",
        variant = ButtonVariant.Text
      )
    }
  )
}

@Composable
private fun AddPersonLoanInputs(
  amountText: String,
  onAmountChange: (String) -> Unit,
  description: String,
  onDescriptionChange: (String) -> Unit,
  errorMessage: String?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    OutlinedTextField(
      value = amountText,
      onValueChange = { onAmountChange(it.filterDigits()) },
      label = { Text("مبلغ (${CurrencyFormatter.unitLabel})") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true
    )
    OutlinedTextField(
      value = description,
      onValueChange = onDescriptionChange,
      label = { Text("توضیحات (اختیاری)") },
      modifier = Modifier.fillMaxWidth()
    )
    if (errorMessage != null) {
      Text(
        text = errorMessage,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}
