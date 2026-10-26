package io.github.mojri.hesabyar.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests for [ConfirmDialog].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ConfirmDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun confirmDialogRendersTitleAndMessage() {
    composeRule.setContent {
      ConfirmDialog(
        title = DIALOG_TITLE,
        message = DIALOG_MESSAGE,
        onConfirm = {},
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText(DIALOG_TITLE).assertIsDisplayed()
    composeRule.onNodeWithText(DIALOG_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun confirmDialogTriggersOnConfirmAndOnDismiss() {
    var confirmed = false
    var dismissed = false

    composeRule.setContent {
      ConfirmDialog(
        title = DIALOG_TITLE,
        message = DIALOG_MESSAGE,
        confirmText = CUSTOM_CONFIRM,
        dismissText = CUSTOM_DISMISS,
        onConfirm = { confirmed = true },
        onDismiss = { dismissed = true }
      )
    }

    composeRule.onNodeWithText(CUSTOM_CONFIRM).assertIsDisplayed().performClick()
    assertTrue(confirmed)

    composeRule.onNodeWithText(CUSTOM_DISMISS).assertIsDisplayed().performClick()
    assertTrue(dismissed)
  }

  @Test
  fun confirmDialogOmitsDismissButtonWhenDismissTextIsBlank() {
    composeRule.setContent {
      ConfirmDialog(
        title = DIALOG_TITLE,
        message = DIALOG_MESSAGE,
        dismissText = "",
        onConfirm = {},
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText("").assertDoesNotExist()
  }

  @Test
  fun confirmDialogRendersDestructiveConfirmAction() {
    var confirmed = false
    composeRule.setContent {
      ConfirmDialog(
        title = DIALOG_TITLE,
        message = DIALOG_MESSAGE,
        confirmText = CUSTOM_CONFIRM,
        confirmColor = Color.Red,
        confirmContentColor = Color.White,
        onConfirm = { confirmed = true },
        onDismiss = {}
      )
    }

    composeRule.onNodeWithText(CUSTOM_CONFIRM).assertIsDisplayed().performClick()
    assertTrue(confirmed)
  }

  private companion object {
    const val DIALOG_TITLE = "تأیید عملیات"
    const val DIALOG_MESSAGE = "آیا از انجام این عملیات اطمینان دارید؟"
    const val CUSTOM_CONFIRM = "حذف مورد"
    const val CUSTOM_DISMISS = "انصراف"
  }
}
