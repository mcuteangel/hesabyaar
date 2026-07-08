package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.AnalyticsData

class GetAnalyticsUseCase {
  fun computeAnalytics(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): AnalyticsData {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.computeAnalyticsSync(
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapCategories(categories)
      )

    if (rustResult != null) {
      return io.github.mojri.hesabyar.rust.RustMappers
        .mapAnalyticsData(rustResult, loans, installments)
    }

    // Kotlin fallback when Rust FFI is unavailable or fails
    val now = System.currentTimeMillis()
    val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
    val monthlyTx = transactions.filter { it.date >= thirtyDaysAgo }

    val monthlySpending =
      io.github.mojri.hesabyar.ui.MonthlyData(
        jalaliYear = 0,
        jalaliMonth = 0,
        label = "",
        income = monthlyTx.filter { it.type == "INCOME" }.sumOf { it.amount },
        expense = monthlyTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
      )

    val unsettledLoans = loans.filter { !it.isSettled }
    val debtors =
      unsettledLoans.filter { it.type == "DEBTOR" }.map {
        io.github.mojri.hesabyar.ui.DebtSummary(
          personName = it.personName,
          originalAmount = it.originalAmount,
          remainingAmount = it.remainingAmount,
          type = it.type,
          progress =
            if (it.originalAmount > 0) {
              ((it.originalAmount - it.remainingAmount).toFloat() / it.originalAmount).coerceIn(0f, 1f)
            } else {
              0f
            }
        )
      }
    val creditors =
      unsettledLoans.filter { it.type == "CREDITOR" }.map {
        io.github.mojri.hesabyar.ui.DebtSummary(
          personName = it.personName,
          originalAmount = it.originalAmount,
          remainingAmount = it.remainingAmount,
          type = it.type,
          progress =
            if (it.originalAmount > 0) {
              ((it.originalAmount - it.remainingAmount).toFloat() / it.originalAmount).coerceIn(0f, 1f)
            } else {
              0f
            }
        )
      }

    return AnalyticsData(
      monthlySpending = listOf(monthlySpending),
      monthlyIncome = emptyList(),
      categoryBreakdown = emptyList(),
      debtors = debtors,
      creditors = creditors,
      totalDebt = unsettledLoans.filter { it.type == "DEBTOR" }.sumOf { it.remainingAmount },
      totalCredit = unsettledLoans.filter { it.type == "CREDITOR" }.sumOf { it.remainingAmount },
      totalInstallments = installments.size,
      paidInstallments = installments.count { it.isPaid }
    )
  }
}
