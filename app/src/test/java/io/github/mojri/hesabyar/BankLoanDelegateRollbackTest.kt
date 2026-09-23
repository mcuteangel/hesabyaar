package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.InstallmentDao
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rollback coverage for [io.github.mojri.hesabyar.data.BankLoanDelegate]'s
 * transactional operations. A failure in any statement inside
 * `database.withTransaction` must leave both the bank_loans and installments
 * tables untouched — a half-applied cascade would corrupt the ledger.
 *
 * Separate from RepositoryLogicTest to stay under the detekt class-size
 * threshold; the failing-DAO decorator mirrors PersonRepositoryTest.RacePersonDao.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BankLoanDelegateRollbackTest {
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database.accountDao().insertAllBlocking(listOf(AccountEntity.DEFAULT_ACCOUNT))
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun createRepository(installmentDao: InstallmentDao = database.installmentDao()): HesabyarRepository =
    HesabyarRepository(
      database.transactionDao(),
      database.loanDao(),
      installmentDao,
      database.paymentHistoryDao(),
      database.categoryDao(),
      database.bankLoanDao(),
      database.accountDao(),
      database.personDao(),
      database
    )

  private fun testBankLoan() =
    BankLoan(
      bankName = "بانک ملت",
      loanName = "وام خودرو",
      receivedAmount = 100_000_000L,
      monthlyInstallmentAmount = 10_000_000L,
      numberOfInstallments = 2,
      totalRepayableAmount = 120_000_000L,
      totalInterest = 20_000_000L,
      startDate = 1_700_000_000_000L,
      description = "test"
    )

  private fun installment(bankLoanId: Long = 0L) =
    Installment(
      title = "قسط",
      amount = 10_000_000L,
      dueDate = 1_700_000_000_000L,
      bankLoanId = bankLoanId
    )

  /** Decorator that can force specific installment statements to throw. */
  private class FailingInstallmentDao(
    private val delegate: InstallmentDao
  ) : InstallmentDao by delegate {
    var failOnInsert = false
    var failOnDeleteByBankLoanId = false

    override suspend fun insertInstallment(installment: Installment): Long {
      if (failOnInsert) throw IllegalStateException("forced insert failure")
      return delegate.insertInstallment(installment)
    }

    override suspend fun deleteInstallmentsByBankLoanId(bankLoanId: Long) {
      if (failOnDeleteByBankLoanId) throw IllegalStateException("forced delete failure")
      delegate.deleteInstallmentsByBankLoanId(bankLoanId)
    }
  }

  @Test
  fun deleteBankLoanRemovesLinkedExpensesOfPaidInstallments() =
    runTest {
      // The cascade must not strand the expenses behind paid installments:
      // deleting the bank loan removes the rows, so their linked money would
      // otherwise keep showing in reports.
      val repo = createRepository()
      database.categoryDao().insertCategory(
        Category(
          name = "Installments",
          key = "Installments",
          icon = "CreditCard",
          color = 1,
          type = CategoryType.EXPENSE
        )
      )
      val loanId =
        repo.addBankLoanWithInstallments(
          testBankLoan(),
          listOf(installment(), installment())
        )
      val stored = database.installmentDao().getInstallmentsByBankLoanIdSync(loanId)
      // One paid installment carries a linked expense, the other stays unpaid.
      // Must be tracked=true with accountId to post a linked expense per Phase 2.
      repo.updateInstallment(
        stored.first().copy(isPaid = true, tracked = true, accountId = DEFAULT_ACCOUNT_ID)
      )
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      repo.deleteBankLoan(database.bankLoanDao().getAllBankLoansBlocking().single())

      assertEquals("bank loan gone", 0, database.bankLoanDao().getAllBankLoansBlocking().size)
      assertEquals("installments gone", 0, database.installmentDao().getAllInstallmentsSync().size)
      assertEquals(
        "the paid installment's linked expense must die with the cascade",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun addbankloanwithinstallmentsRollsBackWhenInstallmentInsertFails() =
    runTest {
      val failingDao = FailingInstallmentDao(database.installmentDao()).apply { failOnInsert = true }
      val repo = createRepository(failingDao)

      val threw =
        try {
          repo.addBankLoanWithInstallments(testBankLoan(), listOf(installment(), installment()))
          false
        } catch (expected: IllegalStateException) {
          true
        }

      assertTrue("forced insert failure must propagate", threw)
      assertEquals(
        "bank loan insert must roll back with the failed installment",
        0,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
      assertEquals(
        "no installment may survive the failed insert",
        0,
        database.installmentDao().getAllInstallmentsBlocking().size
      )
    }

  @Test
  fun deletebankloanRollsBackWhenInstallmentDeleteFails() =
    runTest {
      // Seed with the real DAO so the cascade has something to undo.
      val repo = createRepository()
      val loanId =
        repo.addBankLoanWithInstallments(
          testBankLoan(),
          listOf(installment(), installment())
        )
      assertEquals(2, database.installmentDao().getAllInstallmentsBlocking().size)

      val failingRepo =
        createRepository(
          FailingInstallmentDao(database.installmentDao()).apply { failOnDeleteByBankLoanId = true }
        )
      val storedLoan = database.bankLoanDao().getAllBankLoansBlocking().single()

      val threw =
        try {
          failingRepo.deleteBankLoan(storedLoan)
          false
        } catch (expected: IllegalStateException) {
          true
        }

      assertTrue("forced delete failure must propagate", threw)
      assertEquals(
        "bank loan must survive the failed cascade",
        1,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
      assertEquals(
        "installments must survive the failed cascade",
        2,
        database.installmentDao().getAllInstallmentsBlocking().size
      )
      assertTrue(loanId > 0)
    }

  @Test
  fun deleteBankLoanWithInitialDisbursementDoesNotDeleteMatchingPersonalLoanTransaction() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 50_000_000L

      repo.insertLoanWithInitial(
        Loan(
          personName = "Ali",
          type = LoanType.CREDITOR,
          originalAmount = sharedAmount,
          remainingAmount = sharedAmount,
          description = "personal loan",
          date = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        ),
        recordInitial = true
      )

      val bankLoan =
        testBankLoan().copy(
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      repo.addBankLoanWithInstallmentsAndInitial(
        bankLoan,
        listOf(installment()),
        recordInitial = true
      )

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(2, allTxBefore.size)

      val storedBankLoan = database.bankLoanDao().getAllBankLoansBlocking().single()
      repo.deleteBankLoan(storedBankLoan)

      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, allTxAfter.size)
      assertEquals("Ali", allTxAfter.single().personName)
      assertEquals(sharedAmount, allTxAfter.single().amount)
    }

  @Test
  fun addBankLoanWithInstallmentsRejectsTrackedWithoutValidAccountAndPersistsNothing() =
    runTest {
      val repo = createRepository()
      val invalidTrackedLoan =
        testBankLoan().copy(
          tracked = true,
          accountId = null
        )
      try {
        repo.addBankLoanWithInstallments(invalidTrackedLoan, listOf(installment()))
        org.junit.Assert.fail("addBankLoanWithInstallments with tracked=true and accountId=null must throw")
      } catch (expected: IllegalArgumentException) {
        // Expected
      }

      assertEquals(0, database.bankLoanDao().getAllBankLoansBlocking().size)
      assertEquals(0, database.installmentDao().getAllInstallmentsBlocking().size)
    }

  @Test
  fun addBankLoanWithInstallmentsForcesInstallmentsUntrackedAndAccountIdNull() =
    runTest {
      val repo = createRepository()
      val inputInstallment =
        installment().copy(
          tracked = true,
          accountId = 5L
        )
      val loanId = repo.addBankLoanWithInstallments(testBankLoan(), listOf(inputInstallment))
      val storedInstallments = database.installmentDao().getInstallmentsByBankLoanIdSync(loanId)
      assertEquals(1, storedInstallments.size)
      val stored = storedInstallments.single()
      org.junit.Assert.assertFalse("bank loan installment must be forced to untracked", stored.tracked)
      org.junit.Assert.assertNull("bank loan installment must have null accountId", stored.accountId)
    }

  @Test
  fun deleteBankLoanUsesPersistedTrackedStateIgnoringStaleCallerSnapshot() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 50_000_000L

      val trackedLoan =
        testBankLoan().copy(
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      repo.addBankLoanWithInstallmentsAndInitial(
        trackedLoan,
        listOf(installment()),
        recordInitial = true
      )
      assertEquals(
        "one disbursement transaction must exist initially",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )

      val storedLoan = database.bankLoanDao().getAllBankLoansBlocking().single()
      val staleUntrackedSnapshot = storedLoan.copy(tracked = false, accountId = null)
      repo.deleteBankLoan(staleUntrackedSnapshot)

      assertEquals(
        "disbursement transaction must be cleaned based on persisted tracked state",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
      assertEquals(
        "bank loan must be deleted",
        0,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
    }

  @Test
  fun deleteBankLoanDoesNotDeleteUnrelatedTransactionWhenPersistedBankLoanIsUntracked() =
    runTest {
      val repo = createRepository()
      val loansCatId =
        repo.insertCategory(
          Category(
            name = "Loans",
            key = "Loans",
            icon = "HistoryEdu",
            color = 1,
            type = CategoryType.BOTH
          )
        )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 40_000_000L

      val untrackedLoan =
        testBankLoan().copy(
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = false,
          accountId = null
        )
      repo.addBankLoanWithInstallmentsAndInitial(
        untrackedLoan,
        listOf(installment()),
        recordInitial = false
      )

      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.INCOME,
          categoryId = loansCatId,
          amount = sharedAmount,
          date = sharedDate,
          description = "Unrelated transaction",
          personName = null,
          personId = null,
          accountId = DEFAULT_ACCOUNT_ID
        )
      )
      assertEquals(
        "unrelated transaction must be present",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )

      val storedUntracked = database.bankLoanDao().getAllBankLoansBlocking().single()
      val staleTrackedSnapshot = storedUntracked.copy(tracked = true, accountId = DEFAULT_ACCOUNT_ID)
      repo.deleteBankLoan(staleTrackedSnapshot)

      val remainingTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(
        "unrelated transaction must not be deleted",
        1,
        remainingTx.size
      )
      assertEquals("Unrelated transaction", remainingTx.single().description)
    }

  @Test
  fun deleteBankLoanFallbackDeletesDisbursementWhenDescriptionWasModified() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 75_000_000L

      val bankLoan =
        testBankLoan().copy(
          loanName = "وام اولیه",
          bankName = "بانک صادرات",
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      repo.addBankLoanWithInstallmentsAndInitial(
        bankLoan,
        listOf(installment()),
        recordInitial = true
      )

      val initialTx = database.transactionDao().getAllTransactionsBlocking().single()
      database.transactionDao().updateTransaction(
        initialTx.copy(description = "شرح سفارشی شده توسط کاربر")
      )

      val stored = database.bankLoanDao().getAllBankLoansBlocking().single()
      repo.deleteBankLoan(stored)

      assertEquals(
        "fallback must delete modified-description disbursement",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
      assertEquals(
        "bank loan must be deleted",
        0,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
    }

  @Test
  fun deleteBankLoanWithExactDescriptionDoesNotDeleteAnotherBankLoanWithSameAmountAndDate() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 60_000_000L

      val bankLoan1 =
        testBankLoan().copy(
          loanName = "وام خودرو",
          bankName = "بانک ملت",
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      val bankLoan2 =
        testBankLoan().copy(
          loanName = "وام مسکن",
          bankName = "بانک سپه",
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )

      repo.addBankLoanWithInstallmentsAndInitial(bankLoan1, listOf(installment()), recordInitial = true)
      repo.addBankLoanWithInstallmentsAndInitial(bankLoan2, listOf(installment()), recordInitial = true)

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("two disbursements must exist", 2, allTxBefore.size)

      val storedLoan1 =
        database.bankLoanDao().getAllBankLoansBlocking().first { it.loanName == "وام خودرو" }
      repo.deleteBankLoan(storedLoan1)

      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("only one disbursement should survive", 1, allTxAfter.size)
      val survivingTx = allTxAfter.single()
      assertEquals(
        "surviving transaction must belong to bank loan 2",
        "دریافت وام وام مسکن از بانک سپه",
        survivingTx.description
      )
      assertEquals("surviving transaction amount matches", sharedAmount, survivingTx.amount)
    }
}
