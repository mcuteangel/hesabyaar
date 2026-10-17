package io.github.mojri.hesabyar.ui.charts

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarDonutChartTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun donutChartRendersWithContentDescriptionAndHeight() {
    val description = "توزیع هزینه‌ها بر اساس دسته‌بندی"
    val slices =
      listOf(
        DonutSlice(label = "خوراک", value = 3_000_000L, color = Color.Red, percentage = 0.75f),
        DonutSlice(label = "حمل‌ونقل", value = 1_000_000L, color = Color.Blue, percentage = 0.25f),
      )

    composeRule.setContent {
      HesabyarDonutChart(
        slices = slices,
        contentDescriptionText = description,
        height = ChartTokens.DonutChartHeight,
      )
    }

    composeRule
      .onNodeWithContentDescription(description)
      .assertIsDisplayed()
      .assertHeightIsEqualTo(ChartTokens.DonutChartHeight)
  }

  @Test
  fun donutChartHandlesEmptySlicesGracefully() {
    val tag = "empty_donut_chart"
    composeRule.setContent {
      HesabyarDonutChart(
        slices = emptyList(),
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
  }

  @Test
  fun donutChartHandlesZeroTotalWithoutCrash() {
    val tag = "zero_total_donut_chart"
    val slices =
      listOf(
        DonutSlice(label = "خوراک", value = 0L, color = Color.Red),
        DonutSlice(label = "حمل‌ونقل", value = 0L, color = Color.Blue),
      )

    composeRule.setContent {
      HesabyarDonutChart(
        slices = slices,
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
  }
}
