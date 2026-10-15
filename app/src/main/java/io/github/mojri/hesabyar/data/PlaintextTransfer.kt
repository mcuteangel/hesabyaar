package io.github.mojri.hesabyar.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.IOException

/**
 * Converts the plaintext database that an old install leaves behind into the
 * encrypted one.
 *
 * It lives outside `AppDatabase.Companion` so that companion stays small enough
 * for detekt. Every helper here is reachable only through
 * [migratePlaintextToEncryptedIfNeeded].
 */
internal object PlaintextTransfer {
  /** Suffix that marks the working copy made from the plaintext database. */
  private const val PLAINTEXT_BACKUP_SUFFIX = "_plaintext_backup"

  /** Every table read out of a plaintext database, ready to be rewritten. */
  private data class PlaintextSnapshot(
    val accounts: List<AccountEntity>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val loans: List<Loan>,
    val bankLoans: List<BankLoan>,
    val installments: List<Installment>,
    val payments: List<PaymentHistory>,
    val persons: List<Person>
  )

  /**
   * Converts the plaintext database to the encrypted one. A working copy is
   * made and the rows are read before any live file is removed, so a failure in
   * those steps leaves the user's data exactly as it was.
   */
  fun migratePlaintextToEncryptedIfNeeded(context: Context) {
    val dbFile = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempName = "${AppDatabase.LIVE_DATABASE_NAME}$PLAINTEXT_BACKUP_SUFFIX"
    if (!isPlaintextDb(dbFile)) {
      deleteDatabaseFiles(context, tempName)
      return
    }

    copyPlaintextFilesToBackup(context, tempName)
    // The key is read before any live file is removed, so a key failure leaves
    // the plaintext database untouched.
    val passphrase = DatabaseKeyManager.getOrCreateKey(context)
    val snapshot = readPlaintextSnapshot(context, tempName)

    try {
      deleteDatabaseFiles(context, AppDatabase.LIVE_DATABASE_NAME)
      writeEncryptedDatabase(context, passphrase, snapshot)
      deleteDatabaseFiles(context, tempName)
    } catch (e: SQLiteException) {
      restorePlaintextBackupAndRethrow(context, tempName, dbFile, e)
    } catch (e: IllegalStateException) {
      restorePlaintextBackupAndRethrow(context, tempName, dbFile, e)
    } catch (e: IOException) {
      restorePlaintextBackupAndRethrow(context, tempName, dbFile, e)
    }
  }

  /** Reads the SQLite header to tell a plaintext database from an encrypted one. */
  fun isPlaintextDb(dbFile: File): Boolean {
    if (!dbFile.exists()) return false
    return try {
      val header = ByteArray(16)
      java.io.FileInputStream(dbFile).use { it.read(header) }
      String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
    } catch (e: IOException) {
      println("Failed to validate SQLite header for ${dbFile.path}: ${e.message}")
      false
    }
  }

  /**
   * Copies the live database and its write-ahead log to [tempName]. This copy
   * is the only remaining plaintext data, so it is kept until the encrypted
   * database is complete.
   */
  private fun copyPlaintextFilesToBackup(
    context: Context,
    tempName: String
  ) {
    val livePath = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempPath = context.getDatabasePath(tempName)
    livePath.copyTo(tempPath, overwrite = true)
    listOf("-wal", "-shm").forEach { suffix ->
      val src = File(livePath.parentFile, livePath.name + suffix)
      val dst = File(tempPath.parentFile, tempPath.name + suffix)
      if (src.exists()) src.copyTo(dst, overwrite = true)
    }
  }

  /**
   * Runs the migration chain on the working copy and reads every table out of
   * it. Reading through the Room DAOs applies the type converters here, so a
   * malformed row surfaces before the live database is touched.
   */
  private fun readPlaintextSnapshot(
    context: Context,
    tempName: String
  ): PlaintextSnapshot {
    val plaintextDb =
      Room
        .databaseBuilder(context, AppDatabase::class.java, tempName)
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()
    val snapshot =
      PlaintextSnapshot(
        accounts = plaintextDb.accountDao().getAllAccountsBlocking(),
        categories = plaintextDb.categoryDao().getAllCategoriesBlocking(),
        transactions = plaintextDb.transactionDao().getAllTransactionsBlocking(),
        loans = plaintextDb.loanDao().getAllLoansBlocking(),
        bankLoans = plaintextDb.bankLoanDao().getAllBankLoansBlocking(),
        installments = plaintextDb.installmentDao().getAllInstallmentsBlocking(),
        payments = plaintextDb.paymentHistoryDao().getAllPaymentHistoriesBlocking(),
        persons = plaintextDb.personDao().getAllPersonsIncludingArchivedBlocking()
      )
    plaintextDb.close()
    return snapshot
  }

  /**
   * Creates the encrypted database at the live path and writes [snapshot] into
   * it. [passphrase] is supplied by the caller so the key is read before any
   * live file is removed.
   */
  private fun writeEncryptedDatabase(
    context: Context,
    passphrase: ByteArray,
    snapshot: PlaintextSnapshot
  ) {
    val factory = SupportOpenHelperFactory(passphrase)
    val encryptedDb =
      Room
        .databaseBuilder(context, AppDatabase::class.java, AppDatabase.LIVE_DATABASE_NAME)
        .openHelperFactory(factory)
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()

    AppDatabase.transferPlaintextData(
      accounts = snapshot.accounts,
      categories = snapshot.categories,
      transactions = snapshot.transactions,
      loans = snapshot.loans,
      bankLoans = snapshot.bankLoans,
      installments = snapshot.installments,
      payments = snapshot.payments,
      persons = snapshot.persons,
      encryptedDb = encryptedDb
    )

    encryptedDb.close()
  }

  /** Removes the database [name] together with its write-ahead log and index. */
  private fun deleteDatabaseFiles(
    context: Context,
    name: String
  ) {
    context.getDatabasePath(name).delete()
    context.getDatabasePath("$name-wal").delete()
    context.getDatabasePath("$name-shm").delete()
  }

  /**
   * Puts the plaintext working copy back and rethrows [cause]. Both recoverable
   * failures end here, so the restore body is written once.
   */
  private fun restorePlaintextBackupAndRethrow(
    context: Context,
    tempName: String,
    dbFile: File,
    cause: Throwable
  ): Nothing {
    restoreTempBackupToLive(context, tempName, dbFile)
    throw cause
  }

  /** Puts the plaintext working copy back at the live path after a failure. */
  private fun restoreTempBackupToLive(
    context: Context,
    tempName: String,
    dbFile: File
  ) {
    val tempPath = context.getDatabasePath(tempName)
    tempPath.copyTo(dbFile, overwrite = true)
    listOf("-wal", "-shm").forEach { suffix ->
      val src = File(tempPath.parentFile, tempPath.name + suffix)
      val dst = File(dbFile.parentFile, dbFile.name + suffix)
      if (src.exists()) src.copyTo(dst, overwrite = true)
    }
  }
}
