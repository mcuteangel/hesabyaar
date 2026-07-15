package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakeRepository : HesabyarRepositoryInterface {
  override val allTransactions: Flow<List<Transaction>> = flowOf(emptyList())
  override val allLoans: Flow<List<Loan>> = flowOf(emptyList())
  override val allInstallments: Flow<List<Installment>> = flowOf(emptyList())
  override val allCategories: Flow<List<Category>> = flowOf(emptyList())
  override val allBankLoans: Flow<List<BankLoan>> = flowOf(emptyList())

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

  override suspend fun getBankLoanById(id: Long): BankLoan? = null

  override suspend fun insertBankLoan(bankLoan: BankLoan): Long = 0L

  override suspend fun updateBankLoan(bankLoan: BankLoan) {}

  override suspend fun deleteBankLoan(bankLoan: BankLoan) {}

  override suspend fun getInstallmentsByBankLoanId(bankLoanId: Long): List<Installment> = emptyList()

  override suspend fun getAllBankLoansSync(): List<BankLoan> = emptyList()

  override suspend fun importBackup(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    paymentHistories: List<PaymentHistory>,
    bankLoans: List<BankLoan>
  ) {}

  override suspend fun replaceAllFromBackup(backup: BackupPayload) {}

  override suspend fun mergeFromBackup(backup: BackupPayload) {}

  override suspend fun getAllPaymentHistories(): List<PaymentHistory> = emptyList()
}
