package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.mojri.hesabyar.ui.designsystem.WindowSizeTokens

/**
 * Standard adaptive content container that constrains content width to [contentMaxWidth]
 * (default [WindowSizeTokens.ContentMaxWidth]) on wide displays and centers it.
 */
@Composable
fun AdaptiveContentContainer(
  modifier: Modifier = Modifier,
  contentMaxWidth: Dp = WindowSizeTokens.ContentMaxWidth,
  applyContentMaxWidth: Boolean = true,
  contentAlignment: Alignment = Alignment.TopCenter,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = contentAlignment,
  ) {
    Box(
      modifier =
        if (applyContentMaxWidth) {
          Modifier.fillMaxHeight().widthIn(max = contentMaxWidth).fillMaxWidth()
        } else {
          Modifier.fillMaxSize()
        },
      contentAlignment = contentAlignment,
    ) {
      content()
    }
  }
}
