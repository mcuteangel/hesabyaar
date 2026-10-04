package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verification of [HesabyarCard] accessibility semantics and role behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarCardTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun clickableCardPublishesButtonRoleByDefault() {
    composeRule.setContent {
      HesabyarCard(onClick = {}, modifier = Modifier.testTag(TAG_CARD)) {
        Text("Card Content")
      }
    }

    composeRule
      .onNodeWithTag(TAG_CARD)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
  }

  @Test
  fun nonClickableCardDoesNotPublishButtonRole() {
    composeRule.setContent {
      HesabyarCard(modifier = Modifier.testTag(TAG_CARD)) {
        Text("Static Content")
      }
    }

    composeRule
      .onNodeWithTag(TAG_CARD)
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
  }

  /**
   * Ground truth for the premise in HesabyarCard's onClick branch: Material 3
   * Card(onClick) publishes the click action but leaves Role undefined.
   */
  @Test
  fun bareMaterial3CardExposesNoRole() {
    composeRule.setContent {
      Card(onClick = {}, modifier = Modifier.testTag(TAG_CARD)) {
        Text("Bare Card Content")
      }
    }

    composeRule
      .onNodeWithTag(TAG_CARD)
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
  }

  private companion object {
    const val TAG_CARD = "hesabyar-card"
  }
}
