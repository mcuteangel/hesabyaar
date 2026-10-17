package io.github.mojri.hesabyar.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

@Composable
fun HesabyarDonutChart(
  slices: List<DonutSlice>,
  modifier: Modifier = Modifier,
  height: Dp = ChartTokens.DonutChartHeight,
  strokeWidth: Dp = ChartTokens.DonutStrokeWidth,
  emptyTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  contentDescriptionText: String? = null,
) {
  val semanticsModifier =
    if (contentDescriptionText != null) {
      modifier.semantics { contentDescription = contentDescriptionText }
    } else {
      modifier
    }
  val total = slices.sumOf { it.value }

  Canvas(
    modifier =
      semanticsModifier
        .fillMaxWidth()
        .height(height),
  ) {
    val strokeWidthPx = strokeWidth.toPx()
    val diameter = minOf(size.width, size.height) - strokeWidthPx
    if (diameter <= 0f) return@Canvas
    val topLeft =
      Offset(
        (size.width - diameter) / 2f,
        (size.height - diameter) / 2f,
      )
    val arcSize = Size(diameter, diameter)

    if (slices.isEmpty() || total <= 0L) {
      drawArc(
        color = emptyTrackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
      )
      return@Canvas
    }

    var startAngle = -90f
    slices.forEach { slice ->
      val sweepAngle = if (total > 0L && slice.value > 0L) slice.value.toFloat() / total.toFloat() * 360f else 0f
      if (sweepAngle > 0f) {
        drawArc(
          color = slice.color,
          startAngle = startAngle,
          sweepAngle = sweepAngle,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
      }
      startAngle += sweepAngle
    }
  }
}
