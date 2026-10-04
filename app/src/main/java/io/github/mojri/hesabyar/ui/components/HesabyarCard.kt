package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun HesabyarCard(
  modifier: Modifier = Modifier,
  shape: Shape = ShapeTokens.Medium,
  elevation: Dp = ElevationTokens.Level0,
  cardColors: CardColors =
    CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
  contentPadding: PaddingValues = PaddingValues(SpacingTokens.lg),
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  if (onClick != null) {
    // M3 Card(onClick) exposes the click action but no accessibility role
    // (verified by HesabyarCardTest.clickableCardPublishesButtonRoleByDefault).
    // Add Role.Button so screen readers announce the card as a button.
    val clickableModifier =
      modifier.semantics(mergeDescendants = true) { role = Role.Button }
    Card(
      onClick = onClick,
      modifier = clickableModifier,
      shape = shape,
      elevation = CardDefaults.cardElevation(defaultElevation = elevation),
      colors = cardColors
    ) {
      Column(
        modifier = Modifier.padding(contentPadding),
        content = content
      )
    }
  } else {
    Card(
      modifier = modifier,
      shape = shape,
      elevation = CardDefaults.cardElevation(defaultElevation = elevation),
      colors = cardColors
    ) {
      Column(
        modifier = Modifier.padding(contentPadding),
        content = content
      )
    }
  }
}
