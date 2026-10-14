package io.github.mojri.hesabyar.data

/**
 * Issue #430: keeps the stable loan links from issue #242 consistent when a
 * backup is restored.
 *
 * REPLACE keeps source ids, so a link survives as-is, but only while its
 * target is in the same backup. A dangling id is dropped because SQLite
 * reuses rowids: a later, unrelated row could take that id and deleteLoan
 * would then remove it.
 */
internal data class SanitizedLoanLinks(
  val transactions: List<Transaction>,
  val paymentHistories: List<PaymentHistory>
)

internal fun sanitizeLoanLinksForReplace(
  transactions: List<Transaction>,
  loans: List<Loan>,
  paymentHistories: List<PaymentHistory>
): SanitizedLoanLinks {
  val loanIds = loans.mapTo(HashSet()) { it.id }
  val transactionIds = transactions.mapTo(HashSet()) { it.id }
  return SanitizedLoanLinks(
    transactions =
      transactions.map { tx ->
        if (tx.loanId != null && tx.loanId !in loanIds) tx.copy(loanId = null) else tx
      },
    paymentHistories =
      paymentHistories.map { p ->
        val link = p.linkedTransactionId
        if (link != null && link !in transactionIds) p.copy(linkedTransactionId = null) else p
      }
  )
}
