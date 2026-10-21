package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for issue #431: payments backdated before the loan creation
 * date or before an already-recorded payment must be rejected without side
 * effects, while chronologically ordered payments keep working.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@org.junit.experimental.categories.Category(RustTest::class)
class LoanPaymentDateValidationTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(true)
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    // Production seeds the default account (id=1) on fresh create
    // (AppDatabase onCreate); the in-memory test DB bypasses it, so mirror
    // the invariant here.
    database.accountDao().insertAllBlocking(listOf(AccountEntity.DEFAULT_ACCOUNT))
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun createRepository(): HesabyarRepository =
    HesabyarRepository(
      database.transactionDao(),
      database.loanDao(),
      database.installmentDao(),
      database.paymentHistoryDao(),
      database.categoryDao(),
      database.bankLoanDao(),
      database.accountDao(),
      database.personDao(),
      database
    )

  private suspend fun seedLoan(
    loanDate: Long,
    remainingAmount: Long = 5_000L
  ): Long {
    val repo = createRepository()
    repo.insertCategory(
      Category(
        name = "Loans",
        key = "Loans",
        icon = "HistoryEdu",
        color = 0xFF4CAF50L,
        type = CategoryType.BOTH
      )
    )
    return repo.insertLoan(
      Loan(
        personName = "Ali",
        type = LoanType.DEBTOR,
        originalAmount = 5_000L,
        remainingAmount = remainingAmount,
        description = "test",
        date = loanDate
      )
    )
  }

  @Test
  fun paymentBeforeLoanCreationDateIsRejectedWithoutSideEffects() =
    runTest {
      val repo = createRepository()
      val loanId = seedLoan(loanDate = 5_000L)

      val success = repo.addPaymentToLoan(loanId, 1_000L, "backdated", 4_000L)
      assertFalse(success)

      assertEquals(0, database.paymentHistoryDao().getAllPaymentHistoriesBlocking().size)
      assertEquals(0, database.transactionDao().getAllTransactionsBlocking().size)
      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(5_000L, loan.remainingAmount)
      assertFalse(loan.isSettled)
    }

  @Test
  fun paymentBeforeExistingPaymentIsRejectedWithoutSideEffects() =
    runTest {
      val repo = createRepository()
      val loanId = seedLoan(loanDate = 5_000L)

      assertTrue(repo.addPaymentToLoan(loanId, 1_000L, "first", 8_000L))
      val backdated = repo.addPaymentToLoan(loanId, 1_000L, "backdated", 7_000L)
      assertFalse(backdated)

      assertEquals(1, database.paymentHistoryDao().getAllPaymentHistoriesBlocking().size)
      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(4_000L, loan.remainingAmount)
      assertFalse(loan.isSettled)
    }

  @Test
  fun orderedPaymentsAreAccepted() =
    runTest {
      val repo = createRepository()
      val loanId = seedLoan(loanDate = 5_000L)

      assertTrue(repo.addPaymentToLoan(loanId, 1_000L, "first", 6_000L))
      assertTrue(repo.addPaymentToLoan(loanId, 1_000L, "second", 9_000L))

      assertEquals(2, database.paymentHistoryDao().getAllPaymentHistoriesBlocking().size)
      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(3_000L, loan.remainingAmount)
      assertFalse(loan.isSettled)
    }

  @Test
  fun paymentOnCreationDateAndEqualDatesAreAccepted() =
    runTest {
      val repo = createRepository()
      val loanId = seedLoan(loanDate = 5_000L)

      assertTrue(repo.addPaymentToLoan(loanId, 1_000L, "on creation date", 5_000L))
      assertTrue(repo.addPaymentToLoan(loanId, 1_000L, "same day", 7_000L))
      assertTrue(repo.addPaymentToLoan(loanId, 1_000L, "same day again", 7_000L))

      assertEquals(3, database.paymentHistoryDao().getAllPaymentHistoriesBlocking().size)
      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(2_000L, loan.remainingAmount)
      assertFalse(loan.isSettled)
    }
}
