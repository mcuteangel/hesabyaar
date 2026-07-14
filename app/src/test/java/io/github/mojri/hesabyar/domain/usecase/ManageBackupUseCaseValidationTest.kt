package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [ManageBackupUseCase.validateBackup] on its **native (Rust)**
 * path — the only path reachable in unit tests, because the `hesabyar_core`
 * library always loads here (the Kotlin fallback in `validateBackupKotlin`
 * is exercised by an instrumentation run without the native library).
 *
 * This locks in the dispatch + Rust-backed validation contract: a well-formed
 * payload is accepted, and a structurally broken one is surfaced as `Invalid`.
 */
class ManageBackupUseCaseValidationTest {
  private class FakeRepository : HesabyarRepositoryInterface {
    override val allTransactions: Flow<List<Transaction>> = flowOf(emptyList())
    override val allLoans: Flow<List<Loan>> = flowOf(emptyList())
    override val allInstallments: Flow<List<Installment>> = flowOf(emptyList())
    override val allCategories: Flow<List<Category>> = flowOf(emptyList())

    override fun getTransactionsInRange(
      start: Long,
      end: Long
    ): Flow<List<Transaction>> = flowOf(emptyList())

    override fun getCategoriesByType(type: String): Flow<List<Category>> = flowOf(emptyList())

    override suspend fun getCategoryById(id: Long): Category? = null

    override suspend fun getCategoryByKey(key: String): Category? = null

    override suspend fun insertCategory(category: Category): Long = 0L

    override suspend fun updateCategory(category: Category) {}

    override suspend fun deleteCategory(category: Category) {}

    override suspend fun insertTransaction(transaction: Transaction): Long = 0L

    override suspend fun deleteTransaction(transaction: Transaction) {}

    override suspend fun updateTransaction(transaction: Transaction) {}

    override suspend fun insertLoan(loan: Loan): Long = 0L

    override suspend fun updateLoan(loan: Loan) {}

    override suspend fun deleteLoan(loan: Loan) {}

    override fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> = flowOf(emptyList())

    override suspend fun addPaymentToLoan(
      loanId: Long,
      amount: Long,
      notes: String,
      customDate: Long?
    ): Boolean = false

    override suspend fun insertInstallment(installment: Installment): Long = 0L

    override suspend fun updateInstallment(installment: Installment) {}

    override suspend fun deleteInstallment(installment: Installment) {}

    override suspend fun importBackup(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      paymentHistories: List<PaymentHistory>
    ) {}

    override suspend fun replaceAllFromBackup(backup: BackupPayload) {}

    override suspend fun mergeFromBackup(backup: BackupPayload) {}

    override suspend fun getAllPaymentHistories(): List<PaymentHistory> = emptyList()
  }

  private val useCase = ManageBackupUseCase(FakeRepository())

  @Test
  fun `well formed payload is valid`() =
    runBlocking {
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 1L,
                amount = 1_000_000L,
                description = "t",
                date = 1_700_000_000_000L
              )
            ),
          loans =
            listOf(
              Loan(
                personName = "علی",
                type = LoanType.DEBTOR,
                originalAmount = 5_000_000L,
                remainingAmount = 3_000_000L,
                description = "l",
                date = 1_700_000_000_000L
              )
            ),
          installments =
            listOf(
              Installment(title = "قسط", amount = 1_000_000L, dueDate = 1_700_000_000_000L)
            ),
          categories =
            listOf(
              Category(
                id = 1L,
                name = "خوراک",
                key = "food",
                icon = "i",
                color = 0xFF0000L,
                type = CategoryType.EXPENSE
              )
            ),
          paymentHistories =
            listOf(PaymentHistory(id = 1L, loanId = 1L, amount = 100_000L, date = 1_700_000_000_000L))
        )

      val result = useCase.validateBackup(payload)
      assertTrue("expected $result to be Valid", result is BackupValidationResult.Valid)
    }

  @Test
  fun `malformed json style payload is surfaced as Invalid`() =
    runBlocking {
      // A transaction with a non-positive amount is invalid by every validator
      // (Kotlin fallback and the Rust core agree), so this must not pass as Valid.
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 1L,
                amount = 0L,
                description = "t",
                date = 1_700_000_000_000L
              )
            )
        )

      val result = useCase.validateBackup(payload)
      assertTrue("expected $result to be Invalid for zero-amount transaction", result is BackupValidationResult.Invalid)
    }
}
