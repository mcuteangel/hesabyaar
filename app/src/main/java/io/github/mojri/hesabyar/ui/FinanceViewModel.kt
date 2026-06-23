package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = HesabyarRepository(
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

    val dashboardState = combine(transactions, loans, installments) { trans, loanList, instList ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardData())

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

    fun makeRepayment(loanId: Long, amount: Long, notes: String) {
        viewModelScope.launch {
            repository.addPaymentToLoan(loanId, amount, notes)
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
