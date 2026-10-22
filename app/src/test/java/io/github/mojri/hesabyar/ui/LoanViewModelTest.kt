package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.LoanUpdateResult
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoanViewModelTest {
  private lateinit var repository: FakeRepository
  private lateinit var viewModel: LoanViewModel
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeRepository()
    viewModel = LoanViewModel(ManageLoanUseCase(repository))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun makeRepaymentReportsFailureWhenRepositoryThrows() =
    runTest(testDispatcher) {
      repository.addPaymentBehavior = { throw IllegalStateException("db down") }
      var reported: Boolean? = null

      viewModel.makeRepayment(1L, 100L, "notes", null) { reported = it }
      advanceUntilIdle()

      assertFalse("repository failure must reach the UI callback as false", reported ?: true)
    }

  private fun sampleLoan() =
    Loan(
      id = 1L,
      personName = "Ali",
      type = LoanType.DEBTOR,
      originalAmount = 5_000L,
      remainingAmount = 5_000L,
      description = "test",
      date = 1_700_000_000_000L
    )

  @Test
  fun updateLoanReportsSuccessForSeededLoan() =
    runTest(testDispatcher) {
      val loan = sampleLoan()
      repository.loansList.add(loan)
      var reported: LoanUpdateResult? = null

      viewModel.updateLoan(loan.copy(personName = "Sara")) { reported = it }
      advanceUntilIdle()

      assertEquals(LoanUpdateResult.Success, reported)
      assertEquals("the edited loan must be stored", "Sara", repository.loansList[0].personName)
    }

  @Test
  fun updateLoanReportsChronologyRejectionFromRepository() =
    runTest(testDispatcher) {
      repository.updateLoanBehavior = { LoanUpdateResult.ChronologyRejected }
      var reported: LoanUpdateResult? = null

      viewModel.updateLoan(sampleLoan()) { reported = it }
      advanceUntilIdle()

      assertEquals(LoanUpdateResult.ChronologyRejected, reported)
    }

  @Test
  fun updateLoanReportsNotFoundForUnknownLoan() =
    runTest(testDispatcher) {
      var reported: LoanUpdateResult? = null

      viewModel.updateLoan(sampleLoan()) { reported = it }
      advanceUntilIdle()

      assertEquals(LoanUpdateResult.NotFound, reported)
    }

  @Test
  fun updateLoanReportsFailureWhenRepositoryThrows() =
    runTest(testDispatcher) {
      repository.updateLoanBehavior = { throw IllegalStateException("db down") }
      var reported: LoanUpdateResult? = null

      viewModel.updateLoan(sampleLoan()) { reported = it }
      advanceUntilIdle()

      assertEquals("repository failure must reach the UI callback as Failed", LoanUpdateResult.Failed, reported)
    }

  @Test
  fun makeRepaymentReportsRepositoryRejection() =
    runTest(testDispatcher) {
      var reported: Boolean? = null

      viewModel.makeRepayment(1L, 100L, "notes", null) { reported = it }
      advanceUntilIdle()

      assertFalse(reported ?: true)
    }
}
