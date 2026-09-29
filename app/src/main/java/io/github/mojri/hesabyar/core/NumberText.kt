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
 * so the visible text stays parseable.
 */
fun String.filterDigits(): String =
  buildString(length) {
    for (c in this@filterDigits) {
      c.acceptedDigitOrNull()?.let { append(it) }
    }
  }

/**
 * Parses the accepted digits into a [Long]. Other characters are ignored.
 * Returns null when the text holds no digit or overflows [Long].
 */
fun String.toCleanLongOrNull(): Long? = filterDigits().toLongOrNull()
