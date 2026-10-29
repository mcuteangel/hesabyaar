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
 * The parser preserves a leading minus sign.
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

/** Bidirectional control characters (Unicode Bidi_Control property). */
private val BIDI_MARKS =
  setOf(
    '\u061C',
    '\u200E',
    '\u200F',
    '\u202A',
    '\u202B',
    '\u202C',
    '\u202D',
    '\u202E',
    '\u2066',
    '\u2067',
    '\u2068',
    '\u2069'
  )

/**
 * Keeps accepted digits only, in ASCII form, preserving a leading minus
 * sign (ASCII or Unicode) so validation can still reject negative input.
 * Leading whitespace and bidirectional marks before the sign are dropped;
 * a minus anywhere else is dropped with the other non-digit characters.
 */
fun String.filterDigitsPreservingLeadingMinus(): String {
  val trimmed = dropWhile { it.isWhitespace() || it in BIDI_MARKS }
  val sign = if (trimmed.startsWith("-") || trimmed.startsWith("\u2212")) "-" else ""
  return sign + trimmed.filterDigits()
}
