package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.ui.UiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric UI tests for [AiResultCard] and [AiResultContent].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AiResultCardTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun aiResultCardRendersHeaderWithHeadingSemantics() {
    composeRule.setContent {
      AiResultCard(
        state = UiResult.Idle,
        title = "تحلیل هوشمند",
        subtitle = "پیش‌بینی ماهانه"
      ) {
        Text("Content")
      }
    }

    composeRule
      .onNodeWithText("تحلیل هوشمند")
      .assertIsDisplayed()
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

    composeRule.onNodeWithText("پیش‌بینی ماهانه").assertIsDisplayed()
  }

  @Test
  fun idleStateRendersDescriptionAndTriggersFetch() {
    var fetchCalled = false

    composeRule.setContent {
      AiResultCard(
        state = UiResult.Idle,
        idleDescription = "توضیحات حالت آماده‌به‌کار",
        idleActionText = "دریافت تحلیل",
        onFetch = { fetchCalled = true }
      ) {
        Text("Success content")
      }
    }

    composeRule.onNodeWithText("توضیحات حالت آماده‌به‌کار").assertIsDisplayed()
    composeRule.onNodeWithTag("ai_result_fetch_button").assertIsDisplayed().performClick()
    assertTrue(fetchCalled)
  }

  @Test
  fun loadingStateRendersIndicatorAndLoadingText() {
    composeRule.setContent {
      AiResultCard(
        state = UiResult.Loading,
        loadingText = "درحال ارزیابی بودجه..."
      ) {
        Text("Success content")
      }
    }

    composeRule.onNodeWithTag("ai_result_loading").assertIsDisplayed()
    composeRule.onNodeWithText("درحال ارزیابی بودجه...").assertIsDisplayed()
  }

  @Test
  fun errorStateRendersMessageAndTriggersRetry() {
    var retryCount = 0

    composeRule.setContent {
      AiResultCard(
        state = UiResult.Error("خطای شبکه رخ داده است"),
        errorTitle = "خطا در برقراری ارتباط",
        onRetry = { retryCount++ }
      ) {
        Text("Success content")
      }
    }

    composeRule.onNodeWithTag("ai_result_error").assertIsDisplayed()
    composeRule.onNodeWithText("خطا در برقراری ارتباط").assertIsDisplayed()
    composeRule.onNodeWithText("خطای شبکه رخ داده است").assertIsDisplayed()
    composeRule.onNodeWithTag("ai_result_retry_button").assertIsDisplayed().performClick()
    assertEquals(1, retryCount)
  }

  @Test
  fun successStateRendersContentAndTriggersActions() {
    var refreshCalled = false
    var closeCalled = false

    composeRule.setContent {
      AiResultCard(
        state = UiResult.Success("پاسخ نهایی تحلیل"),
        onRefresh = { refreshCalled = true },
        onClose = { closeCalled = true }
      ) { data ->
        Text(text = "داده: $data", modifier = Modifier.testTag("custom_data_node"))
      }
    }

    composeRule.onNodeWithTag("custom_data_node").assertIsDisplayed()
    composeRule.onNodeWithText("داده: پاسخ نهایی تحلیل").assertIsDisplayed()

    composeRule.onNodeWithTag("ai_result_refresh_button").assertIsDisplayed().performClick()
    assertTrue(refreshCalled)

    composeRule.onNodeWithTag("ai_result_close_button").assertIsDisplayed().performClick()
    assertTrue(closeCalled)
  }

  @Test
  fun customSlotsOverrideDefaultStateRendering() {
    composeRule.setContent {
      AiResultContent(
        state = UiResult.Idle,
        idleContent = { Text("سفارشی: آماده به کار") }
      ) {
        Text("Success")
      }
    }

    composeRule.onNodeWithText("سفارشی: آماده به کار").assertIsDisplayed()
  }
}
