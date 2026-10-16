package io.github.mojri.hesabyar.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarFabTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun fabRendersWithContentDescriptionAndTriggersClick() {
    var clicked = false
    val description = "افزودن حساب"

    composeRule.setContent {
      HesabyarFab(
        onClick = { clicked = true },
        contentDescription = description,
        icon = Icons.Filled.Add,
      )
    }

    composeRule
      .onNodeWithContentDescription(description)
      .assertIsDisplayed()
      .performClick()

    assertTrue("Click handler should be invoked", clicked)
  }

  @Test
  fun fabSupportsCustomModifierAndTestTag() {
    val testTag = "add_transaction_fab"

    composeRule.setContent {
      HesabyarFab(
        onClick = {},
        modifier = Modifier.testTag(testTag),
        icon = Icons.Filled.Edit,
        contentDescription = "ویرایش"
      )
    }

    composeRule
      .onNodeWithTag(testTag)
      .assertIsDisplayed()
      .assertHasClickAction()
  }

  @Test
  fun fabRendersWithDefaultParametersAndTriggersClick() {
    var clicked = false
    val description = "تراکنش جدید"

    composeRule.setContent {
      HesabyarFab(
        onClick = { clicked = true },
        contentDescription = description,
      )
    }

    composeRule
      .onNodeWithContentDescription(description)
      .assertIsDisplayed()
      .assertHasClickAction()
      .performClick()

    assertTrue("Click handler should be invoked", clicked)
  }
}
