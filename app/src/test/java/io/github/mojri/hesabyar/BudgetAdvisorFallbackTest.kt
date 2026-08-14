package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Kotlin-fallback coverage for [BudgetAdvisor.calculateFinancialHealthScore].
 *
 * The @Before forces the Rust availability decision off, so these tests
 * genuinely exercise [BudgetAdvisor]'s local fallback path even though the
 * native `hesabyar_core` library sits on the test class path for every test
 * task (see the `rustJvmArgs` wiring in `app/build.gradle.kts`).
 *
 * This class is intentionally **not** tagged with `@Category(RustTest::class)`
 * — it belongs to the fast `testDebugUnitTest` suite alongside the other
 * fallback tests (e.g. [GetAnalyticsUseCaseFallbackTest]).
 * [BudgetAdvisorTest] is the native-path counterpart.
 */
class BudgetAdvisorFallbackTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  private fun createTransaction(
    type: TransactionType,
    amount: Long,
    categoryId: Long = 1L
  ): Transaction = Transaction(type = type, amount = amount, categoryId = categoryId, description = "test")

  private fun createBankLoan(isSettled: Boolean): BankLoan =
    BankLoan(
      bankName = "بانک ملی",
      loanName = "وام خودرو",
      receivedAmount = 10_000_000,
      monthlyInstallmentAmount = 1_000_000,
      numberOfInstallments = 12,
      totalRepayableAmount = 12_000_000,
      totalInterest = 2_000_000,
      startDate = 1_700_000_000_000,
      description = "",
      isSettled = isSettled
    )

  private fun createSettledLoan(): Loan =
    Loan(
      personName = "test",
      type = LoanType.CREDITOR,
      originalAmount = 10_000_000,
      remainingAmount = 10_000_000,
      description = "test",
      isSettled = true
    )

  @Test
  fun calculatefinancialhealthscoreLocalFallbackWhenRustUnavailable() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 2_000_000)
      )
    val score =
      BudgetAdvisor.calculateFinancialHealthScore(
        transactions,
        emptyList(),
        emptyList(),
        emptyList()
      )
    // Deterministic local computation: savings rate 0.8 (+25) + no debt (+15) + 1 category (+0) = 90.
    assertEquals(90, score)
    assertTrue(score in 0..100)

    // Determinism: a second call yields the same result (no flaky time dependence).
    assertEquals(
      score,
      BudgetAdvisor.calculateFinancialHealthScore(transactions, emptyList(), emptyList(), emptyList())
    )
  }

  @Test
  fun calculatefinancialhealthscoreEmptyDataReturnsZeroViaLocalFallback() {
    assertEquals(
      0,
      BudgetAdvisor.calculateFinancialHealthScore(emptyList(), emptyList(), emptyList(), emptyList())
    )
  }

  @Test
  fun getofflineforecastSettledBankLoansStillNoDataViaLocalFallback() {
    // A settled bank loan is not an active obligation and must not suppress the
    // "no data" message — parity with the Rust get_offline_forecast guard.
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        emptyList(),
        emptyList(),
        listOf(createBankLoan(isSettled = true))
      )
    assertTrue(
      "settled bank loan must not produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastSettledLoansStillNoDataViaLocalFallback() {
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        listOf(createSettledLoan()),
        emptyList(),
        emptyList()
      )
    assertTrue(
      "settled loan must not produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastPaidInstallmentsOnlyNoDataViaLocalFallback() {
    // Only paid installments — no unpaid installments due within 30 days.
    // Parity with Rust get_offline_forecast: total_obligations only counts
    // unpaid upcoming installments, so paid installments must not suppress
    // the "no data" message.
    val paidInstallment =
      Installment(
        title = "قسط ماهانه",
        amount = 1_000_000,
        dueDate = 1_700_000_000_000L,
        isPaid = true
      )
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        emptyList(),
        listOf(paidInstallment),
        emptyList()
      )
    assertTrue(
      "paid installments must not produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastDebtorLoansOnlyNoDataViaLocalFallback() {
    // Only unsettled DEBTOR loans — Rust's total_obligations only counts
    // unsettled CREDITOR loans, so DEBTOR loans must not suppress the
    // "no data" message.
    val debtorLoan =
      Loan(
        personName = "طرف مقابل",
        type = LoanType.DEBTOR,
        originalAmount = 5_000_000,
        remainingAmount = 5_000_000,
        description = "وام شخصی",
        isSettled = false
      )
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        listOf(debtorLoan),
        emptyList(),
        emptyList()
      )
    assertTrue(
      "DEBTOR loan must not produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastActiveBankLoanProducesForecastViaLocalFallback() {
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        emptyList(),
        emptyList(),
        listOf(createBankLoan(isSettled = false))
      )
    assertFalse(
      "active bank loan must produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }
}
