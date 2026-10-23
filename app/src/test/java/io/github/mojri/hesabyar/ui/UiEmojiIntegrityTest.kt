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
    assertEquals(1, EMOJI_REGEX.findAll("📝 یادداشت").count())
    assertEquals(0, EMOJI_REGEX.findAll("یادداشت ساده").count())
  }

  private companion object {
    val EMOJI_REGEX = Regex("[\\x{1F300}-\\x{1FAFF}]")
  }
}
