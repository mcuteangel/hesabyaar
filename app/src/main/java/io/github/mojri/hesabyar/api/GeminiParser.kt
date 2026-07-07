package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

object GeminiParser {
  private const val TAG = "GeminiParser"

  private const val TYPE_EXPENSE = "EXPENSE"
  private const val TYPE_INCOME = "INCOME"

  private const val KEYWORD_PARKING = "پارکینگ"
  private const val KEYWORD_OT = "اضافه کار"
  private const val KEYWORD_INVESTMENT = "سرمایه گذاری"
  private const val KEYWORD_PAYMENT = "پرداخت"
  private const val KEYWORD_DID_PAY = "پرداخت کردم"
  private const val KEYWORD_HAVE_CLAIM = "طلب دارم"
  private const val KEYWORD_CREDITOR = "طلبکار"
  private const val KEYWORD_DEBTOR = "بدهکار"
  private const val TYPE_LOAN_DEBTOR = "LOAN_DEBTOR"
  private const val TYPE_LOAN_CREDITOR = "LOAN_CREDITOR"
  private const val TYPE_INSTALLMENT = "INSTALLMENT"
  private const val KEY_DAYS_FROM_NOW = "daysFromNow"
  private const val KEYWORD_ICE_CREAM = "بستنی"

  private const val CATEGORY_FOOD = "Food"
  private const val CATEGORY_TRANSPORTATION = "Transportation"
  private const val CATEGORY_SHOPPING = "Shopping"
  private const val CATEGORY_BILLS = "Bills"
  private const val CATEGORY_INSTALLMENTS = "Installments"
  private const val CATEGORY_LOANS = "Loans"
  private const val CATEGORY_INCOME = "Income"
  private const val CATEGORY_OTHER = "Other"
  private const val CATEGORY_PERSONAL_CARE = "Personal Care"
  private const val CATEGORY_EDUCATION = "Education"
  private const val CATEGORY_RENT_UTILITIES = "Rent & Utilities"
  private const val CATEGORY_LOANS_DEBT = "Loans & Debt"
  private const val CATEGORY_EVENTS_GIFTS = "Events & Gifts"
  private const val CATEGORY_CHARITY = "Charity"
  private const val CATEGORY_INVESTMENT = "Investment"

  private val VALID_TYPES = listOf(TYPE_EXPENSE, TYPE_INCOME, TYPE_LOAN_DEBTOR, TYPE_LOAN_CREDITOR, TYPE_INSTALLMENT)

  private const val KEYWORD_TOMAN = "تومان"
  private const val KEYWORD_HAZAR = "هزار"

  private const val LOAN_ADVICE =
    "🤝 **امور مالی اشخاص (قرض و وام)**: شما دارای %d مورد تسویه نشده هستید. " +
      "تسویه به موقع دیون و پیگیری منظم طلب‌ها از اشخاص به پایداری روابط کاری و شخصی شما یاری می‌رساند.\n\n"
  private const val INSTALLMENT_ADVICE =
    "📅 **بدهی‌های سررسیددار (اقساط)**: شما در پیش‌رو %d قسط پرداخت‌نشده به ارزش مجموع %d تومان دارید. " +
      "توصیه می‌شود مبلغ اقساط را زودتر کنار بگذارید تا سررسید آن‌ها باعث جریمه یا فشار مالی نشود."

  suspend fun parseSentence(
    sentence: String,
    config: AiProviderConfig? = null
  ): ParsedResult? =
    withContext(Dispatchers.IO) {
      AppLogger.d(
        TAG,
        "parseSentence: config=${config?.let {
          "provider=${it.providerType}, isConfigured=${it.isConfigured}, model=${it.model}"
        } ?: "null"}"
      )
      val providerConfig = config ?: AiProviderConfig()
      if (!providerConfig.isConfigured) {
        AppLogger.w(TAG, "AI provider not configured, using offline local parser fallback")
        return@withContext parseSentenceOffline(sentence)
      }

      val systemInstruction =
        """
        You are a smart financial analyzer for a Persian accounting app. Parse Persian text and return JSON:
        {
          "type": "EXPENSE" | "INCOME" | "LOAN_DEBTOR" | "LOAN_CREDITOR" | "INSTALLMENT",
          "amount": double (Toman amount. Convert: 5 میلیون = 5000000, 450 هزار = 450000),
          "category": "Food" | "Transportation" | "Shopping" | "Bills" | "Installments" | "Loans" | "Income" | "Other",
          "personName": string (person name if specified, else null),
          "description": string (Persian description. For INSTALLMENT type, use future-oriented like 'قسط آینده' not 'پرداخت شده'),
          "daysFromNow": integer (calculate actual days from today to the Jalali date specified. Today's date is ${java.time.LocalDate.now()}. If text says '25 تیر', convert that Jalali date to Gregorian and compute days from today),
          "title": string (installment title like 'قسط ماشین', or null),
          "dateOffsetDays": integer ('دیروز'=-1, 'پریروز'=-2, 'فردا'=1, 'امروز'=0, default 0),
          "hour": integer (0-23, 'ساعت ۸ شب'=20, else null),
          "minute": integer (0-59, 'ساعت ۲ و نیم'=30, else null),
          "confidence": float (0.0-1.0 based on text clarity),
          "notes": string (for INSTALLMENT: 'قسط در انتظار پرداخت'. For loans: brief note. Or null)
        }
        Jalali months: فروردین(1), اردیبهشت(2), خرداد(3), تیر(4), مرداد(5), شهریور(6), مهر(7), آبان(8), آذر(9), دی(10), بهمن(11), اسفند(12). Days: months 1-6 have 31 days, months 7-11 have 30 days, month 12 has 29 (30 in leap years).
        Persian examples:
        - "۵۰۰ هزار تومن بابت برق" -> type="EXPENSE", amount=500000, category="Bills", description="پرداخت قبض برق"
        - "امروز حقوق گرفتم ۲۰ میلیون" -> type="INCOME", amount=20000000, category="Income", description="دریافت حقوق"
        - "به علی ۲ میلیون قرض دادم" -> type="LOAN_DEBTOR", amount=2000000, category="Loans", personName="علی"
        - "قسط ماشین 25 تیر 10 میلیون" -> type="INSTALLMENT", amount=10000000, category="Installments", title="قسط ماشین", daysFromNow=(actual days to 25 Tir), description="قسط آینده", notes="قسط در انتظار پرداخت"
        Return ONLY raw JSON, no markdown tags.
        """.trimIndent()

      when (
        val result =
          AiProvider.generateContent(
            config = providerConfig,
            prompt = sentence,
            systemInstruction = systemInstruction,
            temperature = 0.1,
            responseMimeType = "application/json"
          )
      ) {
        is AiProvider.ApiResult.Success -> {
          AppLogger.d(TAG, "AI parsed output received")
          parseJsonResultOffline(result.text) ?: parseSentenceOffline(sentence)
        }
        is AiProvider.ApiResult.Failure -> {
          AppLogger.e(TAG, "AI parse failed: ${result.error}, falling back to offline")
          parseSentenceOffline(sentence)
        }
      }
    }

  internal fun parseJsonResultOffline(jsonStr: String): ParsedResult? {
    val aiResult =
      io.github.mojri.hesabyar.rust.RustBridge
        .parseAiTransactionJsonSync(jsonStr)
    if (aiResult != null) {
      if (aiResult.wasRepaired) {
        AppLogger.w(TAG, "AI result repaired by Rust: ${aiResult.repairNotes.joinToString()}")
      }
      val r = aiResult.result
      return ParsedResult(
        type = r.txType.name,
        amount = r.amount,
        category = r.category,
        personName = r.personName,
        description = r.description,
        daysFromNow = r.daysFromNow,
        title = r.title,
        dateOffsetDays = r.dateOffsetDays,
        hour = r.hour,
        minute = r.minute,
        confidence = r.confidence,
        notes = r.notes
      )
    }
    // Fallback: parse manually if Rust unavailable
    return parseJsonResultFallback(jsonStr)
  }

  private fun parseJsonResultFallback(jsonStr: String): ParsedResult? {
    return try {
      val json = JSONObject(jsonStr)
      val type = json.optString("type", TYPE_EXPENSE)
      val amount = json.optLong("amount", 0L)
      if (amount <= 0L) return null
      val category = json.optString("category", CATEGORY_OTHER)
      val personName = json.optString("personName").takeIf { it.isNotEmpty() }
      val description = json.optString("description").takeIf { it.isNotEmpty() } ?: ""
      val daysFromNow =
        if (json.has(KEY_DAYS_FROM_NOW) && !json.isNull(KEY_DAYS_FROM_NOW)) {
          try {
            json.getInt(KEY_DAYS_FROM_NOW)
          } catch (_: Exception) {
            null
          }
        } else {
          null
        }
      val title = json.optString("title").takeIf { it.isNotEmpty() }
      val dateOffsetDays = json.optInt("dateOffsetDays", 0)
      val hour = json.optInt("hour", -1).let { if (it >= 0) it else null }
      val minute = json.optInt("minute", -1).let { if (it >= 0) it else null }
      val confidence = json.optDouble("confidence", 0.8).toFloat()
      val notes = json.optString("notes").takeIf { it.isNotEmpty() }

      ParsedResult(
        type = if (type in VALID_TYPES) type else TYPE_EXPENSE,
        amount = amount,
        category = category,
        personName = personName,
        description = description,
        daysFromNow = daysFromNow,
        title = title,
        dateOffsetDays = dateOffsetDays,
        hour = hour,
        minute = minute,
        confidence = confidence,
        notes = notes
      )
    } catch (e: JSONException) {
      AppLogger.e(TAG, "Failed to parse JSON result", e)
      null
    }
  }

  // parseSentenceOffline now delegates to Rust — see below

  /**
   * Parse a sentence offline using the Rust core.
   *
   * Maps from Rust's [io.github.mojri.hesabyar.rust.ParsedResult] (which uses
   * [TransactionType] enum) to the Kotlin [ParsedResult] (which uses a String type).
   */
  fun parseSentenceOffline(rawSentence: String): ParsedResult? {
    AppLogger.d(TAG, "Using offline Rust parser")
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge
        .parseSentenceOfflineSync(rawSentence)
    if (rustResult != null) {
      return ParsedResult(
        type = rustResult.txType.name,
        amount = rustResult.amount,
        category = rustResult.category,
        personName = rustResult.personName,
        description = rustResult.description,
        daysFromNow = rustResult.daysFromNow,
        title = rustResult.title,
        dateOffsetDays = rustResult.dateOffsetDays,
        hour = rustResult.hour,
        minute = rustResult.minute,
        confidence = rustResult.confidence,
        notes = rustResult.notes
      )
    }
    // Return null to indicate parsing failure — callers should not create zero-amount transactions
    AppLogger.w(TAG, "Rust parser returned null, returning null to indicate failure")
    return null
  }

  suspend fun getBudgetAdvice(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    config: AiProviderConfig? = null
  ): String? =
    withContext(Dispatchers.IO) {
      AppLogger.d(
        TAG,
        "getBudgetAdvice: config=${config?.let {
          "provider=${it.providerType}, isConfigured=${it.isConfigured}"
        } ?: "null"}"
      )
      val providerConfig = config ?: AiProviderConfig()
      if (!providerConfig.isConfigured) {
        AppLogger.w(TAG, "AI provider not configured, using offline local generator fallback")
        return@withContext getBudgetAdviceOffline(transactions, loans, installments, categories)
      }

      val incomeTotal = transactions.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
      val expenseTotal = transactions.filter { it.type == TYPE_EXPENSE }.sumOf { it.amount }
      val balance = incomeTotal - expenseTotal

      val categoryTotals =
        transactions
          .filter { it.type == TYPE_EXPENSE }
          .groupBy { it.categoryId }
          .mapValues { entry -> entry.value.sumOf { it.amount } }

      val activeLoans = loans.filter { !it.isSettled }
      val activeInstallments = installments.filter { !it.isPaid }

      val systemPrompt =
        """
        You are an expert Iranian financial advisor and budget planner. Inspect the user's financial ledger data (in Toman) and provide personalized, highly practical, smart budget recommendations in Persian.
        Give actionable recommendations to optimize expenses, manage loans, and improve savings.
        Adhere to these rules:
        1. Use direct, polite, friendly, and professional conversational Persian.
        2. Split suggestions into 3-4 structured bullet points.
        3. Highlight key categories of concern if they have high spending.
        4. Make references to their loans or upcoming installments if present to help them prioritize.
        5. Present prices in Toman (تومان) formatted clearly with thousands separators (e.g., 5,000,000 تومان).
        6. Keep the response concise but highly personalized, positive, and motivating.

        Format response with neat markdown structure. Keep the total length around 150-200 words. Highlight crucial sections.
        """.trimIndent()

      val dataSummary =
        StringBuilder()
          .apply {
            appendLine("تعداد کل تراکنش‌ها: ${transactions.size}")
            appendLine("کل درآمد ثبت شده: $incomeTotal تومان")
            appendLine("کل مخارج ثبت شده: $expenseTotal تومان")
            appendLine("تراز باقیمانده (پس‌انداز): $balance تومان")

            appendLine("\nتفکیک هزینه‌ها به دسته‌بندی:")
            categoryTotals.forEach { (catId, amt) ->
              val cat = categories.find { it.id == catId }
              appendLine("- ${cat?.name ?: "سایر"}: $amt تومان")
            }

            if (activeLoans.isNotEmpty()) {
              appendLine("\nوام‌ها و قرض‌های فعال:")
              activeLoans.forEach { loan ->
                val role = if (loan.type == "DEBTOR") "طلبکار (قرض دادید به)" else "بدهکار (قرض گرفتید از)"
                appendLine(
                  "- ${loan.personName} ($role): کل ${loan.originalAmount} تومان | مانده ${loan.remainingAmount} تومان"
                )
              }
            }

            if (activeInstallments.isNotEmpty()) {
              appendLine("\nاقساط پرداخت نشده:")
              activeInstallments.forEach { inst ->
                appendLine("- ${inst.title}: ${inst.amount} تومان")
              }
            }

            if (transactions.isNotEmpty()) {
              appendLine("\nتراکنش‌های اخیر:")
              transactions.sortedByDescending { it.date }.take(10).forEach { t ->
                val sign = if (t.type == TYPE_INCOME) "آمد" else "رفت"
                val cat = categories.find { it.id == t.categoryId }
                appendLine("- ${t.description} (${cat?.name ?: "سایر"}): ${t.amount} تومان [$sign]")
              }
            }
          }.toString()

      val prompt = "در اینجا اطلاعات مالی من برای تحلیل و توصیه آمده است:\n$dataSummary"

      when (
        val result =
          AiProvider.generateContent(
            config = providerConfig,
            prompt = prompt,
            systemInstruction = systemPrompt,
            temperature = 0.6
          )
      ) {
        is AiProvider.ApiResult.Success -> {
          AppLogger.d(TAG, "AI advice outcome received")
          val validation =
            io.github.mojri.hesabyar.rust.RustBridge
              .validateAiAdvice(result.text)
          if (!validation.isValid) {
            AppLogger.w(TAG, "AI advice failed validation, using offline: ${validation.warnings}")
            return@withContext getBudgetAdviceOffline(transactions, loans, installments, categories)
          }
          if (validation.wasTruncated) {
            AppLogger.d(TAG, "AI advice truncated: ${validation.warnings}")
          }
          validation.sanitizedText
        }
        is AiProvider.ApiResult.Failure -> {
          AppLogger.e(TAG, "AI budget advice failed: ${result.error}")
          getBudgetAdviceOffline(transactions, loans, installments, categories)
        }
      }
    }

  fun getBudgetAdviceOffline(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): String {
    val incomeTotal = transactions.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
    val expenseTotal = transactions.filter { it.type == TYPE_EXPENSE }.sumOf { it.amount }
    val balance = incomeTotal - expenseTotal

    val sb = StringBuilder()
    sb.append("💡 **تحلیلگر و مشاور مالی هوشمند (آفلاین)**\n\n")

    if (transactions.isEmpty()) {
      sb.append(
        "شما هنوز هیچ تراکنشی ثبت نکرده‌اید! اولین تراکنش‌های دریافتی یا مخارج خود را ثبت کنید تا حسابیار بتواند رفتار مالی شما را تحلیل کند."
      )
      return sb.toString()
    }

    sb.append("بر اساس مداقه بر تراکنش‌های ثبت شده، چند توصیه عملی برای شما داریم:\n\n")

    // 1. Savings advice
    if (incomeTotal > 0) {
      val savingRate = (balance * 100.0 / incomeTotal)
      if (savingRate < 0) {
        sb.append(
          "⚠️ **کنترل تراز مخارج**: متاسفانه مخارج شما در این دوره بیش از درآمدتان بوده است (${String.format(
            "%.1f",
            savingRate
          )}٪ کسری). توصیه می‌شود خریدهای غیرضروری خود را به زمان بهتری موکول کرده و روی کالاهای اساسی متمرکز شوید.\n\n"
        )
      } else if (savingRate < 10) {
        sb.append(
          "📉 **بهبود نرخ پس‌انداز**: شما حدود ${String.format(
            "%.1f",
            savingRate
          )}٪ از درآمد خود را پس‌انداز کرده‌اید. برای داشتن پشتوانه مالی مطمئن‌تر، تلاش کنید با کاهش مخارج کوچکِ روزمره، این نسبت را به حداقل ۲۰٪ برسانید.\n\n"
        )
      } else {
        sb.append(
          "🎉 **عملکرد عالی پس‌انداز**: آفرین! شما توانسته‌اید بیش از ${String.format(
            "%.1f",
            savingRate
          )}٪ از درآمد خود را پس‌انداز کنید. این روند فوق‌العاده را برای ثروت‌آفرینی بیشتر ادامه دهید.\n\n"
        )
      }
    } else {
      sb.append(
        "📉 **جذب و ثبت درآمد**: شما تاکنون درآمد چشمگیری ثبت نکرده‌اید اما هزینه‌های ثبت شده وجود دارد. تلاش کنید درآمدهای خود را نیز ثبت کنید تا نسبت درآمد به مخارج دقیق‌تر محاسبه شود.\n\n"
      )
    }

    // 2. High spending category detection
    val categoryTotals =
      transactions
        .filter { it.type == TYPE_EXPENSE }
        .groupBy { it.categoryId }
        .mapValues { it.value.sumOf { trans -> trans.amount } }

    val worstCategoryId = categoryTotals.maxByOrNull { it.value }?.key
    val worstCategory = categories.find { it.id == worstCategoryId }
    if (worstCategory != null && expenseTotal > 0) {
      val catName = worstCategory.name
      val catPct = (categoryTotals[worstCategoryId] ?: 0L) * 100.0 / expenseTotal
      sb.append(
        "📊 **بزرگترین کانون هزینه**: دسته‌بندی **$catName** با سهمی معادل ${catPct.toInt()}٪، بیشترین میزان مصرف نقدینگی را داشته است. بررسی کنید آیا امکان کنترل هزینه‌ها در این بخش وجود دارد یا خیر.\n\n"
      )
    }

    // 3. Loans and Installments advice
    val activeLoans = loans.filter { !it.isSettled }
    val activeInstallments = installments.filter { !it.isPaid }

    if (activeLoans.isNotEmpty()) {
      sb.append(LOAN_ADVICE.format(activeLoans.size))
    }

    if (activeInstallments.isNotEmpty()) {
      val totalInstAmt = activeInstallments.sumOf { it.amount }
      sb.append(INSTALLMENT_ADVICE.format(activeInstallments.size, totalInstAmt))
    }

    return sb.toString()
  }
}

data class ParsedResult(
  val type: String,
  val amount: Long,
  val category: String,
  val personName: String?,
  val description: String,
  val daysFromNow: Int?,
  val title: String?,
  val dateOffsetDays: Int? = 0,
  val hour: Int? = null,
  val minute: Int? = null,
  val confidence: Float = 0.8f,
  val notes: String? = null
)
