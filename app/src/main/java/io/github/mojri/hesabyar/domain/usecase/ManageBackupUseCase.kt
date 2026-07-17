package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.BuildConfig
import io.github.mojri.hesabyar.data.BACKUP_SCHEMA_VERSION
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupSettings
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.RestoreMode
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ManageBackupUseCase(
  private val repository: HesabyarRepositoryInterface,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  suspend fun parseBackupJson(jsonString: String): BackupPayload? =
    withContext(dispatcher) {
      val rustResult =
        io.github.mojri.hesabyar.rust.RustBridge
          .parseBackupJsonSync(jsonString)
      if (rustResult != null) {
        try {
          val rootJson = parseRawJson(jsonString)
          BackupPayload(
            version = rustResult.version,
            timestamp = rustResult.timestamp,
            appVersion = rustResult.appVersion,
            transactions =
              rustResult.transactions.map {
                io.github.mojri.hesabyar.rust.RustMappers
                  .fromRustTransaction(it)
              },
            loans =
              rustResult.loans.map {
                io.github.mojri.hesabyar.rust.RustMappers
                  .fromRustLoan(it)
              },
            installments =
              rustResult.installments.map {
                io.github.mojri.hesabyar.rust.RustMappers
                  .fromRustInstallment(it)
              },
            paymentHistories = parsePaymentHistories(rootJson),
            categories =
              rustResult.categories.map {
                io.github.mojri.hesabyar.rust.RustMappers
                  .fromRustCategory(it)
              },
            bankLoans =
              rustResult.bankLoans.map {
                io.github.mojri.hesabyar.rust.RustMappers
                  .fromRustBankLoan(it)
              },
            settings = parseSettings(rootJson)
          )
        } catch (_: IllegalArgumentException) {
          // Malformed/outdated enum strings — fall back to Kotlin-only parsing
          parseBackupJsonKotlin(jsonString)
        }
      } else {
        // Rust unavailable — fall back to Kotlin-only JSON parsing
        parseBackupJsonKotlin(jsonString)
      }
    }

  private fun parseRawJson(jsonString: String): JSONObject? =
    try {
      JSONObject(jsonString)
    } catch (_: Exception) {
      null
    }

  private fun parseSettings(rootJson: JSONObject?): BackupSettings {
    val obj = rootJson?.optJSONObject("settings") ?: return BackupSettings()
    return BackupSettings(darkMode = obj.optBoolean("darkMode", true))
  }

  private inline fun <reified T : Enum<T>> parseType(
    obj: JSONObject,
    default: T
  ): T {
    val typeStr = obj.optString("type", "")
    if (typeStr.isEmpty()) return default
    return try {
      enumValueOf<T>(typeStr)
    } catch (_: IllegalArgumentException) {
      default
    }
  }

  private fun parseBackupJsonKotlin(jsonString: String): BackupPayload? {
    val root = parseRawJson(jsonString) ?: return null
    return try {
      BackupPayload(
        version = root.optInt("version", BACKUP_SCHEMA_VERSION),
        timestamp = root.optLong("timestamp", System.currentTimeMillis()),
        appVersion = root.optString("appVersion", BuildConfig.VERSION_NAME),
        transactions = parseTransactions(root),
        loans = parseLoans(root),
        installments = parseInstallmentsFromJson(root),
        paymentHistories = parsePaymentHistories(root),
        categories = parseCategories(root),
        bankLoans = parseBankLoansFromJson(root),
        settings = parseSettings(root)
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun parseTransactions(root: JSONObject): List<Transaction> =
    root.optJSONArray("transactions")?.let { arr ->
      (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val type = parseType(o, io.github.mojri.hesabyar.data.TransactionType.EXPENSE)
        Transaction(
          id = o.optLong("id", 0L),
          type = type,
          categoryId = o.optLong("categoryId", 0L),
          amount = o.optLong("amount", 0L),
          description = o.optString("description", ""),
          personName = o.optString("personName", "").ifBlank { null },
          date = o.optLong("date", 0L),
          dueDate = o.optLong("dueDate", 0L).takeIf { it != 0L },
          installmentId = o.optLong("installmentId", 0L).takeIf { it != 0L }
        )
      }
    } ?: emptyList()

  private fun parseLoans(root: JSONObject): List<Loan> =
    root.optJSONArray("loans")?.let { arr ->
      (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val type = parseType(o, io.github.mojri.hesabyar.data.LoanType.CREDITOR)
        Loan(
          id = o.optLong("id", 0L),
          personName = o.optString("personName", ""),
          type = type,
          originalAmount = o.optLong("originalAmount", 0L),
          remainingAmount = o.optLong("remainingAmount", 0L),
          description = o.optString("description", ""),
          date = o.optLong("date", 0L),
          isSettled = o.optBoolean("isSettled", false)
        )
      }
    } ?: emptyList()

  private fun parseInstallmentsFromJson(root: JSONObject): List<Installment> =
    root.optJSONArray("installments")?.let { arr ->
      (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        Installment(
          id = o.optLong("id", 0L),
          title = o.optString("title", ""),
          amount = o.optLong("amount", 0L),
          dueDate = o.optLong("dueDate", 0L),
          isPaid = o.optBoolean("isPaid", false),
          reminderEnabled = o.optBoolean("reminderEnabled", true),
          notes = o.optString("notes", ""),
          bankLoanId =
            if (o.has("bankLoanId") && !o.isNull("bankLoanId")) o.optLong("bankLoanId") else null
        )
      }
    } ?: emptyList()

  private fun parseCategories(root: JSONObject): List<Category> =
    root.optJSONArray("categories")?.let { arr ->
      (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val type = parseType(o, io.github.mojri.hesabyar.data.CategoryType.EXPENSE)
        Category(
          id = o.optLong("id", 0L),
          name = o.optString("name", ""),
          key = o.optString("key", ""),
          icon = o.optString("icon", ""),
          color = o.optLong("color", 0L),
          type = type,
          isDefault = o.optBoolean("isDefault", false)
        )
      }
    } ?: emptyList()

  private fun parsePaymentHistories(rootJson: JSONObject?): List<PaymentHistory> {
    val arr = rootJson?.optJSONArray("paymentHistories") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
      val obj = arr.optJSONObject(i) ?: return@mapNotNull null
      PaymentHistory(
        id = obj.optLong("id", 0L),
        loanId = obj.optLong("loanId", 0L),
        amount = obj.optLong("amount", 0L),
        date = obj.optLong("date", System.currentTimeMillis()),
        notes = obj.optString("notes", "")
      )
    }
  }

  private fun parseBankLoansFromJson(root: JSONObject): List<BankLoan> =
    root.optJSONArray("bankLoans")?.let { arr ->
      (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        BankLoan(
          id = o.optLong("id", 0L),
          bankName = o.optString("bankName", ""),
          loanName = o.optString("loanName", ""),
          receivedAmount = o.optLong("receivedAmount", 0L),
          monthlyInstallmentAmount = o.optLong("monthlyInstallmentAmount", 0L),
          numberOfInstallments = o.optInt("numberOfInstallments", 0),
          totalRepayableAmount = o.optLong("totalRepayableAmount", 0L),
          totalInterest = o.optLong("totalInterest", 0L),
          startDate = o.optLong("startDate", 0L),
          description = o.optString("description", ""),
          isSettled = o.optBoolean("isSettled", false)
        )
      }
    } ?: emptyList()

  private fun BackupPayload.toRustPayload(): io.github.mojri.hesabyar.rust.BackupPayload =
    io.github.mojri.hesabyar.rust.BackupPayload(
      version = version,
      timestamp = timestamp,
      appVersion = appVersion,
      transactions =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
      loans =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
      installments =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments),
      bankLoans =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapBankLoans(bankLoans),
      categories =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapCategories(categories)
    )

  suspend fun validateBackup(backup: BackupPayload): BackupValidationResult =
    withContext(dispatcher) {
      if (io.github.mojri.hesabyar.rust.RustBridge.isAvailable) {
        val rustResult =
          io.github.mojri.hesabyar.rust.RustBridge
            .validateBackupPayloadSync(backup.toRustPayload())

        if (rustResult.isValid) {
          BackupValidationResult.Valid
        } else {
          BackupValidationResult.Invalid(rustResult.errors)
        }
      } else {
        // Rust unavailable — fall back to local Kotlin validation
        validateBackupKotlin(backup)
      }
    }

  private fun validateBackupKotlin(backup: BackupPayload): BackupValidationResult {
    val errors = mutableListOf<String>()

    if (backup.version <= 0) errors.add("نسخه پشتیبان نامعتبر است")
    if (backup.appVersion.isBlank()) errors.add("نسخه برنامه پشتیبان نامعتبر است")
    if (backup.timestamp <= 0) errors.add("زمان تهیه پشتیبان نامعتبر است")

    validateBackupTransactions(backup.transactions, errors)
    validateBackupLoans(backup.loans, errors)
    validateBackupInstallments(backup.installments, errors)
    validateBackupCategories(backup.categories, errors)
    validateBackupPaymentHistories(backup.paymentHistories, errors)
    validateBackupBankLoans(backup.bankLoans, errors)

    return if (errors.isEmpty()) {
      BackupValidationResult.Valid
    } else {
      BackupValidationResult.Invalid(errors)
    }
  }

  private fun validateBackupTransactions(
    transactions: List<Transaction>,
    errors: MutableList<String>
  ) {
    transactions.forEachIndexed { i, t ->
      if (t.amount <= 0) errors.add("مبلغ تراکنش #$i نامعتبر است")
      if (t.date <= 0) errors.add("تاریخ تراکنش #$i نامعتبر است")
    }
  }

  private fun validateBackupLoans(
    loans: List<Loan>,
    errors: MutableList<String>
  ) {
    loans.forEachIndexed { i, l ->
      if (l.personName.isBlank()) errors.add("نام شخص وام #$i خالی است")
      if (l.date <= 0) errors.add("تاریخ وام #$i نامعتبر است")
      if (l.originalAmount <= 0) errors.add("مبلغ اولیه وام #$i نامعتبر است")
      if (l.remainingAmount < 0) errors.add("مبلغ باقی‌مانده وام #$i نامعتبر است")
    }
  }

  private fun validateBackupInstallments(
    installments: List<Installment>,
    errors: MutableList<String>
  ) {
    installments.forEachIndexed { i, installment ->
      if (installment.title.isBlank()) errors.add("عنوان قسط #$i خالی است")
      if (installment.amount <= 0) errors.add("مبلغ قسط #$i نامعتبر است")
      if (installment.dueDate <= 0) errors.add("تاریخ سررسید قسط #$i نامعتبر است")
    }
  }

  private fun validateBackupCategories(
    categories: List<Category>,
    errors: MutableList<String>
  ) {
    categories.forEachIndexed { i, category ->
      if (category.name.isBlank()) errors.add("نام دسته‌بندی #$i خالی است")
    }
  }

  private fun validateBackupPaymentHistories(
    payments: List<PaymentHistory>,
    errors: MutableList<String>
  ) {
    payments.forEachIndexed { i, payment ->
      if (payment.amount <= 0) errors.add("مبلغ پرداخت #$i نامعتبر است")
      if (payment.date <= 0) errors.add("تاریخ پرداخت #$i نامعتبر است")
    }
  }

  private fun validateBackupBankLoans(
    bankLoans: List<BankLoan>,
    errors: MutableList<String>
  ) {
    bankLoans.forEachIndexed { i, bankLoan ->
      if (bankLoan.bankName.isBlank()) errors.add("نام بانک وام #$i خالی است")
      if (bankLoan.receivedAmount <= 0) errors.add("مبلغ دریافتی وام #$i نامعتبر است")
      if (bankLoan.numberOfInstallments <= 0) errors.add("تعداد اقساط وام #$i نامعتبر است")
      if (bankLoan.monthlyInstallmentAmount <= 0) errors.add("مبلغ قسط ماهانه وام #$i نامعتبر است")
      if (bankLoan.startDate <= 0) errors.add("تاریخ شروع وام #$i نامعتبر است")
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
    rootJson.put("version", BACKUP_SCHEMA_VERSION)
    rootJson.put("timestamp", System.currentTimeMillis())
    rootJson.put("appVersion", BuildConfig.VERSION_NAME)

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
    val curBankLoans = repository.allBankLoans.firstOrNull() ?: emptyList()
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
          put("type", it.type.name)
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
          put("type", it.type.name)
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
          put("type", it.type.name)
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
          put("bankLoanId", it.bankLoanId ?: JSONObject.NULL)
        }
      )
    }
    rootJson.put("installments", instArray)

    val bankLoansArray = JSONArray()
    curBankLoans.forEach {
      bankLoansArray.put(
        JSONObject().apply {
          put("id", it.id)
          put("bankName", it.bankName)
          put("loanName", it.loanName)
          put("receivedAmount", it.receivedAmount)
          put("monthlyInstallmentAmount", it.monthlyInstallmentAmount)
          put("numberOfInstallments", it.numberOfInstallments)
          put("totalRepayableAmount", it.totalRepayableAmount)
          put("totalInterest", it.totalInterest)
          put("startDate", it.startDate)
          put("description", it.description)
          put("isSettled", it.isSettled)
        }
      )
    }
    rootJson.put("bankLoans", bankLoansArray)

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

  suspend fun importBackupFromFile(backup: BackupPayload) {
    repository.replaceAllFromBackup(backup)
  }

  fun buildBackupSummary(backup: BackupPayload): String =
    "${backup.transactions.size} تراکنش، ${backup.loans.size} وام، ${backup.installments.size} قسط، ${backup.categories.size} دسته‌بندی، ${backup.bankLoans.size} وام بانکی بازیابی شد."

  fun buildExportSummary(
    transCount: Int,
    loanCount: Int,
    instCount: Int,
    catCount: Int,
    bankLoanCount: Int = 0
  ): String = "$transCount تراکنش، $loanCount وام، $instCount قسط، $catCount دسته‌بندی، $bankLoanCount وام بانکی"
}
