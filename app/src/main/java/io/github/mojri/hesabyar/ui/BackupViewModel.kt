package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao()
    )

    private val transactions = repository.allTransactions
    private val loans = repository.allLoans
    private val installments = repository.allInstallments

    fun exportBackupToFile(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                val rootJson = JSONObject()

                val curTrans = transactions.firstOrNull() ?: emptyList()
                val curLoans = loans.firstOrNull() ?: emptyList()
                val curInstallments = installments.firstOrNull() ?: emptyList()

                val allPayments = ArrayList<PaymentHistory>()
                curLoans.forEach { loan ->
                    val history = repository.getPaymentHistoryForLoan(loan.id).firstOrNull() ?: emptyList()
                    allPayments.addAll(history)
                }

                val transArray = JSONArray()
                curTrans.forEach {
                    transArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("type", it.type)
                        put("categoryId", it.categoryId)
                        put("amount", it.amount.toLong())
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
                        put("amount", it.amount.toLong())
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
                        put("amount", it.amount.toLong())
                        put("date", it.date)
                        put("notes", it.notes)
                    })
                }
                rootJson.put("paymentHistories", paymentsArray)

                outputStream.use { os ->
                    os.write(rootJson.toString(2).toByteArray())
                }
            } catch (_: Exception) {
            }
        }
    }

    fun importBackupFromFile(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val text = inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(text)

                val transList = ArrayList<Transaction>()
                val transArray = rootJson.optJSONArray("transactions") ?: JSONArray()
                for (i in 0 until transArray.length()) {
                    val obj = transArray.getJSONObject(i)
                    val categoryId = if (obj.has("categoryId")) {
                        obj.getLong("categoryId")
                    } else {
                        1L
                    }
                    transList.add(Transaction(
                        type = obj.getString("type"),
                        categoryId = categoryId,
                        amount = obj.getLong("amount"),
                        description = obj.getString("description"),
                        personName = obj.optString("personName").let { if (it.isBlank()) null else it },
                        date = obj.getLong("date"),
                        dueDate = obj.optLong("dueDate").let { if (it == 0L) null else it },
                        installmentId = obj.optLong("installmentId").let { if (it == 0L) null else it }
                    ))
                }

                val loanList = ArrayList<Loan>()
                val loansArray = rootJson.optJSONArray("loans") ?: JSONArray()
                for (i in 0 until loansArray.length()) {
                    val obj = loansArray.getJSONObject(i)
                    loanList.add(Loan(
                        personName = obj.getString("personName"),
                        type = obj.getString("type"),
                        originalAmount = obj.getLong("originalAmount"),
                        remainingAmount = obj.getLong("remainingAmount"),
                        description = obj.getString("description"),
                        date = obj.getLong("date"),
                        isSettled = obj.getBoolean("isSettled")
                    ))
                }

                val instList = ArrayList<Installment>()
                val instArray = rootJson.optJSONArray("installments") ?: JSONArray()
                for (i in 0 until instArray.length()) {
                    val obj = instArray.getJSONObject(i)
                    instList.add(Installment(
                        title = obj.getString("title"),
                        amount = obj.getLong("amount"),
                        dueDate = obj.getLong("dueDate"),
                        isPaid = obj.getBoolean("isPaid"),
                        reminderEnabled = obj.getBoolean("reminderEnabled"),
                        notes = obj.optString("notes", "")
                    ))
                }

                val pmList = ArrayList<PaymentHistory>()
                val paymentsArray = rootJson.optJSONArray("paymentHistories") ?: JSONArray()
                for (i in 0 until paymentsArray.length()) {
                    val obj = paymentsArray.getJSONObject(i)
                    pmList.add(PaymentHistory(
                        loanId = obj.getLong("loanId"),
                        amount = obj.getLong("amount"),
                        date = obj.getLong("date"),
                        notes = obj.optString("notes", "")
                    ))
                }

                repository.importBackup(transList, loanList, instList, pmList)
            } catch (_: Exception) {
            }
        }
    }
}
