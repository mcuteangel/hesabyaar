package io.github.mojri.hesabyar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the UI package against literal emoji characters.
 *
 * Screen readers pronounce raw unicode emoji in English or broken translations.
 * That breaks Persian TalkBack navigation. Use Compose [androidx.compose.material3.Icon]
 * elements with `contentDescription = null` for decorative accents instead.
 */
class UiEmojiIntegrityTest {
  @Test
  fun uiPackageContainsNoLiteralEmoji() {
    val uiRoot =
      listOf(
        File("src/main/java/io/github/mojri/hesabyar/ui"),
        File("app/src/main/java/io/github/mojri/hesabyar/ui"),
      ).firstOrNull { it.isDirectory }
        ?: error("UI source directory not found relative to ${File(".").absolutePath}")

    val sources = uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    // A silent zero-file scan would make this guard vacuous. Fail loudly instead.
    assertTrue("No Kotlin sources found under ${uiRoot.absolutePath}", sources.isNotEmpty())

    val violations =
      sources.flatMap { file ->
        file
          .readLines()
          .withIndex()
          .flatMap { (index, line) ->
            EMOJI_REGEX.findAll(line).map { match ->
              "${file.relativeTo(uiRoot).path}:${index + 1} U+${match.value.codePointAt(0).toString(16).uppercase()}"
            }
          }
      }

    assertEquals(
      "Literal emoji characters are not allowed in the ui package. Found: $violations",
      0,
      violations.size,
    )
  }

  @Test
  fun emojiRegexDetectsKnownEmoji() {
    // Guards the guard: a regex that matches nothing would let every emoji through.
    // Covers the supplementary emoji planes and the BMP emoji blocks
    // (Misc Symbols U+2600-26FF and Dingbats U+2700-27BF).
    assertEquals(1, EMOJI_REGEX.findAll("📝 یادداشت").count())
    assertEquals(1, EMOJI_REGEX.findAll("⚠️ علامت خطر").count())
    assertEquals(1, EMOJI_REGEX.findAll("⚙️ تنظیمات").count())
    assertEquals(1, EMOJI_REGEX.findAll("✅ تایید شده").count())
    assertEquals(1, EMOJI_REGEX.findAll("✏️ ویرایش").count())
    assertEquals(0, EMOJI_REGEX.findAll("یادداشت ساده").count())
    // Persian ZWNJ, em dash, and the U+2192 arrow stay allowed.
    assertEquals(0, EMOJI_REGEX.findAll("به‌سوی → مقصد — پایان").count())
  }

  private companion object {
    // Supplemental Symbols and Pictographs, Emoticons, Transport & Map,
    // Alchemical Symbols, plus Chess/pictographic extensions.
    const val SUPPLEMENTARY_EMOJI = "[\\x{1F300}-\\x{1FAFF}]"

    // Misc Symbols (U+2600-26FF: ⚠️ ⚙️ ✅ origin), Dingbats (U+2700-27BF: ✏️ ✅),
    // and Misc Symbols and Arrows (U+2B00-2BFF: ⭐ ⭕). Persian text uses
    // ZWNJ (U+200C), em dash (U+2014), and the arrow U+2192 — excluded on purpose.
    const val BMP_EMOJI = "[\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}]"
    val EMOJI_REGEX = Regex("$SUPPLEMENTARY_EMOJI|$BMP_EMOJI")
  }
}
