package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.utils.DisplayAmountInput

private const val DESCRIPTION_MAX_LENGTH = 500

/**
 * Dialog for adding a DEBTOR or CREDITOR loan linked to a person (plans/011 Phase 3).
 *
 * Rejects amounts flagged by overflowsTomanConversion, so the
 * display-to-Rial conversion cannot overflow.
 */
@Composable
internal fun AddPersonLoanDialog(
  type: LoanType,
  personName: String,
  onConfirm: (amountRial: Long, description: String) -> Unit,
  onDismiss: () -> Unit,
  errorMessage: String? = null,
  isSubmitting: Boolean = false,
) {
  var amountText by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  val parsedAmount = amountText.toCleanLongOrNull() ?: 0L
  val isValidAmount = parsedAmount >= 1L && !overflowsTomanConversion(parsedAmount)
  val typeLabel = if (type == LoanType.DEBTOR) "طلب از" else "بدهی به"

  AlertDialog(
    onDismissRequest = { if (!isSubmitting) onDismiss() },
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
        onAmountChange = { amountText = it.filterDigits() },
        description = description,
        onDescriptionChange = { description = it },
        errorMessage = errorMessage
      )
    },
    confirmButton = {
      HesabyarButton(
        onClick = {
          if (isValidAmount && !isSubmitting) {
            onConfirm(CurrencyFormatter.toRial(parsedAmount), description)
          }
        },
        enabled = isValidAmount && !isSubmitting,
        text = "ثبت"
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        enabled = !isSubmitting,
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
    HesabyarInputField(
      value = amountText,
      onValueChange = onAmountChange,
      label = "مبلغ (${CurrencyFormatter.unitLabel})",
      singleLine = true
    )
    HesabyarInputField(
      value = description,
      onValueChange = { onDescriptionChange(it.take(DESCRIPTION_MAX_LENGTH)) },
      label = "توضیحات (اختیاری)",
      supportingText = {
        Text(
          text = "${description.length}/$DESCRIPTION_MAX_LENGTH",
          style = MaterialTheme.typography.labelSmall
        )
      },
      singleLine = false
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

private fun overflowsTomanConversion(displayValue: Long): Boolean = displayValue > DisplayAmountInput.maxDisplayAmount()
