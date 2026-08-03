package io.github.mojri.hesabyar.domain.usecase

import android.util.Log
import io.github.mojri.hesabyar.BuildConfig
import io.github.mojri.hesabyar.auth.BackupCipher
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupSettings
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
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
import java.security.GeneralSecurityException

class ManageBackupUseCase(
  private val repository: HesabyarRepositoryInterface,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  companion object {
    private const val TAG = "ManageBackupUseCase"
    private const val ENCRYPTION_KEY = "sensitiveFieldsEncryption"
    private const val SALT_KEY = "salt"
    private const val ITERATIONS_KEY = "iterations"

    /**
     * Returns true if the backup JSON indicates that sensitive banking fields
     * (cardNumber, accountNumber, iban) are encrypted with a passphrase.
     */
    fun isEncryptedBackup(rootJson: JSONObject): Boolean = rootJson.has(ENCRYPTION_KEY)

    /**
     * Extracts the PBKDF2 salt from the encryption metadata in the backup JSON.
     * @return the hex-encoded salt string, or null if no encryption metadata is present
     */
    fun getEncryptionSalt(rootJson: JSONObject): String? = rootJson.optJSONObject(ENCRYPTION_KEY)?.optString(SALT_KEY)
  }

  /**
   * Decrypts the sensitive banking fields (cardNumber, accountNumber, iban) in all
   * accounts of a parsed [BackupPayload], using the raw JSON to re-read the encrypted
   * values and [passphrase] to derive the decryption key.
   *
   * This method uses the same parsing path (Rust or Kotlin) that [parseBackupJson]
   * originally used — the parsed [backup] was produced by [parseBackupJson] and the
   * raw JSON is only re-read to obtain the encrypted field values, rather than
   * introducing a second independent parser.
   *
   * Raw JSON accounts are matched to parsed accounts by their stable `id` field
   * (present in both the serialized JSON and [io.github.mojri.hesabyar.data.AccountEntity]),
   * NOT by positional index. Index-based matching could attach ciphertext to the
   * wrong account if a raw entry is missing or the array is reordered; id matching
   * makes that impossible. Any malformed raw entry, duplicate id, or parsed account
   * with no raw counterpart fails loudly instead of returning misaligned financial data.
   *
   * @throws GeneralSecurityException if the passphrase is wrong or the ciphertext is tampered
   * @throws IllegalArgumentException if the encrypted data is malformed
   * @throws IllegalStateException if the raw accounts array cannot be matched 1:1 with
   *   the parsed accounts by id (malformed entry, duplicate id, or missing counterpart)
   */
  suspend fun decryptBackupWithPassphrase(
    backup: BackupPayload,
    rootJson: JSONObject,
    passphrase: String
  ): BackupPayload =
    withContext(dispatcher) {
      val salt =
        getEncryptionSalt(rootJson)
          ?: throw IllegalArgumentException("Backup does not contain encryption metadata")
      val key = BackupCipher.deriveKey(passphrase, salt)
      val accountsArray = rootJson.optJSONArray("accounts") ?: return@withContext backup

      // Index raw JSON accounts by stable account id. A raw entry that is not an
      // object, lacks an id, or duplicates another id would make id-based matching
      // ambiguous — reject instead of guessing.
      val encryptedById = HashMap<Long, JSONObject>(accountsArray.length() * 2)
      for (i in 0 until accountsArray.length()) {
        val o =
          accountsArray.optJSONObject(i)
            ?: throw IllegalStateException(
              "Account entry #$i in encrypted backup is not a JSON object"
            )
        if (!o.has("id")) {
          throw IllegalStateException("Account entry #$i in encrypted backup has no id field")
        }
        val id = o.optLong("id")
        if (encryptedById.containsKey(id)) {
          throw IllegalStateException("Duplicate account id $id in encrypted backup")
        }
        encryptedById[id] = o
      }

      // Each parsed account must have exactly one raw counterpart to decrypt.
      // A missing counterpart means the parsed payload and raw JSON diverged —
      // failing beats silently preserving the wrong account's ciphertext.
      val decryptedAccounts =
        backup.accounts.map { account ->
          val raw =
            encryptedById[account.id]
              ?: throw IllegalStateException(
                "Parsed account ${account.id} has no counterpart in encrypted backup"
              )
          account.copy(
            cardNumber = BackupCipher.decryptOrNull(raw.opt("cardNumber"), key),
            accountNumber = BackupCipher.decryptOrNull(raw.opt("accountNumber"), key),
            iban = BackupCipher.decryptOrNull(raw.opt("iban"), key)
          )
        }
      backup.copy(accounts = decryptedAccounts)
    }

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
            paymentHistories =
              rustResult.paymentHistories.map {
                io.github.mojri.hesabyar.rust.RustMappers
                  .fromRustPaymentHistory(it)
              },
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
            accounts =
              rustResult.accounts.map {
                io.github.mojri.hesabyar.rust.RustMappers
                  .fromRustAccount(it)
              },
            settings = parseSettings(rootJson)
          )
        } catch (e: IllegalArgumentException) {
          // Malformed/outdated enum strings — fall back to Kotlin-only parsing
          Log.w(TAG, "Rust→Kotlin mapping failed, falling back to Kotlin parser", e)
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
    } catch (e: org.json.JSONException) {
      Log.w(TAG, "parseRawJson: malformed JSON input", e)
      null
    }

  private fun parseSettings(rootJson: JSONObject?): BackupSettings {
    val obj = rootJson?.optJSONObject("settings") ?: return BackupSettings()
    return BackupSettings(darkMode = obj.optBoolean("darkMode", true))
  }

  /** Absent type → BANK (backward compat); present-but-unknown → OTHER via safeValueOf. */
  private fun parseAccountType(obj: JSONObject): io.github.mojri.hesabyar.data.AccountType {
    val typeStr = obj.optString("type", "")
    return if (typeStr.isEmpty()) {
      io.github.mojri.hesabyar.data.AccountType.BANK
    } else {
      io.github.mojri.hesabyar.data.AccountType
        .safeValueOf(typeStr)
    }
  }

  /** Returns [JSONObject.NULL] as Kotlin null, or the string value if present and non-null. */
  private fun JSONObject.nullableString(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null

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

  @Suppress("TooGenericExceptionCaught") // Safety net: opt* returns defaults but constructor params may NPE
  private fun parseBackupJsonKotlin(jsonString: String): BackupPayload? {
    val root = parseRawJson(jsonString) ?: return null
    return try {
      BackupPayload(
        version = root.optInt("version", BuildConfig.BACKUP_SCHEMA_VERSION),
        timestamp = root.optLong("timestamp", System.currentTimeMillis()),
        appVersion = root.optString("appVersion", BuildConfig.VERSION_NAME),
        transactions = parseTransactions(root),
        loans = parseLoans(root),
        installments = parseInstallmentsFromJson(root),
        paymentHistories = parsePaymentHistories(root),
        categories = parseCategories(root),
        bankLoans = parseBankLoansFromJson(root),
        accounts = parseAccountsFromJson(root),
        settings = parseSettings(root)
      )
    } catch (e: NumberFormatException) {
      Log.w(TAG, "Kotlin backup parse: malformed number in backup JSON", e)
      null
    } catch (e: IllegalArgumentException) {
      Log.w(TAG, "Kotlin backup parse: invalid enum value", e)
      null
    } catch (e: org.json.JSONException) {
      Log.w(TAG, "Kotlin backup parse: malformed JSON structure", e)
      null
    } catch (e: NullPointerException) {
      // Safety net: opt* methods return defaults but constructor params may still NPE
      Log.w(TAG, "Kotlin backup parse: null field in backup JSON", e)
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
          installmentId = o.optLong("installmentId", 0L).takeIf { it != 0L },
          accountId = o.optLong("accountId", DEFAULT_ACCOUNT_ID),
          destinationAccountId =
            if (o.has("destinationAccountId") && !o.isNull("destinationAccountId")) {
              o.optLong("destinationAccountId")
            } else {
              null
            }
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

  private fun parseAccountsFromJson(root: JSONObject): List<io.github.mojri.hesabyar.data.AccountEntity> =
    root.optJSONArray("accounts")?.let { arr ->
      val now = System.currentTimeMillis()
      (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        io.github.mojri.hesabyar.data.AccountEntity(
          id = o.optLong("id", 0L),
          name = o.optString("name", ""),
          type = parseAccountType(o),
          bankName = o.nullableString("bankName"),
          cardNumber = o.nullableString("cardNumber"),
          accountNumber = o.nullableString("accountNumber"),
          iban = o.nullableString("iban"),
          initialBalance = o.optLong("initialBalance", 0L),
          color = o.optLong("color", AccountEntity.DEFAULT_COLOR),
          icon = o.nullableString("icon"),
          isArchived = o.optBoolean("isArchived", false),
          displayOrder = o.optInt("displayOrder", 0),
          createdAt = o.optLong("createdAt", now),
          updatedAt = o.optLong("updatedAt", now)
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
      paymentHistories =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapPaymentHistories(paymentHistories),
      bankLoans =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapBankLoans(bankLoans),
      categories =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapCategories(categories),
      accounts =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapAccounts(accounts)
    )

  suspend fun validateBackup(backup: BackupPayload): BackupValidationResult =
    withContext(dispatcher) {
      if (io.github.mojri.hesabyar.rust.RustBridge.isAvailable) {
        try {
          val rustResult =
            io.github.mojri.hesabyar.rust.RustBridge
              .validateBackupPayloadSync(backup.toRustPayload())

          if (rustResult.isValid) {
            BackupValidationResult.Valid
          } else {
            BackupValidationResult.Invalid(rustResult.errors)
          }
        } catch (e: IllegalArgumentException) {
          // Mapping to Rust payload failed (e.g. from mapAccounts/mapCategories);
          // fall back to Kotlin validation instead of escaping as an unhandled exception.
          Log.w(TAG, "Rust→Kotlin mapping failed during validation, falling back to Kotlin", e)
          validateBackupKotlin(backup)
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
    validateBackupAccounts(backup.accounts, errors)

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

  private fun validateBackupAccounts(
    accounts: List<io.github.mojri.hesabyar.data.AccountEntity>,
    errors: MutableList<String>
  ) {
    accounts.forEachIndexed { i, account ->
      if (account.name.isBlank()) errors.add("نام حساب #$i خالی است")
      // createdAt == 0 is a legacy sentinel from the v6→v7 migration
      // (MIGRATION_6_7 used DEFAULT 0 for accounts that existed before
      // timestamps were tracked). Accept it as valid.
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

  // TODO(automatic-backups): Background/automatic backups cannot prompt for a passphrase
  // interactively. When automatic backups are implemented, they will need either a persisted
  // passphrase (protected via EncryptedSharedPreferences) or a default no-encryption fallback,
  // since the user cannot be prompted during a headless export.
  suspend fun exportBackupJson(
    isDarkMode: Boolean = true,
    passphrase: String? = null
  ): JSONObject {
    val rootJson = JSONObject()
    rootJson.put("version", BuildConfig.BACKUP_SCHEMA_VERSION)
    rootJson.put("timestamp", System.currentTimeMillis())
    rootJson.put("appVersion", BuildConfig.VERSION_NAME)

    // Derive encryption key if passphrase is provided
    val encryptionKey =
      if (passphrase != null) {
        val salt = BackupCipher.generateSalt()
        rootJson.put(
          ENCRYPTION_KEY,
          JSONObject().apply {
            put(SALT_KEY, salt)
            put(ITERATIONS_KEY, 600_000)
          }
        )
        BackupCipher.deriveKey(passphrase, salt)
      } else {
        null
      }

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
    val curAccounts = repository.allAccounts.firstOrNull() ?: emptyList()

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
          put("accountId", it.accountId)
          put("destinationAccountId", it.destinationAccountId ?: JSONObject.NULL)
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

    val accountsArray = JSONArray()
    curAccounts.forEach {
      accountsArray.put(
        JSONObject().apply {
          put("id", it.id)
          put("name", it.name)
          put("type", it.type.name)
          put("bankName", it.bankName ?: JSONObject.NULL)
          // When a passphrase is provided, encrypt sensitive banking identifiers;
          // otherwise store them as plaintext.  Encrypted values are base64-encoded
          // AES-GCM ciphertext and pass through Rust/serde deserialization as-is
          // (they're still Option<String>).
          if (encryptionKey != null) {
            put("cardNumber", BackupCipher.encryptOrNull(it.cardNumber, encryptionKey))
            put("accountNumber", BackupCipher.encryptOrNull(it.accountNumber, encryptionKey))
            put("iban", BackupCipher.encryptOrNull(it.iban, encryptionKey))
          } else {
            put("cardNumber", it.cardNumber ?: JSONObject.NULL)
            put("accountNumber", it.accountNumber ?: JSONObject.NULL)
            put("iban", it.iban ?: JSONObject.NULL)
          }
          put("initialBalance", it.initialBalance)
          put("color", it.color)
          put("icon", it.icon ?: JSONObject.NULL)
          put("isArchived", it.isArchived)
          put("displayOrder", it.displayOrder)
          put("createdAt", it.createdAt)
          put("updatedAt", it.updatedAt)
        }
      )
    }
    rootJson.put("accounts", accountsArray)

    return rootJson
  }

  suspend fun importBackupFromFile(backup: BackupPayload) {
    repository.replaceAllFromBackup(backup)
  }

  fun buildBackupSummary(backup: BackupPayload): String =
    "${backup.transactions.size} تراکنش، ${backup.loans.size} وام، ${backup.installments.size} قسط، ${backup.categories.size} دسته‌بندی، ${backup.bankLoans.size} وام بانکی، ${backup.accounts.size} حساب بازیابی شد."

  fun buildExportSummary(
    transCount: Int,
    loanCount: Int,
    instCount: Int,
    catCount: Int,
    bankLoanCount: Int = 0,
    accountCount: Int = 0
  ): String =
    "$transCount تراکنش، $loanCount وام، $instCount قسط، $catCount دسته‌بندی، $bankLoanCount وام بانکی، $accountCount حساب"
}
