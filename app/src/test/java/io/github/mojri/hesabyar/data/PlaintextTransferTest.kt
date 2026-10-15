package io.github.mojri.hesabyar.data

import android.content.Context
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
}
