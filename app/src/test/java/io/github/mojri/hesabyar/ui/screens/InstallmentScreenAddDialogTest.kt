package io.github.mojri.hesabyar.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class InstallmentScreenAddDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

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
    val prefs = context.getSharedPreferences("installment_add_test", Context.MODE_PRIVATE)
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

  private fun settle() {
    testDispatcher.scheduler.advanceUntilIdle()
    composeRule.waitForIdle()
  }

  private fun openAddDialog() {
    composeRule.setContent {
      InstallmentScreen(
        installmentViewModel = installmentViewModel,
        settingsViewModel = settingsViewModel,
        bankLoanViewModel = bankLoanViewModel
      )
    }
    composeRule.onNodeWithTag("add_installment_button").performClick()
    composeRule.waitForIdle()
  }

  private fun fillDialog(
    title: String,
    amount: String
  ) {
    composeRule
      .onNode(hasText(TITLE_LABEL).and(hasSetTextAction()))
      .performTextInput(title)
    composeRule
      .onNode(hasText(amountLabel()).and(hasSetTextAction()))
      .performTextInput(amount)
  }

  @Test
  fun persianDigitsAmountIsNormalizedAndSaved() {
    openAddDialog()
    fillDialog("قسط ماشین", "۱۲۳۴۵")
    composeRule.onNodeWithText(CONFIRM_BUTTON).performClick()
    settle()
    assertEquals("one installment must be saved", 1, repo.saved.size)
    assertEquals("amount in Rial", 123_450L, repo.saved.single().amount)
    assertEquals("title passed through", "قسط ماشین", repo.saved.single().title)
    composeRule.onNodeWithText(DIALOG_TITLE).assertDoesNotExist()
  }

  @Test
  fun arabicIndicDigitsAmountIsNormalizedAndSaved() {
    openAddDialog()
    fillDialog("قسط وام", "١٢٣")
    composeRule.onNodeWithText(CONFIRM_BUTTON).performClick()
    settle()
    assertEquals("one installment must be saved", 1, repo.saved.size)
    assertEquals("amount in Rial", 1_230L, repo.saved.single().amount)
  }

  @Test
  fun emptyAmountBlocksSaveAndKeepsDialogOpen() {
    openAddDialog()
    fillDialog("قسط ماشین", "")
    composeRule.onNodeWithText(CONFIRM_BUTTON).performClick()
    settle()
    assertTrue("nothing must be saved", repo.saved.isEmpty())
    composeRule.onNodeWithText(DIALOG_TITLE).assertIsDisplayed()
  }

  @Test
  fun negativeAmountIsRejectedAndKeepsDialogOpen() {
    openAddDialog()
    fillDialog("قسط ماشین", "-5000")
    composeRule.onNodeWithText(CONFIRM_BUTTON).performClick()
    settle()
    assertTrue("nothing must be saved", repo.saved.isEmpty())
    composeRule.onNodeWithText(DIALOG_TITLE).assertIsDisplayed()
  }

  @Test
  fun tomanAmountOverflowingRialConversionIsRejectedAndKeepsDialogOpen() {
    openAddDialog()
    fillDialog("قسط ماشین", "922337203685477581")
    composeRule.onNodeWithText(CONFIRM_BUTTON).performClick()
    settle()
    assertTrue("nothing must be saved", repo.saved.isEmpty())
    composeRule.onNodeWithText(DIALOG_TITLE).assertIsDisplayed()
  }

  @Test
  fun tomanAmountAtOverflowBoundaryIsAccepted() {
    openAddDialog()
    fillDialog("قسط ماشین", "922337203685477580")
    composeRule.onNodeWithText(CONFIRM_BUTTON).performClick()
    settle()
    assertEquals("one installment must be saved", 1, repo.saved.size)
    assertEquals("amount in Rial", 9_223_372_036_854_775_800L, repo.saved.single().amount)
    composeRule.onNodeWithText(DIALOG_TITLE).assertDoesNotExist()
  }

  @Test
  fun rialModeAcceptsLongMaxValueUnchanged() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    openAddDialog()
    fillDialog("قسط ماشین", Long.MAX_VALUE.toString())
    composeRule.onNodeWithText(CONFIRM_BUTTON).performClick()
    settle()
    assertEquals("one installment must be saved", 1, repo.saved.size)
    assertEquals("amount unchanged in Rial mode", Long.MAX_VALUE, repo.saved.single().amount)
    composeRule.onNodeWithText(DIALOG_TITLE).assertDoesNotExist()
  }

  companion object {
    private const val DIALOG_TITLE = "ثبت قسط جدید"
    private const val CONFIRM_BUTTON = "ذخیره قسط"
    private const val TITLE_LABEL = "عنوان قسط (مثلا قسط ماشین)"

    private fun amountLabel() = "مبلغ قسط (${CurrencyFormatter.unitLabel})"
  }
}
