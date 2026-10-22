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
import io.github.mojri.hesabyar.data.LoanUpdateResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for issue #532: editing a loan's creation date must move
 * the stamped disbursement transaction to the new date, so point-in-time
 * balances count it on the right day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@org.junit.experimental.categories.Category(RustTest::class)
class LoanDisbursementDateUpdateTest {
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

  private suspend fun seedLoansCategory() {
    createRepository().insertCategory(
      Category(
        name = "Loans",
        key = "Loans",
        icon = "HistoryEdu",
        color = 0xFF4CAF50L,
        type = CategoryType.BOTH
      )
    )
  }

  private fun disbursementFor(loanId: Long) =
    database.transactionDao().getAllTransactionsBlocking().single { it.loanId == loanId }

  @Test
  fun dateEdit_movesStampedDisbursementTransactionDate() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Ali",
            type = LoanType.DEBTOR,
            originalAmount = 5_000L,
            remainingAmount = 5_000L,
            description = "test",
            date = 5_000L,
            tracked = true,
            accountId = 1L
          ),
          recordInitial = true
        )
      assertEquals(5_000L, disbursementFor(loanId).date)

      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(date = 9_000L)))

      assertEquals(9_000L, requireNotNull(database.loanDao().getLoanById(loanId)).date)
      assertEquals(9_000L, disbursementFor(loanId).date)
    }

  @Test
  fun dateUnchanged_leavesDisbursementTransactionAlone() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Ali",
            type = LoanType.DEBTOR,
            originalAmount = 5_000L,
            remainingAmount = 5_000L,
            description = "test",
            date = 5_000L,
            tracked = true,
            accountId = 1L
          ),
          recordInitial = true
        )

      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(personName = "Sara")))

      val updated = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals("Sara", updated.personName)
      assertEquals(5_000L, updated.date)
      assertEquals(5_000L, disbursementFor(loanId).date)
    }

  @Test
  fun dateEdit_untrackedLoan_noDisbursementRow() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Ali",
            type = LoanType.DEBTOR,
            originalAmount = 5_000L,
            remainingAmount = 5_000L,
            description = "test",
            date = 5_000L
          ),
          recordInitial = true
        )
      assertTrue(database.transactionDao().getAllTransactionsBlocking().isEmpty())

      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(date = 9_000L)))

      assertEquals(9_000L, requireNotNull(database.loanDao().getLoanById(loanId)).date)
      assertTrue(database.transactionDao().getAllTransactionsBlocking().isEmpty())
    }
}
