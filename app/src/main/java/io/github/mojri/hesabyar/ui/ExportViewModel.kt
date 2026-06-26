package io.github.mojri.hesabyar.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mojri.hesabyar.domain.usecase.ExportExcelUseCase
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val exportExcelUseCase: ExportExcelUseCase
) : ViewModel() {

    private val appContext = application

    val exportState = mutableStateOf<ExportState>(ExportState.Idle)

    fun exportExcel() {
        viewModelScope.launch {
            exportState.value = ExportState.Exporting
            try {
                val result = exportExcelUseCase.export()

                val savedPath = saveToDownloads(result.file)
                result.file.delete()

                val summary = buildString {
                    append("فایل اکسل در پوشه Downloads ذخیره شد:\n")
                    append(savedPath)
                    append("\n\n")
                    append("${result.transactionCount} تراکنش")
                    if (result.incomeCount > 0) append(" (${result.incomeCount} دریافتی)")
                    if (result.expenseCount > 0) append(" (${result.expenseCount} پرداختی)")
                    append(", ${result.loanCount} وام, ${result.installmentCount} قسط")
                }

                exportState.value = ExportState.Success(summary)
            } catch (e: Exception) {
                exportState.value = ExportState.Error(
                    "خطا در ایجاد فایل اکسل: ${e.localizedMessage ?: "خطای ناشناخته"}"
                )
            }
        }
    }

    private fun saveToDownloads(tempFile: File): String {
        val fileName = tempFile.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("ایجاد فایل در Downloads ناموفق بود")

            resolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw Exception("نوشتن فایل ناموفق بود")

            return "Downloads/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val destFile = File(downloadsDir, fileName)
            tempFile.copyTo(destFile, overwrite = true)

            return destFile.absolutePath
        }
    }

    fun clearState() {
        exportState.value = ExportState.Idle
    }
}
