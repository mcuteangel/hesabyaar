package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.domain.utils.PersonBalanceCalculator
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.PersonViewModel
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.utils.formatPersianDate

private const val CONTAINER_SIZE_DP = 40
private const val ICON_SIZE_DP = 24

/**
 * Bottom sheet displaying a person ledger summary, timeline, and quick actions.
 *
 * Implements plan 011 Phase 3 item 3. Shows combined timeline of loans with
 * payment histories and actions to create new loans or settle balances fully.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailSheet(
  personId: Long,
  personName: String,
  personViewModel: PersonViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val loansFlow = remember(personId) { personViewModel.getLoansForPerson(personId) }
  val loans by loansFlow.collectAsState(initial = emptyList())
  val balances by personViewModel.personBalances.collectAsState()
  val currentBalance = balances.firstOrNull { it.personId == personId }

  var addLoanType by remember { mutableStateOf<LoanType?>(null) }
  var addLoanError by remember { mutableStateOf<String?>(null) }
  var showSettleConfirm by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier
  ) {
    PersonSheetContent(
      personName = personName,
      balance = currentBalance,
      loans = loans,
      personViewModel = personViewModel,
      onAddReceivable = { addLoanType = LoanType.DEBTOR },
      onAddDebt = { addLoanType = LoanType.CREDITOR },
      onSettleFully = { showSettleConfirm = true }
    )
  }

  PersonSheetDialogs(
    dialogState =
      PersonDialogState(
        personId = personId,
        personName = personName,
        addLoanType = addLoanType,
        addLoanError = addLoanError,
        showSettleConfirm = showSettleConfirm
      ),
    personViewModel = personViewModel,
    onAddLoanSuccess = { addLoanType = null },
    onAddLoanFailure = { addLoanError = it },
    onDismissAddLoan = {
      addLoanType = null
      addLoanError = null
    },
    onConfirmSettle = {
      personViewModel.settleFully(personId)
      showSettleConfirm = false
    },
    onDismissSettle = { showSettleConfirm = false }
  )
}

private data class PersonDialogState(
  val personId: Long,
  val personName: String,
  val addLoanType: LoanType?,
  val addLoanError: String?,
  val showSettleConfirm: Boolean,
)

@Composable
private fun PersonSheetDialogs(
  dialogState: PersonDialogState,
  personViewModel: PersonViewModel,
  onAddLoanSuccess: () -> Unit,
  onAddLoanFailure: (String) -> Unit,
  onDismissAddLoan: () -> Unit,
  onConfirmSettle: () -> Unit,
  onDismissSettle: () -> Unit,
) {
  dialogState.addLoanType?.let { type ->
    AddPersonLoanDialog(
      type = type,
      personName = dialogState.personName,
      errorMessage = dialogState.addLoanError,
      onConfirm = { amountRial, description ->
        personViewModel.addLoanForPerson(
          personId = dialogState.personId,
          personName = dialogState.personName,
          type = type,
          amount = amountRial,
          description = description
        ) { success ->
          if (success) {
            onAddLoanSuccess()
          } else {
            onAddLoanFailure("ثبت ناموفق بود. دوباره تلاش کنید.")
          }
        }
      },
      onDismiss = onDismissAddLoan
    )
  }

  if (dialogState.showSettleConfirm) {
    ConfirmDialog(
      title = "تسویه کامل",
      message = "آیا از تسویه کامل تمام وام‌ها و طلب‌های ${dialogState.personName} اطمینان دارید؟",
      confirmText = "تسویه کن",
      onConfirm = onConfirmSettle,
      onDismiss = onDismissSettle
    )
  }
}

@Composable
private fun PersonSheetContent(
  personName: String,
  balance: PersonBalanceCalculator.PersonBalance?,
  loans: List<Loan>,
  personViewModel: PersonViewModel,
  onAddReceivable: () -> Unit,
  onAddDebt: () -> Unit,
  onSettleFully: () -> Unit
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = SpacingTokens.lg)
        .navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    PersonHeader(personName = personName, balance = balance)

    PersonQuickActions(
      onAddReceivable = onAddReceivable,
      onAddDebt = onAddDebt,
      onSettleFully = onSettleFully,
      canSettle = loans.any { !it.isSettled }
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Text(
      text = "تاریخچه تراکنش‌ها و وام‌ها",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface
    )

    LoanTimelineList(
      loans = loans,
      personViewModel = personViewModel,
      modifier = Modifier.weight(1f, fill = false)
    )

    Spacer(modifier = Modifier.height(SpacingTokens.sm))
  }
}

@Composable
private fun PersonHeader(
  personName: String,
  balance: PersonBalanceCalculator.PersonBalance?
) {
  val net = balance?.netBalance ?: 0L
  val (label, tint, icon) = balanceDirectionMeta(net)

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      IconCircle(
        icon = icon,
        tint = tint,
        backgroundColor = tint,
        containerSize = CONTAINER_SIZE_DP.dp,
        iconSize = ICON_SIZE_DP.dp,
        contentDescription = label
      )
      Column {
        Text(
          text = personName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          color = tint
        )
      }
    }
    Text(
      text = CurrencyFormatter.format(net),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = tint,
      textAlign = TextAlign.End
    )
  }
}

@Composable
private fun balanceDirectionMeta(net: Long): Triple<String, Color, ImageVector> =
  when {
    net > 0L -> Triple("بدهکار", MaterialTheme.colorScheme.primary, Icons.Filled.ArrowCircleDown)
    net < 0L -> Triple("طلبکار", MaterialTheme.colorScheme.secondary, Icons.Filled.ArrowCircleUp)
    else -> Triple("تسویه شده", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.AccountCircle)
  }

@Composable
private fun PersonQuickActions(
  onAddReceivable: () -> Unit,
  onAddDebt: () -> Unit,
  onSettleFully: () -> Unit,
  canSettle: Boolean
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    maxItemsInEachRow = 3
  ) {
    HesabyarButton(
      onClick = onAddReceivable,
      text = "ثبت طلب",
      icon = Icons.Filled.ArrowCircleDown,
      modifier = Modifier.weight(1f)
    )
    HesabyarButton(
      onClick = onAddDebt,
      text = "ثبت بدهی",
      icon = Icons.Filled.ArrowCircleUp,
      modifier = Modifier.weight(1f)
    )
    if (canSettle) {
      HesabyarButton(
        onClick = onSettleFully,
        text = "تسویه",
        icon = Icons.Filled.CheckCircle,
        variant = ButtonVariant.Outlined,
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun LoanTimelineList(
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
  // Remember the flow per loan instance: a replaced loan (same id after a
  // restore) is a new object, so it resubscribes instead of reusing a stale
  // subscription, and ordinary recomposition does not resubscribe.
  val paymentsFlow = remember(loan) { personViewModel.getPaymentHistoryForLoan(loan.id) }
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
      LoanItemHeader(loan = loan, tint = tint, icon = icon, typeLabel = typeLabel)

      LoanItemAmounts(loan = loan)

      if (payments.isNotEmpty()) {
        PaymentsSection(payments = payments)
      }
    }
  }
}

@Composable
private fun LoanItemHeader(
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
private fun LoanItemAmounts(loan: Loan) {
  if (loan.description.isNotBlank()) {
    Text(
      text = loan.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
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
