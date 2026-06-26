package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoanViewModel @Inject constructor(
    private val manageLoanUseCase: ManageLoanUseCase
) : ViewModel() {

    val loans: StateFlow<List<Loan>> = manageLoanUseCase.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLoan(personName: String, type: String, amount: Long, description: String, customDate: Long? = null) {
        viewModelScope.launch {
            manageLoanUseCase.addLoan(personName, type, amount, description, customDate)
        }
    }

    fun makeRepayment(loanId: Long, amount: Long, notes: String, customDate: Long? = null) {
        viewModelScope.launch {
            manageLoanUseCase.makeRepayment(loanId, amount, notes, customDate)
        }
    }

    fun getPaymentHistory(loanId: Long): Flow<List<PaymentHistory>> {
        return manageLoanUseCase.getPaymentHistory(loanId)
    }

    fun updateLoan(loan: Loan) {
        viewModelScope.launch {
            manageLoanUseCase.updateLoan(loan)
        }
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch {
            manageLoanUseCase.deleteLoan(loan)
        }
    }
}
