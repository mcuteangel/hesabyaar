package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.core.filterDigits
import io.github.mojri.hesabyar.core.toCleanLongOrNull
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

private const val DESCRIPTION_MAX_LENGTH = 500

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
  isSubmitting: Boolean = false,
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
  val title =
    when {
      personName.isNotBlank() && type == LoanType.DEBTOR ->
        stringResource(R.string.add_person_loan_debtor_title, personName.trim())
      personName.isNotBlank() ->
        stringResource(R.string.add_person_loan_creditor_title, personName.trim())
      type == LoanType.DEBTOR ->
        stringResource(R.string.add_person_loan_debtor_generic_title)
      else ->
        stringResource(R.string.add_person_loan_creditor_generic_title)
    }

  HesabyarDialog(
    title = title,
    onDismissRequest = { if (!isSubmitting) onDismiss() },
    confirmButton = {
      HesabyarButton(
        onClick = {
          if (isValidAmount && !isSubmitting) {
            onConfirm(CurrencyFormatter.toRial(parsedAmount), description)
          }
        },
        enabled = isValidAmount && !isSubmitting,
        text = stringResource(R.string.person_loan_submit),
        variant = ButtonVariant.Filled
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        enabled = !isSubmitting,
        text = stringResource(R.string.action_cancel),
        variant = ButtonVariant.Text
      )
    }
  ) {
    AddPersonLoanInputs(
      amountText = amountText,
      onAmountChange = { amountText = it },
      description = description,
      onDescriptionChange = { description = it },
      errorMessage = errorMessage
    )
  }
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
      onValueChange = { onAmountChange(it.filterDigits()) },
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
