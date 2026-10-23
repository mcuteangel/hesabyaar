package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests for [SectionHeader].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SectionHeaderTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun sectionHeaderRendersTitle() {
    composeRule.setContent {
      SectionHeader(title = HEADER_TITLE)
    }

    composeRule.onNodeWithText(HEADER_TITLE).assertIsDisplayed()
  }

  @Test
  fun sectionHeaderExposesHeadingSemantics() {
    composeRule.setContent {
      SectionHeader(title = HEADER_TITLE)
    }

    composeRule
      .onNodeWithText(HEADER_TITLE)
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
  }

  @Test
  fun sectionHeaderRendersActionAndEmitsClick() {
    var clicked = false

    composeRule.setContent {
      SectionHeader(
        title = HEADER_TITLE,
        action = {
          TextButton(onClick = { clicked = true }) {
            Text(ACTION_LABEL)
          }
        },
      )
    }

    composeRule.onNodeWithText(ACTION_LABEL).assertIsDisplayed().performClick()

    assertEquals("Action button emits click", true, clicked)
  }

  private companion object {
    const val HEADER_TITLE = "عنوان بخش"
    const val ACTION_LABEL = "مشاهده همه"
  }
}
