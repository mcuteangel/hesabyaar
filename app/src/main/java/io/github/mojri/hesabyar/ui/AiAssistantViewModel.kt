package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.api.AiConfigManager
import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.api.GeminiParser
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository: HesabyarRepositoryInterface = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao()
    )
    private val aiConfigManager = AiConfigManager(application)

    // AI Configuration
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

    fun getActiveConfig(): AiProviderConfig? = aiConfigManager.getActiveConfig()

    fun addAiConfig(config: AiProviderConfig) {
        val newConfig = aiConfigManager.addConfig(config)
        aiConfigs.value = aiConfigManager.loadConfigs()
        if (aiConfigs.value.size == 1) {
            activeConfigId.value = newConfig.id
            aiConfigManager.setActiveConfigId(newConfig.id)
        }
    }

    fun updateAiConfig(config: AiProviderConfig) {
        aiConfigManager.updateConfig(config)
        aiConfigs.value = aiConfigManager.loadConfigs()
    }

    fun deleteAiConfig(id: String) {
        aiConfigManager.deleteConfig(id)
        aiConfigs.value = aiConfigManager.loadConfigs()
        activeConfigId.value = aiConfigManager.getActiveConfigId() ?: ""
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

    private val _modelFetchState = MutableStateFlow<ModelFetchState>(ModelFetchState.Idle)
    val modelFetchState = _modelFetchState.asStateFlow()

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

    // AI Cache for Forecast and Advice
    private var lastForecastTime = 0L
    private var cachedForecast: String? = null
    private var lastAdviceTime = 0L
    private var cachedAdvice: String? = null
    private val AI_CACHE_DURATION_MS = 10 * 60 * 1000L

    private fun isForecastCacheValid(): Boolean {
        return cachedForecast != null && (System.currentTimeMillis() - lastForecastTime) < AI_CACHE_DURATION_MS
    }

    private fun isAdviceCacheValid(): Boolean {
        return cachedAdvice != null && (System.currentTimeMillis() - lastAdviceTime) < AI_CACHE_DURATION_MS
    }

    fun getCachedForecast(): String? = cachedForecast
    fun getCachedAdvice(): String? = cachedAdvice

    // Smart Parser
    private val _parserState = MutableStateFlow<ParserUIState>(ParserUIState.Idle)
    val parserState = _parserState.asStateFlow()

    fun parseSmartSentence(sentence: String, isOnlineMode: Boolean) {
        if (sentence.isBlank()) return
        viewModelScope.launch {
            _parserState.value = ParserUIState.Loading
            try {
                val config = if (isOnlineMode) aiConfigManager.getActiveConfig() else null
                AppLogger.d("AiAssistantViewModel", "parseSmartSentence: isOnlineMode=$isOnlineMode, config=${config?.let { "found(${it.providerType}, model=${it.model})" } ?: "null"}")
                val result = GeminiParser.parseSentence(sentence, config)
                if (result != null) {
                    _parserState.value = ParserUIState.Success(result)
                } else {
                    _parserState.value = ParserUIState.Error("خطا در تحلیل متن")
                }
            } catch (e: Exception) {
                AppLogger.e("AiAssistantViewModel", "parseSmartSentence failed", e)
                _parserState.value = ParserUIState.Error(e.localizedMessage ?: "خطای ناشناخته")
            }
        }
    }

    fun clearParserState() {
        _parserState.value = ParserUIState.Idle
    }

    fun approveParsedResult(result: ParsedResult, customDate: Long? = null) {
        viewModelScope.launch {
            try {
                val amountRial = result.amount
                val category = repository.getCategoryByKey(result.category)
                val categoryId = category?.id ?: 1L
                when (result.type) {
                    "INCOME", "EXPENSE" -> {
                        repository.insertTransaction(Transaction(
                            type = result.type,
                            categoryId = categoryId,
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
                _parserState.value = ParserUIState.Idle
            } catch (e: Exception) {
                AppLogger.e("AiAssistantViewModel", "approveParsedResult failed", e)
            }
        }
    }

    // Budget Advisor
    private val _advisorState = MutableStateFlow<AdvisorUIState>(AdvisorUIState.Idle)
    val advisorState = _advisorState.asStateFlow()

    fun fetchBudgetAdvice(
        transactions: List<Transaction>,
        categories: List<Category>,
        isOnlineMode: Boolean,
        forceRefresh: Boolean = false
    ) {
        if (!forceRefresh && isAdviceCacheValid()) {
            _advisorState.value = AdvisorUIState.Success(cachedAdvice!!)
            return
        }
        viewModelScope.launch {
            _advisorState.value = AdvisorUIState.Loading
            try {
                val config = if (isOnlineMode) aiConfigManager.getActiveConfig() else null
                val advice = BudgetAdvisor.getBudgetAdvice(transactions, categories, config)
                cachedAdvice = advice
                lastAdviceTime = System.currentTimeMillis()
                _advisorState.value = AdvisorUIState.Success(advice)
            } catch (e: Exception) {
                AppLogger.e("AiAssistantViewModel", "fetchBudgetAdvice failed", e)
                _advisorState.value = AdvisorUIState.Error(e.localizedMessage ?: "خطای ناشناخته در دریافت توصیه‌ها")
            }
        }
    }

    fun clearAdvisorState() {
        _advisorState.value = AdvisorUIState.Idle
    }

    // Budget Forecast
    private val _forecastState = MutableStateFlow<ForecastUIState>(ForecastUIState.Idle)
    val forecastState = _forecastState.asStateFlow()

    fun fetchBudgetForecast(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>,
        isOnlineMode: Boolean,
        forceRefresh: Boolean = false
    ) {
        if (!forceRefresh && isForecastCacheValid()) {
            _forecastState.value = ForecastUIState.Success(cachedForecast!!)
            return
        }
        viewModelScope.launch {
            _forecastState.value = ForecastUIState.Loading
            try {
                val config = if (isOnlineMode) aiConfigManager.getActiveConfig() else null
                val forecast = BudgetAdvisor.getBudgetForecast(
                    transactions, loans, installments, categories, config
                )
                cachedForecast = forecast
                lastForecastTime = System.currentTimeMillis()
                _forecastState.value = ForecastUIState.Success(forecast)
            } catch (e: Exception) {
                AppLogger.e("AiAssistantViewModel", "fetchBudgetForecast failed", e)
                _forecastState.value = ForecastUIState.Error(e.localizedMessage ?: "خطای ناشناخته در پیش‌بینی بودجه")
            }
        }
    }

    fun clearForecastState() {
        _forecastState.value = ForecastUIState.Idle
    }
}
