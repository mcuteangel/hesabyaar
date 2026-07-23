package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.LoanViewModel
import io.github.mojri.hesabyar.ui.ManualTransactionSubmitter
import io.github.mojri.hesabyar.ui.TransactionViewModel
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.JalaliDateTimePicker
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun ManualTransactionDialog(
  transactionViewModel: TransactionViewModel,
  loanViewModel: LoanViewModel,
  installmentViewModel: InstallmentViewModel,
  categories: List<Category>,
  transactionToEdit: Transaction? = null,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val isEditMode = transactionToEdit != null
  var selectedType by remember { mutableStateOf(transactionToEdit?.type?.name ?: TransactionType.EXPENSE.name) }
  val originalAmountRial by remember { mutableStateOf(transactionToEdit?.amount ?: 0L) }
  var amountValue by remember {
    mutableStateOf(
      TextFieldValue(
        if (isEditMode) CurrencyFormatter.fromRial(transactionToEdit.amount).toString() else ""
      )
    )
  }
  var amountModified by remember { mutableStateOf(false) }
  var descriptionText by remember { mutableStateOf(transactionToEdit?.description.orEmpty()) }
  var selectedCategoryId by remember { mutableStateOf(transactionToEdit?.categoryId ?: 0L) }
  var personNameText by remember { mutableStateOf(transactionToEdit?.personName ?: "") }
  var titleText by remember { mutableStateOf(transactionToEdit?.description ?: "") }
  var daysFromNowText by remember { mutableStateOf("30") }
  var customDate by remember { mutableStateOf(transactionToEdit?.date ?: System.currentTimeMillis()) }

  val filteredCategories =
    categories.filter { cat ->
      when (selectedType) {
        TransactionType.INCOME.name -> cat.type == CategoryType.INCOME || cat.type == CategoryType.BOTH
        TransactionType.EXPENSE.name -> cat.type == CategoryType.EXPENSE || cat.type == CategoryType.BOTH
        else -> cat.key == "Loans" || cat.key == "Installments" || cat.key == "Other"
      }
    }

  val typeColor = resolveDialogTypeColor(selectedType)

  HesabyarDialog(
    title = if (isEditMode) "ویرایش تراکنش" else "ثبت دستی تراکنش جدید",
    onDismissRequest = onDismiss,
    widthFraction = 0.92f,
    actions = {
      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier.weight(1f),
        shape = ShapeTokens.Medium
      ) {
        Text("انصراف")
      }

      Button(
        onClick = {
          val finalAmountDisplay = amountValue.text.toLongOrNull() ?: 0L
          val validationResult =
            ManualTransactionSubmitter.validate(
              amountDisplay = finalAmountDisplay,
              selectedType = selectedType,
              selectedCategoryId = selectedCategoryId,
              personName = personNameText,
              title = titleText
            )

          if (validationResult is ManualTransactionSubmitter.ValidationResult.Error) {
            showToast(context, validationResult.message)
            return@Button
          }

          val submitResult =
            ManualTransactionSubmitter.submit(
              selectedType = selectedType,
              amountDisplay = finalAmountDisplay,
              isEditMode = isEditMode,
              originalAmountRial = originalAmountRial,
              amountModified = amountModified,
              selectedCategoryId = selectedCategoryId,
              descriptionText = descriptionText,
              personName = personNameText,
              title = titleText,
              daysFromNowText = daysFromNowText,
              customDate = customDate,
              categories = categories,
              transactionViewModel = transactionViewModel,
              loanViewModel = loanViewModel,
              installmentViewModel = installmentViewModel,
              transactionToEdit = transactionToEdit
            )

          if (submitResult.success) {
            onDismiss()
          } else {
            submitResult.errorMessage?.let { showToast(context, it) }
          }
        },
        modifier = Modifier.weight(1f),
        shape = ShapeTokens.Medium,
        colors = ButtonDefaults.buttonColors(containerColor = typeColor)
      ) {
        Text(
          if (isEditMode) "ذخیره تغییرات" else "ثبت تراکنش",
          color = MaterialTheme.colorScheme.onPrimary
        )
      }
    }
  ) {
    TransactionTypeSelector(
      selectedType = selectedType,
      isEditMode = isEditMode,
      categories = categories,
      onTypeSelected = { type, categoryId ->
        selectedType = type
        selectedCategoryId = categoryId
      }
    )

    TransactionAmountInput(
      amountValue = amountValue,
      typeColor = typeColor,
      onAmountChanged = { value, fromQuickFill ->
        if (isEditMode && fromQuickFill) amountModified = true
        amountValue = value
      }
    )

    if (selectedType == "EXPENSE" || selectedType == "INCOME") {
      TransactionCategorySelector(
        filteredCategories = filteredCategories,
        selectedCategoryId = selectedCategoryId,
        onCategorySelected = { selectedCategoryId = it }
      )
    }

    if (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") {
      LoanPersonNameInput(
        personName = personNameText,
        onPersonNameChanged = { personNameText = it }
      )
    }

    if (selectedType == "INSTALLMENT") {
      InstallmentFormFields(
        title = titleText,
        daysFromNow = daysFromNowText,
        onTitleChanged = { titleText = it },
        onDaysChanged = { daysFromNowText = it }
      )
    }

    JalaliDateTimePicker(
      initialTimestamp = customDate,
      onTimestampChanged = { customDate = it }
    )

    TransactionDescriptionInput(
      description = descriptionText,
      onDescriptionChanged = { descriptionText = it }
    )
  }
}

@Composable
private fun resolveDialogTypeColor(selectedType: String) =
  when (selectedType) {
    "INCOME", "LOAN_DEBTOR" -> MaterialTheme.colorScheme.primary
    "EXPENSE", "LOAN_CREDITOR" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
  }

private fun showToast(
  context: android.content.Context,
  message: String
) {
  android.widget.Toast
    .makeText(context, message, android.widget.Toast.LENGTH_SHORT)
    .show()
}
