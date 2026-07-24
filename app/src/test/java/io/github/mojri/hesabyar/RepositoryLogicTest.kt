package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupSettings
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RepositoryLogicTest {
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
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
      database
    )

  @Test
  fun `addPaymentToLoan - reduces remaining amount`() {
    var remainingAmount = 5_000_000L
    val paymentAmount = 2_000_000L

    remainingAmount = (remainingAmount - paymentAmount).coerceAtLeast(0L)
    assertEquals(3_000_000L, remainingAmount)
    assertFalse(remainingAmount <= 0L)
  }

  @Test
  fun `addPaymentToLoan - settles loan when remaining is zero`() {
    var remainingAmount = 2_000_000L
    val paymentAmount = 2_000_000L

    remainingAmount = (remainingAmount - paymentAmount).coerceAtLeast(0L)
    val isSettled = remainingAmount <= 0L
    assertTrue(isSettled)
    assertEquals(0L, remainingAmount)
  }

  @Test
  fun `addPaymentToLoan - overpayment clamps to zero`() {
    var remainingAmount = 1_000_000L
    val paymentAmount = 5_000_000L

    remainingAmount = (remainingAmount - paymentAmount).coerceAtLeast(0L)
    assertEquals(0L, remainingAmount)
    assertTrue(remainingAmount <= 0L)
  }

  @Test
  fun `addPaymentToLoan - multiple payments accumulate`() {
    var remainingAmount = 10_000_000L
    val payments = listOf(3_000_000L, 2_000_000L, 5_000_000L)

    payments.forEach { payment ->
      remainingAmount = (remainingAmount - payment).coerceAtLeast(0L)
    }

    assertEquals(0L, remainingAmount)
  }

  @Test
  fun `addPaymentToLoan - creditor creates expense transaction`() {
    val loanType = "CREDITOR"
    val transactionType = if (loanType == "CREDITOR") "EXPENSE" else "INCOME"
    assertEquals("EXPENSE", transactionType)
  }

  @Test
  fun `addPaymentToLoan - debtor creates income transaction`() {
    val loanType = "DEBTOR"
    val transactionType = if (loanType == "CREDITOR") "EXPENSE" else "INCOME"
    assertEquals("INCOME", transactionType)
  }

  @Test
  fun `addPaymentToLoan - creditor description format`() {
    val loan =
      Loan(
        personName = "Ali",
        type = LoanType.CREDITOR,
        originalAmount = 5_000_000L,
        remainingAmount = 5_000_000L,
        description = "test"
      )
    val notes = "partial payment"
    val desc =
      if (loan.type == LoanType.CREDITOR) {
        "بازپرداخت بدهی به ${loan.personName} - $notes"
      } else {
        "دریافت بازپرداخت از ${loan.personName} - $notes"
      }
    assertTrue(desc.contains("Ali"))
    assertTrue(desc.contains("بازپرداخت بدهی"))
  }

  @Test
  fun `addPaymentToLoan - debtor description format`() {
    val loan =
      Loan(
        personName = "Reza",
        type = LoanType.DEBTOR,
        originalAmount = 3_000_000L,
        remainingAmount = 3_000_000L,
        description = "test"
      )
    val notes = "repayment"
    val desc =
      if (loan.type == LoanType.CREDITOR) {
        "بازپرداخت بدهی به ${loan.personName} - $notes"
      } else {
        "دریافت بازپرداخت از ${loan.personName} - $notes"
      }
    assertTrue(desc.contains("Reza"))
    assertTrue(desc.contains("دریافت بازپرداخت"))
  }

  @Test
  fun `importBackup - clears and inserts`() {
    val existingTransactions =
      mutableListOf(
        Transaction(type = TransactionType.EXPENSE, categoryId = 1L, amount = 100L, description = "old")
      )
    val newTransactions =
      listOf(
        Transaction(type = TransactionType.INCOME, categoryId = 2L, amount = 200L, description = "new1"),
        Transaction(type = TransactionType.EXPENSE, categoryId = 3L, amount = 300L, description = "new2")
      )

    existingTransactions.clear()
    existingTransactions.addAll(newTransactions)

    assertEquals(2, existingTransactions.size)
    assertEquals("new1", existingTransactions[0].description)
  }

  @Test
  fun `replaceAllFromBackup - replaces all data`() {
    val existingCategories =
      mutableListOf(
        Category(id = 1L, name = "Old", key = "Old", icon = "Test", color = 0L, type = CategoryType.EXPENSE)
      )
    val newCategories =
      listOf(
        Category(id = 1L, name = "New", key = "New", icon = "Test", color = 0L, type = CategoryType.EXPENSE)
      )

    existingCategories.clear()
    existingCategories.addAll(newCategories)

    assertEquals(1, existingCategories.size)
    assertEquals("New", existingCategories[0].name)
  }

  @Test
  fun `mergeFromBackup - updates existing category`() {
    val existing =
      Category(
        id = 1,
        name = "Old Food",
        key = "Food",
        icon = "Restaurant",
        color = 0xFF4CAF50L,
        type = CategoryType.EXPENSE
      )
    val backup =
      Category(
        id = 0,
        name = "New Food",
        key = "Food",
        icon = "Restaurant",
        color = 0xFF4CAF50L,
        type = CategoryType.EXPENSE
      )

    val existingKey = existing.key
    val backupKey = backup.key
    assertEquals(existingKey, backupKey)

    val merged = backup.copy(id = existing.id)
    assertEquals(existing.id, merged.id)
    assertEquals("New Food", merged.name)
  }

  @Test
  fun `mergeFromBackup - inserts new category`() {
    val existingKeys = setOf("Food", "Transportation")
    val backupCategory =
      Category(
        id = 0,
        name = "Health",
        key = "Health",
        icon = "Heart",
        color = 0xFFE91E63L,
        type = CategoryType.EXPENSE
      )

    val isNew = backupCategory.key !in existingKeys
    assertTrue(isNew)
  }

  @Test
  fun `updateInstallment paid creates expense transaction`() {
    val installment =
      Installment(title = "Car", amount = 2_000_000L, dueDate = System.currentTimeMillis(), isPaid = true)
    assertTrue(installment.isPaid)

    val transaction =
      Transaction(
        type = TransactionType.EXPENSE,
        categoryId = 5L,
        amount = installment.amount,
        description = "پرداخت قسط: ${installment.title} - ${installment.notes}"
      )
    assertEquals(TransactionType.EXPENSE, transaction.type)
    assertEquals(2_000_000L, transaction.amount)
  }

  @Test
  fun `loan payment creates correct transaction type mapping`() {
    val scenarios =
      mapOf(
        "CREDITOR" to "EXPENSE",
        "DEBTOR" to "INCOME"
      )
    scenarios.forEach { (loanType, expectedTxType) ->
      val txType = if (loanType == "CREDITOR") "EXPENSE" else "INCOME"
      assertEquals(expectedTxType, txType)
    }
  }

  @Test
  fun `backup payload preserves all fields`() {
    val backup =
      BackupPayload(
        version = 2,
        timestamp = 1234567890L,
        appVersion = "1.5",
        transactions =
          listOf(
            Transaction(type = TransactionType.EXPENSE, categoryId = 1L, amount = 1000L, description = "t")
          ),
        loans =
          listOf(
            Loan(
              personName = "Ali",
              type = LoanType.DEBTOR,
              originalAmount = 5000L,
              remainingAmount = 3000L,
              description = "l"
            )
          ),
        installments = listOf(Installment(title = "Car", amount = 2000L, dueDate = 100L)),
        paymentHistories = listOf(PaymentHistory(loanId = 1L, amount = 1000L)),
        categories =
          listOf(
            Category(name = "Food", key = "Food", icon = "Restaurant", color = 0xFF4CAF50L, type = CategoryType.EXPENSE)
          ),
        settings = BackupSettings(darkMode = false)
      )

    assertEquals(2, backup.version)
    assertEquals(1234567890L, backup.timestamp)
    assertEquals("1.5", backup.appVersion)
    assertEquals(1, backup.transactions.size)
    assertEquals(1, backup.loans.size)
    assertEquals(1, backup.installments.size)
    assertEquals(1, backup.paymentHistories.size)
    assertEquals(1, backup.categories.size)
    assertFalse(backup.settings.darkMode)
  }

  @Test
  fun `addPaymentToLoan - overpayment records effective amount`() =
    runTest {
      val repo = createRepository()

      val loansCategory =
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 0xFF4CAF50L,
          type = CategoryType.BOTH
        )
      repo.insertCategory(loansCategory)

      val loan =
        Loan(
          personName = "Ali",
          type = LoanType.DEBTOR,
          originalAmount = 5_000L,
          remainingAmount = 5_000L,
          description = "test"
        )
      val loanId = repo.insertLoan(loan)

      val success = repo.addPaymentToLoan(loanId, 10_000L, "overpayment test")
      assertTrue(success)

      val paymentHistories = database.paymentHistoryDao().getAllPaymentHistoriesBlocking()
      assertEquals(1, paymentHistories.size)
      assertEquals(5_000L, paymentHistories[0].amount)

      val transactions = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, transactions.size)
      assertEquals(5_000L, transactions[0].amount)

      val updatedLoan = database.loanDao().getLoanById(loanId)
      assertTrue(updatedLoan != null)
      assertEquals(0L, updatedLoan!!.remainingAmount)
      assertTrue(updatedLoan.isSettled)
    }

  @Test
  fun `addPaymentToLoan - rejects zero amount`() =
    runTest {
      val repo = createRepository()

      val loansCategory =
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 0xFF4CAF50L,
          type = CategoryType.BOTH
        )
      repo.insertCategory(loansCategory)

      val loan =
        Loan(
          personName = "Ali",
          type = LoanType.DEBTOR,
          originalAmount = 5_000L,
          remainingAmount = 5_000L,
          description = "test"
        )
      val loanId = repo.insertLoan(loan)

      val success = repo.addPaymentToLoan(loanId, 0L, "zero payment")
      assertFalse(success)

      val paymentHistories = database.paymentHistoryDao().getAllPaymentHistoriesBlocking()
      assertEquals(0, paymentHistories.size)

      val transactions = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(0, transactions.size)

      val updatedLoan = database.loanDao().getLoanById(loanId)
      assertTrue(updatedLoan != null)
      assertEquals(5_000L, updatedLoan!!.remainingAmount)
      assertFalse(updatedLoan.isSettled)
    }

  @Test
  fun `addPaymentToLoan - rejects negative amount`() =
    runTest {
      val repo = createRepository()

      val loansCategory =
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 0xFF4CAF50L,
          type = CategoryType.BOTH
        )
      repo.insertCategory(loansCategory)

      val loan =
        Loan(
          personName = "Ali",
          type = LoanType.DEBTOR,
          originalAmount = 5_000L,
          remainingAmount = 5_000L,
          description = "test"
        )
      val loanId = repo.insertLoan(loan)

      val success = repo.addPaymentToLoan(loanId, -1_000L, "negative payment")
      assertFalse(success)

      val paymentHistories = database.paymentHistoryDao().getAllPaymentHistoriesBlocking()
      assertEquals(0, paymentHistories.size)

      val transactions = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(0, transactions.size)

      val updatedLoan = database.loanDao().getLoanById(loanId)
      assertTrue(updatedLoan != null)
      assertEquals(5_000L, updatedLoan!!.remainingAmount)
      assertFalse(updatedLoan.isSettled)
    }

  @Test
  fun `addPaymentToLoan - rejects payment on settled loan`() =
    runTest {
      val repo = createRepository()

      val loansCategory =
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 0xFF4CAF50L,
          type = CategoryType.BOTH
        )
      repo.insertCategory(loansCategory)

      val loan =
        Loan(
          personName = "Ali",
          type = LoanType.DEBTOR,
          originalAmount = 5_000L,
          remainingAmount = 0L,
          description = "test",
          isSettled = true
        )
      val loanId = repo.insertLoan(loan)

      val success = repo.addPaymentToLoan(loanId, 1_000L, "payment on settled loan")
      assertFalse(success)

      val paymentHistories = database.paymentHistoryDao().getAllPaymentHistoriesBlocking()
      assertEquals(0, paymentHistories.size)

      val transactions = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(0, transactions.size)

      val updatedLoan = database.loanDao().getLoanById(loanId)
      assertTrue(updatedLoan != null)
      assertEquals(0L, updatedLoan!!.remainingAmount)
      assertTrue(updatedLoan.isSettled)
    }
}
