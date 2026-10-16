package io.github.mojri.hesabyar.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun HesabyarBarChart(
  entries: List<BarEntry>,
  modifier: Modifier = Modifier,
  height: Dp = ChartTokens.DefaultHeight,
  defaultColor: Color = MaterialTheme.colorScheme.primary,
  labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  contentDescriptionText: String? = null,
) {
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val semanticsModifier =
    if (contentDescriptionText != null) {
      modifier.semantics { contentDescription = contentDescriptionText }
    } else {
      modifier
    }

  Canvas(
    modifier =
      semanticsModifier
        .fillMaxWidth()
        .height(height),
  ) {
    val barCount = entries.size
    if (barCount == 0) return@Canvas

    val maxValue = entries.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    val barWidth = size.width / (barCount * 2f)
    val spacing = barWidth
    val labelHeightPx = ChartTokens.AxisLabelSize.toPx() + ChartTokens.LabelPaddingBottom.toPx()
    val chartHeightPx = (size.height - labelHeightPx).coerceAtLeast(0f)
    val cornerRadiusPx = ChartTokens.BarCornerRadius.toPx()

    entries.forEachIndexed { index, entry ->
      val effectiveIndex = if (isRtl) barCount - 1 - index else index
      val x = effectiveIndex * (barWidth + spacing) + spacing / 2f
      val barHeight = if (maxValue > 0) entry.value.toFloat() / maxValue.toFloat() * chartHeightPx else 0f
      val y = chartHeightPx - barHeight

      drawRoundRect(
        color = entry.color ?: defaultColor,
        topLeft = Offset(x, y),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
      )
    }

    drawContext.canvas.nativeCanvas.apply {
      val paint =
        android.graphics.Paint().apply {
          textSize = ChartTokens.AxisLabelSize.toPx()
          textAlign = android.graphics.Paint.Align.CENTER
          this.color = labelColor.toArgb()
          isAntiAlias = true
        }
      entries.forEachIndexed { index, entry ->
        val effectiveIndex = if (isRtl) barCount - 1 - index else index
        val x = effectiveIndex * (barWidth + spacing) + spacing / 2f + barWidth / 2f
        drawText(entry.label, x, size.height - ChartTokens.LabelPaddingBottom.toPx(), paint)
      }
    }
  }
}
