package io.github.mojri.hesabyar.ui.charts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarBarChartTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun barChartRendersWithContentDescription() {
    val description = "نمودار میله‌ای هزینه‌های ماهانه"
    val entries =
      listOf(
        BarEntry(label = "فروردین", value = 1_000_000L),
        BarEntry(label = "اردیبهشت", value = 2_500_000L),
        BarEntry(label = "خرداد", value = 1_800_000L),
      )

    composeRule.setContent {
      HesabyarBarChart(
        entries = entries,
        contentDescriptionText = description,
        height = ChartTokens.DefaultHeight,
      )
    }

    composeRule
      .onNodeWithContentDescription(description)
      .assertIsDisplayed()
      .assertHeightIsEqualTo(ChartTokens.DefaultHeight)
  }

  @Test
  fun barChartHandlesEmptyEntriesGracefully() {
    val tag = "empty_bar_chart"
    composeRule.setContent {
      HesabyarBarChart(
        entries = emptyList(),
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
  }

  @Test
  fun barChartRendersInRtlLayout() {
    val tag = "rtl_bar_chart"
    val entries =
      listOf(
        BarEntry(label = "مهر", value = 500_000L, color = Color.Red),
        BarEntry(label = "آبان", value = 1_200_000L, color = Color.Blue),
      )

    composeRule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HesabyarBarChart(
          entries = entries,
          modifier = Modifier.testTag(tag),
        )
      }
    }

    composeRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  @Test
  fun barChartRendersInLtrLayout() {
    val tag = "ltr_bar_chart"
    val entries =
      listOf(
        BarEntry(label = "مهر", value = 500_000L, color = Color.Red),
        BarEntry(label = "آبان", value = 1_200_000L, color = Color.Blue),
      )

    composeRule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HesabyarBarChart(
          entries = entries,
          modifier = Modifier.testTag(tag),
        )
      }
    }

    composeRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  @Test
  fun barChartHandlesZeroValueEntries() {
    val tag = "zero_value_bars"
    val entries =
      listOf(
        BarEntry(label = "نمونه", value = 0L),
        BarEntry(label = "شکلی", value = 0L),
      )

    composeRule.setContent {
      HesabyarBarChart(
        entries = entries,
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  @Test
  fun barChartHandlesEntriesWithoutLabels() {
    val tag = "no_label_bars"
    val entries =
      listOf(
        BarEntry(label = "", value = 1_000_000L),
        BarEntry(label = "خالی", value = 500_000L),
      )

    composeRule.setContent {
      HesabyarBarChart(
        entries = entries,
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule.onNodeWithTag(tag).assertIsDisplayed()
  }
}
