package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.api.AiConfigManager
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType

class ManageAiConfigUseCase(
    private val aiConfigManager: AiConfigManager
) {
    fun loadConfigs(): List<AiProviderConfig> = aiConfigManager.loadConfigs()

    fun getActiveConfigId(): String? = aiConfigManager.getActiveConfigId()

    fun getActiveConfig(): AiProviderConfig? = aiConfigManager.getActiveConfig()

    fun isOnlineMode(): Boolean = aiConfigManager.isOnlineMode()

    fun setOnlineMode(enabled: Boolean) = aiConfigManager.setOnlineMode(enabled)

    fun addConfig(config: AiProviderConfig): AiProviderConfig = aiConfigManager.addConfig(config)

    fun updateConfig(config: AiProviderConfig) = aiConfigManager.updateConfig(config)

    fun deleteConfig(id: String) = aiConfigManager.deleteConfig(id)

    fun setActiveConfigId(id: String) = aiConfigManager.setActiveConfigId(id)

    fun isAiConfigured(): Boolean = aiConfigManager.getActiveConfig()?.isConfigured == true

    fun getProviderStatusText(): String {
        val config = aiConfigManager.getActiveConfig()
        return if (config != null && config.isConfigured) {
            "${config.displayName} | ${config.model}"
        } else {
            "تنظیم نشده (حالت آفلاین)"
        }
    }

    suspend fun fetchModels(providerType: AiProviderType, apiKey: String, baseUrl: String? = null): List<String> {
        val cached = aiConfigManager.getCachedModels(providerType)
        if (cached != null && !cached.isExpired) {
            return cached.models
        }
        val models = io.github.mojri.hesabyar.api.AiProvider.fetchModels(providerType, apiKey, baseUrl)
        if (models.isNotEmpty()) {
            val modelIds = models.map { it.id }
            aiConfigManager.cacheModels(providerType, modelIds)
            return modelIds
        }
        return emptyList()
    }
}
