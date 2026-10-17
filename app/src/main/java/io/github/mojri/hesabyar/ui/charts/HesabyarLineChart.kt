package io.github.mojri.hesabyar.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun HesabyarLineChart(
  series: List<ChartSeries>,
  modifier: Modifier = Modifier,
  height: Dp = ChartTokens.LineChartHeight,
  labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  contentDescriptionText: String? = null,
) {
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val density = LocalDensity.current
  val description =
    contentDescriptionText
      ?: series.joinToString(separator = ". ") { s -> "${s.name}: ${s.points.size} نقطه" }
  val semanticsModifier =
    if (description.isNotEmpty()) {
      modifier.semantics { contentDescription = description }
    } else {
      modifier
    }

  val axisLabelPaint =
    remember(labelColor, density) {
      android.graphics.Paint().apply {
        textSize = with(density) { ChartTokens.AxisLabelSize.toPx() }
        textAlign = android.graphics.Paint.Align.CENTER
        this.color = labelColor.toArgb()
        isAntiAlias = true
      }
    }

  Canvas(
    modifier =
      semanticsModifier
        .fillMaxWidth()
        .height(height),
  ) {
    val maxPoints = series.maxOfOrNull { it.points.size } ?: 0
    if (maxPoints < 1) return@Canvas

    val allValues = series.flatMap { s -> s.points.map { it.value } }
    val maxValue = allValues.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val labelHeightPx = ChartTokens.AxisLabelSize.toPx() + ChartTokens.LabelPaddingBottom.toPx()
    val chartHeightPx = (size.height - labelHeightPx - 8f).coerceAtLeast(0f)
    val contentPaddingPx = ChartTokens.ContentPadding.toPx()
    val availableWidthPx = size.width - 2f * contentPaddingPx
    val spacing = if (maxPoints > 1) availableWidthPx / (maxPoints - 1).toFloat() else availableWidthPx

    drawLineSeries(series, isRtl, maxPoints, contentPaddingPx, spacing, chartHeightPx, maxValue)
    drawChartLabels(series, isRtl, maxPoints, contentPaddingPx, spacing, axisLabelPaint)
  }
}

private fun DrawScope.drawLineSeries(
  series: List<ChartSeries>,
  isRtl: Boolean,
  maxPoints: Int,
  contentPaddingPx: Float,
  spacing: Float,
  chartHeightPx: Float,
  maxValue: Long,
) {
  val strokeWidthPx = ChartTokens.LineStrokeWidth.toPx()
  val pointRadiusPx = ChartTokens.PointRadius.toPx()

  series.forEach { s ->
    if (s.points.isEmpty()) return@forEach
    val pointOffsets =
      s.points.mapIndexed { idx, pt ->
        val effectiveIndex = if (isRtl) maxPoints - 1 - idx else idx
        val x = contentPaddingPx + effectiveIndex * spacing
        val y = chartHeightPx - pt.value.coerceAtLeast(0L).toFloat() / maxValue.toFloat() * chartHeightPx
        Offset(x, y)
      }

    for (i in 0 until pointOffsets.size - 1) {
      drawLine(
        color = s.color,
        start = pointOffsets[i],
        end = pointOffsets[i + 1],
        strokeWidth = strokeWidthPx,
        cap = StrokeCap.Round,
      )
    }
    pointOffsets.forEach { pt ->
      drawCircle(color = s.color, radius = pointRadiusPx, center = pt)
    }
  }
}

private fun DrawScope.drawChartLabels(
  series: List<ChartSeries>,
  isRtl: Boolean,
  maxPoints: Int,
  contentPaddingPx: Float,
  spacing: Float,
  labelPaint: android.graphics.Paint,
) {
  if (maxPoints < 1) return

  drawContext.canvas.nativeCanvas.apply {
    for (gridIndex in 0 until maxPoints) {
      val effectiveIndex = if (isRtl) maxPoints - 1 - gridIndex else gridIndex
      val x = contentPaddingPx + effectiveIndex * spacing
      val label =
        series
          .firstOrNull { gridIndex < it.points.size && it.points[gridIndex].label.isNotBlank() }
          ?.points
          ?.get(gridIndex)
          ?.label ?: ""
      if (label.isNotEmpty()) {
        drawText(label, x, size.height - ChartTokens.LabelPaddingBottom.toPx(), labelPaint)
      }
    }
  }
}
