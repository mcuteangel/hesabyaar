package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens

@Composable
fun HesabyarChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  leadingIcon: ImageVector? = null,
  selectedLeadingIcon: ImageVector? = leadingIcon,
  enabled: Boolean = true,
  shape: Shape = ShapeTokens.Medium
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    label = { Text(label) },
    leadingIcon =
      if (leadingIcon != null) {
        {
          Icon(
            imageVector = if (selected) selectedLeadingIcon else leadingIcon,
            contentDescription = null,
            modifier = Modifier.size(FilterChipDefaults.IconSize)
          )
        }
      } else {
        null
      },
    enabled = enabled,
    shape = shape,
    border =
      FilterChipDefaults.filterChipBorder(
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = MaterialTheme.colorScheme.primary,
        borderWidth = FilterChipDefaults.MediumBorderWidth,
        selectedBorderWidth = FilterChipDefaults.MediumBorderWidth
      ),
    colors =
      FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
      )
  )
}
