package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  action: @Composable (() -> Unit)? = null
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          horizontal = SpacingTokens.lg,
          vertical = SpacingTokens.md
        ),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      modifier = if (action != null) Modifier.weight(1f) else Modifier,
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      icon?.let {
        Icon(
          imageVector = it,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
    action?.invoke()
  }
}
