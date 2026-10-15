package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.core.AppLogger

/**
 * Issue #430: keeps the stable loan links from issue #242 consistent when a
 * backup is restored. Runs on every restore path (REPLACE, MERGE and the
 * raw-list importBackup) before anything is inserted.
 *
 * A link survives only when it is unambiguous inside the same backup:
 *  - `Transaction.loanId` must name a loan in the backup;
 *  - `PaymentHistory.linkedTransactionId` must name a transaction in the
 *    backup that is not a loan disbursement (`loanId == null`) and that no
 *    other payment claims.
 *
 * Anything else is dropped to null, so deleteLoan uses its legacy matching
 * for that row instead of deleting a row by id that may belong to something
 * else. REPLACE keeps source ids, and SQLite reuses rowids, so a dangling id
 * could otherwise land on an unrelated row created later.
 */
internal data class SanitizedLoanLinks(
  val transactions: List<Transaction>,
  val paymentHistories: List<PaymentHistory>
)

private const val TAG = "HesabyarRepository"

internal fun sanitizeBackupLoanLinks(
  transactions: List<Transaction>,
  loans: List<Loan>,
  paymentHistories: List<PaymentHistory>
): SanitizedLoanLinks {
  val loanIds = loans.filter { it.id > 0L }.mapTo(HashSet()) { it.id }
  val sanitizedTransactions =
    transactions.map { tx ->
      val loanId = tx.loanId
      if (loanId != null && loanId !in loanIds) {
        AppLogger.w(TAG, "restore: dropping loanId=$loanId of transaction=${tx.id}: loan not in backup")
        tx.copy(loanId = null)
      } else {
        tx
      }
    }
  // Only plain ledger rows can be a repayment's generated transaction.
  val linkableTransactionIds =
    sanitizedTransactions
      .filter { it.id > 0L && it.loanId == null }
      .mapTo(HashSet()) { it.id }
  val claimCounts = paymentHistories.mapNotNull { it.linkedTransactionId }.groupingBy { it }.eachCount()
  val sanitizedPayments =
    paymentHistories.map { p ->
      val link = p.linkedTransactionId
      val valid = link == null || (link in linkableTransactionIds && claimCounts[link] == 1)
      if (valid) {
        p
      } else {
        AppLogger.w(
          TAG,
          "restore: dropping linkedTransactionId=$link of payment=${p.id} (loanId=${p.loanId}): " +
            "target missing, a loan disbursement, or shared by another payment"
        )
        p.copy(linkedTransactionId = null)
      }
    }
  return SanitizedLoanLinks(sanitizedTransactions, sanitizedPayments)
}
