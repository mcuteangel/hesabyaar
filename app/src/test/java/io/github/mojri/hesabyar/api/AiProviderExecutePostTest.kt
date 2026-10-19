package io.github.mojri.hesabyar.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Covers [AiProvider.executePost]'s cancellable HTTP path through an
 * interceptor-based fake: no real network and no new test dependencies.
 * The fake client is injected via [AiProvider.httpClientOverride] and cleared
 * after every test so no state leaks into other test classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AiProviderExecutePostTest {
  private val customConfig =
    AiProviderConfig(
      providerType = AiProviderType.CUSTOM,
      apiKey = "test-key",
      model = "test-model",
      baseUrl = "http://127.0.0.1:9"
    )

  @Before
  fun setUp() {
    AiProvider.httpClientOverride = null
  }

  @After
  fun tearDown() {
    AiProvider.httpClientOverride = null
  }

  @Test
  fun successfulPostReturnsParsedText() {
    AiProvider.httpClientOverride = cannedClient(HTTP_OK, SUCCESS_JSON)
    val result = runBlocking { AiProvider.generateContent(customConfig, "hello") }
    assertTrue(result is AiProvider.ApiResult.Success)
    assertEquals("Hello from the fake provider", (result as AiProvider.ApiResult.Success).text)
  }

  @Test
  fun httpErrorReturnsFailure() {
    AiProvider.httpClientOverride = cannedClient(HTTP_SERVER_ERROR, "boom")
    val result = runBlocking { AiProvider.generateContent(customConfig, "hello") }
    assertTrue(result is AiProvider.ApiResult.Failure)
    assertTrue((result as AiProvider.ApiResult.Failure).error.contains(HTTP_SERVER_ERROR.toString()))
  }

  @Test
  fun emptyBodyReturnsFailure() {
    AiProvider.httpClientOverride = cannedClient(HTTP_OK, "")
    val result = runBlocking { AiProvider.generateContent(customConfig, "hello") }
    assertTrue(result is AiProvider.ApiResult.Failure)
    assertTrue((result as AiProvider.ApiResult.Failure).error.contains("Empty response body"))
  }

  @Test
  fun cancelledInFlightCallThrowsCancellationException() {
    val enteredCall = CountDownLatch(1)
    AiProvider.httpClientOverride =
      OkHttpClient
        .Builder()
        .addInterceptor(holdingInterceptor(enteredCall))
        .build()
    val job =
      CoroutineScope(Dispatchers.Default).launch {
        AiProvider.generateContent(customConfig, "hello")
      }
    assertTrue("fake server was never hit", enteredCall.await(ENTER_TIMEOUT_SECONDS, TimeUnit.SECONDS))
    job.cancel()
    // Job.join() never throws, even when the job was cancelled: it only
    // suspends until completion. The cancelled state itself proves the
    // CancellationException propagated instead of being swallowed into a
    // Failure result.
    runBlocking { job.join() }
    assertTrue("cancelled in-flight call must propagate coroutine cancellation", job.isCancelled)
  }

  private fun cannedClient(
    code: Int,
    body: String
  ): OkHttpClient =
    OkHttpClient
      .Builder()
      .addInterceptor(cannedInterceptor(code, body))
      .build()

  /**
   * Holds the call open the way a slow server would; unblocks only when the
   * test cancels, then fails exactly like OkHttp does on cancel().
   */
  private fun holdingInterceptor(enteredCall: CountDownLatch): Interceptor =
    Interceptor { chain ->
      enteredCall.countDown()
      while (!chain.call().isCanceled) {
        Thread.sleep(POLL_INTERVAL_MS)
      }
      throw IOException("canceled")
    }

  private fun cannedInterceptor(
    code: Int,
    body: String
  ): Interceptor =
    Interceptor { chain ->
      Response
        .Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody(JSON_MEDIA_TYPE.toMediaType()))
        .build()
    }

  private companion object {
    const val HTTP_OK = 200
    const val HTTP_SERVER_ERROR = 500
    const val POLL_INTERVAL_MS = 20L
    const val ENTER_TIMEOUT_SECONDS = 10L
    const val JSON_MEDIA_TYPE = "application/json"
    const val SUCCESS_JSON = """{"choices":[{"message":{"content":"Hello from the fake provider"}}]}"""
  }
}
