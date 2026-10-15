package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #430: the stable loan links from #242 (Transaction.loanId and
 * PaymentHistory.linkedTransactionId) must survive a backup round-trip, so
 * deleteLoan after a restore removes only the loan's own ledger rows and
 * never a manual transaction that happens to share person, amount and date.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupLoanLinksRestoreTest {
  @get:Rule
  val rustIsolationRule = RustIsolationRule()

  private val databases = mutableListOf<AppDatabase>()

  @Before
  fun forceKotlinParser() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  @After
  fun tearDown() {
    databases.forEach { it.close() }
  }

  private fun newDatabase(): AppDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    db.accountDao().insertAllBlocking(listOf(AccountEntity.DEFAULT_ACCOUNT))
    db.categoryDao().insertAllBlocking(
      listOf(
        Category(
          id = LOANS_CATEGORY_ID,
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 0xFF4CAF50L,
          type = CategoryType.BOTH,
          isDefault = true
        )
      )
    )
    databases += db
    return db
  }

  private fun repositoryFor(db: AppDatabase): HesabyarRepository =
    HesabyarRepository(
      db.transactionDao(),
      db.loanDao(),
      db.installmentDao(),
      db.paymentHistoryDao(),
      db.categoryDao(),
      db.bankLoanDao(),
      db.accountDao(),
      db.personDao(),
      db
    )

  private fun manualTransaction(description: String) =
    Transaction(
      type = TransactionType.INCOME,
      categoryId = LOANS_CATEGORY_ID,
      amount = PAYMENT_AMOUNT,
      description = description,
      personName = PERSON,
      date = PAYMENT_DATE,
      accountId = AccountEntity.DEFAULT_ACCOUNT.id
    )

  /** Source DB: a tracked loan with its disbursement, one tracked repayment, and a colliding manual row. */
  private suspend fun exportSourceBackup(): Pair<String, Long> {
    val db = newDatabase()
    val repo = repositoryFor(db)
    val loanId =
      repo.insertLoanWithInitial(
        Loan(
          personName = PERSON,
          type = LoanType.DEBTOR,
          originalAmount = 5_000_000L,
          remainingAmount = 5_000_000L,
          description = "loan",
          date = PAYMENT_DATE - 1_000L,
          tracked = true,
          accountId = AccountEntity.DEFAULT_ACCOUNT.id
        ),
        recordInitial = true
      )
    repo.addPaymentToLoan(loanId, PAYMENT_AMOUNT, "repayment", PAYMENT_DATE)
    db.transactionDao().insertTransaction(manualTransaction(MANUAL_DESCRIPTION))
    val json = ManageBackupUseCase(repo).exportBackupJson().toString()
    return json to loanId
  }

  private suspend fun parse(json: String): BackupPayload =
    requireNotNull(ManageBackupUseCase(repositoryFor(newDatabase())).parseBackupJson(json))

  @Test
  fun exportWritesBothLinksAndParserReadsThemBack() =
    runTest {
      val (json, loanId) = exportSourceBackup()
      val payload = parse(json)

      val disbursement = payload.transactions.single { it.loanId != null }
      assertEquals(loanId, disbursement.loanId)
      val payment = payload.paymentHistories.single()
      val linkedId = assertNotNullAndGet(payment.linkedTransactionId)
      assertTrue(
        "linkedTransactionId must point at a transaction in the same backup",
        payload.transactions.any { it.id == linkedId }
      )
    }

  @Test
  fun replaceRestoreKeepsLinksSoDeleteLoanSparesManualTransaction() =
    runTest {
      val (json, _) = exportSourceBackup()
      val payload = parse(json)
      val target = newDatabase()
      val repo = repositoryFor(target)

      repo.replaceAllFromBackup(payload)
      val restoredLoan = target.loanDao().getAllLoansBlocking().single()
      repo.deleteLoan(restoredLoan)

      val remaining = target.transactionDao().getAllTransactionsBlocking()
      assertEquals(listOf(MANUAL_DESCRIPTION), remaining.map { it.description })
    }

  @Test
  fun mergeRestoreRemapsLinksToNewIdsSoDeleteLoanSparesOtherRows() =
    runTest {
      val (json, _) = exportSourceBackup()
      val payload = parse(json)
      val target = newDatabase()
      val repo = repositoryFor(target)
      // Pre-existing local rows shift every merged id away from the backup's.
      target.transactionDao().insertTransaction(manualTransaction("local 1"))
      target.transactionDao().insertTransaction(manualTransaction("local 2"))
      target.loanDao().insertLoan(
        Loan(
          personName = "Other",
          type = LoanType.DEBTOR,
          originalAmount = 1L,
          remainingAmount = 1L,
          description = "local loan"
        )
      )

      repo.mergeFromBackup(payload)
      val mergedLoan = target.loanDao().getAllLoansBlocking().single { it.personName == PERSON }
      val sourceLoanId = payload.loans.single().id
      assertNotEquals("merge must give the loan a fresh id", sourceLoanId, mergedLoan.id)
      val mergedPayment = target.paymentHistoryDao().getPaymentHistoriesForLoanSync(mergedLoan.id).single()
      val linked = assertNotNullAndGet(mergedPayment.linkedTransactionId)
      val merged = target.transactionDao().getAllTransactionsBlocking()
      assertEquals(PAYMENT_AMOUNT, merged.single { it.id == linked }.amount)
      assertEquals(mergedLoan.id, merged.single { it.loanId != null }.loanId)

      repo.deleteLoan(mergedLoan)

      val remaining =
        target
          .transactionDao()
          .getAllTransactionsBlocking()
          .map { it.description }
          .sorted()
      assertEquals(listOf("local 1", "local 2", MANUAL_DESCRIPTION).sorted(), remaining)
    }

  private fun <T : Any> assertNotNullAndGet(value: T?): T {
    assertNotNull(value)
    return requireNotNull(value)
  }

  private companion object {
    const val LOANS_CATEGORY_ID = 1L
    const val PERSON = "Ali"
    const val PAYMENT_AMOUNT = 1_000_000L
    const val PAYMENT_DATE = 1_700_000_000_000L
    const val MANUAL_DESCRIPTION = "manual collision"
  }
}
