package io.github.mojri.hesabyar.ui.screens

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.core.filterDigits
import io.github.mojri.hesabyar.core.filterDigitsPreservingLeadingMinus
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.ui.BankLoanViewModel
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.HesabyarFab
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.HesabyarScaffold
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.utils.DisplayAmountInput

@Composable
fun BankLoanScreen(
  bankLoanViewModel: BankLoanViewModel,
  modifier: Modifier = Modifier
) {
  val bankLoans by bankLoanViewModel.bankLoans.collectAsState()
  var showAddDialog by rememberSaveable { mutableStateOf(false) }

  HesabyarScaffold(
    modifier = modifier,
    floatingActionButton = {
      HesabyarFab(onClick = { showAddDialog = true }, contentDescription = "افزودن وام بانکی")
    }
  ) { contentPadding ->
    if (bankLoans.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center
      ) {
        Text("وام بانکی ثبت نشده است", style = MaterialTheme.typography.bodyLarge)
      }
    } else {
      LazyColumn(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(SpacingTokens.md),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
        contentPadding = contentPadding
      ) {
        items(bankLoans, key = { it.id }) { loan ->
          BankLoanItem(
            loan = loan,
            onToggleSettled = { bankLoanViewModel.toggleSettled(loan.id) },
            onDelete = { bankLoanViewModel.deleteBankLoan(loan) }
          )
        }
      }
    }
  }

  if (showAddDialog) {
    AddBankLoanDialog(
      onDismiss = { showAddDialog = false },
      onConfirm = { bankName, loanName, received, monthly, count, start, desc ->
        bankLoanViewModel.addBankLoan(bankName, loanName, received, monthly, count, start, desc)
        showAddDialog = false
      }
    )
  }
}

@Composable
private fun BankLoanItem(
  loan: BankLoan,
  onToggleSettled: () -> Unit,
  onDelete: () -> Unit
) {
  var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
  if (showDeleteConfirm) {
    ConfirmDialog(
      title = "حذف وام بانکی",
      message = "آیا از حذف این وام و اقساط تولید شده اطمینان دارید؟ این عمل قابل بازگشت نیست.",
      confirmText = stringResource(R.string.action_delete),
      dismissText = stringResource(R.string.action_cancel),
      onDismiss = { showDeleteConfirm = false },
      onConfirm = {
        showDeleteConfirm = false
        onDelete()
      }
    )
  }
  HesabyarCard {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${loan.bankName} — ${loan.loanName}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        if (loan.isSettled) {
          AssistChip(
            onClick = {},
            label = { Text("تسویه شده") },
            leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
          )
        }
      }
      bankLoanDetailRows(loan)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        HesabyarButton(
          onClick = onToggleSettled,
          text = if (loan.isSettled) "باز کردن" else "تسویه",
          variant = ButtonVariant.Outlined,
          modifier = Modifier.weight(1f)
        )
        HesabyarButton(
          onClick = { showDeleteConfirm = true },
          text = "حذف",
          variant = ButtonVariant.Outlined,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun bankLoanDetailRows(loan: BankLoan) {
  Text(
    "مبلغ دریافتی: ${CurrencyFormatter.format(loan.receivedAmount)}",
    style = MaterialTheme.typography.bodyMedium
  )
  Text(
    "قسط ماهانه: ${CurrencyFormatter.format(loan.monthlyInstallmentAmount)}",
    style = MaterialTheme.typography.bodyMedium
  )
  Text("تعداد اقساط: ${loan.numberOfInstallments}", style = MaterialTheme.typography.bodyMedium)
  Text(
    "مبلغ کل بازپرداخت: ${CurrencyFormatter.format(loan.totalRepayableAmount)}",
    style = MaterialTheme.typography.bodyMedium
  )
  Text(
    "سود کل: ${CurrencyFormatter.format(loan.totalInterest)}",
    style = MaterialTheme.typography.bodyMedium
  )
  if (loan.description.isNotBlank()) {
    Text("توضیحات: ${loan.description}", style = MaterialTheme.typography.bodySmall)
  }
}

internal class AddBankLoanFormState(
  val bankNameState: androidx.compose.runtime.MutableState<String>,
  val loanNameState: androidx.compose.runtime.MutableState<String>,
  val receivedState: androidx.compose.runtime.MutableState<String>,
  val monthlyState: androidx.compose.runtime.MutableState<String>,
  val countState: androidx.compose.runtime.MutableState<String>,
  val descriptionState: androidx.compose.runtime.MutableState<String>,
  val startDateState: androidx.compose.runtime.MutableState<Long>,
  val showDatePickerState: androidx.compose.runtime.MutableState<Boolean>
) {
  constructor(
    bankName: String = "",
    loanName: String = "",
    received: String = "",
    monthly: String = "",
    count: String = "",
    description: String = "",
    startDate: Long = System.currentTimeMillis(),
    showDatePicker: Boolean = false
  ) : this(
    bankNameState = mutableStateOf(bankName),
    loanNameState = mutableStateOf(loanName),
    receivedState = mutableStateOf(received),
    monthlyState = mutableStateOf(monthly),
    countState = mutableStateOf(count),
    descriptionState = mutableStateOf(description),
    startDateState = mutableStateOf(startDate),
    showDatePickerState = mutableStateOf(showDatePicker)
  )

  var bankName by bankNameState
  var loanName by loanNameState
  var received by receivedState
  var monthly by monthlyState
  var count by countState
  var description by descriptionState
  var startDate by startDateState
  var showDatePicker by showDatePickerState

  val countVal: Int
    get() = count.toIntOrNull() ?: 0

  val receivedRial: Long
    get() = DisplayAmountInput.toRialOrNull(received) ?: 0L

  val monthlyRial: Long
    get() = DisplayAmountInput.toRialOrNull(monthly) ?: 0L

  val canConfirm: Boolean
    get() = bankName.trim().isNotBlank() && receivedRial > 0 && monthlyRial > 0 && countVal > 0
}

@Composable
private fun rememberAddBankLoanFormState(initialStartDate: Long = System.currentTimeMillis()): AddBankLoanFormState {
  val bankNameState = rememberSaveable { mutableStateOf("") }
  val loanNameState = rememberSaveable { mutableStateOf("") }
  val receivedState = rememberSaveable { mutableStateOf("") }
  val monthlyState = rememberSaveable { mutableStateOf("") }
  val countState = rememberSaveable { mutableStateOf("") }
  val descriptionState = rememberSaveable { mutableStateOf("") }
  val startDateState = rememberSaveable { mutableStateOf(initialStartDate) }
  val showDatePickerState = rememberSaveable { mutableStateOf(false) }

  return androidx.compose.runtime.remember {
    AddBankLoanFormState(
      bankNameState = bankNameState,
      loanNameState = loanNameState,
      receivedState = receivedState,
      monthlyState = monthlyState,
      countState = countState,
      descriptionState = descriptionState,
      startDateState = startDateState,
      showDatePickerState = showDatePickerState
    )
  }
}

@Composable
private fun AddBankLoanDialog(
  onDismiss: () -> Unit,
  onConfirm: (
    bankName: String,
    loanName: String,
    receivedAmount: Long,
    monthlyInstallmentAmount: Long,
    numberOfInstallments: Int,
    startDate: Long,
    description: String
  ) -> Unit
) {
  val state = rememberAddBankLoanFormState()

  if (state.showDatePicker) {
    JalaliDatePickerDialog(
      initialTimestamp = state.startDate,
      onDismissRequest = { state.showDatePicker = false },
      onDateSelected = { state.startDate = it }
    )
  }

  val jDate = JalaliCalendarHelper.gregorianToJalali(state.startDate)

  HesabyarDialog(
    onDismissRequest = onDismiss,
    title = "ثبت وام بانکی",
    confirmButton = {
      HesabyarButton(
        onClick = {
          onConfirm(
            state.bankName.trim(),
            state.loanName.trim(),
            state.receivedRial,
            state.monthlyRial,
            state.countVal,
            state.startDate,
            state.description.trim()
          )
        },
        text = stringResource(R.string.action_save),
        variant = ButtonVariant.Filled,
        enabled = state.canConfirm
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        text = stringResource(R.string.action_cancel),
        variant = ButtonVariant.Text
      )
    }
  ) {
    BankLoanForm(
      bankName = state.bankName,
      onBankName = { state.bankName = it },
      loanName = state.loanName,
      onLoanName = { state.loanName = it },
      received = state.received,
      onReceived = { state.received = it },
      monthly = state.monthly,
      onMonthly = { state.monthly = it },
      count = state.count,
      onCount = { state.count = it },
      description = state.description,
      onDescription = { state.description = it },
      startDateLabel = "تاریخ شروع: ${jDate.year}/${jDate.month}/${jDate.day}",
      onPickDate = { state.showDatePicker = true }
    )
  }
}

@VisibleForTesting
@Composable
internal fun BankLoanForm(
  bankName: String,
  onBankName: (String) -> Unit,
  loanName: String,
  onLoanName: (String) -> Unit,
  received: String,
  onReceived: (String) -> Unit,
  monthly: String,
  onMonthly: (String) -> Unit,
  count: String,
  onCount: (String) -> Unit,
  description: String,
  onDescription: (String) -> Unit,
  startDateLabel: String,
  onPickDate: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    HesabyarInputField(value = bankName, onValueChange = onBankName, label = "نام بانک")
    HesabyarInputField(value = loanName, onValueChange = onLoanName, label = "نام وام")
    HesabyarInputField(
      value = received,
      onValueChange = { onReceived(it.filterDigitsPreservingLeadingMinus()) },
      label = stringResource(R.string.bank_loan_received_label, CurrencyFormatter.unitLabel),
      isError = isInvalidAmount(received),
      supportingText = invalidAmountText(received)?.let { text -> { Text(text) } },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    HesabyarInputField(
      value = monthly,
      onValueChange = { onMonthly(it.filterDigitsPreservingLeadingMinus()) },
      label = stringResource(R.string.bank_loan_monthly_label, CurrencyFormatter.unitLabel),
      isError = isInvalidAmount(monthly),
      supportingText = invalidAmountText(monthly)?.let { text -> { Text(text) } },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    HesabyarInputField(
      value = count,
      onValueChange = { onCount(it.filterDigits()) },
      label = "تعداد اقساط",
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    HesabyarInputField(value = description, onValueChange = onDescription, label = "توضیحات")
    HesabyarButton(
      onClick = onPickDate,
      text = startDateLabel,
      variant = ButtonVariant.Outlined
    )
  }
}

/** True when the user typed something that is not a usable loan amount (garbage, too large, zero, or negative). */
@VisibleForTesting
internal fun isInvalidAmount(text: String): Boolean {
  if (text.isBlank()) return false
  return DisplayAmountInput.toRialOrNull(text)?.let { it > 0L } != true
}

@Composable
private fun invalidAmountText(text: String): String? {
  if (!isInvalidAmount(text)) return null
  return stringResource(R.string.balance_invalid_amount)
}
