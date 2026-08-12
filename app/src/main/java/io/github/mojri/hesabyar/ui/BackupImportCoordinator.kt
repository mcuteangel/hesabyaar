package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupSettings
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.RestoreMode
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

/**
 * Owns the import/restore half of the backup UI flow: reading and validating
 * the backup file, the import passphrase dialog, and executing the restore.
 * Shared operation/dialog state is passed in by [BackupViewModel], which stays
 * the single owner of cross-flow state.
 */
class BackupImportCoordinator(
  private val application: Context,
  private val manageBackupUseCase: ManageBackupUseCase,
  private val scope: CoroutineScope,
  private val operationState: MutableState<BackupOperationState>,
  private val isCryptoInProgress: MutableState<Boolean>
) {
  @VisibleForTesting
  internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

  /**
   * Dispatcher for CPU-intensive PBKDF2 key derivation (600k iterations) —
   * CPU-bound work belongs on [Dispatchers.Default], not the I/O pool.
   */
  @VisibleForTesting
  internal var cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default

  val pendingRestoreBackup = mutableStateOf<BackupPayload?>(null)
  val selectedRestoreMode = mutableStateOf(RestoreMode.REPLACE)

  /** Current passphrase dialog state. */
  val passphraseDialogState = mutableStateOf<PassphraseDialogState>(PassphraseDialogState.Hidden)

  @VisibleForTesting
  internal var pendingImportRawJson: String? = null

  @VisibleForTesting
  internal var pendingImportSalt: String? = null

  /**
   * Reads the backup file, parses it, and checks for encrypted fields.
   * If encrypted, stages the raw JSON and shows the import passphrase dialog.
   * If not encrypted, proceeds directly to validation.
   */
  fun validateAndStageImport(inputStream: InputStream) {
    scope.launch {
      try {
        val text = withContext(ioDispatcher) { inputStream.bufferedReader().use { it.readText() } }
        val rootJson =
          try {
            JSONObject(text)
          } catch (_: JSONException) {
            null
          }

        if (rootJson != null && ManageBackupUseCase.isEncryptedBackup(rootJson)) {
          // Encrypted backup — pause and ask for passphrase
          val salt = ManageBackupUseCase.getEncryptionSalt(rootJson)
          if (salt == null) {
            // Encryption marker present but no PBKDF2 salt (foreign or hand-edited
            // backup): reject at detection. Opening the dialog with an empty-salt
            // fallback would be a dead-end — decryptAndStageImport silently hides
            // the dialog when pendingImportSalt is null, with no error surfaced.
            pendingImportRawJson = null
            pendingImportSalt = null
            operationState.value =
              BackupOperationState.Error(
                application.getString(R.string.error_backup_encryption_incomplete)
              )
            return@launch
          }
          pendingImportRawJson = text
          pendingImportSalt = salt
          passphraseDialogState.value = PassphraseDialogState.ImportPassphrase(salt)
          return@launch
        }

        // Plaintext backup — proceed directly
        val backup = parseBackupOrReportError(text) ?: return@launch
        stageValidatedBackup(backup)
      } catch (e: IOException) {
        operationState.value =
          BackupOperationState.Error(
            application.getString(
              R.string.error_reading_backup_file,
              e.localizedMessage ?: "خطای ناشناخته"
            )
          )
      } catch (e: JSONException) {
        operationState.value =
          BackupOperationState.Error(
            application.getString(
              R.string.error_parsing_backup_file,
              e.localizedMessage ?: "خطای ناشناخته"
            )
          )
      }
    }
  }

  /**
   * Called from the import passphrase dialog.
   * Derives the key and decrypts the backup on [cryptoDispatcher].
   * Uses a single generic error message since wrong-passphrase and tampered-file
   * are cryptographically indistinguishable.
   */
  fun decryptAndStageImport(passphrase: String) {
    val rawJson = pendingImportRawJson
    val salt = pendingImportSalt
    if (rawJson == null || salt == null) {
      passphraseDialogState.value = PassphraseDialogState.Hidden
      return
    }

    scope.launch {
      isCryptoInProgress.value = true
      try {
        val parsed =
          parseBackupOrReportError(rawJson) ?: run {
            // Structural parse error — clear staged data and dismiss dialog
            pendingImportRawJson = null
            pendingImportSalt = null
            passphraseDialogState.value = PassphraseDialogState.Hidden
            return@launch
          }
        val rootJson = JSONObject(rawJson)
        val decrypted =
          withContext(cryptoDispatcher) {
            manageBackupUseCase.decryptBackupWithPassphrase(parsed, rootJson, passphrase)
          }
        // Success — clear staged data and dismiss dialog
        pendingImportRawJson = null
        pendingImportSalt = null
        passphraseDialogState.value = PassphraseDialogState.Hidden
        stageValidatedBackup(decrypted)
      } catch (e: CancellationException) {
        // Coroutine cancelled (e.g. ViewModel cleared) — propagate, do not
        // surface it as a wrong-passphrase/corrupt-backup failure.
        throw e
      } catch (_: Exception) {
        // Wrong passphrase or tampered ciphertext — keep staged data for retry
        passphraseDialogState.value =
          PassphraseDialogState.ImportPassphrase(
            salt,
            application.getString(R.string.passphrase_wrong_or_corrupt)
          )
        operationState.value =
          BackupOperationState.Error(application.getString(R.string.passphrase_wrong_or_corrupt))
      } finally {
        isCryptoInProgress.value = false
      }
    }
  }

  /**
   * Cancel the passphrase dialog and clean up any staged import data.
   */
  fun cancelPassphraseDialog() {
    passphraseDialogState.value = PassphraseDialogState.Hidden
    pendingImportRawJson = null
    pendingImportSalt = null
  }

  private suspend fun stageValidatedBackup(backup: BackupPayload) {
    val result = manageBackupUseCase.validateBackup(backup)
    when (result) {
      is BackupValidationResult.Invalid -> {
        operationState.value = BackupOperationState.ValidationFailed(result.errors)
      }
      is BackupValidationResult.Valid -> {
        pendingRestoreBackup.value = backup
      }
    }
  }

  // --- Restore execution ---

  fun executeRestore() {
    val backup = pendingRestoreBackup.value ?: return
    val mode = selectedRestoreMode.value

    scope.launch {
      operationState.value = BackupOperationState.Importing
      try {
        manageBackupUseCase.executeRestore(backup, mode)
        applySettings(backup.settings)
        operationState.value =
          BackupOperationState.ImportSuccess(
            when (mode) {
              RestoreMode.REPLACE -> "بازیابی کامل با موفقیت انجام شد. ${manageBackupUseCase.buildBackupSummary(
                backup
              )}"
              RestoreMode.MERGE -> "ادغام پشتیبان با موفقیت انجام شد."
              else -> "عملیات با موفقیت انجام شد."
            }
          )
        pendingRestoreBackup.value = null
      } catch (e: IOException) {
        operationState.value =
          BackupOperationState.Error(
            "خطا در دسترسی به فایل پشتیبان: ${e.localizedMessage ?: "خطای ورودی/خروجی"}"
          )
      } catch (e: SecurityException) {
        operationState.value =
          BackupOperationState.Error(
            "دسترسی به فایل پشتیبان غیرمجاز است: ${e.localizedMessage ?: ""}"
          )
      } catch (e: IllegalArgumentException) {
        operationState.value =
          BackupOperationState.Error(
            "تنظیمات پشتیبان نامعتبر است: ${e.localizedMessage ?: ""}"
          )
      }
    }
  }

  private fun applySettings(settings: BackupSettings) {
    application
      .getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)
      .edit()
      .putBoolean("dark_mode", settings.darkMode)
      .apply()
  }

  private fun reportInvalidBackupParse() {
    pendingRestoreBackup.value = null
    operationState.value =
      BackupOperationState.Error(
        application.getString(R.string.error_invalid_backup_structure)
      )
  }

  private suspend fun parseBackupOrReportError(text: String): BackupPayload? {
    val backup = manageBackupUseCase.parseBackupJson(text)
    if (backup == null) reportInvalidBackupParse()
    return backup
  }

  fun cancelPendingRestore() {
    pendingRestoreBackup.value = null
  }

  /**
   * Legacy import path (always REPLACE mode, no passphrase support).
   * Kept for backward compatibility; new code should use
   * [validateAndStageImport] + [executeRestore].
   */
  fun importBackupFromFile(inputStream: InputStream) {
    scope.launch {
      operationState.value = BackupOperationState.Importing
      try {
        val text = withContext(ioDispatcher) { inputStream.bufferedReader().use { it.readText() } }
        val backup = parseBackupOrReportError(text) ?: return@launch
        manageBackupUseCase.importBackupFromFile(backup)
        operationState.value = BackupOperationState.ImportSuccess("وارد کردن پشتیبان با موفقیت انجام شد.")
      } catch (e: IOException) {
        operationState.value =
          BackupOperationState.Error(
            "خطا در خواندن فایل پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
          )
      } catch (e: JSONException) {
        operationState.value =
          BackupOperationState.Error(
            "خطا در تجزیه فایل پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
          )
      } catch (e: CancellationException) {
        throw e
      } catch (e: IllegalStateException) {
        operationState.value =
          BackupOperationState.Error(
            "خطا در وارد کردن پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
          )
      }
    }
  }
}
