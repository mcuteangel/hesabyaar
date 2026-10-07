package io.github.mojri.hesabyar.ui.utils

import io.github.mojri.hesabyar.ui.CurrencyFormatter

/**
 * Adapter between amount text fields and stored amounts.
 *
 * Users type amounts in their chosen display unit ([CurrencyFormatter.currentUnit],
 * usually Toman), while the database stores Rial. Every amount field must go
 * through [CurrencyFormatter.toRial] / [CurrencyFormatter.fromRial]; this helper
 * keeps that conversion (and sign handling) in one place for fields that are not
 * covered by `TransactionAmountResolver`.
 */
object DisplayAmountInput {
  /**
   * Parses [text] typed in the display unit and returns the Rial value, or null
   * when the text is blank or not a valid integer. Negative values keep their sign.
   */
  fun toRialOrNull(
    text: String,
    toRial: (Long) -> Long = CurrencyFormatter::toRial
  ): Long? {
    val display = text.trim().toLongOrNull() ?: return null
    return when {
      display == 0L -> 0L
      display < 0L && display != Long.MIN_VALUE -> -toRial(-display)
      else -> toRial(display)
    }
  }

  /** Converts a stored Rial value into display-unit text for prefilling an edit field. */
  fun toDisplayText(
    rial: Long,
    fromRial: (Long) -> Long = CurrencyFormatter::fromRial
  ): String {
    val display =
      when {
        rial == 0L -> 0L
        rial < 0L && rial != Long.MIN_VALUE -> -fromRial(-rial)
        else -> fromRial(rial)
      }
    return display.toString()
  }

  /**
   * Resolves the Rial value of an edit field. When the user left the prefilled
   * text untouched, the original Rial value is kept as-is so a Rial → Toman → Rial
   * round trip cannot drop the last digit (e.g. 12_345 Rial shown as 1_234 Toman).
   */
  fun resolveEditedRial(
    text: String,
    prefilledText: String,
    originalRial: Long?,
    toRial: (Long) -> Long = CurrencyFormatter::toRial
  ): Long? =
    if (originalRial != null && text.trim() == prefilledText) {
      originalRial
    } else {
      toRialOrNull(text, toRial)
    }
}
