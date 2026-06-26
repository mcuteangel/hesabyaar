package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.PaymentHistory
import kotlinx.coroutines.flow.Flow

class ManageLoanUseCase(
    private val repository: HesabyarRepositoryInterface
) {
    val allLoans: Flow<List<Loan>> = repository.allLoans

    suspend fun addLoan(
        personName: String,
        type: String,
        amount: Long,
        description: String,
        customDate: Long? = null
    ): Long = repository.insertLoan(
        Loan(
            personName = personName,
            type = type,
            originalAmount = amount,
            remainingAmount = amount,
            description = description,
            date = customDate ?: System.currentTimeMillis()
        )
    )

    suspend fun makeRepayment(loanId: Long, amount: Long, notes: String, customDate: Long? = null): Boolean =
        repository.addPaymentToLoan(loanId, amount, notes, customDate)

    fun getPaymentHistory(loanId: Long): Flow<List<PaymentHistory>> =
        repository.getPaymentHistoryForLoan(loanId)

    suspend fun updateLoan(loan: Loan) = repository.updateLoan(loan)

    suspend fun deleteLoan(loan: Loan) = repository.deleteLoan(loan)
}
