package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the delete confirmation in [ConfigItem]: tapping the delete button
 * asks first (warning that the API key is removed too), confirming deletes
 * exactly the tapped config, and dismissing deletes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ConfigItemDeleteConfirmTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val config =
    AiProviderConfig(
      id = "cfg-1",
      providerType = AiProviderType.GEMINI,
      apiKey = "secret",
      model = "gemini-2.0-flash",
      label = "تست"
    )

  private fun setContent(onDelete: (AiProviderConfig) -> Unit = {}) {
    composeRule.setContent {
      ConfigItem(
        config = config,
        isActive = false,
        onSelect = {},
        onEdit = {},
        onDelete = onDelete
      )
    }
  }

  @Test
  fun deleteButtonOpensConfirmationDialog() {
    setContent()

    composeRule.onNodeWithContentDescription("حذف").performClick()

    composeRule.onNodeWithText("حذف پیکربندی").assertIsDisplayed()
    composeRule.onNodeWithText("کلید API", substring = true).assertIsDisplayed()
  }

  @Test
  fun confirmingDeletesTheTappedConfig() {
    var deleted: AiProviderConfig? = null
    setContent(onDelete = { deleted = it })

    composeRule.onNodeWithContentDescription("حذف").performClick()
    composeRule.onNodeWithText("حذف").performClick()

    assertEquals(config, deleted)
    composeRule.onNodeWithText("حذف پیکربندی").assertDoesNotExist()
  }

  @Test
  fun dismissingDeletesNothing() {
    var deleted: AiProviderConfig? = null
    setContent(onDelete = { deleted = it })

    composeRule.onNodeWithContentDescription("حذف").performClick()
    composeRule.onNodeWithText("انصراف").performClick()

    assertNull(deleted)
    composeRule.onNodeWithText("حذف پیکربندی").assertDoesNotExist()
  }
}
