package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.AnalyticsData
import java.math.RoundingMode

class GetAnalyticsUseCase {
  private val jalaliMonthNames =
    listOf(
      "فروردین",
      "اردیبهشت",
      "خرداد",
      "تیر",
      "مرداد",
      "شهریور",
      "مهر",
      "آبان",
      "آذر",
      "دی",
      "بهمن",
      "اسفند"
    )

  private fun computeDebtProgress(
    original: Long,
    remaining: Long
  ): Float =
    if (original > 0L) {
      val paid = (original - remaining).toBigDecimal()
      paid
        .divide(original.toBigDecimal(), 6, RoundingMode.HALF_UP)
        .toFloat()
        .coerceIn(0f, 1f)
    } else {
      0f
    }

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

    // Use the Rust result unless it failed (null) or came back as a blank
    // placeholder while real data exists. In those cases fall back to a local
    // computation so the UI never shows misleading empty analytics.
    val hasData =
      transactions.isNotEmpty() || loans.isNotEmpty() || installments.isNotEmpty()
    if (rustResult != null && !(hasData && rustResult.isBlank())) {
      return io.github.mojri.hesabyar.rust.RustMappers
        .mapAnalyticsData(rustResult, loans, installments)
    }

    // Kotlin fallback when Rust FFI is unavailable, panicked, or returned
    // empty/invalid data. Computed directly from the local DB lists.
    val now = System.currentTimeMillis()
    val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
    val monthlyTx = transactions.filter { it.date >= thirtyDaysAgo }

    val jalaliDate =
      io.github.mojri.hesabyar.ui.JalaliCalendarHelper
        .gregorianToJalali(now)
    val monthLabel =
      if (jalaliDate.month in 1..12) jalaliMonthNames[jalaliDate.month - 1] else ""

    val monthlySpending =
      io.github.mojri.hesabyar.ui.MonthlyData(
        jalaliYear = jalaliDate.year,
        jalaliMonth = jalaliDate.month,
        label = monthLabel,
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
          progress = computeDebtProgress(it.originalAmount, it.remainingAmount)
        )
      }
    val creditors =
      unsettledLoans.filter { it.type == "CREDITOR" }.map {
        io.github.mojri.hesabyar.ui.DebtSummary(
          personName = it.personName,
          originalAmount = it.originalAmount,
          remainingAmount = it.remainingAmount,
          type = it.type,
          progress = computeDebtProgress(it.originalAmount, it.remainingAmount)
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

  /** True when every collection/aggregate is empty/zero, i.e. the Rust result
   *  is a blank placeholder rather than a real computation. */
  private fun io.github.mojri.hesabyar.rust.AnalyticsData.isBlank(): Boolean =
    monthlySpending.isEmpty() &&
      monthlyIncome.isEmpty() &&
      categoryBreakdown.isEmpty() &&
      debtors.isEmpty() &&
      creditors.isEmpty() &&
      totalDebt == 0L &&
      totalCredit == 0L &&
      totalInstallments == 0 &&
      paidInstallments == 0
}
