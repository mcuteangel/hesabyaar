package io.github.mojri.hesabyar.ui.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

@Composable
fun HesabyarProgressRing(
  progress: Float,
  modifier: Modifier = Modifier,
  size: Dp = ChartTokens.ProgressRingSize,
  color: Color = MaterialTheme.colorScheme.primary,
  trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  strokeWidth: Dp = ChartTokens.ProgressStrokeWidth,
  animated: Boolean = true,
) {
  val clampedProgress = progress.coerceIn(0f, 1f)
  val animatedProgress by animateFloatAsState(
    targetValue = clampedProgress,
    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
    label = "progress",
  )
  val effectiveProgress = if (animated) animatedProgress else clampedProgress
  val percentLabel = (effectiveProgress * 100).roundToInt()

  Canvas(
    modifier =
      modifier
        .size(size)
        .semantics {
          progressBarRangeInfo = ProgressBarRangeInfo(effectiveProgress, 0f..1f)
          contentDescription = "پیشرفت $percentLabel درصد"
          stateDescription = "$percentLabel%"
        },
  ) {
    val strokeWidthPx = strokeWidth.toPx()
    val diameter = minOf(this.size.width, this.size.height) - strokeWidthPx
    if (diameter <= 0f) return@Canvas
    val topLeft =
      Offset(
        (this.size.width - diameter) / 2f,
        (this.size.height - diameter) / 2f,
      )
    val arcSize = Size(diameter, diameter)

    drawArc(
      color = trackColor,
      startAngle = 0f,
      sweepAngle = 360f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
    )

    if (effectiveProgress > 0f) {
      drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = effectiveProgress * 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
      )
    }
  }
}
