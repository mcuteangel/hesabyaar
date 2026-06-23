package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.api.AiConfigManager
import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val aiConfigManager = AiConfigManager(application)

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
}
