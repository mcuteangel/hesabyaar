package io.github.mojri.hesabyar.ui.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun TransactionDetailDialog(
  transaction: Transaction,
  categories: List<Category>,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit
) {
  val isIncome = transaction.type == TransactionType.INCOME
  val category = categories.find { it.id == transaction.categoryId }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "جزئیات تراکنش",
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Right
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "نوع:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
          )
          Text(
            text = if (isIncome) "درآمد" else "هزینه",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "مبلغ:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
          )
          Text(
            text = CurrencyFormatter.format(transaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "دسته‌بندی:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
          )
          Text(
            text = category?.name ?: "سایر",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "تاریخ:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
          )
          Text(
            text = formatPersianDate(transaction.date),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "توضیحات:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
          )
          Text(
            text = transaction.description,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }
    },
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        Button(
          onClick = onEdit,
          modifier = Modifier.weight(1f),
          shape = ShapeTokens.Small
        ) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSmall)
          )
          Spacer(modifier = Modifier.width(SpacingTokens.xs))
          Text("ویرایش")
        }
        Button(
          onClick = onDelete,
          modifier = Modifier.weight(1f),
          shape = ShapeTokens.Small,
          colors = ButtonDefaults.buttonColors(containerColor = FinancialColors.ExpenseRed)
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSmall)
          )
          Spacer(modifier = Modifier.width(SpacingTokens.xs))
          Text("حذف")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("بستن")
      }
    }
  )
}
