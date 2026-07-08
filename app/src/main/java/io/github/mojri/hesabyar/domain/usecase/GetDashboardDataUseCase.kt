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
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.computeDashboardDataSync(
        io.github.mojri.hesabyar.rust.RustMappers.mapTransactions(transactions),
        io.github.mojri.hesabyar.rust.RustMappers.mapLoans(loans),
        io.github.mojri.hesabyar.rust.RustMappers.mapInstallments(installments)
      )

    if (rustResult != null) {
      return io.github.mojri.hesabyar.rust.RustMappers
        .mapDashboardData(rustResult, installments)
    }

    // Kotlin fallback when Rust FFI is unavailable or fails
    val now = System.currentTimeMillis()
    val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000

    val monthlyTx = transactions.filter { it.date >= thirtyDaysAgo }
    val monthlyIncome = monthlyTx.filter { it.type == "INCOME" }.sumOf { it.amount }
    val monthlyExpenses = monthlyTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }

    val unsettledLoans = loans.filter { !it.isSettled }
    val debtors = unsettledLoans.filter { it.type == "DEBTOR" }.sumOf { it.remainingAmount }
    val creditors = unsettledLoans.filter { it.type == "CREDITOR" }.sumOf { it.remainingAmount }

    val currentBalance = monthlyIncome - monthlyExpenses
    val savingsRate = if (monthlyIncome > 0) (monthlyIncome - monthlyExpenses).toDouble() / monthlyIncome else 0.0
    val monthlyDebt = unsettledLoans
      .filter { it.type == "CREDITOR" }
      .sumOf { it.remainingAmount / 12 }
    val debtToIncome = if (monthlyIncome > 0) monthlyDebt.toDouble() / monthlyIncome else 0.0

    val upcomingIns = installments.filter { !it.isPaid }.sortedBy { it.dueDate }

    return DashboardData(
      currentBalance = currentBalance,
      monthlyExpenses = monthlyExpenses,
      monthlyIncome = monthlyIncome,
      debtorsTotal = debtors,
      creditorsTotal = creditors,
      upcomingInstallments = upcomingIns,
      savingsRate = savingsRate,
      debtToIncomeRatio = debtToIncome
    )
  }
}
