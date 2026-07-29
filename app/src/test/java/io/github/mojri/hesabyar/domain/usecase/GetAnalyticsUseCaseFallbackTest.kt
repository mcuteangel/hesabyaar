package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.BankLoan
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GetAnalyticsUseCaseFallbackTest {
  private val useCase = GetAnalyticsUseCase()

  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  @After
  fun tearDown() {
    HesabyarApp.setRustInitializedForTesting(true)
  }

  @Test
  fun `kotlin fallback populates bank loan summaries`() =
    runTest {
      val bankLoans =
        listOf(
          BankLoan(
            bankName = "بانک",
            loanName = "l",
            receivedAmount = 100_000_000L,
            monthlyInstallmentAmount = 10_000_000L,
            numberOfInstallments = 10,
            totalRepayableAmount = 120_000_000L,
            totalInterest = 20_000_000L,
            startDate = 1_700_000_000_000L,
            description = "",
            isSettled = false
          )
        )
      val result =
        useCase.computeAnalytics(emptyList(), emptyList(), emptyList(), emptyList(), bankLoans)

      assertEquals(1, result.bankLoans.size)
      assertEquals(120_000_000L, result.bankLoansTotalDebt)
      assertEquals(120_000_000L, result.bankLoans[0].remainingDebt)
    }

  @Test
  fun `kotlin fallback zeroes debt for settled loan`() =
    runTest {
      val bankLoans =
        listOf(
          BankLoan(
            bankName = "b",
            loanName = "l",
            receivedAmount = 100_000_000L,
            monthlyInstallmentAmount = 10_000_000L,
            numberOfInstallments = 10,
            totalRepayableAmount = 120_000_000L,
            totalInterest = 20_000_000L,
            startDate = 1_700_000_000_000L,
            description = "",
            isSettled = true
          )
        )
      val result =
        useCase.computeAnalytics(emptyList(), emptyList(), emptyList(), emptyList(), bankLoans)

      assertEquals(0L, result.bankLoansTotalDebt)
      assertTrue(result.bankLoans.all { it.remainingDebt == 0L })
    }
}
