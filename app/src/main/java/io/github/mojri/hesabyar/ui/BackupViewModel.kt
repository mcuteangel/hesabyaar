package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val salt: String
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
     * Called from SettingsScreen after export button tap.
     * Shows the export passphrase dialog.
     */
    fun requestExportPassphraseDialog() {
      passphraseDialogState.value = PassphraseDialogState.ExportPassphrase
    }

    /**
     * Called from the export passphrase dialog with the user-entered passphrase.
     * Runs PBKDF2 derivation + encryption on [cryptoDispatcher], stages the JSON,
     * and clears the dialog. The file picker is launched by the screen after
     * this returns successfully (observe [pendingExportJsonText]).
     */
    @Suppress("TooGenericExceptionCaught") // Safety net: PBKDF2/Cipher can throw unchecked RuntimeException
    fun exportWithPassphrase(passphrase: String) {
      viewModelScope.launch {
        isCryptoInProgress.value = true
        passphraseDialogState.value = PassphraseDialogState.Hidden
        try {
          val rootJson =
            withContext(cryptoDispatcher) {
              manageBackupUseCase.exportBackupJson(passphrase = passphrase)
            }
          pendingExportJsonText = rootJson.toString(2)
          operationState.value = BackupOperationState.Exporting
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // Safety net: PBKDF2/Cipher can throw unchecked RuntimeException (e.g. NPE from
          // provider failures). All crypto errors surface as a single user-facing message.
          operationState.value =
            BackupOperationState.Error(
              "خطا در رمزگذاری پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
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
        passphraseDialogState.value = PassphraseDialogState.Hidden
        operationState.value = BackupOperationState.Exporting
        try {
          val rootJson = manageBackupUseCase.exportBackupJson()
          pendingExportJsonText = rootJson.toString(2)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // Safety net: repository/JSON operations can throw unchecked exceptions.
          operationState.value =
            BackupOperationState.Error(
              "خطا در تهیه پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
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
        operationState.value = BackupOperationState.Error("خطا: داده پشتیبان آماده نیست")
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
              "خطا در ذخیره پشتیبان: ${e.localizedMessage ?: "خطای ورودی/خروجی"}"
            )
        } catch (e: JSONException) {
          operationState.value =
            BackupOperationState.Error(
              "خطا در پردازش JSON پشتیبان: ${e.localizedMessage ?: "خطای نامشخص JSON"}"
            )
        } finally {
          pendingExportJsonText = null
        }
      }
    }

    private fun buildExportSummary(root: JSONObject?): String {
      if (root == null) return "پشتیبان با موفقیت ذخیره شد."
      val txCount = root.optJSONArray("transactions")?.length() ?: 0
      val loanCount = root.optJSONArray("loans")?.length() ?: 0
      val instCount = root.optJSONArray("installments")?.length() ?: 0
      val catCount = root.optJSONArray("categories")?.length() ?: 0
      val accountCount = root.optJSONArray("accounts")?.length() ?: 0
      return "پشتیبان با موفقیت ذخیره شد. ${manageBackupUseCase.buildExportSummary(
        txCount,
        loanCount,
        instCount,
        catCount,
        accountCount = accountCount
      )}"
    }

    // --- Import passphrase flow ---

    private var pendingImportRawJson: String? = null
    private var pendingImportSalt: String? = null

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
              "خطا در خواندن فایل پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
            )
        } catch (e: JSONException) {
          operationState.value =
            BackupOperationState.Error(
              "خطا در تجزیه فایل پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
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
        passphraseDialogState.value = PassphraseDialogState.Hidden
        try {
          val parsed = parseBackupOrReportError(rawJson) ?: return@launch
          val rootJson = JSONObject(rawJson)
          val decrypted =
            withContext(cryptoDispatcher) {
              manageBackupUseCase.decryptBackupWithPassphrase(parsed, rootJson, passphrase)
            }
          stageValidatedBackup(decrypted)
        } catch (_: Exception) {
          // Wrong passphrase or tampered ciphertext — both throw, but we cannot
          // distinguish them (AEADBadTagException vs IllegalArgumentException).
          operationState.value =
            BackupOperationState.Error("رمز عبور اشتباه است یا فایل بکاپ خراب است")
        } finally {
          isCryptoInProgress.value = false
          pendingImportRawJson = null
          pendingImportSalt = null
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
          "خطا در تجزیه فایل پشتیبان: ساختار فایل نامعتبر است"
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
