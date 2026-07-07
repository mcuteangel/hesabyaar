package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.ui.AnalyticsData as KAnalyticsData
import io.github.mojri.hesabyar.ui.CategoryBreakdown as KCategoryBreakdown
import io.github.mojri.hesabyar.ui.DashboardData as KDashboardData
import io.github.mojri.hesabyar.ui.DebtSummary as KDebtSummary
import io.github.mojri.hesabyar.ui.InstallmentProgress as KInstallmentProgress
import io.github.mojri.hesabyar.ui.MonthlyData as KMonthlyData

/**
 * Mappers between Rust-generated UniFFI types and Kotlin UI types.
 */
object RustMappers {
  fun mapDashboardData(
    rust: DashboardData,
    installments: List<Installment>
  ): KDashboardData {
    val upcomingIns = installments.filter { !it.isPaid }.sortedBy { it.dueDate }
    return KDashboardData(
      currentBalance = rust.currentBalance,
      monthlyExpenses = rust.monthlyExpenses,
      monthlyIncome = rust.monthlyIncome,
      debtorsTotal = rust.debtorsTotal,
      creditorsTotal = rust.creditorsTotal,
      upcomingInstallments = upcomingIns,
      savingsRate = rust.savingsRate,
      debtToIncomeRatio = rust.debtToIncomeRatio
    )
  }

  fun mapAnalyticsData(
    rust: AnalyticsData,
    loans: List<io.github.mojri.hesabyar.data.Loan>,
    installments: List<Installment>
  ): KAnalyticsData {
    val unsettledLoans = loans.filter { !it.isSettled }
    val debtors =
      unsettledLoans.filter { it.type == "DEBTOR" }.map { loan ->
        KDebtSummary(
          personName = loan.personName,
          originalAmount = loan.originalAmount,
          remainingAmount = loan.remainingAmount,
          type = loan.type,
          progress =
            if (loan.originalAmount > 0) {
              (1f - (loan.remainingAmount.toFloat() / loan.originalAmount)).coerceIn(0f, 1f)
            } else {
              0f
            }
        )
      }
    val creditors =
      unsettledLoans.filter { it.type == "CREDITOR" }.map { loan ->
        KDebtSummary(
          personName = loan.personName,
          originalAmount = loan.originalAmount,
          remainingAmount = loan.remainingAmount,
          type = loan.type,
          progress =
            if (loan.originalAmount > 0) {
              (1f - (loan.remainingAmount.toFloat() / loan.originalAmount)).coerceIn(0f, 1f)
            } else {
              0f
            }
        )
      }
    val installmentProgress =
      installments.map { inst ->
        KInstallmentProgress(
          id = inst.id,
          title = inst.title,
          amount = inst.amount,
          dueDate = inst.dueDate,
          isPaid = inst.isPaid
        )
      }
    return KAnalyticsData(
      monthlySpending = rust.monthlySpending.map { mapMonthlyData(it) },
      monthlyIncome = rust.monthlyIncome.map { mapMonthlyData(it) },
      categoryBreakdown = rust.categoryBreakdown.map { mapCategoryBreakdown(it) },
      debtors = debtors,
      creditors = creditors,
      activeLoans = unsettledLoans,
      installmentProgress = installmentProgress,
      totalInstallments = rust.totalInstallments,
      paidInstallments = rust.paidInstallments,
      totalDebt = rust.totalDebt,
      totalCredit = rust.totalCredit
    )
  }

  private fun mapMonthlyData(rust: MonthlyData) =
    KMonthlyData(
      jalaliYear = rust.jalaliYear,
      jalaliMonth = rust.jalaliMonth,
      label = rust.label,
      income = rust.income,
      expense = rust.expense
    )

  private fun mapCategoryBreakdown(rust: CategoryBreakdown) =
    KCategoryBreakdown(
      categoryId = rust.categoryId,
      categoryName = rust.categoryName,
      color = rust.color,
      total = rust.total,
      percentage = rust.percentage
    )

  fun mapCategoryMap(categories: List<Category>): Map<Long, Category> = categories.associateBy { it.id }
}
