package io.github.mojri.hesabyar.domain.utils

/**
 * Normalizes person names into dedup keys (plans/011 §D4).
 *
 * ADR-001 permanent fallback ("Person-name normalization"): Room migrations
 * cannot load the native library, so backfill runs this Kotlin util inside
 * the migration. Every runtime create/rename path reuses it, so dedup
 * semantics never drift between migration and runtime.
 */
object PersonNameNormalizer {
  // Code points: ZWSP(200B), ZWNJ(200C), ZWJ(200D), word joiner(2060), BOM(FEFF).
  private val zeroWidthCodes = setOf(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF)

  // Arabic yeh/kaf/teh-marbuta fold to their Persian counterparts.
  private val arabicToPersian = mapOf('ي' to 'ی', 'ك' to 'ک', 'ة' to 'ه')

  /**
   * Collapses spelling variants to one dedup key: trims, collapses internal
   * whitespace to single spaces, strips zero-width characters, folds Arabic
   * variants to Persian, lowercases the Latin part.
   */
  fun normalize(name: String): String {
    var pendingSpace = false
    return buildString(name.length) {
      for (raw in name) {
        val folded = arabicToPersian[raw] ?: raw
        when {
          folded.code in zeroWidthCodes -> Unit
          folded.isWhitespace() -> pendingSpace = length > 0
          else -> {
            if (pendingSpace && length > 0) append(' ')
            pendingSpace = false
            append(folded.lowercaseChar())
          }
        }
      }
    }
  }

  /** Display form of a raw input: first trimmed original, no other changes. */
  fun displayForm(name: String): String = name.trim()
}
