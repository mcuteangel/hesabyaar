package io.github.mojri.hesabyar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.AccountDashboardSummary
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor

private const val SELECTED_BACKGROUND_ALPHA = 0.08f
private const val SELECTED_BORDER_WIDTH = 2f
private const val CHECK_ICON_SIZE = 18

@Composable
fun AccountBalanceCard(
  summary: AccountDashboardSummary,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val accentColor = summary.accountColor.toComposeColor()
  val animatedBorderColor by animateColorAsState(
    targetValue = if (isSelected) accentColor else Color.Transparent,
    label = "border_color"
  )
  val animatedBorderWidth by animateDpAsState(
    targetValue = if (isSelected) SELECTED_BORDER_WIDTH.dp else 0.dp,
    label = "border_width"
  )

  val cardLabel =
    if (isSelected) {
      "حساب ${summary.accountName}، انتخاب‌شده"
    } else {
      "حساب ${summary.accountName}"
    }

  Box(
    modifier =
      modifier
        .widthIn(min = 140.dp)
        .fillMaxWidth()
        .background(
          color = animatedCardBackground(accentColor, isSelected),
          shape = ShapeTokens.Medium,
        ).border(width = animatedBorderWidth, color = animatedBorderColor, shape = ShapeTokens.Medium)
        .semantics {
          selected = isSelected
          contentDescription = cardLabel
        }.then(
          if (onClick != null) {
            Modifier.selectable(
              selected = isSelected,
              onClick = onClick,
              role = Role.Checkbox,
            )
          } else {
            Modifier
          }
        ),
  ) {
    CardCardContent(summary, accentColor)
    if (isSelected) SelectionBadge(accentColor)
  }
}

@Composable
private fun CardCardContent(
  summary: AccountDashboardSummary,
  accentColor: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(3.dp)
          .background(accentColor),
    )
    Row(
      modifier = Modifier.padding(SpacingTokens.md),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AccountNameAndType(summary)
      Text(
        text = CurrencyFormatter.format(summary.balance),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun RowScope.AccountNameAndType(summary: AccountDashboardSummary) {
  Column(
    modifier = Modifier.weight(1f),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
  ) {
    Text(
      text = summary.accountName,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = summary.accountType.displayName,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun BoxScope.SelectionBadge(accentColor: Color) {
  Icon(
    imageVector = Icons.Filled.Check,
    contentDescription = "حساب انتخاب‌شده",
    modifier =
      Modifier
        .align(Alignment.TopEnd)
        .padding(SpacingTokens.xs)
        .size(CHECK_ICON_SIZE.dp)
        .background(color = accentColor, shape = ShapeTokens.Small),
    tint = Color.White,
  )
}

@Composable
private fun animatedCardBackground(
  accentColor: Color,
  isSelected: Boolean,
): Color {
  val targetColor =
    if (isSelected) {
      accentColor.copy(alpha = SELECTED_BACKGROUND_ALPHA)
    } else {
      MaterialTheme.colorScheme.surfaceContainerLow
    }
  return animateColorAsState(targetValue = targetColor, label = "card_bg").value
}
