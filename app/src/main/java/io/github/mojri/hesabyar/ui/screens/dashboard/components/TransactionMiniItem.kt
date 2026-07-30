package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor
import io.github.mojri.hesabyar.ui.utils.formatPersianDate
import io.github.mojri.hesabyar.ui.utils.resolveCategoryIcon

@Suppress("LongMethod")
@Composable
internal fun TransactionMiniItem(
  transaction: Transaction,
  categories: List<Category> = emptyList(),
  accounts: List<AccountEntity> = emptyList(),
  onClick: () -> Unit = {},
  onDelete: () -> Unit = {}
) {
  val isIncome = transaction.type == TransactionType.INCOME
  val isTransfer = transaction.type == TransactionType.TRANSFER
  val category = categories.find { it.id == transaction.categoryId }
  val categoryColor =
    category?.let { Color(it.color) }
      ?: if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
  val icon = resolveCategoryIcon(category?.icon)

  val sourceAccount = accounts.find { it.id == transaction.accountId }
  val destAccount =
    if (isTransfer) accounts.find { it.id == transaction.destinationAccountId } else null
  val sourceColor = sourceAccount?.color?.toComposeColor() ?: MaterialTheme.colorScheme.primary
  val destColor = destAccount?.color?.toComposeColor()

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
        AccountColorIndicator(sourceColor = sourceColor, destColor = destColor)
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
            text = buildSubtitle(transaction, category, sourceAccount, destAccount, isTransfer),
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

/** Builds the subtitle line: date | category · account → destAccount. */
private fun buildSubtitle(
  transaction: Transaction,
  category: Category?,
  sourceAccount: AccountEntity?,
  destAccount: AccountEntity?,
  isTransfer: Boolean,
): String =
  buildString {
    append(formatPersianDate(transaction.date))
    append(" | ")
    append(category?.name ?: "سایر")
    sourceAccount?.let {
      append(" · ")
      append(it.name)
    }
    if (isTransfer && destAccount != null) {
      append(" → ")
      append(destAccount.name)
    }
  }

/**
 * Small colored dot indicating the source account.
 *
 * For transfers, two half-dots (source + destination) are shown side by side
 * to visually communicate the two-account nature of the transaction.
 *
 * This is the primary color signal; the account name text in the subtitle
 * serves as the secondary (accessibility) signal.
 */
@Composable
private fun AccountColorIndicator(
  sourceColor: Color,
  destColor: Color?,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.size(Dimens.IconSmall),
    contentAlignment = Alignment.Center,
  ) {
    if (destColor != null) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Box(
          modifier =
            Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(sourceColor),
        )
        Spacer(modifier = Modifier.height(1.dp))
        Box(
          modifier =
            Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(destColor),
        )
      }
    } else {
      Box(
        modifier =
          Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(sourceColor),
      )
    }
  }
}
