package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

internal class InstallmentDelegate(
  private val installmentDao: InstallmentDao,
  private val transactionDao: TransactionDao,
  private val transactionLinkDao: TransactionLinkDao,
  private val categoryDao: CategoryDao,
  private val database: AppDatabase
) : InstallmentOps {
  override val allInstallments: Flow<List<Installment>> = installmentDao.getAllInstallments()

  override suspend fun insertInstallment(installment: Installment): Long = installmentDao.insertInstallment(installment)

  override suspend fun insertInstallmentWithInitial(
    installment: Installment,
    recordInitial: Boolean
  ): Long {
    TrackedLedgerHelper.validateTrackedAccount(installment.tracked, installment.accountId)
    val normalizedInstallment =
      installment.copy(
        accountId = TrackedLedgerHelper.normalizeAccountId(installment.tracked, installment.accountId)
      )
    return database.withTransaction {
      val id = installmentDao.insertInstallment(normalizedInstallment)
      if (recordInitial && normalizedInstallment.tracked && normalizedInstallment.isPaid) {
        val category =
          categoryDao.getCategoryByKey("Installments")
            ?: throw IllegalStateException(
              "Installments category is missing from database"
            )
        val stored = installmentDao.getInstallmentById(id) ?: normalizedInstallment.copy(id = id)
        transactionDao.insertTransaction(
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = category.id,
            amount = stored.amount,
            description = "پرداخت قسط: ${stored.title} - ${stored.notes}",
            installmentId = id,
            accountId = TrackedLedgerHelper.resolveAccountId(stored.accountId)
          )
        )
      }
      id
    }
  }

  override suspend fun updateInstallment(installment: Installment) {
    database.withTransaction {
      // A stale or already-deleted installment updates zero rows and must not
      // trigger a payment transition for a row that does not exist.
      val existing =
        installmentDao.getInstallmentById(installment.id)
          ?: return@withTransaction
      installmentDao.updateInstallment(installment)
      val justPaid = installment.isPaid && !existing.isPaid
      val justUnpaid = !installment.isPaid && existing.isPaid
      // Phase 2 opt-in: only tracked rows post or reverse ledger entries.
      // Untracked rows only flip isPaid; historical transactions stay untouched.
      if (!installment.tracked) {
        installmentDao.updateInstallment(installment)
        return@withTransaction
      }
      val installmentsCategory = categoryDao.getCategoryByKey("Installments")
      if (justPaid) {
        val category =
          installmentsCategory
            ?: throw IllegalStateException(
              "Installments category is missing; cannot record the paid installment expense"
            )
        transactionDao.insertTransaction(
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = category.id,
            amount = installment.amount,
            description = "پرداخت قسط: ${installment.title} - ${installment.notes}",
            installmentId = installment.id,
            accountId = TrackedLedgerHelper.resolveAccountId(installment.accountId)
          )
        )
      } else if (justUnpaid) {
        // Reverse the expense recorded when the installment was first paid, so
        // toggling paid → unpaid → paid never double-counts the money. A
        // missing category aborts the whole update — the paid→unpaid flip rolls
        // back with the transaction — instead of leaving the expense behind an
        // unpaid row.
        val category =
          installmentsCategory
            ?: throw IllegalStateException(
              "Installments category is missing; cannot reverse the paid installment expense"
            )
        transactionLinkDao.deleteTransactionForInstallment(
          installmentId = installment.id,
          categoryId = category.id
        )
      }
    }
  }

  override suspend fun deleteInstallment(installment: Installment) {
    database.withTransaction {
      // Decide from the persisted row, not the caller's object: a stale
      // unpaid snapshot deleting a row that is actually paid would skip the
      // linked-expense cleanup and strand the money behind a dead row. A row
      // already deleted through another path (bank-loan cascade) can still
      // carry a linked expense behind it — clean it before returning.
      val existing = installmentDao.getInstallmentById(installment.id)
      if (existing == null) {
        transactionLinkDao.deleteTransactionsForInstallment(installment.id)
        return@withTransaction
      }
      if (existing.isPaid) {
        transactionLinkDao.deleteTransactionsForInstallment(existing.id)
      }
      installmentDao.deleteInstallment(installment)
    }
  }
}
