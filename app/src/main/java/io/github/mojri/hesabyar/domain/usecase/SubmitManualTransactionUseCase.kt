package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.AmountResolutionInput
import io.github.mojri.hesabyar.ui.TransactionAmountResolver

class SubmitManualTransactionUseCase(
  private val manageTransaction: ManageTransactionUseCase,
  private val manageLoan: ManageLoanUseCase,
  private val manageInstallment: ManageInstallmentUseCase
) {
  sealed class ValidationResult {
    data object Valid : ValidationResult()

    data class Error(
      val message: String
    ) : ValidationResult()
  }

  data class SubmitResult(
    val success: Boolean,
    val errorMessage: String? = null
  )

  fun validate(
    amountDisplay: Long,
    selectedType: String,
    selectedCategoryId: Long,
    personName: String,
    title: String
  ): ValidationResult {
    val error =
      when {
        amountDisplay <= 0L -> "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید"
        (selectedType == "INCOME" || selectedType == "EXPENSE") && selectedCategoryId == 0L ->
          "لطفا دسته‌بندی را انتخاب کنید"
        (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") && personName.isBlank() ->
          "لطفا نام شخص مربوطه را وارد کنید"
        selectedType == "INSTALLMENT" && title.isBlank() -> "لطفا عنوان قسط را وارد کنید"
        else -> null
      }
    return if (error != null) ValidationResult.Error(error) else ValidationResult.Valid
  }

  suspend fun submit(
    selectedType: String,
    amountDisplay: Long,
    isEditMode: Boolean,
    originalAmountRial: Long,
    amountModified: Boolean,
    selectedCategoryId: Long,
    descriptionText: String,
    personName: String,
    title: String,
    daysFromNowText: String,
    customDate: Long,
    categories: List<Category>,
    transactionToEdit: Transaction?
  ): SubmitResult {
    val finalAmountRial = resolveAmount(amountDisplay, isEditMode, originalAmountRial, amountModified)
    return when (selectedType) {
      "INCOME", "EXPENSE" ->
        submitTransaction(
          selectedType,
          finalAmountRial,
          selectedCategoryId,
          descriptionText,
          customDate,
          isEditMode,
          transactionToEdit,
          categories
        )

      "LOAN_DEBTOR", "LOAN_CREDITOR" ->
        submitLoan(
          selectedType,
          finalAmountRial,
          personName,
          descriptionText,
          customDate
        )

      "INSTALLMENT" ->
        submitInstallment(
          finalAmountRial,
          title,
          descriptionText,
          daysFromNowText,
          customDate
        )

      else -> SubmitResult(success = false, errorMessage = "نوع تراکنش نامعتبر است")
    }
  }

  private suspend fun submitTransaction(
    selectedType: String,
    finalAmountRial: Long,
    selectedCategoryId: Long,
    descriptionText: String,
    customDate: Long,
    isEditMode: Boolean,
    transactionToEdit: Transaction?,
    categories: List<Category>
  ): SubmitResult {
    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "سایر"
    val desc = descriptionText.trim().ifEmpty { selectedCategoryName }
    if (isEditMode && transactionToEdit != null) {
      val updated =
        transactionToEdit.copy(
          type = TransactionType.valueOf(selectedType),
          categoryId = selectedCategoryId,
          amount = finalAmountRial,
          description = desc,
          date = customDate
        )
      manageTransaction.updateTransaction(updated)
    } else {
      manageTransaction.addTransaction(
        type = TransactionType.valueOf(selectedType),
        categoryId = selectedCategoryId,
        amount = finalAmountRial,
        description = desc,
        customDate = customDate
      )
    }
    return SubmitResult(success = true)
  }

  private suspend fun submitLoan(
    selectedType: String,
    finalAmountRial: Long,
    personName: String,
    descriptionText: String,
    customDate: Long
  ): SubmitResult {
    val defaultDesc = if (selectedType == "LOAN_DEBTOR") "قرض دادن به $personName" else "قرض گرفتن از $personName"
    val desc = descriptionText.trim().ifEmpty { defaultDesc }
    manageLoan.addLoan(
      personName = personName,
      type = if (selectedType == "LOAN_DEBTOR") LoanType.DEBTOR else LoanType.CREDITOR,
      amount = finalAmountRial,
      description = desc,
      customDate = customDate
    )
    return SubmitResult(success = true)
  }

  private suspend fun submitInstallment(
    finalAmountRial: Long,
    title: String,
    descriptionText: String,
    daysFromNowText: String,
    customDate: Long
  ): SubmitResult {
    val millisPerDay = 24L * 60 * 60 * 1000
    val daysOffset = daysFromNowText.toLongOrNull()
    val dueDate =
      if (daysOffset != null && daysOffset > 0) {
        System.currentTimeMillis() + daysOffset * millisPerDay
      } else {
        customDate
      }
    manageInstallment.addInstallment(
      title = title,
      amount = finalAmountRial,
      dueDate = dueDate,
      reminderEnabled = true,
      notes = descriptionText.trim()
    )
    return SubmitResult(success = true)
  }

  private fun resolveAmount(
    amountDisplay: Long,
    isEditMode: Boolean,
    originalAmountRial: Long,
    amountModified: Boolean
  ): Long {
    val result =
      TransactionAmountResolver.resolveAmount(
        AmountResolutionInput(
          displayedAmount = amountDisplay,
          isEditMode = isEditMode,
          originalRialAmount = originalAmountRial,
          userModifiedAmount = amountModified
        )
      )
    return result.rialAmount
  }
}
