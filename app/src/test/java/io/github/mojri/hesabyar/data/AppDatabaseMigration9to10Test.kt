package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for MIGRATION_9_10 — linkedTransactionId on payment_history
 * and loanId on transactions (Issue #242: stable loan-payment transaction link).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppDatabaseMigration9to10Test {
  private fun createV9CoreTables(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS categories (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "name TEXT NOT NULL, key TEXT NOT NULL, " +
        "icon TEXT NOT NULL, color INTEGER NOT NULL, " +
        "type TEXT NOT NULL, isDefault INTEGER NOT NULL)"
    )
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
    createV9TransactionsAndLoans(db)
    createV9InstallmentsAndPayments(db)
    createV9BankLoansAndRoomMaster(db)
  }

  private fun createV9TransactionsAndLoans(db: SupportSQLiteDatabase) {
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
        "isSettled INTEGER NOT NULL, " +
        "personId INTEGER DEFAULT NULL, " +
        "tracked INTEGER NOT NULL DEFAULT 0, " +
        "accountId INTEGER DEFAULT NULL)"
    )
  }

  private fun createV9InstallmentsAndPayments(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS installments (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "title TEXT NOT NULL, amount INTEGER NOT NULL, " +
        "dueDate INTEGER NOT NULL, isPaid INTEGER NOT NULL, " +
        "reminderEnabled INTEGER NOT NULL, " +
        "notes TEXT NOT NULL, bankLoanId INTEGER, " +
        "tracked INTEGER NOT NULL DEFAULT 0, " +
        "accountId INTEGER DEFAULT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS payment_history (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "loanId INTEGER NOT NULL, amount INTEGER NOT NULL, " +
        "date INTEGER NOT NULL, notes TEXT NOT NULL)"
    )
  }

  private fun createV9BankLoansAndRoomMaster(db: SupportSQLiteDatabase) {
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
        "tracked INTEGER NOT NULL DEFAULT 0, " +
        "accountId INTEGER DEFAULT NULL)"
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
  fun migration9to10AddsLinkedTransactionIdAndLoanId() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "migration_9_10_test_db"
    val dbFile = context.getDatabasePath(dbName)

    try {
      createAndSeedV9Database(context, dbName) { raw ->
        raw.execSQL(
          "INSERT INTO payment_history (loanId, amount, date, notes) " +
            "VALUES (1, 50000, 1000, 'قسط اول')"
        )
        raw.execSQL(
          "INSERT INTO transactions (type, categoryId, amount, description, date, accountId) " +
            "VALUES ('EXPENSE', 1, 50000, 'تراکنش قدیمی', 1000, 1)"
        )
      }

      val migratedDb =
        Room
          .databaseBuilder(context, AppDatabase::class.java, dbName)
          .allowMainThreadQueries()
          .addMigrations(AppDatabase.MIGRATION_9_10)
          .build()

      // Verify payment_history row survived and has null linkedTransactionId
      val payments = migratedDb.paymentHistoryDao().getAllPaymentHistoriesBlocking()
      assertEquals(1, payments.size)
      assertEquals(1L, payments[0].loanId)
      assertEquals(50000L, payments[0].amount)
      assertNull(payments[0].linkedTransactionId)

      // Verify transaction row survived and has null loanId
      val txs = migratedDb.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, txs.size)
      assertEquals(50000L, txs[0].amount)
      assertNull(txs[0].loanId)

      // Verify new rows can use the new columns
      migratedDb.paymentHistoryDao().insertAllBlocking(
        listOf(PaymentHistory(loanId = 2L, amount = 30000L, linkedTransactionId = 99L))
      )
      val updatedPayments = migratedDb.paymentHistoryDao().getAllPaymentHistoriesBlocking()
      val newPayment = updatedPayments.first { it.loanId == 2L }
      assertEquals(99L, newPayment.linkedTransactionId)

      migratedDb.close()
    } finally {
      dbFile.delete()
      context.getDatabasePath("$dbName-wal").delete()
      context.getDatabasePath("$dbName-shm").delete()
    }
  }
}
