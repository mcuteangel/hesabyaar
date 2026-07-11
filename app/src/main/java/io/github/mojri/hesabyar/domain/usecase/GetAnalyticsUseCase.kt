package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
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
    val jalaliDate =
      io.github.mojri.hesabyar.ui.JalaliCalendarHelper
        .gregorianToJalali(now)
    val jalaliMonthStart =
      io.github.mojri.hesabyar.ui.JalaliCalendarHelper
        .jalaliToGregorian(jalaliDate.year, jalaliDate.month, 1)
        ?.timeInMillis ?: (now - 30L * 24 * 60 * 60 * 1000)
    val monthlyTx = transactions.filter { it.date in jalaliMonthStart..now }

    val monthLabel =
      if (jalaliDate.month in 1..12) jalaliMonthNames[jalaliDate.month - 1] else ""

    val monthlyIncomeTotal = monthlyTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val monthlyExpenseTotal = monthlyTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    val monthlySpending =
      io.github.mojri.hesabyar.ui.MonthlyData(
        jalaliYear = jalaliDate.year,
        jalaliMonth = jalaliDate.month,
        label = monthLabel,
        income = monthlyIncomeTotal,
        expense = monthlyExpenseTotal
      )

    val unsettledLoans = loans.filter { !it.isSettled }
    val debtors =
      unsettledLoans.filter { it.type == LoanType.DEBTOR }.map {
        io.github.mojri.hesabyar.ui.DebtSummary(
          personName = it.personName,
          originalAmount = it.originalAmount,
          remainingAmount = it.remainingAmount,
          type = it.type.name,
          progress = computeDebtProgress(it.originalAmount, it.remainingAmount)
        )
      }
    val creditors =
      unsettledLoans.filter { it.type == LoanType.CREDITOR }.map {
        io.github.mojri.hesabyar.ui.DebtSummary(
          personName = it.personName,
          originalAmount = it.originalAmount,
          remainingAmount = it.remainingAmount,
          type = it.type.name,
          progress = computeDebtProgress(it.originalAmount, it.remainingAmount)
        )
      }

    val monthlyIncomeData =
      io.github.mojri.hesabyar.ui.MonthlyData(
        jalaliYear = jalaliDate.year,
        jalaliMonth = jalaliDate.month,
        label = monthLabel,
        income = monthlyIncomeTotal,
        expense = 0L
      )

    val categoryBreakdown =
      if (monthlyExpenseTotal > 0) {
        val catById = categories.associateBy { it.id }
        monthlyTx
          .filter { it.type == TransactionType.EXPENSE }
          .groupBy { it.categoryId }
          .map { (catId, txs) ->
            val cat = catById[catId]
            val total = txs.sumOf { it.amount }
            io.github.mojri.hesabyar.ui.CategoryBreakdown(
              categoryId = catId,
              categoryName = cat?.name ?: "سایر",
              color = cat?.color ?: 0xFF999999,
              total = total,
              percentage = total * 100f / monthlyExpenseTotal
            )
          }.sortedByDescending { it.total }
      } else {
        emptyList()
      }

    return AnalyticsData(
      monthlySpending = listOf(monthlySpending),
      monthlyIncome = listOf(monthlyIncomeData),
      categoryBreakdown = categoryBreakdown,
      debtors = debtors,
      creditors = creditors,
      activeLoans = unsettledLoans,
      totalDebt = unsettledLoans.filter { it.type == LoanType.DEBTOR }.sumOf { it.remainingAmount },
      totalCredit = unsettledLoans.filter { it.type == LoanType.CREDITOR }.sumOf { it.remainingAmount },
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
