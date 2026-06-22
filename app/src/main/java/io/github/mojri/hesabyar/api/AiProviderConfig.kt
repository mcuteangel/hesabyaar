package io.github.mojri.hesabyar.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AiProviderType(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENROUTER("OpenRouter"),
    CUSTOM("Custom Endpoint")
}

data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerType: AiProviderType = AiProviderType.GEMINI,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val label: String = ""
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

    val displayName: String
        get() = label.ifBlank {
            when (providerType) {
                AiProviderType.GEMINI -> "Google Gemini"
                AiProviderType.OPENROUTER -> "OpenRouter"
                AiProviderType.CUSTOM -> "Custom"
            }
        }
}

data class ModelCacheEntry(
    val models: List<String>,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() - fetchedAt > 24 * 60 * 60 * 1000
}

class AiConfigManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_provider_config", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AiConfigManager"
        private const val KEY_CONFIGS_JSON = "configs_json"
        private const val KEY_ACTIVE_ID = "active_id"
        private const val KEY_ONLINE_MODE = "online_mode"
        private const val KEY_MODEL_CACHE = "model_cache"
    }

    fun loadConfigs(): List<AiProviderConfig> {
        val json = prefs.getString(KEY_CONFIGS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AiProviderConfig(
                    id = obj.optString("id", ""),
                    providerType = AiProviderType.entries.getOrElse(obj.optInt("providerType", 0)) { AiProviderType.GEMINI },
                    apiKey = obj.optString("apiKey", ""),
                    model = obj.optString("model", ""),
                    baseUrl = obj.optString("baseUrl", ""),
                    label = obj.optString("label", "")
                )
            }.filter { it.id.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse configs", e)
            emptyList()
        }
    }

    fun saveConfigs(configs: List<AiProviderConfig>) {
        val arr = JSONArray()
        configs.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("providerType", c.providerType.ordinal)
                put("apiKey", c.apiKey)
                put("model", c.model)
                put("baseUrl", c.baseUrl)
                put("label", c.label)
            })
        }
        prefs.edit().putString(KEY_CONFIGS_JSON, arr.toString()).apply()
    }

    fun getActiveConfigId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun setActiveConfigId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun getActiveConfig(): AiProviderConfig? {
        val configs = loadConfigs()
        val activeId = getActiveConfigId()
        return configs.find { it.id == activeId } ?: configs.firstOrNull { it.isConfigured }
    }

    fun isOnlineMode(): Boolean = prefs.getBoolean(KEY_ONLINE_MODE, true)

    fun setOnlineMode(online: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_MODE, online).apply()
    }

    fun addConfig(config: AiProviderConfig): AiProviderConfig {
        val configs = loadConfigs().toMutableList()
        val newConfig = config.copy(id = UUID.randomUUID().toString())
        configs.add(newConfig)
        saveConfigs(configs)
        if (configs.size == 1) {
            setActiveConfigId(newConfig.id)
        }
        return newConfig
    }

    fun updateConfig(config: AiProviderConfig) {
        val configs = loadConfigs().toMutableList()
        val idx = configs.indexOfFirst { it.id == config.id }
        if (idx >= 0) {
            configs[idx] = config
            saveConfigs(configs)
        }
    }

    fun deleteConfig(id: String) {
        val configs = loadConfigs().toMutableList()
        configs.removeAll { it.id == id }
        saveConfigs(configs)
        if (getActiveConfigId() == id) {
            setActiveConfigId(configs.firstOrNull()?.id ?: "")
        }
    }

    fun getCachedModels(providerType: AiProviderType): ModelCacheEntry? {
        val json = prefs.getString(KEY_MODEL_CACHE, null) ?: return null
        return try {
            val obj = JSONObject(json)
            val providerJson = obj.optString(providerType.name, null) ?: return null
            val entry = JSONObject(providerJson)
            val modelsArr = entry.getJSONArray("models")
            val models = (0 until modelsArr.length()).map { modelsArr.getString(it) }
            ModelCacheEntry(
                models = models,
                fetchedAt = entry.getLong("fetchedAt")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun cacheModels(providerType: AiProviderType, models: List<String>) {
        val existing = try {
            val json = prefs.getString(KEY_MODEL_CACHE, null)
            if (json != null) JSONObject(json) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }

        existing.put(providerType.name, JSONObject().apply {
            val arr = JSONArray()
            models.forEach { arr.put(it) }
            put("models", arr)
            put("fetchedAt", System.currentTimeMillis())
        })

        prefs.edit().putString(KEY_MODEL_CACHE, existing.toString()).apply()
    }
}
