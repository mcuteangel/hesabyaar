package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
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
}
