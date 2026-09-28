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
  private lateinit var fakeRepository: TestFakeRepository
  private lateinit var manageLoanUseCase: ManageLoanUseCase
  private lateinit var getPersonBalancesUseCase: GetPersonBalancesUseCase
  private lateinit var viewModel: PersonViewModel

  private class TestFakeRepository : FakeRepository() {
    val personsFlow = MutableStateFlow<List<Person>>(emptyList())
    val loansFlow = MutableStateFlow<List<Loan>>(emptyList())
    val insertedLoans = mutableListOf<Loan>()
    var makeRepaymentCallCount = 0

    override val allPersons = personsFlow
    override val allLoans = loansFlow

    override suspend fun insertLoan(loan: Loan): Long {
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
      return true
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    fakeRepository = TestFakeRepository()
    manageLoanUseCase = ManageLoanUseCase(fakeRepository)
    getPersonBalancesUseCase = GetPersonBalancesUseCase()
    viewModel = PersonViewModel(fakeRepository, manageLoanUseCase, getPersonBalancesUseCase)
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
      val personSara = Person(id = 2L, name = "Sara", normalizedName = "sara")
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
        personName = "Ali",
        type = LoanType.DEBTOR,
        amount = 100_000L,
        description = "Test loan"
      )
      advanceUntilIdle()

      assertEquals("1 loan inserted", 1, fakeRepository.insertedLoans.size)
      val loan = fakeRepository.insertedLoans[0]
      assertEquals(42L, loan.personId)
      assertEquals("Ali", loan.personName)
      assertEquals(100_000L, loan.originalAmount)
      assertEquals(LoanType.DEBTOR, loan.type)
    }

  @Test
  fun settleFullyIteratesUnsettledLoans() =
    runTest(testDispatcher) {
      val loan1 =
        Loan(
          id = 101L,
          personId = 5L,
          personName = "Sara",
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
          personName = "Sara",
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
  fun getLoansForPersonFiltersByPersonId() =
    runTest(testDispatcher) {
      val loan1 =
        Loan(
          id = 1L,
          personId = 10L,
          personName = "A",
          type = LoanType.DEBTOR,
          originalAmount = 100L,
          remainingAmount = 100L,
          description = "",
          date = 1L,
          isSettled = false
        )
      val loan2 =
        Loan(
          id = 2L,
          personId = 20L,
          personName = "B",
          type = LoanType.DEBTOR,
          originalAmount = 200L,
          remainingAmount = 200L,
          description = "",
          date = 1L,
          isSettled = false
        )
      fakeRepository.loansFlow.value = listOf(loan1, loan2)

      val person10Loans = viewModel.getLoansForPerson(10L).first()
      assertEquals("Only loan for person 10 returned", 1, person10Loans.size)
      assertEquals(1L, person10Loans[0].id)
    }
}
