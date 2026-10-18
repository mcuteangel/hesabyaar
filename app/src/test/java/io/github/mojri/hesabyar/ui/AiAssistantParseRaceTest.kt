package io.github.mojri.hesabyar.ui

import android.content.Context
import io.github.mojri.hesabyar.api.AiConfigManager
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.AiForecastAdviceCache
import io.github.mojri.hesabyar.domain.usecase.CacheEntry
import io.github.mojri.hesabyar.domain.usecase.GetBudgetAdviceUseCase
import io.github.mojri.hesabyar.domain.usecase.GetForecastUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageAiConfigUseCase
import io.github.mojri.hesabyar.domain.usecase.ParseTransactionUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression tests for the parseSmartSentence race condition: a slow parse that is
 * superseded by a newer parseSmartSentence call must be cancelled and must never
 * overwrite the newer result.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AiAssistantParseRaceTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var viewModel: TestableAiAssistantViewModel

  private val fakeCache =
    object : AiForecastAdviceCache {
      override fun getForecast(
        signature: String,
        currentTime: Long
      ): CacheEntry? = null

      override fun peekForecast(currentTime: Long): CacheEntry? = null

      override fun putForecast(
        signature: String,
        value: String
      ) {
        // no-op
      }

      override fun getAdvice(
        signature: String,
        currentTime: Long
      ): CacheEntry? = null

      override fun peekAdvice(currentTime: Long): CacheEntry? = null

      override fun putAdvice(
        signature: String,
        value: String
      ) {
        // no-op
      }

      override fun clear() {
        // no-op
      }
    }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    val appContext = RuntimeEnvironment.getApplication()
    viewModel =
      TestableAiAssistantViewModel(
        parseTransactionUseCase = ParseTransactionUseCase(FakeRepository()),
        getBudgetAdviceUseCase = GetBudgetAdviceUseCase(),
        getForecastUseCase = GetForecastUseCase(),
        manageAiConfigUseCase = FakeManageAiConfigUseCase(appContext),
        aiForecastAdviceCache = fakeCache
      )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `stale parse is cancelled and never overwrites newer result`() {
    viewModel.parseSmartSentence(FIRST_SENTENCE, false)
    testDispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.parserState.value is ParserUIState.Loading)

    viewModel.parseSmartSentence(SECOND_SENTENCE, false)
    testDispatcher.scheduler.advanceUntilIdle()

    val confirming = viewModel.parserState.value as ParserUIState.Confirming
    assertEquals(SECOND_RESULT.amount, confirming.result.amount)
    assertEquals(SECOND_RESULT.title, confirming.result.title)

    // Releasing the stale gate must not resurrect the cancelled parse.
    viewModel.gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    val stillConfirming = viewModel.parserState.value as ParserUIState.Confirming
    assertEquals(SECOND_RESULT.amount, stillConfirming.result.amount)
    assertEquals(SECOND_RESULT.title, stillConfirming.result.title)
  }

  @Test
  fun `single parse applies its result normally`() {
    viewModel.parseSmartSentence(SECOND_SENTENCE, false)
    testDispatcher.scheduler.advanceUntilIdle()

    val confirming = viewModel.parserState.value as ParserUIState.Confirming
    assertEquals(SECOND_RESULT.amount, confirming.result.amount)
    assertEquals(SECOND_RESULT.title, confirming.result.title)
  }

  @Test
  fun `blank sentence leaves parser state idle`() {
    viewModel.parseSmartSentence("   ", false)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.parserState.value is ParserUIState.Idle)
  }

  /**
   * Config use case fake: the real [AiConfigManager] builds EncryptedSharedPreferences
   * on first prefs access, which needs AndroidKeyStore — unavailable under Robolectric.
   * The race tests never touch configs (isOnlineMode=false), so empty defaults suffice.
   */
  private class FakeManageAiConfigUseCase(
    context: Context
  ) : ManageAiConfigUseCase(AiConfigManager(context)) {
    override fun loadConfigs(): List<AiProviderConfig> = emptyList()

    override fun getActiveConfigId(): String? = null

    override fun isOnlineMode(): Boolean = false
  }

  private class TestableAiAssistantViewModel(    parseTransactionUseCase: ParseTransactionUseCase,
    getBudgetAdviceUseCase: GetBudgetAdviceUseCase,
    getForecastUseCase: GetForecastUseCase,
    manageAiConfigUseCase: ManageAiConfigUseCase,
    aiForecastAdviceCache: AiForecastAdviceCache
  ) : AiAssistantViewModel(
    parseTransactionUseCase,
    getBudgetAdviceUseCase,
    getForecastUseCase,
    manageAiConfigUseCase,
    aiForecastAdviceCache
  ) {
    val gate = CompletableDeferred<Unit>()

    override suspend fun performParse(
      sentence: String,
      config: AiProviderConfig?
    ): ParsedResult? {
      if (sentence == FIRST_SENTENCE) {
        gate.await()
        return FIRST_RESULT
      }
      return SECOND_RESULT
    }
  }

  companion object {
    private const val FIRST_SENTENCE = "first"
    private const val SECOND_SENTENCE = "second"

    private val FIRST_RESULT =
      ParsedResult(
        type = TransactionType.EXPENSE.name,
        amount = 1000L,
        category = "Other",
        personName = null,
        description = "first parse result",
        daysFromNow = null,
        title = "first title"
      )

    private val SECOND_RESULT =
      ParsedResult(
        type = TransactionType.EXPENSE.name,
        amount = 2000L,
        category = "Other",
        personName = null,
        description = "second parse result",
        daysFromNow = null,
        title = "second title"
      )
  }
}
