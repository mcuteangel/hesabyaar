package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the Kotlin fallback computation in [GetDashboardDataUseCase].
 *
 * The Rust bridge is available in the unit-test environment, so
 * [GetDashboardDataUseCase.computeDashboardData] always follows the Rust path.
 * These tests exercise [GetDashboardDataUseCase.computeFallbackDashboardData]
 * directly to verify the three fallback decision branches:
 *
 * 1. Rust returns null → Kotlin fallback runs
 * 2. Rust returns valid data → Rust path used (not testable without native mock)
 * 3. Rust returns blank placeholder while local data exists → Kotlin fallback runs
 *
 * Branches 2 and 3 require the native library and are covered by
 * instrumented/integration tests.
 */
class GetDashboardDataUseCaseTest {
  private fun tx(
    type: TransactionType,
    amount: Long,
    date: Long = System.currentTimeMillis()
  ) = Transaction(type = type, categoryId = 1L, amount = amount, description = "", date = date)

  private fun loan(
    type: LoanType,
    remaining: Long,
    settled: Boolean = false
  ) = Loan(
    personName = "test",
    type = type,
    originalAmount = remaining,
    remainingAmount = remaining,
    description = "",
    isSettled = settled
  )

  private fun inst(
    amount: Long,
    paid: Boolean = false,
    dueDate: Long = System.currentTimeMillis()
  ) = Installment(title = "t", amount = amount, dueDate = dueDate, isPaid = paid)

  // -- Branch 1 & 3: Kotlin fallback computation -----------------------------

  @Test
  fun `fallback computes monthly income and expenses from recent transactions`() {
    val now = System.currentTimeMillis()
    val (jalaliMonthStart, jalaliMonthEndExclusive) =
      JalaliCalendarHelper.getUtcJalaliMonthBoundaries(now)
    // Place transaction safely inside the current Jalali month (1 day after start).
    val recent = jalaliMonthStart + 1L * 24 * 60 * 60 * 1000
    // Place transaction before the current Jalali month (excluded).
    val old = jalaliMonthStart - 1L

    val transactions =
      listOf(
        tx(TransactionType.INCOME, 5_000_000, recent),
        tx(TransactionType.EXPENSE, 2_000_000, recent),
        tx(TransactionType.INCOME, 10_000_000, old) // should be excluded
      )

    val result =
      GetDashboardDataUseCase.computeFallbackDashboardData(
        transactions = transactions,
        loans = emptyList(),
        installments = emptyList(),
        now = now
      )

    assertEquals(5_000_000L, result.monthlyIncome)
    assertEquals(2_000_000L, result.monthlyExpenses)
    // currentBalance uses ALL transactions (lifetime), not just the Jalali month:
    // total income = 5M (recent) + 10M (old) = 15M, total expense = 2M → 13M
    assertEquals(13_000_000L, result.currentBalance)
  }

  @Test
  fun `fallback aggregates debtor and creditor totals from unsettled loans`() {
    val transactions = listOf(tx(TransactionType.INCOME, 10_000_000))
    val loans =
      listOf(
        loan(LoanType.DEBTOR, 3_000_000),
        loan(LoanType.CREDITOR, 7_000_000),
        loan(LoanType.DEBTOR, 1_000_000, settled = true) // settled → excluded
      )

    val result = GetDashboardDataUseCase.computeFallbackDashboardData(transactions, loans, emptyList())

    assertEquals(3_000_000L, result.debtorsTotal)
    assertEquals(7_000_000L, result.creditorsTotal)
  }

  @Test
  fun `fallback filters unpaid installments as upcoming`() {
    val now = System.currentTimeMillis()
    val upcoming = inst(1_000_000, paid = false, dueDate = now + 5L * 24 * 60 * 60 * 1000)
    val paid = inst(2_000_000, paid = true)

    val result = GetDashboardDataUseCase.computeFallbackDashboardData(emptyList(), emptyList(), listOf(upcoming, paid))

    assertEquals(1, result.upcomingInstallments.size)
    assertEquals(1_000_000L, result.upcomingInstallments[0].amount)
  }

  @Test
  fun `fallback computes savings rate correctly`() {
    val now = System.currentTimeMillis()
    val transactions = listOf(tx(TransactionType.INCOME, 10_000_000, now), tx(TransactionType.EXPENSE, 3_000_000, now))

    val result = GetDashboardDataUseCase.computeFallbackDashboardData(transactions, emptyList(), emptyList())

    // savingsRate = (10M - 3M) / 10M = 0.7
    assertEquals(0.7, result.savingsRate, 0.01)
  }

  @Test
  fun `fallback computes debt-to-income ratio from creditor loans`() {
    val now = System.currentTimeMillis()
    val transactions = listOf(tx(TransactionType.INCOME, 12_000_000, now))
    // Creditor loan remaining 12M → monthly debt = 12M/12 = 1M
    // debtToIncome = 1M / 12M ≈ 0.0833
    val loans = listOf(loan(LoanType.CREDITOR, 12_000_000))

    val result = GetDashboardDataUseCase.computeFallbackDashboardData(transactions, loans, emptyList())

    assertEquals(0.0833, result.debtToIncomeRatio, 0.01)
  }

  @Test
  fun `fallback debt-to-income sums current-cycle installments with prorated creditor loans`() {
    val now = System.currentTimeMillis()
    val transactions = listOf(tx(TransactionType.INCOME, 12_000_000, now))
    // Creditor loan remaining 12M → prorated monthly = 1M
    val loans = listOf(loan(LoanType.CREDITOR, 12_000_000))
    // Unpaid installment due this cycle adds its full amount (2M) to monthly debt.
    val installments =
      listOf(
        inst(2_000_000, paid = false, dueDate = now),
        inst(5_000_000, paid = true, dueDate = now), // paid → excluded
        inst(9_000_000, paid = false, dueDate = now - 400L * 24 * 60 * 60 * 1000) // prior cycle → excluded
      )

    val result =
      GetDashboardDataUseCase.computeFallbackDashboardData(transactions, loans, installments)

    // monthlyDebt = 1M (loan/12) + 2M (current-cycle installment) = 3M
    // debtToIncome = 3M / 12M = 0.25
    assertEquals(0.25, result.debtToIncomeRatio, 0.01)
  }

  @Test
  fun `fallback returns all zeros for empty input`() {
    val result = GetDashboardDataUseCase.computeFallbackDashboardData(emptyList(), emptyList(), emptyList())

    assertEquals(0L, result.currentBalance)
    assertEquals(0L, result.monthlyIncome)
    assertEquals(0L, result.monthlyExpenses)
    assertEquals(0L, result.debtorsTotal)
    assertEquals(0L, result.creditorsTotal)
    assertTrue(result.upcomingInstallments.isEmpty())
    assertEquals(0.0, result.savingsRate, 0.001)
    assertEquals(0.0, result.debtToIncomeRatio, 0.001)
  }

  @Test
  fun `fallback with only expenses shows negative balance`() {
    val now = System.currentTimeMillis()
    val transactions = listOf(tx(TransactionType.EXPENSE, 4_000_000, now))

    val result = GetDashboardDataUseCase.computeFallbackDashboardData(transactions, emptyList(), emptyList())

    assertEquals(-4_000_000L, result.currentBalance)
    assertEquals(0.0, result.savingsRate, 0.001) // no income → 0
  }

  @Test
  fun `fallback with only income shows positive balance`() {
    val now = System.currentTimeMillis()
    val transactions = listOf(tx(TransactionType.INCOME, 8_000_000, now))

    val result = GetDashboardDataUseCase.computeFallbackDashboardData(transactions, emptyList(), emptyList())

    assertEquals(8_000_000L, result.currentBalance)
    assertEquals(1.0, result.savingsRate, 0.01) // all income saved
  }

  // -- Edge cases requested by reviewer --------------------------------------

  @Test
  fun `fallback clamps savings rate to zero when expenses exceed income`() {
    // Both transactions fall inside the current Jalali month (date defaults to now).
    val transactions =
      listOf(
        tx(TransactionType.INCOME, 5_000_000),
        tx(TransactionType.EXPENSE, 8_000_000) // expense > income
      )

    val result = GetDashboardDataUseCase.computeFallbackDashboardData(transactions, emptyList(), emptyList())

    // savingsRate = (5M - 8M) / 5M = -0.6, clamped to 0.0 by coerceIn(0.0, 1.0).
    assertEquals(0.0, result.savingsRate, 0.0001)
    assertEquals(-3_000_000L, result.currentBalance) // lifetime balance stays negative
  }

  @Test
  fun `fallback returns debt-to-income ratio of one when income is zero and debt exists`() {
    // No income transactions -> monthlyIncome == 0.
    // An unpaid installment due within the current cycle creates monthly debt.
    val installments = listOf(inst(2_000_000, paid = false))

    val result = GetDashboardDataUseCase.computeFallbackDashboardData(emptyList(), emptyList(), installments)

    assertEquals(0L, result.monthlyIncome)
    // monthlyIncome <= 0 and monthlyDebt > 0 -> debtToIncomeRatio == 1.0
    assertEquals(1.0, result.debtToIncomeRatio, 0.0001)
  }

  @Test
  fun `fallback includes transaction inside UTC month boundary but excludes next month start`() {
    // The fallback uses UTC half-open [start, end) boundaries matching the Rust
    // core, so a transaction one ms before the exclusive end is included while
    // one exactly at the next-month start is excluded.
    val (_, jalaliMonthEndExclusive) =
      JalaliCalendarHelper.getUtcJalaliMonthBoundaries(System.currentTimeMillis())

    val inside = tx(TransactionType.INCOME, 4_000_000, jalaliMonthEndExclusive - 1L)
    val outside = tx(TransactionType.INCOME, 9_000_000, jalaliMonthEndExclusive)

    val result =
      GetDashboardDataUseCase.computeFallbackDashboardData(
        listOf(inside, outside),
        emptyList(),
        emptyList()
      )

    // Only the inside transaction is included in the monthly window.
    assertEquals(4_000_000L, result.monthlyIncome)
  }

  @Test
  fun `fallback debt-to-income filters installments on UTC month boundary matching Rust`() {
    val now = System.currentTimeMillis()
    val (_, jalaliMonthEndExclusive) =
      JalaliCalendarHelper.getUtcJalaliMonthBoundaries(now)

    val transactions = listOf(tx(TransactionType.INCOME, 12_000_000, now))
    // Unpaid installment due one ms before the UTC month end (exclusive) is in
    // the current cycle; one exactly at the next-month start is not. This mirrors
    // Rust compute_dashboard_data's `due_date < month_end_ms` half-open filter.
    val inside = inst(2_000_000, paid = false, dueDate = jalaliMonthEndExclusive - 1L)
    val outside = inst(5_000_000, paid = false, dueDate = jalaliMonthEndExclusive)

    val result =
      GetDashboardDataUseCase.computeFallbackDashboardData(
        transactions,
        emptyList(),
        listOf(inside, outside)
      )

    // monthlyDebt = 2M (inside only) / 12M income = 0.1667 — proving the
    // out-of-cycle installment is excluded, identical to the Rust path.
    assertEquals(0.1667, result.debtToIncomeRatio, 0.01)
  }
}
