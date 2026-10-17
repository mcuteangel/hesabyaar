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
class HesabyarLineChartTest {
  @get:Rule
  val composeRule = createComposeRule()

  private fun buildSeries(
    name: String,
    color: Color,
    labels: List<String>,
    values: List<Long>
  ): ChartSeries =
    ChartSeries(
      name = name,
      color = color,
      points =
        labels.mapIndexed { idx, label ->
          ChartPoint(label = label, value = values[idx])
        },
    )

  @Test
  fun lineChartRendersSingleSeriesWithContentDescription() {
    val description = "نمودار خطی درآمد ماهانه"
    val s =
      buildSeries(
        "income",
        Color.Green,
        listOf("فروردین", "اردیبهشت", "خرداد"),
        listOf(1_000_000L, 2_000_000L, 1_500_000L)
      )
    composeRule.setContent {
      HesabyarLineChart(
        series = listOf(s),
        contentDescriptionText = description,
        height = ChartTokens.LineChartHeight,
      )
    }
    composeRule
      .onNodeWithContentDescription(
        description
      ).assertIsDisplayed()
      .assertHeightIsEqualTo(ChartTokens.LineChartHeight)
  }

  @Test
  fun lineChartRendersEmptySeriesWithoutCrash() {
    val tag = "empty_line_chart"
    composeRule.setContent {
      HesabyarLineChart(
        series = emptyList(),
        modifier = Modifier.testTag(tag),
      )
    }
    composeRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  @Test
  fun lineChartRendersInRtlLayout() {
    val s1 = buildSeries("income", Color.Green, listOf("مهر", "آبان"), listOf(500_000L, 1_200_000L))
    val s2 = buildSeries("expense", Color.Red, listOf("مهر", "آبان"), listOf(300_000L, 800_000L))

    composeRule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HesabyarLineChart(
          series = listOf(s1, s2),
          modifier = Modifier.testTag("rtl_line"),
        )
      }
    }
    composeRule.onNodeWithTag("rtl_line").assertIsDisplayed()
  }

  @Test
  fun lineChartRendersInLtrLayout() {
    val s1 = buildSeries("income", Color.Green, listOf("مهر", "آبان"), listOf(500_000L, 1_200_000L))
    val s2 = buildSeries("expense", Color.Red, listOf("مهر", "آبان"), listOf(300_000L, 800_000L))

    composeRule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HesabyarLineChart(
          series = listOf(s1, s2),
          modifier = Modifier.testTag("ltr_line"),
        )
      }
    }
    composeRule.onNodeWithTag("ltr_line").assertIsDisplayed()
  }

  @Test
  fun lineChartGeneratesDefaultContentDescription() {
    val s = buildSeries("درآمد", Color.Green, listOf("فروردین"), listOf(1_000_000L))
    composeRule.setContent {
      HesabyarLineChart(
        series = listOf(s),
      )
    }

    composeRule
      .onNodeWithContentDescription("درآمد: 1 نقطه")
      .assertIsDisplayed()
  }

  @Test
  fun lineChartHandlesSinglePointSeries() {
    val tag = "single_point_line"
    val s = buildSeries("هزینه", Color.Red, listOf("اردیبهشت"), listOf(500_000L))
    composeRule.setContent {
      HesabyarLineChart(
        series = listOf(s),
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  @Test
  fun lineChartHandlesSeriesWithEmptyPoints() {
    val tag = "empty_points_series"
    val s = ChartSeries(name = "خالی", color = Color.Gray, points = emptyList())
    composeRule.setContent {
      HesabyarLineChart(
        series = listOf(s),
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule.onNodeWithTag(tag).assertIsDisplayed()
  }
}
