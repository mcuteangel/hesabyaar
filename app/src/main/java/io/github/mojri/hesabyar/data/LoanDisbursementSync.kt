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
   * fast path moves stamped rows that are in sync with the loan date; the
   * fallback heals a single diverged stamped row, or moves a single
   * unambiguous legacy (pre-#242, unstamped) row. Corrupt duplicates are
   * never moved. Loans created untracked have no disbursement row; a loan
   * flipped from tracked to untracked keeps its stamped row, which still
   * moves with the loan date.
   */
  suspend fun syncDisbursementDate(
    persisted: Loan,
    updated: Loan
  ) {
    val targetId = findDisbursementIdToMove(persisted, updated)
    if (targetId != null) {
      transactionLinkDao.updateTransactionDateById(targetId, updated.date)
    }
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
      findSingleLegacyDisbursementId(persisted, updated)
    } else {
      null
    }
  }

  /**
   * Issue #532: id of the legacy (pre-#242, unstamped) disbursement to move,
   * or null. The persisted fields identify the row but carry no loan
   * identity, so the row moves only when exactly one row matches.
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
          personNames = setOf(persisted.personName, updated.personName),
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
