package io.github.mojri.hesabyar.api

import android.util.Log
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

    suspend fun generateContent(
        config: AiProviderConfig,
        prompt: String,
        systemInstruction: String? = null,
        temperature: Double = 0.1,
        responseMimeType: String? = null
    ): ApiResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext ApiResult.Failure("API key not configured")
        }

        when (config.providerType) {
            AiProviderType.GEMINI -> callGemini(config, prompt, systemInstruction, temperature, responseMimeType)
            AiProviderType.OPENROUTER -> callOpenAiCompatible(config, prompt, systemInstruction, temperature, responseMimeType, isGeminiFormat = true)
            AiProviderType.CUSTOM -> callOpenAiCompatible(config, prompt, systemInstruction, temperature, responseMimeType, isGeminiFormat = false)
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
        return executePost(url, requestJson, ::parseGeminiResponse)
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
        return executePost(url, requestJson, ::parseOpenAiResponse)
    }

    private fun executePost(
        url: String,
        body: JSONObject,
        responseParser: (String) -> ApiResult
    ): ApiResult {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "API error ${response.code}: $bodyStr")
                    ApiResult.Failure("API error ${response.code}: ${response.message}")
                } else {
                    responseParser(bodyStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call failed", e)
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
            Log.e(TAG, "Failed to parse Gemini response", e)
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
            Log.e(TAG, "Failed to parse OpenAI response", e)
            ApiResult.Failure("Failed to parse response: ${e.localizedMessage}")
        }
    }
}
