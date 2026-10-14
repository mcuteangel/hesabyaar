package io.github.mojri.hesabyar.core

/**
 * Largest plausible single amount, in Toman.
 *
 * The quick-fill buttons clamp their result to this value. Migrations that
 * must separate a legitimate amount from a corrupted one use the same bound,
 * so a database correction can never misjudge a value the user could
 * legitimately type.
 */
internal const val MAX_AMOUNT_TOMAN = 999_999_999_999L
