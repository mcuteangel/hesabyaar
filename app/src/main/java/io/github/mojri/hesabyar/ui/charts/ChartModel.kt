package io.github.mojri.hesabyar.ui.charts

import androidx.compose.ui.graphics.Color

data class BarEntry(
  val label: String,
  val value: Long,
  val color: Color? = null,
)

data class ChartPoint(
  val label: String,
  val value: Long,
)

data class ChartSeries(
  val name: String,
  val color: Color,
  val points: List<ChartPoint>,
)

data class DonutSlice(
  val label: String,
  val value: Long,
  val color: Color,
  val percentage: Float = 0f,
)
