package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Budget advice generation — extracted from [GeminiParser] to keep
 * both classes under the detekt LargeClass threshold.
 */
internal object BudgetAdviceGenerator {
  private const val TAG = "BudgetAdviceGenerator"
  private const val LOW_SAVINGS_THRESHOLD = 10.0
  private const val MAX_RECENT_TRANSACTIONS = 10

  private const val LOAN_ADVICE =
    "🤝 **امور مالی اشخاص (قرض و وام)**: شما دارای %d مورد " +
      "تسویه نشده هستید. تسویه به موقع دیون و پیگیری منظم " +
      "طلب‌ها از اشخاص به پایداری روابط کاری و شخصی شما یاری " +
      "می‌رساند.\n\n"
  private const val INSTALLMENT_ADVICE =
    "📅 **بدهی‌های سررسیددار (اقساط)**: شما در پیش‌رو %d قسط " +
      "پرداخت‌نشده به ارزش مجموع %d تومان دارید. توصیه می‌شود " +
      "مبلغ اقساط را زودتر کنار بگذارید تا سررسید آن‌ها باعث " +
      "جریمه یا فشار مالی نشود."
  private const val EMPTY_TRANSACTIONS_MSG =
    "شما هنوز هیچ تراکنشی ثبت نکرده‌اید! اولین تراکنش‌های " +
      "دریافتی یا مخارج خود را ثبت کنید تا حسابیار بتواند " +
      "رفتار مالی شما را تحلیل کند."
  private const val NO_INCOME_MSG =
    "📉 **جذب و ثبت درآمد**: شما تاکنون درآمد چشمگیری ثبت " +
      "نکرده‌اید اما هزینه‌های ثبت شده وجود دارد. تلاش کنید " +
      "درآمدهای خود را نیز ثبت کنید تا نسبت درآمد به مخارج " +
      "دقیق‌تر محاسبه شود.\n\n"

  private data class TransactionTotals(
    val income: Long = 0L,
    val expense: Long = 0L,
    val categoryTotals: Map<Long, Long> = emptyMap()
  )

  private fun calculateTransactionTotals(transactions: List<Transaction>): TransactionTotals {
    val cats = mutableMapOf<Long, Long>()
    val (income, expense) =
      transactions.fold(0L to 0L) { (inc, exp), t ->
        when (t.type) {
          TransactionType.INCOME -> inc + t.amount to exp
          TransactionType.EXPENSE -> {
            cats[t.categoryId] =
              (cats[t.categoryId] ?: 0L) + t.amount
            inc to exp + t.amount
          }
          else -> inc to exp
        }
      }
    return TransactionTotals(income, expense, cats)
  }

  suspend fun getBudgetAdvice(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    config: AiProviderConfig? = null
  ): String? =
    withContext(Dispatchers.IO) {
      val cfg = config ?: AiProviderConfig()
      AppLogger.d(TAG, "getBudgetAdvice: configured=${cfg.isConfigured}")
      if (!cfg.isConfigured) {
        AppLogger.w(TAG, "AI not configured, using offline fallback")
        return@withContext getBudgetAdviceOffline(
          transactions,
          loans,
          installments,
          categories
        )
      }
      val summary =
        buildDataSummary(
          transactions,
          loans,
          installments,
          categories
        )
      val prompt =
        "در اینجا اطلاعات مالی من برای تحلیل و توصیه آمده است:\n$summary"
      val result =
        AiProvider.generateContent(
          config = cfg,
          prompt = prompt,
          systemInstruction = ADVICE_SYSTEM_PROMPT,
          temperature = 0.6
        )
      handleAdviceResult(
        result,
        transactions,
        loans,
        installments,
        categories
      )
    }

  private fun buildDataSummary(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): String {
    val totals = calculateTransactionTotals(transactions)
    val balance = totals.income - totals.expense
    return StringBuilder()
      .apply {
        appendLine("تعداد کل تراکنش‌ها: ${transactions.size}")
        appendLine("کل درآمد ثبت شده: ${totals.income} تومان")
        appendLine("کل مخارج ثبت شده: ${totals.expense} تومان")
        appendLine("تراز باقیمانده (پس‌انداز): $balance تومان")
        appendCategoryBreakdown(this, totals.categoryTotals, categories)
        appendActiveDebtsToSummary(this, loans, installments)
        appendRecentTransactions(this, transactions, categories)
      }.toString()
  }

  private fun appendCategoryBreakdown(
    sb: StringBuilder,
    categoryTotals: Map<Long, Long>,
    categories: List<Category>
  ) {
    sb.appendLine("\nتفکیک هزینه‌ها به دسته‌بندی:")
    categoryTotals.forEach { (catId, amt) ->
      val cat = categories.find { it.id == catId }
      sb.appendLine("- ${cat?.name ?: "سایر"}: $amt تومان")
    }
  }

  private fun appendActiveDebtsToSummary(
    sb: StringBuilder,
    loans: List<Loan>,
    installments: List<Installment>
  ) {
    val activeLoans = loans.filter { !it.isSettled }
    if (activeLoans.isNotEmpty()) {
      sb.appendLine("\nوام‌ها و قرض‌های فعال:")
      activeLoans.forEach { loan ->
        val role =
          if (loan.type == LoanType.DEBTOR) {
            "طلبکار (قرض دادید به)"
          } else {
            "بدهکار (قرض گرفتید از)"
          }
        sb.appendLine(
          "- ${loan.personName} ($role): " +
            "کل ${loan.originalAmount} تومان | " +
            "مانده ${loan.remainingAmount} تومان"
        )
      }
    }
    val activeInstallments = installments.filter { !it.isPaid }
    if (activeInstallments.isNotEmpty()) {
      sb.appendLine("\nاقساط پرداخت نشده:")
      activeInstallments.forEach { inst ->
        sb.appendLine("- ${inst.title}: ${inst.amount} تومان")
      }
    }
  }

  private fun appendRecentTransactions(
    sb: StringBuilder,
    transactions: List<Transaction>,
    categories: List<Category>
  ) {
    if (transactions.isEmpty()) return
    sb.appendLine("\nتراکنش‌های اخیر:")
    transactions
      .sortedByDescending { it.date }
      .take(MAX_RECENT_TRANSACTIONS)
      .forEach { t ->
        val sign =
          if (t.type == TransactionType.INCOME) {
            "آمد"
          } else {
            "رفت"
          }
        val cat = categories.find { it.id == t.categoryId }
        sb.appendLine(
          "- ${t.description} " +
            "(${cat?.name ?: "سایر"}): " +
            "${t.amount} تومان [$sign]"
        )
      }
  }

  private suspend fun handleAdviceResult(
    result: AiProvider.ApiResult,
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): String? {
    val fallback = {
      getBudgetAdviceOffline(
        transactions,
        loans,
        installments,
        categories
      )
    }
    return when (result) {
      is AiProvider.ApiResult.Success -> {
        AppLogger.d(TAG, "AI advice outcome received")
        val validation =
          io.github.mojri.hesabyar.rust.RustBridge
            .validateAiAdvice(result.text)
        if (!validation.isValid) {
          AppLogger.w(
            TAG,
            "AI advice validation failed: ${validation.warnings}"
          )
          return fallback()
        }
        if (validation.wasTruncated) {
          AppLogger.d(TAG, "AI advice truncated: ${validation.warnings}")
        }
        validation.sanitizedText
      }
      is AiProvider.ApiResult.Failure -> {
        AppLogger.e(TAG, "AI budget advice failed: ${result.error}")
        fallback()
      }
    }
  }

  private val ADVICE_SYSTEM_PROMPT =
    """
    You are an expert Iranian financial advisor and budget planner.
    Inspect the user's financial ledger data (in Toman) and provide
    personalized, highly practical, smart budget recommendations in
    Persian. Give actionable recommendations to optimize expenses,
    manage loans, and improve savings.
    Adhere to these rules:
    1. Use direct, polite, friendly, professional Persian.
    2. Split suggestions into 3-4 structured bullet points.
    3. Highlight key categories of concern if they have high spending.
    4. Reference loans or upcoming installments to help prioritize.
    5. Present prices in Toman with thousands separators.
    6. Keep the response concise, personalized, and motivating.
    Format with neat markdown. Total length ~150-200 words.
    """.trimIndent()

  fun getBudgetAdviceOffline(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): String {
    val totals = calculateTransactionTotals(transactions)
    val balance = totals.income - totals.expense
    val sb = StringBuilder()
    sb.append("💡 **تحلیلگر و مشاور مالی هوشمند (آفلاین)**\n\n")
    if (transactions.isEmpty()) {
      sb.append(EMPTY_TRANSACTIONS_MSG)
      // Retain debt-related advice when the ledger is empty but the user still
      // has active unpaid obligations (loans or installments). Only return early
      // when there are genuinely no relevant debt records to surface.
      val hasActiveDebts =
        loans.any { !it.isSettled } || installments.any { !it.isPaid }
      if (!hasActiveDebts) return sb.toString()
      sb.append("\n\n")
    } else {
      sb.append(
        "بر اساس مداقه بر تراکنش‌های ثبت شده، " +
          "چند توصیه عملی برای شما داریم:\n\n"
      )
      sb.append(formatSavingsAdvice(totals.income, balance))
      appendCategoryAdvice(
        sb,
        totals.categoryTotals,
        totals.expense,
        categories
      )
    }
    appendLoanAdvice(sb, loans, installments)
    return sb.toString()
  }

  private fun formatSavingsAdvice(
    incomeTotal: Long,
    balance: Long
  ): String {
    if (incomeTotal <= 0) {
      return NO_INCOME_MSG
    }
    val rate = balance * 100.0 / incomeTotal
    return when {
      rate < 0 ->
        "⚠️ **کنترل تراز مخارج**: متاسفانه مخارج شما در این " +
          "دوره بیش از درآمدتان بوده است " +
          "(${String.format("%.1f", rate)}٪ کسری). " +
          "توصیه می‌شود خریدهای غیرضروری خود را به زمان بهتری " +
          "موکول کرده و روی کالاهای اساسی متمرکز شوید.\n\n"
      rate < LOW_SAVINGS_THRESHOLD ->
        "📉 **بهبود نرخ پس‌انداز**: شما حدود " +
          "${String.format("%.1f", rate)}٪ از درآمد خود را " +
          "پس‌انداز کرده‌اید. برای داشتن پشتوانه مالی مطمئن‌تر، " +
          "تلاش کنید با کاهش مخارج کوچکِ روزمره، " +
          "این نسبت را به حداقل ۲۰٪ برسانید.\n\n"
      else ->
        "🎉 **عملکرد عالی پس‌انداز**: آفرین! شما توانسته‌اید " +
          "بیش از ${String.format("%.1f", rate)}٪ از درآمد خود را " +
          "پس‌انداز کنید. این روند فوق‌العاده را برای " +
          "ثروت‌آفرینی بیشتر ادامه دهید.\n\n"
    }
  }

  private fun appendCategoryAdvice(
    sb: StringBuilder,
    categoryTotals: Map<Long, Long>,
    expenseTotal: Long,
    categories: List<Category>
  ) {
    val worstId = categoryTotals.maxByOrNull { it.value }?.key
    val worst = categories.find { it.id == worstId }
    if (worst == null || expenseTotal <= 0) return
    val pct = (categoryTotals[worstId] ?: 0L) * 100.0 / expenseTotal
    sb.append(
      "📊 **بزرگترین کانون هزینه**: دسته‌بندی " +
        "**${worst.name}** با سهمی معادل ${pct.toInt()}٪، " +
        "بیشترین میزان مصرف نقدینگی را داشته است. " +
        "بررسی کنید آیا امکان کنترل هزینه‌ها در این بخش " +
        "وجود دارد یا خیر.\n\n"
    )
  }

  private fun appendLoanAdvice(
    sb: StringBuilder,
    loans: List<Loan>,
    installments: List<Installment>
  ) {
    val activeLoans = loans.filter { !it.isSettled }
    val activeInst = installments.filter { !it.isPaid }
    if (activeLoans.isNotEmpty()) {
      sb.append(LOAN_ADVICE.format(activeLoans.size))
    }
    if (activeInst.isNotEmpty()) {
      val total = activeInst.sumOf { it.amount }
      sb.append(INSTALLMENT_ADVICE.format(activeInst.size, total))
    }
  }
}
