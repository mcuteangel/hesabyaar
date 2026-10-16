package io.github.mojri.hesabyar.core

/**
 * Practical upper bound on a plausible single amount, in Toman.
 *
 * The quick-fill buttons clamp their result to this value. Migrations that
 * must separate a legitimate amount from an inflated one use the same bound
 * as a heuristic: ordinary personal transactions remain below this threshold,
 * keeping the risk of misclassifying legitimate data minimal.
 */
internal const val MAX_AMOUNT_TOMAN = 999_999_999_999L
