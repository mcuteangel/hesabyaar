package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BudgetAdvisor {
  private const val TAG = "BudgetAdvisor"

  suspend fun getBudgetAdvice(
    transactions: List<Transaction>,
    categories: List<Category>,
    config: AiProviderConfig? = null
  ): String =
    withContext(Dispatchers.IO) {
      val providerConfig = config ?: AiProviderConfig()
      if (!providerConfig.isConfigured) {
        AppLogger.w(TAG, "AI provider not configured, using offline local rules budget advisor")
        return@withContext getOfflineAdvice(transactions, categories)
      }
      if (transactions.isEmpty()) {
        return@withContext noTransactionsAdviceMessage()
      }

      val prompt = buildBudgetPrompt(transactions, categories)
      val systemInstruction =
        "You are Hesabyar's Elite Financial Advisor. Analyze the user's Persian transactions " +
          "carefully and provide smart, structured financial recommendations in beautiful Persian. " +
          "Be friendly, polite, action-oriented, and encouraging."

      val result =
        AiProvider.generateContent(
          config = providerConfig,
          prompt = prompt,
          systemInstruction = systemInstruction,
          temperature = 0.7
        )
      handleBudgetAdviceResult(result, transactions, categories)
    }

  private fun noTransactionsAdviceMessage(): String =
    "هنوز تراکنشی در حسابیار ثبت نشده است. لطفا چند تراکنش ثبت کنید تا " +
      "هوش مصنوعی بتواند بودجه شما را تحلیل کند."

  private fun buildBudgetPrompt(
    transactions: List<Transaction>,
    categories: List<Category>
  ): String {
    val summary = summarizeTransactions(transactions)
    val categoryReport = buildCategoryReport(transactions, categories)
    val transactionList = buildTransactionList(transactions, categories)

    return """
      سلام. من یک حسابدار شخصی ایرانی دارم به نام «حسابیار».
      لطفاً تراکنش‌های مالی اخیر مرا بررسی کرده و توصیه‌های هوشمند، کاربردی و روان به زبان فارسی برای مدیریت بهتر بودجه، کاهش هزینه‌ها و افزایش پس‌انداز به من ارائه بده.

      آمارهای کلی من:
      - کل درآمد ثبت شده: ${formatAmountClean(summary.income)}
      - کل هزینه‌های ثبت شده: ${formatAmountClean(summary.expense)}
      - تراز باقیمانده: ${formatAmountClean(summary.balance)}

      هزینه‌ها به تفکیک دسته‌بندی:
      $categoryReport

      لیست ۳۰ تراکنش آخر من:
      $transactionList

      لطفا با یک لحن صمیمی و حرفه‌ای (مشابه یک مشاور مالی باتجربه و دلسوز) تحلیل خودت رو ارائه بدی. 
      توصیه‌ها رو بخش‌بندی کن (مثلاً تحلیل کلی تراز مالی، بررسی دسته‌بندی هزینه‌های عمده، نکات کاهش مخارج خاص بر اساس تراکنشام، و چند پیشنهاد طلایی کاربردی). 
      از قالب‌بندی زیبای Markdown (بولد کردن، ایموجی‌ها، لیست‌های نشانه‌دار) استفاده کن تا خواندن آن راحت باشد.
      """.trimIndent()
  }

  private fun buildCategoryReport(
    transactions: List<Transaction>,
    categories: List<Category>
  ): String {
    val categoriesGroup =
      transactions
        .filter { it.type == "EXPENSE" }
        .groupBy { it.categoryId }
        .mapValues { it.value.sumOf { tx -> tx.amount } }

    return categoriesGroup.entries.joinToString("\n") { (catId, sum) ->
      val cat = categories.find { it.id == catId }
      "- ${cat?.name ?: "سایر"}: ${formatAmountClean(sum)}"
    }
  }

  private fun buildTransactionList(
    transactions: List<Transaction>,
    categories: List<Category>
  ): String =
    transactions.take(30).joinToString("\n") { tx ->
      val typeStr = if (tx.type == "INCOME") "درآمد" else "هزینه"
      val cat = categories.find { it.id == tx.categoryId }
      "- ${cat?.name ?: "سایر"} | $typeStr | ${formatAmountClean(tx.amount)} | شرح: ${tx.description}"
    }

  private suspend fun handleBudgetAdviceResult(
    result: AiProvider.ApiResult,
    transactions: List<Transaction>,
    categories: List<Category>
  ): String =
    when (result) {
      is AiProvider.ApiResult.Success -> validateOrFallback(result.text, transactions, categories)
      is AiProvider.ApiResult.Failure -> {
        AppLogger.e(TAG, "AI budget advice failed: ${result.error}")
        getOfflineAdvice(transactions, categories)
      }
    }

  private suspend fun validateOrFallback(
    text: String,
    transactions: List<Transaction>,
    categories: List<Category>
  ): String {
    val validation = io.github.mojri.hesabyar.rust.RustBridge.validateAiAdvice(text)
    if (!validation.isValid && io.github.mojri.hesabyar.rust.RustBridge.isAvailable) {
      AppLogger.w(TAG, "AI advice failed validation, using offline: ${validation.warnings}")
      return getOfflineAdvice(transactions, categories)
    }
    if (validation.wasTruncated) {
      AppLogger.d(TAG, "AI advice truncated: ${validation.warnings}")
    }
    return validation.sanitizedText
  }

  private fun getPersianCategoryName(category: String): String =
    when (category) {
      "Food" -> "خوراک"
      "Transportation" -> "حمل و نقل"
      "Shopping" -> "خرید و پوشاک"
      "Bills" -> "قبض‌ها و اشتراک"
      "Installments" -> "اقساط"
      "Loans" -> "وام و امور اشخاص"
      "Income" -> "درآمد"
      else -> "سایر موارد"
    }

  private class TxSummary(
    val income: Long,
    val expense: Long
  ) {
    val balance get() = income - expense
  }

  private fun summarizeTransactions(transactions: List<Transaction>): TxSummary {
    val income = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    return TxSummary(income, expense)
  }

  private fun formatAmountClean(amount: Long): String = CurrencyFormatter.format(amount)

  // High quality local rules budget advisor for offline mode
  fun getOfflineAdvice(
    transactions: List<Transaction>,
    categories: List<Category>
  ): String {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.getOfflineBudgetAdviceSync(
        transactions.map {
          io.github.mojri.hesabyar.rust.Transaction(
            id = it.id,
            txType =
              io.github.mojri.hesabyar.rust.TransactionType
                .valueOf(it.type),
            categoryId = it.categoryId,
            amount = it.amount,
            description = it.description,
            personName = it.personName,
            date = it.date,
            dueDate = it.dueDate,
            installmentId = it.installmentId
          )
        },
        categories.map {
          io.github.mojri.hesabyar.rust.Category(
            id = it.id,
            name = it.name,
            key = it.key,
            icon = it.icon,
            color = it.color,
            categoryType = it.type,
            isDefault = it.isDefault
          )
        }
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
    val categoriesGroup =
      transactions
        .filter { it.type == "EXPENSE" }
        .groupBy { it.categoryId }
        .mapValues { it.value.sumOf { tx -> tx.amount } }
    val categoryReport =
      categoriesGroup.entries.joinToString("\n") { (catId, sum) ->
        val cat = categories.find { it.id == catId }
        "- ${cat?.name ?: "سایر"}: ${formatAmountClean(sum)}"
      }

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
      summary.income > 0 && summary.balance.toDouble() / summary.income.toDouble() > 0.2 ->
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
    config: AiProviderConfig? = null
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
        return@withContext getOfflineForecast(transactions, loans, installments)
      }

      if (transactions.isEmpty() && installments.isEmpty()) {
        val message =
          "تراکنش یا قسطی در سیستم ثبت نشده است. برای پیش‌بینی دقیق بودجه ماه آینده، لازم است " +
            "تراکنش‌ها یا تعهدات مالی خود را در حسابیار وارد کنید."
        return@withContext message
      }

      val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
      val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
      val activeLoansCount = loans.filter { !it.isSettled }.size
      val upcomingInstallments = installments.filter { !it.isPaid }
      val totalUpcomingAmount = upcomingInstallments.sumOf { it.amount }

      val categoryReport =
        transactions
          .filter { it.type == "EXPENSE" }
          .groupBy { it.categoryId }
          .mapValues { it.value.sumOf { tx -> tx.amount } }
          .entries
          .joinToString("\n") { (catId, sum) ->
            val cat = categories.find { it.id == catId }
            "- ${cat?.name ?: "سایر"}: ${formatAmountClean(sum)}"
          }

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
              getOfflineForecast(transactions, loans, installments)
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
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.getOfflineForecastSync(
        transactions.map {
          io.github.mojri.hesabyar.rust.Transaction(
            id = it.id,
            txType =
              io.github.mojri.hesabyar.rust.TransactionType
                .valueOf(it.type),
            categoryId = it.categoryId,
            amount = it.amount,
            description = it.description,
            personName = it.personName,
            date = it.date,
            dueDate = it.dueDate,
            installmentId = it.installmentId
          )
        },
        loans.map {
          io.github.mojri.hesabyar.rust.Loan(
            id = it.id,
            personName = it.personName,
            loanType = it.type,
            originalAmount = it.originalAmount,
            remainingAmount = it.remainingAmount,
            description = it.description,
            date = it.date,
            isSettled = it.isSettled
          )
        },
        installments.map {
          io.github.mojri.hesabyar.rust.Installment(
            id = it.id,
            title = it.title,
            amount = it.amount,
            dueDate = it.dueDate,
            isPaid = it.isPaid,
            reminderEnabled = it.reminderEnabled,
            notes = it.notes
          )
        }
      )
    if (rustResult.isNotEmpty()) return rustResult

    // Rust unavailable: serve a baseline forecast from local data instead of a false "insufficient data" message.
    return buildLocalOfflineForecast(transactions, loans, installments)
  }

  // Local, dependency-free baseline forecast used when the Rust core is unavailable.
  private fun buildLocalOfflineForecast(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>
  ): String {
    val summary = summarizeTransactions(transactions)
    val upcomingInstallments = installments.filter { !it.isPaid }
    val totalUpcoming = upcomingInstallments.sumOf { it.amount }
    val activeLoans = loans.filter { !it.isSettled }
    val totalDebt = activeLoans.sumOf { it.remainingAmount }

    if (transactions.isEmpty() && installments.isEmpty() && loans.isEmpty()) {
      return "تراکنش یا قسطی برای پیش‌بینی ثبت نشده است. لطفا اطلاعات مالی خود را وارد کنید."
    }

    val projectedBalance = summary.balance - totalUpcoming
    val sb = StringBuilder()
    sb.appendLine("### 🔮 پیش‌بینی بودجه محلی (آفلاین)")
    sb.appendLine()
    sb.appendLine("**تراز فعلی:** ${formatAmountClean(summary.balance)}")
    sb.appendLine("**اقساط پیش‌رو:** ${upcomingInstallments.size} مورد به مبلغ ${formatAmountClean(totalUpcoming)}")
    if (activeLoans.isNotEmpty()) {
      sb.appendLine("**بدهی‌های فعال:** ${activeLoans.size} مورد به مبلغ ${formatAmountClean(totalDebt)}")
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
    monthlyIncome: Long
  ): Double =
    io.github.mojri.hesabyar.rust.RustBridge.calculateDebtToIncomeRatioSync(
      loans.map {
        io.github.mojri.hesabyar.rust.Loan(
          id = it.id,
          personName = it.personName,
          loanType = it.type,
          originalAmount = it.originalAmount,
          remainingAmount = it.remainingAmount,
          description = it.description,
          date = it.date,
          isSettled = it.isSettled
        )
      },
      installments.map {
        io.github.mojri.hesabyar.rust.Installment(
          id = it.id,
          title = it.title,
          amount = it.amount,
          dueDate = it.dueDate,
          isPaid = it.isPaid,
          reminderEnabled = it.reminderEnabled,
          notes = it.notes
        )
      },
      monthlyIncome
    )

  fun predictTimeToGoal(
    currentSavings: Long,
    monthlySavings: Long,
    goalAmount: Long
  ): Int =
    io.github.mojri.hesabyar.rust.RustBridge.predictTimeToGoalSync(
      currentSavings,
      monthlySavings,
      goalAmount
    )

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
    categories: List<Category>
  ): Int =
    io.github.mojri.hesabyar.rust.RustBridge.calculateFinancialHealthScoreSync(
      transactions.map {
        io.github.mojri.hesabyar.rust.Transaction(
          id = it.id,
          txType =
            io.github.mojri.hesabyar.rust.TransactionType
              .valueOf(it.type),
          categoryId = it.categoryId,
          amount = it.amount,
          description = it.description,
          personName = it.personName,
          date = it.date,
          dueDate = it.dueDate,
          installmentId = it.installmentId
        )
      },
      loans.map {
        io.github.mojri.hesabyar.rust.Loan(
          id = it.id,
          personName = it.personName,
          loanType = it.type,
          originalAmount = it.originalAmount,
          remainingAmount = it.remainingAmount,
          description = it.description,
          date = it.date,
          isSettled = it.isSettled
        )
      },
      installments.map {
        io.github.mojri.hesabyar.rust.Installment(
          id = it.id,
          title = it.title,
          amount = it.amount,
          dueDate = it.dueDate,
          isPaid = it.isPaid,
          reminderEnabled = it.reminderEnabled,
          notes = it.notes
        )
      },
      categories.map {
        io.github.mojri.hesabyar.rust.Category(
          id = it.id,
          name = it.name,
          key = it.key,
          icon = it.icon,
          color = it.color,
          categoryType = it.type,
          isDefault = it.isDefault
        )
      }
    )
}
