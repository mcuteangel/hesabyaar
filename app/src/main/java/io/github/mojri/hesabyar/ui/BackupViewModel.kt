package io.github.mojri.hesabyar.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

sealed interface BackupOperationState {
    object Idle : BackupOperationState
    object Exporting : BackupOperationState
    object Importing : BackupOperationState
    data class ExportSuccess(val message: String) : BackupOperationState
    data class ImportSuccess(val message: String) : BackupOperationState
    data class Error(val message: String) : BackupOperationState
    data class ValidationFailed(val errors: List<String>) : BackupOperationState
}

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao()
    )

    private val sharedPrefs = application.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)

    val operationState = mutableStateOf<BackupOperationState>(BackupOperationState.Idle)
    val pendingRestoreBackup = mutableStateOf<BackupPayload?>(null)
    val selectedRestoreMode = mutableStateOf(RestoreMode.REPLACE)

    private fun parseBackupJson(jsonString: String): BackupPayload {
        val root = JSONObject(jsonString)

        val transactions = ArrayList<Transaction>()
        val transArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until transArray.length()) {
            val obj = transArray.getJSONObject(i)
            transactions.add(Transaction(
                id = obj.optLong("id", 0),
                type = obj.getString("type"),
                categoryId = obj.optLong("categoryId", 1L),
                amount = obj.getLong("amount"),
                description = obj.optString("description", ""),
                personName = obj.optString("personName").let { if (it.isBlank()) null else it },
                date = obj.optLong("date", System.currentTimeMillis()),
                dueDate = obj.optLong("dueDate", 0L).let { if (it == 0L) null else it },
                installmentId = obj.optLong("installmentId", 0L).let { if (it == 0L) null else it }
            ))
        }

        val loans = ArrayList<Loan>()
        val loansArray = root.optJSONArray("loans") ?: JSONArray()
        for (i in 0 until loansArray.length()) {
            val obj = loansArray.getJSONObject(i)
            loans.add(Loan(
                id = obj.optLong("id", 0),
                personName = obj.getString("personName"),
                type = obj.getString("type"),
                originalAmount = obj.getLong("originalAmount"),
                remainingAmount = obj.getLong("remainingAmount"),
                description = obj.optString("description", ""),
                date = obj.optLong("date", System.currentTimeMillis()),
                isSettled = obj.optBoolean("isSettled", false)
            ))
        }

        val installments = ArrayList<Installment>()
        val instArray = root.optJSONArray("installments") ?: JSONArray()
        for (i in 0 until instArray.length()) {
            val obj = instArray.getJSONObject(i)
            installments.add(Installment(
                id = obj.optLong("id", 0),
                title = obj.getString("title"),
                amount = obj.getLong("amount"),
                dueDate = obj.getLong("dueDate"),
                isPaid = obj.optBoolean("isPaid", false),
                reminderEnabled = obj.optBoolean("reminderEnabled", true),
                notes = obj.optString("notes", "")
            ))
        }

        val paymentHistories = ArrayList<PaymentHistory>()
        val paymentsArray = root.optJSONArray("paymentHistories") ?: JSONArray()
        for (i in 0 until paymentsArray.length()) {
            val obj = paymentsArray.getJSONObject(i)
            paymentHistories.add(PaymentHistory(
                id = obj.optLong("id", 0),
                loanId = obj.getLong("loanId"),
                amount = obj.getLong("amount"),
                date = obj.optLong("date", System.currentTimeMillis()),
                notes = obj.optString("notes", "")
            ))
        }

        val categories = ArrayList<Category>()
        val catArray = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until catArray.length()) {
            val obj = catArray.getJSONObject(i)
            categories.add(Category(
                id = obj.optLong("id", 0),
                name = obj.getString("name"),
                key = obj.getString("key"),
                icon = obj.optString("icon", "Paid"),
                color = obj.optLong("color", 0xFF757575L),
                type = obj.optString("type", "BOTH"),
                isDefault = obj.optBoolean("isDefault", false)
            ))
        }

        val settingsObj = root.optJSONObject("settings")
        val settings = BackupSettings(
            darkMode = settingsObj?.optBoolean("darkMode", true) ?: true
        )

        return BackupPayload(
            version = root.optInt("version", 1),
            timestamp = root.optLong("timestamp", System.currentTimeMillis()),
            appVersion = root.optString("appVersion", "1.0"),
            transactions = transactions,
            loans = loans,
            installments = installments,
            paymentHistories = paymentHistories,
            categories = categories,
            settings = settings
        )
    }

    private fun validateBackup(backup: BackupPayload): BackupValidationResult {
        val errors = ArrayList<String>()

        if (backup.version < 1) {
            errors.add("نسخه پشتیبان نامعتبر است")
        }

        for (tx in backup.transactions) {
            if (tx.amount <= 0) {
                errors.add("مبلغ تراکنش نامعتبر: ${tx.description}")
            }
            if (tx.type !in listOf("EXPENSE", "INCOME")) {
                errors.add("نوع تراکنش نامعتبر: ${tx.type}")
            }
        }

        for (loan in backup.loans) {
            if (loan.originalAmount <= 0) {
                errors.add("مبلغ وام نامعتبر: ${loan.personName}")
            }
            if (loan.type !in listOf("DEBTOR", "CREDITOR")) {
                errors.add("نوع وام نامعتبر: ${loan.type}")
            }
        }

        for (inst in backup.installments) {
            if (inst.amount <= 0) {
                errors.add("مبلغ قسط نامعتبر: ${inst.title}")
            }
        }

        if (errors.isNotEmpty()) {
            return BackupValidationResult.Invalid(errors)
        }
        return BackupValidationResult.Valid
    }

    fun validateAndStageImport(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val text = inputStream.bufferedReader().use { it.readText() }
                val backup = parseBackupJson(text)

                when (val result = validateBackup(backup)) {
                    is BackupValidationResult.Invalid -> {
                        operationState.value = BackupOperationState.ValidationFailed(result.errors)
                    }
                    is BackupValidationResult.Valid -> {
                        pendingRestoreBackup.value = backup
                    }
                }
            } catch (e: Exception) {
                operationState.value = BackupOperationState.Error(
                    "خطا در خواندن فایل پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
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
                when (mode) {
                    RestoreMode.REPLACE -> {
                        repository.replaceAllFromBackup(backup)
                        applySettings(backup.settings)
                        operationState.value = BackupOperationState.ImportSuccess(
                            "بازیابی کامل با موفقیت انجام شد. " +
                            "${backup.transactions.size} تراکنش، " +
                            "${backup.loans.size} وام، " +
                            "${backup.installments.size} قسط، " +
                            "${backup.categories.size} دسته‌بندی بازیابی شد."
                        )
                    }
                    RestoreMode.MERGE -> {
                        repository.mergeFromBackup(backup)
                        applySettings(backup.settings)
                        operationState.value = BackupOperationState.ImportSuccess(
                            "ادغام پشتیبان با موفقیت انجام شد."
                        )
                    }
                }
                pendingRestoreBackup.value = null
            } catch (e: Exception) {
                operationState.value = BackupOperationState.Error(
                    "خطا در بازیابی: ${e.localizedMessage ?: "خطای ناشناخته"}"
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
                val rootJson = JSONObject()
                rootJson.put("version", 1)
                rootJson.put("timestamp", System.currentTimeMillis())
                rootJson.put("appVersion", "1.0")

                val curCategories = repository.allCategories.firstOrNull() ?: emptyList()
                val curTrans = repository.allTransactions.firstOrNull() ?: emptyList()
                val curLoans = repository.allLoans.firstOrNull() ?: emptyList()
                val curInstallments = repository.allInstallments.firstOrNull() ?: emptyList()
                val allPayments = repository.getAllPaymentHistories()

                val catArray = JSONArray()
                curCategories.forEach {
                    catArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("key", it.key)
                        put("icon", it.icon)
                        put("color", it.color)
                        put("type", it.type)
                        put("isDefault", it.isDefault)
                    })
                }
                rootJson.put("categories", catArray)

                val transArray = JSONArray()
                curTrans.forEach {
                    transArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("type", it.type)
                        put("categoryId", it.categoryId)
                        put("amount", it.amount)
                        put("description", it.description)
                        put("personName", it.personName ?: "")
                        put("date", it.date)
                        put("dueDate", it.dueDate ?: 0L)
                        put("installmentId", it.installmentId ?: 0L)
                    })
                }
                rootJson.put("transactions", transArray)

                val loansArray = JSONArray()
                curLoans.forEach {
                    loansArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("personName", it.personName)
                        put("type", it.type)
                        put("originalAmount", it.originalAmount)
                        put("remainingAmount", it.remainingAmount)
                        put("description", it.description)
                        put("date", it.date)
                        put("isSettled", it.isSettled)
                    })
                }
                rootJson.put("loans", loansArray)

                val instArray = JSONArray()
                curInstallments.forEach {
                    instArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("title", it.title)
                        put("amount", it.amount)
                        put("dueDate", it.dueDate)
                        put("isPaid", it.isPaid)
                        put("reminderEnabled", it.reminderEnabled)
                        put("notes", it.notes)
                    })
                }
                rootJson.put("installments", instArray)

                val paymentsArray = JSONArray()
                allPayments.forEach {
                    paymentsArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("loanId", it.loanId)
                        put("amount", it.amount)
                        put("date", it.date)
                        put("notes", it.notes)
                    })
                }
                rootJson.put("paymentHistories", paymentsArray)

                val darkMode = sharedPrefs.getBoolean("dark_mode", true)
                rootJson.put("settings", JSONObject().apply {
                    put("darkMode", darkMode)
                })

                outputStream.use { os ->
                    os.write(rootJson.toString(2).toByteArray())
                }

                operationState.value = BackupOperationState.ExportSuccess(
                    "پشتیبان با موفقیت ذخیره شد. " +
                    "${curTrans.size} تراکنش، " +
                    "${curLoans.size} وام، " +
                    "${curInstallments.size} قسط، " +
                    "${curCategories.size} دسته‌بندی"
                )
            } catch (e: Exception) {
                operationState.value = BackupOperationState.Error(
                    "خطا در ذخیره پشتیبان: ${e.localizedMessage ?: "خطای ناشناخته"}"
                )
            }
        }
    }

    fun importBackupFromFile(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val text = inputStream.bufferedReader().use { it.readText() }
                val backup = parseBackupJson(text)
                repository.importBackup(
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
