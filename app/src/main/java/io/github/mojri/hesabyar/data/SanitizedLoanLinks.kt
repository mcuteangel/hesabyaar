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
  // A repeated transaction id is ambiguous, so no row sharing it is linkable.
  val seenTransactionIds = HashSet<Long>()
  val duplicateTransactionIds = HashSet<Long>()
  for (tx in sanitizedTransactions) {
    if (tx.id > 0L && !seenTransactionIds.add(tx.id)) duplicateTransactionIds += tx.id
  }
  if (duplicateTransactionIds.isNotEmpty()) {
    AppLogger.w(TAG, "restore: duplicate transaction ids are not linkable: $duplicateTransactionIds")
  }
  val linkableTransactions =
    sanitizedTransactions
      .filter { it.id > 0L && it.loanId == null && it.id !in duplicateTransactionIds }
      .associateBy { it.id }
  val claimCounts = paymentHistories.mapNotNull { it.linkedTransactionId }.groupingBy { it }.eachCount()
  val sanitizedPayments =
    paymentHistories.map { p ->
      val link = p.linkedTransactionId
      val target = link?.let { linkableTransactions[it] }
      // A generated repayment copies its payment's amount at creation and payments are never
      // edited, so an amount mismatch means the link does not point at this payment's own row.
      val valid = link == null || target != null && claimCounts[link] == 1 && target.amount == p.amount
      if (valid) {
        p
      } else {
        AppLogger.w(
          TAG,
          "restore: dropping linkedTransactionId=$link of payment=${p.id} (loanId=${p.loanId}): " +
            "target missing, a loan disbursement, a duplicated id, an amount mismatch, " +
            "or shared by another payment"
        )
        p.copy(linkedTransactionId = null)
      }
    }
  return SanitizedLoanLinks(sanitizedTransactions, sanitizedPayments)
}
