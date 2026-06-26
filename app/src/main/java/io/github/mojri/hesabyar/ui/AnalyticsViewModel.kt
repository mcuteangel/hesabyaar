package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.domain.usecase.GetAnalyticsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: HesabyarRepositoryInterface,
    private val getAnalyticsUseCase: GetAnalyticsUseCase,
    private val manageLoanUseCase: ManageLoanUseCase,
    private val manageInstallmentUseCase: ManageInstallmentUseCase
) : ViewModel() {

    private val _selectedJalaliMonth = MutableStateFlow<Pair<Int, Int>?>(null)
    val selectedJalaliMonth: StateFlow<Pair<Int, Int>?> = _selectedJalaliMonth.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<Loan>> = manageLoanUseCase.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments: StateFlow<List<Installment>> = manageInstallmentUseCase.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val analyticsData: StateFlow<AnalyticsData> = combine(
        transactions, loans, installments, categories
    ) { trans, loanList, instList, catList ->
        getAnalyticsUseCase.computeAnalytics(trans, loanList, instList, catList)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsData())

    fun setSelectedJalaliMonth(year: Int?, month: Int?) {
        _selectedJalaliMonth.value = if (year != null && month != null) year to month else null
    }
}
