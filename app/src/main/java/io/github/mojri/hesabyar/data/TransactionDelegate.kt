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
   * date. The persisted row is the current truth for the link and
   * wins in both directions: a snapshot that predates the loan
   * link (a legacy row stamped by a later loan date edit) must not
   * unstamp the row, and a stale snapshot link must not reattach
   * a row whose persisted link is null (e.g. after a restore).
   * Only a row with no persisted counterpart falls back to the
   * snapshot's link. The reads and the write share one
   * transaction, so a concurrent loan date edit cannot slip
   * between them.
   */
  override suspend fun updateTransaction(transaction: Transaction) {
    database.withTransaction {
      // No new DAO query: every DAO in Daos.kt sits at detekt's
      // function-count threshold (11), and the edit path is
      // user-initiated, so a single full-table read to find one
      // row by id is acceptable here.
      val persisted =
        transactionDao.getAllTransactionsBlocking().singleOrNull { it.id == transaction.id }
      val loanId = if (persisted != null) persisted.loanId else transaction.loanId
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
