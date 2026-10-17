package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
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
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SmartAssistantScreenConfirmFlowTest {
  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule
  val rustIsolationRule = RustIsolationRule()

  private val testDispatcher = StandardTestDispatcher()

  private val approved = mutableListOf<ParsedResult>()

  private var previousUnit: CurrencyUnit? = null

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    HesabyarApp.setRustInitializedForTesting(false)
    previousUnit = CurrencyFormatter.currentUnit
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    approved.clear()
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

  private fun openCard() {
    composeRule.setContent {
      ParsedResultCard(
        result =
          ParsedResult(
            type = TransactionType.EXPENSE.name,
            amount = 0L,
            category = "Other",
            personName = null,
            description = "تست",
            daysFromNow = null,
            title = null
          ),
        categories = emptyList(),
        onApprove = { r, _ -> approved += r },
        onCancel = {}
      )
    }
    composeRule.waitForIdle()
  }

  private fun confirmWith(amountText: String) {
    composeRule.onNodeWithTag("parsed_amount_input").performTextReplacement(amountText)
    settle()
    composeRule.onNodeWithText(CONFIRM_BUTTON).performClick()
    settle()
  }

  @Test
  fun tomanAmountAtOverflowBoundaryIsAccepted() {
    openCard()
    confirmWith("922337203685477580")
    assertEquals("one approval expected", 1, approved.size)
    assertEquals("amount in Rial", 9_223_372_036_854_775_800L, approved.single().amount)
  }

  @Test
  fun tomanAmountOverflowingRialConversionIsRejectedWithToast() {
    openCard()
    confirmWith("922337203685477581")
    assertTrue("nothing must be approved", approved.isEmpty())
    assertEquals(
      "overflow toast",
      "مبلغ وارد شده بیش از حد مجاز است",
      ShadowToast.getTextOfLatestToast()
    )
    composeRule.onNodeWithText(CONFIRM_BUTTON).assertIsDisplayed()
  }

  @Test
  fun rialModeAcceptsLongMaxValueUnchanged() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    openCard()
    confirmWith(Long.MAX_VALUE.toString())
    assertEquals("one approval expected", 1, approved.size)
    assertEquals("amount unchanged in Rial mode", Long.MAX_VALUE, approved.single().amount)
  }

  @Test
  fun normalTomanAmountIsConvertedToRial() {
    openCard()
    confirmWith("5000")
    assertEquals("one approval expected", 1, approved.size)
    assertEquals("amount in Rial", 50_000L, approved.single().amount)
  }

  @Test
  fun overflowingAmountSuppressesEquivalentPreview() {
    openCard()
    composeRule.onNodeWithTag("parsed_amount_input").performTextReplacement("922337203685477581")
    settle()
    composeRule.onNodeWithText("معادل:", substring = true).assertDoesNotExist()
  }

  companion object {
    private const val CONFIRM_BUTTON = "ذخیره و ثبت نهایی"
  }
}
