package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.AmountResolutionInput
import io.github.mojri.hesabyar.ui.TransactionAmountResolver
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
  fun `validate returns error when amount is zero or negative`() =
    runTest {
      val result =
        useCase.validate(
          amountDisplay = 0L,
          selectedType = "INCOME",
          selectedCategoryId = 1L,
          personName = "",
          title = ""
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun `validate returns error when income or expense has no category`() =
    runTest {
      val result =
        useCase.validate(
          amountDisplay = 1000L,
          selectedType = "EXPENSE",
          selectedCategoryId = 0L,
          personName = "",
          title = ""
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا دسته‌بندی را انتخاب کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun `validate returns error when loan type has no person name`() =
    runTest {
      val result =
        useCase.validate(
          amountDisplay = 1000L,
          selectedType = "LOAN_DEBTOR",
          selectedCategoryId = 0L,
          personName = "  ",
          title = ""
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا نام شخص مربوطه را وارد کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun `validate returns error when installment has no title`() =
    runTest {
      val result =
        useCase.validate(
          amountDisplay = 1000L,
          selectedType = "INSTALLMENT",
          selectedCategoryId = 0L,
          personName = "",
          title = "   "
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Error)
      assertEquals(
        "لطفا عنوان قسط را وارد کنید",
        (result as SubmitManualTransactionUseCase.ValidationResult.Error).message
      )
    }

  @Test
  fun `validate returns valid for acceptable input`() =
    runTest {
      val result =
        useCase.validate(
          amountDisplay = 1000L,
          selectedType = "INCOME",
          selectedCategoryId = 1L,
          personName = "علی",
          title = "قسط"
        )
      assertTrue(result is SubmitManualTransactionUseCase.ValidationResult.Valid)
    }

  @Test
  fun `submit returns success for valid income transaction`() =
    runTest {
      val categories =
        listOf(Category(id = 1L, name = "Salary", key = "Income", icon = "", color = 0L, type = CategoryType.INCOME))
      val result =
        useCase.submit(
          selectedType = "INCOME",
          amountDisplay = 1000L,
          isEditMode = false,
          originalAmountRial = 0L,
          amountModified = false,
          selectedCategoryId = 1L,
          descriptionText = "Test income",
          personName = "",
          title = "",
          daysFromNowText = "30",
          customDate = System.currentTimeMillis(),
          categories = categories,
          transactionToEdit = null
        )
      assertTrue(result.success)
      assertEquals(null, result.errorMessage)
    }

  @Test
  fun `submit returns success for valid loan`() =
    runTest {
      val result =
        useCase.submit(
          selectedType = "LOAN_DEBTOR",
          amountDisplay = 2000L,
          isEditMode = false,
          originalAmountRial = 0L,
          amountModified = false,
          selectedCategoryId = 0L,
          descriptionText = "Loan desc",
          personName = "رضا",
          title = "",
          daysFromNowText = "30",
          customDate = System.currentTimeMillis(),
          categories = emptyList(),
          transactionToEdit = null
        )
      assertTrue(result.success)
      assertEquals(null, result.errorMessage)
    }

  @Test
  fun `submit returns success for valid installment`() =
    runTest {
      val result =
        useCase.submit(
          selectedType = "INSTALLMENT",
          amountDisplay = 3000L,
          isEditMode = false,
          originalAmountRial = 0L,
          amountModified = false,
          selectedCategoryId = 0L,
          descriptionText = "Installment notes",
          personName = "",
          title = "Car installment",
          daysFromNowText = "30",
          customDate = System.currentTimeMillis(),
          categories = emptyList(),
          transactionToEdit = null
        )
      assertTrue(result.success)
      assertEquals(null, result.errorMessage)

      val installments = fake.allInstallments.first()
      assertEquals(1, installments.size)
      assertEquals("Car installment", installments.first().title)
      assertEquals(30000L, installments.first().amount)
      assertEquals(true, installments.first().reminderEnabled)
    }

  @Test
  fun `submit returns error for invalid transaction type`() =
    runTest {
      val result =
        useCase.submit(
          selectedType = "UNKNOWN_TYPE",
          amountDisplay = 1000L,
          isEditMode = false,
          originalAmountRial = 0L,
          amountModified = false,
          selectedCategoryId = 0L,
          descriptionText = "",
          personName = "",
          title = "",
          daysFromNowText = "30",
          customDate = System.currentTimeMillis(),
          categories = emptyList(),
          transactionToEdit = null
        )
      assertFalse(result.success)
      assertEquals("نوع تراکنش نامعتبر است", result.errorMessage)
    }

  @Test
  fun `submit update path uses edit mode values`() =
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
      val result =
        useCase.submit(
          selectedType = "EXPENSE",
          amountDisplay = 1500L,
          isEditMode = true,
          originalAmountRial = 5000L,
          amountModified = true,
          selectedCategoryId = 1L,
          descriptionText = "Updated",
          personName = "",
          title = "",
          daysFromNowText = "30",
          customDate = System.currentTimeMillis(),
          categories = categories,
          transactionToEdit = original
        )
      assertTrue(result.success)
    }

  @Test
  fun `resolveAmount preserves original when not modified in edit mode`() =
    runTest {
      val input =
        AmountResolutionInput(
          displayedAmount = 0L,
          isEditMode = true,
          originalRialAmount = 7500L,
          userModifiedAmount = false
        )
      val resolved = TransactionAmountResolver.resolveAmount(input)
      assertEquals(7500L, resolved.rialAmount)
      assertEquals(true, resolved.preservedOriginal)
    }
}
