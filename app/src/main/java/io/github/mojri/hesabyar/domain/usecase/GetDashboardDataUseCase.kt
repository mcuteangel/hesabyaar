package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.rust.BankLoanSummary
import io.github.mojri.hesabyar.ui.AccountDashboardSummary
import io.github.mojri.hesabyar.ui.DashboardData
import kotlinx.coroutines.flow.Flow

class GetDashboardDataUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val transactions: Flow<List<Transaction>> = repository.allTransactions
  val loans: Flow<List<Loan>> = repository.allLoans
  val installments: Flow<List<Installment>> = repository.allInstallments
  val categories: Flow<List<Category>> = repository.allCategories
  val bankLoans: Flow<List<BankLoan>> = repository.allBankLoans
  val accounts: Flow<List<AccountEntity>> = repository.allAccounts

  fun computeDashboardData(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    accountId: Long? = null,
    includeArchived: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
  ): DashboardData {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.computeDashboardDataSync(
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments),
        bankLoans,
        accounts,
        accountId,
        includeArchived,
        nowMs,
      )

    // Use the Rust result unless it failed (null) or came back as an all-zero
    // placeholder while real data exists. In those cases fall back to a local
    // computation so the UI never shows misleading blank zeros.
    val hasData =
      transactions.isNotEmpty() ||
        loans.isNotEmpty() ||
        installments.isNotEmpty() ||
        bankLoans.isNotEmpty()
    if (rustResult != null && !(hasData && rustResult.isBlank())) {
      return io.github.mojri.hesabyar.rust.RustMappers
        .mapDashboardData(rustResult, installments, accounts)
    }

    // Kotlin fallback when Rust FFI is unavailable, panicked, or returned
    // empty/invalid data. Computed directly from the local DB lists.
    return computeFallbackDashboardData(
      transactions,
      loans,
      installments,
      bankLoans,
      accounts,
      now = nowMs,
      includeArchived = includeArchived,
    )
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
      debtToIncomeRatio == 0.0 &&
      totalNetWorth == 0L

  companion object {
    /** Kotlin-only dashboard computation.  Extracted so unit tests can verify
     *  the fallback logic without requiring the Rust native library. */
    internal fun computeFallbackDashboardData(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      bankLoans: List<BankLoan> = emptyList(),
      accounts: List<AccountEntity> = emptyList(),
      now: Long = System.currentTimeMillis(),
      includeArchived: Boolean = false,
    ): DashboardData {
      val effectiveTransactions = filterArchivedTransactions(transactions, accounts, includeArchived)

      // Current Jalali month boundaries in UTC, half-open [start, endExclusive),
      // matching the Rust core's compute_dashboard_data (which interprets
      // timestamps in UTC). Centralized in JalaliCalendarHelper so the fallback
      // and Rust paths assign transactions/installments to the same Jalali month.
      val (jalaliMonthStart, jalaliMonthEndExclusive) =
        io.github.mojri.hesabyar.ui.JalaliCalendarHelper
          .getUtcJalaliMonthBoundaries(now)

      val monthlyTx = effectiveTransactions.filter { it.date >= jalaliMonthStart && it.date < jalaliMonthEndExclusive }
      val monthlyIncome = monthlyTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
      val monthlyExpenses = monthlyTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

      val unsettledLoans = loans.filter { !it.isSettled }
      val debtors = unsettledLoans.filter { it.type == LoanType.DEBTOR }.sumOf { it.remainingAmount }
      val creditors = unsettledLoans.filter { it.type == LoanType.CREDITOR }.sumOf { it.remainingAmount }

      // currentBalance from all effective transactions (lifetime), not just the filtered month.
      val totalIncome = effectiveTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
      val totalExpenses = effectiveTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
      val currentBalance = totalIncome - totalExpenses

      val savingsRate =
        if (monthlyIncome > 0) {
          ((monthlyIncome - monthlyExpenses).toDouble() / monthlyIncome).coerceIn(0.0, 1.0)
        } else {
          0.0
        }

      // Monthly debt obligations mirror the Rust core's calculate_debt_to_income_ratio:
      // unpaid installments due in the current cycle (full amount) plus the prorated
      // monthly portion (remaining / 12) of unsettled creditor loans.
      val installmentDebt =
        installments
          .filter { !it.isPaid && it.dueDate >= jalaliMonthStart && it.dueDate < jalaliMonthEndExclusive }
          .sumOf { it.amount }
      val creditorLoanDebt =
        unsettledLoans
          .filter { it.type == LoanType.CREDITOR }
          .sumOf { it.remainingAmount / 12 }
      val monthlyDebt = installmentDebt + creditorLoanDebt
      val debtToIncome =
        if (monthlyIncome > 0) {
          monthlyDebt.toDouble() / monthlyIncome
        } else if (monthlyDebt > 0) {
          1.0
        } else {
          0.0
        }

      val upcomingIns = installments.filter { !it.isPaid }.sortedBy { it.dueDate }

      return DashboardData(
        currentBalance = currentBalance,
        monthlyExpenses = monthlyExpenses,
        monthlyIncome = monthlyIncome,
        debtorsTotal = debtors,
        creditorsTotal = creditors,
        upcomingInstallments = upcomingIns,
        savingsRate = savingsRate,
        debtToIncomeRatio = debtToIncome,
        bankLoans = toBankLoanSummaries(bankLoans, installments),
        bankLoansTotal = bankLoans.filter { !it.isSettled }.sumOf { it.totalRepayableAmount },
        accounts = computeAccountSummaries(accounts, effectiveTransactions, jalaliMonthStart, jalaliMonthEndExclusive),
        totalNetWorth = currentBalance
      )
    }

    private fun toBankLoanSummaries(
      bankLoans: List<BankLoan>,
      installments: List<Installment>
    ): List<BankLoanSummary> =
      bankLoans
        .filter { !it.isSettled }
        .map { loan ->
          val paidAmount = installments.filter { it.bankLoanId == loan.id && it.isPaid }.sumOf { it.amount }
          BankLoanSummary(
            bankName = loan.bankName,
            loanName = loan.loanName,
            receivedAmount = loan.receivedAmount,
            totalRepayableAmount = loan.totalRepayableAmount,
            totalInterest = loan.totalInterest,
            numberOfInstallments = loan.numberOfInstallments,
            isSettled = loan.isSettled,
            remainingDebt = if (loan.isSettled) 0 else (loan.totalRepayableAmount - paidAmount).coerceAtLeast(0L)
          )
        }

    private fun filterArchivedTransactions(
      transactions: List<Transaction>,
      accounts: List<AccountEntity>,
      includeArchived: Boolean,
    ): List<Transaction> {
      if (includeArchived) return transactions
      val archivedIds = accounts.filter { it.isArchived }.map { it.id }.toSet()
      return transactions.filter { tx ->
        tx.accountId !in archivedIds &&
          (tx.destinationAccountId == null || tx.destinationAccountId !in archivedIds)
      }
    }

    /** Noise threshold for previous-month net (Rial). When abs(prevNet) is
     *  below this, the delta is set to 0.0 to avoid misleading percentages. */
    private const val DELTA_PREV_NET_THRESHOLD = 1_000L

    private fun computeAccountSummaries(
      accounts: List<AccountEntity>,
      transactions: List<Transaction>,
      monthStartMs: Long,
      monthEndMs: Long,
    ): List<AccountDashboardSummary> {
      if (accounts.isEmpty()) return emptyList()

      // Previous Jalali month boundaries for delta computation, matching the
      // Rust core's jalali month arithmetic.
      val (prevMonthStart, prevMonthEnd) =
        io.github.mojri.hesabyar.ui.JalaliCalendarHelper
          .getUtcJalaliPreviousMonthBoundaries(monthStartMs)

      return accounts
        .filter { !it.isArchived }
        .map { account ->
          val accountTxs =
            transactions.filter {
              it.accountId == account.id || it.destinationAccountId == account.id
            }
          var balance = account.initialBalance
          var monthlyIncome = 0L
          var monthlyExpenses = 0L
          var prevIncome = 0L
          var prevExpenses = 0L
          for (tx in accountTxs) {
            val inMonth = tx.date >= monthStartMs && tx.date < monthEndMs
            val inPrev = tx.date >= prevMonthStart && tx.date < prevMonthEnd
            val delta = balanceDeltaForAccount(tx, account.id)
            balance += delta.balanceDelta
            if (inMonth) {
              monthlyIncome += delta.incomeDelta
              monthlyExpenses += delta.expenseDelta
            }
            if (inPrev) {
              prevIncome += delta.incomeDelta
              prevExpenses += delta.expenseDelta
            }
          }

          // Month-over-month delta: (currentNet - prevNet) / max(abs(prevNet), 1)
          // When prevNet is below noise threshold, show 0.0 to avoid
          // misleading percentages near zero (e.g. +8000000%).
          val currentNet = monthlyIncome - monthlyExpenses
          val prevNet = prevIncome - prevExpenses
          val monthlyDelta =
            if (kotlin.math.abs(prevNet) < DELTA_PREV_NET_THRESHOLD) {
              0.0
            } else {
              (currentNet - prevNet).toDouble() / kotlin.math.abs(prevNet).toDouble()
            }

          AccountDashboardSummary(
            accountId = account.id,
            accountName = account.name,
            accountType = account.type,
            balance = balance,
            monthlyIncome = monthlyIncome,
            monthlyExpenses = monthlyExpenses,
            accountColor = account.color,
            monthlyDelta = monthlyDelta
          )
        }
    }

    private data class BalanceDelta(
      val balanceDelta: Long,
      val incomeDelta: Long,
      val expenseDelta: Long,
    )

    private fun balanceDeltaForAccount(
      tx: Transaction,
      accountId: Long
    ): BalanceDelta {
      val isSource = tx.accountId == accountId
      val isDest = tx.destinationAccountId == accountId
      return when (tx.type) {
        TransactionType.INCOME ->
          if (isSource) BalanceDelta(tx.amount, tx.amount, 0L) else BalanceDelta(0L, 0L, 0L)
        TransactionType.EXPENSE ->
          if (isSource) BalanceDelta(-tx.amount, 0L, tx.amount) else BalanceDelta(0L, 0L, 0L)
        TransactionType.TRANSFER -> {
          var bal = 0L
          var inc = 0L
          var exp = 0L
          if (isSource) {
            bal -= tx.amount
            exp += tx.amount
          }
          if (isDest) {
            bal += tx.amount
            inc += tx.amount
          }
          BalanceDelta(bal, inc, exp)
        }
        else -> BalanceDelta(0L, 0L, 0L)
      }
    }
  }
}
