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
   * Upper bound for a display-unit amount, so that the ×10 Toman → Rial
   * conversion can never overflow [Long]. Real amounts are many orders of
   * magnitude below this.
   */
  const val MAX_DISPLAY_AMOUNT: Long = Long.MAX_VALUE / 10

  /**
   * Parses [text] typed in the display unit and returns the Rial value, or null
   * when the text is blank, not a valid integer, or too large to convert safely.
   * Persian (۰-۹) and Arabic-Indic (٠-٩) digits and thousands separators are
   * accepted. Negative values keep their sign.
   */
  fun toRialOrNull(
    text: String,
    toRial: (Long) -> Long = CurrencyFormatter::toRial
  ): Long? {
    val display = normalizeDigits(text).toLongOrNull() ?: return null
    if (display > MAX_DISPLAY_AMOUNT || display < -MAX_DISPLAY_AMOUNT) return null
    return when {
      display == 0L -> 0L
      display < 0L -> -toRial(-display)
      else -> toRial(display)
    }
  }

  /**
   * True when both texts parse to the same display amount, so reformatting the
   * prefilled value (leading zero, Persian digits, separators) is not an edit.
   */
  private fun isSameDisplayAmount(
    text: String,
    prefilledText: String
  ): Boolean {
    val typed = normalizeDigits(text).toLongOrNull() ?: return false
    return typed == normalizeDigits(prefilledText).toLongOrNull()
  }

  /**
   * Maps Persian and Arabic-Indic digits to ASCII, drops thousands separators
   * (`,` `٬` `،`) and whitespace, and accepts the Unicode minus sign.
   */
  internal fun normalizeDigits(text: String): String =
    buildString(text.length) {
      for (c in text) {
        when (c) {
          in '\u06F0'..'\u06F9' -> append('0' + (c - '\u06F0'))
          in '\u0660'..'\u0669' -> append('0' + (c - '\u0660'))
          ',', '\u066C', '\u060C' -> Unit
          '\u2212' -> append('-')
          else -> if (!c.isWhitespace()) append(c)
        }
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
    if (originalRial != null && isSameDisplayAmount(text, prefilledText)) {
      originalRial
    } else {
      toRialOrNull(text, toRial)
    }
}
