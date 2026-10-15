package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.data.sanitizeBackupLoanLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #430: a restored link must be unambiguous inside its own backup.
 * A dangling, disbursement-targeted or shared link is dropped to null so
 * deleteLoan never removes a row by an id that belongs to something else.
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
      sanitizeBackupLoanLinks(
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
      sanitizeBackupLoanLinks(
        transactions = listOf(tx(1L, loanId = 99L)),
        loans = listOf(loan),
        paymentHistories = listOf(PaymentHistory(id = 3L, loanId = 7L, amount = 1L, linkedTransactionId = 42L))
      )
    assertNull(result.transactions[0].loanId)
    assertNull(result.paymentHistories[0].linkedTransactionId)
  }

  @Test
  fun dropsPaymentLinkThatTargetsALoanDisbursement() {
    val result =
      sanitizeBackupLoanLinks(
        transactions = listOf(tx(1L, loanId = 7L)),
        loans = listOf(loan),
        paymentHistories = listOf(PaymentHistory(id = 3L, loanId = 7L, amount = 1L, linkedTransactionId = 1L))
      )
    assertEquals(7L, result.transactions[0].loanId)
    assertNull(result.paymentHistories[0].linkedTransactionId)
  }

  @Test
  fun dropsPaymentLinksSharedByTwoPayments() {
    val result =
      sanitizeBackupLoanLinks(
        transactions = listOf(tx(2L, loanId = null)),
        loans = listOf(loan),
        paymentHistories =
          listOf(
            PaymentHistory(id = 3L, loanId = 7L, amount = 1L, linkedTransactionId = 2L),
            PaymentHistory(id = 4L, loanId = 7L, amount = 1L, linkedTransactionId = 2L)
          )
      )
    assertNull(result.paymentHistories[0].linkedTransactionId)
    assertNull(result.paymentHistories[1].linkedTransactionId)
  }

  @Test
  fun ignoresNonPositiveIdsAsLinkTargets() {
    val result =
      sanitizeBackupLoanLinks(
        transactions = listOf(tx(0L, loanId = 0L)),
        loans = listOf(loan.copy(id = 0L)),
        paymentHistories = listOf(PaymentHistory(id = 3L, loanId = 0L, amount = 1L, linkedTransactionId = 0L))
      )
    assertNull(result.transactions[0].loanId)
    assertNull(result.paymentHistories[0].linkedTransactionId)
  }

  @Test
  fun dropsPaymentLinkWhenTransactionIdIsDuplicated() {
    val result =
      sanitizeBackupLoanLinks(
        transactions = listOf(tx(2L, loanId = null), tx(2L, loanId = null)),
        loans = listOf(loan),
        paymentHistories = listOf(PaymentHistory(id = 3L, loanId = 7L, amount = 1L, linkedTransactionId = 2L))
      )
    assertNull(result.paymentHistories[0].linkedTransactionId)
  }
}
