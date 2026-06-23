package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class InstallmentViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository: HesabyarRepositoryInterface = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao()
    )

    val installments: StateFlow<List<Installment>> = repository.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addInstallment(title: String, amount: Long, dueDate: Long, reminderEnabled: Boolean, notes: String) {
        viewModelScope.launch {
            repository.insertInstallment(Installment(
                title = title,
                amount = amount,
                dueDate = dueDate,
                reminderEnabled = reminderEnabled,
                notes = notes
            ))
        }
    }

    fun toggleInstallmentPaid(installment: Installment) {
        viewModelScope.launch {
            repository.updateInstallment(installment.copy(isPaid = !installment.isPaid))
        }
    }

    fun deleteInstallment(installment: Installment) {
        viewModelScope.launch {
            repository.deleteInstallment(installment)
        }
    }
}
