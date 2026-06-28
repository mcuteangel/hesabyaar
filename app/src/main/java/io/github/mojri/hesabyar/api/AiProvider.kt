package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiProvider {
    private const val TAG = "AiProvider"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class ChatMessage(
        val role: String,
        val content: String
    )

    sealed class ApiResult {
        data class Success(val text: String) : ApiResult()
        data class Failure(val error: String) : ApiResult()
    }

    data class FetchedModel(
        val id: String,
        val displayName: String,
        val provider: String,
        val isFree: Boolean
    )

    suspend fun generateContent(
        config: AiProviderConfig,
        prompt: String,
        systemInstruction: String? = null,
        temperature: Double = 0.1,
        responseMimeType: String? = null
    ): ApiResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "generateContent: provider=${config.providerType}, model=${config.model}, isConfigured=${config.isConfigured}, apiKeyLength=${config.apiKey.length}")
        if (!config.isConfigured) {
            AppLogger.w(TAG, "generateContent: API key not configured")
            return@withContext ApiResult.Failure("API key not configured")
        }

        when (config.providerType) {
            AiProviderType.GEMINI -> callGemini(config, prompt, systemInstruction, temperature, responseMimeType)
            AiProviderType.OPENROUTER -> callOpenAiCompatible(config, prompt, systemInstruction, temperature, responseMimeType, isGeminiFormat = true)
            AiProviderType.CUSTOM -> callOpenAiCompatible(config, prompt, systemInstruction, temperature, responseMimeType, isGeminiFormat = false)
        }
    }

    suspend fun fetchModels(
        providerType: AiProviderType,
        apiKey: String,
        baseUrl: String? = null
    ): List<FetchedModel> = withContext(Dispatchers.IO) {
        try {
            when (providerType) {
                AiProviderType.GEMINI -> fetchGeminiModels(apiKey)
                AiProviderType.OPENROUTER -> fetchOpenRouterModels(apiKey)
                AiProviderType.CUSTOM -> fetchCustomModels(apiKey, baseUrl ?: "")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch models for $providerType", e)
            emptyList()
        }
    }

    private fun fetchGeminiModels(apiKey: String): List<FetchedModel> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val modelsArray = json.getJSONArray("models")

            return (0 until modelsArray.length()).mapNotNull { i ->
                val model = modelsArray.getJSONObject(i)
                val name = model.getString("name").removePrefix("models/")
                val displayName = model.optString("displayName", name)
                val methods = model.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = (0 until (methods?.length() ?: 0)).any {
                    methods!!.getString(it) == "generateContent"
                }
                if (!supportsGenerate) return@mapNotNull null

                val family = when {
                    name.contains("gemini", ignoreCase = true) -> "Gemini"
                    name.contains("gemma", ignoreCase = true) -> "Gemma"
                    name.contains("imagen", ignoreCase = true) -> "Imagen"
                    else -> "Other"
                }

                FetchedModel(
                    id = name,
                    displayName = "$displayName ($family)",
                    provider = family,
                    isFree = false
                )
            }.sortedBy { it.displayName }
        }
    }

    private fun fetchOpenRouterModels(apiKey: String): List<FetchedModel> {
        val url = "https://openrouter.ai/api/v1/models"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val modelsArray = json.getJSONArray("data")

            return (0 until modelsArray.length()).map { i ->
                val model = modelsArray.getJSONObject(i)
                val id = model.getString("id")
                val name = model.optString("name", id)
                val pricing = model.optJSONObject("pricing")
                val promptPrice = pricing?.optString("prompt", "0")?.toDoubleOrNull() ?: 0.0
                val provider = id.split("/").firstOrNull() ?: "unknown"

                FetchedModel(
                    id = id,
                    displayName = "$name ($provider)",
                    provider = provider.replaceFirstChar { it.uppercase() },
                    isFree = promptPrice == 0.0
                )
            }.sortedWith(compareBy<FetchedModel> { !it.isFree }.thenBy { it.displayName })
        }
    }

    private fun fetchCustomModels(apiKey: String, baseUrl: String): List<FetchedModel> {
        if (baseUrl.isBlank()) return emptyList()
        val url = "${baseUrl.trimEnd('/')}/models"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val modelsArray = json.optJSONArray("data") ?: return emptyList()

            return (0 until modelsArray.length()).map { i ->
                val model = modelsArray.getJSONObject(i)
                val id = model.getString("id")
                val name = model.optString("name", id)

                FetchedModel(
                    id = id,
                    displayName = name,
                    provider = "Custom",
                    isFree = false
                )
            }.sortedBy { it.displayName }
        }
    }

    private fun callGemini(
        config: AiProviderConfig,
        prompt: String,
        systemInstruction: String?,
        temperature: Double,
        responseMimeType: String?
    ): ApiResult {
        val model = config.model.ifBlank { "gemini-2.0-flash" }

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            if (systemInstruction != null) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemInstruction)
                    }))
                })
            }
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                if (responseMimeType != null) {
                    put("responseMimeType", responseMimeType)
                }
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.apiKey}"
        return executePost(url, requestJson, ::parseGeminiResponse, apiKey = null)
    }

    private fun callOpenAiCompatible(
        config: AiProviderConfig,
        prompt: String,
        systemInstruction: String?,
        temperature: Double,
        responseMimeType: String?,
        isGeminiFormat: Boolean
    ): ApiResult {
        val baseUrl = config.baseUrl.ifBlank {
            when {
                isGeminiFormat -> "https://openrouter.ai/api/v1"
                else -> ""
            }
        }
        if (baseUrl.isBlank()) {
            return ApiResult.Failure("Base URL is required for custom provider")
        }

        val model = config.model.ifBlank { return ApiResult.Failure("Model is required") }

        val messages = JSONArray()
        if (systemInstruction != null) {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
            if (responseMimeType == "application/json") {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }

        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        return executePost(url, requestJson, ::parseOpenAiResponse, apiKey = config.apiKey, isOpenRouter = isGeminiFormat)
    }

    private fun executePost(
        url: String,
        body: JSONObject,
        responseParser: (String) -> ApiResult,
        apiKey: String? = null,
        isOpenRouter: Boolean = false
    ): ApiResult {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        if (isOpenRouter) {
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/mojri/hesabyar")
            requestBuilder.addHeader("X-Title", "Hesabyar")
        }

        val request = requestBuilder.build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "API error ${response.code} for URL $url: $bodyStr")
                    ApiResult.Failure("API error ${response.code}: $bodyStr")
                } else {
                    responseParser(bodyStr)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "API call failed", e)
            ApiResult.Failure("Network error: ${e.localizedMessage}")
        }
    }

    private fun parseGeminiResponse(bodyStr: String): ApiResult {
        return try {
            val json = JSONObject(bodyStr)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val parts = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                if (parts.length() > 0) {
                    val text = parts.getJSONObject(0).getString("text")
                    ApiResult.Success(text)
                } else {
                    ApiResult.Failure("Empty response parts")
                }
            } else {
                ApiResult.Failure("No candidates in response")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse Gemini response", e)
            ApiResult.Failure("Failed to parse response: ${e.localizedMessage}")
        }
    }

    private fun parseOpenAiResponse(bodyStr: String): ApiResult {
        return try {
            val json = JSONObject(bodyStr)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                val text = message.getString("content")
                ApiResult.Success(text)
            } else {
                ApiResult.Failure("No choices in response")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse OpenAI response", e)
            ApiResult.Failure("Failed to parse response: ${e.localizedMessage}")
        }
    }
}
