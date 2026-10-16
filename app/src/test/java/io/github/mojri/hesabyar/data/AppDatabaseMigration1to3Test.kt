package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the historical [AppDatabase.MIGRATION_1_2] and [AppDatabase.MIGRATION_2_3].
 * Verifies that MIGRATION_1_2 converts Double amounts in Toman to Long amounts in Rial
 * by multiplying by 10, and MIGRATION_2_3 introduces categories and remaps transactions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppDatabaseMigration1to3Test {
  private lateinit var context: Context
  private val dbNames = mutableListOf<String>()
  private val openedDatabases = mutableListOf<AppDatabase>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @After
  fun tearDown() {
    openedDatabases.forEach { db ->
      if (db.isOpen) db.close()
    }
    openedDatabases.clear()
    dbNames.forEach { name ->
      context.getDatabasePath(name).delete()
      context.getDatabasePath("$name-wal").delete()
      context.getDatabasePath("$name-shm").delete()
    }
    dbNames.clear()
  }

  @Test
  fun migration1to2ConvertsTomanToRialAcrossAllMonetaryTables() {
    val dbName = "test_mig_1_2"
    dbNames += dbName

    // Seed v1 database with REAL (Double) amounts
    createAndSeedV1Database(dbName) { db ->
      db.execSQL(
        "INSERT INTO transactions " +
          "(id, type, category, amount, description, personName, date, dueDate, installmentId) " +
          "VALUES (1, 'EXPENSE', 'Food', 5000.0, 'lunch', 'علی', 1000, NULL, NULL)"
      )
      db.execSQL(
        "INSERT INTO loans (id, personName, type, originalAmount, remainingAmount, description, date, isSettled) " +
          "VALUES (1, 'رضا', 'DEBTOR', 20000.0, 15000.0, 'loan desc', 1000, 0)"
      )
      db.execSQL(
        "INSERT INTO installments (id, title, amount, dueDate, isPaid, reminderEnabled, notes) " +
          "VALUES (1, 'installment title', 3000.0, 1000, 0, 1, 'note')"
      )
      db.execSQL(
        "INSERT INTO payment_history (id, loanId, amount, date, notes) " +
          "VALUES (1, 1, 1000.0, 1000, 'paid part')"
      )
    }

    // Migrate to latest via standard Room builder which applies all migrations starting at 1
    val migrated =
      Room
        .databaseBuilder(context, AppDatabase::class.java, dbName)
        .allowMainThreadQueries()
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()

    openedDatabases += migrated

    val tx = migrated.transactionDao().getAllTransactionsBlocking().single()
    assertEquals("Transaction amount should be 5000 * 10 = 50000 Rial", 50000L, tx.amount)

    val loan = migrated.loanDao().getAllLoansBlocking().single()
    assertEquals("Loan originalAmount should be 20000 * 10 = 200000 Rial", 200000L, loan.originalAmount)
    assertEquals("Loan remainingAmount should be 15000 * 10 = 150000 Rial", 150000L, loan.remainingAmount)

    val installment = migrated.installmentDao().getAllInstallmentsBlocking().single()
    assertEquals("Installment amount should be 3000 * 10 = 30000 Rial", 30000L, installment.amount)

    val payment = migrated.paymentHistoryDao().getAllPaymentHistoriesBlocking().single()
    assertEquals("Payment amount should be 1000 * 10 = 10000 Rial", 10000L, payment.amount)
  }

  @Test
  fun migration2to3CreatesCategoriesAndRemapsTransactionCategories() {
    val dbName = "test_mig_2_3"
    dbNames += dbName

    createAndSeedV2Database(dbName) { db ->
      db.execSQL(
        "INSERT INTO transactions " +
          "(id, type, category, amount, description, personName, date, dueDate, installmentId) " +
          "VALUES (1, 'EXPENSE', 'Food', 50000, 'lunch', 'علی', 1000, NULL, NULL)"
      )
      db.execSQL(
        "INSERT INTO loans (id, personName, type, originalAmount, remainingAmount, description, date, isSettled) " +
          "VALUES (1, 'رضا', 'DEBTOR', 200000, 150000, 'loan desc', 1000, 0)"
      )
      db.execSQL(
        "INSERT INTO installments (id, title, amount, dueDate, isPaid, reminderEnabled, notes) " +
          "VALUES (1, 'installment title', 30000, 1000, 0, 1, 'note')"
      )
      db.execSQL(
        "INSERT INTO payment_history (id, loanId, amount, date, notes) " +
          "VALUES (1, 1, 10000, 1000, 'paid part')"
      )
    }

    val migrated =
      Room
        .databaseBuilder(context, AppDatabase::class.java, dbName)
        .allowMainThreadQueries()
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()

    openedDatabases += migrated

    val tx = migrated.transactionDao().getAllTransactionsBlocking().single()
    assertEquals("Amount should remain unchanged across v2->v3", 50000L, tx.amount)
    assertEquals("Food string category should map to categoryId 1", 1L, tx.categoryId)
    val foodCategory = migrated.categoryDao().getAllCategoriesBlocking().first { it.key == "Food" }
    assertEquals(1L, foodCategory.id)
  }

  private fun createAndSeedV2Database(
    dbName: String,
    seed: (SupportSQLiteDatabase) -> Unit
  ) {
    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
          .builder(context)
          .name(dbName)
          .callback(
            object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
              override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                  "CREATE TABLE transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "type TEXT NOT NULL, category TEXT NOT NULL, " +
                    "amount INTEGER NOT NULL, description TEXT NOT NULL, " +
                    "personName TEXT, date INTEGER NOT NULL, " +
                    "dueDate INTEGER, installmentId INTEGER)"
                )
                db.execSQL(
                  "CREATE TABLE loans (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "personName TEXT NOT NULL, type TEXT NOT NULL, " +
                    "originalAmount INTEGER NOT NULL, remainingAmount INTEGER NOT NULL, " +
                    "description TEXT NOT NULL, date INTEGER NOT NULL, " +
                    "isSettled INTEGER NOT NULL)"
                )
                db.execSQL(
                  "CREATE TABLE installments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, amount INTEGER NOT NULL, " +
                    "dueDate INTEGER NOT NULL, isPaid INTEGER NOT NULL, " +
                    "reminderEnabled INTEGER NOT NULL, notes TEXT NOT NULL)"
                )
                db.execSQL(
                  "CREATE TABLE payment_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "loanId INTEGER NOT NULL, amount INTEGER NOT NULL, " +
                    "date INTEGER NOT NULL, notes TEXT NOT NULL)"
                )
                db.execSQL(
                  "CREATE TABLE room_master_table (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "identity_hash TEXT)"
                )
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '')")
              }

              override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
              ) {
                error("No upgrade in seed helper ($oldVersion -> $newVersion)")
              }
            }
          ).build()
      )
    val raw = helper.writableDatabase
    seed(raw)
    raw.close()
    helper.close()
  }

  private fun createAndSeedV1Database(
    dbName: String,
    seed: (SupportSQLiteDatabase) -> Unit
  ) {
    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
          .builder(context)
          .name(dbName)
          .callback(
            object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
              override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                  "CREATE TABLE transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "type TEXT NOT NULL, category TEXT NOT NULL, " +
                    "amount REAL NOT NULL, description TEXT NOT NULL, " +
                    "personName TEXT, date INTEGER NOT NULL, " +
                    "dueDate INTEGER, installmentId INTEGER)"
                )
                db.execSQL(
                  "CREATE TABLE loans (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "personName TEXT NOT NULL, type TEXT NOT NULL, " +
                    "originalAmount REAL NOT NULL, remainingAmount REAL NOT NULL, " +
                    "description TEXT NOT NULL, date INTEGER NOT NULL, " +
                    "isSettled INTEGER NOT NULL)"
                )
                db.execSQL(
                  "CREATE TABLE installments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, amount REAL NOT NULL, " +
                    "dueDate INTEGER NOT NULL, isPaid INTEGER NOT NULL, " +
                    "reminderEnabled INTEGER NOT NULL, notes TEXT NOT NULL)"
                )
                db.execSQL(
                  "CREATE TABLE payment_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "loanId INTEGER NOT NULL, amount REAL NOT NULL, " +
                    "date INTEGER NOT NULL, notes TEXT NOT NULL)"
                )
                db.execSQL(
                  "CREATE TABLE room_master_table (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "identity_hash TEXT)"
                )
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '')")
              }

              override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
              ) {
                error("No upgrade in seed helper ($oldVersion -> $newVersion)")
              }
            }
          ).build()
      )
    val raw = helper.writableDatabase
    seed(raw)
    raw.close()
    helper.close()
  }
}
