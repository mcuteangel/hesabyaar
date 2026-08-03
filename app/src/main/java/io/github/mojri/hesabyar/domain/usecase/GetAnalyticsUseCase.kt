package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.AnalyticsData
import io.github.mojri.hesabyar.ui.MonthlyData
import java.math.RoundingMode

class GetAnalyticsUseCase {
  private companion object {
    const val DEFAULT_FALLBACK_COLOR = 0xFF999999L
  }

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
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList(),
    accounts: List<io.github.mojri.hesabyar.data.AccountEntity> = emptyList(),
    accountId: Long? = null,
    includeArchived: Boolean = false,
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
          .mapCategories(categories),
        bankLoans,
        accounts,
        accountId,
        includeArchived,
      )

    // Use the Rust result unless it failed (null) or came back as a blank
    // placeholder while real data exists. In those cases fall back to a local
    // computation so the UI never shows misleading empty analytics.
    val hasData =
      transactions.isNotEmpty() ||
        loans.isNotEmpty() ||
        installments.isNotEmpty() ||
        bankLoans.isNotEmpty()
    if (rustResult != null && !(hasData && rustResult.isBlank())) {
      return io.github.mojri.hesabyar.rust.RustMappers
        .mapAnalyticsData(rustResult, loans, installments)
    }

    // Kotlin fallback when Rust FFI is unavailable, panicked, or returned
    // empty/invalid data. Computed directly from the local DB lists.
    return computeFallbackAnalytics(
      transactions,
      loans,
      installments,
      categories,
      bankLoans,
      accounts,
      accountId,
      includeArchived
    )
  }

  private fun computeFallbackAnalytics(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan>,
    accounts: List<io.github.mojri.hesabyar.data.AccountEntity>,
    accountId: Long?,
    includeArchived: Boolean = false,
  ): AnalyticsData {
    val now = System.currentTimeMillis()
    val jalaliDate =
      io.github.mojri.hesabyar.ui.JalaliCalendarHelper
        .gregorianToJalali(now)
    val jalaliMonthStart =
      io.github.mojri.hesabyar.ui.JalaliCalendarHelper
        .jalaliToGregorian(jalaliDate.year, jalaliDate.month, 1)
        ?.timeInMillis ?: now - 30L * 24 * 60 * 60 * 1000
    val monthlyTx =
      GetDashboardDataUseCase
        .filterArchivedTransactions(transactions, accounts, includeArchived)
        .filter { it.date in jalaliMonthStart..now }
        .let { txs ->
          if (accountId != null) {
            txs.filter { it.accountId == accountId || it.destinationAccountId == accountId }
          } else {
            txs
          }
        }

    val monthLabel =
      if (jalaliDate.month in 1..12) jalaliMonthNames[jalaliDate.month - 1] else ""
    val monthlyIncomeTotal = monthlyTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val monthlyExpenseTotal = monthlyTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    val unsettledLoans = loans.filter { !it.isSettled }
    val debtors = mapDebtSummaries(unsettledLoans, LoanType.DEBTOR)
    val creditors = mapDebtSummaries(unsettledLoans, LoanType.CREDITOR)
    val bankLoanSummaries = mapBankLoanSummaries(bankLoans)

    return buildFallbackAnalyticsResult(
      FallbackAnalyticsInput(
        monthlySpending = buildMonthlyData(jalaliDate, monthLabel, monthlyIncomeTotal, monthlyExpenseTotal),
        monthlyIncome = buildMonthlyData(jalaliDate, monthLabel, monthlyIncomeTotal, 0L),
        categoryBreakdown = buildCategoryBreakdown(monthlyTx, categories, monthlyExpenseTotal),
        accountBreakdown = buildAccountBreakdown(monthlyTx, accounts, monthlyExpenseTotal),
        unsettledLoans = unsettledLoans,
        debtors = debtors,
        creditors = creditors,
        installments = installments,
        bankLoanSummaries = bankLoanSummaries
      )
    )
  }

  private fun mapDebtSummaries(
    unsettledLoans: List<Loan>,
    type: LoanType
  ): List<io.github.mojri.hesabyar.ui.DebtSummary> =
    unsettledLoans.filter { it.type == type }.map {
      io.github.mojri.hesabyar.ui.DebtSummary(
        personName = it.personName,
        originalAmount = it.originalAmount,
        remainingAmount = it.remainingAmount,
        type = it.type.name,
        progress = computeDebtProgress(it.originalAmount, it.remainingAmount)
      )
    }

  private fun mapBankLoanSummaries(bankLoans: List<BankLoan>): List<io.github.mojri.hesabyar.rust.BankLoanSummary> =
    bankLoans.map { loan ->
      io.github.mojri.hesabyar.rust.BankLoanSummary(
        bankName = loan.bankName,
        loanName = loan.loanName,
        receivedAmount = loan.receivedAmount,
        totalRepayableAmount = loan.totalRepayableAmount,
        totalInterest = loan.totalInterest,
        numberOfInstallments = loan.numberOfInstallments,
        isSettled = loan.isSettled,
        remainingDebt = if (loan.isSettled) 0 else loan.totalRepayableAmount
      )
    }

  /** Pre-computed pieces the fallback result assembler needs. Grouped as a
   *  data class so [buildFallbackAnalyticsResult] stays a single-arg function. */
  private data class FallbackAnalyticsInput(
    val monthlySpending: io.github.mojri.hesabyar.ui.MonthlyData,
    val monthlyIncome: io.github.mojri.hesabyar.ui.MonthlyData,
    val categoryBreakdown: List<io.github.mojri.hesabyar.ui.CategoryBreakdown>,
    val accountBreakdown: List<io.github.mojri.hesabyar.ui.CategoryBreakdown>,
    val unsettledLoans: List<Loan>,
    val debtors: List<io.github.mojri.hesabyar.ui.DebtSummary>,
    val creditors: List<io.github.mojri.hesabyar.ui.DebtSummary>,
    val installments: List<Installment>,
    val bankLoanSummaries: List<io.github.mojri.hesabyar.rust.BankLoanSummary>,
  )

  private fun buildMonthlyData(
    jalaliDate: io.github.mojri.hesabyar.ui.JalaliCalendarHelper.JalaliDate,
    label: String,
    income: Long,
    expense: Long
  ): io.github.mojri.hesabyar.ui.MonthlyData =
    io.github.mojri.hesabyar.ui.MonthlyData(
      jalaliYear = jalaliDate.year,
      jalaliMonth = jalaliDate.month,
      label = label,
      income = income,
      expense = expense
    )

  private fun buildCategoryBreakdown(
    monthlyTx: List<Transaction>,
    categories: List<Category>,
    totalExpense: Long
  ): List<io.github.mojri.hesabyar.ui.CategoryBreakdown> =
    buildBreakdown(
      monthlyTx,
      keySelector = { it.categoryId },
      metadataResolver = { id -> categories.find { it.id == id } },
      nameResolver = { it?.name ?: "سایر" },
      colorResolver = { it?.color ?: DEFAULT_FALLBACK_COLOR },
      totalExpense = totalExpense
    )

  private fun buildAccountBreakdown(
    monthlyTx: List<Transaction>,
    accounts: List<io.github.mojri.hesabyar.data.AccountEntity>,
    totalExpense: Long
  ): List<io.github.mojri.hesabyar.ui.CategoryBreakdown> =
    buildBreakdown(
      monthlyTx,
      keySelector = { it.accountId },
      metadataResolver = { id -> accounts.find { it.id == id } },
      nameResolver = { it?.name ?: "سایر" },
      colorResolver = { it?.color ?: DEFAULT_FALLBACK_COLOR },
      totalExpense = totalExpense
    )

  private fun buildFallbackAnalyticsResult(input: FallbackAnalyticsInput): AnalyticsData {
    val bankLoanTotalDebt = input.bankLoanSummaries.sumOf { it.remainingDebt }

    return AnalyticsData(
      monthlySpending = listOf(input.monthlySpending),
      monthlyIncome = listOf(input.monthlyIncome),
      categoryBreakdown = input.categoryBreakdown,
      accountBreakdown = input.accountBreakdown,
      debtors = input.debtors,
      creditors = input.creditors,
      activeLoans = input.unsettledLoans,
      totalDebt = input.unsettledLoans.filter { it.type == LoanType.DEBTOR }.sumOf { it.remainingAmount },
      totalCredit = input.unsettledLoans.filter { it.type == LoanType.CREDITOR }.sumOf { it.remainingAmount },
      totalInstallments = input.installments.size,
      paidInstallments = input.installments.count { it.isPaid },
      bankLoans = input.bankLoanSummaries,
      bankLoansTotalDebt = bankLoanTotalDebt
    )
  }

  /** Generic breakdown builder — groups expense transactions by [keySelector],
   *  resolves display metadata via [metadataResolver], and computes totals/percentages. */
  private fun <T> buildBreakdown(
    monthlyTx: List<Transaction>,
    keySelector: (Transaction) -> Long,
    metadataResolver: (Long) -> T?,
    nameResolver: (T?) -> String,
    colorResolver: (T?) -> Long,
    totalExpense: Long,
  ): List<io.github.mojri.hesabyar.ui.CategoryBreakdown> =
    if (totalExpense > 0) {
      monthlyTx
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy(keySelector)
        .map { (key, txs) ->
          val meta = metadataResolver(key)
          val total = txs.sumOf { it.amount }
          io.github.mojri.hesabyar.ui.CategoryBreakdown(
            categoryId = key,
            categoryName = nameResolver(meta),
            color = colorResolver(meta),
            total = total,
            percentage = total * 100f / totalExpense
          )
        }.sortedByDescending { it.total }
    } else {
      emptyList()
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
      paidInstallments == 0 &&
      bankLoans.isEmpty() &&
      bankLoansTotalDebt == 0L
}
