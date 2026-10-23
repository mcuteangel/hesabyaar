package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion

/**
 * Issue #532: moves the disbursement transaction when a loan date edit is
 * accepted, before the loan row itself moves. Extracted from LoanDelegate to
 * satisfy detekt's TooManyFunctions limit.
 */
internal class LoanDisbursementSync(
  private val transactionLinkDao: TransactionLinkDao,
  private val categoryDao: CategoryDao
) {
  /**
   * Move the disbursement transaction when a loan date edit is accepted. The
   * fast path moves stamped rows that are in sync with the loan date — those
   * are certainly the disbursement (or its exact duplicates, which move
   * together consistently). The fallback heals a single diverged stamped
   * row, or moves a single unambiguous legacy (pre-#242, unstamped) row.
   * Diverged rows in a multi-row set are never touched: they are ambiguous
   * (duplicate? corrupt attachment?) and guessing would risk retiming the
   * wrong row. Loans created untracked have no disbursement row; a loan
   * flipped from tracked to untracked keeps its stamped row, which still
   * moves with the loan date.
   */
  suspend fun syncDisbursementDate(
    persisted: Loan,
    updated: Loan
  ) {
    val targetId = findDisbursementIdToMove(persisted, updated)
    if (targetId != null) {
      transactionLinkDao.updateTransactionById(targetId, updated.date)
    }
  }

  /**
   * Issue #532: stamp the legacy (unstamped) disbursement with the loan id
   * when a name/amount edit would otherwise orphan it from future date
   * syncs. The row is found by the persisted (pre-edit) fields, which still
   * identify it; only stamps on an exactly-one match.
   */
  suspend fun stampLegacyDisbursement(
    persisted: Loan,
    updated: Loan
  ) {
    findAndStampLegacyDisbursement(persisted, updated)
  }

  /**
   * Issue #532: id of the disbursement row that may move to the new date, or
   * null when nothing may move.
   */
  private suspend fun findDisbursementIdToMove(
    persisted: Loan,
    updated: Loan
  ): Long? {
    if (transactionLinkDao.updateDisbursementDateByLoanId(updated.id, persisted.date, updated.date) > 0) {
      return null
    }
    return findFallbackDisbursementId(persisted, updated)
  }

  /**
   * Issue #532: id of the row to move when the fast path moved nothing, or
   * null. A single stamped row is healed by id; several stamped rows mean a
   * corrupt restore and are never touched.
   */
  private suspend fun findFallbackDisbursementId(
    persisted: Loan,
    updated: Loan
  ): Long? {
    val stampedIds = transactionLinkDao.findTransactionIdsByLoanId(updated.id)
    return if (stampedIds.size == 1) {
      stampedIds[0]
    } else if (stampedIds.isEmpty()) {
      findAndStampLegacyDisbursement(persisted, updated)
    } else {
      null
    }
  }

  /**
   * Issue #532: find the legacy (pre-#242, unstamped) disbursement by the
   * persisted fields and stamp it with the loan id, returning its id — or
   * null when no unambiguous row exists. Stamping lets later edits and
   * deletes use the stable loanId key instead of fragile field matching.
   * The date is left at the persisted value here; callers move it when the
   * edit changed the date.
   */
  private suspend fun findAndStampLegacyDisbursement(
    persisted: Loan,
    updated: Loan
  ): Long? {
    val id = findSingleLegacyDisbursementId(persisted, updated) ?: return null
    transactionLinkDao.updateTransactionById(id, persisted.date, updated.id)
    return id
  }

  /**
   * Issue #532: id of the legacy (pre-#242, unstamped) disbursement to move,
   * or null. Only the persisted person name identifies the row: the
   * transaction keeps the name it was created with (a loan edit never
   * rewrites it), so including the updated name could only match unrelated
   * rows. The row moves only when exactly one row matches.
   */
  private suspend fun findSingleLegacyDisbursementId(
    persisted: Loan,
    updated: Loan
  ): Long? {
    val loansCategoryId =
      if (persisted.tracked || updated.tracked) {
        categoryDao.getCategoryByKey(LoansCategoryExclusion.CATEGORY_KEY)?.id
      } else {
        null
      }
    return if (loansCategoryId != null) {
      val ids =
        transactionLinkDao.findLegacyDisbursementIds(
          personNames = setOf(persisted.personName),
          categoryId = loansCategoryId,
          amount = persisted.originalAmount,
          oldDate = persisted.date
        )
      ids.singleOrNull()
    } else {
      null
    }
  }
}
