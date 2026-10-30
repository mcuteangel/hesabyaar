package io.github.mojri.hesabyar.core

// Numeric text helpers for Persian-first input fields.
//
// Persian keyboards emit Persian digits (۰-۹) and some keyboards emit
// Arabic-Indic digits (٠-٩). `Char.isDigit()` accepts them, but
// `String.toLongOrNull()` parses ASCII digits only. These helpers normalise
// the digits before parsing or filtering.

private fun Char.acceptedDigitOrNull(): Char? =
  when (this) {
    in '0'..'9' -> this
    in '۰'..'۹' -> (code - '۰'.code + '0'.code).toChar()
    in '٠'..'٩' -> (code - '٠'.code + '0'.code).toChar()
    else -> null
  }

/**
 * Keeps accepted digits only, in ASCII form. Use in an `onValueChange` filter
 * for positive integer input fields so the visible text stays parseable.
 * Removes all non-digit characters including minus sign, decimal point, and grouping separators.
 */
fun String.filterDigits(): String {
  var needsFiltering = false
  for (c in this) {
    if (c !in '0'..'9') {
      needsFiltering = true
      break
    }
  }
  if (!needsFiltering) return this

  return buildString(length) {
    for (c in this@filterDigits) {
      c.acceptedDigitOrNull()?.let { append(it) }
    }
  }
}

/**
 * Parses the accepted digits into a [Long].
 * The parser preserves a minus before the first digit.
 * The minus can be ASCII or Unicode.
 * It can follow leading whitespace and bidirectional marks.
 * A preserved minus yields a negative [Long].
 * The parser drops a minus anywhere else.
 * The parser returns null when the text holds no digit.
 * The parser returns null when the text holds only a lone minus.
 * The parser returns null when the value overflows [Long].
 * The parser returns null for negative overflow too.
 */
fun String.toCleanLongOrNull(): Long? = filterDigitsPreservingLeadingMinus().toLongOrNull()

/**
 * Keeps accepted digits only, in ASCII form.
 * The parser preserves a minus before the first digit.
 * The minus can be ASCII or Unicode.
 * Prefix text before the minus does not remove it.
 * The parser drops a minus after the first digit.
 * Validation can then reject negative input.
 */
fun String.filterDigitsPreservingLeadingMinus(): String {
  var sign = ""
  for (c in this) {
    if (c.acceptedDigitOrNull() != null) break
    if (c == '-' || c == '\u2212') {
      sign = "-"
      break
    }
  }
  return sign + filterDigits()
}
