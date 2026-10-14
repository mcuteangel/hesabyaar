package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.data.sanitizeLoanLinksForReplace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #430: REPLACE keeps source ids, so a link whose target is missing
 * from the same backup must be dropped — SQLite reuses rowids and a later,
 * unrelated row could otherwise inherit the dangling link.
 */
class BackupLoanLinksSanitizeTest {
  private fun tx(
    id: Long,
    loanId: Long?
  ) = Transaction(
    id = id,
    type = TransactionType.EXPENSE,
    categoryId = 1L,
    amount = 1_000L,
    description = "tx$id",
    loanId = loanId
  )

  private val loan =
    Loan(
      id = 7L,
      personName = "Ali",
      type = LoanType.DEBTOR,
      originalAmount = 1_000L,
      remainingAmount = 1_000L,
      description = "loan"
    )

  @Test
  fun keepsLinksWhoseTargetsAreInTheBackup() {
    val result =
      sanitizeLoanLinksForReplace(
        transactions = listOf(tx(1L, loanId = 7L), tx(2L, loanId = null)),
        loans = listOf(loan),
        paymentHistories = listOf(PaymentHistory(id = 3L, loanId = 7L, amount = 1L, linkedTransactionId = 2L))
      )
    assertEquals(7L, result.transactions[0].loanId)
    assertEquals(2L, result.paymentHistories[0].linkedTransactionId)
  }

  @Test
  fun dropsDanglingLinks() {
    val result =
      sanitizeLoanLinksForReplace(
        transactions = listOf(tx(1L, loanId = 99L)),
        loans = listOf(loan),
        paymentHistories = listOf(PaymentHistory(id = 3L, loanId = 7L, amount = 1L, linkedTransactionId = 42L))
      )
    assertNull(result.transactions[0].loanId)
    assertNull(result.paymentHistories[0].linkedTransactionId)
  }
}
