package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupTrackedLedgerRestoreNormalizationTest {
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
  fun replaceAllFromBackupNormalizesTrackedInstallmentWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          installments =
            listOf(
              Installment(
                id = 1L,
                title = "Invalid tracked",
                amount = 1_000_000L,
                dueDate = 1000L,
                tracked = true,
                accountId = null
              ),
              Installment(
                id = 2L,
                title = "Stale untracked",
                amount = 2_000_000L,
                dueDate = 1000L,
                tracked = false,
                accountId = 99L
              ),
              Installment(
                id = 3L,
                title = "Valid tracked",
                amount = 3_000_000L,
                dueDate = 1000L,
                tracked = true,
                accountId = DEFAULT_ACCOUNT_ID
              )
            )
        )

      repo.replaceAllFromBackup(payload)

      val stored = database.installmentDao().getAllInstallmentsSync()
      val invalidTracked = stored.first { it.title == "Invalid tracked" }
      assertFalse(invalidTracked.tracked)
      assertNull(invalidTracked.accountId)

      val staleUntracked = stored.first { it.title == "Stale untracked" }
      assertFalse(staleUntracked.tracked)
      assertNull(staleUntracked.accountId)

      val validTracked = stored.first { it.title == "Valid tracked" }
      assertTrue(validTracked.tracked)
      assertEquals(DEFAULT_ACCOUNT_ID, validTracked.accountId)
    }

  @Test
  fun mergeFromBackupNormalizesTrackedInstallmentWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          installments =
            listOf(
              Installment(
                id = 10L,
                title = "Merge invalid tracked",
                amount = 500_000L,
                dueDate = 2000L,
                tracked = true,
                accountId = 0L
              ),
              Installment(
                id = 20L,
                title = "Merge stale untracked",
                amount = 700_000L,
                dueDate = 2000L,
                tracked = false,
                accountId = 42L
              )
            )
        )

      repo.mergeFromBackup(payload)

      val stored = database.installmentDao().getAllInstallmentsSync()
      val invalid = stored.first { it.title == "Merge invalid tracked" }
      assertFalse(invalid.tracked)
      assertNull(invalid.accountId)

      val stale = stored.first { it.title == "Merge stale untracked" }
      assertFalse(stale.tracked)
      assertNull(stale.accountId)
    }

  @Test
  fun replaceAllFromBackupNormalizesTrackedLoanWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          loans =
            listOf(
              Loan(
                id = 1L,
                personName = "Ali",
                type = LoanType.CREDITOR,
                originalAmount = 1_000_000L,
                remainingAmount = 1_000_000L,
                description = "",
                date = 1000L,
                tracked = true,
                accountId = null
              ),
              Loan(
                id = 2L,
                personName = "Reza",
                type = LoanType.DEBTOR,
                originalAmount = 2_000_000L,
                remainingAmount = 2_000_000L,
                description = "",
                date = 1000L,
                tracked = false,
                accountId = 88L
              )
            )
        )

      repo.replaceAllFromBackup(payload)

      val stored = database.loanDao().getAllLoansSync()
      val invalidLoan = stored.first { it.personName == "Ali" }
      assertFalse(invalidLoan.tracked)
      assertNull(invalidLoan.accountId)

      val staleLoan = stored.first { it.personName == "Reza" }
      assertFalse(staleLoan.tracked)
      assertNull(staleLoan.accountId)
    }
}
