package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.LoanType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class ManageLoanUseCaseTest {
  private val fake = FakeRepository()
  private val useCase = ManageLoanUseCase(fake)

  @Test
  fun addLoanWithPersonNameUpsertsAndLinksPersonId() =
    runTest {
      val loanId =
        useCase.addLoan(
          personName = "Ali Reza",
          type = LoanType.CREDITOR,
          amount = 10_000_000L,
          description = "loan 1"
        )
      val loans = fake.allLoans.first()
      val storedLoan = loans.first { it.id == loanId }
      assertNotNull(storedLoan.personId)
      val persons = fake.getAllPersonsIncludingArchived()
      assertEquals(1, persons.size)
      assertEquals(storedLoan.personId, persons.first().id)
      assertEquals("Ali Reza", persons.first().name)
    }

  @Test
  fun addLoanWithExplicitPersonIdPreservesProvidedPersonId() =
    runTest {
      val explicitPersonId = 99L
      val loanId =
        useCase.addLoan(
          personName = "Sara",
          type = LoanType.DEBTOR,
          amount = 5_000_000L,
          description = "loan 2",
          personId = explicitPersonId
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertEquals(explicitPersonId, storedLoan.personId)
    }

  @Test
  fun addTrackedLoanWithPersonNameUpsertsAndLinksPersonId() =
    runTest {
      val loanId =
        useCase.addTrackedLoan(
          personName = "Mohammad",
          type = LoanType.CREDITOR,
          amount = 20_000_000L,
          description = "tracked loan",
          tracked = true,
          accountId = 1L
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertNotNull(storedLoan.personId)
      val person = fake.getPersonById(storedLoan.personId!!)
      assertNotNull(person)
      assertEquals("Mohammad", person!!.name)
    }

  @Test
  fun addTrackedLoanWithExplicitPersonIdPreservesProvidedPersonId() =
    runTest {
      val explicitPersonId = 77L
      val loanId =
        useCase.addTrackedLoan(
          personName = "Zahra",
          type = LoanType.DEBTOR,
          amount = 15_000_000L,
          description = "tracked loan with id",
          tracked = true,
          accountId = 1L,
          personId = explicitPersonId
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertEquals(explicitPersonId, storedLoan.personId)
    }

  @Test
  fun addTrackedLoanRequiresValidAccountIdWhenTracked() =
    runTest {
      try {
        useCase.addTrackedLoan(
          personName = "Test",
          type = LoanType.CREDITOR,
          amount = 1_000_000L,
          description = "",
          tracked = true,
          accountId = null
        )
        fail("tracked loan with null accountId must throw IllegalArgumentException")
      } catch (expected: IllegalArgumentException) {
        // Expected
      }
    }
}
