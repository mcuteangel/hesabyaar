package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitManualTransactionUseCaseTest {
  private val fake = FakeRepository()
  private val manageTransaction = ManageTransactionUseCase(fake)
  private val manageLoan = ManageLoanUseCase(fake)
  private val manageInstallment = ManageInstallmentUseCase(fake)
  private val useCase =
    SubmitManualTransactionUseCase(manageTransaction, manageLoan, manageInstallment)

  @Test
  fun validateReturnsErrorWhenAmountIsZeroOrNegative() =
    runTest {
      val result =
        useCase.validate(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 0L,
            selectedType = "INCOME",
            selectedCategoryId = 1L,
            descriptionText = "",
            personName = "",
            title = "",
            daysFromNowText = "",
            amountRial = 0L,
            customDate = System.currentTimeMillis(),
            categories = emptyList()
          )
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun validateReturnsErrorWhenIncomeOrExpenseHasNoCategory() =
    runTest {
      val result =
        useCase.validate(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1000L,
            selectedType = "EXPENSE",
            selectedCategoryId = 0L,
            descriptionText = "",
            personName = "",
            title = "",
            daysFromNowText = "",
            amountRial = 1000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList()
          )
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا دسته‌بندی را انتخاب کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun validateReturnsErrorWhenLoanTypeHasNoPersonName() =
    runTest {
      val result =
        useCase.validate(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1000L,
            selectedType = "LOAN_DEBTOR",
            selectedCategoryId = 0L,
            descriptionText = "",
            personName = "  ",
            title = "",
            daysFromNowText = "",
            amountRial = 1000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList()
          )
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا نام شخص مربوطه را وارد کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun validateReturnsErrorWhenInstallmentHasNoTitle() =
    runTest {
      val result =
        useCase.validate(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1000L,
            selectedType = "INSTALLMENT",
            selectedCategoryId = 0L,
            descriptionText = "",
            personName = "",
            title = "   ",
            daysFromNowText = "30",
            amountRial = 1000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList()
          )
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا عنوان قسط را وارد کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun validateReturnsErrorWhenInstallmentHasInvalidDaysText() =
    runTest {
      val result =
        useCase.validate(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1000L,
            selectedType = "INSTALLMENT",
            selectedCategoryId = 0L,
            descriptionText = "",
            personName = "",
            title = "Car installment",
            daysFromNowText = "abc",
            amountRial = 1000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList()
          )
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا تعداد روزها را به صورت عدد وارد کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun validateReturnsValidForInstallmentWithEmptyDaysText() =
    runTest {
      val result =
        useCase.validate(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1000L,
            selectedType = "INSTALLMENT",
            selectedCategoryId = 0L,
            descriptionText = "",
            personName = "",
            title = "Car installment",
            daysFromNowText = "",
            amountRial = 1000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList()
          )
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Valid)
    }

  @Test
  fun validateReturnsValidForAcceptableInput() =
    runTest {
      val result =
        useCase.validate(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1000L,
            selectedType = "INCOME",
            selectedCategoryId = 1L,
            descriptionText = "",
            personName = "علی",
            title = "قسط",
            daysFromNowText = "",
            amountRial = 1000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList()
          )
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Valid)
    }

  @Test
  fun submitReturnsSuccessForValidIncomeTransaction() =
    runTest {
      val categories =
        listOf(Category(id = 1L, name = "Salary", key = "Income", icon = "", color = 0L, type = CategoryType.INCOME))
      val result =
        useCase.submit(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1000L,
            selectedType = "INCOME",
            selectedCategoryId = 1L,
            descriptionText = "Test income",
            personName = "",
            title = "",
            daysFromNowText = "30",
            amountRial = 1000L,
            customDate = System.currentTimeMillis(),
            categories = categories,
            transactionToEdit = null
          )
        )
      assertTrue(result.success)
      assertEquals(null, result.errorMessage)

      val stored = fake.allTransactions.first()
      assertEquals(1, stored.size)
      assertEquals(TransactionType.INCOME, stored.first().type)
      assertEquals(1000L, stored.first().amount)
      assertEquals("Test income", stored.first().description)
      assertEquals(1L, stored.first().categoryId)
    }

  @Test
  fun submitReturnsSuccessForValidLoan() =
    runTest {
      val result =
        useCase.submit(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 2000L,
            selectedType = "LOAN_DEBTOR",
            selectedCategoryId = 0L,
            descriptionText = "Loan desc",
            personName = "رضا",
            title = "",
            daysFromNowText = "30",
            amountRial = 2000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList(),
            transactionToEdit = null
          )
        )
      assertTrue(result.success)
      assertEquals(null, result.errorMessage)

      val stored = fake.allLoans.first()
      assertEquals(1, stored.size)
      assertEquals(LoanType.DEBTOR, stored.first().type)
      assertEquals(2000L, stored.first().originalAmount)
      assertEquals("Loan desc", stored.first().description)
      assertEquals("رضا", stored.first().personName)
    }

  @Test
  fun submitReturnsSuccessForValidInstallment() =
    runTest {
      val result =
        useCase.submit(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 30000L,
            selectedType = "INSTALLMENT",
            selectedCategoryId = 0L,
            descriptionText = "Installment notes",
            personName = "",
            title = "Car installment",
            daysFromNowText = "30",
            amountRial = 30000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList(),
            transactionToEdit = null
          )
        )
      assertTrue(result.success)
      assertEquals(null, result.errorMessage)

      val installments = fake.allInstallments.first()
      assertEquals(1, installments.size)
      assertEquals("Car installment", installments.first().title)
      assertEquals(30000L, installments.first().amount)
      assertEquals(true, installments.first().reminderEnabled)
      assertEquals("Installment notes", installments.first().notes)
    }

  @Test
  fun submitReturnsErrorForInvalidTransactionType() =
    runTest {
      val result =
        useCase.submit(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1000L,
            selectedType = "UNKNOWN_TYPE",
            selectedCategoryId = 0L,
            descriptionText = "",
            personName = "",
            title = "",
            daysFromNowText = "30",
            amountRial = 1000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList(),
            transactionToEdit = null
          )
        )
      assertFalse(result.success)
      assertEquals("نوع تراکنش نامعتبر است", result.errorMessage)
    }

  @Test
  fun submitUpdatePathUsesEditModeValues() =
    runTest {
      val categories =
        listOf(Category(id = 1L, name = "Food", key = "Food", icon = "", color = 0L, type = CategoryType.EXPENSE))
      val original =
        Transaction(
          id = 10L,
          type = TransactionType.EXPENSE,
          categoryId = 1L,
          amount = 5000L,
          description = "Old",
          personName = null,
          date = System.currentTimeMillis()
        )
      fake.insertTransaction(original)

      val result =
        useCase.submit(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 1500L,
            selectedType = "EXPENSE",
            selectedCategoryId = 1L,
            descriptionText = "Updated",
            personName = "",
            title = "",
            daysFromNowText = "30",
            amountRial = 1500L,
            customDate = System.currentTimeMillis(),
            categories = categories,
            transactionToEdit = original
          )
        )
      assertTrue(result.success)

      val stored = fake.allTransactions.first()
      assertEquals(1, stored.size)
      val updated = stored.first()
      assertEquals(10L, updated.id)
      assertEquals(1500L, updated.amount)
      assertEquals("Updated", updated.description)
      assertEquals(TransactionType.EXPENSE, updated.type)
    }
}
