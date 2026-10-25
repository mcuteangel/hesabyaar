package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests for [HesabyarDialog].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun dialogRendersTitleAndContent() {
    composeRule.setContent {
      HesabyarDialog(
        title = DIALOG_TITLE,
        onDismissRequest = {}
      ) {
        Text(DIALOG_CONTENT)
      }
    }

    composeRule.onNodeWithText(DIALOG_TITLE).assertIsDisplayed()
    composeRule.onNodeWithText(DIALOG_CONTENT).assertIsDisplayed()
  }

  @Test
  fun dialogHidesCloseButtonByDefault() {
    composeRule.setContent {
      HesabyarDialog(
        title = DIALOG_TITLE,
        onDismissRequest = {}
      ) {
        Text(DIALOG_CONTENT)
      }
    }

    composeRule.onNodeWithContentDescription(CLOSE_DESCRIPTION).assertDoesNotExist()
  }

  @Test
  fun dialogShowsCloseButtonWhenRequestedAndHandlesClick() {
    var dismissed = false
    composeRule.setContent {
      HesabyarDialog(
        title = DIALOG_TITLE,
        onDismissRequest = { dismissed = true },
        showCloseButton = true
      ) {
        Text(DIALOG_CONTENT)
      }
    }

    composeRule.onNodeWithContentDescription(CLOSE_DESCRIPTION).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(CLOSE_DESCRIPTION).performClick()
    assertTrue(dismissed)
  }

  @Test
  fun dialogRendersStructuredConfirmAndDismissButtons() {
    var confirmed = false
    var dismissed = false

    composeRule.setContent {
      HesabyarDialog(
        title = DIALOG_TITLE,
        onDismissRequest = {},
        confirmButton = {
          TextButton(onClick = { confirmed = true }) {
            Text(CONFIRM_TEXT)
          }
        },
        dismissButton = {
          TextButton(onClick = { dismissed = true }) {
            Text(DISMISS_TEXT)
          }
        }
      ) {
        Text(DIALOG_CONTENT)
      }
    }

    composeRule.onNodeWithText(CONFIRM_TEXT).assertIsDisplayed().performClick()
    assertTrue(confirmed)

    composeRule.onNodeWithText(DISMISS_TEXT).assertIsDisplayed().performClick()
    assertTrue(dismissed)
  }

  @Test
  fun dialogRendersLegacyActionsWhenStructuredSlotsAbsent() {
    var actionClicked = false

    composeRule.setContent {
      HesabyarDialog(
        title = DIALOG_TITLE,
        onDismissRequest = {},
        actions = {
          TextButton(onClick = { actionClicked = true }) {
            Text(ACTION_TEXT)
          }
        }
      ) {
        Text(DIALOG_CONTENT)
      }
    }

    composeRule.onNodeWithText(ACTION_TEXT).assertIsDisplayed().performClick()
    assertTrue(actionClicked)
  }

  private companion object {
    const val DIALOG_TITLE = "عنوان دیالوگ"
    const val DIALOG_CONTENT = "متن محتوای دیالوگ"
    const val CONFIRM_TEXT = "تأیید"
    const val DISMISS_TEXT = "انصراف"
    const val ACTION_TEXT = "اقدام اختیاری"
    const val CLOSE_DESCRIPTION = "بستن"
  }
}
