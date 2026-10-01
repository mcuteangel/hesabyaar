package io.github.mojri.hesabyar.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [HesabyarButton] loading state and accessibility semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarButtonTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun buttonShowsLabelAndSpinnerWithCustomContentDescriptionWhenLoading() {
    composeRule.setContent {
      HesabyarButton(
        onClick = {},
        text = "ذخیره",
        loading = true,
        iconContentDescription = "در حال ذخیره‌سازی"
      )
    }

    composeRule.onNodeWithText("ذخیره").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("در حال ذخیره‌سازی").assertIsDisplayed()
    composeRule.onNodeWithText("ذخیره").assertIsNotEnabled()
  }

  @Test
  fun buttonFallsBackToLabelForLoadingContentDescription() {
    composeRule.setContent {
      HesabyarButton(
        onClick = {},
        text = "تایید",
        loading = true
      )
    }

    composeRule.onNodeWithText("تایید").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("تایید").assertIsDisplayed()
    composeRule.onNodeWithText("تایید").assertIsNotEnabled()
  }
}
