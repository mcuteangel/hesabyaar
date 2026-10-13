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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stable-ID merge semantics for backup restore (issue #156). Backup accounts
 * match local rows by stableId first; name matching is only the legacy
 * fallback for rows and backups that predate stable IDs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RepositoryMergeAccountsStableIdTest {
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

  private fun account(
    id: Long,
    name: String,
    type: AccountType = AccountType.BANK,
    stableId: String = ""
  ) = AccountEntity(
    id = id,
    name = name,
    type = type,
    color = 0L,
    icon = "",
    isArchived = false,
    displayOrder = 0,
    stableId = stableId
  )

  private fun transaction(
    id: Long,
    type: TransactionType,
    accountId: Long,
    amount: Long = 3000L
  ) = Transaction(
    id = id,
    type = type,
    categoryId = 0L,
    amount = amount,
    description = "tx",
    personName = null,
    date = System.currentTimeMillis(),
    accountId = accountId
  )

  private fun backupPayload(
    accounts: List<AccountEntity>,
    transactions: List<Transaction>
  ) = BackupPayload(
    version = 3,
    timestamp = System.currentTimeMillis(),
    appVersion = "3.0",
    accounts = accounts,
    transactions = transactions
  )

  @Test
  fun sameNameDifferentStableIdMergeSeparatelyWithoutCollapse() =
    runTest {
      val uuidA = "11111111-1111-4111-8111-111111111111"
      val uuidB = "22222222-2222-4222-8222-222222222222"
      val repo = createRepository()
      repo.insertAccount(account(id = 5L, name = "پس‌انداز", stableId = uuidA))
      repo.insertAccount(account(id = 6L, name = "پس‌انداز", stableId = uuidB))
      val backup =
        backupPayload(
          accounts =
            listOf(
              account(id = 10L, name = "پس‌انداز", stableId = uuidA),
              account(id = 20L, name = "پس‌انداز", stableId = uuidB)
            ),
          transactions =
            listOf(
              transaction(id = 1L, type = TransactionType.EXPENSE, accountId = 10L, amount = 1_000L),
              transaction(id = 2L, type = TransactionType.EXPENSE, accountId = 20L, amount = 2_000L)
            )
        )
      repo.mergeFromBackup(backup)

      val savings = database.accountDao().getAllAccountsBlocking().filter { it.name == "پس‌انداز" }
      assertEquals(
        "same-name accounts with distinct stable IDs must not collapse",
        2,
        savings.size
      )
      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("both backup transactions must be restored", 2, txs.size)
      assertEquals(
        "backup account 10 must remap to the local row carrying uuidA",
        5L,
        txs.first { it.amount == 1_000L }.accountId
      )
      assertEquals(
        "backup account 20 must remap to the local row carrying uuidB",
        6L,
        txs.first { it.amount == 2_000L }.accountId
      )
    }

  @Test
  fun renamedAccountWithStableIdMatchesById() =
    runTest {
      val uuid = "33333333-3333-4333-8333-333333333333"
      val repo = createRepository()
      repo.insertAccount(account(id = 5L, name = "New Name", stableId = uuid))
      val backup =
        backupPayload(
          accounts = listOf(account(id = 10L, name = "Old Name", stableId = uuid)),
          transactions =
            listOf(
              transaction(id = 1L, type = TransactionType.EXPENSE, accountId = 10L, amount = 1_000L)
            )
        )
      repo.mergeFromBackup(backup)

      val locals = database.accountDao().getAllAccountsBlocking()
      assertEquals(
        "stable-ID match must not create a duplicate row",
        2,
        locals.size
      )
      val merged = locals.first { it.id == 5L }
      assertEquals("backup data wins on the matched row", "Old Name", merged.name)
      assertEquals("stable ID survives the merge", uuid, merged.stableId)
      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("transaction must remap to the matched local row", 5L, txs.first().accountId)
    }

  @Test
  fun legacyBackupNameFallbackPreservesLocalStableId() =
    runTest {
      val uuid = "44444444-4444-4434-8444-444444444444"
      val repo = createRepository()
      repo.insertAccount(account(id = 5L, name = "Main", stableId = uuid))
      val backup =
        backupPayload(
          accounts = listOf(account(id = 10L, name = "Main")),
          transactions =
            listOf(
              transaction(id = 1L, type = TransactionType.EXPENSE, accountId = 10L, amount = 1_000L)
            )
        )
      repo.mergeFromBackup(backup)

      val local = database.accountDao().getAllAccountsBlocking().first { it.id == 5L }
      assertEquals(
        "a blank backup stable ID must never wipe the local one",
        uuid,
        local.stableId
      )
      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("transaction must remap to the name-matched local row", 5L, txs.first().accountId)
    }

  @Test
  fun nameFallbackAdoptsBackupStableIdWhenLocalIsBlank() =
    runTest {
      val uuid = "55555555-5555-4555-8555-555555555555"
      val repo = createRepository()
      repo.insertAccount(account(id = 5L, name = "Main"))
      val backup =
        backupPayload(
          accounts = listOf(account(id = 10L, name = "Main", stableId = uuid)),
          transactions =
            listOf(
              transaction(id = 1L, type = TransactionType.EXPENSE, accountId = 10L, amount = 1_000L)
            )
        )
      repo.mergeFromBackup(backup)

      val local = database.accountDao().getAllAccountsBlocking().first { it.id == 5L }
      assertEquals(
        "a blank local stable ID is repaired from the backup",
        uuid,
        local.stableId
      )
    }

  @Test
  fun unmatchedBackupAccountWithBlankStableIdGetsFreshUuidOnInsert() =
    runTest {
      val repo = createRepository()
      val backup =
        backupPayload(
          accounts =
            listOf(
              account(id = 10L, name = "Fresh A"),
              account(id = 20L, name = "Fresh B")
            ),
          transactions = emptyList()
        )
      repo.mergeFromBackup(backup)

      val fresh = database.accountDao().getAllAccountsBlocking().filter { it.name.startsWith("Fresh") }
      assertEquals("both unmatched accounts must be inserted", 2, fresh.size)
      assertTrue(
        "inserted rows must carry a minted stable ID",
        fresh.all { it.stableId.isNotBlank() }
      )
      assertEquals(
        "minted stable IDs must be distinct",
        2,
        fresh.map { it.stableId }.toSet().size
      )
    }
}
