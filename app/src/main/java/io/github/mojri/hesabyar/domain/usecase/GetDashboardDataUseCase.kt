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
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments)
      )

    // Use the Rust result unless it failed (null) or came back as an all-zero
    // placeholder while real data exists. In those cases fall back to a local
    // computation so the UI never shows misleading blank zeros.
    val hasData =
      transactions.isNotEmpty() || loans.isNotEmpty() || installments.isNotEmpty()
    if (rustResult != null && !(hasData && rustResult.isBlank())) {
      return io.github.mojri.hesabyar.rust.RustMappers
        .mapDashboardData(rustResult, installments)
    }

    // Kotlin fallback when Rust FFI is unavailable, panicked, or returned
    // empty/invalid data. Computed directly from the local DB lists.
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
    val monthlyDebt =
      unsettledLoans
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

  /** True when every field is at its zero/default, i.e. the Rust result is a
   *  blank placeholder rather than a real computation. */
  private fun DashboardData.isBlank(): Boolean =
    currentBalance == 0L &&
      monthlyExpenses == 0L &&
      monthlyIncome == 0L &&
      debtorsTotal == 0L &&
      creditorsTotal == 0L &&
      upcomingInstallments.isEmpty() &&
      savingsRate == 0.0 &&
      debtToIncomeRatio == 0.0
}
