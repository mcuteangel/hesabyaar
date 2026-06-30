package io.github.mojri.hesabyar.ui

import android.content.Context
import com.squareup.moshi.JsonDataException
import java.io.IOException
import org.json.JSONException
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
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val manageBackupUseCase: ManageBackupUseCase
) : ViewModel() {

    private val sharedPrefs = application.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)

    val operationState = mutableStateOf<BackupOperationState>(BackupOperationState.Idle)
    val pendingRestoreBackup = mutableStateOf<BackupPayload?>(null)
    val selectedRestoreMode = mutableStateOf(RestoreMode.REPLACE)

    fun validateAndStageImport(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val text = inputStream.bufferedReader().use { it.readText() }
                val backup = manageBackupUseCase.parseBackupJson(text)

                when (val result = manageBackupUseCase.validateBackup(backup)) {
                    is BackupValidationResult.Invalid -> {
                        operationState.value = BackupOperationState.ValidationFailed(result.errors)
                    }
                    is BackupValidationResult.Valid -> {
                        pendingRestoreBackup.value = backup
                    }
                }
            } catch (e: IOException) {
                operationState.value = BackupOperationState.Error(
                    "خطا در خواندن فایل پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
                )
            } catch (e: JsonDataException) {
                operationState.value = BackupOperationState.Error(
                    "خطا در تجزیه فایل پشتیبان: ${e.message ?: "خطای ناشناخته"}"
                )
            }
        }
    }

    fun executeRestore() {
        val backup = pendingRestoreBackup.value ?: return
        val mode = selectedRestoreMode.value

        viewModelScope.launch {
            operationState.value = BackupOperationState.Importing
            try {
                manageBackupUseCase.executeRestore(backup, mode)
                applySettings(backup.settings)
                operationState.value = BackupOperationState.ImportSuccess(
                    when (mode) {
                        RestoreMode.REPLACE -> "بازیابی کامل با موفقیت انجام شد. ${manageBackupUseCase.buildBackupSummary(backup)}"
                        RestoreMode.MERGE -> "ادغام پشتیبان با موفقیت انجام شد."
                        else -> "عملیات با موفقیت انجام شد."
                    }
                )
                pendingRestoreBackup.value = null
            } catch (e: IOException) {
                operationState.value = BackupOperationState.Error(
                    "خطا در دسترسی به فایل پشتیبان: ${e.localizedMessage ?: "خطای ورودی/خروجی"}"
                )
            } catch (e: SecurityException) {
                operationState.value = BackupOperationState.Error(
                    "دسترسی به فایل پشتیبان غیرمجاز است: ${e.localizedMessage ?: ""}"
                )
            } catch (e: IllegalArgumentException) {
                operationState.value = BackupOperationState.Error(
                    "تنظیمات پشتیبان نامعتبر است: ${e.localizedMessage ?: ""}"
                )
            }
        }
    }

    private fun applySettings(settings: BackupSettings) {
        sharedPrefs.edit().putBoolean("dark_mode", settings.darkMode).apply()
    }

    fun cancelPendingRestore() {
        pendingRestoreBackup.value = null
    }

    fun clearOperationState() {
        operationState.value = BackupOperationState.Idle
    }

    fun exportBackupToFile(outputStream: OutputStream) {
        viewModelScope.launch {
            operationState.value = BackupOperationState.Exporting
            try {
                val rootJson = manageBackupUseCase.exportBackupJson()

                outputStream.use { os ->
                    os.write(rootJson.toString(2).toByteArray())
                }

                val summary = rootJson.let {
                    val txCount = it.optJSONArray("transactions")?.length() ?: 0
                    val loanCount = it.optJSONArray("loans")?.length() ?: 0
                    val instCount = it.optJSONArray("installments")?.length() ?: 0
                    val catCount = it.optJSONArray("categories")?.length() ?: 0
                    "پشتیبان با موفقیت ذخیره شد. ${manageBackupUseCase.buildExportSummary(txCount, loanCount, instCount, catCount)}"
                }

                operationState.value = BackupOperationState.ExportSuccess(summary)
            } catch (e: IOException) {
                operationState.value = BackupOperationState.Error(
                    "خطا در ذخیره پشتیبان: ${e.localizedMessage ?: "خطای ورودی/خروجی"}"
                )
            } catch (e: JSONException) {
                operationState.value = BackupOperationState.Error(
                    "خطا در پردازش JSON پشتیبان: ${e.localizedMessage ?: "خطای نامشخص JSON"}"
                )
            }
        }
    }

    fun importBackupFromFile(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val text = inputStream.bufferedReader().use { it.readText() }
                val backup = manageBackupUseCase.parseBackupJson(text)
                manageBackupUseCase.importBackupFromFile(
                    backup.transactions,
                    backup.loans,
                    backup.installments,
                    backup.paymentHistories
                )
            } catch (_: Exception) {
            }
        }
    }
}
