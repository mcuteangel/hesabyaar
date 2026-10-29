package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.SubmitManualTransactionUseCase
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.AccountSelector
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.JalaliDateTimePicker
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualTransactionDialog(
  onSubmit: suspend (
    SubmitManualTransactionUseCase.SubmitManualTransactionRequest
  ) -> SubmitManualTransactionUseCase.SubmitResult,
  categories: List<Category>,
  transactionToEdit: Transaction? = null,
  onDismiss: () -> Unit,
  accounts: List<AccountEntity> = emptyList(),
  selectedAccountId: Long? = null,
  onAccountSelected: (Long?) -> Unit = {}
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val formState = rememberManualTransactionFormState(transactionToEdit, categories, selectedAccountId)
  val typeColor = resolveDialogTypeColor(formState.selectedType)

  HesabyarDialog(
    title = if (formState.isEditMode) "ویرایش تراکنش" else "ثبت دستی تراکنش جدید",
    onDismissRequest = { if (!formState.isSubmitting) onDismiss() },
    widthFraction = 0.92f,
    actions = {
      HesabyarButton(
        onClick = onDismiss,
        modifier = Modifier.weight(1f),
        text = stringResource(R.string.action_cancel),
        variant = ButtonVariant.Text,
        enabled = !formState.isSubmitting
      )

      HesabyarButton(
        onClick = {
          submitManualTransaction(
            formState = formState,
            context = context,
            coroutineScope = coroutineScope,
            onSubmit = onSubmit,
            onDismiss = onDismiss
          )
        },
        modifier = Modifier.weight(1f),
        text =
          if (formState.isEditMode) {
            stringResource(R.string.transaction_save_changes)
          } else {
            stringResource(R.string.transaction_submit)
          },
        variant = ButtonVariant.Filled,
        colors = ButtonDefaults.buttonColors(containerColor = typeColor),
        enabled = !formState.isSubmitting,
        loading = formState.isSubmitting
      )
    }
  ) {
    ManualTransactionFormBody(
      formState = formState,
      accounts = accounts,
      typeColor = typeColor,
      onAccountSelected = onAccountSelected
    )
  }
}

@Composable
internal fun rememberManualTransactionFormState(
  transactionToEdit: Transaction?,
  categories: List<Category>,
  selectedAccountId: Long?
): ManualTransactionFormState {
  val isEditMode = transactionToEdit != null
  val selectedType =
    rememberSaveable {
      mutableStateOf(transactionToEdit?.type?.name ?: TransactionType.EXPENSE.name)
    }
  val destinationAccountId =
    rememberSaveable {
      mutableStateOf(
        if (isEditMode && transactionToEdit.type == TransactionType.TRANSFER) {
          transactionToEdit.destinationAccountId
        } else {
          null
        }
      )
    }
  val sourceAccountId =
    rememberSaveable {
      mutableStateOf(selectedAccountId ?: transactionToEdit?.accountId)
    }
  val originalAmountRial = remember { transactionToEdit?.amount ?: 0L }
  val amountValue =
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
      mutableStateOf(
        TextFieldValue(
          if (isEditMode) CurrencyFormatter.fromRial(transactionToEdit.amount).toString() else ""
        )
      )
    }
  val amountModified = rememberSaveable { mutableStateOf(false) }
  val descriptionText = rememberSaveable { mutableStateOf(transactionToEdit?.description.orEmpty()) }
  val selectedCategoryId = rememberSaveable { mutableStateOf(transactionToEdit?.categoryId ?: 0L) }
  val personNameText = rememberSaveable { mutableStateOf(transactionToEdit?.personName ?: "") }
  val titleText = rememberSaveable { mutableStateOf(transactionToEdit?.description ?: "") }
  val daysFromNowText = rememberSaveable { mutableStateOf("30") }
  val customDate = rememberSaveable { mutableStateOf(transactionToEdit?.date ?: System.currentTimeMillis()) }
  val isSubmitting = remember { mutableStateOf(false) }

  return remember(transactionToEdit, categories, selectedAccountId) {
    ManualTransactionFormState(
      transactionToEdit = transactionToEdit,
      categories = categories,
      originalAmountRial = originalAmountRial,
      selectedTypeState = selectedType,
      destinationAccountIdState = destinationAccountId,
      sourceAccountIdState = sourceAccountId,
      amountValueState = amountValue,
      amountModifiedState = amountModified,
      descriptionTextState = descriptionText,
      selectedCategoryIdState = selectedCategoryId,
      personNameTextState = personNameText,
      titleTextState = titleText,
      daysFromNowTextState = daysFromNowText,
      customDateState = customDate,
      isSubmittingState = isSubmitting
    )
  }
}

internal class ManualTransactionFormState(
  val transactionToEdit: Transaction?,
  val categories: List<Category>,
  val originalAmountRial: Long,
  val selectedTypeState: MutableState<String>,
  val destinationAccountIdState: MutableState<Long?>,
  val sourceAccountIdState: MutableState<Long?>,
  val amountValueState: MutableState<TextFieldValue>,
  val amountModifiedState: MutableState<Boolean>,
  val descriptionTextState: MutableState<String>,
  val selectedCategoryIdState: MutableState<Long>,
  val personNameTextState: MutableState<String>,
  val titleTextState: MutableState<String>,
  val daysFromNowTextState: MutableState<String>,
  val customDateState: MutableState<Long>,
  val isSubmittingState: MutableState<Boolean>
) {
  val isEditMode: Boolean get() = transactionToEdit != null
  var selectedType: String by selectedTypeState
  var destinationAccountId: Long? by destinationAccountIdState
  var sourceAccountId: Long? by sourceAccountIdState
  var amountValue: TextFieldValue by amountValueState
  var amountModified: Boolean by amountModifiedState
  var descriptionText: String by descriptionTextState
  var selectedCategoryId: Long by selectedCategoryIdState
  var personNameText: String by personNameTextState
  var titleText: String by titleTextState
  var daysFromNowText: String by daysFromNowTextState
  var customDate: Long by customDateState
  var isSubmitting: Boolean by isSubmittingState

  fun calculateFinalAmountRial(): Long {
    val finalAmountDisplay = amountValue.text.toLongOrNull() ?: 0L
    return if (isEditMode && !amountModified) {
      originalAmountRial
    } else {
      CurrencyFormatter.toRial(finalAmountDisplay)
    }
  }

  fun validateAccounts(): Int? =
    when {
      sourceAccountId == null -> R.string.select_source_account
      selectedType == "TRANSFER" && destinationAccountId == null -> R.string.select_destination_account
      selectedType == "TRANSFER" && destinationAccountId == sourceAccountId ->
        R.string.source_dest_accounts_cannot_be_same
      else -> null
    }

  fun buildRequest(): SubmitManualTransactionUseCase.SubmitManualTransactionRequest {
    val finalAmountDisplay = amountValue.text.toLongOrNull() ?: 0L
    val accountId =
      requireNotNull(sourceAccountId) { "sourceAccountId must be validated before building request" }
    return SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
      amountDisplay = finalAmountDisplay,
      selectedType = selectedType,
      selectedCategoryId = selectedCategoryId,
      descriptionText = descriptionText,
      personName = personNameText,
      title = titleText,
      daysFromNowText = daysFromNowText,
      amountRial = calculateFinalAmountRial(),
      customDate = customDate,
      categories = categories,
      transactionToEdit = transactionToEdit,
      accountId = accountId,
      destinationAccountId = if (selectedType == "TRANSFER") destinationAccountId else null
    )
  }
}

@Suppress(
  // Coroutine safety net rethrowing CancellationException for structured concurrency
  "TooGenericExceptionCaught"
)
private fun submitManualTransaction(
  formState: ManualTransactionFormState,
  context: Context,
  coroutineScope: CoroutineScope,
  onSubmit: suspend (
    SubmitManualTransactionUseCase.SubmitManualTransactionRequest
  ) -> SubmitManualTransactionUseCase.SubmitResult,
  onDismiss: () -> Unit
) {
  if (formState.isSubmitting) return
  formState.isSubmitting = true
  coroutineScope.launch {
    try {
      val validationError = formState.validateAccounts()
      if (validationError != null) {
        showToast(context, context.getString(validationError))
        return@launch
      }

      val request = formState.buildRequest()
      val submitResult = onSubmit(request)

      if (submitResult.success) {
        onDismiss()
      } else {
        submitResult.errorMessage?.let { showToast(context, it) }
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AppLogger.e("ManualTransactionDialog", "Error submitting transaction", e)
      showToast(context, context.getString(R.string.error_unknown))
    } finally {
      formState.isSubmitting = false
    }
  }
}

@Composable
private fun ManualTransactionFormBody(
  formState: ManualTransactionFormState,
  accounts: List<AccountEntity>,
  typeColor: Color,
  onAccountSelected: (Long?) -> Unit
) {
  val filteredCategories = filterCategoriesForType(formState.categories, formState.selectedType)

  TransactionTypeSelector(
    selectedType = formState.selectedType,
    isEditMode = formState.isEditMode,
    categories = formState.categories,
    onTypeSelected = { type, categoryId ->
      formState.selectedType = type
      formState.selectedCategoryId = categoryId
      if (type != "TRANSFER") {
        formState.destinationAccountId = null
      }
    }
  )

  ManualTransactionAccountSection(
    formState = formState,
    accounts = accounts,
    onAccountSelected = onAccountSelected
  )

  TransactionAmountInput(
    amountValue = formState.amountValue,
    typeColor = typeColor,
    onAmountChanged = { value, _ ->
      if (formState.isEditMode && value.text != formState.amountValue.text) {
        formState.amountModified = true
      }
      formState.amountValue = value
    }
  )

  TransactionTypeSpecificFields(
    formState = formState,
    filteredCategories = filteredCategories
  )

  JalaliDateTimePicker(
    initialTimestamp = formState.customDate,
    onTimestampChanged = { formState.customDate = it }
  )

  TransactionDescriptionInput(
    description = formState.descriptionText,
    onDescriptionChanged = { formState.descriptionText = it }
  )
}

@Composable
private fun ManualTransactionAccountSection(
  formState: ManualTransactionFormState,
  accounts: List<AccountEntity>,
  onAccountSelected: (Long?) -> Unit
) {
  if (accounts.isNotEmpty()) {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
      Text(
        text = "حساب:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      AccountSelector(
        accounts = accounts,
        selectedAccountId = formState.sourceAccountId,
        onAccountSelected = {
          formState.sourceAccountId = it
          onAccountSelected(it)
        },
        includeAllAccountsOption = false
      )
    }
  }

  if (formState.selectedType == "TRANSFER") {
    DestinationAccountSelector(
      accounts = accounts,
      sourceAccountId = formState.sourceAccountId ?: 0L,
      selectedDestinationAccountId = formState.destinationAccountId,
      onDestinationAccountSelected = { formState.destinationAccountId = it }
    )
  }
}

@Composable
private fun TransactionTypeSpecificFields(
  formState: ManualTransactionFormState,
  filteredCategories: List<Category>
) {
  if (formState.selectedType == "EXPENSE" || formState.selectedType == "INCOME") {
    TransactionCategorySelector(
      filteredCategories = filteredCategories,
      selectedCategoryId = formState.selectedCategoryId,
      onCategorySelected = { formState.selectedCategoryId = it }
    )
  }

  if (formState.selectedType == "LOAN_DEBTOR" || formState.selectedType == "LOAN_CREDITOR") {
    LoanPersonNameInput(
      personName = formState.personNameText,
      onPersonNameChanged = { formState.personNameText = it }
    )
  }

  if (formState.selectedType == "INSTALLMENT") {
    InstallmentFormFields(
      title = formState.titleText,
      daysFromNow = formState.daysFromNowText,
      onTitleChanged = { formState.titleText = it },
      onDaysChanged = { formState.daysFromNowText = it }
    )
  }
}

private fun filterCategoriesForType(
  categories: List<Category>,
  selectedType: String
): List<Category> =
  when (selectedType) {
    TransactionType.INCOME.name ->
      categories.filter { cat ->
        cat.type == CategoryType.INCOME || cat.type == CategoryType.BOTH
      }
    TransactionType.EXPENSE.name ->
      categories.filter { cat ->
        cat.type == CategoryType.EXPENSE || cat.type == CategoryType.BOTH
      }
    "TRANSFER" -> emptyList()
    else -> categories.filter { cat -> cat.key == "Loans" || cat.key == "Installments" || cat.key == "Other" }
  }

@Composable
private fun resolveDialogTypeColor(selectedType: String) =
  when (selectedType) {
    "INCOME", "LOAN_DEBTOR" -> MaterialTheme.colorScheme.primary
    "EXPENSE", "LOAN_CREDITOR" -> MaterialTheme.colorScheme.error
    "TRANSFER" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.tertiary
  }

private fun showToast(
  context: Context,
  message: String
) {
  android.widget.Toast
    .makeText(context, message, android.widget.Toast.LENGTH_SHORT)
    .show()
}
