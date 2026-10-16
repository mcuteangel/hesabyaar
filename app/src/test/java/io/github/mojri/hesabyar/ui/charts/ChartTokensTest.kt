package io.github.mojri.hesabyar.ui.charts

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartTokensTest {
  @Test
  fun chartDimensionsTokensMatchSpec() {
    assertEquals("Default height should be 180dp", 180.dp, ChartTokens.DefaultHeight)
    assertEquals("Line chart height should be 160dp", 160.dp, ChartTokens.LineChartHeight)
    assertEquals("Donut chart height should be 200dp", 200.dp, ChartTokens.DonutChartHeight)
    assertEquals("Progress ring size should be 100dp", 100.dp, ChartTokens.ProgressRingSize)

    assertEquals("Bar corner radius should be 4dp", 4.dp, ChartTokens.BarCornerRadius)
    assertEquals("Line stroke width should be 2dp", 2.dp, ChartTokens.LineStrokeWidth)
    assertEquals("Point radius should be 3dp", 3.dp, ChartTokens.PointRadius)
    assertEquals("Donut stroke width should be 20dp", 20.dp, ChartTokens.DonutStrokeWidth)
    assertEquals("Progress stroke width should be 6dp", 6.dp, ChartTokens.ProgressStrokeWidth)

    assertEquals("Axis label size should be 11sp", 11.sp, ChartTokens.AxisLabelSize)
    assertEquals("Label padding bottom should be 4dp", 4.dp, ChartTokens.LabelPaddingBottom)
    assertEquals("Content padding should be 12dp", 12.dp, ChartTokens.ContentPadding)
  }

  @Test
  fun chartTokensHavePositiveDimensions() {
    assertTrue("DefaultHeight must be positive", ChartTokens.DefaultHeight.value > 0f)
    assertTrue("LineChartHeight must be positive", ChartTokens.LineChartHeight.value > 0f)
    assertTrue("DonutChartHeight must be positive", ChartTokens.DonutChartHeight.value > 0f)
    assertTrue("ProgressRingSize must be positive", ChartTokens.ProgressRingSize.value > 0f)
    assertTrue("BarCornerRadius must be positive", ChartTokens.BarCornerRadius.value > 0f)
    assertTrue("LineStrokeWidth must be positive", ChartTokens.LineStrokeWidth.value > 0f)
    assertTrue("PointRadius must be positive", ChartTokens.PointRadius.value > 0f)
    assertTrue("DonutStrokeWidth must be positive", ChartTokens.DonutStrokeWidth.value > 0f)
    assertTrue("ProgressStrokeWidth must be positive", ChartTokens.ProgressStrokeWidth.value > 0f)
    assertTrue("AxisLabelSize must be positive", ChartTokens.AxisLabelSize.value > 0f)
  }
}
