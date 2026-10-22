package io.github.mojri.hesabyar.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.LoanUpdateResult
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.FakeRepository
import io.github.mojri.hesabyar.ui.LoanViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen-level test for the issue #529 rejection path: while an edit save
 * runs, the dialog actions stay disabled; when the repository rejects the
 * new date, the editor stays open and the rejection message is emitted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LoanEditRejectionScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule
  val rustIsolationRule = RustIsolationRule()

  private val testDispatcher = StandardTestDispatcher()
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val repository = FakeRepository()
  private val loanViewModel: LoanViewModel by lazy {
    LoanViewModel(ManageLoanUseCase(repository))
  }
  private val settingsViewModel: SettingsViewModel by lazy {
    val prefs = context.getSharedPreferences("loan_edit_rejection_test", Context.MODE_PRIVATE)
    SettingsViewModel(GetSettingsUseCase(prefs))
  }
  private val firstMessage = CompletableDeferred<String>()
  private var messagesJob: Job? = null
  private var previousUnit: CurrencyUnit? = null

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    // Force the Kotlin fallback path so tests don't depend on the native lib.
    HesabyarApp.setRustInitializedForTesting(false)
    previousUnit = CurrencyFormatter.currentUnit
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    repository.loansList.add(sampleLoan())
    messagesJob =
      CoroutineScope(testDispatcher).launch {
        firstMessage.complete(settingsViewModel.uiMessage.first())
      }
  }

  @After
  fun tearDown() {
    messagesJob?.cancel()
    Dispatchers.resetMain()
    HesabyarApp.setRustInitializedForTesting(null)
    previousUnit?.let { CurrencyFormatter.setUnit(it) }
  }

  private fun settle() {
    testDispatcher.scheduler.advanceUntilIdle()
    composeRule.waitForIdle()
  }

  private fun sampleLoan() =
    Loan(
      id = 1L,
      personName = "Ali",
      type = LoanType.DEBTOR,
      originalAmount = 5_000L,
      remainingAmount = 5_000L,
      description = "test",
      date = 1_700_000_000_000L
    )

  @Test
  fun chronologyRejectionKeepsEditorOpenAndShowsMessage() {
    // Park the repository call on a gate so the in-flight state is stable.
    val gate = CompletableDeferred<Unit>()
    repository.updateLoanBehavior = {
      gate.await()
      LoanUpdateResult.ChronologyRejected
    }

    composeRule.setContent {
      LoanManagementScreen(
        loanViewModel = loanViewModel,
        settingsViewModel = settingsViewModel
      )
    }
    settle()

    // Expand the loan card and open the editor.
    composeRule.onNode(hasStateDescription("بسته")).performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithContentDescription("ویرایش قرض").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("ویرایش قرض").assertIsDisplayed()

    // Start the save; the update now sits parked on the gate.
    composeRule.onNodeWithText("ذخیره تغییرات").performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    composeRule.waitForIdle()

    // While the save runs, both dialog actions are disabled.
    composeRule.onNodeWithText("ذخیره تغییرات").assertIsNotEnabled()
    composeRule.onNodeWithText("انصراف").assertIsNotEnabled()

    // Release the repository with a chronology rejection.
    gate.complete(Unit)
    settle()

    // The editor stays open, the actions recover, and the message went out.
    composeRule.onNodeWithText("ویرایش قرض").assertIsDisplayed()
    composeRule.onNodeWithText("انصراف").assertIsEnabled()
    assertTrue("the rejection message must be emitted", firstMessage.isCompleted)
    assertEquals(
      "تاریخ جدید وام نمی‌تواند بعد از بازپرداخت‌های ثبت‌شده باشد",
      runBlocking { firstMessage.await() }
    )
  }
}
