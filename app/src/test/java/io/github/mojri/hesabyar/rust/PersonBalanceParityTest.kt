package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.RustTest
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.usecase.GetPersonBalancesUseCase
import io.github.mojri.hesabyar.domain.utils.PersonBalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Verifies parity between the native Rust core and the Kotlin fallback for
 * person balance calculations (plans/011 Phase 3).
 */
@Category(RustTest::class)
@Suppress("LongMethod")
class PersonBalanceParityTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  private val useCase = GetPersonBalancesUseCase()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(true)
  }

  private fun createPerson(
    id: Long,
    name: String
  ): Person =
    Person(
      id = id,
      name = name,
      normalizedName = name,
      createdAt = 1_000L
    )

  private fun createLoan(
    id: Long,
    personId: Long?,
    personName: String,
    type: LoanType,
    originalAmount: Long,
    remainingAmount: Long,
    isSettled: Boolean
  ): Loan =
    Loan(
      id = id,
      personName = personName,
      personId = personId,
      type = type,
      originalAmount = originalAmount,
      remainingAmount = remainingAmount,
      description = "loan-$id",
      isSettled = isSettled
    )

  @Test
  fun emptyInputsReturnEmptyListInBothImplementations() {
    val persons = emptyList<Person>()
    val loans = emptyList<Loan>()

    val kotlinResult = PersonBalanceCalculator.compute(persons, loans)
    val nativeResult = RustBridge.computePersonBalancesSync(persons, loans)

    assertTrue("Kotlin fallback must return empty list", kotlinResult.isEmpty())
    assertTrue("Native Rust must return empty list", nativeResult.isEmpty())
  }

  @Test
  fun personsWithoutLoansHaveZeroBalancesInBothImplementations() {
    val persons =
      listOf(
        createPerson(1L, "Ali"),
        createPerson(2L, "Reza")
      )
    val loans = emptyList<Loan>()

    val kotlinResult = PersonBalanceCalculator.compute(persons, loans)
    val nativeResult = RustBridge.computePersonBalancesSync(persons, loans)

    assertEquals("Result count must match", kotlinResult.size, nativeResult.size)
    assertEquals("Count must be 2", 2, kotlinResult.size)

    for (i in kotlinResult.indices) {
      val k = kotlinResult[i]
      val n = nativeResult[i]
      assertEquals("personId parity", k.personId, n.personId)
      assertEquals("personName parity", k.personName, n.personName)
      assertEquals("totalReceivables parity", k.totalReceivables, n.totalReceivables)
      assertEquals("totalDebts parity", k.totalDebts, n.totalDebts)
      assertEquals("netBalance parity", k.netBalance, n.netBalance)
      assertEquals("activeLoanCount parity", k.activeLoanCount, n.activeLoanCount)
      assertEquals("settledLoanCount parity", k.settledLoanCount, n.settledLoanCount)
    }
  }

  @Test
  fun complexLoansProduceExactParityBetweenKotlinAndRust() {
    val persons =
      listOf(
        createPerson(10L, "Maryam"),
        createPerson(20L, "Babak"),
        createPerson(30L, "Sara")
      )
    val loans =
      listOf(
        // Maryam: 1 active receivable, 1 settled receivable
        createLoan(1L, 10L, "Maryam", LoanType.DEBTOR, 5_000_000L, 3_000_000L, false),
        createLoan(2L, 10L, "Maryam", LoanType.DEBTOR, 2_000_000L, 0L, true),
        // Babak: 1 active debt, 1 active receivable
        createLoan(3L, 20L, "Babak", LoanType.CREDITOR, 8_000_000L, 4_000_000L, false),
        createLoan(4L, 20L, "Babak", LoanType.DEBTOR, 10_000_000L, 10_000_000L, false),
        // Sara: only settled debt
        createLoan(5L, 30L, "Sara", LoanType.CREDITOR, 1_000_000L, 0L, true),
        // Unlinked loan (null personId) - must be ignored by both
        createLoan(6L, null, "Unknown", LoanType.DEBTOR, 9_000_000L, 9_000_000L, false),
        // Dangling loan (unknown personId) - must be ignored by both
        createLoan(7L, 999L, "Ghost", LoanType.CREDITOR, 5_000_000L, 5_000_000L, false)
      )

    val kotlinResult = PersonBalanceCalculator.compute(persons, loans)
    val nativeResult = RustBridge.computePersonBalancesSync(persons, loans)

    assertEquals("Result count must match", kotlinResult.size, nativeResult.size)
    assertEquals("Should have 3 persons", 3, kotlinResult.size)

    for (i in kotlinResult.indices) {
      val k = kotlinResult[i]
      val n = nativeResult[i]
      assertEquals("personId parity at index $i", k.personId, n.personId)
      assertEquals("personName parity at index $i", k.personName, n.personName)
      assertEquals("totalReceivables parity at index $i", k.totalReceivables, n.totalReceivables)
      assertEquals("totalDebts parity at index $i", k.totalDebts, n.totalDebts)
      assertEquals("netBalance parity at index $i", k.netBalance, n.netBalance)
      assertEquals("activeLoanCount parity at index $i", k.activeLoanCount, n.activeLoanCount)
      assertEquals("settledLoanCount parity at index $i", k.settledLoanCount, n.settledLoanCount)
    }

    // Verify concrete calculations on Maryam
    val maryam = kotlinResult.first { it.personId == 10L }
    assertEquals("Maryam receivables", 3_000_000L, maryam.totalReceivables)
    assertEquals("Maryam debts", 0L, maryam.totalDebts)
    assertEquals("Maryam net", 3_000_000L, maryam.netBalance)
    assertEquals("Maryam active count", 1, maryam.activeLoanCount)
    assertEquals("Maryam settled count", 1, maryam.settledLoanCount)

    // Verify concrete calculations on Babak
    val babak = kotlinResult.first { it.personId == 20L }
    assertEquals("Babak receivables", 10_000_000L, babak.totalReceivables)
    assertEquals("Babak debts", 4_000_000L, babak.totalDebts)
    assertEquals("Babak net", 6_000_000L, babak.netBalance)
    assertEquals("Babak active count", 2, babak.activeLoanCount)
    assertEquals("Babak settled count", 0, babak.settledLoanCount)
  }

  @Test
  fun useCaseSwitchesToFallbackWhenRustIsDisabled() {
    val persons = listOf(createPerson(1L, "Ali"))
    val loans = listOf(createLoan(1L, 1L, "Ali", LoanType.DEBTOR, 5_000_000L, 5_000_000L, false))

    // Disable Rust
    HesabyarApp.setRustInitializedForTesting(false)

    val fallbackResult = useCase.computePersonBalances(persons, loans)
    assertEquals("Fallback must produce 1 record", 1, fallbackResult.size)
    assertEquals("Fallback net balance", 5_000_000L, fallbackResult[0].netBalance)
    assertEquals("Fallback active count", 1, fallbackResult[0].activeLoanCount)
  }
}
