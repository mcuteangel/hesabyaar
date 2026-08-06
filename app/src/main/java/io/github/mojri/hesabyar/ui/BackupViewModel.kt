package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupSettings
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.RestoreMode
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** UI state for the passphrase dialog shown during export or import. */
sealed class PassphraseDialogState {
  data object Hidden : PassphraseDialogState()

  /** Export dialog: user can enter a passphrase or skip (plaintext export). */
  data object ExportPassphrase : PassphraseDialogState()

  /** Import dialog: backup contains encrypted fields, user must enter passphrase. */
  data class ImportPassphrase(
    val salt: String,
    val errorMessage: String? = null
  ) : PassphraseDialogState()
}

@HiltViewModel
class BackupViewModel
  @Inject
  constructor(
    @ApplicationContext private val application: Context,
    private val manageBackupUseCase: ManageBackupUseCase
  ) : ViewModel() {
    @VisibleForTesting
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Dispatcher for CPU-intensive PBKDF2 key derivation (600k iterations). */
    @VisibleForTesting
    internal var cryptoDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val sharedPrefs = application.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)

    val operationState = mutableStateOf<BackupOperationState>(BackupOperationState.Idle)
    val pendingRestoreBackup = mutableStateOf<BackupPayload?>(null)
    val selectedRestoreMode = mutableStateOf(RestoreMode.REPLACE)

    /** Current passphrase dialog state. */
    val passphraseDialogState = mutableStateOf<PassphraseDialogState>(PassphraseDialogState.Hidden)

    /** True while PBKDF2 derivation + encrypt/decrypt is running (shows loading spinner). */
    val isCryptoInProgress = mutableStateOf(false)

    // --- Export passphrase flow ---

    private var pendingExportJsonText: String? = null

    /**
     * One-shot signal raised once staging completes so the screen launches the
     * SAF save-location picker from a LaunchedEffect instead of inline in the
     * click handler. The old inline launch raced the async PBKDF2/encryption —
     * the picker callback could run while [pendingExportJsonText] was still null,
     * and the picker opened even when staging would fail.
     */
    val exportPickerLaunchRequest = mutableStateOf(false)

    /**
     * Called from SettingsScreen after export button tap.
     * Shows the export passphrase dialog.
     */
    fun requestExportPassphraseDialog() {
      passphraseDialogState.value = PassphraseDialogState.ExportPassphrase
    }

    /**
     * Called from the export passphrase dialog with the user-entered passphrase.
     * Runs PBKDF2 derivation + encryption on [cryptoDispatcher], stages the JSON,
     * clears the dialog, and raises [exportPickerLaunchRequest] so the screen
     * launches the picker only after staging fully succeeded.
     */
    @Suppress("TooGenericExceptionCaught") // Safety net: PBKDF2/Cipher can throw unchecked RuntimeException
    fun exportWithPassphrase(passphrase: String) {
      viewModelScope.launch {
        isCryptoInProgress.value = true
        exportPickerLaunchRequest.value = false
        passphraseDialogState.value = PassphraseDialogState.Hidden
        try {
          val rootJson =
            withContext(cryptoDispatcher) {
              manageBackupUseCase.exportBackupJson(passphrase = passphrase)
            }
          pendingExportJsonText = rootJson.toString(2)
          operationState.value = BackupOperationState.Exporting
          exportPickerLaunchRequest.value = true
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // Safety net: PBKDF2/Cipher can throw unchecked RuntimeException (e.g. NPE from
          // provider failures). All crypto errors surface as a single user-facing message.
          operationState.value =
            BackupOperationState.Error(
              application.getString(
                R.string.error_encrypting_backup,
                e.localizedMessage ?: "خطای ناشناخته"
              )
            )
        } finally {
          isCryptoInProgress.value = false
        }
      }
    }

    /**
     * Called from the export passphrase dialog when the user skips encryption.
     * Exports without a passphrase (plaintext sensitive fields).
     */
    @Suppress("TooGenericExceptionCaught") // Safety net: repository/JSON operations can throw unchecked exceptions
    fun exportWithoutPassphrase() {
      viewModelScope.launch {
        exportPickerLaunchRequest.value = false
        passphraseDialogState.value = PassphraseDialogState.Hidden
        operationState.value = BackupOperationState.Exporting
        try {
          val rootJson = manageBackupUseCase.exportBackupJson()
          pendingExportJsonText = rootJson.toString(2)
          exportPickerLaunchRequest.value = true
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // Safety net: repository/JSON operations can throw unchecked exceptions.
          operationState.value =
            BackupOperationState.Error(
              application.getString(
                R.string.error_preparing_backup,
                e.localizedMessage ?: "خطای ناشناخته"
              )
            )
        }
      }
    }

    /**
     * Writes the staged export JSON to the output stream.
     * Called by SettingsScreen after the file picker returns a URI.
     * Nulls out [pendingExportJsonText] immediately after write (success or failure)
     * to avoid keeping plaintext-adjacent sensitive data in ViewModel state.
     */
    fun writeStagedExportToFile(outputStream: OutputStream) {
      val json = pendingExportJsonText
      if (json == null) {
        operationState.value = BackupOperationState.Error(application.getString(R.string.error_backup_data_not_ready))
        return
      }
      viewModelScope.launch {
        try {
          withContext(ioDispatcher) {
            outputStream.use { os -> os.write(json.toByteArray()) }
          }
          val root =
            try {
              JSONObject(json)
            } catch (_: JSONException) {
              null
            }
          val summary = buildExportSummary(root)
          operationState.value = BackupOperationState.ExportSuccess(summary)
        } catch (e: IOException) {
          operationState.value =
            BackupOperationState.Error(
              application.getString(
                R.string.error_saving_backup,
                e.localizedMessage ?: "خطای ورودی/خروجی"
              )
            )
        } catch (e: JSONException) {
          operationState.value =
            BackupOperationState.Error(
              application.getString(
                R.string.error_processing_backup_json,
                e.localizedMessage ?: "خطای نامشخص JSON"
              )
            )
        } finally {
          pendingExportJsonText = null
        }
      }
    }

    private fun buildExportSummary(root: JSONObject?): String {
      if (root == null) return application.getString(R.string.backup_saved_success) + "."
      val txCount = root.optJSONArray("transactions")?.length() ?: 0
      val loanCount = root.optJSONArray("loans")?.length() ?: 0
      val instCount = root.optJSONArray("installments")?.length() ?: 0
      val catCount = root.optJSONArray("categories")?.length() ?: 0
      val accountCount = root.optJSONArray("accounts")?.length() ?: 0
      return application.getString(R.string.backup_saved_success) +
        ". ${manageBackupUseCase.buildExportSummary(
          txCount,
          loanCount,
          instCount,
          catCount,
          accountCount = accountCount
        )}"
    }

    // --- Import passphrase flow ---

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
      viewModelScope.launch {
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
            pendingImportRawJson = text
            pendingImportSalt = salt
            passphraseDialogState.value = PassphraseDialogState.ImportPassphrase(salt ?: "")
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

      viewModelScope.launch {
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

    /**
     * Called when the user cancels the export file picker (SAF).
     * Clears the staged export JSON and resets [operationState] to [BackupOperationState.Idle]
     * so the export button is re-enabled. Cancellation is a normal user action, not an error.
     */
    fun onExportPickerCancelled() {
      pendingExportJsonText = null
      exportPickerLaunchRequest.value = false
      operationState.value = BackupOperationState.Idle
    }

    /**
     * Resets [exportPickerLaunchRequest] once the screen has launched the picker.
     */
    fun consumeExportPickerLaunchRequest() {
      exportPickerLaunchRequest.value = false
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

    // --- Restore execution (unchanged) ---

    fun executeRestore() {
      val backup = pendingRestoreBackup.value ?: return
      val mode = selectedRestoreMode.value

      viewModelScope.launch {
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
      sharedPrefs.edit().putBoolean("dark_mode", settings.darkMode).apply()
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

    fun clearOperationState() {
      operationState.value = BackupOperationState.Idle
    }

    /**
     * Legacy import path (always REPLACE mode, no passphrase support).
     * Kept for backward compatibility; new code should use
     * [validateAndStageImport] + [executeRestore].
     */
    fun importBackupFromFile(inputStream: InputStream) {
      viewModelScope.launch {
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
