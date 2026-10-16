package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Shared tab row for top-level screen sections and wizards.
 *
 * Wraps [TabRow] when [scrollable] is false and [SecondaryScrollableTabRow]
 * when [scrollable] is true. Height is fixed to [Dimens.TabHeight] (48.dp).
 * Indicators and colors follow the Material 3 theme defaults.
 */
@Composable
fun HesabyarTabRow(
  selectedTabIndex: Int,
  modifier: Modifier = Modifier,
  scrollable: Boolean = false,
  edgePadding: Dp = SpacingTokens.md,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = MaterialTheme.colorScheme.primary,
  showDivider: Boolean = true,
  tabs: @Composable () -> Unit
) {
  val rowModifier = modifier.height(Dimens.TabHeight)
  val dividerComposable: @Composable () -> Unit = {
    if (showDivider) {
      HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceContainer,
        thickness = Dimens.DividerThickness
      )
    }
  }

  if (scrollable) {
    SecondaryScrollableTabRow(
      selectedTabIndex = selectedTabIndex,
      modifier = rowModifier,
      containerColor = containerColor,
      contentColor = contentColor,
      edgePadding = edgePadding,
      divider = dividerComposable,
      tabs = tabs
    )
  } else {
    TabRow(
      selectedTabIndex = selectedTabIndex,
      modifier = rowModifier,
      containerColor = containerColor,
      contentColor = contentColor,
      divider = dividerComposable,
      tabs = tabs
    )
  }
}
