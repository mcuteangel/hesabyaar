package io.github.mojri.hesabyar.ui.components

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [remapSelection] in [HesabyarInputField].
 */
class HesabyarInputFieldTest {
  @Test
  fun emptyNewTextReturnsZero() {
    val result = remapSelection(oldText = "some text", newText = "", selection = TextRange(5))
    assertEquals(TextRange.Zero, result)
  }

  @Test
  fun sameLengthNormalizationPreservesCaret() {
    // e.g. Persian digits "۱۲۳" converted to ASCII "123" by filterDigits
    val result = remapSelection(oldText = "۱۲۳", newText = "123", selection = TextRange(2))
    assertEquals(TextRange(2), result)
  }

  @Test
  fun caretInsideUnchangedCommonPrefixIsPreserved() {
    // Caret at offset 3 inside unchanged prefix "سلام "
    val result =
      remapSelection(
        oldText = "سلام علی",
        newText = "سلام حسن",
        selection = TextRange(3)
      )
    assertEquals(TextRange(3), result)
  }

  @Test
  fun caretAtCommonPrefixBoundaryIsPreserved() {
    // Caret exactly at the boundary between the unchanged prefix and the changed part
    val result =
      remapSelection(
        oldText = "foo_bar",
        newText = "foo_baz",
        selection = TextRange(4)
      )
    assertEquals(TextRange(4), result)
  }

  @Test
  fun unrelatedReplacementMovesCaretToEnd() {
    // Concrete issue: SmartAssistant example chip tapped while old input was "500"
    val result =
      remapSelection(
        oldText = "500",
        newText = "امروز حقوق گرفتم ۲۰ میلیون",
        selection = TextRange(3)
      )
    assertEquals(TextRange("امروز حقوق گرفتم ۲۰ میلیون".length), result)
  }

  @Test
  fun textAppendedAtEndKeepsCaretAtBoundary() {
    val result =
      remapSelection(
        oldText = "hello",
        newText = "hello world",
        selection = TextRange(5)
      )
    assertEquals(TextRange(5), result)
  }

  @Test
  fun textShortenedBeyondCaretClampsToEnd() {
    // Text shortened from 10 chars to 4 chars while caret was at 8
    val result =
      remapSelection(
        oldText = "0123456789",
        newText = "0123",
        selection = TextRange(8)
      )
    assertEquals(TextRange(4), result)
  }
}
