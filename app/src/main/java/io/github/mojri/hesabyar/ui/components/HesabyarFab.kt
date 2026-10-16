package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens

/**
 * Standard Hesabyar Floating Action Button component.
 *
 * It uses the canonical design system tokens:
 * [Dimens.FABSize] (56dp) container and [Dimens.FABIconSize] (24dp) icon.
 *
 * The default colors come from the Material 3 theme.
 * They are [MaterialTheme.colorScheme.primary] and [MaterialTheme.colorScheme.onPrimary].
 */
@Composable
fun HesabyarFab(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector = Icons.Filled.Add,
  contentDescription: String? = null,
  containerColor: Color = MaterialTheme.colorScheme.primary,
  contentColor: Color = MaterialTheme.colorScheme.onPrimary,
  shape: Shape = ShapeTokens.Large,
) {
  FloatingActionButton(
    onClick = onClick,
    modifier = modifier.size(Dimens.FABSize),
    containerColor = containerColor,
    contentColor = contentColor,
    shape = shape,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(Dimens.FABIconSize),
    )
  }
}
