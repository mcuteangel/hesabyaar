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
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
  private var databaseInstance: AppDatabase? = null
  private val database: AppDatabase
    get() = requireNotNull(databaseInstance) { "Database must be initialized" }
  private var loansCategoryId: Long = 0L

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    db.accountDao().insertAllBlocking(
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
    db.categoryDao().insertAllBlocking(
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
    databaseInstance = db
    loansCategoryId = 1L
  }

  @After
  fun tearDown() {
    databaseInstance?.close()
    databaseInstance = null
  }

  private suspend fun insertTransaction(
    description: String,
    personName: String,
    amount: Long,
    date: Long,
    type: TransactionType = TransactionType.INCOME
  ): Long =
    database.transactionDao().insertTransaction(
      Transaction(
        type = type,
        categoryId = loansCategoryId,
        amount = amount,
        description = description,
        personName = personName,
        date = date,
        accountId = 1L
      )
    )

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
      val tx1Id = requireNotNull(payments1[0].linkedTransactionId)
      val tx2Id = requireNotNull(payments2[0].linkedTransactionId)
      assertTrue("Transactions must have distinct IDs", tx1Id != tx2Id)

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(2, allTxBefore.size)

      // Delete loan 1
      val loan1 = requireNotNull(database.loanDao().getLoanById(loan1Id))
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
      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
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

      // An unrelated transaction with the same field values (as if entered
      // manually or generated for a colliding loan) must survive: the delete
      // path must use the loanId stamp, not the fields.
      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = loansCategoryId,
          amount = 2_000_000L,
          description = "Colliding manual entry",
          personName = "Omid",
          date = fixedDate,
          accountId = 1L
        )
      )
      assertEquals(2, database.transactionDao().getAllTransactionsBlocking().size)

      // Delete the loan
      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      repo.deleteLoan(loan)

      // Initial transaction should be deleted; the colliding row survives
      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("Only the stamped disbursement should be deleted", 1, allTxAfter.size)
      assertEquals("Colliding manual entry", allTxAfter[0].description)
      assertNull(allTxAfter[0].loanId)
    }

  @Test
  fun deleteLegacyTrackedLoanWithoutStampedDisbursementUsesFieldMatch() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L

      // Tracked loan without an initial disbursement recorded, simulating a
      // pre-migration loan whose disbursement was field-matched. The rows
      // inserted by hand carry no loanId and should be cleaned by the
      // legacy fallback.
      val loanId =
        repo.insertLoan(
          Loan(
            personName = "LegacyPay",
            type = LoanType.DEBTOR,
            originalAmount = 7_000_000L,
            remainingAmount = 7_000_000L,
            description = "Legacy tracked loan",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          )
        )
      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = loansCategoryId,
          amount = 7_000_000L,
          description = "Legacy disbursement",
          personName = "LegacyPay",
          date = fixedDate,
          accountId = 1L
        )
      )
      // Transaction matching modified caller snapshot fields
      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = loansCategoryId,
          amount = 9_000_000L,
          description = "Legacy disbursement with edited fields",
          personName = "LegacyEdited",
          date = fixedDate + 5000L,
          accountId = 1L
        )
      )
      assertEquals(2, database.transactionDao().getAllTransactionsBlocking().size)

      val storedLoan = requireNotNull(database.loanDao().getLoanById(loanId))
      // Caller snapshot has modified name, amount, and date to exercise the
      // full fallback cleaning loop over all names and modified values.
      val modifiedSnapshot =
        storedLoan.copy(
          personName = "LegacyEdited",
          originalAmount = 9_000_000L,
          date = fixedDate + 5000L
        )
      repo.deleteLoan(modifiedSnapshot)

      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(
        "Both existing and caller-modified disbursements removed via fallback",
        0,
        allTxAfter.size
      )
    }

  @Test
  fun deleteTrackedLoanWithNoPaymentsStillDeletesInitialDisbursement() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L

      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "ZeroPay",
            type = LoanType.DEBTOR,
            originalAmount = 5_000_000L,
            remainingAmount = 5_000_000L,
            description = "No payments yet",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          ),
          recordInitial = true
        )

      // The initial disbursement exists, no repayment payments recorded.
      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, allTxBefore.size)
      assertEquals(loanId, allTxBefore[0].loanId)
      assertEquals(0, database.paymentHistoryDao().getPaymentHistoriesForLoanSync(loanId).size)

      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      repo.deleteLoan(loan)

      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("Initial disbursement deleted via loanId even with zero payments", 0, allTxAfter.size)
    }

  @Test
  fun deleteLoanHandlesMixedLinkedAndUnlinkedPayments() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L

      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Mixed",
            type = LoanType.DEBTOR,
            originalAmount = 1_000_000L,
            remainingAmount = 1_000_000L,
            description = "Mixed payments",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          ),
          recordInitial = true
        )

      // Payment 1: linked (normal flow through addPaymentToLoan)
      repo.addPaymentToLoan(loanId, 200_000L, "linked payment", fixedDate)

      // Payment 2: inserted directly without a linkedTransactionId (simulates a
      // legacy/pre-migration payment row).
      database.paymentHistoryDao().insertAllBlocking(
        listOf(
          PaymentHistory(
            loanId = loanId,
            amount = 300_000L,
            date = fixedDate,
            notes = "legacy unlinked payment"
          )
        )
      )

      // Legacy transaction matching Payment 2 (no loanId or stable link).
      val legacyTxId =
        insertTransaction("Legacy repayment", "Mixed", 300_000L, fixedDate)

      // Unrelated transaction with the same category, amount, and date for a
      // different person. Must survive loan deletion.
      val survivingTxId =
        insertTransaction("Unrelated transaction", "OtherPerson", 300_000L, fixedDate)

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      // Initial (1) + linked payment (1) + legacy payment (1) + unrelated (1) = 4
      assertEquals(4, allTxBefore.size)
      assertTrue(allTxBefore.any { it.id == legacyTxId })

      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      repo.deleteLoan(loan)

      // Transactions for this loan must be gone:
      // 1. Initial disbursement via loanId.
      // 2. Linked payment transaction via linkedTransactionId.
      // 3. Legacy unlinked payment transaction via field-matching fallback.
      // The unrelated transaction for "OtherPerson" must survive.
      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("Only the unrelated transaction should survive", 1, allTxAfter.size)
      assertEquals(survivingTxId, allTxAfter[0].id)
      assertEquals("OtherPerson", allTxAfter[0].personName)

      val remainingPayments =
        database.paymentHistoryDao().getPaymentHistoriesForLoanSync(loanId)
      assertEquals(0, remainingPayments.size)
    }

  @Test
  fun deleteUntrackedLoanDeletesOnlyPaymentHistory() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L

      val loanId =
        repo.insertLoan(
          Loan(
            personName = "Untracked",
            type = LoanType.CREDITOR,
            originalAmount = 3_000_000L,
            remainingAmount = 3_000_000L,
            description = "Untracked loan",
            tracked = false,
            accountId = null,
            date = fixedDate
          )
        )
      repo.addPaymentToLoan(loanId, 500_000L, "untracked repayment", fixedDate)
      assertEquals(0, database.transactionDao().getAllTransactionsBlocking().size)
      assertEquals(1, database.paymentHistoryDao().getPaymentHistoriesForLoanSync(loanId).size)

      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      repo.deleteLoan(loan)

      assertNull(database.loanDao().getLoanById(loanId))
      assertEquals(0, database.paymentHistoryDao().getPaymentHistoriesForLoanSync(loanId).size)
      assertEquals(0, database.transactionDao().getAllTransactionsBlocking().size)
    }

  @Test
  fun deleteLoanWithMoreThanChunkSizeLinkedPaymentsDeletesAllTransactions() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L
      val paymentCount = 950

      val loanId =
        repo.insertLoan(
          Loan(
            personName = "ChunkTest",
            type = LoanType.CREDITOR,
            originalAmount = 10_000_000L,
            remainingAmount = 10_000_000L,
            description = "Large payment count",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          )
        )

      val transactions =
        (1..paymentCount).map { i ->
          Transaction(
            id = i.toLong(),
            type = TransactionType.EXPENSE,
            categoryId = loansCategoryId,
            amount = 1_000L,
            description = "Payment $i",
            personName = "ChunkTest",
            date = fixedDate + i,
            accountId = 1L
          )
        }
      database.transactionDao().insertAllBlocking(transactions)

      val payments =
        (1..paymentCount).map { i ->
          PaymentHistory(
            id = i.toLong(),
            loanId = loanId,
            amount = 1_000L,
            date = fixedDate + i,
            notes = "Pay $i",
            linkedTransactionId = i.toLong()
          )
        }
      database.paymentHistoryDao().insertAllBlocking(payments)

      assertEquals(paymentCount, database.transactionDao().getAllTransactionsBlocking().size)
      assertEquals(
        paymentCount,
        database.paymentHistoryDao().getPaymentHistoriesForLoanSync(loanId).size
      )

      val loan = requireNotNull(database.loanDao().getLoanById(loanId))
      repo.deleteLoan(loan)

      assertEquals(
        "All transactions across all chunks must be deleted",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
      assertEquals(0, database.paymentHistoryDao().getPaymentHistoriesForLoanSync(loanId).size)
      assertNull(database.loanDao().getLoanById(loanId))
    }

  @Test
  fun deleteLoanTrackedThenChangedToUntrackedDeletesInitialDisbursement() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L

      // Insert loan as tracked with initial disbursement (carries loanId stamp)
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "TransitionTracked",
            type = LoanType.CREDITOR,
            originalAmount = 4_000_000L,
            remainingAmount = 4_000_000L,
            description = "Tracked loan transitioning to untracked",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          ),
          recordInitial = true
        )

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, allTxBefore.size)
      assertEquals(loanId, allTxBefore[0].loanId)

      // Later updated to untracked (existing.tracked becomes false in DB)
      val storedLoan = requireNotNull(database.loanDao().getLoanById(loanId))
      repo.updateLoan(storedLoan.copy(tracked = false, accountId = null))

      val untrackedLoan = requireNotNull(database.loanDao().getLoanById(loanId))
      org.junit.Assert.assertFalse(untrackedLoan.tracked)

      // Deleting the loan must still delete the initial disbursement by loanId
      repo.deleteLoan(untrackedLoan)

      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(
        "Stamped initial disbursement must be deleted even if loan transitioned to untracked",
        0,
        allTxAfter.size
      )
    }

  @Test
  fun deleteLegacyLoanTrackedThenChangedToUntrackedUsesFieldMatchFallback() =
    runTest {
      val repo = createRepository()
      val fixedDate = 1_700_000_000_000L

      // Legacy tracked loan without stamped loanId
      val loanId =
        repo.insertLoan(
          Loan(
            personName = "LegacyTransition",
            type = LoanType.DEBTOR,
            originalAmount = 6_000_000L,
            remainingAmount = 6_000_000L,
            description = "Legacy tracked loan transitioning to untracked",
            tracked = true,
            accountId = 1L,
            date = fixedDate
          )
        )
      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = loansCategoryId,
          amount = 6_000_000L,
          description = "Legacy unstamped disbursement",
          personName = "LegacyTransition",
          date = fixedDate,
          accountId = 1L
        )
      )
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      // Updated to untracked in DB
      val storedLoan = requireNotNull(database.loanDao().getLoanById(loanId))
      repo.updateLoan(storedLoan.copy(tracked = false, accountId = null))

      // Delete passing the caller snapshot where tracked was true
      repo.deleteLoan(storedLoan)

      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(
        "Legacy disbursement removed via field-matching fallback when snapshot was tracked",
        0,
        allTxAfter.size
      )
    }

  @Test
  fun deleteTransactionsByIdsWithEmptyListDoesNotThrow() =
    runTest {
      val fixedDate = 1_700_000_000_000L
      val existingTxId =
        insertTransaction("Surviving transaction", "PersonA", 500_000L, fixedDate)
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      // Guards against `WHERE id IN ()` SQLite syntax errors from
      // empty delete lists and verifies existing rows survive
      database.transactionLinkDao().deleteTransactionsByIds(emptyList())

      val txAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("Existing transaction must survive empty-list delete", 1, txAfter.size)
      assertEquals(existingTxId, txAfter[0].id)
    }
}
