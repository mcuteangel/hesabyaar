package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.mojri.hesabyar.R
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
import java.io.OutputStream

/**
 * Owns the export half of the backup UI flow: the passphrase dialog, staging
 * the encrypted/plaintext JSON, and the one-shot SAF picker launch signal.
 * Shared operation/dialog state is passed in by [BackupViewModel], which stays
 * the single owner of cross-flow state.
 */
class BackupExportCoordinator(
  private val application: Context,
  private val manageBackupUseCase: ManageBackupUseCase,
  private val scope: CoroutineScope,
  private val operationState: MutableState<BackupOperationState>,
  private val passphraseDialogState: MutableState<PassphraseDialogState>,
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
    scope.launch {
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
    scope.launch {
      exportPickerLaunchRequest.value = false
      passphraseDialogState.value = PassphraseDialogState.Hidden
      try {
        val rootJson =
          withContext(cryptoDispatcher) {
            manageBackupUseCase.exportBackupJson()
          }
        pendingExportJsonText = rootJson.toString(2)
        operationState.value = BackupOperationState.Exporting
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
    scope.launch {
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
}
