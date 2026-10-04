package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
      HesabyarCard(onClick = {}) {
        Text("Card Content")
      }
    }

    composeRule
      .onNodeWithText("Card Content")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
  }

  @Test
  fun nonClickableCardDoesNotPublishButtonRole() {
    composeRule.setContent {
      HesabyarCard {
        Text("Static Content")
      }
    }

    composeRule
      .onNodeWithText("Static Content")
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
  }
}
