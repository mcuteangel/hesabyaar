package io.github.mojri.hesabyar

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.runComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBankLoanUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import io.github.mojri.hesabyar.ui.BankLoanViewModel
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.FakeRepository
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.screens.DebtSection
import io.github.mojri.hesabyar.ui.screens.InstallmentScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the #348 acceptance criteria that survive a configuration change:
 * the current tab does not fall back to the dashboard, and a half-filled
 * installment form keeps its values. Navigation state was already
 * rememberSaveable on main; this test locks that behaviour in instead of
 * re-implementing it.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class StateRestorationTest {
  @get:Rule
  val rustIsolationRule = RustIsolationRule()

  private val testDispatcher = StandardTestDispatcher()
  private val context = ApplicationProvider.getApplicationContext<Context>()

  private class RecordingRepository : FakeRepository() {
    val saved = mutableListOf<Installment>()

    override suspend fun insertInstallment(installment: Installment): Long {
      saved += installment
      return saved.size.toLong()
    }
  }

  private val repo = RecordingRepository()
  private val installmentViewModel: InstallmentViewModel by lazy {
    InstallmentViewModel(ManageInstallmentUseCase(repo))
  }
  private val settingsViewModel: SettingsViewModel by lazy {
    val prefs = context.getSharedPreferences("state_restoration_test", Context.MODE_PRIVATE)
    SettingsViewModel(GetSettingsUseCase(prefs))
  }
  private val bankLoanViewModel: BankLoanViewModel by lazy {
    BankLoanViewModel(ManageBankLoanUseCase(repo))
  }

  private var previousUnit: CurrencyUnit? = null

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    HesabyarApp.setRustInitializedForTesting(false)
    previousUnit = CurrencyFormatter.currentUnit
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    repo.saved.clear()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    HesabyarApp.setRustInitializedForTesting(null)
    previousUnit?.let { CurrencyFormatter.setUnit(it) }
  }

  @Test
  fun tabSurvivesRestoration() =
    runComposeUiTest {
      fun settle() {
        testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()
      }

      val tester = StateRestorationTester(this)
      var observedTab = TAB_DASHBOARD
      var navToAssistant: (() -> Unit)? = null

      tester.setContent {
        MainNavigationCoordinator(
          startTab = TAB_DASHBOARD,
          startDebtSection = DebtSection.INSTALLMENTS,
          onExitConfirmed = {},
          mainContent = { currentTab, _, callbacks ->
            observedTab = currentTab
            navToAssistant = callbacks.onNavigateToAssistant
            Box(Modifier.fillMaxSize().testTag("main_content"))
          }
        )
      }
      onNodeWithTag("main_content").assertIsDisplayed()

      runOnUiThread { checkNotNull(navToAssistant)() }
      settle()
      assertEquals(TAB_ASSISTANT, observedTab)

      tester.emulateSavedInstanceStateRestore()
      settle()
      onNodeWithTag("main_content").assertIsDisplayed()
      assertEquals(TAB_ASSISTANT, observedTab)
    }

  @Test
  fun installmentAddDialogSurvivesRestoration() =
    runComposeUiTest {
      fun settle() {
        testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()
      }

      val tester = StateRestorationTester(this)
      tester.setContent {
        InstallmentScreen(
          installmentViewModel = installmentViewModel,
          settingsViewModel = settingsViewModel,
          bankLoanViewModel = bankLoanViewModel
        )
      }
      onNodeWithTag("add_installment_button").performClick()
      settle()

      onNode(hasText("عنوان قسط (مثلا قسط ماشین)").and(hasSetTextAction()))
        .performTextInput("قسط ماشین")
      onNode(hasText("مبلغ قسط (${CurrencyFormatter.unitLabel})").and(hasSetTextAction()))
        .performTextInput("۱۲۳۴۵")
      settle()

      tester.emulateSavedInstanceStateRestore()
      settle()

      onNodeWithText("ثبت قسط جدید").assertIsDisplayed()
      onNode(hasText("قسط ماشین").and(hasSetTextAction()))
        .assertIsDisplayed()
      onNode(hasText("12345").and(hasSetTextAction()))
        .assertIsDisplayed()
    }
}
