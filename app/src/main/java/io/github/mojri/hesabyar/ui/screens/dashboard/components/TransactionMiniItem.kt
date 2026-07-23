package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.dashboard.utils.CATEGORY_ICONS_MAP
import io.github.mojri.hesabyar.ui.screens.dashboard.utils.formatPersianDate

@Suppress("LongMethod")
@Composable
internal fun TransactionMiniItem(
  transaction: Transaction,
  categories: List<Category> = emptyList(),
  onClick: () -> Unit = {},
  onDelete: () -> Unit = {}
) {
  val isIncome = transaction.type == TransactionType.INCOME
  val category = categories.find { it.id == transaction.categoryId }
  val categoryColor =
    category?.let { Color(it.color) }
      ?: if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
  val icon = CATEGORY_ICONS_MAP[category?.icon] ?: Icons.Filled.Paid

  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    shape = ShapeTokens.Medium,
    cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    contentPadding = PaddingValues(SpacingTokens.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        IconCircle(
          icon = icon,
          tint = categoryColor,
          backgroundColor = categoryColor,
          iconSize = 18.dp
        )
        Spacer(modifier = Modifier.width(SpacingTokens.md))
        Column {
          Text(
            text = transaction.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "${formatPersianDate(transaction.date)} | ${category?.name ?: "سایر"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = (if (isIncome) "+" else "-") + CurrencyFormatter.format(transaction.amount),
          style = MaterialTheme.typography.bodyMedium,
          color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "حذف تراکنش",
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
