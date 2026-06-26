package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.domain.usecase.ManageTransactionUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val manageTransactionUseCase: ManageTransactionUseCase
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> = manageTransactionUseCase.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTransaction(type: String, categoryId: Long, amount: Long, description: String, personName: String? = null, customDate: Long? = null) {
        viewModelScope.launch {
            manageTransactionUseCase.addTransaction(type, categoryId, amount, description, personName, customDate)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            manageTransactionUseCase.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            manageTransactionUseCase.deleteTransaction(transaction)
        }
    }
}
