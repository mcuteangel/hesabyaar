package io.github.mojri.hesabyar.ui.screens.dashboard.utils

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import java.util.*

internal fun formatPersianDate(timestamp: Long): String {
  val jalali = JalaliCalendarHelper.gregorianToJalali(timestamp)
  val cal = Calendar.getInstance()
  cal.timeInMillis = timestamp
  val hour = cal.get(Calendar.HOUR_OF_DAY)
  val minute = cal.get(Calendar.MINUTE)
  return String.format("%s - %02d:%02d", jalali.toString(), hour, minute)
}

internal fun extractForecastPreview(forecast: String): String {
  val lines = forecast.lines()
  val contentLines =
    lines
      .filter { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#")
      }.map { line ->
        line
          .trim()
          .removePrefix("-")
          .removePrefix("*")
          .trim()
      }.filter { it.isNotEmpty() }

  if (contentLines.isEmpty()) return "گزارش آماده است"

  val preview =
    contentLines.take(3).joinToString(" | ") { line ->
      if (line.length > 60) line.substring(0, 60).substringBeforeLast(" ") + "..." else line
    }

  return if (preview.length > 150) {
    preview.substring(0, 150).substringBeforeLast(" ") + "..."
  } else {
    preview
  }
}
