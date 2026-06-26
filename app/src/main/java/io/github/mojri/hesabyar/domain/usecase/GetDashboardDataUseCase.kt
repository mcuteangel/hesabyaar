package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.DashboardData
import kotlinx.coroutines.flow.Flow

class GetDashboardDataUseCase(
    private val repository: HesabyarRepositoryInterface
) {
    val transactions: Flow<List<Transaction>> = repository.allTransactions
    val loans: Flow<List<Loan>> = repository.allLoans
    val installments: Flow<List<Installment>> = repository.allInstallments
    val categories: Flow<List<Category>> = repository.allCategories

    fun computeDashboardData(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>
    ): DashboardData {
        var totalIncome = 0L
        var totalExpense = 0L
        var monthlyIncome = 0L
        var monthlyExpense = 0L

        val now = System.currentTimeMillis()
        val oneMonthAgo = now - (30L * 24L * 60L * 60L * 1000L)

        transactions.forEach {
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
        loans.filter { !it.isSettled }.forEach {
            if (it.type == "DEBTOR") {
                debtorsTotal += it.remainingAmount
            } else {
                creditorsTotal += it.remainingAmount
            }
        }

        val upcomingIns = installments.filter { !it.isPaid }.sortedBy { it.dueDate }

        val balance = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) {
            balance.toDouble() / totalIncome.toDouble()
        } else 0.0

        val monthlyInstallmentTotal = upcomingIns.sumOf { it.amount }
        val debtToIncomeRatio = if (monthlyIncome > 0) {
            monthlyInstallmentTotal.toDouble() / monthlyIncome.toDouble()
        } else 0.0

        return DashboardData(
            currentBalance = balance,
            monthlyExpenses = monthlyExpense,
            monthlyIncome = monthlyIncome,
            debtorsTotal = debtorsTotal,
            creditorsTotal = creditorsTotal,
            upcomingInstallments = upcomingIns,
            savingsRate = savingsRate,
            debtToIncomeRatio = debtToIncomeRatio
        )
    }
}
