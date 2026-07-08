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
  fun parseBackupJson(jsonString: String): BackupPayload? {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge
        .parseBackupJsonSync(jsonString) ?: return null

    // Parse paymentHistories from raw JSON (Rust ignores unknown fields)
    val paymentHistories: List<PaymentHistory> =
      try {
        val json = JSONObject(jsonString)
        val arr = json.optJSONArray("paymentHistories")
        if (arr != null) {
          (0 until arr.length()).mapNotNull { i ->
            try {
              val obj = arr.getJSONObject(i)
              PaymentHistory(
                id = obj.optLong("id", 0L),
                loanId = obj.optLong("loanId", 0L),
                amount = obj.optLong("amount", 0L),
                date = obj.optLong("date", System.currentTimeMillis()),
                notes = obj.optString("notes", "")
              )
            } catch (_: Exception) {
              null
            }
          }
        } else {
          emptyList()
        }
      } catch (_: Exception) {
        emptyList()
      }

    // Parse settings from raw JSON
    val settings: BackupSettings =
      try {
        val json = JSONObject(jsonString)
        val settingsObj = json.optJSONObject("settings")
        if (settingsObj != null) {
          BackupSettings(darkMode = settingsObj.optBoolean("darkMode", true))
        } else {
          BackupSettings()
        }
      } catch (_: Exception) {
        BackupSettings()
      }

    return BackupPayload(
      version = rustResult.version,
      timestamp = rustResult.timestamp,
      appVersion = rustResult.appVersion,
      transactions =
        rustResult.transactions.map { tx ->
          Transaction(
            id = tx.id,
            type = tx.txType.name,
            categoryId = tx.categoryId,
            amount = tx.amount,
            description = tx.description,
            personName = tx.personName,
            date = tx.date,
            dueDate = tx.dueDate,
            installmentId = tx.installmentId
          )
        },
      loans =
        rustResult.loans.map { loan ->
          Loan(
            id = loan.id,
            personName = loan.personName,
            type = loan.loanType,
            originalAmount = loan.originalAmount,
            remainingAmount = loan.remainingAmount,
            description = loan.description,
            date = loan.date,
            isSettled = loan.isSettled
          )
        },
      installments =
        rustResult.installments.map { inst ->
          Installment(
            id = inst.id,
            title = inst.title,
            amount = inst.amount,
            dueDate = inst.dueDate,
            isPaid = inst.isPaid,
            reminderEnabled = inst.reminderEnabled,
            notes = inst.notes
          )
        },
      paymentHistories = paymentHistories,
      categories =
        rustResult.categories.map { cat ->
          Category(
            id = cat.id,
            name = cat.name,
            key = cat.key,
            icon = cat.icon,
            color = cat.color,
            type = cat.categoryType,
            isDefault = cat.isDefault
          )
        },
      settings = settings
    )
  }

  fun validateBackup(backup: BackupPayload): BackupValidationResult {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.validateBackupPayloadSync(
        io.github.mojri.hesabyar.rust.BackupPayload(
          version = backup.version,
          timestamp = backup.timestamp,
          appVersion = backup.appVersion,
          transactions =
            backup.transactions.mapNotNull { tx ->
              val txType =
                try {
                  io.github.mojri.hesabyar.rust.TransactionType
                    .valueOf(tx.type)
                } catch (_: IllegalArgumentException) {
                  return@mapNotNull null
                }
              io.github.mojri.hesabyar.rust.Transaction(
                id = tx.id,
                txType = txType,
                categoryId = tx.categoryId,
                amount = tx.amount,
                description = tx.description,
                personName = tx.personName,
                date = tx.date,
                dueDate = tx.dueDate,
                installmentId = tx.installmentId
              )
            },
          loans =
            backup.loans.map { loan ->
              io.github.mojri.hesabyar.rust.Loan(
                id = loan.id,
                personName = loan.personName,
                loanType = loan.type,
                originalAmount = loan.originalAmount,
                remainingAmount = loan.remainingAmount,
                description = loan.description,
                date = loan.date,
                isSettled = loan.isSettled
              )
            },
          installments =
            backup.installments.map { inst ->
              io.github.mojri.hesabyar.rust.Installment(
                id = inst.id,
                title = inst.title,
                amount = inst.amount,
                dueDate = inst.dueDate,
                isPaid = inst.isPaid,
                reminderEnabled = inst.reminderEnabled,
                notes = inst.notes
              )
            },
          categories =
            backup.categories.map { cat ->
              io.github.mojri.hesabyar.rust.Category(
                id = cat.id,
                name = cat.name,
                key = cat.key,
                icon = cat.icon,
                color = cat.color,
                categoryType = cat.type,
                isDefault = cat.isDefault
              )
            }
        )
      )

    return if (rustResult.isValid) {
      BackupValidationResult.Valid
    } else {
      BackupValidationResult.Invalid(rustResult.errors)
    }
  }

  suspend fun executeRestore(
    backup: BackupPayload,
    mode: RestoreMode
  ) {
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

    rootJson.put(
      "settings",
      JSONObject().apply {
        put("darkMode", isDarkMode)
      }
    )

    val curCategories = repository.allCategories.firstOrNull() ?: emptyList()
    val curTrans = repository.allTransactions.firstOrNull() ?: emptyList()
    val curLoans = repository.allLoans.firstOrNull() ?: emptyList()
    val curInstallments = repository.allInstallments.firstOrNull() ?: emptyList()
    val allPayments = repository.getAllPaymentHistories()

    val catArray = JSONArray()
    curCategories.forEach {
      catArray.put(
        JSONObject().apply {
          put("id", it.id)
          put("name", it.name)
          put("key", it.key)
          put("icon", it.icon)
          put("color", it.color)
          put("type", it.type)
          put("isDefault", it.isDefault)
        }
      )
    }
    rootJson.put("categories", catArray)

    val transArray = JSONArray()
    curTrans.forEach {
      transArray.put(
        JSONObject().apply {
          put("id", it.id)
          put("type", it.type)
          put("categoryId", it.categoryId)
          put("amount", it.amount)
          put("description", it.description)
          put("personName", it.personName ?: "")
          put("date", it.date)
          put("dueDate", it.dueDate ?: 0L)
          put("installmentId", it.installmentId ?: 0L)
        }
      )
    }
    rootJson.put("transactions", transArray)

    val loansArray = JSONArray()
    curLoans.forEach {
      loansArray.put(
        JSONObject().apply {
          put("id", it.id)
          put("personName", it.personName)
          put("type", it.type)
          put("originalAmount", it.originalAmount)
          put("remainingAmount", it.remainingAmount)
          put("description", it.description)
          put("date", it.date)
          put("isSettled", it.isSettled)
        }
      )
    }
    rootJson.put("loans", loansArray)

    val instArray = JSONArray()
    curInstallments.forEach {
      instArray.put(
        JSONObject().apply {
          put("id", it.id)
          put("title", it.title)
          put("amount", it.amount)
          put("dueDate", it.dueDate)
          put("isPaid", it.isPaid)
          put("reminderEnabled", it.reminderEnabled)
          put("notes", it.notes)
        }
      )
    }
    rootJson.put("installments", instArray)

    val paymentsArray = JSONArray()
    allPayments.forEach {
      paymentsArray.put(
        JSONObject().apply {
          put("id", it.id)
          put("loanId", it.loanId)
          put("amount", it.amount)
          put("date", it.date)
          put("notes", it.notes)
        }
      )
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

  fun buildExportSummary(
    transCount: Int,
    loanCount: Int,
    instCount: Int,
    catCount: Int
  ): String = "$transCount تراکنش، $loanCount وام، $instCount قسط، $catCount دسته‌بندی"
}
