package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.core.MAX_AMOUNT_TOMAN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for MIGRATION_3_4.
 *
 * MIGRATION_1_2 once multiplied every amount by 1000 instead of 10, so the
 * stored Rial values were 100x too large. MIGRATION_3_4 divides by 100 to undo
 * that. An earlier revision used the single predicate `amount > 1000000000`,
 * which corrupted every legitimate amount above 1e9 Rial and left every
 * inflated amount at or below it in place.
 *
 * The hardened predicate is a bounded band: `MAX_LEGITIMATE_RIAL < amount <=
 * MAX_INFLATED_RIAL`. These tests pin both failure modes and the band
 * boundaries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppDatabaseMigration3to4Test {
  private lateinit var context: Context
  private val dbNames = mutableListOf<String>()
  private val openedDatabases = mutableListOf<AppDatabase>()

  /** Upper bound on a legitimate Rial amount. Mirrors MigrationSql. */
  private val maxLegitimateRial = MAX_AMOUNT_TOMAN * 10L

  /** Largest value the faulty MIGRATION_1_2 could produce. Mirrors MigrationSql. */
  private val maxInflatedRial = MAX_AMOUNT_TOMAN * 1000L

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

  /**
   * A legitimate amount above the old absolute threshold of 1e9 Rial. The
   * hardcoded predicate divided these by 100 and destroyed the value.
   * 3e9 Rial is 300 million Toman, an ordinary car purchase.
   */
  @Test
  fun legitimateAmountAboveOldThresholdIsNotDivided() {
    val amount = 3_000_000_000L
    val migrated =
      migrateV3To4(dbName = "mig34_legit_big") { raw ->
        raw.insertTransaction(amount = amount)
      }

    val tx = migrated.transactionDao().getAllTransactionsBlocking().single()
    assertEquals(
      "a legitimate 3e9 Rial amount must survive the correction untouched",
      amount,
      tx.amount
    )
  }

  /** A legitimate amount just under the band's lower bound. */
  @Test
  fun legitimateAmountAtBandLowerBoundIsNotDivided() {
    val amount = maxLegitimateRial
    val migrated =
      migrateV3To4(dbName = "mig34_legit_bound") { raw ->
        raw.insertTransaction(amount = amount)
      }

    val tx = migrated.transactionDao().getAllTransactionsBlocking().single()
    assertEquals(
      "an amount equal to MAX_LEGITIMATE_RIAL is still a legitimate value",
      amount,
      tx.amount
    )
  }

  /** A value above the band's upper bound is unexplainable and left alone. */
  @Test
  fun unexplainableAmountAboveBandUpperBoundIsNotDivided() {
    val amount = maxInflatedRial + 1_000L
    val migrated =
      migrateV3To4(dbName = "mig34_over_bound") { raw ->
        raw.insertTransaction(amount = amount)
      }

    val tx = migrated.transactionDao().getAllTransactionsBlocking().single()
    assertEquals(
      "an amount above MAX_INFLATED_RIAL is not corrected",
      amount,
      tx.amount
    )
  }

  /**
   * The inflation this migration exists to undo: 100x too large. 1e14 Rial is
   * inside the band, so it is the only case that still gets divided.
   */
  @Test
  fun inflatedValueInsideBandIsDividedBy100() {
    val inflated = 100_000_000_000_000L
    val corrected = inflated / 100L
    val migrated =
      migrateV3To4(dbName = "mig34_inflated") { raw ->
        raw.insertTransaction(amount = inflated)
      }

    val tx = migrated.transactionDao().getAllTransactionsBlocking().single()
    assertEquals(
      "an inflated value inside the band is divided by 100",
      corrected,
      tx.amount
    )
  }

  /**
   * Every column the faulty MIGRATION_1_2 touched is corrected, including
   * both amount columns on `loans`. `bank_loans` does not exist at version 3
   * (MIGRATION_4_5 creates it), so it is intentionally absent.
   */
  @Test
  fun everyAffectedColumnIsCorrected() {
    val inflated = 100_000_000_000_000L
    val corrected = inflated / 100L
    val migrated =
      migrateV3To4(dbName = "mig34_all_columns") { raw ->
        raw.insertTransaction(amount = inflated)
        raw.execSQL(
          "INSERT INTO loans (id, personName, type, originalAmount, remainingAmount, description, date, isSettled) " +
            "VALUES (1, 'رضا', 'DEBTOR', $inflated, $inflated, 'وام', 1000, 0)"
        )
        raw.execSQL(
          "INSERT INTO installments (id, title, amount, dueDate, isPaid, reminderEnabled, notes) " +
            "VALUES (1, 'قسط', $inflated, 1000, 0, 0, '')"
        )
        raw.execSQL(
          "INSERT INTO payment_history (id, loanId, amount, date, notes) " +
            "VALUES (1, 1, $inflated, 1000, '')"
        )
      }

    val tx = migrated.transactionDao().getAllTransactionsBlocking().single()
    val loan = migrated.loanDao().getAllLoansBlocking().single()
    val installment = migrated.installmentDao().getAllInstallmentsBlocking().single()
    val payment = migrated.paymentHistoryDao().getAllPaymentHistoriesBlocking().single()

    assertEquals("transactions.amount corrected", corrected, tx.amount)
    assertEquals("loans.originalAmount corrected", corrected, loan.originalAmount)
    assertEquals("loans.remainingAmount corrected", corrected, loan.remainingAmount)
    assertEquals("installments.amount corrected", corrected, installment.amount)
    assertEquals("payment_history.amount corrected", corrected, payment.amount)
  }

  /**
   * The documented limitation. A small v1 amount that the faulty MIGRATION_1_2
   * inflated — 5,000,000 Toman became 5e9 Rial, 100x too large — sits below the
   * band, so it is no longer corrected. It is left as-is rather than divided,
   * because no threshold can tell it apart from a legitimate amount. The row
   * survives intact, so the migration is loss-free for it.
   */
  @Test
  fun inflatedValueBelowBandIsLeftUnchanged() {
    val inflated = 5_000_000_000L
    val migrated =
      migrateV3To4(dbName = "mig34_below_band") { raw ->
        raw.insertTransaction(amount = inflated)
      }

    val tx = migrated.transactionDao().getAllTransactionsBlocking().single()
    assertEquals(
      "a below-band inflated value is left unchanged, not divided",
      inflated,
      tx.amount
    )
  }

  /**
   * The schema must still validate after the correction, so the database opens
   * cleanly at the current version and the corrected row stays readable.
   */
  @Test
  fun correctedDatabasePassesRoomSchemaValidation() {
    val inflated = 100_000_000_000_000L
    val corrected = inflated / 100L
    val migrated =
      migrateV3To4(dbName = "mig34_schema_ok") { raw ->
        raw.insertTransaction(amount = inflated)
        raw.execSQL(
          "INSERT INTO loans (id, personName, type, originalAmount, remainingAmount, description, date, isSettled) " +
            "VALUES (1, 'رضا', 'DEBTOR', $inflated, $inflated, 'وام', 1000, 0)"
        )
      }

    // A DAO round-trip is what forces Room to validate the migrated schema.
    assertEquals(1, migrated.transactionDao().getAllTransactionsBlocking().size)
    assertEquals(
      corrected,
      migrated
        .transactionDao()
        .getAllTransactionsBlocking()
        .single()
        .amount
    )
    assertEquals(1, migrated.loanDao().getAllLoansBlocking().size)
  }

  // ---------------------------------------------------------------- helpers

  /** Inserts one expense transaction in the version 3 shape. */
  private fun SupportSQLiteDatabase.insertTransaction(amount: Long) {
    execSQL(
      "INSERT INTO transactions (id, type, categoryId, amount, description, date) " +
        "VALUES (1, 'EXPENSE', 1, $amount, 'خرید', 1000)"
    )
  }

  /**
   * Builds a version 3 database seeded by [seed] and migrates it through the
   * full chain to the current version, so MIGRATION_3_4 runs for real and Room
   * validates the result. The returned database is closed by [tearDown].
   */
  private fun migrateV3To4(
    dbName: String,
    seed: (SupportSQLiteDatabase) -> Unit
  ): AppDatabase {
    dbNames += dbName
    createAndSeedV3Database(dbName, seed)

    val migrated =
      Room
        .databaseBuilder(context, AppDatabase::class.java, dbName)
        .allowMainThreadQueries()
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()
    // Force the open, which runs the migrations and Room's schema validation.
    migrated.transactionDao().getAllTransactionsBlocking()
    openedDatabases += migrated
    return migrated
  }

  /** Creates the exact version 3 schema and seeds it through a raw helper. */
  private fun createAndSeedV3Database(
    dbName: String,
    seed: (SupportSQLiteDatabase) -> Unit
  ) {
    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
          .builder(context)
          .name(dbName)
          .callback(
            object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
              override fun onCreate(db: SupportSQLiteDatabase) {
                createV3SchemaTables(db)
                createRoomMetadata(db)
              }

              override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
              ) {
                error("Upgrade not supported in the seed helper ($oldVersion -> $newVersion)")
              }
            }
          ).build()
      )
    val raw = helper.writableDatabase
    seed(raw)
    raw.close()
    helper.close()
  }

  /** The version 3 schema, as MIGRATION_2_3 leaves it. */
  private fun createV3SchemaTables(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS categories (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "name TEXT NOT NULL, key TEXT NOT NULL, " +
        "icon TEXT NOT NULL, color INTEGER NOT NULL, " +
        "type TEXT NOT NULL, isDefault INTEGER NOT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS transactions (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "type TEXT NOT NULL, categoryId INTEGER NOT NULL, " +
        "amount INTEGER NOT NULL, description TEXT NOT NULL, " +
        "personName TEXT, date INTEGER NOT NULL, " +
        "dueDate INTEGER, installmentId INTEGER)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS loans (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "personName TEXT NOT NULL, type TEXT NOT NULL, " +
        "originalAmount INTEGER NOT NULL, " +
        "remainingAmount INTEGER NOT NULL, " +
        "description TEXT NOT NULL, date INTEGER NOT NULL, " +
        "isSettled INTEGER NOT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS installments (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "title TEXT NOT NULL, amount INTEGER NOT NULL, " +
        "dueDate INTEGER NOT NULL, isPaid INTEGER NOT NULL, " +
        "reminderEnabled INTEGER NOT NULL, notes TEXT NOT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS payment_history (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "loanId INTEGER NOT NULL, amount INTEGER NOT NULL, " +
        "date INTEGER NOT NULL, notes TEXT NOT NULL)"
    )
  }

  /** Inserts Room metadata so Room reads the file as a version 3 database. */
  private fun createRoomMetadata(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS room_master_table (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "identity_hash TEXT)"
    )
    db.execSQL(
      "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '')"
    )
  }
}
