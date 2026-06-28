package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.ui.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

object BudgetAdvisor {
    private const val TAG = "BudgetAdvisor"

    suspend fun getBudgetAdvice(
        transactions: List<Transaction>,
        categories: List<Category>,
        config: AiProviderConfig? = null
    ): String = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "getBudgetAdvice: config=${config?.let { "provider=${it.providerType}, isConfigured=${it.isConfigured}" } ?: "null"}")
        val providerConfig = config ?: AiProviderConfig()
        if (!providerConfig.isConfigured) {
            AppLogger.w(TAG, "AI provider not configured, using offline local rules budget advisor")
            return@withContext getOfflineAdvice(transactions, categories)
        }

        if (transactions.isEmpty()) {
            return@withContext "هنوز تراکنشی در حسابیار ثبت نشده است. لطفا چند تراکنش ثبت کنید تا هوش مصنوعی بتواند بودجه شما را تحلیل کند."
        }

        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val categoriesGroup = transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .mapValues { it.value.sumOf { tx -> tx.amount } }

        val categoryReport = categoriesGroup.entries.joinToString("\n") { (catId, sum) ->
            val cat = categories.find { it.id == catId }
            "- ${cat?.name ?: "سایر"}: ${formatTomanClean(sum)} تومان"
        }

        val transactionListPrompt = transactions.take(30).joinToString("\n") { tx ->
            val typeStr = if (tx.type == "INCOME") "درآمد" else "هزینه"
            val cat = categories.find { it.id == tx.categoryId }
            "- ${cat?.name ?: "سایر"} | $typeStr | ${formatTomanClean(tx.amount)} تومان | شرح: ${tx.description}"
        }

        val prompt = """
            سلام. من یک حسابدار شخصی ایرانی دارم به نام «حسابیار».
            لطفاً تراکنش‌های مالی اخیر مرا بررسی کرده و توصیه‌های هوشمند، کاربردی و روان به زبان فارسی برای مدیریت بهتر بودجه، کاهش هزینه‌ها و افزایش پس‌انداز به من ارائه بده.

            آمارهای کلی من:
            - کل درآمد ثبت شده: ${formatTomanClean(totalIncome)} تومان
            - کل هزینه‌های ثبت شده: ${formatTomanClean(totalExpense)} تومان
            - تراز باقیمانده: ${formatTomanClean(totalIncome - totalExpense)} تومان

            هزینه‌ها به تفکیک دسته‌بندی:
            $categoryReport

            لیست ۳۰ تراکنش آخر من:
            $transactionListPrompt

            لطفا با یک لحن صمیمی و حرفه‌ای (مشابه یک مشاور مالی باتجربه و دلسوز) تحلیل خودت رو ارائه بدی. 
            توصیه‌ها رو بخش‌بندی کن (مثلاً تحلیل کلی تراز مالی، بررسی دسته‌بندی هزینه‌های عمده، نکات کاهش مخارج خاص بر اساس تراکنشام، و چند پیشنهاد طلایی کاربردی). 
            از قالب‌بندی زیبای Markdown (بولد کردن، ایموجی‌ها، لیست‌های نشانه‌دار) استفاده کن تا خواندن آن راحت باشد.
        """.trimIndent()

        val systemInstruction = "You are Hesabyar's Elite Financial Advisor. Analyze the user's Persian transactions carefully and provide smart, structured financial recommendations in beautiful Persian. Be friendly, polite, action-oriented, and encouraging."

        when (val result = AiProvider.generateContent(
            config = providerConfig,
            prompt = prompt,
            systemInstruction = systemInstruction,
            temperature = 0.7
        )) {
            is AiProvider.ApiResult.Success -> result.text
            is AiProvider.ApiResult.Failure -> {
                AppLogger.e(TAG, "AI budget advice failed: ${result.error}")
                getOfflineAdvice(transactions, categories)
            }
        }
    }

    private fun getPersianCategoryName(category: String): String {
        return when (category) {
            "Food" -> "خوراک"
            "Transportation" -> "حمل و نقل"
            "Shopping" -> "خرید و پوشاک"
            "Bills" -> "قبض‌ها و اشتراک"
            "Installments" -> "اقساط"
            "Loans" -> "وام و امور اشخاص"
            "Income" -> "درآمد"
            else -> "سایر موارد"
        }
    }

    private fun formatTomanClean(amount: Long): String {
        val tomanValue = amount / 1000
        return try {
            val formatter = NumberFormat.getNumberInstance(Locale.US)
            formatter.maximumFractionDigits = 0
            formatter.format(tomanValue)
        } catch (e: Exception) {
            e.printStackTrace()
            tomanValue.toString()
        }
    }

    // High quality local rules budget advisor for offline mode
    fun getOfflineAdvice(transactions: List<Transaction>, categories: List<Category>): String {
        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }

        val categoryTotals = transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val highestCategoryId = categoryTotals.maxByOrNull { it.value }?.key
        val highestCategory = categories.find { it.id == highestCategoryId }

        val sb = java.lang.StringBuilder()
        sb.append("### 💡 توصیه‌های هوشمند بودجه (تحلیل هوش مصنوعی محلی)\n\n")

        if (transactions.isEmpty()) {
            sb.append("شما هنوز هیچ تراکنشی ثبت نکرده‌اید. برای دریافت تحلیل وضعیت بودجه، ابتدا چند تراکنش در بخش‌های دیگر برنامه ثبت کنید تا دستیار شما شروع به کار کند.")
            return sb.toString()
        }

        sb.append("بر اساس تحلیل تراکنش‌های ثبت شده شما در حسابیار، گزارش زیر آماده شده است:\n\n")

        val ratio = if (totalIncome > 0) (totalExpense.toDouble() / totalIncome.toDouble()) else 2.0
        if (ratio > 0.9) {
            sb.append("⚠️ **ناترازی و زنگ خطر مالی:** میزان هزینه‌های شما بسیار نزدیک به درآمد یا فراتر از آن است (**${(ratio * 100).toInt()}%** از درآمد شما خرج شده است!). توصیه اکید داریم که با کنترل فوری خریدهای غیرضروری، جلوی کسری بودجه یا کشیده شدن به سمت بدهی بیشتر را بگیرید.\n\n")
        } else if (ratio < 0.3 && totalIncome > 0) {
            sb.append("✅ **وضعیت فوق‌العاده پس‌انداز:** شما موفق شده‌اید بیش از ۷۰٪ از کل درآمد خود را حفظ و پس‌انداز کنید! این یک دستاورد مالی استثنایی است. پیشنهاد مشاور شما این است که این سرمایه انباشته شده را راکد نگذاشته و بخشی از آن را در بازارهای سودده مثل طلا، بورس یا صندوق‌های درآمد ثابت سرمایه‌گذاری کنید.\n\n")
        } else {
            sb.append("⚖️ **تعادل نسبی بودجه:** وضعیت دخل و خرج شما نسبتاً متعادل است و حدود **${(100 - ratio * 100).toInt()}%** از درآمدتان را پس‌انداز کرده‌اید. تلاش کنید با چالش‌های کوچک مالی (مثل کاهش ۱۰ درصدی هزینه‌های تفریحی) این نرخ طلایی پس‌انداز را افزایش دهید.\n\n")
        }

        if (highestCategory != null) {
            val catNameFarsi = highestCategory.name
            val catExpense = categoryTotals[highestCategoryId] ?: 0L
            sb.append("📊 ** تمرکز روی پرهزینه‌ترین بخش مخارج:**\n")
            sb.append("بزرگترین کانون مخارج شما مربوط به دسته‌بندی **$catNameFarsi** با مجموع مبلغ **${formatTomanClean(catExpense)}** تومان است.\n\n")
            sb.append("💡 **پیشنهاد تخصصی مشاور:** ")
            when (highestCategory.key) {
                "Food" -> sb.append("تدارک مواد غذایی خانگی به جای رستوران‌ها و کافه‌های غیرضروری و نوشتن لیست خریدهای خواربار قبل از مراجعه به فروشگاه، می‌تواند تا ۳۰ درصد هزینه‌های این دسته را کاهش دهد.")
                "Shopping" -> sb.append("بسیاری از خریدهای پوشاک یا وسایل شخصی جنبه احساسی دارند. قانون ۲۴ ساعت را اجرا کنید: برای هر خرید غیرضروری، ۲۴ ساعت صبر کنید. اگر پس از آن همچنان مایل بودید، اقدام کنید.")
                "Transportation" -> sb.append("هزینه‌های تردد خود را با استفاده ترکیبی از تاکسی‌های اشتراکی، مترو یا اتوبوس بهینه‌سازی کنید و تا حد امکان ترددهای انفرادی اتومبیل را محدود سازید.")
                "Bills" -> sb.append("سرویس‌ها و اشتراک‌های آنلاین غیرضروری که کمتر استفاده می‌شوند را لغو کنید. همچنین رعایت الگوهای بهینه مصرف انرژی می‌تواند هزینه‌های ثابت قبوض را کاهش دهد.")
                "Installments" -> sb.append("شما تعهدات قسطی سنگینی دارید. تا پایان یافتن اقساط فعلی، به هیچ عنوان خرید اقساطی یا تعهد مالی جدیدی برای خود ثبت نکنید.")
                "Loans" -> sb.append("بازپرداخت بدهی‌ها روند خوبی دارد. بدهی‌های دارای اولویت یا مبالغ بالاتر را زودتر تصفیه کنید تا ذهن شما آسوده‌تر شود.")
                else -> sb.append("سعی کنید جزئیات این هزینه‌های فرعی را ثبت کنید؛ نقاط تاریک مالی و هزینه‌های از دست رفته معمولا در این دسته‌بندی متفرقه مخفی می‌شوند.")
            }
            sb.append("\n\n")
        }

        sb.append("📌 **قوانین و راهکارهای جادویی مدیریت منابع مالی:**\n")
        sb.append("- **استراتژی ۵۰-۳۰-۲۰:** نیمی از درآمد را به اجاره و نیازهای اساسی، ۳۰ درصد را به علایق و خواسته‌ها و ۲۰ درصد باقیمانده را مستقیماً به پس‌انداز یا تسویه بدهی تخصیص دهید.\n")
        sb.append("- **پیشگیری از فرار مخارج:** کوچک‌ترین فاکتورها مانند خرید آب‌معدنی یا کرایه کوتاه را هم در حسابیار ثبت کنید. مجموع این مبالغ ناچیز در انتهای ماه چشمگیر خواهد شد.\n")
        sb.append("- **ایجاد صندوق اضطراری:** همیشه معادل ۳ الی ۶ برابر مخارج ماهانه خود را در یک حساب مجزا برای بروز حوادث غیرمترقبه ذخیره کنید تا ثبات مالی شما هرگز به لرزه در نیاید.")

        return sb.toString()
    }

    suspend fun getBudgetForecast(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>,
        config: AiProviderConfig? = null
    ): String = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "getBudgetForecast: config=${config?.let { "provider=${it.providerType}, isConfigured=${it.isConfigured}" } ?: "null"}")
        val providerConfig = config ?: AiProviderConfig()
        if (!providerConfig.isConfigured) {
            AppLogger.w(TAG, "AI provider not configured, using offline local budget forecast")
            return@withContext getOfflineForecast(transactions, loans, installments)
        }

        if (transactions.isEmpty() && installments.isEmpty()) {
            return@withContext "تراکنش یا قسطی در سیستم ثبت نشده است. برای پیش‌بینی دقیق بودجه ماه آینده، لازم است تراکنش‌ها یا تعهدات مالی خود را در حسابیار وارد کنید."
        }

        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val activeLoansCount = loans.filter { !it.isSettled }.size
        val upcomingInstallments = installments.filter { !it.isPaid }
        val totalUpcomingAmount = upcomingInstallments.sumOf { it.amount }
        
        val categoryReport = transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .entries.joinToString("\n") { (catId, sum) ->
                val cat = categories.find { it.id == catId }
                "- ${cat?.name ?: "سایر"}: ${formatTomanClean(sum)} تومان"
            }

        val installmentListPrompt = upcomingInstallments.take(15).joinToString("\n") { inst ->
            "- قسط: ${inst.title} | مبلغ: ${formatTomanClean(inst.amount)} تومان"
        }

        val promptText = """
            سلام. من یک حسابدار شخصی ایرانی دارم به نام «حسابیار».
            لطفاً تراکنش‌های مالی اخیر و تعهدات مالی پیش‌روی مرا تحلیل کرده و پیش‌بینی وضعیت بودجه و تراز مالی ماه آینده مرا به همراه یک هشدار هوشمند (Smart Alert) صمیمی و روان به زبان فارسی ارائه دهی.

            داده‌های کلی من:
            - پایش درآمد کل جاری: ${formatTomanClean(totalIncome)} تومان
            - پایش مخارج کل جاری: ${formatTomanClean(totalExpense)} تومان
            - اقساط پرداخت نشده در آینده نزدیک: ${upcomingInstallments.size} مورد با مبلغ کل تعهد ${formatTomanClean(totalUpcomingAmount)} تومان
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

        val systemInstruction = "You are Hesabyar's Elite Financial Advisor. Analyze the user's Persian transactions carefully and provide smart, structured financial recommendations in beautiful Persian. Be friendly, polite, action-oriented, and encouraging."

        when (val result = AiProvider.generateContent(
            config = providerConfig,
            prompt = promptText,
            systemInstruction = systemInstruction,
            temperature = 0.7
        )) {
            is AiProvider.ApiResult.Success -> result.text
            is AiProvider.ApiResult.Failure -> {
                AppLogger.e(TAG, "AI forecast failed: ${result.error}")
                "⚠️ اتصال به سرور ابری انجام نشد یا کلید معتبر نیست. پیش‌بینی محلی شما به شرح زیر است:\n\n" + 
                    getOfflineForecast(transactions, loans, installments)
            }
        }
    }

    // Local predictive forecasting offline rules fallback
    fun getOfflineForecast(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>
    ): String {
        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val unpaidInstallments = installments.filter { !it.isPaid }
        val upcomingInstallmentsSum = unpaidInstallments.sumOf { it.amount }
        
        // Estimate next month income and expense
        // If system is empty, return static tip
        if (transactions.isEmpty() && unpaidInstallments.isEmpty()) {
            return "هنوز اطلاعات تراکنش یا قسطی در حسابیار ثبت نشده است. لطفاً دخل و خرج‌های روزانه خود را وارد کنید تا پیش‌بینی هوشمند ماه آینده صادر شود."
        }
        
        val averageIncome = if (transactions.any { it.type == "INCOME" }) totalIncome else 0L
        val averageExpense = if (transactions.any { it.type == "EXPENSE" }) totalExpense else 0L
        
        val estimatedBalance = averageIncome - averageExpense - upcomingInstallmentsSum
        
        val sb = StringBuilder()
        sb.append("### 🔮 پیش‌بینی هوشمند وضعیت بودجه ماه آینده\n\n")
        
        sb.append("بررسی روندهای آماری تراکنش‌های ثبت شده شما و مطابقت آن با اقساط سررسید آینده، خروجی‌های زیر را ترسیم می‌کند:\n\n")
        
        sb.append("📋 **برآورد جریان نقدی ۳۰ روز پیش‌رو:**\n")
        sb.append("- 💵 **درآمد تخمینی:** ${formatTomanClean(averageIncome)} تومان\n")
        sb.append("- 💸 **مخارج تخمینی:** ${formatTomanClean(averageExpense)} تومان\n")
        sb.append("- 🗓️ **تعهد اقساط در شرف سررسید:** ${formatTomanClean(upcomingInstallmentsSum)} تومان\n")
        
        val formattedEstimatedBalance = formatTomanClean(kotlin.math.abs(estimatedBalance))
        if (estimatedBalance < 0) {
            sb.append("\n### 🚨 هشدار هوشمند: ریسک کسری بودجه در ماه بعد!\n")
            sb.append("با نگرانی خفیف به استحضار می‌رساند مخارج متوسط شما به همراه اقساط پیش رو، احتمالاً تراز نقدی شما در ماه آینده را با **کسری حدودی $formattedEstimatedBalance تومان** روبرو خواهد کرد.\n\n")
            sb.append("💡 **اقدامات اصلاحی فوری:**\n")
            sb.append("۱. **کنترل هزینه‌های غیرضروری:** برخی خریدهای چند روز اخیر مانند دسته‌بندی خرید یا تفریح را مسدود کنید.\n")
            sb.append("۲. **اولویت بازپرداخت:** در اوایل ماه جدید، مبلغ اقساط آینده را سریعاً اولویت‌بندی کرده و کنار بگذارید تا با تاخیر و جریمه مواجه نشوید.")
        } else {
            sb.append("\n### 🟢 هشدار هوشمند: وضعیت مالی پایدار و سبز\n")
            sb.append("خوشبختانه بررسی الگوی دخل و خرج نشان می‌دهد جریان درآمدی شما برای پوشش مخارج جاری و تصفیه اقساط کاملاً کافی است و پیش‌بینی می‌شود ماه آینده را با **مازاد بودجه حدودی $formattedEstimatedBalance تومان** پشت سر بگذارید.\n\n")
            sb.append("💡 **اقدامات توصیه‌ای مشاور:**\n")
            sb.append("۱. **پس‌انداز هدفمند:** پیشنهاد می‌شود بلافاصله پس از واریز درآمد جدید، حداقل ۱۵ درصد آن را به عنوان پس‌انداز طلایی به حساب مجزا انتقال دهید.\n")
            sb.append("۲. **خاکریز امن سرمایه‌گذاری:** با انباشت مازاد نقدی، به تدریج اقدام به ساخت سبد دارایی پایدار نمایید.")
        }
        
        return sb.toString()
    }

    fun calculateDebtToIncomeRatio(
        loans: List<Loan>,
        installments: List<Installment>,
        monthlyIncome: Long
    ): Double {
        val monthlyDebtPayments = installments.filter { !it.isPaid }.sumOf { it.amount } +
            loans.filter { !it.isSettled && it.type == "CREDITOR" }.sumOf {
                it.remainingAmount / 12
            }
        if (monthlyIncome <= 0 && monthlyDebtPayments > 0) return 1.0
        if (monthlyIncome <= 0) return 0.0
        return monthlyDebtPayments.toDouble() / monthlyIncome.toDouble()
    }

    fun predictTimeToGoal(
        currentSavings: Long,
        monthlySavings: Long,
        goalAmount: Long
    ): Int {
        if (monthlySavings <= 0) return -1
        val remaining = goalAmount - currentSavings
        return if (remaining > 0) {
            ((remaining + monthlySavings - 1) / monthlySavings).toInt()
        } else 0
    }

    fun getPersonalizedAdvice(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>
    ): String {
        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = totalIncome - totalExpense
        val debtToIncome = calculateDebtToIncomeRatio(loans, installments, totalIncome)
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
            sb.appendLine("📅 **اقساط در انتظار پرداخت:** ${upcomingInstallments.size} مورد (${formatTomanClean(totalUpcoming)} تومان)")
            upcomingInstallments.take(3).forEach { inst ->
                sb.appendLine("- ${inst.title}: ${formatTomanClean(inst.amount)} تومان")
            }
        }

        return sb.toString()
    }

    fun calculateFinancialHealthScore(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>
    ): Int {
        if (transactions.isEmpty()) return 0

        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = totalIncome - totalExpense

        var score = 50

        // Savings rate (max +25)
        if (totalIncome > 0) {
            val savingsRate = balance.toDouble() / totalIncome.toDouble()
            score += when {
                savingsRate >= 0.3 -> 25
                savingsRate >= 0.2 -> 20
                savingsRate >= 0.1 -> 10
                savingsRate >= 0 -> 0
                else -> -15
            }
        }

        // Debt-to-income (max +15)
        val debtRatio = calculateDebtToIncomeRatio(loans, installments, totalIncome)
        score += when {
            debtRatio <= 0.1 -> 15
            debtRatio <= 0.2 -> 10
            debtRatio <= 0.3 -> 5
            debtRatio <= 0.4 -> 0
            else -> -10
        }

        // Category diversification (+10 if spending across 3+ categories)
        val expenseCategories = transactions.filter { it.type == "EXPENSE" }
            .map { it.categoryId }.distinct().size
        score += when {
            expenseCategories >= 5 -> 10
            expenseCategories >= 3 -> 5
            else -> 0
        }

        return score.coerceIn(0, 100)
    }
}
