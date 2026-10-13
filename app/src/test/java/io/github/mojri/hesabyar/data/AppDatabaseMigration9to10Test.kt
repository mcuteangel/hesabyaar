package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for MIGRATION_9_10 — stableId column on the accounts
 * table with a UUID v4 backfill for pre-existing rows (issue #156).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppDatabaseMigration9to10Test {
  private val uuidV4Pattern =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

  private fun createV9CoreTables(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS categories (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "name TEXT NOT NULL, key TEXT NOT NULL, " +
        "icon TEXT NOT NULL, color INTEGER NOT NULL, " +
        "type TEXT NOT NULL, isDefault INTEGER NOT NULL)"
    )
    // v9 accounts schema: identical to v8 (MIGRATION_8_9 did not touch it),
    // without the stableId column that MIGRATION_9_10 adds.
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS accounts (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "name TEXT NOT NULL, type TEXT NOT NULL, bankName TEXT, " +
        "cardNumber TEXT, accountNumber TEXT, iban TEXT, " +
        "initialBalance INTEGER NOT NULL DEFAULT 0, " +
        "color INTEGER NOT NULL DEFAULT 4283215696, icon TEXT, " +
        "isArchived INTEGER NOT NULL DEFAULT 0, " +
        "displayOrder INTEGER NOT NULL DEFAULT 0, " +
        "createdAt INTEGER NOT NULL DEFAULT 0, " +
        "updatedAt INTEGER NOT NULL DEFAULT 0)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS persons (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "name TEXT NOT NULL, normalizedName TEXT NOT NULL, " +
        "phone TEXT, notes TEXT, createdAt INTEGER NOT NULL, " +
        "isArchived INTEGER NOT NULL DEFAULT 0)"
    )
    db.execSQL(
      "CREATE UNIQUE INDEX IF NOT EXISTS index_persons_normalizedName " +
        "ON persons (normalizedName)"
    )
  }

  private fun createV9LedgerTables(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS transactions (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "type TEXT NOT NULL, categoryId INTEGER NOT NULL, " +
        "amount INTEGER NOT NULL, description TEXT NOT NULL, " +
        "personName TEXT, date INTEGER NOT NULL, " +
        "dueDate INTEGER, installmentId INTEGER, " +
        "accountId INTEGER NOT NULL DEFAULT 1, " +
        "destinationAccountId INTEGER DEFAULT NULL, " +
        "personId INTEGER DEFAULT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS loans (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "personName TEXT NOT NULL, type TEXT NOT NULL, " +
        "originalAmount INTEGER NOT NULL, " +
        "remainingAmount INTEGER NOT NULL, " +
        "description TEXT NOT NULL, date INTEGER NOT NULL, " +
        "isSettled INTEGER NOT NULL, personId INTEGER DEFAULT NULL, " +
        "tracked INTEGER NOT NULL DEFAULT 0, accountId INTEGER)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS installments (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "title TEXT NOT NULL, amount INTEGER NOT NULL, " +
        "dueDate INTEGER NOT NULL, isPaid INTEGER NOT NULL, " +
        "reminderEnabled INTEGER NOT NULL, " +
        "notes TEXT NOT NULL, bankLoanId INTEGER, " +
        "tracked INTEGER NOT NULL DEFAULT 0, accountId INTEGER)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS payment_history (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "loanId INTEGER NOT NULL, amount INTEGER NOT NULL, " +
        "date INTEGER NOT NULL, notes TEXT NOT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS bank_loans (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "bankName TEXT NOT NULL, loanName TEXT NOT NULL, " +
        "receivedAmount INTEGER NOT NULL, " +
        "monthlyInstallmentAmount INTEGER NOT NULL, " +
        "numberOfInstallments INTEGER NOT NULL, " +
        "totalRepayableAmount INTEGER NOT NULL, " +
        "totalInterest INTEGER NOT NULL, " +
        "startDate INTEGER NOT NULL, " +
        "description TEXT NOT NULL, " +
        "isSettled INTEGER NOT NULL, " +
        "tracked INTEGER NOT NULL DEFAULT 0, accountId INTEGER)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS room_master_table (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "identity_hash TEXT)"
    )
    db.execSQL(
      "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '')"
    )
  }

  private fun createAndSeedV9Database(
    context: Context,
    dbName: String,
    seed: (SupportSQLiteDatabase) -> Unit
  ) {
    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
          .builder(context)
          .name(dbName)
          .callback(
            object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(9) {
              override fun onCreate(db: SupportSQLiteDatabase) {
                createV9CoreTables(db)
                createV9LedgerTables(db)
              }

              override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
              ) {}
            }
          ).build()
      )
    val raw = helper.writableDatabase
    seed(raw)
    raw.close()
    helper.close()
  }

  @Test
  fun migration9to10BackfillsDistinctUuidV4StableIds() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val dbName = "migration_9_10_test_db"
      val dbFile = context.getDatabasePath(dbName)

      try {
        createAndSeedV9Database(context, dbName) { raw -> seedV9Accounts(raw) }

        val migratedDb =
          Room
            .databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_9_10)
            .build()

        assertBackfilledStableIds(migratedDb)
        assertInsertWithStableId(migratedDb)

        migratedDb.close()
      } finally {
        dbFile.delete()
        context.getDatabasePath("$dbName-wal").delete()
        context.getDatabasePath("$dbName-shm").delete()
      }
    }

  private fun seedV9Accounts(raw: SupportSQLiteDatabase) {
    // Two rows share a name on purpose: the backfill must still give
    // each row its own ID (this is the issue #156 failure mode).
    raw.execSQL(
      "INSERT INTO accounts (name, type, initialBalance) " +
        "VALUES ('حساب اصلی', 'BANK', 1000000)"
    )
    raw.execSQL(
      "INSERT INTO accounts (name, type, initialBalance) " +
        "VALUES ('پس‌انداز', 'SAVINGS_INVESTMENT', 500000)"
    )
    raw.execSQL(
      "INSERT INTO accounts (name, type, initialBalance) " +
        "VALUES ('پس‌انداز', 'BANK', 250000)"
    )
  }

  private fun assertBackfilledStableIds(migratedDb: AppDatabase) {
    val accounts = migratedDb.accountDao().getAllAccountsBlocking()
    assertEquals("all pre-existing rows preserved", 3, accounts.size)

    val main = accounts.first { it.name == "حساب اصلی" }
    assertEquals("pre-existing data untouched", 1000000L, main.initialBalance)

    val ids = accounts.map { it.stableId }
    assertTrue("backfill leaves no blank stable IDs", ids.all { it.isNotBlank() })
    assertEquals("backfilled IDs are distinct per row", 3, ids.toSet().size)
    assertTrue(
      "backfilled IDs are UUID v4 shaped",
      ids.all { uuidV4Pattern.matches(it) }
    )
  }

  private suspend fun assertInsertWithStableId(migratedDb: AppDatabase) {
    // New rows inserted after the migration keep working with the new column.
    val freshId =
      migratedDb.accountDao().insert(
        AccountEntity(
          name = "Fresh",
          type = AccountType.CASH_WALLET,
          stableId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        )
      )
    val fresh = migratedDb.accountDao().getAllAccountsBlocking().first { it.id == freshId }
    assertEquals(
      "explicit stable ID survives insert",
      "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      fresh.stableId
    )
  }
}
