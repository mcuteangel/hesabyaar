package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository: HesabyarRepositoryInterface = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao()
    )

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTransaction(type: String, categoryId: Long, amount: Long, description: String, personName: String? = null, customDate: Long? = null) {
        viewModelScope.launch {
            repository.insertTransaction(Transaction(
                type = type,
                categoryId = categoryId,
                amount = amount,
                description = description,
                personName = personName,
                date = customDate ?: System.currentTimeMillis()
            ))
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }
}
