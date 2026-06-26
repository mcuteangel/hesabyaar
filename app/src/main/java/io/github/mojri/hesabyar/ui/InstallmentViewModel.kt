package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallmentViewModel @Inject constructor(
    private val manageInstallmentUseCase: ManageInstallmentUseCase
) : ViewModel() {

    val installments: StateFlow<List<Installment>> = manageInstallmentUseCase.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addInstallment(title: String, amount: Long, dueDate: Long, reminderEnabled: Boolean, notes: String) {
        viewModelScope.launch {
            manageInstallmentUseCase.addInstallment(title, amount, dueDate, reminderEnabled, notes)
        }
    }

    fun toggleInstallmentPaid(installment: Installment) {
        viewModelScope.launch {
            manageInstallmentUseCase.toggleInstallmentPaid(installment)
        }
    }

    fun deleteInstallment(installment: Installment) {
        viewModelScope.launch {
            manageInstallmentUseCase.deleteInstallment(installment)
        }
    }
}
