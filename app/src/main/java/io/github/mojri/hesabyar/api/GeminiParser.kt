package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.ui.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object GeminiParser {
    private const val TAG = "GeminiParser"

    suspend fun parseSentence(
        sentence: String,
        config: AiProviderConfig? = null
    ): ParsedResult? = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "parseSentence: config=${config?.let { "provider=${it.providerType}, isConfigured=${it.isConfigured}, model=${it.model}" } ?: "null"}")
        val providerConfig = config ?: AiProviderConfig()
        if (!providerConfig.isConfigured) {
            AppLogger.w(TAG, "AI provider not configured, using offline local parser fallback")
            return@withContext parseSentenceOffline(sentence)
        }

        val systemInstruction = """
            You are a smart financial analyzer for a Persian accounting app. Parse the Persian text and return JSON matching this schema:
            {
              "type": "EXPENSE" | "INCOME" | "LOAN_DEBTOR" | "LOAN_CREDITOR" | "INSTALLMENT",
              "amount": double (extracted raw Toman amount. If "میلیون" is written, convert to double e.g. 5 میلیون = 5000000, 450 هزار = 450000),
              "category": "Food" | "Transportation" | "Shopping" | "Bills" | "Installments" | "Loans" | "Income" | "Other",
              "personName": string (if person is specified, else empty/null),
              "description": string (Farsi description of what was purchased/done),
              "daysFromNow": integer (if installment or due date has numeric info like '15 تیر', extract approximate days from now. Today's date is June 21, 2026. So 15 تیر is July 6 which is 15 days from now),
              "title": string (for installment titles, e.g., 'قسط ماشین', or null),
              "dateOffsetDays": integer (if text specifies relative day like 'دیروز' -> -1, 'پریروز' -> -2, 'فردا' -> 1, 'امروز' -> 0, otherwise default to 0),
              "hour": integer (hour specified in text 0-23, e.g. 'ساعت ۲' -> 14 or 2, 'ساعت ۸ شب' -> 20, otherwise null),
              "minute": integer (minute specified in text 0-59, e.g. 'ساعت ۲ و ده دقیقه' -> 10, 'ساعت ۲ و نیم' -> 30, otherwise null)
            }
            Ensure categories match the requested categories: Food, Transportation, Shopping, Bills, Installments, Loans, Income, Other.
            Example inputs:
            - "امروز مرغ خریدم 450 هزار تومان" -> type="EXPENSE", amount=450000, category="Food", description="خرید مرغ", dateOffsetDays=0
            - "دیروز از علی 5 میلیون قرض گرفتم" -> type="LOAN_CREDITOR", amount=5000000, category="Loans", personName="علی", description="قرض گرفتن از علی", dateOffsetDays=-1
            - "به رضا 2 میلیون قرض دادم ساعت ۸ شب" -> type="LOAN_DEBTOR", amount=2000000, category="Loans", personName="رضا", description="قرض دادن به رضا", dateOffsetDays=0, hour=20, minute=0
            - "قسط ماشین 15 تیر 3 میلیون" -> type="INSTALLMENT", amount=3000000, category="Installments", title="قسط ماشین", daysFromNow=15, description="قسط ماشین"
            - "حقوق گرفتم 20 میلیون ساعت ۲ و نیم بعد از ظهر" -> type="INCOME", amount=20000000, category="Income", description="دریافت حقوق", hour=14, minute=30
            Strictly return ONLY raw JSON, do not wrap in markdown tags.
        """.trimIndent()

        when (val result = AiProvider.generateContent(
            config = providerConfig,
            prompt = sentence,
            systemInstruction = systemInstruction,
            temperature = 0.1,
            responseMimeType = "application/json"
        )) {
            is AiProvider.ApiResult.Success -> {
                AppLogger.i(TAG, "AI parsed output: ${result.text}")
                parseJsonResult(result.text) ?: parseSentenceOffline(sentence)
            }
            is AiProvider.ApiResult.Failure -> {
                AppLogger.e(TAG, "AI parse failed: ${result.error}, falling back to offline")
                parseSentenceOffline(sentence)
            }
        }
    }

    private fun parseJsonResult(jsonStr: String): ParsedResult? {
        return try {
            val cleanStr = jsonStr.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(cleanStr)
            ParsedResult(
                type = json.optString("type", "EXPENSE"),
                amount = json.optDouble("amount", 0.0),
                category = json.optString("category", "Other"),
                personName = json.optString("personName", "").let { if (it == "null" || it.isBlank()) null else it },
                description = json.optString("description", "ثبت دستیار هوشمند"),
                daysFromNow = if (json.isNull("daysFromNow")) null else json.optInt("daysFromNow"),
                title = json.optString("title", "").let { if (it == "null" || it.isBlank()) null else it },
                dateOffsetDays = json.optInt("dateOffsetDays", 0),
                hour = if (json.isNull("hour")) null else json.optInt("hour"),
                minute = if (json.isNull("minute")) null else json.optInt("minute")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse json result: $jsonStr", e)
            null
        }
    }

    // High quality regex-based fallback parsing for offline/no key scenarios
    fun parseSentenceOffline(sentence: String): ParsedResult {
        AppLogger.i(TAG, "Using offline natural parser heuristics")
        var amount = 0.0
        var type = "EXPENSE"
        var category = "Other"
        var personName: String? = null
        var description = sentence
        var daysFromNow: Int? = null
        var installmentTitle: String? = null
        var dateOffsetDays = 0
        var hour: Int? = null
        var minute: Int? = null

        if (sentence.contains("دیروز")) {
            dateOffsetDays = -1
        } else if (sentence.contains("پریروز")) {
            dateOffsetDays = -2
        } else if (sentence.contains("فردا")) {
            dateOffsetDays = 1
        } else if (sentence.contains("پسفردا") || sentence.contains("پس فردا")) {
            dateOffsetDays = 2
        } else if (sentence.contains("امروز")) {
            dateOffsetDays = 0
        }

        // Try extracting simple time "ساعت ۱۲" or "ساعت 14:30" or similar
        val hourRegex = "(ساعت|ساعتِ)\\s*(\\d+)".toRegex()
        val hourMatch = hourRegex.find(sentence)
        if (hourMatch != null) {
            hour = hourMatch.groupValues[2].toIntOrNull()
            if (sentence.contains("نیم")) {
                minute = 30
            } else {
                val minRegex = "و\\s*(\\d+)\\s*(دقیقه)?".toRegex()
                val minMatch = minRegex.find(sentence, hourMatch.range.last + 1)
                if (minMatch != null) {
                    minute = minMatch.groupValues[1].toIntOrNull()
                }
            }
        }

        // Extract amount helper (e.g. 450 هزار or 5 میلیون)
        val millionRegex = "(\\d+([./]\\d+)?)\\s*(میلیون|میلیون تومان|ملیون)".toRegex()
        val thousandRegex = "(\\d+([./]\\d+)?)\\s*(هزار|تومن|تومان)".toRegex()

        val millionMatch = millionRegex.find(sentence)
        val thousandMatch = thousandRegex.find(sentence)

        if (millionMatch != null) {
            val num = millionMatch.groupValues[1].replace("/", ".").toDoubleOrNull() ?: 1.0
            amount = num * 1_000_000.0
        } else if (thousandMatch != null) {
            val num = thousandMatch.groupValues[1].replace("/", ".").toDoubleOrNull() ?: 1.0
            // check context if it's high digit e.g. 450 هزار or just 450
            amount = if (sentence.contains("هزار")) num * 1000.0 else num
        } else {
            // Check for simple number
            val numbers = "\\d+".toRegex().findAll(sentence).toList()
            if (numbers.isNotEmpty()) {
                val num = numbers.last().value.toDoubleOrNull() ?: 0.0
                if (num < 1000) {
                    amount = if (sentence.contains("میلیون")) num * 1_000_000 else if (sentence.contains("هزار")) num * 1000 else num
                } else {
                    amount = num
                }
            }
        }

        // Detect Persons elements
        val personRegex = "(به|از)\\s+([^\\s]+)".toRegex()
        val personMatch = personRegex.find(sentence)
        if (personMatch != null) {
            val nameCandidate = personMatch.groupValues[2]
                .replace("تومان", "")
                .replace("هزار", "")
                .replace("قرض", "")
                .trim()
            if (nameCandidate.length > 2 && nameCandidate != "من" && nameCandidate != "خودم") {
                personName = nameCandidate
            }
        }

        // Determine type based on sentence keyword matches
        // Check income FIRST because income keywords are more specific than expense defaults
        val incomeKeywords = listOf(
            "حقوق", "درآمد", "واریز", "اضافه کار", "اضافه‌کار", "دستمزد", "پاداش",
            "بونوس", "bonus", "سود", "دریافتی", "واریزی", "حقوقی", "کارانه",
            "فروش", "درآمدزایی", "حق بیمه", "عیدی", " سنوات", "پرداختی",
            "حقوق ماه", "حقوق اداره", "حقوق شرکت", "حقوقم", "حقوقم رو",
            "دریافت کردم", "واریز شد", "رسید", "واریز کرد"
        )
        val expenseKeywords = listOf(
            "خریدم", "پرداخت", "هزینه", "قبض", " اجاره", "خرید", "پول دادم",
            "خرج", "پرداخت کردم", "دادم", "رفت", "گذاشتم"
        )
        val isIncome = incomeKeywords.any { sentence.contains(it, ignoreCase = true) }
        val isExpense = expenseKeywords.any { sentence.contains(it, ignoreCase = true) }

        if (sentence.contains("قرض گرفتم") || sentence.contains("بدهکار شدم") || sentence.contains("گرفتم از")) {
            type = "LOAN_CREDITOR"
            category = "Loans"
            description = "قرض گرفتن از ${personName ?: "طلبکار"}"
        } else if (sentence.contains("قرض دادم") || sentence.contains("طلبکار شدم") || sentence.contains("دادم به")) {
            type = "LOAN_DEBTOR"
            category = "Loans"
            description = "قرض دادن به ${personName ?: "بدهکار"}"
        } else if (sentence.contains("قسط")) {
            type = "INSTALLMENT"
            category = "Installments"
            description = "قسطی"
            installmentTitle = if (sentence.contains("ماشین")) "قسط ماشین" else if (sentence.contains("خانه")) "قسط خانه" else "قسط جدید"
            if (sentence.contains("تیر")) daysFromNow = 15
            else if (sentence.contains("مرداد")) daysFromNow = 45
            else daysFromNow = 30
        } else if (isIncome) {
            type = "INCOME"
            category = "Income"
            description = when {
                sentence.contains("اضافه کار") || sentence.contains("اضافه‌کار") -> "دریافت اضافه کار"
                sentence.contains("پاداش") -> "دریافت پاداش"
                sentence.contains("دستمزد") -> "دریافت دستمزد"
                sentence.contains("فروش") -> "درآمد از فروش"
                sentence.contains("سود") -> "دریافت سود"
                sentence.contains("حقوق") -> "دریافت حقوق"
                else -> "دریافت درآمد"
            }
        } else if (isExpense) {
            type = "EXPENSE"
            if (sentence.contains("لباس") || sentence.contains("کفش") || sentence.contains("پوشاک")) {
                category = "Shopping"
                description = "خرید پوشاک"
            } else if (sentence.contains("بنزین") || sentence.contains("اسنپ") || sentence.contains("کرایه") || sentence.contains("تاکسی") || sentence.contains("ماشین")) {
                category = "Transportation"
                description = "هزینه های رفت و آمد"
            } else if (sentence.contains("قبض") || sentence.contains("برق") || sentence.contains("آب") || sentence.contains("گاز") || sentence.contains("تلفن")) {
                category = "Bills"
                description = "پرداخت قبوض"
            } else if (sentence.contains("مرغ") || sentence.contains("گوشت") || sentence.contains("غذا") || sentence.contains("میوه") || sentence.contains("رستوران") || sentence.contains("خرید")) {
                category = "Food"
                description = "خرید مواد غذایی"
            } else {
                category = "Other"
                description = "ثبت دستی"
            }
        } else {
            // Default to expense when no clear income or expense signal detected
            type = "EXPENSE"
            category = "Other"
            description = "ثبت دستی"
        }

        return ParsedResult(
            type = type,
            amount = amount,
            category = category,
            personName = personName,
            description = description,
            daysFromNow = daysFromNow,
            title = installmentTitle,
            dateOffsetDays = dateOffsetDays,
            hour = hour,
            minute = minute
        )
    }

    suspend fun getBudgetAdvice(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        config: AiProviderConfig? = null
    ): String? = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "getBudgetAdvice: config=${config?.let { "provider=${it.providerType}, isConfigured=${it.isConfigured}" } ?: "null"}")
        val providerConfig = config ?: AiProviderConfig()
        if (!providerConfig.isConfigured) {
            AppLogger.w(TAG, "AI provider not configured, using offline local generator fallback")
            return@withContext getBudgetAdviceOffline(transactions, loans, installments)
        }

        val incomeTotal = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expenseTotal = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = incomeTotal - expenseTotal

        val categoryTotals = transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val activeLoans = loans.filter { !it.isSettled }
        val activeInstallments = installments.filter { !it.isPaid }

        val systemPrompt = """
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

        val dataSummary = StringBuilder().apply {
            appendLine("تعداد کل تراکنش‌ها: ${transactions.size}")
            appendLine("کل درآمد ثبت شده: ${incomeTotal} تومان")
            appendLine("کل مخارج ثبت شده: ${expenseTotal} تومان")
            appendLine("تراز باقیمانده (پس‌انداز): ${balance} تومان")
            
            appendLine("\nتفکیک هزینه‌ها به دسته‌بندی:")
            categoryTotals.forEach { (cat, amt) ->
                appendLine("- ${getPersianCategoryName(cat)}: ${amt} تومان")
            }

            if (activeLoans.isNotEmpty()) {
                appendLine("\nوام‌ها و قرض‌های فعال:")
                activeLoans.forEach { loan ->
                    val role = if (loan.type == "DEBTOR") "طلبکار (قرض دادید به)" else "بدهکار (قرض گرفتید از)"
                    appendLine("- ${loan.personName} (${role}): کل ${loan.originalAmount} تومان | مانده ${loan.remainingAmount} تومان")
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
                    val sign = if (t.type == "INCOME") "آمد" else "رفت"
                    appendLine("- ${t.description} (${getPersianCategoryName(t.category)}): ${t.amount} تومان [${sign}]")
                }
            }
        }.toString()

        val prompt = "در اینجا اطلاعات مالی من برای تحلیل و توصیه آمده است:\n$dataSummary"

        when (val result = AiProvider.generateContent(
            config = providerConfig,
            prompt = prompt,
            systemInstruction = systemPrompt,
            temperature = 0.6
        )) {
            is AiProvider.ApiResult.Success -> {
                AppLogger.i(TAG, "AI advice outcome: ${result.text}")
                result.text
            }
            is AiProvider.ApiResult.Failure -> {
                AppLogger.e(TAG, "AI budget advice failed: ${result.error}")
                getBudgetAdviceOffline(transactions, loans, installments)
            }
        }
    }

    private fun getPersianCategoryName(category: String): String {
        return when (category) {
            "Food" -> "خوراک"
            "Transportation" -> "حمل و نقل"
            "Shopping" -> "خرید"
            "Bills" -> "قبوض"
            "Installments" -> "اقساط"
            "Loans" -> "قرض و وام"
            "Income" -> "درآمد"
            else -> "سایر"
        }
    }

    fun getBudgetAdviceOffline(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>
    ): String {
        val incomeTotal = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expenseTotal = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = incomeTotal - expenseTotal

        val sb = StringBuilder()
        sb.append("💡 **تحلیلگر و مشاور مالی هوشمند (آفلاین)**\n\n")

        if (transactions.isEmpty()) {
            sb.append("شما هنوز هیچ تراکنشی ثبت نکرده‌اید! اولین تراکنش‌های دریافتی یا مخارج خود را ثبت کنید تا حسابیار بتواند رفتار مالی شما را تحلیل کند.")
            return sb.toString()
        }

        sb.append("بر اساس مداقه بر تراکنش‌های ثبت شده، چند توصیه عملی برای شما داریم:\n\n")

        // 1. Savings advice
        if (incomeTotal > 0) {
            val savingRate = (balance / incomeTotal) * 100
            if (savingRate < 0) {
                sb.append("⚠️ **کنترل تراز مخارج**: متاسفانه مخارج شما در این دوره بیش از درآمدتان بوده است (${String.format("%.1f", savingRate)}٪ کسری). توصیه می‌شود خریدهای غیرضروری خود را به زمان بهتری موکول کرده و روی کالاهای اساسی متمرکز شوید.\n\n")
            } else if (savingRate < 10) {
                sb.append("📉 **بهبود نرخ پس‌انداز**: شما حدود ${String.format("%.1f", savingRate)}٪ از درآمد خود را پس‌انداز کرده‌اید. برای داشتن پشتوانه مالی مطمئن‌تر، تلاش کنید با کاهش مخارج کوچکِ روزمره، این نسبت را به حداقل ۲۰٪ برسانید.\n\n")
            } else {
                sb.append("🎉 **عملکرد عالی پس‌انداز**: آفرین! شما توانسته‌اید بیش از ${String.format("%.1f", savingRate)}٪ از درآمد خود را پس‌انداز کنید. این روند فوق‌العاده را برای ثروت‌آفرینی بیشتر ادامه دهید.\n\n")
            }
        } else {
            sb.append("📉 **جذب و ثبت درآمد**: شما تاکنون درآمد چشمگیری ثبت نکرده‌اید اما هزینه‌های ثبت شده وجود دارد. تلاش کنید درآمدهای خود را نیز ثبت کنید تا نسبت درآمد به مخارج دقیق‌تر محاسبه شود.\n\n")
        }

        // 2. High spending category detection
        val categoryTotals = transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { trans -> trans.amount } }

        val worstCategory = categoryTotals.maxByOrNull { it.value }
        if (worstCategory != null && expenseTotal > 0) {
            val catName = getPersianCategoryName(worstCategory.key)
            val catPct = (worstCategory.value / expenseTotal) * 100
            sb.append("📊 **بزرگترین کانون هزینه**: دسته‌بندی **$catName** با سهمی معادل ${catPct.toInt()}٪، بیشترین میزان مصرف نقدینگی را داشته است. بررسی کنید آیا امکان کنترل هزینه‌ها در این بخش وجود دارد یا خیر.\n\n")
        }

        // 3. Loans and Installments advice
        val activeLoans = loans.filter { !it.isSettled }
        val activeInstallments = installments.filter { !it.isPaid }

        if (activeLoans.isNotEmpty()) {
            sb.append("🤝 **امور مالی اشخاص (قرض و وام)**: شما دارای ${activeLoans.size} مورد تسویه نشده هستید. تسویه به موقع دیون و پیگیری منظم طلب‌ها از اشخاص به پایداری روابط کاری و شخصی شما یاری می‌رساند.\n\n")
        }

        if (activeInstallments.isNotEmpty()) {
            val totalInstAmt = activeInstallments.sumOf { it.amount }
            sb.append("📅 **بدهی‌های سررسیددار (اقساط)**: شما در پیش‌رو ${activeInstallments.size} قسط پرداخت‌نشده به ارزش مجموع ${totalInstAmt.toLong()} تومان دارید. توصیه می‌شود مبلغ اقساط را زودتر کنار بگذارید تا سررسید آن‌ها باعث جریمه یا فشار مالی نشود.")
        }

        return sb.toString()
    }
}

data class ParsedResult(
    val type: String,
    val amount: Double,
    val category: String,
    val personName: String?,
    val description: String,
    val daysFromNow: Int?,
    val title: String?,
    val dateOffsetDays: Int? = 0,
    val hour: Int? = null,
    val minute: Int? = null
)
