package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.HesabyarApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin wrapper around the Rust shared core (hesabyar-core).
 *
 * All Rust FFI calls are dispatched on [Dispatchers.Default] to avoid blocking the main thread.
 * If the Rust library failed to load, every function returns a safe fallback.
 *
 * Naming convention: functions mirror the Rust API 1:1.
 * Generated UniFFI bindings live under [HesabyarCore].
 */
object RustBridge {
  private const val TAG = "RustBridge"

  @Volatile
  private var selfInitialized = false

  private val available: Boolean
    get() {
      if (HesabyarApp.isRustInitialized()) return true
      if (selfInitialized) return true
      return trySelfInit()
    }

  private fun trySelfInit(): Boolean {
    synchronized(this) {
      if (selfInitialized) return true
      return try {
        HesabyarCore.initialize()
        selfInitialized = true
        true
      } catch (_: Throwable) {
        false
      }
    }
  }

  private suspend fun <T> rustCall(
    fallback: T,
    block: () -> T
  ): T {
    if (!available) return fallback
    return withContext(Dispatchers.Default) { block() }
  }

  private fun <T> rustCallSync(
    fallback: T,
    block: () -> T
  ): T {
    if (!available) return fallback
    return try {
      block()
    } catch (_: Exception) {
      fallback
    }
  }

  // ===========================================================================
  // Calendar
  // ===========================================================================

  fun gregorianToJalaliSync(timestampMs: Long): Long = rustCallSync(0L) { HesabyarCore.gregorianToJalali(timestampMs) }

  fun jalaliToGregorianSync(
    year: Int,
    month: Int,
    day: Int
  ): Long = rustCallSync(0L) { HesabyarCore.jalaliToGregorian(year, month, day) }

  fun getJalaliDaysInMonthSync(
    year: Int,
    month: Int
  ): Int = rustCallSync(30) { HesabyarCore.getJalaliDaysInMonth(year, month) }

  fun isJalaliLeapYearSync(year: Int): Boolean = rustCallSync(false) { HesabyarCore.isJalaliLeapYear(year) }

  // ===========================================================================
  // Currency
  // ===========================================================================

  fun formatCurrencySync(
    rial: Long,
    unit: CurrencyUnit
  ): String = rustCallSync("") { HesabyarCore.formatCurrency(rial, unit) }

  fun toRialSync(
    displayValue: Long,
    unit: CurrencyUnit
  ): Long = rustCallSync(0L) { HesabyarCore.toRial(displayValue, unit) }

  fun fromRialSync(
    rial: Long,
    unit: CurrencyUnit
  ): Long = rustCallSync(0L) { HesabyarCore.fromRial(rial, unit) }

  fun formatNumberSync(value: Long): String = rustCallSync("") { HesabyarCore.formatNumber(value) }

  // ===========================================================================
  // Parser
  // ===========================================================================

  fun parseSentenceOfflineSync(rawSentence: String): ParsedResult? =
    rustCallSync(null) {
      try {
        HesabyarCore.parseSentenceOffline(rawSentence)
      } catch (_: Exception) {
        null
      }
    }

  fun inferExpenseCategorySync(sentence: String): CategoryGuess =
    rustCallSync(CategoryGuess(category = "Other", subcategory = "")) {
      HesabyarCore.inferExpenseCategory(sentence)
    }

  fun containsMoneySync(sentence: String): Boolean = rustCallSync(false) { HesabyarCore.containsMoney(sentence) }

  fun normalizeMoneyTextSync(text: String): String = rustCallSync(text) { HesabyarCore.normalizeMoneyText(text) }

  fun parsePersianAmountSync(sentence: String): Long = rustCallSync(0L) { HesabyarCore.parsePersianAmount(sentence) }

  fun parseAmountSync(
    sentence: String,
    shorthandMode: Boolean
  ): Long =
    rustCallSync(0L) {
      io.github.mojri.hesabyar.rust
        .parseAmount(sentence, shorthandMode)
    }

  fun preprocessPersianTextSync(text: String): String = rustCallSync(text) { HesabyarCore.preprocessPersianText(text) }

  // ===========================================================================
  // AI validation
  // ===========================================================================

  suspend fun validateAiAdvice(text: String): AdviceValidation =
    rustCall(
      AdviceValidation(
        isValid = false,
        sanitizedText = text,
        warnings = listOf("Rust not available"),
        wasTruncated = false,
      )
    ) { HesabyarCore.validateAiAdvice(text) }

  fun parseAiTransactionJsonSync(json: String): AiParsedTransaction? =
    rustCallSync(null) {
      try {
        HesabyarCore.parseAiTransactionJson(json)
      } catch (_: Exception) {
        null
      }
    }

  // ===========================================================================
  // Validation (all throw on error)
  // ===========================================================================

  fun validateTransactionSync(transaction: Transaction): Boolean =
    rustCallSync(false) {
      try {
        HesabyarCore.validateTransaction(transaction)
        true
      } catch (_: Exception) {
        false
      }
    }

  fun validateLoanSync(loan: Loan): Boolean =
    rustCallSync(false) {
      try {
        HesabyarCore.validateLoan(loan)
        true
      } catch (_: Exception) {
        false
      }
    }

  fun validateInstallmentSync(installment: Installment): Boolean =
    rustCallSync(false) {
      try {
        HesabyarCore.validateInstallment(installment)
        true
      } catch (_: Exception) {
        false
      }
    }

  fun validateParsedResultSync(result: ParsedResult): Boolean =
    rustCallSync(false) {
      try {
        HesabyarCore.validateParsedResult(result)
        true
      } catch (_: Exception) {
        false
      }
    }

  // ===========================================================================
  // Budget
  // ===========================================================================

  fun getOfflineBudgetAdviceSync(
    transactions: List<Transaction>,
    categories: List<Category>
  ): String = rustCallSync("") { HesabyarCore.getOfflineBudgetAdvice(transactions, categories) }

  fun getOfflineForecastSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>
  ): String = rustCallSync("") { HesabyarCore.getOfflineForecast(transactions, loans, installments) }

  fun calculateDebtToIncomeRatioSync(
    loans: List<Loan>,
    installments: List<Installment>,
    monthlyIncome: Long
  ): Double = rustCallSync(0.0) { HesabyarCore.calculateDebtToIncomeRatio(loans, installments, monthlyIncome) }

  fun predictTimeToGoalSync(
    currentSavings: Long,
    monthlySavings: Long,
    goalAmount: Long
  ): Int = rustCallSync(0) { HesabyarCore.predictTimeToGoal(currentSavings, monthlySavings, goalAmount) }

  fun calculateFinancialHealthScoreSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): Int =
    rustCallSync(0) {
      HesabyarCore.calculateFinancialHealthScore(transactions, loans, installments, categories)
    }

  // ===========================================================================
  // Analytics
  // ===========================================================================

  fun computeAnalyticsSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): AnalyticsData? =
    rustCallSync(null) {
      try {
        HesabyarCore.computeAnalytics(transactions, loans, installments, categories)
      } catch (_: Exception) {
        null
      }
    }

  fun computeDashboardDataSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>
  ): DashboardData? =
    rustCallSync(null) {
      try {
        HesabyarCore.computeDashboardData(transactions, loans, installments)
      } catch (_: Exception) {
        null
      }
    }

  // ===========================================================================
  // Search
  // ===========================================================================

  fun searchTransactionsSync(
    transactions: List<Transaction>,
    query: SearchQuery
  ): SearchResponse =
    rustCallSync(
      SearchResponse(results = emptyList(), totalCount = 0L, totalAmount = 0L)
    ) { HesabyarCore.searchTransactions(transactions, query) }

  // ===========================================================================
  // Backup
  // ===========================================================================

  fun parseBackupJsonSync(json: String): BackupPayload? =
    rustCallSync(null) {
      try {
        HesabyarCore.parseBackupJson(json)
      } catch (_: Exception) {
        null
      }
    }

  fun validateBackupPayloadSync(payload: BackupPayload): ValidationResult =
    rustCallSync(ValidationResult(isValid = false, errors = emptyList())) {
      HesabyarCore.validateBackupPayload(payload)
    }

  suspend fun validateBackup(payload: BackupPayload) {
    if (!available) return
    withContext(Dispatchers.Default) {
      HesabyarCore.validateBackup(payload)
    }
  }

  fun exportBackupJsonSync(payload: BackupPayload): String =
    rustCallSync("") {
      try {
        HesabyarCore.exportBackupJson(payload)
      } catch (_: Exception) {
        ""
      }
    }

  fun buildEncryptedBackupFileSync(
    json: String,
    key: ByteArray
  ): ByteArray? =
    rustCallSync(null) {
      try {
        HesabyarCore.buildEncryptedBackupFile(json, key)
      } catch (_: Exception) {
        null
      }
    }

  fun parseEncryptedBackupFileSync(
    data: ByteArray,
    key: ByteArray
  ): String? =
    rustCallSync(null) {
      try {
        HesabyarCore.parseEncryptedBackupFile(data, key)
      } catch (_: Exception) {
        null
      }
    }

  // ===========================================================================
  // Checksums
  // ===========================================================================

  fun computeChecksumSync(data: ByteArray): String = rustCallSync("") { HesabyarCore.computeChecksum(data) }

  fun verifyChecksumSync(
    data: ByteArray,
    expected: String
  ): Boolean = rustCallSync(false) { HesabyarCore.verifyChecksum(data, expected) }

  // ===========================================================================
  // Excel export
  // ===========================================================================

  fun generateExcel(workbook: WorkbookData): ByteArray? =
    rustCallSync(null) {
      try {
        HesabyarCore.generateExcel(workbook)
      } catch (_: Exception) {
        null
      }
    }
}
