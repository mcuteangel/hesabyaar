package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import java.math.RoundingMode
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
      debtToIncomeRatio = rust.debtToIncomeRatio,
      bankLoans = rust.bankLoans,
      bankLoansTotal = rust.bankLoansTotal
    )
  }

  fun mapAnalyticsData(
    rust: AnalyticsData,
    loans: List<io.github.mojri.hesabyar.data.Loan>,
    installments: List<Installment>
  ): KAnalyticsData {
    val unsettledLoans = loans.filter { !it.isSettled }
    val debtors =
      unsettledLoans
        .filter {
          it.type == io.github.mojri.hesabyar.data.LoanType.DEBTOR
        }.map { mapDebtSummary(it) }
    val creditors =
      unsettledLoans
        .filter {
          it.type == io.github.mojri.hesabyar.data.LoanType.CREDITOR
        }.map { mapDebtSummary(it) }
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
      totalCredit = rust.totalCredit,
      bankLoans = rust.bankLoans,
      bankLoansTotalDebt = rust.bankLoansTotalDebt
    )
  }

  private fun mapDebtSummary(loan: io.github.mojri.hesabyar.data.Loan): KDebtSummary {
    val progress =
      if (loan.originalAmount > 0L) {
        // Ratio of paid amount over original; divide with BigDecimal to avoid
        // Float precision loss on large Rial values, then keep only the ratio as Float.
        val paid = (loan.originalAmount - loan.remainingAmount).toBigDecimal()
        val ratio = paid.divide(loan.originalAmount.toBigDecimal(), 6, RoundingMode.HALF_UP)
        ratio.toFloat().coerceIn(0f, 1f)
      } else {
        0f
      }
    return KDebtSummary(
      personName = loan.personName,
      originalAmount = loan.originalAmount,
      remainingAmount = loan.remainingAmount,
      type = loan.type.name,
      progress = progress
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

  /**
   * Map a DB transaction type to the Rust [TransactionType].
   * Since [Transaction.type] is now a typed enum, this is a direct passthrough.
   */
  fun mapTransactionType(type: TransactionType): io.github.mojri.hesabyar.rust.TransactionType =
    when (type) {
      TransactionType.UNKNOWN -> io.github.mojri.hesabyar.rust.TransactionType.EXPENSE
      else ->
        io.github.mojri.hesabyar.rust.TransactionType
          .valueOf(type.name)
    }

  fun mapTransaction(tx: Transaction): io.github.mojri.hesabyar.rust.Transaction =
    io.github.mojri.hesabyar.rust.Transaction(
      id = tx.id,
      txType = mapTransactionType(tx.type),
      categoryId = tx.categoryId,
      amount = tx.amount,
      description = tx.description,
      personName = tx.personName,
      date = tx.date,
      dueDate = tx.dueDate,
      installmentId = tx.installmentId
    )

  fun mapLoan(loan: Loan): io.github.mojri.hesabyar.rust.Loan =
    io.github.mojri.hesabyar.rust.Loan(
      id = loan.id,
      personName = loan.personName,
      loanType = loan.type.name,
      originalAmount = loan.originalAmount,
      remainingAmount = loan.remainingAmount,
      description = loan.description,
      date = loan.date,
      isSettled = loan.isSettled
    )

  fun mapInstallment(inst: Installment): io.github.mojri.hesabyar.rust.Installment =
    io.github.mojri.hesabyar.rust.Installment(
      id = inst.id,
      title = inst.title,
      amount = inst.amount,
      dueDate = inst.dueDate,
      isPaid = inst.isPaid,
      reminderEnabled = inst.reminderEnabled,
      notes = inst.notes
    )

  fun mapBankLoan(bankLoan: BankLoan): io.github.mojri.hesabyar.rust.BankLoan =
    io.github.mojri.hesabyar.rust.BankLoan(
      id = bankLoan.id,
      bankName = bankLoan.bankName,
      loanName = bankLoan.loanName,
      receivedAmount = bankLoan.receivedAmount,
      monthlyInstallmentAmount = bankLoan.monthlyInstallmentAmount,
      numberOfInstallments = bankLoan.numberOfInstallments,
      totalRepayableAmount = bankLoan.totalRepayableAmount,
      totalInterest = bankLoan.totalInterest,
      startDate = bankLoan.startDate,
      description = bankLoan.description,
      isSettled = bankLoan.isSettled
    )

  fun mapCategory(cat: Category): io.github.mojri.hesabyar.rust.Category =
    io.github.mojri.hesabyar.rust.Category(
      id = cat.id,
      name = cat.name,
      key = cat.key,
      icon = cat.icon,
      color = cat.color,
      categoryType = cat.type.name,
      isDefault = cat.isDefault
    )

  // ===========================================================================
  // Batch mappers: lists of Kotlin domain → Rust types
  // ===========================================================================

  fun mapTransactions(list: List<Transaction>): List<io.github.mojri.hesabyar.rust.Transaction> =
    list.map { mapTransaction(it) }

  fun mapLoans(list: List<Loan>): List<io.github.mojri.hesabyar.rust.Loan> = list.map { mapLoan(it) }

  fun mapInstallments(list: List<Installment>): List<io.github.mojri.hesabyar.rust.Installment> =
    list.map { mapInstallment(it) }

  fun mapBankLoans(list: List<BankLoan>): List<io.github.mojri.hesabyar.rust.BankLoan> = list.map { mapBankLoan(it) }

  fun mapCategories(list: List<Category>): List<io.github.mojri.hesabyar.rust.Category> = list.map { mapCategory(it) }

  // ===========================================================================
  // Reverse mappers: Rust → Kotlin domain types
  // ===========================================================================

  private fun toKotlinTransactionType(rustName: String): TransactionType =
    when (rustName) {
      "INCOME", "LOAN_CREDITOR" -> TransactionType.INCOME
      else -> TransactionType.EXPENSE
    }

  fun fromRustTransaction(tx: io.github.mojri.hesabyar.rust.Transaction): Transaction =
    Transaction(
      id = tx.id,
      type = toKotlinTransactionType(tx.txType.name),
      categoryId = tx.categoryId,
      amount = tx.amount,
      description = tx.description,
      personName = tx.personName,
      date = tx.date,
      dueDate = tx.dueDate,
      installmentId = tx.installmentId
    )

  fun fromRustLoan(loan: io.github.mojri.hesabyar.rust.Loan): Loan =
    Loan(
      id = loan.id,
      personName = loan.personName,
      type = LoanType.valueOf(loan.loanType),
      originalAmount = loan.originalAmount,
      remainingAmount = loan.remainingAmount,
      description = loan.description,
      date = loan.date,
      isSettled = loan.isSettled
    )

  fun fromRustInstallment(inst: io.github.mojri.hesabyar.rust.Installment): Installment =
    Installment(
      id = inst.id,
      title = inst.title,
      amount = inst.amount,
      dueDate = inst.dueDate,
      isPaid = inst.isPaid,
      reminderEnabled = inst.reminderEnabled,
      notes = inst.notes
    )

  fun fromRustBankLoan(bankLoan: io.github.mojri.hesabyar.rust.BankLoan): BankLoan =
    BankLoan(
      id = bankLoan.id,
      bankName = bankLoan.bankName,
      loanName = bankLoan.loanName,
      receivedAmount = bankLoan.receivedAmount,
      monthlyInstallmentAmount = bankLoan.monthlyInstallmentAmount,
      numberOfInstallments = bankLoan.numberOfInstallments,
      totalRepayableAmount = bankLoan.totalRepayableAmount,
      totalInterest = bankLoan.totalInterest,
      startDate = bankLoan.startDate,
      description = bankLoan.description,
      isSettled = bankLoan.isSettled
    )

  fun fromRustCategory(cat: io.github.mojri.hesabyar.rust.Category): Category =
    Category(
      id = cat.id,
      name = cat.name,
      key = cat.key,
      icon = cat.icon,
      color = cat.color,
      type = CategoryType.valueOf(cat.categoryType),
      isDefault = cat.isDefault
    )
}
