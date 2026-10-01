package io.github.mojri.hesabyar.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
  fun buttonCombinesLabelAndCustomContentDescriptionWhenLoading() {
    composeRule.setContent {
      HesabyarButton(
        onClick = {},
        text = TEXT_SAVE,
        loading = true,
        iconContentDescription = DESC_SAVING
      )
    }

    composeRule.onNodeWithContentDescription(EXPECTED_SAVE_LOADING_DESC).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(EXPECTED_SAVE_LOADING_DESC).assertIsNotEnabled()
  }

  @Test
  fun buttonCombinesLabelAndDefaultLoadingDescription() {
    composeRule.setContent {
      HesabyarButton(
        onClick = {},
        text = TEXT_CONFIRM,
        loading = true
      )
    }

    composeRule.onNodeWithContentDescription(EXPECTED_CONFIRM_LOADING_DESC).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(EXPECTED_CONFIRM_LOADING_DESC).assertIsNotEnabled()
  }

  private companion object {
    const val TEXT_SAVE = "ذخیره"
    const val DESC_SAVING = "در حال ذخیره‌سازی"
    const val EXPECTED_SAVE_LOADING_DESC = "ذخیره، در حال ذخیره‌سازی"
    const val TEXT_CONFIRM = "تایید"
    const val EXPECTED_CONFIRM_LOADING_DESC = "تایید، در حال بارگذاری"
  }
}
