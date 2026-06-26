package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase
) : ViewModel() {

    val transactions = getDashboardDataUseCase.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans = getDashboardDataUseCase.loans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments = getDashboardDataUseCase.installments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = getDashboardDataUseCase.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardState: StateFlow<DashboardData> = combine(transactions, loans, installments) { trans, loanList, instList ->
        getDashboardDataUseCase.computeDashboardData(trans, loanList, instList)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardData())
}
