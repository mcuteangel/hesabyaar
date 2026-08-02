package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Regression tests for [AppDatabase.migratePlaintextToEncryptedIfNeeded].
 *
 * The migration reads all entity types from a plaintext DB and inserts them
 * into an encrypted DB. Previously, accounts were missing from both the read
 * and the insert, causing data loss during conversion.
 *
 * These tests simulate the conversion pattern (read from source → insert into
 * target) using two in-memory Room databases to verify all entity types
 * survive the round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppDatabaseMigrationTest {
  private lateinit var sourceDb: AppDatabase
  private lateinit var targetDb: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    sourceDb =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    targetDb =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
  }

  @After
  fun tearDown() {
    sourceDb.close()
    targetDb.close()
  }

  @Test
  fun accountsSurviveConversionToEncryptedDb() =
    runTest {
      // Arrange: insert accounts into source (simulating plaintext DB)
      val account1 =
        AccountEntity(
          id = 1,
          name = "حساب اصلی",
          type = AccountType.BANK,
          initialBalance = 5_000_000,
          createdAt = 1000L,
          updatedAt = 2000L,
        )
      val account2 =
        AccountEntity(
          id = 2,
          name = "کیف پول",
          type = AccountType.CASH_WALLET,
          initialBalance = 1_000_000,
          createdAt = 3000L,
          updatedAt = 4000L,
        )
      sourceDb.accountDao().insertAllBlocking(listOf(account1, account2))

      // Act: read from source, insert into target (same pattern as migration)
      val accounts = sourceDb.accountDao().getAllAccountsBlocking()
      assertTrue("Source must have accounts", accounts.isNotEmpty())
      targetDb.accountDao().insertAllBlocking(accounts)

      // Assert: accounts survived the round-trip
      val migrated = targetDb.accountDao().getAllAccountsBlocking()
      assertEquals("Should have 2 accounts after migration", 2, migrated.size)

      val migrated1 = migrated.find { it.id == 1L }
      assertEquals("Account 1 name preserved", "حساب اصلی", migrated1?.name)
      assertEquals("Account 1 balance preserved", 5_000_000L, migrated1?.initialBalance)
      assertEquals("Account 1 createdAt preserved", 1000L, migrated1?.createdAt)
      assertEquals("Account 1 updatedAt preserved", 2000L, migrated1?.updatedAt)

      val migrated2 = migrated.find { it.id == 2L }
      assertEquals("Account 2 name preserved", "کیف پول", migrated2?.name)
      assertEquals("Account 2 type preserved", AccountType.CASH_WALLET, migrated2?.type)
    }

  @Test
  fun allEntityTypesSurviveConversion() =
    runTest {
      // Arrange: insert all entity types into source
      val account =
        AccountEntity(
          id = 1,
          name = "حساب اصلی",
          type = AccountType.BANK,
          createdAt = 1000L,
          updatedAt = 2000L,
        )
      sourceDb.accountDao().insertAllBlocking(listOf(account))

      val category =
        Category(
          id = 1,
          name = "خوراک",
          key = "Food",
          icon = "Restaurant",
          color = 808464432,
          type = CategoryType.EXPENSE,
          isDefault = true,
        )
      sourceDb.categoryDao().insertAllBlocking(listOf(category))

      val transaction =
        Transaction(
          id = 1,
          type = TransactionType.EXPENSE,
          categoryId = 1L,
          amount = 100_000L,
          description = "ناهار",
          date = System.currentTimeMillis(),
          accountId = 1,
        )
      sourceDb.transactionDao().insertAllBlocking(listOf(transaction))

      // Act: read all from source, insert into target
      val accounts = sourceDb.accountDao().getAllAccountsBlocking()
      val categories = sourceDb.categoryDao().getAllCategoriesBlocking()
      val transactions = sourceDb.transactionDao().getAllTransactionsBlocking()

      targetDb.accountDao().insertAllBlocking(accounts)
      targetDb.categoryDao().insertAllBlocking(categories)
      targetDb.transactionDao().insertAllBlocking(transactions)

      // Assert: all entity types survived
      assertEquals("Accounts survived", 1, targetDb.accountDao().getAllAccountsBlocking().size)
      assertEquals("Categories survived", 1, targetDb.categoryDao().getAllCategoriesBlocking().size)
      assertEquals("Transactions survived", 1, targetDb.transactionDao().getAllTransactionsBlocking().size)
    }

  @Test
  fun emptyAccountsListDoesNotFail() =
    runTest {
      // Act: read empty accounts from source, insert into target
      val accounts = sourceDb.accountDao().getAllAccountsBlocking()
      assertTrue("Source should have no accounts", accounts.isEmpty())
      targetDb.accountDao().insertAllBlocking(accounts)

      // Assert: target also has no accounts (no crash)
      assertEquals("Target should have no accounts", 0, targetDb.accountDao().getAllAccountsBlocking().size)
    }
}
