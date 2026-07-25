package io.github.mojri.hesabyar.rust

import androidx.annotation.VisibleForTesting
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.ui.JalaliNativeBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin wrapper around the Rust shared core (hesabyar-core).
 *
 * Not all bridge operations are asynchronous. Async APIs are dispatched on
 * [Dispatchers.Default] (via [rustCall]) to avoid blocking the main thread, while
 * the many synchronous APIs run directly on the caller's thread (via [rustCallSync])
 * with no coroutine hop. If the Rust library failed to load, every function returns
 * a safe fallback.
 *
 * Maintainers: inspect whether you are calling a `suspend`/async variant or a
 * synchronous `*Sync` variant, and handle threading appropriately — synchronous
 * calls can block the calling thread (e.g. the main thread) if invoked from UI code.
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

  @Suppress("Detekt.ThrowsCount", "TooGenericExceptionCaught")
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun <T> rustCallSync(
    fallback: T,
    block: () -> T
  ): T {
    if (!available) return fallback
    return try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw e
    } catch (e: VirtualMachineError) {
      throw e
    } catch (e: RuntimeException) {
      throw e
    } catch (e: Exception) {
      AppLogger.e(TAG, "Rust fallback: fallback used due to ${e.javaClass.simpleName}: ${e.message}", e)
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

  // Returns the native month length, or -1 if the Rust core is unavailable or
  // the call fails. -1 is an explicit failure sentinel (valid Jalali months are
  // always >= 29); callers must fall back to local calendar logic instead of
  // treating it as a real length.
  override fun getJalaliDaysInMonthSync(
    year: Int,
    month: Int
  ): Int = rustCallSync(-1) { HesabyarCore.getJalaliDaysInMonth(year, month) }

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

  @Suppress("TooGenericExceptionCaught")
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
    } catch (e: Exception) {
      if (e is CancellationException) throw e
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
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList()
  ): String =
    rustCallSync("") {
      HesabyarCore.getOfflineForecast(
        transactions,
        loans,
        installments,
        RustMappers.mapBankLoans(bankLoans)
      )
    }

  fun calculateDebtToIncomeRatioSync(
    loans: List<Loan>,
    installments: List<Installment>,
    monthlyIncome: Long,
    bankLoans: List<BankLoan> = emptyList()
  ): Double =
    rustCallSync(0.0) {
      HesabyarCore.calculateDebtToIncomeRatio(
        loans,
        installments,
        monthlyIncome,
        RustMappers.mapBankLoans(bankLoans)
      )
    }

  fun predictTimeToGoalSync(
    currentSavings: Long,
    monthlySavings: Long,
    goalAmount: Long
  ): Int = rustCallSync(0) { HesabyarCore.predictTimeToGoal(currentSavings, monthlySavings, goalAmount) }

  fun calculateFinancialHealthScoreSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList()
  ): Int =
    rustCallSync(0) {
      HesabyarCore.calculateFinancialHealthScore(
        transactions,
        loans,
        installments,
        categories,
        RustMappers.mapBankLoans(bankLoans)
      )
    }

  // ===========================================================================
  // Analytics
  // ===========================================================================

  fun computeAnalyticsSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList()
  ): AnalyticsData? =
    rustCallSync(null) {
      HesabyarCore.computeAnalytics(
        transactions,
        loans,
        installments,
        categories,
        RustMappers.mapBankLoans(bankLoans)
      )
    }

  fun computeDashboardDataSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList()
  ): DashboardData? =
    rustCallSync(null) {
      HesabyarCore.computeDashboardData(
        transactions,
        loans,
        installments,
        RustMappers.mapBankLoans(bankLoans)
      )
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

  fun parseBackupJsonSync(json: String): BackupPayload? = rustCallSync(null) { HesabyarCore.parseBackupJson(json) }

  fun validateBackupPayloadSync(payload: BackupPayload): ValidationResult =
    rustCallSync(ValidationResult(isValid = false, errors = emptyList())) {
      HesabyarCore.validateBackupPayload(payload)
    }

  @Suppress("TooGenericExceptionCaught")
  suspend fun validateBackup(payload: BackupPayload) {
    if (!available) return
    try {
      withContext(Dispatchers.Default) {
        HesabyarCore.validateBackup(payload)
      }
    } catch (e: Exception) {
      // Swallow non-cancellation failures so a Rust/FFI error doesn't break the
      // calling coroutine. A failed validation simply means "not validated".
      if (e is CancellationException) throw e
      AppLogger.e(TAG, "Rust backup validation failed (non-fatal, treated as not validated)", e)
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
