package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper

// Kotlin fallback forecast builder for the offline budget advisor.
// See LocalBudgetAdvice for the advice counterpart.

/** Local, dependency-free baseline forecast used when the Rust core is unavailable. */
internal object LocalBudgetForecast {
  /** Window end offset in Jalali days. 31 makes an exclusive end at the start of
   *  day 31, so obligations through the end of day 30 are included. */
  private const val FORECAST_WINDOW_DAYS = 31
  private const val MONTHS_PER_YEAR = 12L
  private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
  private const val LAST_JALALI_MONTH = 12

  fun forecast(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan>,
    excludedCategoryIds: List<Long> = emptyList()
  ): String {
    val summary = LocalBudgetAdvice.summarize(transactions, excludedCategoryIds)
    val nowMs = System.currentTimeMillis()
    // Window end is derived with Jalali calendar arithmetic so "the next 30 days"
    // spans exactly 30 Jalali days (months are 29–31 days) rather than a fixed
    // 30 × 24h millisecond span. See FORECAST_WINDOW_DAYS.
    val windowEndMs = jalaliPlusDaysMs(nowMs, FORECAST_WINDOW_DAYS)
    val upcomingInstallments =
      installments.filter { !it.isPaid && it.dueDate >= nowMs && it.dueDate < windowEndMs }
    // Saturating fold matching the Rust forecast's saturating_add.
    val totalUpcoming =
      upcomingInstallments.fold(0L) { total, installment ->
        saturatingAdd(total, installment.amount)
      }
    val activeBankLoans = bankLoans.filter { !it.isSettled }
    val activeLoans = loans.filter { !it.isSettled }
    val totalDebt =
      saturatingAdd(
        activeLoans.fold(0L) { total, loan -> saturatingAdd(total, loan.remainingAmount) },
        activeBankLoans.fold(0L) { total, bankLoan -> saturatingAdd(total, bankLoan.totalRepayableAmount) }
      )
    val activeDebtCount = activeLoans.size + activeBankLoans.size

    // Parity with the Rust guard (get_offline_forecast): only unsettled CREDITOR
    // loans contribute to the monthly obligation sum (remainingAmount / 12).
    // Unsettled DEBTOR loans and CREDITOR loans with zero monthly obligation
    // must not suppress the "no data" message.
    val unsettledCreditorMonthlyObligation =
      loans
        .filter { !it.isSettled && it.type == LoanType.CREDITOR }
        .fold(0L) { total, loan ->
          val monthly = loan.remainingAmount / MONTHS_PER_YEAR
          if (monthly > 0L && total > Long.MAX_VALUE - monthly) Long.MAX_VALUE else total + monthly
        }
    val hasNoData =
      // Parity with the Rust guard: transactions counts only once the excluded
      // categories are dropped (plan 011 D2), not the raw table size.
      transactions.none { !LoansCategoryExclusion.isExcluded(it, excludedCategoryIds) } &&
        upcomingInstallments.isEmpty() &&
        unsettledCreditorMonthlyObligation == 0L &&
        activeBankLoans.isEmpty()
    if (hasNoData) {
      return "تراکنش یا قسطی برای پیش‌بینی ثبت نشده است. لطفا اطلاعات مالی خود را وارد کنید."
    }

    // The Rust forecast nets both upcoming installments and the monthly
    // creditor-loan obligation out of the balance; omitting the obligation
    // here made the two paths diverge whenever creditor loans existed.
    val projectedBalance = summary.balance - totalUpcoming - unsettledCreditorMonthlyObligation
    val sb = StringBuilder()
    sb.appendLine("### 🔮 پیش‌بینی بودجه محلی (آفلاین)")
    sb.appendLine()
    sb.appendLine("**تراز فعلی:** ${LocalBudgetAdvice.formatAmount(summary.balance)}")
    sb.appendLine(
      "**اقساط پیش‌رو:** ${upcomingInstallments.size} مورد به مبلغ " +
        LocalBudgetAdvice.formatAmount(totalUpcoming)
    )
    if (activeDebtCount > 0) {
      sb.appendLine(
        "**بدهی‌های فعال:** $activeDebtCount مورد به مبلغ ${LocalBudgetAdvice.formatAmount(totalDebt)}"
      )
    }
    sb.appendLine()
    sb.appendLine("**تراز پیش‌بینی‌شده (۳۰ روز آینده):** ${LocalBudgetAdvice.formatAmount(projectedBalance)}")
    sb.appendLine()
    sb.appendLine(
      if (projectedBalance < 0) {
        "⚠️ **هشدار هوشمند:** تراز پیش‌بینی منفی است. تعدیل هزینه‌ها یا مدیریت اقساط پیش‌رو ضروری است."
      } else {
        "✅ **وضعیت پایدار:** تراز پیش‌بینی مثبت است. ادامه روند فعلی توصیه می‌شود."
      }
    )
    return sb.toString()
  }

  private fun saturatingAdd(
    total: Long,
    amount: Long
  ): Long =
    if (amount > 0L && total > Long.MAX_VALUE - amount) {
      Long.MAX_VALUE
    } else if (amount < 0L && total < Long.MIN_VALUE - amount) {
      Long.MIN_VALUE
    } else {
      total + amount
    }

  // Adds [days] Jalali days to the date represented by [fromMs] and returns the
  // resulting day's local-midnight timestamp. Uses JalaliCalendarHelper for all
  // calendar arithmetic (month lengths differ across Jalali months) so the
  // 30-day forecast window tracks the Iranian calendar instead of a fixed
  // millisecond span. Falls back to a millisecond offset if the conversion is
  // unavailable.
  private fun jalaliPlusDaysMs(
    fromMs: Long,
    days: Int
  ): Long {
    val today = JalaliCalendarHelper.gregorianToJalali(fromMs)
    var year = today.year
    var month = today.month
    var day = today.day
    var remaining = days
    while (remaining > 0) {
      val daysInMonth = JalaliCalendarHelper.getDaysInMonth(year, month)
      val daysLeftInMonth = daysInMonth - day
      if (remaining <= daysLeftInMonth) {
        day += remaining
        remaining = 0
      } else {
        remaining -= daysLeftInMonth + 1
        day = 1
        if (month == LAST_JALALI_MONTH) {
          month = 1
          year += 1
        } else {
          month += 1
        }
      }
    }
    return JalaliCalendarHelper.jalaliToGregorian(year, month, day)?.timeInMillis
      ?: fromMs + days.toLong() * MILLIS_PER_DAY
  }
}
