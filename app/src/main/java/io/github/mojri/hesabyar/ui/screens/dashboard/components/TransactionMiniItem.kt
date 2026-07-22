package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.dashboard.utils.CATEGORY_ICONS_MAP
import io.github.mojri.hesabyar.ui.screens.dashboard.utils.formatPersianDate

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
    category?.let { Color(it.color) } ?: if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
  val icon = CATEGORY_ICONS_MAP[category?.icon] ?: Icons.Filled.Paid

  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    shape = ShapeTokens.Medium,
    cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
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
            fontWeight = FontWeight.Medium,
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
          fontWeight = FontWeight.Bold,
          color = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "حذف تراکنش",
            tint = FinancialColors.ExpenseRed.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
