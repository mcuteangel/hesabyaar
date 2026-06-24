package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.data.Category
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
                amount = (json.optDouble("amount", 0.0) * 1000).toLong(),
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

    private fun inferExpenseCategory(sentence: String): Pair<String, String> {
        return when {
            sentence.contains("مرغ") || sentence.contains("گوشت") || sentence.contains("غذا") ||
                    sentence.contains("میوه") || sentence.contains("رستوران") ||
                    sentence.contains("نان") || sentence.contains("شیر") || sentence.contains("پنیر") ||
                    sentence.contains("شام") || sentence.contains("ناهار") ||
                    sentence.contains(" صبحانه") || sentence.contains("چای") || sentence.contains("قهوه") ||
                    sentence.contains("اسنک") || sentence.contains("بستنی") || sentence.contains("سالاد") ||
                    sentence.contains("ماهی") || sentence.contains("میگو") || sentence.contains("سبزی") ||
                    sentence.contains("مربا") || sentence.contains("روغن") || sentence.contains("برنج") ||
                    sentence.contains("ماکارونی") || sentence.contains("رب") || sentence.contains("ادویه") -> Pair("Food", "خرید مواد غذایی")
            sentence.contains("بنزین") || sentence.contains("اسنپ") || sentence.contains("کرایه") ||
                    sentence.contains("تاکسی") || sentence.contains("مترو") || sentence.contains("اتوبوس") ||
                    sentence.contains("بلیط") || sentence.contains("پارکینگ") || sentence.contains("عوارض") ||
                    sentence.contains("لنت") || sentence.contains("لاستیک") || sentence.contains("تعویض روغن") ||
                    sentence.contains("مکانیک") || sentence.contains("تعمیرگاه") -> Pair("Transportation", "هزینه حمل و نقل")
            sentence.contains("لباس") || sentence.contains("کفش") || sentence.contains("پوشاک") ||
                    sentence.contains("کیف") || sentence.contains("کلاه") || sentence.contains("عینک") ||
                    sentence.contains("ساعت مچی") || sentence.contains("جواهرات") || sentence.contains("زیورآلات") ||
                    sentence.contains("کت") || sentence.contains("شلوار") || sentence.contains("پیراهن") ||
                    sentence.contains("مانتو") || sentence.contains("چادر") -> Pair("Shopping", "خرید پوشاک و اکسسوری")
            sentence.contains("قبض") || sentence.contains("برق") || sentence.contains("آب") ||
                    sentence.contains("گاز") || sentence.contains("تلفن") || sentence.contains("اینترنت") ||
                    sentence.contains("شارژ") || sentence.contains("فیبر") || sentence.contains("موبایل") ||
                    sentence.contains("tv") || sentence.contains("tv اشتراک") -> Pair("Bills", "پرداخت قبوض و شارژ")
            sentence.contains("اصلاح") || sentence.contains("سالن") || sentence.contains("آرایشگاه") ||
                    sentence.contains("کوتاهی") || sentence.contains("رنگ مو") || sentence.contains("واکس") ||
                    sentence.contains("پدیکور") || sentence.contains("مانیکور") || sentence.contains("ماساژ") ||
                    sentence.contains("اسپا") || sentence.contains("فیشال") || sentence.contains("لیزر") ||
                    sentence.contains("کرم") || sentence.contains("شامپو") || sentence.contains("عطر") ||
                    sentence.contains("ادکلن") || sentence.contains("لوازم آرایش") || sentence.contains("آرایش") ||
                    sentence.contains("پیرایش") || sentence.contains("ابرو") || sentence.contains("ریمل") ||
                    sentence.contains("رژ لب") || sentence.contains("پودر") || sentence.contains("کانسیلر") ||
                    sentence.contains("بنز") || sentence.contains("سیگار") || sentence.contains("قلیان") ||
                    sentence.contains("قهوه خانه") || sentence.contains("چایخانه") || sentence.contains("بستنی") ||
                    sentence.contains("هتل") || sentence.contains("اقامت") || sentence.contains("بلیط هواپیما") ||
                    sentence.contains("بلیط قطار") || sentence.contains("سفر") || sentence.contains("گردشگری") ||
                    sentence.contains("تفریح") || sentence.contains("سینما") || sentence.contains("تئاتر") ||
                    sentence.contains("کنسرت") || sentence.contains("بازی") || sentence.contains("ورزش") ||
                    sentence.contains("باشگاه") || sentence.contains("fitness") || sentence.contains("Gym") ||
                    sentence.contains("دارو") || sentence.contains("داروخانه") || sentence.contains("ویتامین") ||
                    sentence.contains("درمان") || sentence.contains("دندانپزشکی") || sentence.contains("چشم پزشکی") ||
                    sentence.contains("آزمایش") || sentence.contains("رادیولوژی") || sentence.contains("سونوگرافی") ||
                    sentence.contains("MRI") || sentence.contains("CT") || sentence.contains("تست") -> Pair("Personal Care", "هزینه شخصی، آرایشی و بهداشتی")
            sentence.contains("کتاب") || sentence.contains("مجله") || sentence.contains("روزنامه") ||
                    sentence.contains("دوره آموزشی") || sentence.contains("کلاس") || sentence.contains("آموزش") ||
                    sentence.contains("مدرسه") || sentence.contains("دانشگاه") || sentence.contains("شهریه") ||
                    sentence.contains("سرویس مدرسه") || sentence.contains("لوازم تحریر") || sentence.contains("مداد") ||
                    sentence.contains("خودکار") || sentence.contains("دفتر") || sentence.contains("کاغذ") ||
                    sentence.contains("Printer") || sentence.contains("پرینتر") || sentence.contains("کارتریج") ||
                    sentence.contains("نرم افزار") || sentence.contains("اپلیکیشن") || sentence.contains("اشتراک") ||
                    sentence.contains("سرویس") || sentence.contains("service") || sentence.contains("membership") -> Pair("Education", "هزینه آموزش و تحصیل")
            sentence.contains("اجاره") || sentence.contains("رهن") || sentence.contains("آپارتمان") ||
                    sentence.contains("خانه") || sentence.contains("ملک") || sentence.contains("زمین") ||
                    sentence.contains("ویلا") || sentence.contains("باغ") || sentence.contains("کلبه") ||
                    sentence.contains("اقامتگاه") || sentence.contains("هتل") || sentence.contains("مهمانخانه") ||
                    sentence.contains("پارکینگ") || sentence.contains("انبار") || sentence.contains("دفتر کار") ||
                    sentence.contains("مغازه") || sentence.contains("فروشگاه") || sentence.contains("بازرگانی") ||
                    sentence.contains("شرکت") || sentence.contains("کارخانه") || sentence.contains("کارگاه") ||
                    sentence.contains("بیمه") || sentence.contains("مالیات") || sentence.contains(" عوارض شهرداری") ||
                    sentence.contains("شارژ آپارتمان") || sentence.contains("تعمیرات ساختمان") ||
                    sentence.contains("نقاشی ساختمان") || sentence.contains("لوله کشی") ||
                    sentence.contains("برقکاری") || sentence.contains("بنایی") || sentence.contains("سنگ") ||
                    sentence.contains("سیمان") || sentence.contains("آجر") || sentence.contains("چوب") ||
                    sentence.contains("MDF") || sentence.contains("لمینت") || sentence.contains("سرامیک") ||
                    sentence.contains("کاشی") || sentence.contains("شیرآلات") || sentence.contains("شوفاژ") ||
                    sentence.contains("کولر") || sentence.contains("بخاری") || sentence.contains("شومینه") ||
                    sentence.contains("پکیج") || sentence.contains("رادیاتور") || sentence.contains("لوله") -> Pair("Rent & Utilities", "هزینه اجاره، رهن و نگهداری ملک")
            sentence.contains("قرض") || sentence.contains("وام") || sentence.contains("بدهی") ||
                    sentence.contains("قسط") || sentence.contains("چک") || sentence.contains("سفته") ||
                    sentence.contains("ضمانت") || sentence.contains("سود وام") || sentence.contains("جریمه") ||
                    sentence.contains("کارمزد") || sentence.contains("سود بانکی") || sentence.contains("بهره") ||
                    sentence.contains("سود مرکب") || sentence.contains("وام مسکن") || sentence.contains("وام خودرو") ||
                    sentence.contains("وام ازدواج") || sentence.contains("وام تحصیلی") || sentence.contains("وام ضربت") ||
                    sentence.contains("وام فوری") || sentence.contains("وام بازنشستگی") || sentence.contains("وام کارمندی") ||
                    sentence.contains("وام دولتی") || sentence.contains("وام خصوصی") || sentence.contains("وام بانکی") ||
                    sentence.contains("وام بدون بهره") || sentence.contains("وام با بهره") || sentence.contains("وام با سود") ||
                    sentence.contains("وام بدون سود") || sentence.contains("وام با کارمزد") || sentence.contains("وام بدون کارمزد") ||
                    sentence.contains("وام با ضمانت") || sentence.contains("وام بدون ضمانت") || sentence.contains("وام با چک") ||
                    sentence.contains("وام با سفته") || sentence.contains("وام با ضامن") || sentence.contains("وام بدون ضامن") -> Pair("Loans & Debt", "بدهی و وام")
            sentence.contains("درآمد") || sentence.contains("حقوق") || sentence.contains("واریز") ||
                    sentence.contains("اضافه کار") || sentence.contains("پاداش") || sentence.contains("بونوس") ||
                    sentence.contains("سود") || sentence.contains("دریافتی") || sentence.contains("واریزی") ||
                    sentence.contains("حقوقی") || sentence.contains("کارانه") || sentence.contains("فروش") ||
                    sentence.contains("درآمدزایی") || sentence.contains("حق بیمه") || sentence.contains("عیدی") ||
                    sentence.contains(" سنوات") || sentence.contains("پرداختی") || sentence.contains("حقوق ماه") ||
                    sentence.contains("حقوق اداره") || sentence.contains("حقوق شرکت") || sentence.contains("حقوقم") ||
                    sentence.contains("حقوقم رو") || sentence.contains("دریافت کردم") || sentence.contains("واریز شد") ||
                    sentence.contains("رسید") || sentence.contains("واریز کرد") || sentence.contains("فروش رفت") ||
                    sentence.contains("درآمد داشتم") || sentence.contains("پول درآوردم") || sentence.contains("سود کردم") ||
                    sentence.contains("بازدهی") || sentence.contains("return") || sentence.contains("profit") -> Pair("Income", "درآمد")
            sentence.contains("هدیه") || sentence.contains("جشن") || sentence.contains("تولد") ||
                    sentence.contains("عروسی") || sentence.contains("نامزدی") || sentence.contains("سالگرد") ||
                    sentence.contains("مراسم") || sentence.contains("مهمانی") || sentence.contains("party") ||
                    sentence.contains("celebration") || sentence.contains("event") || sentence.contains("wedding") -> Pair("Events & Gifts", "جشن و هدیه")
            sentence.contains("خیریه") || sentence.contains("صدقه") || sentence.contains("کمک") ||
                    sentence.contains(" donate") || sentence.contains("charity") || sentence.contains("philanthropy") -> Pair("Charity", "خیریه و کمک مالی")
            sentence.contains("سرمایه گذاری") || sentence.contains("خرید سهام") || sentence.contains("صندوق سرمایه") ||
                    sentence.contains("طلا") || sentence.contains("سکه") || sentence.contains("دلار") ||
                    sentence.contains("ارز") || sentence.contains("نفت") || sentence.contains("گاز") ||
                    sentence.contains("مسکن") || sentence.contains("زمین") || sentence.contains("باغ") ||
                    sentence.contains("بیمه عمر") || sentence.contains("بیمه تصادف") || sentence.contains("بیمه آتش سوزی") ||
                    sentence.contains("بیمه زلزله") || sentence.contains("بیمه سرقت") || sentence.contains("بیمه مسئولیت") -> Pair("Investment", "سرمایه گذاری")
            else -> Pair("Other", "سایر هزینه‌ها")
        }
    }

    // High quality regex-based fallback parsing for offline/no key scenarios
    fun parseSentenceOffline(sentence: String): ParsedResult {
        AppLogger.i(TAG, "Using offline natural parser heuristics")
        var amountToman = 0.0
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
        val hourRegex = "(ساعت|ساعتِ)\\s*([0-9۰-۹]+)".toRegex()
        val hourMatch = hourRegex.find(sentence)
        if (hourMatch != null) {
            hour = hourMatch.groupValues[2]
                .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3")
                .replace("۴", "4").replace("۵", "5").replace("۶", "6").replace("۷", "7")
                .replace("۸", "8").replace("۹", "9")
                .toIntOrNull()
            if (sentence.contains("نیم")) {
                minute = 30
            } else {
                val minRegex = "و\\s*([0-9۰-۹]+)\\s*(دقیقه)?".toRegex()
                val minMatch = minRegex.find(sentence, hourMatch.range.last + 1)
                if (minMatch != null) {
                    minute = minMatch.groupValues[1]
                        .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3")
                        .replace("۴", "4").replace("۵", "5").replace("۶", "6").replace("۷", "7")
                        .replace("۸", "8").replace("۹", "9")
                        .toIntOrNull()
                }
            }
        }

        // Extract amount helper (e.g. 450 هزار or 5 میلیون)
        val digit = "[0-9۰-۹]"
        val numPattern = "($digit+([./]$digit+)?)"
        val millionRegex = "$numPattern\\s*(میلیون|میلیون تومان|ملیون)".toRegex()
        val thousandRegex = "$numPattern\\s*(هزار|تومن|تومان)".toRegex()

        fun toArabicDigits(s: String): String = s
            .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3")
            .replace("۴", "4").replace("۵", "5").replace("۶", "6").replace("۷", "7")
            .replace("۸", "8").replace("۹", "9")

        val millionMatch = millionRegex.find(sentence)
        val thousandMatch = thousandRegex.find(sentence)

        if (millionMatch != null) {
            val num = toArabicDigits(millionMatch.groupValues[1]).replace("/", ".").toDoubleOrNull() ?: 1.0
            amountToman = num * 1_000_000.0
        } else if (thousandMatch != null) {
            val num = toArabicDigits(thousandMatch.groupValues[1]).replace("/", ".").toDoubleOrNull() ?: 1.0
            amountToman = if (sentence.contains("هزار")) num * 1000.0 else num
        } else {
            // Check for simple number
            val numbers = "[$digit]+".toRegex().findAll(sentence).toList()
            if (numbers.isNotEmpty()) {
                val num = toArabicDigits(numbers.last().value).toDoubleOrNull() ?: 0.0
                if (num < 1000) {
                    amountToman = if (sentence.contains("میلیون")) num * 1_000_000 else if (sentence.contains("هزار")) num * 1000 else num
                } else {
                    amountToman = num
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

        // Determine type and category based on sentence keyword matches
        val incomeKeywords = listOf(
            "حقوق", "درآمد", "واریز", "اضافه کار", "اضافه‌کار", "دستمزد", "پاداش",
            "بونوس", "bonus", "سود", "دریافتی", "واریزی", "حقوقی", "کارانه",
            "فروش", "درآمدزایی", "حق بیمه", "عیدی", " سنوات", "پرداختی",
            "حقوق ماه", "حقوق اداره", "حقوق شرکت", "حقوقم", "حقوقم رو",
            "دریافت کردم", "واریز شد", "رسید", "واریز کرد"
        )
        val expenseKeywords = listOf(
            "خریدم", "پرداخت", "هزینه", "قبض", " اجاره", "خرید", "پول دادم",
            "خرج", "پرداخت کردم", "دادم", "رفت", "گذاشتم",
            "اصلاح", "سالن", "آرایشگاه", "کوتاهی مو", "رنگ مو", "واکس", "پدیکور", "مانیکور",
            "ماساژ", "اسپا", "فیشال", "لیزر", "کرم", "شامپو", "عطر", "ادکلن", "لوازم آرایش", "آرایش",
            "پیرایش", "ابرو", "ریمل", "رژ لب", "پودر", "کانسیلر", "بنز", "سیگار", "قلیان",
            "قهوه خانه", "چایخانه", "بستنی", "هتل", "اقامت", "بلیط هواپیما", "بلیط قطار",
            "سفر", "گردشگری", "تفریح", "سینما", "تئاتر", "کنسرت", "بازی", "ورزش", "باشگاه",
            "fitness", "Gym", "دارو", "داروخانه", "ویتامین", "درمان", "دندانپزشکی", "چشم پزشکی",
            "آزمایش", "رادیولوژی", "سونوگرافی", "MRI", "CT", "تست",
            "اجاره", "رهن", "آپارتمان", "خانه", "ملک", "زمین", "ویلا", "باغ", "کلبه",
            "اقامتگاه", "مهمانخانه", "پارکینگ", "انبار", "دفتر کار", "مغازه", "فروشگاه",
            "بازرگانی", "شرکت", "کارخانه", "کارگاه", "بیمه", "مالیات", " عوارض شهرداری",
            "شارژ آپارتمان", "تعمیرات ساختمان", "نقاشی ساختمان", "لوله کشی", "برقکاری",
            "بنایی", "سنگ", "سیمان", "آجر", "چوب", "MDF", "لمینت", "سرامیک", "کاشی",
            "شیرآلات", "شوفاژ", "کولر", "بخاری", "شومینه", "پکیج", "رادیاتور", "لوله",
            "قرض", "وام", "بدهی", "قسط", "چک", "سفته", "ضمانت", "سود وام", "جریمه",
            "کارمزد", "سود بانکی", "بهره", "سود مرکب", "وام مسکن", "وام خودرو", "وام ازدواج",
            "وام تحصیلی", "وام ضربت", "وام فوری", "وام بازنشستگی", "وام کارمندی",
            "هدیه", "جشن", "تولد", "عروسی", "نامزدی", "سالگرد", "مراسم", "مهمانی",
            "خیریه", "صدقه", " کمک", "سرمایه گذاری", "خرید سهام", "صندوق سرمایه",
            "طلا", "سکه", "دلار", "ارز", "نفت", "بیمه عمر", "بیمه تصادف", "بیمه آتش سوزی",
            "بیمه زلزله", "بیمه سرقت", "بیمه مسئولیت"
        )
        val isIncome = incomeKeywords.any { sentence.contains(it, ignoreCase = true) }
        val isExpense = expenseKeywords.any { sentence.contains(it, ignoreCase = true) }

        fun extractDescription(): String {
            val cleaned = sentence
                .replace("امروز", "").replace("دیروز", "").replace("پریروز", "")
                .replace("فردا", "").replace("پسفردا", "").replace("پس فردا", "")
                .replace("ساعت", "").replace("نیم", "").replace("دقیقه", "")
                .replace("هزار", "").replace("تومان", "").replace("تومن", "")
                .replace("میلیون", "").replace("ملیون", "")
                .trim()
                .replace("\\s+".toRegex(), " ")
            return cleaned.ifBlank { sentence }
        }

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
            val (inferredCategory, inferredDescription) = inferExpenseCategory(sentence)
            category = inferredCategory
            description = inferredDescription
        } else {
            // Default: still infer category even without explicit expense keywords
            type = "EXPENSE"
            val (inferredCategory, inferredDescription) = inferExpenseCategory(sentence)
            category = inferredCategory
            description = if (inferredCategory != "Other") inferredDescription else extractDescription()
        }

        return ParsedResult(
            type = type,
            amount = (amountToman * 1000).toLong(),
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
        categories: List<Category>,
        config: AiProviderConfig? = null
    ): String? = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "getBudgetAdvice: config=${config?.let { "provider=${it.providerType}, isConfigured=${it.isConfigured}" } ?: "null"}")
        val providerConfig = config ?: AiProviderConfig()
        if (!providerConfig.isConfigured) {
            AppLogger.w(TAG, "AI provider not configured, using offline local generator fallback")
            return@withContext getBudgetAdviceOffline(transactions, loans, installments, categories)
        }

        val incomeTotal = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expenseTotal = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = incomeTotal - expenseTotal

        val categoryTotals = transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
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
            categoryTotals.forEach { (catId, amt) ->
                val cat = categories.find { it.id == catId }
                appendLine("- ${cat?.name ?: "سایر"}: ${amt} تومان")
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
                    val cat = categories.find { it.id == t.categoryId }
                    appendLine("- ${t.description} (${cat?.name ?: "سایر"}): ${t.amount} تومان [${sign}]")
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
            val savingRate = (balance * 100.0 / incomeTotal)
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
            .groupBy { it.categoryId }
            .mapValues { it.value.sumOf { trans -> trans.amount } }

        val worstCategoryId = categoryTotals.maxByOrNull { it.value }?.key
        val worstCategory = categories.find { it.id == worstCategoryId }
        if (worstCategory != null && expenseTotal > 0) {
            val catName = worstCategory.name
            val catPct = (categoryTotals[worstCategoryId] ?: 0L) * 100.0 / expenseTotal
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
    val amount: Long,
    val category: String,
    val personName: String?,
    val description: String,
    val daysFromNow: Int?,
    val title: String?,
    val dateOffsetDays: Int? = 0,
    val hour: Int? = null,
    val minute: Int? = null
)
