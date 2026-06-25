package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LoanViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository: HesabyarRepositoryInterface = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao()
    )

    val loans: StateFlow<List<Loan>> = repository.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLoan(personName: String, type: String, amount: Long, description: String, customDate: Long? = null) {
        viewModelScope.launch {
            repository.insertLoan(Loan(
                personName = personName,
                type = type,
                originalAmount = amount,
                remainingAmount = amount,
                description = description,
                date = customDate ?: System.currentTimeMillis()
            ))
        }
    }

    fun makeRepayment(loanId: Long, amount: Long, notes: String, customDate: Long? = null) {
        viewModelScope.launch {
            repository.addPaymentToLoan(loanId, amount, notes, customDate)
        }
    }

    fun getPaymentHistory(loanId: Long): Flow<List<PaymentHistory>> {
        return repository.getPaymentHistoryForLoan(loanId)
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch {
            repository.deleteLoan(loan)
        }
    }
}
