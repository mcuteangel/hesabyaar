package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import io.github.mojri.hesabyar.rust.RustBridge
import kotlinx.coroutines.flow.Flow

internal class LoanDelegate(
  private val loanDao: LoanDao,
  private val paymentHistoryDao: PaymentHistoryDao,
  private val transactionDao: TransactionDao,
  private val transactionLinkDao: TransactionLinkDao,
  private val categoryDao: CategoryDao,
  private val database: AppDatabase
) : LoanOps {
  override val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()

  override suspend fun insertLoan(loan: Loan): Long {
    TrackedLedgerHelper.validateTrackedAccount(loan.tracked, loan.accountId)
    val normalizedLoan =
      loan.copy(accountId = TrackedLedgerHelper.normalizeAccountId(loan.tracked, loan.accountId))
    return loanDao.insertLoan(normalizedLoan)
  }

  override suspend fun insertLoanWithInitial(
    loan: Loan,
    recordInitial: Boolean
  ): Long {
    TrackedLedgerHelper.validateTrackedAccount(loan.tracked, loan.accountId)
    val normalizedLoan =
      loan.copy(accountId = TrackedLedgerHelper.normalizeAccountId(loan.tracked, loan.accountId))
    return database.withTransaction {
      val loanId = loanDao.insertLoan(normalizedLoan)
      if (recordInitial && normalizedLoan.tracked) {
        val loansCategory =
          categoryDao.getCategoryByKey(LoansCategoryExclusion.CATEGORY_KEY)
            ?: throw IllegalStateException(
              "Loans category is missing; cannot record the initial loan transaction"
            )
        val accountId = TrackedLedgerHelper.resolveAccountId(normalizedLoan.accountId)
        val stored = loanDao.getLoanById(loanId) ?: normalizedLoan.copy(id = loanId)
        val isDebt = stored.type == LoanType.CREDITOR
        val transactionType = if (isDebt) TransactionType.INCOME else TransactionType.EXPENSE
        transactionDao.insertTransaction(
          Transaction(
            // CREDITOR = "I owe": receiving the money is an inflow, and the
            // description ("دریافت وام") proves it. DEBTOR = "owed to me":
            // handing the money out is the outflow.
            type = transactionType,
            categoryId = loansCategory.id,
            amount = stored.originalAmount,
            description =
              if (isDebt) {
                "دریافت وام از ${stored.personName}"
              } else {
                "پرداخت وام به ${stored.personName}"
              },
            personName = stored.personName,
            personId = stored.personId,
            date = stored.date,
            accountId = accountId,
            // Issue #242: stamp loanId so deleteLoan can find this row
            // without matching on mutable fields.
            loanId = loanId
          )
        )
      }
      loanId
    }
  }

  override suspend fun updateLoan(loan: Loan): LoanUpdateResult {
    TrackedLedgerHelper.validateTrackedAccount(loan.tracked, loan.accountId)
    val normalizedLoan =
      loan.copy(accountId = TrackedLedgerHelper.normalizeAccountId(loan.tracked, loan.accountId))
    // Issue #529: reject edits that move the creation date after an
    // already-recorded payment. They corrupt point-in-time balances.
    // The read-validate-write runs in one transaction so a concurrent
    // writer cannot slip a payment between the check and the persist.
    return database.withTransaction {
      val persisted =
        loanDao.getLoanById(normalizedLoan.id)
          ?: return@withTransaction LoanUpdateResult.NotFound
      if (persisted.date != normalizedLoan.date &&
        !isLoanDateEditChronologicallyValid(normalizedLoan.id, normalizedLoan.date)
      ) {
        return@withTransaction LoanUpdateResult.ChronologyRejected
      }
      loanDao.updateLoan(normalizedLoan)
      // Issue #532: an accepted date edit must move the stamped disbursement
      // transaction too, or point-in-time balances keep counting it on the
      // old date. Matches only the stamped row via loanId; payment rows are
      // stamped via PaymentHistory.linkedTransactionId and never match.
      // Untracked loans have no disbursement row: the update matches 0 rows.
      if (persisted.date != normalizedLoan.date) {
        transactionLinkDao.updateDisbursementDateByLoanId(normalizedLoan.id, normalizedLoan.date)
      }
      LoanUpdateResult.Success
    }
  }

  override suspend fun deleteLoan(loan: Loan) {
    database.withTransaction {
      // Decide from the persisted row when available: the caller may hold a
      // stale snapshot whose tracked flag no longer matches the database.
      val existing = loanDao.getLoanById(loan.id) ?: loan
      // Issue #242: delete generated transactions by stable id, not by
      // mutable field matching. Payments stamped with linkedTransactionId
      // are deleted by primary key; the initial disbursement is deleted by
      // loanId. Legacy rows (NULL link, or a disbursement predating the
      // stamp) fall back to the old path.
      val payments = paymentHistoryDao.getPaymentHistoriesForLoanSync(loan.id)
      val linkedTransactionIds =
        payments.mapNotNull { it.linkedTransactionId }
      if (linkedTransactionIds.isNotEmpty()) {
        // Chunk at 900 to stay well below SQLite's 999 bind parameter limit
        // on older Android platforms.
        linkedTransactionIds.chunked(SQLITE_IN_CHUNK_SIZE).forEach { chunk ->
          transactionLinkDao.deleteTransactionsByIds(chunk)
        }
      }
      // Delete the initial disbursement by stable key (loanId). This works
      // regardless of the loan's current tracked status because the stamp
      // was written at creation time. If it deletes nothing (legacy loan
      // created before the stamp), the field-matching fallback below must
      // still run for the disbursement leg when the loan was tracked.
      // Use OR of persisted and caller state: updateLoan never creates a
      // disbursement, so creation-time tracked status is unrecoverable after
      // a flip. OR favors cleanup of legacy disbursements (tracked->untracked)
      // over the rarer collateral risk (untracked->tracked with a colliding
      // unrelated row). Stamped modern rows never reach the fallback.
      val disbursementDeletedById =
        transactionLinkDao.deleteTransactionsByLoanId(loan.id) > 0
      val wasTracked = existing.tracked || loan.tracked
      // Issue #532: legacy (pre-#242, unstamped) disbursement rows keep their
      // creation-time date. A loan date edit makes the old date unrecoverable
      // here, so the field match below cannot find them after an edit. Only
      // the stamped row moves with the loan date (see updateLoan).
      val needsDisbursementFallback = wasTracked && !disbursementDeletedById
      val unlinkedPayments = payments.filter { it.linkedTransactionId == null }
      if (unlinkedPayments.isNotEmpty() || needsDisbursementFallback) {
        // Legacy fallback: match on personName + Loans category + amount + date.
        val loansCategoryId = categoryDao.getCategoryByKey(LoansCategoryExclusion.CATEGORY_KEY)?.id
        if (loansCategoryId != null) {
          deleteLegacyFallbackTransactions(
            loan = loan,
            existing = existing,
            unlinkedPayments = unlinkedPayments,
            loansCategoryId = loansCategoryId,
            deleteDisbursementLeg = needsDisbursementFallback
          )
        }
      }
      paymentHistoryDao.deletePaymentHistoryForLoan(loan.id)
      loanDao.deleteLoan(loan)
    }
  }

  private suspend fun deleteLegacyFallbackTransactions(
    loan: Loan,
    existing: Loan,
    unlinkedPayments: List<PaymentHistory>,
    loansCategoryId: Long,
    deleteDisbursementLeg: Boolean
  ) {
    val namesToClean =
      buildSet {
        add(existing.personName)
        add(loan.personName)
      }
    for (name in namesToClean) {
      unlinkedPayments.forEach { payment ->
        transactionLinkDao.deleteLoanPaymentTransaction(
          personName = name,
          categoryId = loansCategoryId,
          amount = payment.amount,
          date = payment.date
        )
      }
      if (deleteDisbursementLeg) {
        transactionLinkDao.deleteLoanPaymentTransaction(
          personName = name,
          categoryId = loansCategoryId,
          amount = existing.originalAmount,
          date = existing.date
        )
        if (loan.originalAmount != existing.originalAmount || loan.date != existing.date) {
          transactionLinkDao.deleteLoanPaymentTransaction(
            personName = name,
            categoryId = loansCategoryId,
            amount = loan.originalAmount,
            date = loan.date
          )
        }
      }
    }
  }

  override fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> =
    paymentHistoryDao.getPaymentHistoryForLoan(loanId)

  override suspend fun addPaymentToLoan(
    loanId: Long,
    amount: Long,
    notes: String,
    customDate: Long?
  ): Boolean {
    if (amount <= 0L) return false
    return database.withTransaction {
      val loan = loanDao.getLoanById(loanId) ?: return@withTransaction false
      // A settled loan must never accept further repayment: a positive
      // remainingAmount on a settled row is stale data, and paying it would
      // resurrect the loan by flipping isSettled back to false.
      if (loan.isSettled || loan.remainingAmount <= 0L) return@withTransaction false
      if (amount > loan.remainingAmount) return@withTransaction false
      val newRemaining = loan.remainingAmount - amount
      val isSettled = newRemaining == 0L
      val date = customDate ?: System.currentTimeMillis()
      // Issue #431: reject payments backdated before the loan creation date or
      // an already-recorded payment. They corrupt point-in-time balances.
      if (!isPaymentDateChronologicallyValid(loan.date, loanId, date)) return@withTransaction false
      // Phase 2 (DECISION 1): untracked loans only reduce the ledger balance
      // and record payment history. Zero transactions are posted — and the
      // Loans category is only required on the tracked path, so untracked
      // repayments keep working after the category was deleted (plan 011 D2).
      // For tracked loans, require the category up front so missing category
      // aborts the transaction cleanly before writing loan/payment mutations.
      val loansCategory =
        if (loan.tracked) {
          categoryDao.getCategoryByKey("Loans")
            ?: throw IllegalStateException(
              "Loans category is missing; cannot record the loan repayment transaction"
            )
        } else {
          null
        }
      val updatedLoan = loan.copy(remainingAmount = newRemaining, isSettled = isSettled)
      val payment = PaymentHistory(loanId = loanId, amount = amount, notes = notes, date = date)
      loanDao.updateLoan(updatedLoan)
      val paymentId = paymentHistoryDao.insertPayment(payment)
      if (loan.tracked && loansCategory != null) {
        val desc =
          if (loan.type == LoanType.CREDITOR) {
            "بازپرداخت بدهی به ${loan.personName} - $notes"
          } else {
            "دریافت بازپرداخت از ${loan.personName} - $notes"
          }
        val txId =
          transactionDao.insertTransaction(
            Transaction(
              type = if (loan.type == LoanType.CREDITOR) TransactionType.EXPENSE else TransactionType.INCOME,
              categoryId = loansCategory.id,
              amount = amount,
              description = desc,
              personName = loan.personName,
              personId = loan.personId,
              date = date,
              accountId = TrackedLedgerHelper.resolveAccountId(loan.tracked, loan.accountId)
            )
          )
        // Issue #242: stamp the payment with the transaction id so deleteLoan
        // can remove the exact row without field matching.
        paymentHistoryDao.updateLinkedTransactionId(paymentId, txId)
      }
      true
    }
  }

  /**
   * True when the new creation date keeps the payment history chronological.
   * Runs the existing Rust validate_loan_payment_date rule once per recorded
   * payment, treating each payment as a new payment against the new date: a
   * payment predating the new date fails the rule and rejects the edit.
   * Skips the check when Rust is unavailable, mirroring
   * isPaymentDateChronologicallyValid's fail-open behavior.
   */
  private suspend fun isLoanDateEditChronologicallyValid(
    loanId: Long,
    newDate: Long
  ): Boolean {
    if (!RustBridge.isAvailable) return true
    val existingPaymentDates = paymentHistoryDao.getPaymentHistoriesForLoanSync(loanId).map { it.date }
    return existingPaymentDates.all { RustBridge.validateLoanPaymentDateSync(newDate, emptyList(), it) }
  }

  /**
   * True when [date] keeps the payment history chronological: not before the
   * loan creation date and not before any already-recorded payment. Returns
   * true when Rust is unavailable, since validateBoolean falls back to false
   * then and rejecting every payment would be worse than skipping the check.
   */
  private suspend fun isPaymentDateChronologicallyValid(
    loanDate: Long,
    loanId: Long,
    date: Long
  ): Boolean {
    if (!RustBridge.isAvailable) return true
    val existingPaymentDates = paymentHistoryDao.getPaymentHistoriesForLoanSync(loanId).map { it.date }
    return RustBridge.validateLoanPaymentDateSync(loanDate, existingPaymentDates, date)
  }
}

// minSdk is 26, where SQLite raises the IN-clause bind limit to 32766, so the
// legacy 999 cap never applies on supported devices. Chunking at 900 stays
// conservatively below the old limit in case the floor is ever lowered.
private const val SQLITE_IN_CHUNK_SIZE = 900
