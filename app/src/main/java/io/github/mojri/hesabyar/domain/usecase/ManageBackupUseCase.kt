package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupSettings
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.RestoreMode
import io.github.mojri.hesabyar.data.Transaction
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject

class ManageBackupUseCase(
    private val repository: HesabyarRepositoryInterface
) {
    fun parseBackupJson(jsonString: String): BackupPayload {
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

    fun validateBackup(backup: BackupPayload): BackupValidationResult {
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

    suspend fun executeRestore(backup: BackupPayload, mode: RestoreMode) {
        when (mode) {
            RestoreMode.REPLACE -> repository.replaceAllFromBackup(backup)
            RestoreMode.MERGE -> repository.mergeFromBackup(backup)
        }
    }

    suspend fun exportBackupJson(isDarkMode: Boolean = true): JSONObject {
        val rootJson = JSONObject()
        rootJson.put("version", 1)
        rootJson.put("timestamp", System.currentTimeMillis())
        rootJson.put("appVersion", "1.0")

        rootJson.put("settings", JSONObject().apply {
            put("darkMode", isDarkMode)
        })

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

        return rootJson
    }

    suspend fun importBackupFromFile(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        paymentHistories: List<PaymentHistory>
    ) {
        repository.importBackup(transactions, loans, installments, paymentHistories)
    }

    fun buildBackupSummary(backup: BackupPayload): String =
        "${backup.transactions.size} تراکنش، ${backup.loans.size} وام، ${backup.installments.size} قسط، ${backup.categories.size} دسته‌بندی بازیابی شد."

    fun buildExportSummary(transCount: Int, loanCount: Int, instCount: Int, catCount: Int): String =
        "$transCount تراکنش، $loanCount وام، $instCount قسط، $catCount دسته‌بندی"
}
