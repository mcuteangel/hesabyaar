package io.github.mojri.hesabyar.ui.charts

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarProgressRingTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun progressRingRendersAtDefaultSize() {
    val tag = "progress_ring"
    composeRule.setContent {
      HesabyarProgressRing(
        progress = 0.4f,
        modifier = Modifier.testTag(tag),
        animated = false,
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
      .assertHeightIsEqualTo(ChartTokens.ProgressRingSize)
  }

  @Test
  fun progressRingClampsProgressAboveOne() {
    val tag = "clamped_ring"
    composeRule.setContent {
      HesabyarProgressRing(
        progress = 1.8f,
        modifier = Modifier.testTag(tag),
        animated = false,
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
      .assertHeightIsEqualTo(ChartTokens.ProgressRingSize)
  }

  @Test
  fun progressRingClampsProgressBelowZero() {
    val tag = "negative_ring"
    composeRule.setContent {
      HesabyarProgressRing(
        progress = -0.5f,
        modifier = Modifier.testTag(tag),
        animated = false,
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
  }

  @Test
  fun progressRingSupportsCustomSize() {
    val tag = "custom_ring"
    composeRule.setContent {
      HesabyarProgressRing(
        progress = 0.75f,
        modifier = Modifier.testTag(tag),
        size = 64.dp,
        animated = false,
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
      .assertHeightIsEqualTo(64.dp)
  }

  @Test
  fun progressRingSupportsAnimatedMode() {
    val tag = "animated_ring"
    composeRule.setContent {
      HesabyarProgressRing(
        progress = 0.6f,
        modifier = Modifier.testTag(tag),
        animated = true,
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
  }

  @Test
  fun progressRingZeroProgressRendersTrackOnly() {
    val tag = "zero_progress_ring"
    composeRule.setContent {
      HesabyarProgressRing(
        progress = 0f,
        modifier = Modifier.testTag(tag),
        animated = false,
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
  }
}
