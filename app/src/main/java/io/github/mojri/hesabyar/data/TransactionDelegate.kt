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
   * date. The persisted row wins for the link itself: a snapshot
   * that predates the loan link (a legacy row stamped by a later
   * loan date edit) must not unstamp the row. The reads and the
   * write share one transaction, so a concurrent loan date edit
   * cannot slip between them.
   */
  override suspend fun updateTransaction(transaction: Transaction) {
    database.withTransaction {
      // No new DAO query: both DAOs sit at detekt's function-count
      // threshold, and the edit path is user-initiated, so a single
      // full-table read to find one row by id is acceptable here.
      val persisted =
        transactionDao.getAllTransactionsBlocking().singleOrNull { it.id == transaction.id }
      val loanId = persisted?.loanId ?: transaction.loanId
      val loanDate = loanId?.let { loanDao.getLoanById(it)?.date }
      val toWrite =
        transaction.copy(
          loanId = loanId,
          date = loanDate ?: transaction.date
        )
      transactionDao.updateTransaction(toWrite)
    }
  }
}
