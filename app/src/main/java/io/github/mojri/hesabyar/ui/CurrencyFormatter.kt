package io.github.mojri.hesabyar.ui

enum class CurrencyUnit(
  val key: String,
  val label: String
) {
  RIAL("rial", "ریال"),
  TOMAN("toman", "تومان");

  companion object {
    fun fromKey(key: String): CurrencyUnit =
      entries.firstOrNull { it.key == key }
        ?: entries.firstOrNull { it.label == key }
        ?: TOMAN
  }
}

/**
 * Centralized currency formatting.
 * DB stores amounts in Rial. This converts to the user-selected display unit.
 * Holds current unit as mutable state — SettingsViewModel updates it.
 */
object CurrencyFormatter {
  var currentUnit = CurrencyUnit.TOMAN
    private set

  val unitLabel: String get() = currentUnit.label

  fun setUnit(unit: CurrencyUnit) {
    currentUnit = unit
  }

  private fun toRustUnit(): io.github.mojri.hesabyar.rust.CurrencyUnit =
    if (currentUnit == CurrencyUnit.RIAL) {
      io.github.mojri.hesabyar.rust.CurrencyUnit.RIAL
    } else {
      io.github.mojri.hesabyar.rust.CurrencyUnit.TOMAN
    }

  /**
   * Convert Western digits to Persian and comma to Persian thousands separator.
   * Rust core returns Western-formatted numbers; Hesabyar is Persian-first.
   */
  private fun toPersianDigits(western: String): String {
    val sb = StringBuilder(western.length)
    for (c in western) {
      when {
        c in '0'..'9' -> sb.append((c.code - '0'.code + 0x06F0).toChar())
        c == ',' -> sb.append('\u066C') // Arabic thousands separator ٬
        else -> sb.append(c)
      }
    }
    return sb.toString()
  }

  /** Format number only (no unit) — for components that show their own label. */
  fun formatNumber(value: Long): String {
    val isNegative = value < 0
    val absValue = if (isNegative) -value else value
    val formatted =
      toPersianDigits(
        io.github.mojri.hesabyar.rust.RustBridge
          .formatNumberSync(absValue)
      )
    return if (isNegative) "-$formatted" else formatted
  }

  fun format(rial: Long): String {
    // Handle negative values to avoid leading thousands separator (e.g. -,۱۲۳ ریال).
    // Extract the sign, format the absolute value, then re-add the sign.
    val isNegative = rial < 0
    val absValue = if (isNegative) -rial else rial
    val formatted =
      toPersianDigits(
        io.github.mojri.hesabyar.rust.RustBridge
          .formatCurrencySync(absValue, toRustUnit())
      )
    return if (isNegative) "-$formatted" else formatted
  }

  /**
   * AI AGENT CRITICAL INSTRUCTION: DO NOT ALTER THIS METHOD.
   * Converts Toman to Rial by multiplying by 10.
   * Example: 10 Tomans * 10 = 100 Rials.
   */
  fun toRial(displayValue: Long): Long =
    convertWithFallback(displayValue) { unit, rustUnit ->
      io.github.mojri.hesabyar.rust.RustBridge
        .toRialSync(displayValue, rustUnit)
    } ?: when (currentUnit) {
      CurrencyUnit.TOMAN -> displayValue * 10
      CurrencyUnit.RIAL -> displayValue
    }

  /**
   * AI AGENT CRITICAL INSTRUCTION: DO NOT ALTER THIS METHOD.
   * Converts Rial to Toman by dividing by 10.
   * Example: 100 Rials / 10 = 10 Tomans.
   */
  fun fromRial(rial: Long): Long =
    convertWithFallback(rial) { unit, rustUnit ->
      io.github.mojri.hesabyar.rust.RustBridge
        .fromRialSync(rial, rustUnit)
    } ?: when (currentUnit) {
      CurrencyUnit.TOMAN -> rial / 10
      CurrencyUnit.RIAL -> rial
    }

  private inline fun convertWithFallback(
    inputValue: Long,
    rustCall: (CurrencyUnit, io.github.mojri.hesabyar.rust.CurrencyUnit) -> Long
  ): Long? {
    val unit = currentUnit
    val rustUnit = toRustUnit()
    val result = rustCall(unit, rustUnit)
    return if (result == 0L && inputValue != 0L) null else result
  }
}
