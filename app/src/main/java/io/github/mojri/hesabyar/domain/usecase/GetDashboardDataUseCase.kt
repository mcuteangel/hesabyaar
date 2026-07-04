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
    val (totalIncome, totalExpense, monthlyIncome, monthlyExpense) = aggregateTransactions(transactions)
    val (debtorsTotal, creditorsTotal) = aggregateLoans(loans)
    val upcomingIns = installments.filter { !it.isPaid }.sortedBy { it.dueDate }

    val balance = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) balance.toDouble() / totalIncome.toDouble() else 0.0

    val monthlyInstallmentTotal = upcomingIns.sumOf { it.amount }
    val debtToIncomeRatio =
      if (monthlyIncome > 0) monthlyInstallmentTotal.toDouble() / monthlyIncome.toDouble() else 0.0

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

  private fun aggregateTransactions(transactions: List<Transaction>): Quadruple {
    var totalIncome = 0L
    var totalExpense = 0L
    var monthlyIncome = 0L
    var monthlyExpense = 0L
    val oneMonthAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)

    transactions.forEach {
      if (it.type == "INCOME") {
        totalIncome += it.amount
        if (it.date >= oneMonthAgo) monthlyIncome += it.amount
      } else {
        totalExpense += it.amount
        if (it.date >= oneMonthAgo) monthlyExpense += it.amount
      }
    }
    return Quadruple(totalIncome, totalExpense, monthlyIncome, monthlyExpense)
  }

  private fun aggregateLoans(loans: List<Loan>): Pair<Long, Long> {
    var debtorsTotal = 0L
    var creditorsTotal = 0L
    loans.filter { !it.isSettled }.forEach {
      if (it.type == "DEBTOR") {
        debtorsTotal += it.remainingAmount
      } else {
        creditorsTotal += it.remainingAmount
      }
    }
    return debtorsTotal to creditorsTotal
  }

  private data class Quadruple(
    val a: Long,
    val b: Long,
    val c: Long,
    val d: Long
  )
}
