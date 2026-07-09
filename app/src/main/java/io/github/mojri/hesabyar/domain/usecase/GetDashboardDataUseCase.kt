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
    return computeFallbackDashboardData(transactions, loans, installments)
  }

  /** True when every field is at its zero/default, i.e. the Rust result is a
   *  blank placeholder rather than a real computation. */
  private fun io.github.mojri.hesabyar.rust.DashboardData.isBlank(): Boolean =
    currentBalance == 0L &&
      monthlyExpenses == 0L &&
      monthlyIncome == 0L &&
      debtorsTotal == 0L &&
      creditorsTotal == 0L &&
      savingsRate == 0.0 &&
      debtToIncomeRatio == 0.0

  companion object {
    /** Kotlin-only dashboard computation.  Extracted so unit tests can verify
     *  the fallback logic without requiring the Rust native library. */
    internal fun computeFallbackDashboardData(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>
    ): DashboardData {
      val now = System.currentTimeMillis()

      // Get current Jalali month boundaries
      val jalaliDate =
        io.github.mojri.hesabyar.ui.JalaliCalendarHelper
          .gregorianToJalali(now)
      val jalaliMonthStart =
        io.github.mojri.hesabyar.ui.JalaliCalendarHelper
          .jalaliToGregorian(jalaliDate.year, jalaliDate.month, 1)
          ?.timeInMillis ?: (now - 30L * 24 * 60 * 60 * 1000)
      val nextMonth = if (jalaliDate.month == 12) 1 else jalaliDate.month + 1
      val nextMonthYear = if (jalaliDate.month == 12) jalaliDate.year + 1 else jalaliDate.year
      val jalaliMonthEnd =
        io.github.mojri.hesabyar.ui.JalaliCalendarHelper
          .jalaliToGregorian(nextMonthYear, nextMonth, 1)
          ?.timeInMillis ?: now

      val monthlyTx = transactions.filter { it.date >= jalaliMonthStart && it.date < jalaliMonthEnd }
      val monthlyIncome = monthlyTx.filter { it.type == "INCOME" }.sumOf { it.amount }
      val monthlyExpenses = monthlyTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }

      val unsettledLoans = loans.filter { !it.isSettled }
      val debtors = unsettledLoans.filter { it.type == "DEBTOR" }.sumOf { it.remainingAmount }
      val creditors = unsettledLoans.filter { it.type == "CREDITOR" }.sumOf { it.remainingAmount }

      // currentBalance from all transactions (lifetime), not just the filtered month.
      val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
      val totalExpenses = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
      val currentBalance = totalIncome - totalExpenses

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
  }
}
