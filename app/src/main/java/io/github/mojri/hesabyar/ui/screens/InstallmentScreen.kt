package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.core.filterDigits
import io.github.mojri.hesabyar.core.toCleanLongOrNull
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.ui.BankLoanViewModel
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarChip
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.JalaliDateTimePicker
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.utils.DisplayAmountInput
import io.github.mojri.hesabyar.ui.utils.formatPersianDate
import java.util.*

@Composable
fun InstallmentScreen(
  installmentViewModel: InstallmentViewModel,
  settingsViewModel: SettingsViewModel,
  bankLoanViewModel: BankLoanViewModel,
  modifier: Modifier = Modifier
) {
  val installments by installmentViewModel.visibleInstallments.collectAsState()

  var showAddDialog by rememberSaveable { mutableStateOf(false) }
  var listFilterState by rememberSaveable { mutableStateOf("UNPAID") } // "UNPAID", "PAID", "ALL"

  // Process lists
  val unpaid = installments.filter { !it.isPaid }
  val paid = installments.filter { it.isPaid }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(SpacingTokens.lg),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
  ) {
    // Title and add trigger row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "دفتر مدیریت اقساط من",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
      )

      HesabyarButton(
        onClick = { showAddDialog = true },
        icon = Icons.Filled.Add,
        text = "ثبت قسط",
        modifier = Modifier.testTag("add_installment_button")
      )
    }

    // Bank-loan filter
    val bankLoans by bankLoanViewModel.bankLoans.collectAsState()
    val bankLoanFilter by installmentViewModel.bankLoanFilter.collectAsState()
    if (bankLoans.isNotEmpty() || bankLoanFilter != null) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
      ) {
        HesabyarChip(
          selected = bankLoanFilter == null,
          onClick = { installmentViewModel.setBankLoanFilter(null) },
          label = "همه وام‌ها"
        )
        bankLoans.forEach { bl ->
          HesabyarChip(
            selected = bankLoanFilter == bl.id,
            onClick = { installmentViewModel.setBankLoanFilter(bl.id) },
            label = "${bl.bankName} — ${bl.loanName}"
          )
        }
      }
    }

    // Horizontal filter bar (Unpaid / Paid / All)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(ShapeTokens.Medium)
          .background(MaterialTheme.colorScheme.surfaceContainer)
          .padding(SpacingTokens.xs),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
    ) {
      val filterButtons =
        listOf(
          "UNPAID" to "پرداخت‌نشده",
          "PAID" to "پرداخت شده",
          "ALL" to "همه اقساط"
        )

      filterButtons.forEach { (filterMode, title) ->
        HesabyarChip(
          selected = listFilterState == filterMode,
          onClick = { listFilterState = filterMode },
          modifier = Modifier.weight(1f),
          label = title
        )
      }
    }

    val displayList =
      when (listFilterState) {
        "UNPAID" -> unpaid
        "PAID" -> paid
        else -> installments
      }

    if (displayList.isEmpty()) {
      Box(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          Icon(
            imageVector = Icons.Filled.CreditCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(Dimens.AvatarLarge)
          )
          Text(
            text =
              when (listFilterState) {
                "UNPAID" -> "هیچ قسط پرداخت‌نشده پیش‌رویی وجود ندارد."
                "PAID" -> "هنوز قسطی پرداخت نشده است."
                else -> "هیچ قسطی ثبت نشده است."
              },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        items(displayList, key = { it.id }) { installment ->
          InstallmentListItem(
            installment = installment,
            installmentViewModel = installmentViewModel
          )
        }
      }
    }
  }

  // Add Installment Dialog
  if (showAddDialog) {
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var dateInMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var reminderEnabled by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = {
        Text(
          "ثبت قسط جدید",
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
          HesabyarInputField(
            value = title,
            onValueChange = { title = it },
            label = "عنوان قسط (مثلا قسط ماشین)"
          )

          HesabyarInputField(
            value = amountText,
            onValueChange = { amountText = it.filterDigits() },
            label = "مبلغ قسط (${CurrencyFormatter.unitLabel})"
          )

          JalaliDateTimePicker(
            initialTimestamp = dateInMillis,
            onTimestampChanged = { dateInMillis = it }
          )

          Row(
            // The whole row toggles the switch. TalkBack reads the label and the state.
            modifier =
              Modifier.fillMaxWidth().toggleable(
                value = reminderEnabled,
                onValueChange = { reminderEnabled = it },
                role = Role.Switch
              ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "اعلام و هشدار سررسید",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
              checked = reminderEnabled,
              onCheckedChange = null,
              enabled = true
            )
          }

          HesabyarInputField(
            value = notes,
            onValueChange = { notes = it },
            label = "شرح و یادداشت اضافی",
            singleLine = false
          )
        }
      },
      confirmButton = {
        HesabyarButton(
          onClick = {
            val amountDisplay = amountText.toCleanLongOrNull() ?: 0L
            when {
              title.isBlank() || amountDisplay <= 0L ->
                settingsViewModel.showMessage("لطفا فیلدهای اولیه قسط را کامل کنید")
              overflowsTomanConversion(amountDisplay) ->
                settingsViewModel.showMessage("مبلغ وارد شده بیش از حد مجاز است")
              else -> {
                val amountRial = CurrencyFormatter.toRial(amountDisplay)
                installmentViewModel.addInstallment(title, amountRial, dateInMillis, reminderEnabled, notes)
                installmentViewModel.setBankLoanFilter(null)
                showAddDialog = false
              }
            }
          },
          text = "ذخیره قسط"
        )
      },
      dismissButton = {
        HesabyarButton(
          onClick = { showAddDialog = false },
          text = "انصراف",
          variant = ButtonVariant.Text
        )
      }
    )
  }
}

@Composable
fun InstallmentListItem(
  installment: Installment,
  installmentViewModel: InstallmentViewModel
) {
  var expanded by remember { mutableStateOf(false) }
  // Holds the tapped installment itself, not a boolean.
  // Items are keyed by installment id. The state stays with its row
  // while the row remains in the list. Filtering the row out removes
  // its composition, and with it any open delete dialog.
  var pendingDelete by remember { mutableStateOf<Installment?>(null) }
  val colorAccent = if (installment.isPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button) { expanded = !expanded }
        // Tell screen readers whether the card is expanded or collapsed.
        .semantics { stateDescription = if (expanded) "باز" else "بسته" },
    shape = ShapeTokens.Large,
    cardColors =
      CardDefaults.cardColors(
        containerColor =
          if (installment.isPaid) {
            MaterialTheme.colorScheme.surfaceContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          }
      )
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconCircle(
            icon = if (installment.isPaid) Icons.Filled.CheckCircle else Icons.Filled.PendingActions,
            tint = colorAccent,
            backgroundColor = colorAccent
          )
          Spacer(modifier = Modifier.width(SpacingTokens.md))
          Column {
            Text(
              text = installment.title,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "سررسید: ${formatPersianDate(installment.dueDate)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Text(
          text = CurrencyFormatter.format(installment.amount),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          color = colorAccent
        )
      }

      // Quick payment toggle button and status
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
        ) {
          if (installment.reminderEnabled && !installment.isPaid) {
            Icon(
              imageVector = Icons.Filled.NotificationsActive,
              contentDescription = "زنگ فعال",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(Dimens.IconSmall)
            )
            Text(
              "زنگ سررسید فعال",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        Button(
          onClick = { installmentViewModel.toggleInstallmentPaid(installment) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (installment.isPaid) {
                  MaterialTheme.colorScheme.surfaceVariant
                } else {
                  colorAccent
                },
              contentColor =
                if (installment.isPaid) {
                  MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                  MaterialTheme.colorScheme.onTertiary
                }
            ),
          shape = ShapeTokens.Small,
          contentPadding = PaddingValues(horizontal = SpacingTokens.lg, vertical = SpacingTokens.xs),
          elevation = null
        ) {
          Text(
            text = if (installment.isPaid) "پرداخت شده" else "علامت پرداخت",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
          )
        }
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
        ) {
          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          if (installment.notes.isNotBlank()) {
            Text(
              text = "یادداشت: ${installment.notes}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = Dimens.SECONDARY_TEXT_ALPHA),
              lineHeight = 16.sp
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            IconButton(
              onClick = { pendingDelete = installment },
              // Enforce the 48dp minimum touch area. The 32dp tinted circle
              // stays on the inner box so the visual does not change.
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Box(
                modifier =
                  Modifier
                    .size(Dimens.AvatarSmall)
                    .background(
                      MaterialTheme.colorScheme.error.copy(alpha = Dimens.CARD_CONTAINER_ALPHA),
                      CircleShape
                    ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Delete,
                  contentDescription = "حذف قسط",
                  modifier = Modifier.size(Dimens.IconMenu),
                  tint = MaterialTheme.colorScheme.error
                )
              }
            }
          }
        }
      }
    }
  }

  if (pendingDelete != null) {
    ConfirmDialog(
      title = "حذف قسط",
      message = "آیا از حذف این قسط اطمینان دارید؟ این عمل قابل بازگشت نیست.",
      confirmText = "حذف",
      dismissText = "انصراف",
      onDismiss = { pendingDelete = null },
      onConfirm = {
        val target = pendingDelete
        pendingDelete = null
        if (target != null) installmentViewModel.deleteInstallment(target)
      }
    )
  }
}

/**
 * True when [displayValue] is too large for [CurrencyFormatter.toRial] to
 * convert without overflowing a Long. The bound comes from
 * [DisplayAmountInput.maxDisplayAmount] — the single source of truth, derived
 * from the conversion itself (Toman mode allows up to a tenth of [Long.MAX_VALUE],
 * Rial mode the full range, where this can never fire).
 */
private fun overflowsTomanConversion(displayValue: Long): Boolean = displayValue > DisplayAmountInput.maxDisplayAmount()
