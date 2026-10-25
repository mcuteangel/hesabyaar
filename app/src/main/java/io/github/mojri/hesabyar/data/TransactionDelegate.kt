package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

internal class TransactionDelegate(
  private val transactionDao: TransactionDao,
  private val loanDao: LoanDao,
  private val database: AppDatabase
) : TransactionOps {
  override val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

  override fun getTransactionsInRange(
    start: Long,
    end: Long
  ): Flow<List<Transaction>> = transactionDao.getTransactionsInRange(start, end)

  override suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insertTransaction(transaction)

  override suspend fun deleteTransaction(transaction: Transaction) {
    transactionDao.deleteTransaction(transaction)
  }

  /**
   * Issue #535: a stale editor snapshot must not restore an old
   * disbursement date. The loan date is the source of truth, so a
   * loan-linked row keeps the loan's current date at save time.
   * Other fields still come from the editor's snapshot. A row with
   * no loan, or a loan that no longer exists, keeps the editor's
   * date. The read and the write share one transaction, so a
   * concurrent loan date edit cannot slip between them.
   */
  override suspend fun updateTransaction(transaction: Transaction) {
    database.withTransaction {
      val loanDate = transaction.loanId?.let { loanDao.getLoanById(it)?.date }
      val toWrite = if (loanDate != null) transaction.copy(date = loanDate) else transaction
      transactionDao.updateTransaction(toWrite)
    }
  }
}
