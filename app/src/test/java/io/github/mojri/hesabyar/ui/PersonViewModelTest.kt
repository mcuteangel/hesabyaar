package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.usecase.GetPersonBalancesUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val fakeRepository = TestFakeRepository()
  private val manageLoanUseCase = ManageLoanUseCase(fakeRepository)
  private val getPersonBalancesUseCase = GetPersonBalancesUseCase()

  /**
   * Lazily constructed so `viewModelScope` binds to the Main dispatcher that
   * [setUp] installs. Eager construction would capture the production Main
   * dispatcher before [Dispatchers.setMain] runs.
   */
  private val viewModel by lazy {
    PersonViewModel(fakeRepository, manageLoanUseCase, getPersonBalancesUseCase)
  }

  private class TestFakeRepository : FakeRepository() {
    val personsFlow = MutableStateFlow<List<Person>>(emptyList())
    val loansFlow = MutableStateFlow<List<Loan>>(emptyList())
    val insertedLoans = mutableListOf<Loan>()
    var makeRepaymentCallCount = 0
    var failNextInsert = false
    var failNextRepayment = false

    override val allPersons = personsFlow
    override val allLoans = loansFlow

    override suspend fun insertLoan(loan: Loan): Long {
      if (failNextInsert) {
        failNextInsert = false
        throw IllegalStateException(INSERT_FAILURE_MESSAGE)
      }
      insertedLoans.add(loan)
      return insertedLoans.size.toLong()
    }

    override suspend fun addPaymentToLoan(
      loanId: Long,
      amount: Long,
      notes: String,
      customDate: Long?
    ): Boolean {
      makeRepaymentCallCount++
      if (failNextRepayment) {
        failNextRepayment = false
        return false
      }
      return true
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun personBalancesFlowFiltersBySearchQuery() =
    runTest(testDispatcher) {
      val collectJob =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          viewModel.personBalances.collect {}
        }

      val personAli = Person(id = 1L, name = "Ali Reza", normalizedName = "alireza")
      val personSara = Person(id = 2L, name = NAME_SARA, normalizedName = "sara")
      fakeRepository.personsFlow.value = listOf(personAli, personSara)

      advanceUntilIdle()
      assertEquals("All persons initially visible", 2, viewModel.personBalances.value.size)

      viewModel.setSearchQuery("ali")
      advanceUntilIdle()

      val filtered = viewModel.personBalances.value
      assertEquals("Only Ali matches", 1, filtered.size)
      assertEquals("Ali Reza", filtered[0].personName)

      viewModel.setSearchQuery("")
      advanceUntilIdle()
      assertEquals("Reset query restores full list", 2, viewModel.personBalances.value.size)

      collectJob.cancel()
    }

  @Test
  fun addLoanForPersonDispatchesInsert() =
    runTest(testDispatcher) {
      viewModel.addLoanForPerson(
        personId = 42L,
        personName = NAME_ALI,
        type = LoanType.DEBTOR,
        amount = 100_000L,
        description = TEST_LOAN_DESC
      )
      advanceUntilIdle()

      assertEquals(MSG_ONE_LOAN_INSERTED, 1, fakeRepository.insertedLoans.size)
      val loan = fakeRepository.insertedLoans[0]
      assertEquals(42L, loan.personId)
      assertEquals(NAME_ALI, loan.personName)
      assertEquals(100_000L, loan.originalAmount)
      assertEquals(LoanType.DEBTOR, loan.type)
    }

  @Test
  fun addLoanForPersonReportsSuccessThroughOnResult() =
    runTest(testDispatcher) {
      var succeeded: Boolean? = null
      viewModel.addLoanForPerson(
        personId = 42L,
        personName = NAME_ALI,
        type = LoanType.DEBTOR,
        amount = 100_000L,
        description = TEST_LOAN_DESC,
        onResult = { succeeded = it },
      )
      advanceUntilIdle()

      assertEquals("onResult reports success", true, succeeded)
      assertEquals(MSG_ONE_LOAN_INSERTED, 1, fakeRepository.insertedLoans.size)
    }

  @Test
  fun addLoanForPersonReportsFailureThroughOnResult() =
    runTest(testDispatcher) {
      fakeRepository.failNextInsert = true
      var succeeded: Boolean? = null
      viewModel.addLoanForPerson(
        personId = 42L,
        personName = NAME_ALI,
        type = LoanType.DEBTOR,
        amount = 100_000L,
        description = TEST_LOAN_DESC,
        onResult = { succeeded = it },
      )
      advanceUntilIdle()

      assertEquals("onResult reports failure", false, succeeded)
      assertEquals("No loan inserted", 0, fakeRepository.insertedLoans.size)
    }

  @Test
  fun settleFullyWithEmptyLoanFlowDoesNotThrow() =
    runTest(testDispatcher) {
      fakeRepository.loansFlow.value = emptyList()

      viewModel.settleFully(personId = 5L)
      advanceUntilIdle()

      assertEquals("No repayments attempted", 0, fakeRepository.makeRepaymentCallCount)
    }

  @Test
  fun settleFullyIteratesUnsettledLoans() =
    runTest(testDispatcher) {
      val loan1 =
        Loan(
          id = 101L,
          personId = 5L,
          personName = NAME_SARA,
          type = LoanType.DEBTOR,
          originalAmount = 50_000L,
          remainingAmount = 25_000L,
          description = "",
          date = 1000L,
          isSettled = false
        )
      val loan2 =
        Loan(
          id = 102L,
          personId = 5L,
          personName = NAME_SARA,
          type = LoanType.CREDITOR,
          originalAmount = 30_000L,
          remainingAmount = 0L,
          description = "",
          date = 1000L,
          isSettled = true
        )
      val otherPersonLoan =
        Loan(
          id = 103L,
          personId = 99L,
          personName = "Other",
          type = LoanType.DEBTOR,
          originalAmount = 10_000L,
          remainingAmount = 10_000L,
          description = "",
          date = 1000L,
          isSettled = false
        )

      fakeRepository.loansFlow.value = listOf(loan1, loan2, otherPersonLoan)

      viewModel.settleFully(personId = 5L)
      advanceUntilIdle()

      assertEquals("Only active loan for person 5 repaid", 1, fakeRepository.makeRepaymentCallCount)
    }

  @Test
  fun settleFullyReportsSuccessThroughOnResult() =
    runTest(testDispatcher) {
      val loan =
        Loan(
          id = 101L,
          personId = 5L,
          personName = NAME_SARA,
          type = LoanType.DEBTOR,
          originalAmount = 50_000L,
          remainingAmount = 25_000L,
          description = "",
          date = 1000L,
          isSettled = false
        )
      fakeRepository.loansFlow.value = listOf(loan)

      var succeeded: Boolean? = null
      viewModel.settleFully(personId = 5L, onResult = { succeeded = it })
      advanceUntilIdle()

      assertEquals("settleFully reports success", true, succeeded)
      assertEquals("1 repayment attempted", 1, fakeRepository.makeRepaymentCallCount)
    }

  @Test
  fun settleFullyReportsFailureWhenRepaymentFails() =
    runTest(testDispatcher) {
      val loan =
        Loan(
          id = 101L,
          personId = 5L,
          personName = NAME_SARA,
          type = LoanType.DEBTOR,
          originalAmount = 50_000L,
          remainingAmount = 25_000L,
          description = "",
          date = 1000L,
          isSettled = false
        )
      fakeRepository.loansFlow.value = listOf(loan)
      fakeRepository.failNextRepayment = true

      var succeeded: Boolean? = null
      viewModel.settleFully(personId = 5L, onResult = { succeeded = it })
      advanceUntilIdle()

      assertEquals("settleFully reports failure", false, succeeded)
      assertEquals("1 repayment attempted", 1, fakeRepository.makeRepaymentCallCount)
    }

  @Test
  fun getLoansForPersonFiltersByPersonIdAndSortsChronologically() =
    runTest(testDispatcher) {
      val loanOlder =
        Loan(
          id = 1L,
          personId = 10L,
          personName = "A",
          type = LoanType.DEBTOR,
          originalAmount = 100L,
          remainingAmount = 100L,
          description = "",
          date = 1_000L,
          isSettled = false
        )
      val loanNewer =
        Loan(
          id = 2L,
          personId = 10L,
          personName = "A",
          type = LoanType.DEBTOR,
          originalAmount = 200L,
          remainingAmount = 200L,
          description = "",
          date = 2_000L,
          isSettled = false
        )
      val otherPersonLoan =
        Loan(
          id = 3L,
          personId = 20L,
          personName = "B",
          type = LoanType.DEBTOR,
          originalAmount = 300L,
          remainingAmount = 300L,
          description = "",
          date = 500L,
          isSettled = false
        )
      // Supplied in reverse chronological order to verify ascending sort.
      fakeRepository.loansFlow.value = listOf(loanNewer, otherPersonLoan, loanOlder)

      val person10Loans = viewModel.getLoansForPerson(10L).first()
      assertEquals("Only loans for person 10 returned", 2, person10Loans.size)
      assertEquals("Oldest loan first", 1L, person10Loans[0].id)
      assertEquals("Newer loan second", 2L, person10Loans[1].id)
    }

  private companion object {
    const val NAME_ALI = "Ali"
    const val NAME_SARA = "Sara"
    const val TEST_LOAN_DESC = "Test loan"
    const val INSERT_FAILURE_MESSAGE = "insert failed"
    const val MSG_ONE_LOAN_INSERTED = "1 loan inserted"
  }
}
