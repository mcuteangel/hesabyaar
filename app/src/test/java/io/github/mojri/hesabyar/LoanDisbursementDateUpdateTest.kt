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
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
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
 * the disbursement transaction to the new date, so point-in-time balances
 * count it on the right day. Covers the stamped row, the legacy (pre-#242,
 * unstamped) row, and the no-op cases.
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

  private suspend fun loansCategoryId(): Long =
    requireNotNull(database.categoryDao().getCategoryByKey("Loans")).id

  private fun disbursementFor(loanId: Long) =
    database.transactionDao().getAllTransactionsBlocking().single { it.loanId == loanId }

  private suspend fun seedTrackedLoan(
    loanDate: Long,
    recordInitial: Boolean
  ): Long {
    val repo = createRepository()
    return repo.insertLoanWithInitial(
      Loan(
        personName = "Ali",
        type = LoanType.DEBTOR,
        originalAmount = 5_000L,
        remainingAmount = 5_000L,
        description = "test",
        date = loanDate,
        tracked = true,
        accountId = 1L
      ),
      recordInitial = recordInitial
    )
  }

  @Test
  fun dateEditMovesStampedDisbursementTransactionDate() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val loanId = seedTrackedLoan(loanDate = 5_000L, recordInitial = true)
      assertEquals(5_000L, disbursementFor(loanId).date)

      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(date = 9_000L)))

      assertEquals(9_000L, requireNotNull(database.loanDao().getLoanById(loanId)).date)
      assertEquals(9_000L, disbursementFor(loanId).date)
    }

  @Test
  fun dateUnchangedLeavesDisbursementTransactionAlone() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val loanId = seedTrackedLoan(loanDate = 5_000L, recordInitial = true)

      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(personName = "Sara")))

      val updated = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals("Sara", updated.personName)
      assertEquals(5_000L, updated.date)
      assertEquals(5_000L, disbursementFor(loanId).date)
    }

  @Test
  fun dateEditUntrackedLoanNoDisbursementRow() =
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

  @Test
  fun dateEditMovesLegacyDisbursementTransactionDate() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      // Legacy loan: tracked, but created before the loanId stamp, so no
      // disbursement row was written by insertLoanWithInitial.
      val loanId = seedTrackedLoan(loanDate = 5_000L, recordInitial = false)
      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = loansCategoryId(),
          amount = 5_000L,
          description = "legacy disbursement",
          personName = "Ali",
          date = 5_000L
        )
      )

      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(date = 9_000L)))

      val moved = database.transactionDao().getAllTransactionsBlocking().single()
      assertEquals(9_000L, moved.date)
    }

  @Test
  fun dateEditDoesNotMoveUnrelatedSameLoanIdRow() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val loanId = seedTrackedLoan(loanDate = 5_000L, recordInitial = true)
      // Corrupt duplicate: same loanId but a different date, e.g. from a
      // bad restore. Only the row in sync with the loan date may move.
      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = loansCategoryId(),
          amount = 5_000L,
          description = "duplicate",
          personName = "Ali",
          date = 7_000L,
          loanId = loanId
        )
      )

      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(date = 9_000L)))

      val transactions = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(2, transactions.size)
      assertEquals(9_000L, transactions.single { it.description != "duplicate" }.date)
      assertEquals(7_000L, transactions.single { it.description == "duplicate" }.date)
    }
}
