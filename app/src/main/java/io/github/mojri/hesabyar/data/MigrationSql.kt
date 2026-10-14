package io.github.mojri.hesabyar.data

import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.mojri.hesabyar.core.MAX_AMOUNT_TOMAN

/**
 * SQL statements and helpers for the historical Room migrations.
 *
 * They live outside `AppDatabase.Companion` so that companion stays small
 * enough for detekt and the statements stay close to each other.
 */
internal object MigrationSql {
  /**
   * Currency conversion factor. Version 1 stored every amount as a Double in
   * Toman. Every later version stores a Long in Rial. 1 Toman = 10 Rials.
   */
  private const val TOMAN_TO_RIAL = 10

  /**
   * Largest legitimate Rial amount. A value at or below this can be a correct
   * Rial amount, so it must never be divided.
   */
  private const val MAX_LEGITIMATE_RIAL = MAX_AMOUNT_TOMAN * 10L

  /**
   * Largest value the faulty revision of MIGRATION_1_2 could produce. Above
   * this, no amount is explainable by either a legitimate entry or the
   * inflation, so the row is corrupt in an unknown way and is left untouched.
   */
  private const val MAX_INFLATED_RIAL = MAX_AMOUNT_TOMAN * 1000L

  /** Inflation factor the faulty revision of MIGRATION_1_2 applied. */
  private const val INFLATION_FACTOR = 100L

  /**
   * Creates `categories`, as MIGRATION_2_3 does. The value is resolved at
   * runtime because `trimIndent()` is a function call, not a constant.
   */
  internal val CREATE_CATEGORIES_V3_SQL =
    """
    CREATE TABLE categories (
      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      name TEXT NOT NULL,
      key TEXT NOT NULL,
      icon TEXT NOT NULL,
      color INTEGER NOT NULL,
      type TEXT NOT NULL,
      isDefault INTEGER NOT NULL
    )
    """.trimIndent()

  /** Creates `transactions` with `categoryId`, as MIGRATION_2_3 does. */
  internal val CREATE_TRANSACTIONS_V3_SQL =
    """
    CREATE TABLE transactions_new (
      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      type TEXT NOT NULL,
      categoryId INTEGER NOT NULL,
      amount INTEGER NOT NULL,
      description TEXT NOT NULL,
      personName TEXT,
      date INTEGER NOT NULL,
      dueDate INTEGER,
      installmentId INTEGER
    )
    """.trimIndent()

  /** Remaps the string `category` column onto `categoryId`, as MIGRATION_2_3 does. */
  internal val COPY_TRANSACTIONS_V3_SQL =
    """
    INSERT INTO transactions_new (id, type, categoryId, amount, description, personName, date, dueDate, installmentId)
    SELECT t.id, t.type,
        CASE t.category
            WHEN 'Food' THEN (SELECT id FROM categories WHERE key = 'Food' LIMIT 1)
            WHEN 'Transportation' THEN (SELECT id FROM categories WHERE key = 'Transportation' LIMIT 1)
            WHEN 'Shopping' THEN (SELECT id FROM categories WHERE key = 'Shopping' LIMIT 1)
            WHEN 'Bills' THEN (SELECT id FROM categories WHERE key = 'Bills' LIMIT 1)
            WHEN 'Installments' THEN (SELECT id FROM categories WHERE key = 'Installments' LIMIT 1)
            WHEN 'Loans' THEN (SELECT id FROM categories WHERE key = 'Loans' LIMIT 1)
            WHEN 'Income' THEN (SELECT id FROM categories WHERE key = 'Income' LIMIT 1)
            ELSE (SELECT id FROM categories WHERE key = 'Other' LIMIT 1)
        END,
        t.amount, t.description, t.personName, t.date, t.dueDate, t.installmentId
    FROM transactions t
    """.trimIndent()

  /** One table that MIGRATION_1_2 rewrites: its version 2 shape and its copy statement. */
  private data class V2Table(
    val name: String,
    val createSql: String,
    val copySql: String
  )

  private val V2_TABLES =
    listOf(
      V2Table(
        name = "transactions",
        createSql =
          """
          CREATE TABLE transactions_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            type TEXT NOT NULL,
            category TEXT NOT NULL,
            amount INTEGER NOT NULL,
            description TEXT NOT NULL,
            personName TEXT,
            date INTEGER NOT NULL,
            dueDate INTEGER,
            installmentId INTEGER
          )
          """.trimIndent(),
        copySql =
          """
          INSERT INTO transactions_new (id, type, category, amount, description, personName, date, dueDate, installmentId)
          SELECT id, type, category, CAST(amount * $TOMAN_TO_RIAL AS INTEGER), description, personName, date, dueDate, installmentId
          FROM transactions
          """.trimIndent()
      ),
      V2Table(
        name = "loans",
        createSql =
          """
          CREATE TABLE loans_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            personName TEXT NOT NULL,
            type TEXT NOT NULL,
            originalAmount INTEGER NOT NULL,
            remainingAmount INTEGER NOT NULL,
            description TEXT NOT NULL,
            date INTEGER NOT NULL,
            isSettled INTEGER NOT NULL
          )
          """.trimIndent(),
        copySql =
          """
          INSERT INTO loans_new (id, personName, type, originalAmount, remainingAmount, description, date, isSettled)
          SELECT id, personName, type, CAST(originalAmount * $TOMAN_TO_RIAL AS INTEGER), CAST(remainingAmount * $TOMAN_TO_RIAL AS INTEGER), description, date, isSettled
          FROM loans
          """.trimIndent()
      ),
      V2Table(
        name = "installments",
        createSql =
          """
          CREATE TABLE installments_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            amount INTEGER NOT NULL,
            dueDate INTEGER NOT NULL,
            isPaid INTEGER NOT NULL,
            reminderEnabled INTEGER NOT NULL,
            notes TEXT NOT NULL
          )
          """.trimIndent(),
        copySql =
          """
          INSERT INTO installments_new (id, title, amount, dueDate, isPaid, reminderEnabled, notes)
          SELECT id, title, CAST(amount * $TOMAN_TO_RIAL AS INTEGER), dueDate, isPaid, reminderEnabled, notes
          FROM installments
          """.trimIndent()
      ),
      V2Table(
        name = "payment_history",
        createSql =
          """
          CREATE TABLE payment_history_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            loanId INTEGER NOT NULL,
            amount INTEGER NOT NULL,
            date INTEGER NOT NULL,
            notes TEXT NOT NULL
          )
          """.trimIndent(),
        copySql =
          """
          INSERT INTO payment_history_new (id, loanId, amount, date, notes)
          SELECT id, loanId, CAST(amount * $TOMAN_TO_RIAL AS INTEGER), date, notes
          FROM payment_history
          """.trimIndent()
      )
    )

  /**
   * Rebuilds the version 2 tables and converts every amount from Toman to Rial.
   */
  fun recreateV2Tables(db: SupportSQLiteDatabase) {
    for (table in V2_TABLES) {
      db.execSQL(table.createSql)
      db.execSQL(table.copySql)
      db.execSQL("DROP TABLE ${table.name}")
      db.execSQL("ALTER TABLE ${table.name}_new RENAME TO ${table.name}")
    }
  }

  /**
   * Undoes the over-inflation that an early revision of MIGRATION_1_2 caused
   * by multiplying by 1000 instead of [TOMAN_TO_RIAL]. Stored values were 100x
   * too large, so dividing by [INFLATION_FACTOR] restores the correct factor.
   *
   * The predicate is bounded on both sides so the correction can only ever
   * touch a value that is impossible as a legitimate amount. Without the upper
   * bound, a single absolute threshold either corrupts a legitimate large
   * amount or leaves an inflated one in place, because both kinds of row are
   * plain integers in the same column and no threshold can separate them. In
   * practice the surviving band is unreachable, so the correction is inert; it
   * exists so a database still on version 3 is never left wrong and no future
   * edit can reintroduce a bare threshold.
   */
  fun undoTomanToRialInflation(
    db: SupportSQLiteDatabase,
    table: String,
    column: String
  ) {
    val predicate = inflatedPredicate(column)
    val affected = countRows(db, table, predicate)
    if (affected == 0L) return
    db.execSQL("UPDATE $table SET $column = $column / $INFLATION_FACTOR WHERE $predicate")
    // android.util.Log is used directly instead of AppLogger because AppLogger
    // collects entries only in debug builds, and this count is the only record
    // of a data correction on a user device.
    android.util.Log.i(
      "AppDatabase",
      "MIGRATION_3_4 corrected $affected row(s) in $table.$column"
    )
  }

  private fun inflatedPredicate(column: String): String =
    "$column > $MAX_LEGITIMATE_RIAL AND $column <= $MAX_INFLATED_RIAL"

  private fun countRows(
    db: SupportSQLiteDatabase,
    table: String,
    predicate: String
  ): Long =
    db
      .compileStatement("SELECT COUNT(*) FROM $table WHERE $predicate")
      .use { it.simpleQueryForLong() }
}
