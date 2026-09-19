package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import kotlinx.coroutines.flow.Flow

class ManageLoanUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val allLoans: Flow<List<Loan>> = repository.allLoans

  suspend fun addLoan(
    personName: String,
    type: LoanType,
    amount: Long,
    description: String,
    customDate: Long? = null
  ): Long =
    repository.insertLoan(
      Loan(
        personName = personName,
        type = type,
        originalAmount = amount,
        remainingAmount = amount,
        description = description,
        date = customDate ?: System.currentTimeMillis()
      )
    )

  /**
   * Phase 2 atomic create: the loan row and its optional initial-leg
   * transaction are written in one withTransaction (see LoanDelegate).
   * Three-state UI maps to (tracked, recordInitial):
   * recordInitial=true posts the initial INCOME/EXPENSE; false skips it
   * (already recorded manually); tracked=false is ledger-only.
   */
  suspend fun addTrackedLoan(
    personName: String,
    type: LoanType,
    amount: Long,
    description: String,
    tracked: Boolean,
    accountId: Long? = null,
    recordInitial: Boolean = true,
    customDate: Long? = null
  ): Long {
    io.github.mojri.hesabyar.data.TrackedLedgerHelper
      .validateTrackedAccount(tracked, accountId)
    return repository.insertLoanWithInitial(
      Loan(
        personName = personName,
        type = type,
        originalAmount = amount,
        remainingAmount = amount,
        description = description,
        date = customDate ?: System.currentTimeMillis(),
        tracked = tracked,
        accountId = accountId
      ),
      recordInitial = recordInitial && tracked
    )
  }

  suspend fun makeRepayment(
    loanId: Long,
    amount: Long,
    notes: String,
    customDate: Long? = null
  ): Boolean = repository.addPaymentToLoan(loanId, amount, notes, customDate)

  fun getPaymentHistory(loanId: Long): Flow<List<PaymentHistory>> = repository.getPaymentHistoryForLoan(loanId)

  suspend fun updateLoan(loan: Loan) = repository.updateLoan(loan)

  suspend fun deleteLoan(loan: Loan) = repository.deleteLoan(loan)
}
