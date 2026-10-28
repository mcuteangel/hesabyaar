package io.github.mojri.hesabyar.ui.screens.account

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.mojri.hesabyar.domain.usecase.FakeRepository
import io.github.mojri.hesabyar.ui.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Proves the #348 account-form objective: a half-filled add-account
 * dialog keeps its values and stays open across restoration.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AccountDialogRestorationTest {
  private val testDispatcher = StandardTestDispatcher()
  private val fakeRepo = FakeRepository()
  private lateinit var viewModel: AccountViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    viewModel = AccountViewModel(RuntimeEnvironment.getApplication(), fakeRepo)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun addDialogSurvivesRestoration() =
    runComposeUiTest {
      val tester = StateRestorationTester(this)
      tester.setContent {
        AccountManagementScreen(accountViewModel = viewModel, onBack = {})
      }
      onNodeWithContentDescription("افزودن حساب").performClick()
      onNodeWithText("افزودن حساب جدید").assertIsDisplayed()
      onNode(hasText("نام حساب").and(hasSetTextAction())).performTextInput("حساب تست")
      testDispatcher.scheduler.advanceUntilIdle()
      waitForIdle()

      tester.emulateSaveAndRestore()
      testDispatcher.scheduler.advanceUntilIdle()
      waitForIdle()

      onNodeWithText("افزودن حساب جدید").assertIsDisplayed()
      onNode(hasText("حساب تست").and(hasSetTextAction())).assertIsDisplayed()
    }
}
