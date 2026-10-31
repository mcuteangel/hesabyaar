package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.icon
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor
import io.github.mojri.hesabyar.ui.utils.formatPersianDate

@Composable
internal fun TransactionDetailDialog(
  transaction: Transaction,
  categories: List<Category>,
  accounts: List<AccountEntity> = emptyList(),
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit
) {
  val category = categories.find { it.id == transaction.categoryId }
  val sourceAccount = accounts.find { it.id == transaction.accountId }
  val destAccount =
    if (transaction.type == TransactionType.TRANSFER) {
      accounts.find { it.id == transaction.destinationAccountId }
    } else {
      null
    }

  HesabyarDialog(
    onDismissRequest = onDismiss,
    title = stringResource(R.string.transaction_details_title),
    confirmButton = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
        verticalAlignment = Alignment.CenterVertically
      ) {
        HesabyarButton(
          onClick = onEdit,
          text = stringResource(R.string.action_edit),
          variant = ButtonVariant.Filled
        )
        HesabyarButton(
          onClick = onDelete,
          text = stringResource(R.string.action_delete),
          variant = ButtonVariant.Filled,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError
            )
        )
      }
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        text = stringResource(R.string.action_close),
        variant = ButtonVariant.Text
      )
    }
  ) {
    TransactionDetailContent(
      transaction = transaction,
      category = category,
      sourceAccount = sourceAccount,
      destAccount = destAccount
    )
  }
}

@Composable
private fun TransactionDetailContent(
  transaction: Transaction,
  category: Category?,
  sourceAccount: AccountEntity?,
  destAccount: AccountEntity?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    TransactionTypeRow(type = transaction.type)
    HorizontalDivider()

    if (sourceAccount != null) {
      TransactionAccountRow(
        sourceAccount = sourceAccount,
        destAccount = destAccount,
        isTransfer = transaction.type == TransactionType.TRANSFER
      )
      HorizontalDivider()
    }

    TransactionAmountRow(amount = transaction.amount, type = transaction.type)
    HorizontalDivider()

    TransactionDetailRow(
      label = "دسته‌بندی:",
      value = category?.name ?: "سایر"
    )
    HorizontalDivider()

    TransactionDetailRow(
      label = "تاریخ:",
      value = formatPersianDate(transaction.date)
    )
    HorizontalDivider()

    TransactionDetailRow(
      label = "توضیحات:",
      value = transaction.description
    )
  }
}

@Composable
private fun TransactionTypeRow(type: TransactionType) {
  val (labelRes, color) =
    when (type) {
      TransactionType.INCOME -> R.string.category_type_income to MaterialTheme.colorScheme.primary
      TransactionType.TRANSFER -> R.string.transaction_type_transfer to MaterialTheme.colorScheme.tertiary
      else -> R.string.category_type_expense to MaterialTheme.colorScheme.error
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "نوع:",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = stringResource(labelRes),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      color = color
    )
  }
}

@Composable
private fun TransactionAccountRow(
  sourceAccount: AccountEntity,
  destAccount: AccountEntity?,
  isTransfer: Boolean
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "حساب:",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
    ) {
      IconCircle(
        icon = sourceAccount.type.icon(),
        tint = sourceAccount.color.toComposeColor(),
        backgroundColor = sourceAccount.color.toComposeColor(),
        iconSize = 10.dp,
        containerSize = 16.dp
      )
      Text(
        text =
          if (isTransfer) {
            if (destAccount != null) {
              "${sourceAccount.name} → ${destAccount.name}"
            } else {
              "${sourceAccount.name} → ${stringResource(R.string.transaction_destination_unknown)}"
            }
          } else {
            sourceAccount.name
          },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun TransactionAmountRow(
  amount: Long,
  type: TransactionType
) {
  val color =
    when (type) {
      TransactionType.INCOME -> MaterialTheme.colorScheme.primary
      TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiary
      else -> MaterialTheme.colorScheme.error
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "مبلغ:",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = CurrencyFormatter.format(amount),
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Bold,
      color = color
    )
  }
}

@Composable
private fun TransactionDetailRow(
  label: String,
  value: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold
    )
  }
}
