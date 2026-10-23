package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.WindowSizeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests for [HesabyarScaffold].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarScaffoldTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun scaffoldRendersTitleAndTriggersBack() {
    var backClicked = false
    val title = "تست عنوان"

    composeRule.setContent {
      HesabyarScaffold(
        title = title,
        onBack = { backClicked = true },
      ) {
        Text("بدنه تست")
      }
    }

    composeRule.onNodeWithText(title).assertIsDisplayed()
    composeRule
      .onNodeWithContentDescription("بازگشت")
      .assertIsDisplayed()
      .assertHasClickAction()
      .performClick()

    assertTrue("onBack callback should be invoked", backClicked)
  }

  @Test
  fun scaffoldRendersCustomTopBar() {
    val customTitle = "نوار بالا سفارشی"

    composeRule.setContent {
      HesabyarScaffold(
        topBar = { Text(customTitle) },
      ) {
        Text("محتوا")
      }
    }

    composeRule.onNodeWithText(customTitle).assertIsDisplayed()
  }

  @Test
  fun scaffoldRendersFab() {
    var fabClicked = false
    val fabDescription = "افزودن آیتم"

    composeRule.setContent {
      HesabyarScaffold(
        floatingActionButton = {
          HesabyarFab(
            onClick = { fabClicked = true },
            contentDescription = fabDescription,
            modifier = Modifier.testTag(TAG_FAB),
          )
        },
      ) {
        Text("محتوا")
      }
    }

    composeRule
      .onNodeWithTag(TAG_FAB)
      .assertIsDisplayed()
      .assertHasClickAction()
      .performClick()

    assertTrue("FAB click callback should be invoked", fabClicked)
  }

  @Test
  fun scaffoldProvidesFabClearanceWhenFabPresent() {
    var recordedPadding: PaddingValues? = null

    composeRule.setContent {
      HesabyarScaffold(
        floatingActionButton = {
          HesabyarFab(onClick = {}, contentDescription = "افزودن")
        },
      ) { contentPadding ->
        recordedPadding = contentPadding
        Text("محتوا")
      }
    }

    assertEquals(
      "Bottom padding must equal Dimens.FabClearance when FAB is present",
      Dimens.FabClearance,
      recordedPadding?.calculateBottomPadding(),
    )
  }

  @Test
  fun scaffoldProvidesZeroBottomPaddingWhenFabAbsent() {
    var recordedPadding: PaddingValues? = null

    composeRule.setContent {
      HesabyarScaffold { contentPadding ->
        recordedPadding = contentPadding
        Text("محتوا")
      }
    }

    assertEquals(
      "Bottom padding must be 0.dp when FAB is absent",
      0.dp,
      recordedPadding?.calculateBottomPadding(),
    )
  }

  @Test
  fun scaffoldConstrainsContentWidthToDefaultMax() {
    composeRule.setContent {
      Box(modifier = Modifier.requiredWidth(EXPANDED_WIDTH)) {
        HesabyarScaffold(
          applyContentMaxWidth = true,
          contentMaxWidth = WindowSizeTokens.ContentMaxWidth,
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .testTag(TAG_CONTENT_CONTAINER),
          )
        }
      }
    }

    composeRule
      .onNodeWithTag(TAG_CONTENT_CONTAINER)
      .assertWidthIsEqualTo(WindowSizeTokens.ContentMaxWidth)
  }

  @Test
  fun scaffoldAllowsUnconstrainedWidthWhenDisabled() {
    composeRule.setContent {
      Box(modifier = Modifier.requiredWidth(EXPANDED_WIDTH)) {
        HesabyarScaffold(
          applyContentMaxWidth = false,
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .testTag(TAG_CONTENT_CONTAINER),
          )
        }
      }
    }

    composeRule
      .onNodeWithTag(TAG_CONTENT_CONTAINER)
      .assertWidthIsEqualTo(EXPANDED_WIDTH)
  }

  private companion object {
    const val TAG_FAB = "scaffold_fab"
    const val TAG_CONTENT_CONTAINER = "scaffold_content_container"
    val EXPANDED_WIDTH: Dp = 1200.dp
  }
}
