package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {
  @Test
  fun `ApiResult has Success and Failure variants`() {
    val success = AiProvider.ApiResult.Success("result")
    val failure = AiProvider.ApiResult.Failure("error")
    assertTrue(success is AiProvider.ApiResult.Success)
    assertTrue(failure is AiProvider.ApiResult.Failure)
  }

  @Test
  fun `ApiResult Success carries text`() {
    val text = "test response"
    val result = AiProvider.ApiResult.Success(text)
    assertEquals(text, (result as AiProvider.ApiResult.Success).text)
  }

  @Test
  fun `ApiResult Failure carries error`() {
    val error = "API error 500"
    val result = AiProvider.ApiResult.Failure(error)
    assertEquals(error, (result as AiProvider.ApiResult.Failure).error)
  }

  @Test
  fun `AiProviderConfig has expected default values`() {
    val config = AiProviderConfig()
    assertEquals(AiProviderType.GEMINI, config.providerType)
    assertNotNull(config.id)
  }

  @Test
  fun `AiProviderConfig with all fields`() {
    val config =
      AiProviderConfig(
        apiKey = "test-key",
        label = "Test Provider",
        model = "test-model",
        baseUrl = "https://test.api.com",
        providerType = AiProviderType.OPENROUTER
      )
    assertEquals("test-key", config.apiKey)
    assertEquals("Test Provider", config.label)
    assertEquals("test-model", config.model)
    assertEquals("https://test.api.com", config.baseUrl)
    assertEquals(AiProviderType.OPENROUTER, config.providerType)
  }

  @Test
  fun `AiProviderType enum has correct display names and entries`() {
    assertEquals("Google Gemini", AiProviderType.GEMINI.displayName)
    assertEquals("OpenRouter", AiProviderType.OPENROUTER.displayName)
    assertEquals("Custom Endpoint", AiProviderType.CUSTOM.displayName)
    assertEquals(3, AiProviderType.entries.size)
  }

  @Test
  fun `redactionInterceptor strips key query parameter from URL`() {
    val urlWithKey = "https://generativelanguage.googleapis.com/v1beta/models?key=SECRET123"
    val request =
      Request
        .Builder()
        .url(urlWithKey)
        .get()
        .build()

    val chain =
      object : Interceptor.Chain {
        override fun request(): Request = request

        override fun proceed(request: Request): Response {
          val outgoingUrl = request.url.toString()
          assertFalse("URL should not contain key parameter", outgoingUrl.contains("key="))
          assertFalse("URL should not contain SECRET123", outgoingUrl.contains("SECRET123"))
          assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models",
            request.url
              .newBuilder()
              .removeAllQueryParameters("key")
              .build()
              .toString()
          )
          return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
        }

        override fun connection(): okhttp3.Connection? = null

        override fun call(): okhttp3.Call = throw UnsupportedOperationException()

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(
          timeout: Int,
          unit: java.util.concurrent.TimeUnit
        ): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(
          timeout: Int,
          unit: java.util.concurrent.TimeUnit
        ): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(
          timeout: Int,
          unit: java.util.concurrent.TimeUnit
        ): Interceptor.Chain = this
      }

    AiProvider.redactionInterceptor.intercept(chain)
  }

  @Test
  fun `redactionInterceptor preserves other query parameters`() {
    val urlWithMultipleParams =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=SECRET123&alt=json"
    val request =
      Request
        .Builder()
        .url(urlWithMultipleParams)
        .get()
        .build()

    val chain =
      object : Interceptor.Chain {
        override fun request(): Request = request

        override fun proceed(request: Request): Response {
          val outgoingUrl = request.url.toString()
          assertFalse("URL should not contain key parameter", outgoingUrl.contains("key="))
          assertTrue("URL should preserve alt parameter", outgoingUrl.contains("alt=json"))
          return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
        }

        override fun connection(): okhttp3.Connection? = null

        override fun call(): okhttp3.Call = throw UnsupportedOperationException()

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(
          timeout: Int,
          unit: java.util.concurrent.TimeUnit
        ): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(
          timeout: Int,
          unit: java.util.concurrent.TimeUnit
        ): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(
          timeout: Int,
          unit: java.util.concurrent.TimeUnit
        ): Interceptor.Chain = this
      }

    AiProvider.redactionInterceptor.intercept(chain)
  }
}
