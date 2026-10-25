package io.github.mojri.hesabyar.ui

import android.content.Context
import io.github.mojri.hesabyar.api.AiConfigManager
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.AiForecastAdviceCache
import io.github.mojri.hesabyar.domain.usecase.CacheEntry
import io.github.mojri.hesabyar.domain.usecase.GetBudgetAdviceUseCase
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import io.github.mojri.hesabyar.domain.usecase.GetForecastUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageAiConfigUseCase
import io.github.mojri.hesabyar.domain.usecase.ParseTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * Unit tests for [AiAssistantViewModel] reactive data fetching and state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AiAssistantViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var fakeRepository: FakeRepository
  private lateinit var viewModel: AiAssistantViewModel

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
      ) {}

      override fun getAdvice(
        signature: String,
        currentTime: Long
      ): CacheEntry? = null

      override fun peekAdvice(currentTime: Long): CacheEntry? = null

      override fun putAdvice(
        signature: String,
        value: String
      ) {}

      override fun clear() {}
    }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    val appContext = RuntimeEnvironment.getApplication()
    fakeRepository = FakeRepository()
    viewModel =
      AiAssistantViewModel(
        parseTransactionUseCase = FakeParseTransactionUseCase(fakeRepository),
        getBudgetAdviceUseCase = FakeGetBudgetAdviceUseCase(),
        getForecastUseCase = FakeGetForecastUseCase(),
        manageAiConfigUseCase = FakeManageAiConfigUseCase(appContext),
        aiForecastAdviceCache = fakeCache,
        getDashboardDataUseCase = GetDashboardDataUseCase(fakeRepository)
      )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun fetchBudgetAdviceParameterlessLoadsFromUseCaseAndEmitsSuccess() {
    assertEquals(UiResult.Idle, viewModel.advisorState.value)

    viewModel.fetchBudgetAdvice(forceRefresh = true)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.advisorState.value
    assertTrue("Expected Success state but was $state", state is UiResult.Success)
    val advice = (state as UiResult.Success).data
    assertEquals("توصیه آزمایشی بودجه", advice)
  }

  @Test
  fun fetchBudgetForecastParameterlessLoadsFromUseCaseAndEmitsSuccess() {
    assertEquals(UiResult.Idle, viewModel.forecastState.value)

    viewModel.fetchBudgetForecast(forceRefresh = true)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.forecastState.value
    assertTrue("Expected Success state but was $state", state is UiResult.Success)
    val forecast = (state as UiResult.Success).data
    assertEquals("پیش‌بینی آزمایشی بودجه", forecast)
  }

  @Test
  fun fetchBudgetAdviceWhenSnapshotFailsEmitsErrorState() {
    val failingRepo =
      object : HesabyarRepositoryInterface by FakeRepository() {
        override val allTransactions: Flow<List<Transaction>> =
          flow {
            throw java.io.IOException("Disk read error")
          }
      }
    val failingViewModel =
      AiAssistantViewModel(
        parseTransactionUseCase = FakeParseTransactionUseCase(failingRepo),
        getBudgetAdviceUseCase = FakeGetBudgetAdviceUseCase(),
        getForecastUseCase = FakeGetForecastUseCase(),
        manageAiConfigUseCase = FakeManageAiConfigUseCase(RuntimeEnvironment.getApplication()),
        aiForecastAdviceCache = fakeCache,
        getDashboardDataUseCase = GetDashboardDataUseCase(failingRepo)
      )

    failingViewModel.fetchBudgetAdvice(forceRefresh = true)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = failingViewModel.advisorState.value
    assertTrue("Expected Error state but was $state", state is UiResult.Error)
    assertEquals("خطا در بارگذاری اطلاعات", (state as UiResult.Error).message)
  }

  @Test
  fun fetchBudgetForecastWhenSnapshotFailsEmitsErrorState() {
    val failingRepo =
      object : HesabyarRepositoryInterface by FakeRepository() {
        override val allTransactions: Flow<List<Transaction>> =
          flow {
            throw java.io.IOException("Disk read error")
          }
      }
    val failingViewModel =
      AiAssistantViewModel(
        parseTransactionUseCase = FakeParseTransactionUseCase(failingRepo),
        getBudgetAdviceUseCase = FakeGetBudgetAdviceUseCase(),
        getForecastUseCase = FakeGetForecastUseCase(),
        manageAiConfigUseCase = FakeManageAiConfigUseCase(RuntimeEnvironment.getApplication()),
        aiForecastAdviceCache = fakeCache,
        getDashboardDataUseCase = GetDashboardDataUseCase(failingRepo)
      )

    failingViewModel.fetchBudgetForecast(forceRefresh = true)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = failingViewModel.forecastState.value
    assertTrue("Expected Error state but was $state", state is UiResult.Error)
    assertEquals("خطا در بارگذاری اطلاعات", (state as UiResult.Error).message)
  }

  @Test
  fun clearAdvisorStateResetsStateToIdle() {
    viewModel.fetchBudgetAdvice(forceRefresh = true)
    testDispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.advisorState.value is UiResult.Success)

    viewModel.clearAdvisorState()
    assertEquals(UiResult.Idle, viewModel.advisorState.value)
  }

  @Test
  fun parseSmartSentenceWithDefaultParameterProcessesOffline() {
    viewModel.parseSmartSentence("ناهار ۵۰۰۰۰ تومن")
    testDispatcher.scheduler.advanceUntilIdle()

    val parserState = viewModel.parserState.value
    assertTrue(parserState is ParserUIState.Confirming)
  }

  private class FakeManageAiConfigUseCase(
    context: Context
  ) : ManageAiConfigUseCase(AiConfigManager(context)) {
    override fun loadConfigs(): List<AiProviderConfig> = emptyList()

    override fun getActiveConfigId(): String? = null

    override fun getActiveConfig(): AiProviderConfig? = null

    override fun isOnlineMode(): Boolean = false
  }

  private class FakeGetBudgetAdviceUseCase : GetBudgetAdviceUseCase() {
    override suspend fun getAdvice(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      categories: List<Category>,
      config: AiProviderConfig?,
      bankLoans: List<BankLoan>
    ): String = "توصیه آزمایشی بودجه"
  }

  private class FakeGetForecastUseCase : GetForecastUseCase() {
    override suspend fun getForecast(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      categories: List<Category>,
      config: AiProviderConfig?,
      bankLoans: List<BankLoan>
    ): String = "پیش‌بینی آزمایشی بودجه"
  }

  private class FakeParseTransactionUseCase(
    repository: HesabyarRepositoryInterface
  ) : ParseTransactionUseCase(repository) {
    override suspend fun parse(
      sentence: String,
      config: AiProviderConfig?
    ): ParsedResult =
      ParsedResult(
        type = TransactionType.EXPENSE.name,
        amount = 500_000L,
        category = "Food",
        personName = null,
        description = "ناهار",
        daysFromNow = null,
        title = "ناهار"
      )
  }
}
