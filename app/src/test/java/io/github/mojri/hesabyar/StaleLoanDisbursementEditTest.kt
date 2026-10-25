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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for issue #535: a stale transaction edit must not
 * undo a loan date edit. The disbursement row keeps the loan's current
 * date, even when the editor snapshot predates the loan date edit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class StaleLoanDisbursementEditTest {
  private companion object {
    const val ORIGINAL_DATE = 5_000L
    const val NEW_DATE = 9_000L
    const val LOAN_AMOUNT = 5_000L
    const val ORPHAN_LOAN_ID = 999_999L
  }

  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
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

  private suspend fun loansCategoryId(): Long = requireNotNull(database.categoryDao().getCategoryByKey("Loans")).id

  private fun transactionById(id: Long): Transaction =
    database.transactionDao().getAllTransactionsBlocking().single { it.id == id }

  @Test
  fun staleTransactionEditKeepsNewLoanDate() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Ali",
            type = LoanType.DEBTOR,
            originalAmount = LOAN_AMOUNT,
            remainingAmount = LOAN_AMOUNT,
            description = "test",
            date = ORIGINAL_DATE,
            tracked = true,
            accountId = 1L
          ),
          recordInitial = true
        )
      // The editor loads the disbursement row before the loan date edit.
      val snapshot =
        database.transactionDao().getAllTransactionsBlocking().single { it.loanId == loanId }
      assertEquals(ORIGINAL_DATE, snapshot.date)

      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(date = NEW_DATE)))

      // The stale editor saves after the loan date edit. The disbursement
      // date must follow the loan, while the edited description still lands.
      repo.updateTransaction(snapshot.copy(description = "edited", date = ORIGINAL_DATE))

      assertEquals(NEW_DATE, requireNotNull(database.loanDao().getLoanById(loanId)).date)
      val updated = transactionById(snapshot.id)
      assertEquals(NEW_DATE, updated.date)
      assertEquals("edited", updated.description)
    }

  @Test
  fun nonLoanTransactionEditHonorsEditedDate() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val txId =
        database.transactionDao().insertTransaction(
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = loansCategoryId(),
            amount = LOAN_AMOUNT,
            description = "original",
            personName = "Ali",
            date = ORIGINAL_DATE
          )
        )
      val stored = transactionById(txId)

      repo.updateTransaction(stored.copy(date = NEW_DATE, description = "edited"))

      val updated = transactionById(txId)
      assertEquals(NEW_DATE, updated.date)
      assertEquals("edited", updated.description)
    }

  @Test
  fun orphanLoanIdFallsBackToEditedDate() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val txId =
        database.transactionDao().insertTransaction(
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = loansCategoryId(),
            amount = LOAN_AMOUNT,
            description = "orphan",
            personName = "Ali",
            date = ORIGINAL_DATE,
            loanId = ORPHAN_LOAN_ID
          )
        )
      val stored = transactionById(txId)

      // No loan row exists for this stamp, so the edit falls back to
      // the editor's date instead of failing or dropping the write.
      repo.updateTransaction(stored.copy(date = NEW_DATE))

      assertEquals(NEW_DATE, transactionById(txId).date)
    }

  @Test
  fun staleLegacyDisbursementEditKeepsStampAndNewDate() =
    runTest {
      val repo = createRepository()
      seedLoansCategory()
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Ali",
            type = LoanType.DEBTOR,
            originalAmount = LOAN_AMOUNT,
            remainingAmount = LOAN_AMOUNT,
            description = "test",
            date = ORIGINAL_DATE,
            tracked = true,
            accountId = 1L
          ),
          recordInitial = false
        )
      // Legacy row: the loan predates the loanId stamp, so the editor
      // snapshot carries no loanId at all.
      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = loansCategoryId(),
          amount = LOAN_AMOUNT,
          description = "legacy disbursement",
          personName = "Ali",
          date = ORIGINAL_DATE
        )
      )
      val snapshot =
        database.transactionDao().getAllTransactionsBlocking().single()
      assertEquals(null, snapshot.loanId)
      assertEquals(ORIGINAL_DATE, snapshot.date)

      // The loan date edit stamps the legacy row and moves it.
      val persisted = requireNotNull(database.loanDao().getLoanById(loanId))
      assertEquals(LoanUpdateResult.Success, repo.updateLoan(persisted.copy(date = NEW_DATE)))
      val stamped = transactionById(snapshot.id)
      assertEquals(loanId, stamped.loanId)
      assertEquals(NEW_DATE, stamped.date)

      // The stale editor saves its pre-stamp snapshot. The persisted
      // link and the loan date must both survive the save.
      repo.updateTransaction(snapshot.copy(description = "edited", date = ORIGINAL_DATE))

      val updated = transactionById(snapshot.id)
      assertEquals(loanId, updated.loanId)
      assertEquals(NEW_DATE, updated.date)
      assertEquals("edited", updated.description)
    }
}
