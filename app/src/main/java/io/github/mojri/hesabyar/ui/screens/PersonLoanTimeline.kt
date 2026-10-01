package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.PersonViewModel
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.utils.formatPersianDate

/**
 * Loan timeline list and per-loan cards for [PersonDetailSheet].
 *
 * Extracted so each file stays within the detekt function-count limit.
 */
@Composable
internal fun LoanTimelineList(
  loans: List<Loan>,
  personViewModel: PersonViewModel,
  modifier: Modifier = Modifier
) {
  if (loans.isEmpty()) {
    Box(
      modifier =
        modifier
          .fillMaxWidth()
          .padding(vertical = SpacingTokens.xl),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "هیچ وامی برای این شخص ثبت نشده است.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  } else {
    LazyColumn(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      items(loans, key = { it.id }) { loan ->
        TimelineLoanItem(loan = loan, personViewModel = personViewModel)
      }
    }
  }
}

@Composable
private fun TimelineLoanItem(
  loan: Loan,
  personViewModel: PersonViewModel
) {
  val (tint, icon, typeLabel) =
    when (loan.type) {
      LoanType.DEBTOR ->
        Triple(MaterialTheme.colorScheme.primary, Icons.Filled.ArrowCircleDown, "طلب")
      LoanType.CREDITOR ->
        Triple(MaterialTheme.colorScheme.secondary, Icons.Filled.ArrowCircleUp, "بدهی")
      LoanType.UNKNOWN ->
        Triple(
          MaterialTheme.colorScheme.onSurfaceVariant,
          Icons.Filled.AccountCircle,
          "نوع نامشخص"
        )
    }
  val paymentsFlow = remember(loan.id) { personViewModel.getPaymentHistoryForLoan(loan.id) }
  val payments by paymentsFlow.collectAsState(initial = emptyList())

  HesabyarCard(
    modifier = Modifier.fillMaxWidth(),
    shape = ShapeTokens.Medium,
    cardColors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
      ),
    contentPadding = PaddingValues(SpacingTokens.md)
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
    ) {
      LoanItemCardBody(loan = loan, tint = tint, icon = icon, typeLabel = typeLabel)

      if (payments.isNotEmpty()) {
        PaymentsSection(payments = payments)
      }
    }
  }
}

@Composable
private fun LoanItemCardBody(
  loan: Loan,
  tint: Color,
  icon: ImageVector,
  typeLabel: String
) {
  LoanItemHeaderRow(loan = loan, tint = tint, icon = icon, typeLabel = typeLabel)

  if (loan.description.isNotBlank()) {
    Text(
      text = loan.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
  LoanItemAmountRow(loan = loan)
}

@Composable
private fun LoanItemHeaderRow(
  loan: Loan,
  tint: Color,
  icon: ImageVector,
  typeLabel: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(Dimens.IconSmall)
      )
      Text(
        text = typeLabel,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = tint
      )
      if (loan.isSettled) {
        Text(
          text = "(تسویه شده)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    Text(
      text = formatPersianDate(loan.date),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun LoanItemAmountRow(loan: Loan) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = "مبلغ اولیه: ${CurrencyFormatter.format(loan.originalAmount)}",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!loan.isSettled && loan.remainingAmount != loan.originalAmount) {
      Text(
        text = "مانده: ${CurrencyFormatter.format(loan.remainingAmount)}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
      )
    }
  }
}

@Composable
private fun PaymentsSection(payments: List<PaymentHistory>) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(top = SpacingTokens.xs),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.xxs)
  ) {
    Text(
      text = "پرداخت‌ها:",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    payments.forEach { payment ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = formatPersianDate(payment.date),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = CurrencyFormatter.format(payment.amount),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}
