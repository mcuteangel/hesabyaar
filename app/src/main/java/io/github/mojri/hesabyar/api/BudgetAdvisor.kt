package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.rust.RustMappers
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object BudgetAdvisor {
  private const val TAG = "BudgetAdvisor"
  private const val IDEAL_SAVINGS_DENOMINATOR = 5

  /**
   * Budget advice entry point used by the app. Delegates to
   * [BudgetAdviceGenerator], which is loans/installments-aware (its offline
   * path surfaces unpaid loans and upcoming installments). Previously this
   * method ignored loans/installments and the generator's richer behavior was
   * unreachable from production code.
   */
  suspend fun getBudgetAdvice(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    config: AiProviderConfig? = null,
    bankLoans: List<BankLoan> = emptyList(),
    aiGenerate: suspend (AiProviderConfig, String, String?, Double) -> AiProvider.ApiResult =
      { cfg, prompt, sys, temp -> AiProvider.generateContent(cfg, prompt, sys, temp) }
  ): String =
    BudgetAdviceGenerator.getBudgetAdvice(
      transactions,
      loans,
      installments,
      categories,
      config,
      bankLoans,
      aiGenerate
    )

  private fun buildCategoryReport(
    transactions: List<Transaction>,
    categories: List<Category>
  ): String {
    val categoriesGroup =
      transactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.categoryId }
        .mapValues { it.value.sumOf { tx -> tx.amount } }

    return categoriesGroup.entries.joinToString("\n") { (catId, sum) ->
      val cat = categories.find { it.id == catId }
      "- ${cat?.name ?: "سایر"}: ${formatAmountClean(sum)}"
    }
  }

  private class TxSummary(
    val income: Long,
    val expense: Long
  ) {
    val balance get() = income - expense
  }

  private fun summarizeTransactions(transactions: List<Transaction>): TxSummary {
    val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    return TxSummary(income, expense)
  }

  private fun formatAmountClean(amount: Long): String = CurrencyFormatter.format(amount)

  /**
   * Resolves the Persian display name for a category key using the production
   * default-category definitions in [Category.DEFAULTS]. Unknown keys fall back
   * to the key itself so no transaction is silently mislabeled.
   */
  fun getPersianCategoryName(key: String): String = Category.DEFAULTS.firstOrNull { it.key == key }?.name ?: key

  // High quality local rules budget advisor for offline mode
  fun getOfflineAdvice(
    transactions: List<Transaction>,
    categories: List<Category>
  ): String {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.getOfflineBudgetAdviceSync(
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapCategories(categories)
      )
    if (rustResult.isNotEmpty()) return rustResult

    // Rust unavailable: serve a local, data-driven fallback instead of a false empty-state.
    return buildLocalOfflineAdvice(transactions, categories)
  }

  // Local, dependency-free budget advice used when the Rust core is unavailable.
  private fun buildLocalOfflineAdvice(
    transactions: List<Transaction>,
    categories: List<Category>
  ): String {
    if (transactions.isEmpty()) {
      return "هنوز تراکنشی در حسابیار ثبت نشده است. لطفا چند تراکنش ثبت کنید تا تحلیل بودجه انجام شود."
    }
    val summary = summarizeTransactions(transactions)
    val categoryReport = buildCategoryReport(transactions, categories)

    val sb = StringBuilder()
    sb.appendLine("### 📊 تحلیل بودجه محلی (آفلاین)")
    sb.appendLine()
    sb.appendLine("**کل درآمد:** ${formatAmountClean(summary.income)}")
    sb.appendLine("**کل هزینه‌ها:** ${formatAmountClean(summary.expense)}")
    sb.appendLine("**تراز باقیمانده:** ${formatAmountClean(summary.balance)}")
    if (categoryReport.isNotEmpty()) {
      sb.appendLine()
      sb.appendLine("**هزینه‌ها به تفکیک دسته‌بندی:**")
      sb.appendLine(categoryReport)
    }
    sb.appendLine()
    when {
      summary.expense > summary.income ->
        sb.appendLine("🚨 **کسری بودجه:** مخارج شما بیش از درآمد است. کاهش هزینه‌های غیرضروری توصیه می‌شود.")
      summary.income > 0 && summary.balance > summary.income / IDEAL_SAVINGS_DENOMINATOR ->
        sb.appendLine("✅ **وضعیت مطلوب:** نرخ پس‌انداز شما مناسب است. ادامه این روند توصیه می‌شود.")
      else ->
        sb.appendLine("⚖️ **وضعیت متعادل:** تلاش کنید نرخ پس‌انداز خود را افزایش دهید.")
    }
    return sb.toString()
  }

  suspend fun getBudgetForecast(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    config: AiProviderConfig? = null,
    bankLoans: List<BankLoan> = emptyList()
  ): String =
    withContext(Dispatchers.IO) {
      AppLogger.d(
        TAG,
        "getBudgetForecast: config=${config?.let {
          "provider=${it.providerType}, isConfigured=${it.isConfigured}"
        } ?: "null"}"
      )
      val providerConfig = config ?: AiProviderConfig()
      if (!providerConfig.isConfigured) {
        AppLogger.w(TAG, "AI provider not configured, using offline local budget forecast")
        return@withContext getOfflineForecast(transactions, loans, installments, bankLoans)
      }

      if (transactions.isEmpty() && installments.isEmpty()) {
        val message =
          "تراکنش یا قسطی در سیستم ثبت نشده است. برای پیش‌بینی دقیق بودجه ماه آینده، لازم است " +
            "تراکنش‌ها یا تعهدات مالی خود را در حسابیار وارد کنید."
        return@withContext message
      }

      val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
      val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
      val activeLoansCount = loans.filter { !it.isSettled }.size + bankLoans.count { !it.isSettled }
      val upcomingInstallments = installments.filter { !it.isPaid }
      val totalUpcomingAmount = upcomingInstallments.sumOf { it.amount }

      val categoryReport = buildCategoryReport(transactions, categories)

      val installmentListPrompt =
        upcomingInstallments.take(15).joinToString("\n") { inst ->
          "- قسط: ${inst.title} | مبلغ: ${formatAmountClean(inst.amount)}"
        }

      val promptText =
        """
            سلام. من یک حسابدار شخصی ایرانی دارم به نام «حسابیار».
            لطفاً تراکنش‌های مالی اخیر و تعهدات مالی پیش‌روی مرا تحلیل کرده و پیش‌بینی وضعیت بودجه و تراز مالی ماه آینده مرا به همراه یک هشدار هوشمند (Smart Alert) صمیمی و روان به زبان فارسی ارائه دهی.

            داده‌های کلی من:
            - پایش درآمد کل جاری: ${formatAmountClean(totalIncome)}
            - پایش مخارج کل جاری: ${formatAmountClean(totalExpense)}
            - اقساط پرداخت نشده در آینده نزدیک: ${upcomingInstallments.size} مورد با مبلغ کل تعهد ${formatAmountClean(
          totalUpcomingAmount
        )}
            - تعداد وام‌های فعال: $activeLoansCount مورد

            خلاصه دسته‌بندی مخارج من:
            $categoryReport

            لیست اقساط پرداخت نشده آینده:
            $installmentListPrompt

            لطفا با لحن صمیمانه، دلسوزانه و انگیزه‌بخش تحلیل کن:
            1. یک پیش‌بینی واقع‌بینانه از تراز نقدی من در ۳۰ روز آینده (با توجه به میانگین دخل و خرج و اقساط پیش رو).
            2. یک «هشدار هوشمند» ارزشمند (بسته به میزان ریسک یا آرامش مالی من در ماه بعد).
            3. حداقل ۲ راهکار کاملاً کاربردی و اختصاصی برای بهبود وضعیت بودجه ماه بعد.
            از ساختار مارک‌داون روان با ایموجی‌های مناسب استفاده کن. در متن نهایی از کلمه‌های انگلیسی استفاده نکن و همه چیز کاملاً فارسی و روان باشد.
        """.trimIndent()

      val systemInstruction =
        "You are Hesabyar's Elite Financial Advisor. Analyze the user's Persian transactions " +
          "carefully and provide smart, structured financial recommendations in beautiful Persian. " +
          "Be friendly, polite, action-oriented, and encouraging."

      when (
        val result =
          AiProvider.generateContent(
            config = providerConfig,
            prompt = promptText,
            systemInstruction = systemInstruction,
            temperature = 0.7
          )
      ) {
        is AiProvider.ApiResult.Success -> {
          val validation =
            io.github.mojri.hesabyar.rust.RustBridge
              .validateAiAdvice(result.text)
          // When the local Rust validator is uninitialized, trust the cloud
          // forecast instead of discarding it on an unavailable engine.
          if (!validation.isValid && io.github.mojri.hesabyar.rust.RustBridge.isAvailable) {
            AppLogger.w(TAG, "AI forecast failed validation, using offline: ${validation.warnings}")
            "⚠️ پیش‌بینی هوش مصنوعی نامعتبر بود. پیش‌بینی محلی شما:\n\n" +
              getOfflineForecast(transactions, loans, installments, bankLoans)
          } else {
            if (validation.wasTruncated) {
              AppLogger.d(TAG, "AI forecast truncated: ${validation.warnings}")
            }
            validation.sanitizedText
          }
        }
        is AiProvider.ApiResult.Failure -> {
          AppLogger.e(TAG, "AI forecast failed: ${result.error}")
          "⚠️ اتصال به سرور ابری انجام نشد یا کلید معتبر نیست. پیش‌بینی محلی شما به شرح زیر است:\n\n" +
            getOfflineForecast(transactions, loans, installments, bankLoans)
        }
      }
    }

  // Local predictive forecasting offline rules fallback
  fun getOfflineForecast(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList()
  ): String {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.getOfflineForecastSync(
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments),
        bankLoans
      )
    if (rustResult.isNotEmpty()) return rustResult

    // Rust unavailable: serve a baseline forecast from local data instead of a false "insufficient data" message.
    return buildLocalOfflineForecast(transactions, loans, installments, bankLoans)
  }

  // Local, dependency-free baseline forecast used when the Rust core is unavailable.
  private fun buildLocalOfflineForecast(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan>
  ): String {
    val summary = summarizeTransactions(transactions)
    val nowMs = System.currentTimeMillis()
    // Window end is derived with Jalali calendar arithmetic so "the next 30 days"
    // spans exactly 30 Jalali days (months are 29–31 days) rather than a fixed
    // 30 × 24h millisecond span. Exclusive end = midnight of the 31st Jalali day
    // from today, so installments due through the end of day 30 are included and
    // later ones are excluded from totalUpcoming.
    val windowEndMs = jalaliPlusDaysMs(nowMs, 31)
    val upcomingInstallments =
      installments.filter { !it.isPaid && it.dueDate >= nowMs && it.dueDate < windowEndMs }
    val totalUpcoming = upcomingInstallments.sumOf { it.amount }
    val activeBankLoans = bankLoans.filter { !it.isSettled }
    val activeLoans = loans.filter { !it.isSettled }
    val totalDebt = activeLoans.sumOf { it.remainingAmount } + activeBankLoans.sumOf { it.totalRepayableAmount }
    val activeDebtCount = activeLoans.size + activeBankLoans.size

    // Parity with the Rust guard (get_offline_forecast): only unsettled CREDITOR
    // loans contribute to the monthly obligation sum (remainingAmount / 12).
    // Unsettled DEBTOR loans and CREDITOR loans with zero monthly obligation
    // must not suppress the "no data" message.
    val unsettledCreditorMonthlyObligation =
      loans
        .filter { !it.isSettled && it.type == LoanType.CREDITOR }
        .fold(0L) { total, loan ->
          val monthly = loan.remainingAmount / 12
          if (monthly > 0L && total > Long.MAX_VALUE - monthly) Long.MAX_VALUE else total + monthly
        }
    val hasNoData =
      transactions.isEmpty() &&
        upcomingInstallments.isEmpty() &&
        unsettledCreditorMonthlyObligation == 0L &&
        activeBankLoans.isEmpty()
    if (hasNoData) {
      return "تراکنش یا قسطی برای پیش‌بینی ثبت نشده است. لطفا اطلاعات مالی خود را وارد کنید."
    }

    val projectedBalance = summary.balance - totalUpcoming
    val sb = StringBuilder()
    sb.appendLine("### 🔮 پیش‌بینی بودجه محلی (آفلاین)")
    sb.appendLine()
    sb.appendLine("**تراز فعلی:** ${formatAmountClean(summary.balance)}")
    sb.appendLine("**اقساط پیش‌رو:** ${upcomingInstallments.size} مورد به مبلغ ${formatAmountClean(totalUpcoming)}")
    if (activeDebtCount > 0) {
      sb.appendLine("**بدهی‌های فعال:** $activeDebtCount مورد به مبلغ ${formatAmountClean(totalDebt)}")
    }
    sb.appendLine()
    sb.appendLine("**تراز پیش‌بینی‌شده (۳۰ روز آینده):** ${formatAmountClean(projectedBalance)}")
    sb.appendLine()
    if (projectedBalance < 0) {
      sb.appendLine("⚠️ **هشدار هوشمند:** تراز پیش‌بینی منفی است. تعدیل هزینه‌ها یا مدیریت اقساط پیش‌رو ضروری است.")
    } else {
      sb.appendLine("✅ **وضعیت پایدار:** تراز پیش‌بینی مثبت است. ادامه روند فعلی توصیه می‌شود.")
    }
    return sb.toString()
  }

  fun calculateDebtToIncomeRatio(
    loans: List<Loan>,
    installments: List<Installment>,
    monthlyIncome: Long,
    bankLoans: List<BankLoan> = emptyList()
  ): Double =
    if (io.github.mojri.hesabyar.rust.RustBridge.isAvailable) {
      io.github.mojri.hesabyar.rust.RustBridge.calculateDebtToIncomeRatioSync(
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments),
        monthlyIncome,
        bankLoans
      )
    } else {
      localCalculateDebtToIncomeRatio(loans, installments, monthlyIncome, bankLoans)
    }

  // Local, dependency-free debt-to-income ratio used when the Rust core is unavailable.
  @Suppress("MagicNumber", "UnusedParameter")
  private fun localCalculateDebtToIncomeRatio(
    loans: List<Loan>,
    installments: List<Installment>,
    monthlyIncome: Long,
    bankLoans: List<BankLoan>
  ): Double {
    val monthlyDebtPayments =
      installments.filter { !it.isPaid }.sumOf { it.amount } +
        loans.filter { !it.isSettled && it.type == LoanType.CREDITOR }.sumOf { it.remainingAmount / 12 }
    return when {
      monthlyIncome <= 0 && monthlyDebtPayments > 0 -> 1.0
      monthlyIncome <= 0 -> 0.0
      else -> monthlyDebtPayments.toDouble() / monthlyIncome.toDouble()
    }
  }

  fun predictTimeToGoal(
    currentSavings: Long,
    monthlySavings: Long,
    goalAmount: Long
  ): Int =
    if (io.github.mojri.hesabyar.rust.RustBridge.isAvailable) {
      io.github.mojri.hesabyar.rust.RustBridge.predictTimeToGoalSync(
        currentSavings,
        monthlySavings,
        goalAmount
      )
    } else {
      localPredictTimeToGoal(currentSavings, monthlySavings, goalAmount)
    }

  // Local, dependency-free time-to-goal prediction used when the Rust core is unavailable.
  private fun localPredictTimeToGoal(
    currentSavings: Long,
    monthlySavings: Long,
    goalAmount: Long
  ): Int {
    if (monthlySavings <= 0) return -1
    val remaining = goalAmount - currentSavings
    return if (remaining > 0) {
      val months = (remaining - 1) / monthlySavings + 1L
      months.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } else {
      0
    }
  }

  fun getPersonalizedAdvice(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList()
  ): String {
    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val balance = totalIncome - totalExpense
    val debtToIncome = calculateDebtToIncomeRatio(loans, installments, totalIncome, bankLoans)
    val savingsRate = if (totalIncome > 0) balance.toDouble() / totalIncome.toDouble() else 0.0
    val upcomingInstallments = installments.filter { !it.isPaid }

    val sb = StringBuilder()
    sb.appendLine("### 📊 تحلیل مالی شخصی شما")
    sb.appendLine()
    sb.appendLine("**نسبت بدهی به درآمد:** ${(debtToIncome * 100).toInt()}٪")
    sb.appendLine("**نرخ پس‌انداز:** ${(savingsRate * 100).toInt()}٪")
    sb.appendLine()

    when {
      debtToIncome > 0.4 -> {
        sb.appendLine("⚠️ **هشدار:** نسبت بدهی به درآمد شما بالا است. توصیه می‌شود:")
        sb.appendLine("- پرداخت بدهی‌های با نرخ سود بالا را در اولویت قرار دهید")
        sb.appendLine("- از گرفتن وام جدید خودداری کنید")
      }
      savingsRate > 0.3 -> {
        sb.appendLine("✅ **تبریک!** نرخ پس‌انداز شما عالی است. توصیه می‌شود:")
        sb.appendLine("- بخشی از پس‌انداز را سرمایه‌گذاری کنید")
        sb.appendLine("- اهداف مالی بلندمدت تعیین کنید")
      }
      savingsRate < 0 -> {
        sb.appendLine("🚨 **کسری بودجه:** مخارج شما بیش از درآمد است!")
        sb.appendLine("- خریدهای غیرضروری را کاهش دهید")
        sb.appendLine("- فوراً یک برنامه کاهش هزینه تنظیم کنید")
      }
      else -> {
        sb.appendLine("⚖️ **وضعیت نسبتاً متعادل:** پس‌انداز شما قابل قبول است.")
        sb.appendLine("- تلاش کنید نرخ پس‌انداز را به بالای ۲۰٪ برسانید")
      }
    }

    if (upcomingInstallments.isNotEmpty()) {
      val totalUpcoming = upcomingInstallments.sumOf { it.amount }
      sb.appendLine()
      sb.appendLine(
        "📅 **اقساط در انتظار پرداخت:** ${upcomingInstallments.size} مورد (${formatAmountClean(totalUpcoming)})"
      )
      upcomingInstallments.take(3).forEach { inst ->
        sb.appendLine("- ${inst.title}: ${formatAmountClean(inst.amount)}")
      }
    }

    return sb.toString()
  }

  fun calculateFinancialHealthScore(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList()
  ): Int =
    if (io.github.mojri.hesabyar.rust.RustBridge.isAvailable) {
      io.github.mojri.hesabyar.rust.RustBridge.calculateFinancialHealthScoreSync(
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapCategories(categories),
        bankLoans
      )
    } else {
      localCalculateFinancialHealthScore(transactions, loans, installments, bankLoans)
    }

  // Local, dependency-free financial health score used when the Rust core is unavailable.
  @Suppress("MagicNumber", "CyclomaticComplexMethod")
  private fun localCalculateFinancialHealthScore(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan>
  ): Int {
    if (transactions.isEmpty()) return 0

    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    var score = 50

    // Savings rate (max +25)
    if (totalIncome > 0) {
      val savingsRate = balance.toDouble() / totalIncome.toDouble()
      score +=
        when {
          savingsRate >= 0.3 -> 25
          savingsRate >= 0.2 -> 20
          savingsRate >= 0.1 -> 10
          savingsRate >= 0 -> 0
          else -> -15
        }
    }

    // Debt-to-income (max +15)
    // Use a trailing-90-day income baseline so all-time accumulated income does
    // not understate the ratio relative to the monthly debt obligations. This
    // mirrors the Rust core's `monthly_income_baseline` scoping.
    val debtRatio =
      calculateDebtToIncomeRatio(loans, installments, localMonthlyIncomeBaseline(transactions), bankLoans)
    score +=
      when {
        debtRatio <= 0.1 -> 15
        debtRatio <= 0.2 -> 10
        debtRatio <= 0.3 -> 5
        debtRatio <= 0.4 -> 0
        else -> -10
      }

    // Category diversification (+10 if spending across 3+ categories)
    val expenseCategories =
      transactions
        .filter { it.type == TransactionType.EXPENSE }
        .map { it.categoryId }
        .distinct()
        .size
    score +=
      when {
        expenseCategories >= 5 -> 10
        expenseCategories >= 3 -> 5
        else -> 0
      }

    return score.coerceIn(0, 100)
  }

  // Trailing-90-day income baseline, mirroring the Rust core's
  // `monthly_income_baseline` so the debt-to-income ratio uses current income.
  // `nowMs` is injectable for deterministic, non-flaky tests.
  internal fun localMonthlyIncomeBaseline(
    transactions: List<Transaction>,
    nowMs: Long = System.currentTimeMillis()
  ): Long {
    val windowStart = nowMs - 90L * 24 * 60 * 60 * 1000
    val recent =
      transactions.filter {
        it.type == TransactionType.INCOME && it.date >= windowStart && it.date <= nowMs
      }
    if (recent.isEmpty()) return 0L
    val oldest = recent.minOf { it.date }
    val msPerDay = 24L * 60 * 60 * 1000
    // Ceiling division into whole days, minimum 1 — matches Rust's integer ceiling
    // path and keeps all arithmetic in Long (no f64 precision loss above 2^53).
    val days = max(1L, (nowMs - oldest + msPerDay - 1) / msPerDay)
    val normalizationDays = days.coerceAtLeast(30L)
    val sum = recent.sumOf { it.amount }
    // sum * 30 can exceed Long.MAX_VALUE; clamp to match Rust's i128 intermediate
    // without risking silent Long overflow.
    val sumTimesThirty = if (sum > Long.MAX_VALUE / 30) Long.MAX_VALUE else sum * 30
    return sumTimesThirty / normalizationDays
  }

  // Adds [days] Jalali days to the date represented by [fromMs] and returns the
  // resulting day's local-midnight timestamp. Uses JalaliCalendarHelper for all
  // calendar arithmetic (month lengths differ across Jalali months) so the
  // 30-day forecast window tracks the Iranian calendar instead of a fixed
  // millisecond span. Falls back to a millisecond offset if the conversion is
  // unavailable.
  @Suppress("MagicNumber")
  private fun jalaliPlusDaysMs(
    fromMs: Long,
    days: Int
  ): Long {
    val today = JalaliCalendarHelper.gregorianToJalali(fromMs)
    var year = today.year
    var month = today.month
    var day = today.day
    var remaining = days
    while (remaining > 0) {
      val daysInMonth = JalaliCalendarHelper.getDaysInMonth(year, month)
      val daysLeftInMonth = daysInMonth - day
      if (remaining <= daysLeftInMonth) {
        day += remaining
        remaining = 0
      } else {
        remaining -= daysLeftInMonth + 1
        day = 1
        if (month == 12) {
          month = 1
          year += 1
        } else {
          month += 1
        }
      }
    }
    return JalaliCalendarHelper.jalaliToGregorian(year, month, day)?.timeInMillis
      ?: fromMs + days.toLong() * 24 * 60 * 60 * 1000
  }
}
