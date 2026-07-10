package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.ui.JalaliNativeBridge
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
object RustBridge : JalaliNativeBridge {
  private const val TAG = "RustBridge"

  private val available: Boolean
    get() = HesabyarApp.ensureRustInitialized()

  /** Public view of [available] so callers can decide whether a local
   *  validation result reflects a real check or merely an uninitialized engine. */
  val isAvailable: Boolean get() = available

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
    } catch (_: Throwable) {
      fallback
    }
  }

  // Runs a Unit-returning Rust validator; returns true if it completes without
  // throwing (i.e. valid), false if Rust is unavailable or validation fails.
  private fun validateBoolean(block: () -> Unit): Boolean =
    rustCallSync(false) {
      block()
      true
    }

  // ===========================================================================
  // Calendar
  // ===========================================================================

  override fun gregorianToJalaliSync(timestampMs: Long): Long =
    rustCallSync(0L) {
      HesabyarCore.gregorianToJalali(timestampMs)
    }

  override fun jalaliToGregorianSync(
    year: Int,
    month: Int,
    day: Int
  ): Long = rustCallSync(Long.MIN_VALUE) { HesabyarCore.jalaliToGregorian(year, month, day) }

  override fun getJalaliDaysInMonthSync(
    year: Int,
    month: Int
  ): Int = rustCallSync(30) { HesabyarCore.getJalaliDaysInMonth(year, month) }

  override fun isJalaliLeapYearSync(year: Int): Boolean = rustCallSync(false) { HesabyarCore.isJalaliLeapYear(year) }

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
    rustCallSync(null) { HesabyarCore.parseSentenceOffline(rawSentence) }

  fun inferExpenseCategorySync(sentence: String): CategoryGuess =
    rustCallSync(CategoryGuess(category = "Other", subcategory = "")) {
      HesabyarCore.inferExpenseCategory(sentence)
    }

  fun containsMoneySync(sentence: String): Boolean = rustCallSync(false) { HesabyarCore.containsMoney(sentence) }

  fun normalizeMoneyTextSync(text: String): String = rustCallSync(text) { HesabyarCore.normalizeMoneyText(text) }

  fun parsePersianAmountSync(sentence: String): Long = rustCallSync(0L) { HesabyarCore.parsePersianAmount(sentence) }

  fun preprocessPersianTextSync(text: String): String = rustCallSync(text) { HesabyarCore.preprocessPersianText(text) }

  // ===========================================================================
  // AI validation
  // ===========================================================================

  suspend fun validateAiAdvice(text: String): AdviceValidation {
    if (!available) {
      return AdviceValidation(
        isValid = false,
        sanitizedText = text,
        warnings = listOf("Rust not available"),
        wasTruncated = false,
      )
    }
    return try {
      withContext(Dispatchers.Default) { HesabyarCore.validateAiAdvice(text) }
    } catch (_: Throwable) {
      AdviceValidation(
        isValid = false,
        sanitizedText = text,
        warnings = listOf("Rust validation failed"),
        wasTruncated = false,
      )
    }
  }

  fun parseAiTransactionJsonSync(json: String): AiParsedTransaction? =
    rustCallSync(null) { HesabyarCore.parseAiTransactionJson(json) }

  // ===========================================================================
  // Validation (all throw on error)
  // ===========================================================================

  fun validateTransactionSync(transaction: Transaction): Boolean =
    validateBoolean { HesabyarCore.validateTransaction(transaction) }

  fun validateLoanSync(loan: Loan): Boolean = validateBoolean { HesabyarCore.validateLoan(loan) }

  fun validateInstallmentSync(installment: Installment): Boolean =
    validateBoolean { HesabyarCore.validateInstallment(installment) }

  fun validateParsedResultSync(result: ParsedResult): Boolean =
    validateBoolean { HesabyarCore.validateParsedResult(result) }

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
    rustCallSync(null) { HesabyarCore.computeAnalytics(transactions, loans, installments, categories) }

  fun computeDashboardDataSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>
  ): DashboardData? = rustCallSync(null) { HesabyarCore.computeDashboardData(transactions, loans, installments) }

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

  fun parseBackupJsonSync(json: String): BackupPayload? = rustCallSync(null) { HesabyarCore.parseBackupJson(json) }

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

  fun exportBackupJsonSync(payload: BackupPayload): String = rustCallSync("") { HesabyarCore.exportBackupJson(payload) }

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

  fun generateExcel(workbook: WorkbookData): ByteArray? = rustCallSync(null) { HesabyarCore.generateExcel(workbook) }
}
