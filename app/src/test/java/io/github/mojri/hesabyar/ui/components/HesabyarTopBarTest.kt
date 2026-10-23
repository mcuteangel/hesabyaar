package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests for [HesabyarTopBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarTopBarTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun topBarRendersTitle() {
    composeRule.setContent {
      HesabyarTopBar(title = TOP_BAR_TITLE)
    }

    composeRule.onNodeWithText(TOP_BAR_TITLE).assertIsDisplayed()
  }

  @Test
  fun topBarTitleExposesHeadingSemantics() {
    composeRule.setContent {
      HesabyarTopBar(title = TOP_BAR_TITLE)
    }

    composeRule
      .onNodeWithText(TOP_BAR_TITLE)
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
  }

  @Test
  fun topBarWithoutOnBackHidesNavigationIcon() {
    composeRule.setContent {
      HesabyarTopBar(title = TOP_BAR_TITLE)
    }

    composeRule.onNodeWithContentDescription(BACK_DESCRIPTION).assertDoesNotExist()
  }

  @Test
  fun topBarBackButtonEmitsClick() {
    var clicked = false

    composeRule.setContent {
      HesabyarTopBar(
        title = TOP_BAR_TITLE,
        onBack = { clicked = true },
      )
    }

    composeRule
      .onNodeWithContentDescription(BACK_DESCRIPTION)
      .assertIsDisplayed()
      .performClick()

    assertTrue("Back click handler should be invoked", clicked)
  }

  @Test
  fun topBarRendersActionsSlot() {
    composeRule.setContent {
      HesabyarTopBar(
        title = TOP_BAR_TITLE,
        actions = {
          Text(ACTION_LABEL)
        },
      )
    }

    composeRule.onNodeWithText(ACTION_LABEL).assertIsDisplayed()
  }

  @Test
  fun topBarActionEmitsClick() {
    var clickedLabel: String? = null

    composeRule.setContent {
      HesabyarTopBar(
        title = TOP_BAR_TITLE,
        actions = {
          TextButton(onClick = { clickedLabel = ACTION_LABEL }) {
            Text(ACTION_LABEL)
          }
        },
      )
    }

    composeRule.onNodeWithText(ACTION_LABEL).performClick()

    assertEquals("Action click should emit its label", ACTION_LABEL, clickedLabel)
  }

  private companion object {
    const val TOP_BAR_TITLE = "عنوان صفحه"
    const val ACTION_LABEL = "عملیات"
    const val BACK_DESCRIPTION = "بازگشت"
  }
}
