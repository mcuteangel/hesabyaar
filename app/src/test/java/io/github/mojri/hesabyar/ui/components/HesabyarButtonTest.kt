package io.github.mojri.hesabyar.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
  fun loadingButtonPublishesActionNameAsContentDescription() {
    composeRule.setContent {
      HesabyarButton(
        onClick = {},
        text = TEXT_SAVE,
        loading = true,
        iconContentDescription = DESC_SAVING
      )
    }

    val node =
      composeRule
        .onNodeWithContentDescription(TEXT_SAVE)
        .assertIsDisplayed()
    node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, DESC_SAVING))
    node.assertIsNotEnabled()
  }

  @Test
  fun loadingButtonFallsBackToDefaultLoadingStateDescription() {
    composeRule.setContent {
      HesabyarButton(
        onClick = {},
        text = TEXT_CONFIRM,
        loading = true
      )
    }

    val node =
      composeRule
        .onNodeWithContentDescription(TEXT_CONFIRM)
        .assertIsDisplayed()
    node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, DEFAULT_LOADING_DESC))
    node.assertIsNotEnabled()
  }

  @Test
  fun busyButtonKeepsClickActionDisabled() {
    var clicked = false
    composeRule.setContent {
      HesabyarButton(
        onClick = { clicked = true },
        text = TEXT_SAVE,
        loading = true,
        iconContentDescription = DESC_SAVING
      )
    }

    composeRule
      .onNodeWithContentDescription(TEXT_SAVE)
      .assertIsNotEnabled()
  }

  private companion object {
    const val TEXT_SAVE = "ذخیره"
    const val DESC_SAVING = "در حال ذخیره‌سازی"
    const val TEXT_CONFIRM = "تایید"
    const val DEFAULT_LOADING_DESC = "در حال بارگذاری"
  }
}
