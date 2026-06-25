package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
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

    val loans: StateFlow<List<Loan>> = repository.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments: StateFlow<List<Installment>> = repository.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardState: StateFlow<DashboardData> = combine(transactions, loans, installments) { trans, loanList, instList ->
        var totalIncome = 0L
        var totalExpense = 0L
        var monthlyIncome = 0L
        var monthlyExpense = 0L

        val now = System.currentTimeMillis()
        val oneMonthAgo = now - (30L * 24L * 60L * 60L * 1000L)

        trans.forEach {
            if (it.type == "INCOME") {
                totalIncome += it.amount
                if (it.date >= oneMonthAgo) monthlyIncome += it.amount
            } else {
                totalExpense += it.amount
                if (it.date >= oneMonthAgo) monthlyExpense += it.amount
            }
        }

        var debtorsTotal = 0L
        var creditorsTotal = 0L
        loanList.filter { !it.isSettled }.forEach {
            if (it.type == "DEBTOR") {
                debtorsTotal += it.remainingAmount
            } else {
                creditorsTotal += it.remainingAmount
            }
        }

        val upcomingIns = instList.filter { !it.isPaid }.sortedBy { it.dueDate }

        DashboardData(
            currentBalance = totalIncome - totalExpense,
            monthlyExpenses = monthlyExpense,
            monthlyIncome = monthlyIncome,
            debtorsTotal = debtorsTotal,
            creditorsTotal = creditorsTotal,
            upcomingInstallments = upcomingIns
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardData())
}
