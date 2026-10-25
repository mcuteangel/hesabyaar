package io.github.mojri.hesabyar.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit and Robolectric tests for [MarkdownText] and [parseBoldMarkdown].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MarkdownTextTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun parseBoldMarkdownWithPlainTextReturnsOriginalText() {
    val annotated = parseBoldMarkdown("Simple text without markdown")
    assertEquals("Simple text without markdown", annotated.text)
    assertEquals(0, annotated.spanStyles.size)
  }

  @Test
  fun parseBoldMarkdownWithBoldSegmentsAppliesBoldSpan() {
    val annotated = parseBoldMarkdown("Hello **world**!")
    assertEquals("Hello world!", annotated.text)
    assertEquals(1, annotated.spanStyles.size)
    val span = annotated.spanStyles[0]
    assertEquals(6, span.start)
    assertEquals(11, span.end)
    assertEquals(FontWeight.Bold, span.item.fontWeight)
  }

  @Test
  fun parseBoldMarkdownWithMultipleBoldSegments() {
    val annotated = parseBoldMarkdown("**First** and **second**")
    assertEquals("First and second", annotated.text)
    assertEquals(2, annotated.spanStyles.size)
    assertEquals(FontWeight.Bold, annotated.spanStyles[0].item.fontWeight)
    assertEquals(FontWeight.Bold, annotated.spanStyles[1].item.fontWeight)
  }

  @Test
  fun markdownTextRendersHeadersWithHeadingSemantics() {
    val markdown =
      """
      # Headline
      ## Section Title
      ### Subsection
      """.trimIndent()

    composeRule.setContent {
      MarkdownText(text = markdown)
    }

    composeRule
      .onNodeWithText("Headline")
      .assertIsDisplayed()
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

    composeRule
      .onNodeWithText("Section Title")
      .assertIsDisplayed()
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

    composeRule
      .onNodeWithText("Subsection")
      .assertIsDisplayed()
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
  }

  @Test
  fun markdownTextRendersBulletListItems() {
    val markdown =
      """
      - First bullet item
      * Second bullet item
      """.trimIndent()

    composeRule.setContent {
      MarkdownText(text = markdown)
    }

    composeRule.onNodeWithText("First bullet item").assertIsDisplayed()
    composeRule.onNodeWithText("Second bullet item").assertIsDisplayed()
  }

  @Test
  fun markdownTextRendersNumberedListItems() {
    val markdown =
      """
      1. First numbered step
      2. Second numbered step
      """.trimIndent()

    composeRule.setContent {
      MarkdownText(text = markdown)
    }

    composeRule.onNodeWithText("1.").assertIsDisplayed()
    composeRule.onNodeWithText("First numbered step").assertIsDisplayed()
    composeRule.onNodeWithText("2.").assertIsDisplayed()
    composeRule.onNodeWithText("Second numbered step").assertIsDisplayed()
  }

  @Test
  fun markdownTextRendersRegularParagraphsAndEmptyLines() {
    val markdown =
      """
      First paragraph.

      Second paragraph after empty line.
      """.trimIndent()

    composeRule.setContent {
      MarkdownText(text = markdown)
    }

    composeRule.onNodeWithText("First paragraph.").assertIsDisplayed()
    composeRule.onNodeWithText("Second paragraph after empty line.").assertIsDisplayed()
  }

  @Test
  fun markdownTextDoesNotTreatBoldStartAsBulletList() {
    val markdown = "**نکته مهم:** متن توضیحی"

    composeRule.setContent {
      MarkdownText(text = markdown)
    }

    composeRule.onNodeWithText("نکته مهم: متن توضیحی").assertIsDisplayed()
    composeRule.onNodeWithText("•").assertDoesNotExist()
  }

  @Test
  fun markdownTextRequiresWhitespaceAfterHeaderHashes() {
    val markdown = "#hashtag and ###notAHeader"

    composeRule.setContent {
      MarkdownText(text = markdown)
    }

    composeRule.onNodeWithText("#hashtag and ###notAHeader").assertIsDisplayed()
  }

  @Test
  fun markdownTextFourHashesRendersAsRegularText() {
    val markdown = "#### Level four header"

    composeRule.setContent {
      MarkdownText(text = markdown)
    }

    composeRule.onNodeWithText("#### Level four header").assertIsDisplayed()
    composeRule.onNodeWithText("# Level four header").assertDoesNotExist()
  }
}
