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
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for Issue #242: Loan-payment transaction deletion matches
 * mutable non-unique fields — needs a stable link.
 *
 * Verifies that:
 * 1. Deleting one loan does NOT delete payment transactions of another loan
 *    sharing the same person, amount, and date.
 * 2. Deleting a loan does NOT delete a manually entered transaction sharing
 *    the same person, Loans category, amount, and date.
 * 3. Initial disbursement transactions are tracked by loanId and deleted
 *    without relying on field matching.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LoanDeletePaymentTransactionTest {
  private lateinit var database: AppDatabase
  private var loansCategoryId: Long = 0L

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database.accountDao().insertAllBlocking(
      listOf(
        AccountEntity.DEFAULT_ACCOUNT,
        AccountEntity(
          id = 2L,
          name = "Second Account",
          type = io.github.mojri.hesabyar.data.AccountType.BANK,
          initialBalance = 0L,
          displayOrder = 1
        )
      )
    )
    database.categoryDao().insertAllBlocking(
      listOf(
        Category(
          id = 1L,
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 0xFF4CAF50L,
          type = CategoryType.BOTH,
          isDefault = true
        )
      )
    )
    loansCategoryId = 1L
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

  @Test
  fun deleteLoanOnlyDeletesItsOwnPaymentTransactionsNotCollidingLoan() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L
      val sameAmount = 1_000_000L

      // Two distinct loans to the same person, both tracked
      val loan1Id =
        repo.insertLoan(
          Loan(
            personName = "Ali",
            type = LoanType.CREDITOR,
            originalAmount = 5_000_000L,
            remainingAmount = 5_000_000L,
            description = "Loan 1",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          )
        )
      val loan2Id =
        repo.insertLoan(
          Loan(
            personName = "Ali",
            type = LoanType.CREDITOR,
            originalAmount = 10_000_000L,
            remainingAmount = 10_000_000L,
            description = "Loan 2",
            tracked = true,
            accountId = 1L,
            date = fixedDate + 1000L
          )
        )

      // Both loans receive a payment of the SAME amount on the SAME date
      repo.addPaymentToLoan(loan1Id, sameAmount, "pay loan 1", fixedDate)
      repo.addPaymentToLoan(loan2Id, sameAmount, "pay loan 2", fixedDate)

      // Verify each payment has a linked transaction
      val payments1 = repo.getPaymentHistoryForLoan(loan1Id).first()
      val payments2 = repo.getPaymentHistoryForLoan(loan2Id).first()
      assertEquals(1, payments1.size)
      assertEquals(1, payments2.size)
      assertNotNull(payments1[0].linkedTransactionId)
      assertNotNull(payments2[0].linkedTransactionId)
      val tx1Id = payments1[0].linkedTransactionId!!
      val tx2Id = payments2[0].linkedTransactionId!!
      assertTrue("Transactions must have distinct IDs", tx1Id != tx2Id)

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(2, allTxBefore.size)

      // Delete loan 1
      val loan1 = database.loanDao().getLoanById(loan1Id)!!
      repo.deleteLoan(loan1)

      // CRITICAL: loan 2's payment transaction MUST survive
      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(
        "Only loan 1's transaction should be deleted; loan 2's transaction must survive",
        1,
        allTxAfter.size
      )
      assertEquals(tx2Id, allTxAfter[0].id)
    }

  @Test
  fun deleteLoanDoesNotDeleteManualTransactionWithSameFields() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L
      val sameAmount = 500_000L

      // A tracked loan
      val loanId =
        repo.insertLoan(
          Loan(
            personName = "Sara",
            type = LoanType.CREDITOR,
            originalAmount = 3_000_000L,
            remainingAmount = 3_000_000L,
            description = "Tracked loan",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          )
        )

      // A payment on the loan
      repo.addPaymentToLoan(loanId, sameAmount, "payment", fixedDate)

      // A manually entered transaction with the EXACT same person, category, amount, date
      val manualTxId =
        database.transactionDao().insertTransaction(
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = loansCategoryId,
            amount = sameAmount,
            description = "Manual loan payment entered by user",
            personName = "Sara",
            date = fixedDate,
            accountId = 1L
          )
        )

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(2, allTxBefore.size)

      // Delete the loan
      val loan = database.loanDao().getLoanById(loanId)!!
      repo.deleteLoan(loan)

      // The manually entered transaction MUST survive
      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(
        "Manual transaction with colliding fields must survive loan deletion",
        1,
        allTxAfter.size
      )
      assertEquals(manualTxId, allTxAfter[0].id)
    }

  @Test
  fun deleteLoanDeletesInitialDisbursementTransactionByLoanId() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L

      // Tracked loan inserted with initial disbursement
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Omid",
            type = LoanType.CREDITOR,
            originalAmount = 2_000_000L,
            remainingAmount = 2_000_000L,
            description = "Debt loan",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          ),
          recordInitial = true
        )

      // Initial transaction should exist and carry loanId
      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, allTxBefore.size)
      assertEquals(loanId, allTxBefore[0].loanId)

      // Delete the loan
      val loan = database.loanDao().getLoanById(loanId)!!
      repo.deleteLoan(loan)

      // Initial transaction should be deleted
      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("Initial disbursement transaction should be deleted", 0, allTxAfter.size)
    }
}
