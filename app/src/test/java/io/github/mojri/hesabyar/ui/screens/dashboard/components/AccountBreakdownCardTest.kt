package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.mojri.hesabyar.ui.CategoryBreakdown
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AccountBreakdownCardTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun cardRendersWithBreakdown() {
    val tag = "account_breakdown_card"
    val breakdown =
      listOf(
        CategoryBreakdown(
          categoryId = 1L,
          categoryName = "خوراک",
          color = 0xFFE53935,
          total = 3_000_000L,
          percentage = 0.75f,
        ),
        CategoryBreakdown(
          categoryId = 2L,
          categoryName = "حمل‌ونقل",
          color = 0xFF1E88E5,
          total = 1_000_000L,
          percentage = 0.25f,
        ),
      )

    composeRule.setContent {
      AccountBreakdownCard(
        accountBreakdown = breakdown,
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule
      .onNodeWithTag(tag)
      .assertIsDisplayed()
  }

  @Test
  fun cardRendersEmptyBreakdownGracefully() {
    val tag = "empty_account_breakdown_card"
    composeRule.setContent {
      AccountBreakdownCard(
        accountBreakdown = emptyList(),
        modifier = Modifier.testTag(tag),
      )
    }

    composeRule.onNodeWithTag(tag).assertIsDisplayed()
  }
}
