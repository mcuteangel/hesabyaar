package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Unit tests for [PlaintextTransfer] helper methods.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PlaintextTransferTest {
  private lateinit var context: Context
  private val testFiles = mutableListOf<File>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @After
  fun tearDown() {
    testFiles.forEach { it.delete() }
    testFiles.clear()
  }

  @Test
  fun isPlaintextDbReturnsFalseForNonExistentFile() {
    val file = File(context.cacheDir, "non_existent.db")
    assertFalse(PlaintextTransfer.isPlaintextDb(file))
  }

  @Test
  fun isPlaintextDbReturnsFalseForEmptyFile() {
    val file = File(context.cacheDir, "empty.db").apply { createNewFile() }
    testFiles += file
    assertFalse(PlaintextTransfer.isPlaintextDb(file))
  }

  @Test
  fun isPlaintextDbReturnsFalseForEncryptedOrArbitraryData() {
    val file = File(context.cacheDir, "encrypted.db")
    testFiles += file
    FileOutputStream(file).use { out ->
      out.write(ByteArray(32) { 0x55.toByte() })
    }
    assertFalse(PlaintextTransfer.isPlaintextDb(file))
  }

  @Test
  fun isPlaintextDbReturnsTrueForValidSqliteHeader() {
    val file = File(context.cacheDir, "valid_sqlite.db")
    testFiles += file
    FileOutputStream(file).use { out ->
      out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
      out.write(ByteArray(16))
    }
    assertTrue(PlaintextTransfer.isPlaintextDb(file))
  }

  @Test
  fun migratePlaintextToEncryptedCleansUpBackupWhenDbNotPlaintext() {
    val tempBackup = context.getDatabasePath("${AppDatabase.LIVE_DATABASE_NAME}_plaintext_backup")
    tempBackup.parentFile?.mkdirs()
    tempBackup.createNewFile()
    testFiles += tempBackup

    // Ensure the live DB file does not exist or is not plaintext
    context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME).delete()

    PlaintextTransfer.migratePlaintextToEncryptedIfNeeded(context)

    assertFalse("Leftover backup file should be cleaned up", tempBackup.exists())
  }

  @Test
  fun recoverInterruptedTransferRestoresBackupWhenLiveDbMissing() {
    val liveDb = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempName = "${AppDatabase.LIVE_DATABASE_NAME}_plaintext_backup"
    val tempBackup = context.getDatabasePath(tempName)

    testFiles += liveDb
    testFiles += tempBackup

    // Live DB is missing
    liveDb.delete()

    // Backup is a valid SQLite DB
    tempBackup.parentFile?.mkdirs()
    FileOutputStream(tempBackup).use { out ->
      out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
      out.write(ByteArray(16))
    }

    val recovered = PlaintextTransfer.recoverInterruptedTransfer(context, liveDb, tempName)

    assertTrue("Should indicate recovery happened", recovered)
    assertTrue("Live DB should have been restored", liveDb.exists())
    assertTrue("Restored live DB should be plaintext SQLite", PlaintextTransfer.isPlaintextDb(liveDb))
    val staging = File(liveDb.parentFile, "${liveDb.name}.restoring")
    assertFalse("Staging file must not remain after a successful restore", staging.exists())
  }

  @Test
  fun recoverInterruptedTransferDeletesBackupWhenLiveDbAlreadyEncrypted() {
    val liveDb = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempName = "${AppDatabase.LIVE_DATABASE_NAME}_plaintext_backup"
    val tempBackup = context.getDatabasePath(tempName)

    testFiles += liveDb
    testFiles += tempBackup

    // Live DB exists and is encrypted (arbitrary non-SQLite header)
    liveDb.parentFile?.mkdirs()
    FileOutputStream(liveDb).use { out ->
      out.write(ByteArray(32) { 0x55.toByte() })
    }

    // Leftover backup from a completed migration where cleanup failed
    tempBackup.parentFile?.mkdirs()
    FileOutputStream(tempBackup).use { out ->
      out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
      out.write(ByteArray(16))
    }

    val recovered = PlaintextTransfer.recoverInterruptedTransfer(context, liveDb, tempName)

    assertFalse("Should not attempt recovery when live DB is already encrypted", recovered)
    assertFalse("Stale plaintext backup should be deleted", tempBackup.exists())
    assertTrue("Live encrypted DB must not be deleted", liveDb.exists())
    assertFalse("Live DB must not be overwritten with plaintext", PlaintextTransfer.isPlaintextDb(liveDb))
  }

  @Test
  fun recoverInterruptedTransferPreservesUnreadableBackupWhenLiveDbMissing() {
    val liveDb = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempName = "${AppDatabase.LIVE_DATABASE_NAME}_plaintext_backup"
    val tempBackup = context.getDatabasePath(tempName)

    testFiles += liveDb
    testFiles += tempBackup

    // Live DB is missing
    liveDb.delete()

    // Backup holds data but is corrupted/unreadable (arbitrary bytes, not SQLite)
    tempBackup.parentFile?.mkdirs()
    FileOutputStream(tempBackup).use { out ->
      out.write(ByteArray(32) { 0xAA.toByte() })
    }

    val recovered = PlaintextTransfer.recoverInterruptedTransfer(context, liveDb, tempName)

    assertFalse("Cannot recover from unreadable backup", recovered)
    assertTrue("Unreadable backup must be preserved on disk for manual recovery", tempBackup.exists())
    assertFalse("Live DB must remain absent", liveDb.exists())
  }

  @Test
  fun copyPlaintextFilesToBackupCopiesMainAndWalAndShm() {
    val liveDb = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val liveWal = File(liveDb.parentFile, "${liveDb.name}-wal")
    val liveShm = File(liveDb.parentFile, "${liveDb.name}-shm")
    val tempName = "copy_test_backup"
    val tempBackup = context.getDatabasePath(tempName)
    val tempWal = File(tempBackup.parentFile, "$tempName-wal")
    val tempShm = File(tempBackup.parentFile, "$tempName-shm")

    testFiles += listOf(liveDb, liveWal, liveShm, tempBackup, tempWal, tempShm)

    liveDb.parentFile?.mkdirs()
    liveDb.writeText("main-db")
    liveWal.writeText("wal-data")
    liveShm.writeText("shm-data")

    PlaintextTransfer.copyPlaintextFilesToBackup(context, tempName)

    assertTrue("Main backup file should exist", tempBackup.exists())
    assertTrue("WAL backup file should exist", tempWal.exists())
    assertTrue("SHM backup file should exist", tempShm.exists())
    org.junit.Assert.assertEquals("main-db", tempBackup.readText())
    org.junit.Assert.assertEquals("wal-data", tempWal.readText())
    org.junit.Assert.assertEquals("shm-data", tempShm.readText())
  }

  @Test
  fun copyPlaintextFilesToBackupDeletesStaleBackupSidecarsWhenLiveHasNone() {
    val liveDb = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val liveWal = File(liveDb.parentFile, "${liveDb.name}-wal")
    val liveShm = File(liveDb.parentFile, "${liveDb.name}-shm")
    val tempName = "copy_stale_sidecars_backup"
    val tempBackup = context.getDatabasePath(tempName)
    val tempWal = File(tempBackup.parentFile, "$tempName-wal")
    val tempShm = File(tempBackup.parentFile, "$tempName-shm")

    testFiles += listOf(liveDb, liveWal, liveShm, tempBackup, tempWal, tempShm)

    // Live DB has no WAL or SHM; an earlier backup left stale sidecars behind.
    liveDb.parentFile?.mkdirs()
    liveDb.writeText("fresh-main-db")
    tempBackup.writeText("old-backup-main")
    tempWal.writeText("stale-wal-data")
    tempShm.writeText("stale-shm-data")

    PlaintextTransfer.copyPlaintextFilesToBackup(context, tempName)

    org.junit.Assert.assertEquals("fresh-main-db", tempBackup.readText())
    assertFalse("Stale backup WAL must be deleted so SQLite cannot replay it", tempWal.exists())
    assertFalse("Stale backup SHM must be deleted so SQLite cannot replay it", tempShm.exists())
  }

  @Test
  fun deleteDatabaseFilesDeletesMainAndWalAndShm() {
    val name = "delete_target_db"
    val main = context.getDatabasePath(name)
    val wal = context.getDatabasePath("$name-wal")
    val shm = context.getDatabasePath("$name-shm")

    testFiles += listOf(main, wal, shm)

    main.parentFile?.mkdirs()
    main.writeText("main")
    wal.writeText("wal")
    shm.writeText("shm")

    PlaintextTransfer.deleteDatabaseFiles(context, name)

    assertFalse("Main DB file should be deleted", main.exists())
    assertFalse("WAL file should be deleted", wal.exists())
    assertFalse("SHM file should be deleted", shm.exists())
  }

  @Test
  fun recoverInterruptedTransferDeletesEmptyBackup() {
    val liveDb = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempName = "${AppDatabase.LIVE_DATABASE_NAME}_empty_backup"
    val tempBackup = context.getDatabasePath(tempName)

    testFiles += liveDb
    testFiles += tempBackup

    liveDb.delete()
    tempBackup.parentFile?.mkdirs()
    tempBackup.createNewFile() // 0-byte file

    val recovered = PlaintextTransfer.recoverInterruptedTransfer(context, liveDb, tempName)

    assertFalse("Cannot recover from empty backup", recovered)
    assertFalse("Empty backup should be cleaned up", tempBackup.exists())
  }

  @Test
  fun copyThenReplaceAtomicallyReplacesTargetFile() {
    val src = File(context.cacheDir, "test_copy_src.db")
    val dst = File(context.cacheDir, "test_copy_dst.db")
    testFiles += listOf(src, dst)

    src.writeText("new-content-from-src")
    dst.writeText("old-content-in-dst")

    PlaintextTransfer.copyThenReplace(src, dst)

    org.junit.Assert.assertEquals("new-content-from-src", dst.readText())
    val staging = File(dst.parentFile, "${dst.name}.restoring")
    assertFalse("Staging file must be cleaned up after replacement", staging.exists())
  }

  @Test
  fun recoverInterruptedTransferReturnsFalseWhenLiveDbIsAlreadyPlaintext() {
    val liveDb = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempName = "${AppDatabase.LIVE_DATABASE_NAME}_plaintext_backup"
    val tempBackup = context.getDatabasePath(tempName)

    testFiles += liveDb
    testFiles += tempBackup

    liveDb.parentFile?.mkdirs()
    FileOutputStream(liveDb).use { out ->
      out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
      out.write(ByteArray(16))
    }

    tempBackup.parentFile?.mkdirs()
    FileOutputStream(tempBackup).use { out ->
      out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
      out.write(ByteArray(16))
    }

    val recovered = PlaintextTransfer.recoverInterruptedTransfer(context, liveDb, tempName)

    assertFalse("Should return false when live DB is already plaintext", recovered)
    assertTrue("Live DB should remain", liveDb.exists())
    assertTrue("Backup should be preserved", tempBackup.exists())
  }

  @Test
  fun readPlaintextSnapshotReadsAllTablesThroughRoom() {
    val tempName = "read_snapshot_test"
    val dbFile = context.getDatabasePath(tempName)
    testFiles += dbFile

    val db =
      Room
        .databaseBuilder(context, AppDatabase::class.java, tempName)
        .allowMainThreadQueries()
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()
    try {
      db.accountDao().insertAllBlocking(
        listOf(
          AccountEntity(
            id = 0,
            name = "تست",
            type = AccountType.BANK,
            bankName = "بانک تست",
            cardNumber = null,
            accountNumber = null,
            iban = null,
            initialBalance = 100L,
            color = 0,
            icon = null,
            isArchived = false,
            displayOrder = 0
          )
        )
      )
    } finally {
      db.close()
    }

    val snapshot =
      java.util.concurrent.Executors
        .newSingleThreadExecutor()
        .submit<PlaintextTransfer.PlaintextSnapshot> {
          PlaintextTransfer.readPlaintextSnapshot(context, tempName)
        }.get(5, java.util.concurrent.TimeUnit.SECONDS)

    assertTrue("readPlaintextSnapshot must read the account row", snapshot.accounts.isNotEmpty())
    val account = snapshot.accounts.single()
    org.junit.Assert.assertEquals("تست", account.name)
    org.junit.Assert.assertEquals(AccountType.BANK, account.type)
    org.junit.Assert.assertEquals(100L, account.initialBalance)
  }

  @Test
  fun restorePlaintextBackupAndRethrowPutsBackupBackAndRethrowsCause() {
    val tempName = "restore_rethrow_test"
    val dbFile = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempPath = context.getDatabasePath(tempName)
    testFiles += dbFile
    testFiles += tempPath

    tempPath.parentFile?.mkdirs()
    tempPath.writeText("restored backup content")

    val cause = RuntimeException("original delete failed")

    val thrown =
      try {
        PlaintextTransfer.restorePlaintextBackupAndRethrow(context, tempName, dbFile, cause)
        null
      } catch (e: RuntimeException) {
        e
      }

    assertTrue("Original cause must be thrown", thrown === cause)
    org.junit.Assert.assertEquals("restored backup content", dbFile.readText())
  }

  @Test
  fun restorePlaintextBackupAndRethrowRemovesStaleLiveWalAndShm() {
    val tempName = "restore_stale_wal_test"
    val dbFile = context.getDatabasePath(AppDatabase.LIVE_DATABASE_NAME)
    val tempPath = context.getDatabasePath(tempName)
    val liveWal = File(dbFile.parentFile, "${dbFile.name}-wal")
    val liveShm = File(dbFile.parentFile, "${dbFile.name}-shm")
    testFiles += listOf(dbFile, tempPath, liveWal, liveShm)

    dbFile.parentFile?.mkdirs()
    // Stale WAL and SHM from a partial encrypted write at the live path.
    liveWal.writeText("stale-encrypted-wal")
    liveShm.writeText("stale-encrypted-shm")
    // Plaintext backup without any WAL or SHM companion files.
    tempPath.writeText("plaintext backup main")

    val cause = RuntimeException("write failed")
    val thrown =
      try {
        PlaintextTransfer.restorePlaintextBackupAndRethrow(context, tempName, dbFile, cause)
        null
      } catch (e: RuntimeException) {
        e
      }

    assertTrue("Original cause must be thrown", thrown === cause)
    org.junit.Assert.assertEquals("plaintext backup main", dbFile.readText())
    assertFalse("Stale live WAL must be deleted before restore", liveWal.exists())
    assertFalse("Stale live SHM must be deleted before restore", liveShm.exists())
  }
}
