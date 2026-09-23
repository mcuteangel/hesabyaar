package io.github.mojri.hesabyar.core

/**
 * Arithmetic helper functions with saturation to prevent Long overflow,
 * mirroring Rust core's `saturating_add` and `saturating_sub`.
 */
object MathUtils {
  /**
   * Adds two [Long] values with saturation at [Long.MAX_VALUE] and [Long.MIN_VALUE].
   */
  fun saturatingAdd(
    a: Long,
    b: Long
  ): Long {
    val res = a + b
    val signA = a xor res
    val signB = b xor res
    if (signA and signB < 0) {
      return if (a > 0) Long.MAX_VALUE else Long.MIN_VALUE
    }
    return res
  }

  /**
   * Subtracts [b] from [a] with saturation at [Long.MAX_VALUE] and [Long.MIN_VALUE].
   */
  fun saturatingSub(
    a: Long,
    b: Long
  ): Long {
    if (b == Long.MIN_VALUE) {
      // a - Long.MIN_VALUE overflows for every a. Rewrite as a + Long.MAX_VALUE,
      // then add 1 when that did not already saturate (i.e. a was not negative).
      val addMax = saturatingAdd(a, Long.MAX_VALUE)
      return if (addMax == Long.MAX_VALUE) addMax else addMax + 1
    }
    val res = a - b
    val diffSigns = a xor b
    val resultDiff = a xor res
    val overflowed = diffSigns and resultDiff < 0
    return when {
      !overflowed -> res
      a >= 0 -> Long.MAX_VALUE
      else -> Long.MIN_VALUE
    }
  }
}
