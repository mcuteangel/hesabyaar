package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for the backup-restore paths against a real in-memory Room
 * database. Split out of [RepositoryLogicTest] to keep that class under the
 * detekt LargeClass threshold; this cluster groups the account-aware
 * merge/replace restore behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RepositoryBackupRestoreTest {
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
    // (AppDatabase onCreate) and on upgrade (MIGRATION_5_6); the in-memory
    // test DB bypasses both, so mirror the invariant here.
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
      database
    )

  @Test
  fun mergeFromBackupRemapsTransactionAccountIdsWhenBackupIdsDifferFromLocal() =
    runTest {
      val repo = createRepository()
      repo.insertAccount(account(id = 5L, name = "Local Account", color = 0xFF4CAF50L))
      val backup =
        backupPayload(
          accounts =
            listOf(
              account(id = 5L, name = "Backup Account", color = 0xFFE91E63L),
              account(id = 10L, name = "Backup Dest", color = 0xFF2196F3L)
            ),
          transactions =
            listOf(
              transaction(
                id = 1L,
                type = TransactionType.TRANSFER,
                accountId = 5L,
                destinationAccountId = 10L,
                amount = 50_000L
              )
            )
        )
      repo.mergeFromBackup(backup)
      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, txs.size)
      val mergedTx = txs.first()
      assertNotEquals(5L, mergedTx.accountId)
      assertNotEquals(10L, mergedTx.destinationAccountId)
      requireNotNull(database.accountDao().getById(mergedTx.accountId))
      requireNotNull(database.accountDao().getById(mergedTx.destinationAccountId!!))
      assertEquals("Local Account", database.accountDao().getById(5L)?.name)
    }

  @Test
  fun mergeFromBackupSkipsTransactionWhoseSourceAccountIsNotInBackupOrLocalDb() =
    runTest {
      val repo = createRepository()
      repo.insertAccount(account(id = 5L, name = "Local Account", color = 0xFF4CAF50L))
      val backup =
        backupPayload(
          accounts = listOf(account(id = 5L, name = "Backup Account", color = 0xFFE91E63L)),
          transactions =
            listOf(
              transaction(id = 1L, type = TransactionType.INCOME, accountId = 99L, amount = 10_000L),
              transaction(id = 2L, type = TransactionType.EXPENSE, accountId = 5L, amount = 5_000L)
            )
        )
      repo.mergeFromBackup(backup)

      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, txs.size)
      assertEquals(5_000L, txs.first().amount)
      assertTrue("orphaned accountId=99 must not be written", txs.none { it.accountId == 99L })
    }

  @Test
  fun mergeFromBackupSkipsTransferWhoseDestinationAccountIsNotInBackup() =
    runTest {
      val repo = createRepository()
      repo.insertAccount(account(id = 5L, name = "Local Account", color = 0xFF4CAF50L))
      val backup =
        backupPayload(
          accounts = listOf(account(id = 5L, name = "Backup Account", color = 0xFFE91E63L)),
          transactions =
            listOf(
              transaction(
                id = 1L,
                type = TransactionType.TRANSFER,
                accountId = 5L,
                destinationAccountId = 99L,
                amount = 50_000L
              )
            )
        )
      repo.mergeFromBackup(backup)

      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertTrue("transfer with dangling destination must not be written", txs.isEmpty())
    }

  @Test
  fun mergeFromBackupKeepsLegacyDefaultAccountIdWhenAccountsSectionIsEmpty() =
    runTest {
      val repo = createRepository()
      val backup =
        backupPayload(
          accounts = emptyList(),
          transactions =
            listOf(
              transaction(id = 1L, type = TransactionType.EXPENSE, accountId = 1L, amount = 3_000L)
            )
        )
      repo.mergeFromBackup(backup)

      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, txs.size)
      assertEquals(1L, txs.first().accountId)
      requireNotNull(database.accountDao().getById(txs.first().accountId))
    }

  @Test
  fun replaceFromLegacyBackupWithoutAccountsKeepsDefaultAccount() =
    runTest {
      val repo = createRepository()
      // Pre-existing account that replace mode must wipe out.
      repo.insertAccount(account(id = 5L, name = "Old Account", color = 0xFF4CAF50L))
      val backup =
        backupPayload(
          accounts = emptyList(),
          transactions =
            listOf(
              transaction(id = 1L, type = TransactionType.EXPENSE, accountId = 1L, amount = 3_000L)
            )
        )
      repo.replaceAllFromBackup(backup)

      // (a) The default account (id=1) must still exist after the restore.
      val accounts = database.accountDao().getAllAccountsBlocking()
      assertEquals(1, accounts.size)
      assertEquals(1L, accounts.first().id)

      // (b) The restored transaction is not orphaned — its accountId resolves.
      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, txs.size)
      assertEquals(1L, txs.first().accountId)
      requireNotNull(database.accountDao().getById(txs.first().accountId))
      // The pre-existing account was wiped by replace mode.
      assertEquals(null, database.accountDao().getById(5L))
    }

  private fun account(
    id: Long,
    name: String,
    type: AccountType = AccountType.BANK,
    color: Long = 0L
  ) = AccountEntity(id = id, name = name, type = type, color = color, icon = "", isArchived = false, displayOrder = 0)

  private fun transaction(
    id: Long,
    type: TransactionType,
    accountId: Long,
    destinationAccountId: Long? = null,
    amount: Long = 3000L
  ) = Transaction(
    id = id,
    type = type,
    categoryId = 0L,
    amount = amount,
    description = "tx",
    personName = null,
    date = System.currentTimeMillis(),
    accountId = accountId,
    destinationAccountId = destinationAccountId
  )

  private fun backupPayload(
    accounts: List<AccountEntity>,
    transactions: List<Transaction>
  ) = BackupPayload(
    version = 1,
    timestamp = System.currentTimeMillis(),
    appVersion = "1.0",
    accounts = accounts,
    transactions = transactions
  )
}
