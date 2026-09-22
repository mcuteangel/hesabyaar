package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer
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
    customDate: Long? = null,
    personId: Long? = null
  ): Long {
    val resolvedPersonId = resolvePersonId(personName, personId)
    return repository.insertLoan(
      Loan(
        personName = personName,
        type = type,
        originalAmount = amount,
        remainingAmount = amount,
        description = description,
        date = customDate ?: System.currentTimeMillis(),
        personId = resolvedPersonId
      )
    )
  }

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
    customDate: Long? = null,
    personId: Long? = null
  ): Long {
    io.github.mojri.hesabyar.data.TrackedLedgerHelper
      .validateTrackedAccount(tracked, accountId)
    val resolvedPersonId = resolvePersonId(personName, personId)
    return repository.insertLoanWithInitial(
      Loan(
        personName = personName,
        type = type,
        originalAmount = amount,
        remainingAmount = amount,
        description = description,
        date = customDate ?: System.currentTimeMillis(),
        tracked = tracked,
        accountId = accountId,
        personId = resolvedPersonId
      ),
      recordInitial = recordInitial && tracked
    )
  }

  private suspend fun resolvePersonId(
    personName: String,
    explicitPersonId: Long?
  ): Long? {
    if (explicitPersonId != null) return explicitPersonId
    val display = PersonNameNormalizer.displayForm(personName)
    val key = PersonNameNormalizer.normalize(display)
    return if (key.isNotEmpty()) {
      repository.upsertPerson(Person(name = display, normalizedName = key)).id
    } else {
      null
    }
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
