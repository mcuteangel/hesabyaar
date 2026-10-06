package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.rust.PersonBalanceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Direct unit tests for [GetPersonBalancesUseCase] covering native dispatch,
 * fallback on disabled Rust, and empty input edge cases.
 */
class GetPersonBalancesUseCaseTest {
  @get:Rule
  val rustIsolationRule = RustIsolationRule()

  private val useCase = GetPersonBalancesUseCase()

  @Test
  fun emptyInputsReturnEmptyList() {
    val result = useCase.computePersonBalances(emptyList(), emptyList())
    assertTrue("Empty inputs yield empty result", result.isEmpty())
  }

  @Test
  fun computePersonBalancesDispatchesToNativeOrFallback() {
    val person = Person(id = 1L, name = NAME_ALI, normalizedName = "ali")
    val loan =
      Loan(
        id = 10L,
        personId = 1L,
        personName = NAME_ALI,
        type = LoanType.DEBTOR,
        originalAmount = 100_000L,
        remainingAmount = 100_000L,
        description = "",
        date = 1000L,
        isSettled = false
      )

    val balances = useCase.computePersonBalances(listOf(person), listOf(loan))
    assertEquals("Should produce 1 balance", 1, balances.size)
    assertEquals("Person ID", 1L, balances[0].personId)
    assertEquals("Person name", NAME_ALI, balances[0].personName)
    assertEquals("Total receivables", 100_000L, balances[0].totalReceivables)
    assertEquals("Net balance", 100_000L, balances[0].netBalance)
  }

  @Test
  fun fallbackProducesBalancesWhenRustDisabled() {
    val previous = HesabyarApp.getRustInitializedOverrideForTesting()
    HesabyarApp.setRustInitializedForTesting(false)
    try {
      val person = Person(id = 2L, name = NAME_REZA, normalizedName = "reza")
      val loan =
        Loan(
          id = 20L,
          personId = 2L,
          personName = NAME_REZA,
          type = LoanType.CREDITOR,
          originalAmount = 50_000L,
          remainingAmount = 50_000L,
          description = "",
          date = 1000L,
          isSettled = false
        )

      val balances = useCase.computePersonBalances(listOf(person), listOf(loan))
      assertEquals("Fallback produces 1 record", 1, balances.size)
      assertEquals("Reza debts", 50_000L, balances[0].totalDebts)
      assertEquals("Reza net", -50_000L, balances[0].netBalance)
    } finally {
      HesabyarApp.setRustInitializedForTesting(previous)
    }
  }

  @Test
  fun fromNativeMapsEverySummaryField() {
    val native =
      PersonBalanceSummary(
        personId = 7L,
        personName = NAME_ALI,
        totalReceivables = 90_000L,
        totalDebts = 40_000L,
        netBalance = 50_000L,
        activeLoanCount = 2,
        settledLoanCount = 3,
      )

    val mapped = useCase.fromNative(listOf(native)).single()

    assertEquals("Person ID", 7L, mapped.personId)
    assertEquals("Person name", NAME_ALI, mapped.personName)
    assertEquals("Receivables", 90_000L, mapped.totalReceivables)
    assertEquals("Debts", 40_000L, mapped.totalDebts)
    assertEquals("Net balance", 50_000L, mapped.netBalance)
    assertEquals("Active loan count", 2, mapped.activeLoanCount)
    assertEquals("Settled loan count", 3, mapped.settledLoanCount)
  }

  private companion object {
    const val NAME_ALI = "Ali"
    const val NAME_REZA = "Reza"
  }
}
