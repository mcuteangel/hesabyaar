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
 * Covers [AiProvider.fetchModels]' cancellable HTTP path through an
 * interceptor-based fake: no real network and no new test dependencies.
 * The fake client is injected via [AiProvider.httpClientOverride] and cleared
 * after every test so no state leaks into other test classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AiProviderFetchModelsTest {
  @Before
  fun setUp() {
    AiProvider.httpClientOverride = null
  }

  @After
  fun tearDown() {
    AiProvider.httpClientOverride = null
  }

  @Test
  fun successfulFetchModelsReturnsParsedModels() {
    AiProvider.httpClientOverride = cannedClient(HTTP_OK, GEMINI_MODELS_JSON)
    val result = runBlocking { AiProvider.fetchModels(AiProviderType.GEMINI, "test-key") }
    assertTrue(result.isSuccess)
    val models = result.getOrThrow()
    // The embedding-only entry must be filtered out (no generateContent).
    assertEquals(1, models.size)
    assertEquals("gemini-2.0-flash", models[0].id)
    assertEquals("Gemini 2.0 Flash (Gemini)", models[0].displayName)
  }

  @Test
  fun cancelledFetchModelsCancelsInFlightCall() {
    val enteredCall = CountDownLatch(1)
    AiProvider.httpClientOverride =
      OkHttpClient
        .Builder()
        .addInterceptor(holdingInterceptor(enteredCall))
        .build()
    val job =
      CoroutineScope(Dispatchers.Default).launch {
        AiProvider.fetchModels(AiProviderType.GEMINI, "test-key")
      }
    assertTrue("fake server was never hit", enteredCall.await(ENTER_TIMEOUT_SECONDS, TimeUnit.SECONDS))
    job.cancel()
    // Job.join() never throws, even when the job was cancelled: it only
    // suspends until completion. The cancelled state itself proves the
    // CancellationException propagated instead of the in-flight OkHttp call
    // running to completion and delivering a stale result.
    runBlocking { job.join() }
    assertTrue("cancelled model fetch must propagate coroutine cancellation", job.isCancelled)
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
      while (!chain.call().isCanceled()) {
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
    const val POLL_INTERVAL_MS = 20L
    const val ENTER_TIMEOUT_SECONDS = 10L
    const val JSON_MEDIA_TYPE = "application/json"
    const val GEMINI_MODELS_JSON =
      """
      {
        "models": [
          {
            "name": "models/gemini-2.0-flash",
            "displayName": "Gemini 2.0 Flash",
            "supportedGenerationMethods": ["generateContent"]
          },
          {
            "name": "models/gemini-embedding-001",
            "displayName": "Gemini Embedding",
            "supportedGenerationMethods": ["embedContent"]
          }
        ]
      }
      """
  }
}
