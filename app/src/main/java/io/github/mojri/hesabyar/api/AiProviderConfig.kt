package io.github.mojri.hesabyar.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

enum class AiProviderType(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENROUTER("OpenRouter"),
    CUSTOM("Custom Endpoint")
}

data class AiProviderConfig(
    val providerType: AiProviderType = AiProviderType.GEMINI,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = ""
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

    val displayName: String
        get() = when (providerType) {
            AiProviderType.GEMINI -> "Google Gemini"
            AiProviderType.OPENROUTER -> "OpenRouter"
            AiProviderType.CUSTOM -> "Custom"
        }
}

class AiConfigManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_provider_config", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AiConfigManager"
        private const val KEY_PROVIDER_TYPE = "provider_type"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_BASE_URL = "base_url"
    }

    fun loadConfig(): AiProviderConfig {
        val typeOrdinal = prefs.getInt(KEY_PROVIDER_TYPE, 0)
        val type = AiProviderType.entries.getOrElse(typeOrdinal) { AiProviderType.GEMINI }
        return AiProviderConfig(
            providerType = type,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            model = prefs.getString(KEY_MODEL, getDefaultModel(type)) ?: getDefaultModel(type),
            baseUrl = prefs.getString(KEY_BASE_URL, "") ?: ""
        )
    }

    fun saveConfig(config: AiProviderConfig) {
        prefs.edit()
            .putInt(KEY_PROVIDER_TYPE, config.providerType.ordinal)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_BASE_URL, config.baseUrl)
            .apply()
        Log.i(TAG, "AI provider config saved: ${config.providerType.name}, model: ${config.model}")
    }

    private fun getDefaultModel(type: AiProviderType): String {
        return when (type) {
            AiProviderType.GEMINI -> "gemini-2.0-flash"
            AiProviderType.OPENROUTER -> "google/gemini-2.0-flash-001"
            AiProviderType.CUSTOM -> ""
        }
    }
}
