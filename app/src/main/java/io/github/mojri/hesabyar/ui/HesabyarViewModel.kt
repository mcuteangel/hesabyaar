package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.api.AiConfigManager
import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.api.GeminiParser
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

class HesabyarViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao()
    )

    private val sharedPrefs = application.getSharedPreferences("hesabyar_prefs", android.content.Context.MODE_PRIVATE)
    private val aiConfigManager = AiConfigManager(application)

    var isDarkMode = mutableStateOf(sharedPrefs.getBoolean("dark_mode", true))
        private set

    fun toggleDarkMode() {
        isDarkMode.value = !isDarkMode.value
        sharedPrefs.edit().putBoolean("dark_mode", isDarkMode.value).apply()
    }

    // AI Multi-Config State
    var aiConfigs = mutableStateOf(aiConfigManager.loadConfigs())
        private set

    var activeConfigId = mutableStateOf(aiConfigManager.getActiveConfigId() ?: "")
        private set

    var isOnlineMode = mutableStateOf(aiConfigManager.isOnlineMode())
        private set

    fun toggleOnlineMode() {
        isOnlineMode.value = !isOnlineMode.value
        aiConfigManager.setOnlineMode(isOnlineMode.value)
    }

    // AI Result Caching
    private var lastForecastTime = 0L
    private var cachedForecast: String? = null
    private var lastAdviceTime = 0L
    private var cachedAdvice: String? = null
    private val AI_CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutes

    private fun isForecastCacheValid(): Boolean {
        return cachedForecast != null && (System.currentTimeMillis() - lastForecastTime) < AI_CACHE_DURATION_MS
    }

    private fun isAdviceCacheValid(): Boolean {
        return cachedAdvice != null && (System.currentTimeMillis() - lastAdviceTime) < AI_CACHE_DURATION_MS
    }

    fun getCachedForecast(): String? = cachedForecast
    fun getCachedAdvice(): String? = cachedAdvice

    fun getActiveConfig(): AiProviderConfig? = aiConfigManager.getActiveConfig()

    fun addAiConfig(config: AiProviderConfig) {
        val newConfig = aiConfigManager.addConfig(config)
        aiConfigs.value = aiConfigManager.loadConfigs()
        if (aiConfigs.value.size == 1) {
            activeConfigId.value = newConfig.id
            aiConfigManager.setActiveConfigId(newConfig.id)
        }
        showMessage("تنظیمات جدید ذخیره شد")
    }

    fun updateAiConfig(config: AiProviderConfig) {
        aiConfigManager.updateConfig(config)
        aiConfigs.value = aiConfigManager.loadConfigs()
        showMessage("تنظیمات به‌روزرسانی شد")
    }

    fun deleteAiConfig(id: String) {
        aiConfigManager.deleteConfig(id)
        aiConfigs.value = aiConfigManager.loadConfigs()
        activeConfigId.value = aiConfigManager.getActiveConfigId() ?: ""
        showMessage("تنظیمات حذف شد")
    }

    fun setActiveConfig(id: String) {
        activeConfigId.value = id
        aiConfigManager.setActiveConfigId(id)
    }

    fun isAiConfigured(): Boolean = aiConfigManager.getActiveConfig()?.isConfigured == true

    fun getProviderStatusText(): String {
        val config = aiConfigManager.getActiveConfig()
        return if (config != null && config.isConfigured) {
            "${config.displayName} | ${config.model}"
        } else {
            "تنظیم نشده (حالت آفلاین)"
        }
    }

    // Model fetching state
    private val _modelFetchState = MutableStateFlow<ModelFetchState>(ModelFetchState.Idle)
    val modelFetchState = _modelFetchState.asStateFlow()

    fun getAiLogs(): List<AppLogger.LogEntry> = AppLogger.getAiLogs()
    fun clearLogs() = AppLogger.clear()

    fun fetchModels(providerType: AiProviderType, apiKey: String, baseUrl: String? = null) {
        viewModelScope.launch {
            _modelFetchState.value = ModelFetchState.Loading
            try {
                val cached = aiConfigManager.getCachedModels(providerType)
                if (cached != null && !cached.isExpired) {
                    _modelFetchState.value = ModelFetchState.Success(cached.models)
                    return@launch
                }

                val models = AiProvider.fetchModels(providerType, apiKey, baseUrl)
                if (models.isNotEmpty()) {
                    val modelIds = models.map { it.id }
                    aiConfigManager.cacheModels(providerType, modelIds)
                    _modelFetchState.value = ModelFetchState.Success(modelIds)
                } else {
                    _modelFetchState.value = ModelFetchState.Error("مدلی یافت نشد")
                }
            } catch (e: Exception) {
                _modelFetchState.value = ModelFetchState.Error(e.localizedMessage ?: "خطا در دریافت مدل‌ها")
            }
        }
    }

    fun clearModelFetchState() {
        _modelFetchState.value = ModelFetchState.Idle
    }

    // Flows from database
    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<Loan>> = repository.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments: StateFlow<List<Installment>> = repository.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Feedback state
    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage = _uiMessage.asSharedFlow()

    fun showMessage(message: String) {
        viewModelScope.launch {
            _uiMessage.emit(message)
        }
    }

    // Core Dashboard Calculations
    val dashboardState = combine(transactions, loans, installments) { trans, loanList, instList ->
        var totalIncome = 0L
        var totalExpense = 0L
        var monthlyIncome = 0L
        var monthlyExpense = 0L

        val now = System.currentTimeMillis()
        val oneMonthAgo = now - (30L * 24L * 60L * 60L * 1000L)

        trans.forEach {
            if (it.type == "INCOME") {
                totalIncome += it.amount
                if (it.date >= oneMonthAgo) monthlyIncome += it.amount
            } else {
                totalExpense += it.amount
                if (it.date >= oneMonthAgo) monthlyExpense += it.amount
            }
        }

        var debtorsTotal = 0L
        var creditorsTotal = 0L
        loanList.filter { !it.isSettled }.forEach {
            if (it.type == "DEBTOR") {
                debtorsTotal += it.remainingAmount
            } else {
                creditorsTotal += it.remainingAmount
            }
        }

        val upcomingIns = instList.filter { !it.isPaid }.sortedBy { it.dueDate }

        DashboardData(
            currentBalance = totalIncome - totalExpense,
            monthlyExpenses = monthlyExpense,
            monthlyIncome = monthlyIncome,
            debtorsTotal = debtorsTotal,
            creditorsTotal = creditorsTotal,
            upcomingInstallments = upcomingIns
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardData())

    // Transactions operations
    fun addTransaction(type: String, category: String, amount: Long, description: String, personName: String? = null, customDate: Long? = null) {
        viewModelScope.launch {
            repository.insertTransaction(Transaction(
                type = type,
                category = category,
                amount = amount,
                description = description,
                personName = personName,
                date = customDate ?: System.currentTimeMillis()
            ))
            showMessage("تراکنش با موفقیت ثبت شد")
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            showMessage("تراکنش حذف شد")
        }
    }

    // Smart Parser integration
    private val _parserState = MutableStateFlow<ParserUIState>(ParserUIState.Idle)
    val parserState = _parserState.asStateFlow()

    fun parseSmartSentence(sentence: String) {
        if (sentence.isBlank()) return
        viewModelScope.launch {
            _parserState.value = ParserUIState.Loading
            try {
                val isOnline = isOnlineMode.value
                val config = if (isOnline) aiConfigManager.getActiveConfig() else null
                AppLogger.d("HesabyarViewModel", "parseSmartSentence: isOnlineMode=$isOnline, config=${config?.let { "found(${it.providerType}, model=${it.model})" } ?: "null"}")
                val result = GeminiParser.parseSentence(sentence, config)
                if (result != null) {
                    _parserState.value = ParserUIState.Success(result)
                } else {
                    _parserState.value = ParserUIState.Error("خطا در تحلیل متن")
                }
            } catch (e: Exception) {
                AppLogger.e("HesabyarViewModel", "parseSmartSentence failed", e)
                _parserState.value = ParserUIState.Error(e.localizedMessage ?: "خطای ناشناخته")
            }
        }
    }

    fun clearParserState() {
        _parserState.value = ParserUIState.Idle
    }

    // Budget Advisor integration state
    private val _advisorState = MutableStateFlow<AdvisorUIState>(AdvisorUIState.Idle)
    val advisorState = _advisorState.asStateFlow()

    fun fetchBudgetAdvice(forceRefresh: Boolean = false) {
        if (!forceRefresh && isAdviceCacheValid()) {
            AppLogger.d("HesabyarViewModel", "fetchBudgetAdvice: using cached result")
            _advisorState.value = AdvisorUIState.Success(cachedAdvice!!)
            return
        }
        viewModelScope.launch {
            _advisorState.value = AdvisorUIState.Loading
            try {
                val isOnline = isOnlineMode.value
                val config = if (isOnline) aiConfigManager.getActiveConfig() else null
                AppLogger.d("HesabyarViewModel", "fetchBudgetAdvice: isOnlineMode=$isOnline, config=${config?.let { "found(${it.providerType})" } ?: "null"}")
                val currentTransactions = transactions.value
                val advice = io.github.mojri.hesabyar.api.BudgetAdvisor.getBudgetAdvice(currentTransactions, config)
                cachedAdvice = advice
                lastAdviceTime = System.currentTimeMillis()
                _advisorState.value = AdvisorUIState.Success(advice)
            } catch (e: Exception) {
                AppLogger.e("HesabyarViewModel", "fetchBudgetAdvice failed", e)
                _advisorState.value = AdvisorUIState.Error(e.localizedMessage ?: "خطای ناشناخته در دریافت توصیه‌ها")
            }
        }
    }

    fun clearAdvisorState() {
        _advisorState.value = AdvisorUIState.Idle
    }

    // Budget Forecast integration state
    private val _forecastState = MutableStateFlow<ForecastUIState>(ForecastUIState.Idle)
    val forecastState = _forecastState.asStateFlow()

    fun fetchBudgetForecast(forceRefresh: Boolean = false) {
        if (!forceRefresh && isForecastCacheValid()) {
            AppLogger.d("HesabyarViewModel", "fetchBudgetForecast: using cached result")
            _forecastState.value = ForecastUIState.Success(cachedForecast!!)
            return
        }
        viewModelScope.launch {
            _forecastState.value = ForecastUIState.Loading
            try {
                val isOnline = isOnlineMode.value
                val config = if (isOnline) aiConfigManager.getActiveConfig() else null
                AppLogger.d("HesabyarViewModel", "fetchBudgetForecast: isOnlineMode=$isOnline, config=${config?.let { "found(${it.providerType})" } ?: "null"}")
                val currentTransactions = transactions.value
                val currentLoans = loans.value
                val currentInstallments = installments.value
                val forecast = io.github.mojri.hesabyar.api.BudgetAdvisor.getBudgetForecast(
                    currentTransactions,
                    currentLoans,
                    currentInstallments,
                    config
                )
                cachedForecast = forecast
                lastForecastTime = System.currentTimeMillis()
                _forecastState.value = ForecastUIState.Success(forecast)
            } catch (e: Exception) {
                AppLogger.e("HesabyarViewModel", "fetchBudgetForecast failed", e)
                _forecastState.value = ForecastUIState.Error(e.localizedMessage ?: "خطای ناشناخته در پیش‌بینی بودجه")
            }
        }
    }

    fun clearForecastState() {
        _forecastState.value = ForecastUIState.Idle
    }

    fun approveParsedResult(result: ParsedResult, customDate: Long? = null) {
        viewModelScope.launch {
            try {
                val amountRial = (result.amount * 1000).toLong()
                when (result.type) {
                    "INCOME", "EXPENSE" -> {
                        repository.insertTransaction(Transaction(
                            type = result.type,
                            category = result.category,
                            amount = amountRial,
                            description = result.description,
                            personName = result.personName,
                            date = customDate ?: System.currentTimeMillis()
                        ))
                    }
                    "LOAN_DEBTOR", "LOAN_CREDITOR" -> {
                        val loanType = if (result.type == "LOAN_DEBTOR") "DEBTOR" else "CREDITOR"
                        repository.insertLoan(Loan(
                            personName = result.personName ?: "نامشخص",
                            type = loanType,
                            originalAmount = amountRial,
                            remainingAmount = amountRial,
                            description = result.description,
                            date = customDate ?: System.currentTimeMillis()
                        ))
                    }
                    "INSTALLMENT" -> {
                        val days = result.daysFromNow ?: 30
                        val due = customDate ?: (System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L))
                        repository.insertInstallment(Installment(
                            title = result.title ?: "قسط دستیار",
                            amount = amountRial,
                            dueDate = due,
                            notes = result.description
                        ))
                    }
                }
                showMessage("اطلاعات با موفقیت ذخیره شد")
                _parserState.value = ParserUIState.Idle
            } catch (e: Exception) {
                showMessage("خطا در ذخیره اطلاعات")
            }
        }
    }

    // Loans operations
    fun addLoan(personName: String, type: String, amount: Long, description: String, customDate: Long? = null) {
        viewModelScope.launch {
            repository.insertLoan(Loan(
                personName = personName,
                type = type,
                originalAmount = amount,
                remainingAmount = amount,
                description = description,
                date = customDate ?: System.currentTimeMillis()
            ))
            showMessage("امور مالی اشخاص با موفقیت ثبت شد")
        }
    }

    fun makeRepayment(loanId: Long, amount: Long, notes: String) {
        viewModelScope.launch {
            val success = repository.addPaymentToLoan(loanId, amount, notes)
            if (success) {
                showMessage("پرداخت با موفقیت ثبت شد")
            } else {
                showMessage("خطا در ثبت پرداخت")
            }
        }
    }

    fun getPaymentHistory(loanId: Long): Flow<List<PaymentHistory>> {
        return repository.getPaymentHistoryForLoan(loanId)
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch {
            repository.deleteLoan(loan)
            showMessage("مورد قرضی حذف شد")
        }
    }

    // Installments operations
    fun addInstallment(title: String, amount: Long, dueDate: Long, reminderEnabled: Boolean, notes: String) {
        viewModelScope.launch {
            repository.insertInstallment(Installment(
                title = title,
                amount = amount,
                dueDate = dueDate,
                reminderEnabled = reminderEnabled,
                notes = notes
            ))
            showMessage("قسط جدید اضافه شد")
        }
    }

    fun toggleInstallmentPaid(installment: Installment) {
        viewModelScope.launch {
            repository.updateInstallment(installment.copy(isPaid = !installment.isPaid))
            showMessage(if (!installment.isPaid) "قسط به عنوان پرداخت شده علامت‌گذاری شد" else "قسط پرداخت نشده شد")
        }
    }

    fun deleteInstallment(installment: Installment) {
        viewModelScope.launch {
            repository.deleteInstallment(installment)
            showMessage("قسط حذف شد")
        }
    }

    // Dynamic Backup and Restore using direct JSON serialization
    fun exportBackupToFile(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                val rootJson = JSONObject()
                
                // Get snapshots of current data
                val curTrans = transactions.value
                val curLoans = loans.value
                val curInstallments = installments.value
                
                // Get payment histories for all loans
                val allPayments = ArrayList<PaymentHistory>()
                curLoans.forEach { loan ->
                    val history = repository.getPaymentHistoryForLoan(loan.id).firstOrNull() ?: emptyList()
                    allPayments.addAll(history)
                }

                // Serialize Transactions
                val transArray = JSONArray()
                curTrans.forEach {
                    transArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("type", it.type)
                        put("category", it.category)
                        put("amount", it.amount.toLong())
                        put("description", it.description)
                        put("personName", it.personName ?: "")
                        put("date", it.date)
                        put("dueDate", it.dueDate ?: 0L)
                        put("installmentId", it.installmentId ?: 0L)
                    })
                }
                rootJson.put("transactions", transArray)

                // Serialize Loans
                val loansArray = JSONArray()
                curLoans.forEach {
                    loansArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("personName", it.personName)
                        put("type", it.type)
                        put("originalAmount", it.originalAmount)
                        put("remainingAmount", it.remainingAmount)
                        put("description", it.description)
                        put("date", it.date)
                        put("isSettled", it.isSettled)
                    })
                }
                rootJson.put("loans", loansArray)

                // Serialize Installments
                val instArray = JSONArray()
                curInstallments.forEach {
                    instArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("title", it.title)
                        put("amount", it.amount.toLong())
                        put("dueDate", it.dueDate)
                        put("isPaid", it.isPaid)
                        put("reminderEnabled", it.reminderEnabled)
                        put("notes", it.notes)
                    })
                }
                rootJson.put("installments", instArray)

                // Serialize Payments
                val paymentsArray = JSONArray()
                allPayments.forEach {
                    paymentsArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("loanId", it.loanId)
                        put("amount", it.amount.toLong())
                        put("date", it.date)
                        put("notes", it.notes)
                    })
                }
                rootJson.put("paymentHistories", paymentsArray)

                outputStream.use { os ->
                    os.write(rootJson.toString(2).toByteArray())
                }
                showMessage("پشتیبان‌گیری با موفقیت انجام شد")
            } catch (e: Exception) {
                showMessage("خطا در پشتیبان‌گیری")
            }
        }
    }

    fun importBackupFromFile(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val text = inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(text)

                val transList = ArrayList<Transaction>()
                val transArray = rootJson.optJSONArray("transactions") ?: JSONArray()
                for (i in 0 until transArray.length()) {
                    val obj = transArray.getJSONObject(i)
                    transList.add(Transaction(
                        type = obj.getString("type"),
                        category = obj.getString("category"),
                        amount = obj.getLong("amount"),
                        description = obj.getString("description"),
                        personName = obj.optString("personName").let { if (it.isBlank()) null else it },
                        date = obj.getLong("date"),
                        dueDate = obj.optLong("dueDate").let { if (it == 0L) null else it },
                        installmentId = obj.optLong("installmentId").let { if (it == 0L) null else it }
                    ))
                }

                val loanList = ArrayList<Loan>()
                val loansArray = rootJson.optJSONArray("loans") ?: JSONArray()
                for (i in 0 until loansArray.length()) {
                    val obj = loansArray.getJSONObject(i)
                    loanList.add(Loan(
                        personName = obj.getString("personName"),
                        type = obj.getString("type"),
                        originalAmount = obj.getLong("originalAmount"),
                        remainingAmount = obj.getLong("remainingAmount"),
                        description = obj.getString("description"),
                        date = obj.getLong("date"),
                        isSettled = obj.getBoolean("isSettled")
                    ))
                }

                val instList = ArrayList<Installment>()
                val instArray = rootJson.optJSONArray("installments") ?: JSONArray()
                for (i in 0 until instArray.length()) {
                    val obj = instArray.getJSONObject(i)
                    instList.add(Installment(
                        title = obj.getString("title"),
                        amount = obj.getLong("amount"),
                        dueDate = obj.getLong("dueDate"),
                        isPaid = obj.getBoolean("isPaid"),
                        reminderEnabled = obj.getBoolean("reminderEnabled"),
                        notes = obj.optString("notes", "")
                    ))
                }

                val pmList = ArrayList<PaymentHistory>()
                val paymentsArray = rootJson.optJSONArray("paymentHistories") ?: JSONArray()
                for (i in 0 until paymentsArray.length()) {
                    val obj = paymentsArray.getJSONObject(i)
                    pmList.add(PaymentHistory(
                        loanId = obj.getLong("loanId"),
                        amount = obj.getLong("amount"),
                        date = obj.getLong("date"),
                        notes = obj.optString("notes", "")
                    ))
                }

                repository.importBackup(transList, loanList, instList, pmList)
                showMessage("بازیابی با موفقیت انجام شد")
            } catch (e: Exception) {
                showMessage("خطا در بازیابی فایل پشتیبان")
            }
        }
    }
}

data class DashboardData(
    val currentBalance: Long = 0L,
    val monthlyExpenses: Long = 0L,
    val monthlyIncome: Long = 0L,
    val debtorsTotal: Long = 0L,
    val creditorsTotal: Long = 0L,
    val upcomingInstallments: List<Installment> = emptyList()
)

sealed interface ParserUIState {
    object Idle : ParserUIState
    object Loading : ParserUIState
    data class Success(val result: ParsedResult) : ParserUIState
    data class Error(val message: String) : ParserUIState
}

sealed interface AdvisorUIState {
    object Idle : AdvisorUIState
    object Loading : AdvisorUIState
    data class Success(val advice: String) : AdvisorUIState
    data class Error(val message: String) : AdvisorUIState
}

sealed interface ForecastUIState {
    object Idle : ForecastUIState
    object Loading : ForecastUIState
    data class Success(val forecast: String) : ForecastUIState
    data class Error(val message: String) : ForecastUIState
}

sealed interface ModelFetchState {
    object Idle : ModelFetchState
    object Loading : ModelFetchState
    data class Success(val models: List<String>) : ModelFetchState
    data class Error(val message: String) : ModelFetchState
}

