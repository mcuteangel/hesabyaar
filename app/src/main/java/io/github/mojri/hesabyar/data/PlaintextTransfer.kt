package io.github.mojri.hesabyar.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
  internal data class PlaintextSnapshot(
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
   *
   * The copy, key read, and snapshot read run before the `try`. Their failures
   * must not enter the restore path: the live database is still intact at that
   * point, and restoring a partial working copy over it would be worse than
   * the original error. Only the delete-then-write step needs recovery.
   */
  fun migratePlaintextToEncryptedIfNeeded(context: Context) {
    val dbFile = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempName = "${AppDatabase.LIVE_DATABASE_NAME}$PLAINTEXT_BACKUP_SUFFIX"
    if (!isPlaintextDb(dbFile) && !recoverInterruptedTransfer(context, dbFile, tempName)) {
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
    } catch (e: SQLiteException) {
      restorePlaintextBackupAndRethrow(context, tempName, dbFile, e)
    } catch (e: IllegalStateException) {
      restorePlaintextBackupAndRethrow(context, tempName, dbFile, e)
    } catch (e: IOException) {
      restorePlaintextBackupAndRethrow(context, tempName, dbFile, e)
    }
    // The encrypted database is complete, so the plaintext working copy is no
    // longer the only surviving data and can go.
    deleteDatabaseFiles(context, tempName)
  }

  /**
   * Handles a transfer that was interrupted after the live database was
   * removed. A transfer that fails throws, restores the plaintext backup, and
   * never reaches this point, so a leftover backup with a missing live
   * database means the process stopped between the delete and the encrypted
   * write. Restoring that backup puts the user's data back instead of
   * discarding the only complete copy.
   *
   * A live database that exists but is not plaintext is the completed
   * encrypted database left behind by a failed backup cleanup. Restoring over
   * it would destroy the transferred data, so the stale backup is deleted
   * instead.
   *
   * A backup that holds data but cannot be read as plaintext is kept. It is
   * not deleted, because it may be the only surviving copy of the user's data.
   * Only an absent or empty backup file is deleted, because it holds no data.
   *
   * Returns `true` when a backup was restored and the migration must run again
   * to retry the transfer. Returns `false` when there is nothing to recover.
   */
  @androidx.annotation.VisibleForTesting
  internal fun recoverInterruptedTransfer(
    context: Context,
    dbFile: File,
    tempName: String
  ): Boolean {
    val tempPath = context.getDatabasePath(tempName)
    val liveExists = dbFile.exists()
    val liveIsPlaintext = isPlaintextDb(dbFile)
    // An empty file holds no rows, so deleting it loses nothing.
    val backupHoldsData = tempPath.exists() && tempPath.length() > 0
    // The live database completed the transfer, so the backup is stale.
    // `&&` binds tighter than `||`, so both cleanup cases are covered.
    if (liveExists && !liveIsPlaintext || !backupHoldsData) {
      deleteDatabaseFiles(context, tempName)
      return false
    }
    // Restore only when the live database is missing and the backup is a
    // readable plaintext copy. An unreadable backup is left on disk.
    return !liveExists && isPlaintextDb(tempPath) && restoreBackupForRetry(context, tempName, dbFile)
  }

  /**
   * Puts the plaintext working copy back at the live path after an interrupted
   * transfer, so the caller can retry the conversion.
   */
  private fun restoreBackupForRetry(
    context: Context,
    tempName: String,
    dbFile: File
  ): Boolean {
    restoreTempBackupToLive(context, tempName, dbFile)
    return true
  }

  /** Reads the SQLite header to tell a plaintext database from an encrypted one. */
  fun isPlaintextDb(dbFile: File): Boolean {
    if (!dbFile.exists()) return false
    return try {
      val header = ByteArray(16)
      java.io.FileInputStream(dbFile).use { it.read(header) }
      String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
    } catch (e: IOException) {
      android.util.Log.w(PlaintextTransfer::class.java.simpleName, "Failed to validate SQLite header: ${e.message}")
      false
    }
  }

  /**
   * Copies the live database and its write-ahead log to [tempName]. This copy
   * is the only remaining plaintext data, so it is kept until the encrypted
   * database is complete.
   *
   * Called before any live file is deleted. If this copy throws (e.g. out of
   * storage), the live plaintext database is left untouched and the caller
   * sees the exception without risking data loss.
   *
   * The copy runs at startup before any writer is active, so the live files
   * do not change while they are read. A file-level copy of the main database,
   * its write-ahead log, and the shared-memory index is a consistent snapshot:
   * SQLite recovers the log on the next open, and a copy interrupted by a
   * crash is redone on the next launch because the live database is still in
   * place.
   */
  @androidx.annotation.VisibleForTesting
  internal fun copyPlaintextFilesToBackup(
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
   *
   * Migrations are applied directly to the working copy so the DAOs can read
   * current entity shapes. If the subsequent encrypted write fails, the
   * restored live file is already at the latest schema version, which is
   * compatible with Room's runtime expectations.
   */
  @androidx.annotation.VisibleForTesting
  internal fun readPlaintextSnapshot(
    context: Context,
    tempName: String
  ): PlaintextSnapshot {
    val plaintextDb =
      Room
        .databaseBuilder(context, AppDatabase::class.java, tempName)
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()
    try {
      return PlaintextSnapshot(
        accounts = plaintextDb.accountDao().getAllAccountsBlocking(),
        categories = plaintextDb.categoryDao().getAllCategoriesBlocking(),
        transactions = plaintextDb.transactionDao().getAllTransactionsBlocking(),
        loans = plaintextDb.loanDao().getAllLoansBlocking(),
        bankLoans = plaintextDb.bankLoanDao().getAllBankLoansBlocking(),
        installments = plaintextDb.installmentDao().getAllInstallmentsBlocking(),
        payments = plaintextDb.paymentHistoryDao().getAllPaymentHistoriesBlocking(),
        persons = plaintextDb.personDao().getAllPersonsIncludingArchivedBlocking()
      )
    } finally {
      plaintextDb.close()
    }
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

    try {
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
    } finally {
      encryptedDb.close()
    }
  }

  /**
   * Removes the database [name] together with its write-ahead log and index. A
   * delete that fails is logged, because a stale file left at a path the next
   * step writes to would otherwise fail much later without a clear cause.
   */
  @androidx.annotation.VisibleForTesting
  internal fun deleteDatabaseFiles(
    context: Context,
    name: String
  ) {
    listOf(name, "$name-wal", "$name-shm").forEach { fileName ->
      val file = context.getDatabasePath(fileName)
      if (file.exists() && !file.delete()) {
        android.util.Log.w(
          PlaintextTransfer::class.java.simpleName,
          "Failed to delete $fileName at ${file.path}"
        )
      }
    }
  }

  /**
   * Puts the plaintext working copy back and rethrows [cause]. Both recoverable
   * failures end here, so the restore body is written once.
   */
  @androidx.annotation.VisibleForTesting
  internal fun restorePlaintextBackupAndRethrow(
    context: Context,
    tempName: String,
    dbFile: File,
    cause: Throwable
  ): Nothing {
    runCatching { restoreTempBackupToLive(context, tempName, dbFile) }
      .exceptionOrNull()
      ?.let { cause.addSuppressed(it) }
    throw cause
  }

  /** Puts the plaintext working copy back at the live path after a failure. */
  private fun restoreTempBackupToLive(
    context: Context,
    tempName: String,
    dbFile: File
  ) {
    val tempPath = context.getDatabasePath(tempName)
    copyThenReplace(tempPath, dbFile)
    listOf("-wal", "-shm").forEach { suffix ->
      val src = File(tempPath.parentFile, tempPath.name + suffix)
      val dst = File(dbFile.parentFile, dbFile.name + suffix)
      if (src.exists()) copyThenReplace(src, dst)
    }
  }

  /**
   * Copies [source] into a staging file beside [target] and then renames that
   * file over [target]. A rename inside one directory either lands the complete
   * file or leaves the previous one in place, so a copy that stops partway
   * cannot truncate the live database.
   */
  @androidx.annotation.VisibleForTesting
  internal fun copyThenReplace(
    source: File,
    target: File
  ) {
    val staging = File(target.parentFile, "${target.name}.restoring")
    try {
      source.copyTo(staging, overwrite = true)
      try {
        Files.move(
          staging.toPath(),
          target.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE
        )
      } catch (e: AtomicMoveNotSupportedException) {
        android.util.Log.d(
          PlaintextTransfer::class.java.simpleName,
          "Atomic move unsupported; falling back to non-atomic replace: ${e.message}"
        )
        Files.move(
          staging.toPath(),
          target.toPath(),
          StandardCopyOption.REPLACE_EXISTING
        )
      }
    } finally {
      // After a successful move the staging file no longer exists, so this
      // only removes the incomplete copy left behind by a failed one.
      if (staging.exists()) {
        staging.delete()
      }
    }
  }
}
