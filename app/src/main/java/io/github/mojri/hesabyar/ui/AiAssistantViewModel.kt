package io.github.mojri.hesabyar.ui

import android.content.Context
import java.io.IOException
import retrofit2.HttpException
import android.database.sqlite.SQLiteException
import com.squareup.moshi.JsonDataException
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.*
import io.github.mojri.hesabyar.domain.usecase.GetBudgetAdviceUseCase
import io.github.mojri.hesabyar.domain.usecase.GetForecastUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageAiConfigUseCase
import io.github.mojri.hesabyar.domain.usecase.ParseTransactionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val parseTransactionUseCase: ParseTransactionUseCase,
    private val getBudgetAdviceUseCase: GetBudgetAdviceUseCase,
    private val getForecastUseCase: GetForecastUseCase,
    private val manageAiConfigUseCase: ManageAiConfigUseCase
) : ViewModel() {

    private val sharedPrefs = application.getSharedPreferences("ai_cache_prefs", Context.MODE_PRIVATE)

    var aiConfigs = mutableStateOf(manageAiConfigUseCase.loadConfigs())
        private set

    var activeConfigId = mutableStateOf(manageAiConfigUseCase.getActiveConfigId() ?: "")
        private set

    var isOnlineMode = mutableStateOf(manageAiConfigUseCase.isOnlineMode())
        private set

    fun toggleOnlineMode() {
        isOnlineMode.value = !isOnlineMode.value
        manageAiConfigUseCase.setOnlineMode(isOnlineMode.value)
    }

    fun getActiveConfig() = manageAiConfigUseCase.getActiveConfig()

    fun addAiConfig(config: io.github.mojri.hesabyar.api.AiProviderConfig) {
        val newConfig = manageAiConfigUseCase.addConfig(config)
        aiConfigs.value = manageAiConfigUseCase.loadConfigs()
        if (aiConfigs.value.size == 1) {
            activeConfigId.value = newConfig.id
            manageAiConfigUseCase.setActiveConfigId(newConfig.id)
        }
    }

    fun updateAiConfig(config: io.github.mojri.hesabyar.api.AiProviderConfig) {
        manageAiConfigUseCase.updateConfig(config)
        aiConfigs.value = manageAiConfigUseCase.loadConfigs()
    }

    fun deleteAiConfig(id: String) {
        manageAiConfigUseCase.deleteConfig(id)
        aiConfigs.value = manageAiConfigUseCase.loadConfigs()
        activeConfigId.value = manageAiConfigUseCase.getActiveConfigId() ?: ""
    }

    fun setActiveConfig(id: String) {
        activeConfigId.value = id
        manageAiConfigUseCase.setActiveConfigId(id)
    }

    fun isAiConfigured(): Boolean = manageAiConfigUseCase.isAiConfigured()

    fun getProviderStatusText(): String = manageAiConfigUseCase.getProviderStatusText()

    private val _modelFetchState = MutableStateFlow<ModelFetchState>(ModelFetchState.Idle)
    val modelFetchState = _modelFetchState.asStateFlow()

    fun fetchModels(providerType: AiProviderType, apiKey: String, baseUrl: String? = null) {
        viewModelScope.launch {
            _modelFetchState.value = ModelFetchState.Loading
            try {
                val models = manageAiConfigUseCase.fetchModels(providerType, apiKey, baseUrl)
                if (models.isNotEmpty()) {
                    _modelFetchState.value = ModelFetchState.Success(models)
                } else {
                    _modelFetchState.value = ModelFetchState.Error("مدلی یافت نشد")
                }
            } catch (e: java.io.IOException) {
                _modelFetchState.value = ModelFetchState.Error(e.localizedMessage ?: "خطا در دریافت مدل‌ها")
            }
        }
    }

    fun clearModelFetchState() {
        _modelFetchState.value = ModelFetchState.Idle
    }

    private val AI_CACHE_DURATION_MS = 10 * 60 * 1000L
    private var cachedForecast: String? = sharedPrefs.getString(KEY_CACHED_FORECAST, null)
    private var cachedAdvice: String? = sharedPrefs.getString(KEY_CACHED_ADVICE, null)
    private var lastForecastFetchTimeMs = sharedPrefs.getLong(KEY_FORECAST_FETCH_TIME, 0L)
    private var lastAdviceFetchTimeMs = sharedPrefs.getLong(KEY_ADVICE_FETCH_TIME, 0L)
    private var lastKnownForecastSignature = sharedPrefs.getString(KEY_FORECAST_SIGNATURE, "") ?: ""
    private var lastKnownAdviceSignature = sharedPrefs.getString(KEY_ADVICE_SIGNATURE, "") ?: ""
    private var forecastDebounceJob: Job? = null
    private var adviceDebounceJob: Job? = null

    private val _lastForecastFetchTime = MutableStateFlow(lastForecastFetchTimeMs)
    val lastForecastFetchTime: StateFlow<Long> = _lastForecastFetchTime.asStateFlow()

    private val _lastAdviceFetchTime = MutableStateFlow(lastAdviceFetchTimeMs)
    val lastAdviceFetchTime: StateFlow<Long> = _lastAdviceFetchTime.asStateFlow()

    private fun persistForecastCache() {
        sharedPrefs.edit()
            .putString(KEY_CACHED_FORECAST, cachedForecast)
            .putLong(KEY_FORECAST_FETCH_TIME, lastForecastFetchTimeMs)
            .putString(KEY_FORECAST_SIGNATURE, lastKnownForecastSignature)
            .apply()
    }

    private fun persistAdviceCache() {
        sharedPrefs.edit()
            .putString(KEY_CACHED_ADVICE, cachedAdvice)
            .putLong(KEY_ADVICE_FETCH_TIME, lastAdviceFetchTimeMs)
            .putString(KEY_ADVICE_SIGNATURE, lastKnownAdviceSignature)
            .apply()
    }

    internal fun computeDataSignature(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>
    ): String {
        val txCount = transactions.size
        val txTotal = transactions.sumOf { it.amount }
        val loanCount = loans.size
        val instCount = installments.size
        val catCount = categories.size
        return "$txCount|$txTotal|$loanCount|$instCount|$catCount"
    }

    internal fun computeAdviceSignature(
        transactions: List<Transaction>,
        categories: List<Category>
    ): String {
        val txCount = transactions.size
        val txTotal = transactions.sumOf { it.amount }
        val catCount = categories.size
        return "$txCount|$txTotal|$catCount"
    }

    fun getCachedForecast(): String? = cachedForecast
    fun getCachedAdvice(): String? = cachedAdvice

    fun formatLastFetchTime(timestamp: Long): String {
        if (timestamp == 0L) return "هنوز به‌روز نشده"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = (diff / 60000).toInt()
        return when {
            minutes < 1 -> "همین الان"
            minutes == 1 -> "۱ دقیقه پیش"
            minutes < 60 -> "$minutes دقیقه پیش"
            else -> {
                val hours = minutes / 60
                if (hours == 1) "۱ ساعت پیش" else "$hours ساعت پیش"
            }
        }
    }

    fun onFinancialDataChanged(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>
    ) {
        val newSignature = computeDataSignature(transactions, loans, installments, categories)

        if (newSignature == lastKnownForecastSignature && !cachedForecast.isNullOrEmpty()) {
            return
        }

        forecastDebounceJob?.cancel()

        if (lastForecastFetchTimeMs == 0L) {
            viewModelScope.launch {
                fetchBudgetForecast(transactions, loans, installments, categories, isOnlineMode.value)
            }
        } else {
            forecastDebounceJob = viewModelScope.launch {
                delay(AI_CACHE_DURATION_MS)
                fetchBudgetForecast(transactions, loans, installments, categories, isOnlineMode.value)
            }
        }
    }

    private val _parserState = MutableStateFlow<ParserUIState>(ParserUIState.Idle)
    val parserState = _parserState.asStateFlow()

    fun parseSmartSentence(sentence: String, isOnlineMode: Boolean) {
        if (sentence.isBlank()) return
        viewModelScope.launch {
            _parserState.value = ParserUIState.Loading
            try {
                val config = if (isOnlineMode) manageAiConfigUseCase.getActiveConfig() else null
                AppLogger.d("AiAssistantViewModel", "parseSmartSentence: isOnlineMode=$isOnlineMode, config=${config?.let { "found(${it.providerType}, model=${it.model})" } ?: "null"}")
                val result = parseTransactionUseCase.parse(sentence, config)
                if (result != null) {
                    _parserState.value = ParserUIState.Confirming(result)
                } else {
                    _parserState.value = ParserUIState.Error("خطا در تحلیل متن")
                }
            } catch (e: java.io.IOException) {
                AppLogger.e("AiAssistantViewModel", "parseSmartSentence I/O error", e)
                _parserState.value = ParserUIState.Error("خطا در اتصال")
            } catch (e: IllegalArgumentException) {
                AppLogger.e("AiAssistantViewModel", "parseSmartSentence invalid argument", e)
                _parserState.value = ParserUIState.Error(e.localizedMessage ?: "ورودی نامعتبر")
            }
        }
    }

    fun confirmParsedResult(result: ParsedResult) {
        _parserState.value = ParserUIState.Success(result)
    }

    fun clearParserState() {
        _parserState.value = ParserUIState.Idle
    }

    fun approveParsedResult(result: ParsedResult, customDate: Long? = null) {
        viewModelScope.launch {
            try {
                parseTransactionUseCase.approveParsedResult(result, customDate)
                _parserState.value = ParserUIState.Idle
            } catch (e: java.io.IOException) {
                AppLogger.e("AiAssistantViewModel", "approveParsedResult I/O failed", e)
            } catch (e: java.time.format.DateTimeParseException) {
                AppLogger.e("AiAssistantViewModel", "approveParsedResult date parse failed", e)
            }
        }
    }

    private val _advisorState = MutableStateFlow<AdvisorUIState>(
        if (!cachedAdvice.isNullOrEmpty()) AdvisorUIState.Success(cachedAdvice.orEmpty()) else AdvisorUIState.Idle
    )
    val advisorState = _advisorState.asStateFlow()

    fun fetchBudgetAdvice(
        transactions: List<Transaction>,
        categories: List<Category>,
        isOnlineMode: Boolean,
        forceRefresh: Boolean = false
    ) {
        val currentSignature = computeAdviceSignature(transactions, categories)

        if (!forceRefresh
            && currentSignature == lastKnownAdviceSignature
            && !cachedAdvice.isNullOrEmpty()
        ) {
            _advisorState.value = AdvisorUIState.Success(cachedAdvice.orEmpty())
            return
        }

        viewModelScope.launch {
            _advisorState.value = AdvisorUIState.Loading
            try {
                val config = if (isOnlineMode) manageAiConfigUseCase.getActiveConfig() else null
                val advice = getBudgetAdviceUseCase.getAdvice(transactions, categories, config)
                cachedAdvice = advice
                lastAdviceFetchTimeMs = System.currentTimeMillis()
                lastKnownAdviceSignature = currentSignature
                _lastAdviceFetchTime.value = lastAdviceFetchTimeMs
                persistAdviceCache()
                _advisorState.value = AdvisorUIState.Success(advice)
            } catch (e: java.io.IOException) {
                AppLogger.e("AiAssistantViewModel", "Network or I/O error in fetchBudgetAdvice", e)
                _advisorState.value = AdvisorUIState.Error(e.localizedMessage ?: "خطای شبکه یا ورودی/خروجی")
            } catch (e: retrofit2.HttpException) {
                AppLogger.e("AiAssistantViewModel", "HTTP error in fetchBudgetAdvice", e)
                _advisorState.value = AdvisorUIState.Error(e.localizedMessage ?: "خطای ارتباط با سرور")
            } catch (e: JsonDataException) {
                AppLogger.e("AiAssistantViewModel", "Data parsing error in fetchBudgetAdvice", e)
                _advisorState.value = AdvisorUIState.Error(e.localizedMessage ?: "خطای تجزیه داده‌ها")
            } catch (e: android.database.sqlite.SQLiteException) {
                AppLogger.e("AiAssistantViewModel", "Database error in persistAdviceCache", e)
                _advisorState.value = AdvisorUIState.Error(e.localizedMessage ?: "خطای پایگاه داده")
            } catch (e: Exception) {
                AppLogger.e("AiAssistantViewModel", "Unexpected error in fetchBudgetAdvice", e)
                _advisorState.value = AdvisorUIState.Error(e.localizedMessage ?: "خطای ناشناخته در دریافت توصیه‌ها")
            }
        }
    }

    fun clearAdvisorState() {
        _advisorState.value = AdvisorUIState.Idle
    }

    private val _forecastState = MutableStateFlow<ForecastUIState>(
        if (!cachedForecast.isNullOrEmpty()) ForecastUIState.Success(cachedForecast.orEmpty()) else ForecastUIState.Idle
    )
    val forecastState = _forecastState.asStateFlow()

    fun fetchBudgetForecast(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>,
        isOnlineMode: Boolean,
        forceRefresh: Boolean = false
    ) {
        val currentSignature = computeDataSignature(transactions, loans, installments, categories)

        if (!forceRefresh
            && currentSignature == lastKnownForecastSignature
            && !cachedForecast.isNullOrEmpty()
        ) {
            _forecastState.value = ForecastUIState.Success(cachedForecast.orEmpty())
            return
        }

        viewModelScope.launch {
            _forecastState.value = ForecastUIState.Loading
            try {
                val config = if (isOnlineMode) manageAiConfigUseCase.getActiveConfig() else null
                val forecast = getForecastUseCase.getForecast(transactions, loans, installments, categories, config)
                cachedForecast = forecast
                lastForecastFetchTimeMs = System.currentTimeMillis()
                lastKnownForecastSignature = currentSignature
                _lastForecastFetchTime.value = lastForecastFetchTimeMs
                persistForecastCache()
                _forecastState.value = ForecastUIState.Success(forecast)
            } catch (e: IOException) {
                AppLogger.e("AiAssistantViewModel", "fetchBudgetForecast failed I/O", e)
                _forecastState.value = ForecastUIState.Error(e.localizedMessage ?: "خطای I/O در پیش‌بینی بودجه")
            } catch (e: HttpException) {
                AppLogger.e("AiAssistantViewModel", "fetchBudgetForecast failed HTTP", e)
                _forecastState.value = ForecastUIState.Error(e.localizedMessage ?: "خطای شبکه در پیش‌بینی بودجه")
            } catch (e: SQLiteException) {
                AppLogger.e("AiAssistantViewModel", "fetchBudgetForecast failed DB", e)
                _forecastState.value = ForecastUIState.Error(e.localizedMessage ?: "خطای پایگاه داده در پیش‌بینی بودجه")
            }
        }
    }

    fun clearForecastState() {
        _forecastState.value = ForecastUIState.Idle
    }

    companion object {
        private const val KEY_CACHED_FORECAST = "cached_forecast"
        private const val KEY_CACHED_ADVICE = "cached_advice"
        private const val KEY_FORECAST_FETCH_TIME = "forecast_fetch_time"
        private const val KEY_ADVICE_FETCH_TIME = "advice_fetch_time"
        private const val KEY_FORECAST_SIGNATURE = "forecast_signature"
        private const val KEY_ADVICE_SIGNATURE = "advice_signature"
    }
}
