package io.github.mojri.hesabyar.ui.screens.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.domain.usecase.FakeRepository
import io.github.mojri.hesabyar.ui.AccountViewModel
import kotlinx.coroutines.CompletableDeferred
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Compose tests for the Add-account double-tap guard in
 * [AccountManagementScreen]: a second tap on Save while the first insert is
 * still suspended must not insert a second identical row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AccountManagementScreenDoubleTapTest {
  @get:Rule
  val composeRule = createComposeRule()

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
  fun doubleTapSaveInsertsOnlyOneAccount() {
    val insertGate = CompletableDeferred<Unit>()
    val gatedRepo = GatedFakeRepository(fakeRepo, insertGate)
    val gatedViewModel = AccountViewModel(RuntimeEnvironment.getApplication(), gatedRepo)
    composeRule.setContent {
      AccountManagementScreen(accountViewModel = gatedViewModel, onBack = {})
    }
    openAddDialog()
    typeAccountName("test account")
    clickSave()
    // Hold the first insert at the gate: Save must stay disabled for the
    // whole suspension, not only before the coroutine runs.
    settle()
    saveButton().assertIsNotEnabled()
    assertEquals(
      "Only one insert must start while the first is pending",
      1,
      gatedRepo.insertCalls
    )
    assertEquals(
      "No row must persist before the gate releases",
      0,
      fakeRepo.accountsList.size
    )
    // A second dialog opened mid-save shares the flag, so its Save must
    // also stay disabled and must not start a second insert.
    composeRule.onNodeWithText(CANCEL_BUTTON_TEXT).performClick()
    openAddDialog()
    typeAccountName("second account")
    saveButton().assertIsNotEnabled()
    assertEquals(
      "The second dialog must not start a second insert",
      1,
      gatedRepo.insertCalls
    )
    // Release the gate: the first insert completes, but it must not close
    // the second dialog, and the second dialog's Save becomes enabled again.
    insertGate.complete(Unit)
    settle()
    assertEquals(
      "The first insert must persist exactly one account",
      1,
      fakeRepo.accountsList.size
    )
    composeRule.onNodeWithText(ADD_DIALOG_TITLE).assertIsDisplayed()
    saveButton().assertIsEnabled()
    clickSave()
    settle()
    assertEquals(
      "The second dialog save must insert the second account",
      2,
      fakeRepo.accountsList.size
    )
    composeRule.onNodeWithText(ADD_DIALOG_TITLE).assertDoesNotExist()
  }

  @Test
  fun saveFailureKeepsDialogOpenAndSaveRetryable() {
    fakeRepo.shouldThrowOnInsert = true
    composeRule.setContent {
      AccountManagementScreen(accountViewModel = viewModel, onBack = {})
    }
    openAddDialog()
    typeAccountName("test account")
    clickSave()
    settle()
    composeRule.onNodeWithText(ADD_DIALOG_TITLE).assertIsDisplayed()
    saveButton().assertIsEnabled()
    fakeRepo.shouldThrowOnInsert = false
    clickSave()
    settle()
    assertEquals(
      "Retry after failure must insert exactly one account",
      1,
      fakeRepo.accountsList.size
    )
    composeRule.onNodeWithText(ADD_DIALOG_TITLE).assertDoesNotExist()
  }

  @Test
  fun secondAddDialogAfterSuccessInsertsAgain() {
    composeRule.setContent {
      AccountManagementScreen(accountViewModel = viewModel, onBack = {})
    }
    openAddDialog()
    typeAccountName("first account")
    clickSave()
    settle()
    assertEquals(
      "First save must insert exactly one account",
      1,
      fakeRepo.accountsList.size
    )
    composeRule.onNodeWithText(ADD_DIALOG_TITLE).assertDoesNotExist()
    openAddDialog()
    typeAccountName("second account")
    clickSave()
    settle()
    assertEquals(
      "Second Add dialog must insert a second account",
      2,
      fakeRepo.accountsList.size
    )
  }

  private fun openAddDialog() {
    composeRule.onNodeWithContentDescription(ADD_BUTTON_DESCRIPTION).performClick()
    composeRule.onNodeWithText(ADD_DIALOG_TITLE).assertIsDisplayed()
  }

  private fun typeAccountName(name: String) {
    composeRule
      .onNode(hasText(NAME_LABEL).and(hasSetTextAction()))
      .performTextInput(name)
  }

  private fun saveButton() = composeRule.onNodeWithText(SAVE_BUTTON_TEXT)

  private fun clickSave() {
    saveButton().performScrollTo().performClick()
  }

  private fun settle() {
    repeat(3) {
      testDispatcher.scheduler.advanceUntilIdle()
      composeRule.waitForIdle()
    }
  }

  private companion object {
    const val ADD_BUTTON_DESCRIPTION = "افزودن حساب"
    const val ADD_DIALOG_TITLE = "افزودن حساب جدید"
    const val NAME_LABEL = "نام حساب"
    const val SAVE_BUTTON_TEXT = "ذخیره"
    const val CANCEL_BUTTON_TEXT = "انصراف"
  }
}

/**
 * Wraps [FakeRepository] and holds each insert at [insertGate] so a test
 * can assert on the in-flight state before any row persists. All other
 * repository calls delegate unchanged, and [insertCalls] counts how many
 * inserts were launched.
 */
private class GatedFakeRepository(
  private val delegate: FakeRepository,
  private val insertGate: CompletableDeferred<Unit>,
) : HesabyarRepositoryInterface by delegate {
  var insertCalls = 0

  override suspend fun insertAccount(account: AccountEntity): Long {
    insertCalls++
    insertGate.await()
    return delegate.insertAccount(account)
  }
}
