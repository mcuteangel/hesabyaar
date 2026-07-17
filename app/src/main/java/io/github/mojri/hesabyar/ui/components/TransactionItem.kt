package io.github.mojri.hesabyar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun TransactionItem(
  title: String,
  amount: Long,
  isIncome: Boolean,
  categoryColor: Color = Color.Gray,
  categoryInitial: String = "",
  date: String? = null,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null
) {
  val content: @Composable (Modifier) -> Unit = { innerModifier ->
    transactionItemContent(
      title = title,
      amount = amount,
      isIncome = isIncome,
      categoryColor = categoryColor,
      categoryInitial = categoryInitial,
      date = date,
      modifier = innerModifier,
      onClick = onClick
    )
  }

  if (onDelete != null) {
    val dismissState =
      rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
      )
    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
      onDelete()
    }
    SwipeToDismissBox(
      state = dismissState,
      modifier = modifier,
      enableDismissFromStartToEnd = false,
      enableDismissFromEndToStart = true,
      backgroundContent = {
        val fraction =
          animateFloatAsState(
            targetValue =
              if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) 1f else 0f,
            label = "swipeFraction"
          )
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .clip(ShapeTokens.Large)
              .background(MaterialTheme.colorScheme.errorContainer),
          contentAlignment = Alignment.CenterEnd
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier =
              Modifier
                .padding(end = SpacingTokens.lg)
                .size(24.dp)
                .graphicsLayer { alpha = fraction.value }
          )
        }
      }
    ) {
      content(Modifier)
    }
  } else {
    content(modifier)
  }
}

@Composable
private fun transactionItemContent(
  title: String,
  amount: Long,
  isIncome: Boolean,
  categoryColor: Color,
  categoryInitial: String,
  date: String?,
  modifier: Modifier,
  onClick: (() -> Unit)?
) {
  val amountColor = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
  val prefix = if (isIncome) "+" else "-"

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .then(
          if (onClick != null) {
            Modifier
              .clickable(onClick = onClick)
              .semantics { role = Role.Button }
          } else {
            Modifier
          }
        ).padding(vertical = SpacingTokens.sm),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    Box(
      modifier =
        Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(categoryColor.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = categoryInitial,
        style = MaterialTheme.typography.titleSmall,
        color = categoryColor
      )
    }

    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      date?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Text(
      text = "$prefix${CurrencyFormatter.format(amount)}",
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      color = amountColor,
      textAlign = TextAlign.End
    )
  }
}
