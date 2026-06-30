package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.ui.LoanViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.HesabyarChip
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import kotlinx.coroutines.flow.firstOrNull
import java.util.*

@Composable
fun LoanManagementScreen(
    loanViewModel: LoanViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val loans by loanViewModel.loans.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<Loan?>(null) }
    var termState by remember { mutableStateOf("DEBTOR") } // "DEBTOR" = they owe me, "CREDITOR" = I owe them

    // Filtered lists
    val debtors = loans.filter { it.type == "DEBTOR" }
    val creditors = loans.filter { it.type == "CREDITOR" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingTokens.lg),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
    ) {
        // Stats and trigger row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "دفتر قرض و امور مالی اشخاص",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            HesabyarButton(
                onClick = { showAddDialog = true },
                text = "ثبت جدید",
                icon = Icons.Filled.Add,
                modifier = Modifier.testTag("add_loan_button")
            )
        }

        // Tab selection (Debtors vs Creditors)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
        ) {
            HesabyarChip(
                selected = termState == "DEBTOR",
                onClick = { termState = "DEBTOR" },
                label = "طلب‌های من (بدهکاران)",
                modifier = Modifier.weight(1f)
            )

            HesabyarChip(
                selected = termState == "CREDITOR",
                onClick = { termState = "CREDITOR" },
                label = "بدهی‌های من (طلبکاران)",
                modifier = Modifier.weight(1f)
            )
        }

        val activeList = if (termState == "DEBTOR") debtors else creditors

        if (activeList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
                ) {
                    Icon(
                        imageVector = if (termState == "DEBTOR") Icons.Filled.ArrowCircleDown else Icons.Filled.ArrowCircleUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = if (termState == "DEBTOR") "هیچ طلبی ثبت نشده است." else "هیچ بدهی‌ای ثبت نشده است.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
            ) {
                items(activeList) { loan ->
                    LoanListItem(
                        loan = loan,
                        loanViewModel = loanViewModel,
                        settingsViewModel = settingsViewModel,
                        onDelete = { loanViewModel.deleteLoan(loan) },
                        onEdit = { editingLoan = loan }
                    )
                }
            }
        }
    }

    // Add Loan Dialog
    if (showAddDialog) {
        var personName by remember { mutableStateOf("") }
        var loanType by remember { mutableStateOf(termState) } // DEBTOR or CREDITOR
        var amountText by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var customDate by remember { mutableStateOf(System.currentTimeMillis()) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    "ثبت قرض جدید",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md),
                    horizontalAlignment = Alignment.End
                ) {
                    // Type selector Inside Dialog
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeTokens.Small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(SpacingTokens.xs)
                    ) {
                        HesabyarButton(
                            onClick = { loanType = "DEBTOR" },
                            modifier = Modifier.weight(1f),
                            text = "من قرض دادم",
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (loanType == "DEBTOR") FinancialColors.IncomeGreen else Color.Transparent,
                                contentColor = if (loanType == "DEBTOR") Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )

                        HesabyarButton(
                            onClick = { loanType = "CREDITOR" },
                            modifier = Modifier.weight(1f),
                            text = "من قرض گرفتم",
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (loanType == "CREDITOR") FinancialColors.ExpenseRed else Color.Transparent,
                                contentColor = if (loanType == "CREDITOR") Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    HesabyarInputField(
                        value = personName,
                        onValueChange = { personName = it },
                        label = "نام شخص طرف حساب"
                    )

                    HesabyarInputField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = "مبلغ قرض (تومان)"
                    )

                    HesabyarInputField(
                        value = description,
                        onValueChange = { description = it },
                        label = "توضیحات و بابت چی..."
                    )

                    JalaliDateTimePicker(
                        initialTimestamp = customDate,
                        onTimestampChanged = { customDate = it }
                    )
                }
            },
            confirmButton = {
                HesabyarButton(
                    onClick = {
                        val amountToman = amountText.toLongOrNull() ?: 0L
                        if (personName.isNotBlank() && amountToman > 0L) {
                            val amountRial = amountToman * 1000L
                            loanViewModel.addLoan(personName, loanType, amountRial, description, customDate)
                            showAddDialog = false
                        } else {
                            settingsViewModel.showMessage("لطفا اطلاعات را کامل و صحیح پر کنید")
                        }
                    },
                    text = "ثبت و ذخیره"
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

    // Edit Loan Dialog
    if (editingLoan != null) {
        val loan = editingLoan!!
        var personName by remember { mutableStateOf(loan.personName) }
        var loanType by remember { mutableStateOf(loan.type) }
        var amountText by remember { mutableStateOf((loan.originalAmount / 1000).toString()) }
        var description by remember { mutableStateOf(loan.description) }
        var customDate by remember { mutableStateOf(loan.date) }

        AlertDialog(
            onDismissRequest = { editingLoan = null },
            title = {
                Text(
                    "ویرایش قرض",
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
                        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
                    ) {
                        HesabyarButton(
                            onClick = { loanType = "DEBTOR" },
                            modifier = Modifier.weight(1f),
                            text = "من قرض دادم",
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (loanType == "DEBTOR") FinancialColors.IncomeGreen else Color.Transparent,
                                contentColor = if (loanType == "DEBTOR") Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )

                        HesabyarButton(
                            onClick = { loanType = "CREDITOR" },
                            modifier = Modifier.weight(1f),
                            text = "من قرض گرفتم",
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (loanType == "CREDITOR") FinancialColors.ExpenseRed else Color.Transparent,
                                contentColor = if (loanType == "CREDITOR") Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    HesabyarInputField(
                        value = personName,
                        onValueChange = { personName = it },
                        label = "نام شخص طرف حساب"
                    )

                    HesabyarInputField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = "مبلغ قرض (تومان)"
                    )

                    HesabyarInputField(
                        value = description,
                        onValueChange = { description = it },
                        label = "توضیحات و بابت چی..."
                    )

                    JalaliDateTimePicker(
                        initialTimestamp = customDate,
                        onTimestampChanged = { customDate = it }
                    )
                }
            },
            confirmButton = {
                HesabyarButton(
                    onClick = {
                        val amountToman = amountText.toLongOrNull() ?: 0L
                        if (personName.isNotBlank() && amountToman > 0L) {
                            val amountRial = amountToman * 1000L
                            loanViewModel.updateLoan(
                                loan.copy(
                                    personName = personName,
                                    type = loanType,
                                    originalAmount = amountRial,
                                    description = description,
                                    date = customDate
                                )
                            )
                            editingLoan = null
                        } else {
                            settingsViewModel.showMessage("لطفا اطلاعات را کامل و صحیح پر کنید")
                        }
                    },
                    text = "ذخیره تغییرات"
                )
            },
            dismissButton = {
                HesabyarButton(
                    onClick = { editingLoan = null },
                    text = "انصراف",
                    variant = ButtonVariant.Text
                )
            }
        )
    }
}

@Composable
fun LoanListItem(
    loan: Loan,
    loanViewModel: LoanViewModel,
    settingsViewModel: SettingsViewModel,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRepayDialog by remember { mutableStateOf(false) }
    val paymentHistory by loanViewModel.getPaymentHistory(loan.id).collectAsState(initial = emptyList())

    val statusColor = if (loan.isSettled) MaterialTheme.colorScheme.primary else if (loan.type == "DEBTOR") FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
    val statusText = if (loan.isSettled) "تسویه شده" else if (loan.type == "DEBTOR") "طلب وصول‌نشده" else "جای بازپرداخت باقی‌مانده"

    HesabyarCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = ShapeTokens.Large,
        cardColors = CardDefaults.cardColors(
            containerColor = if (loan.isSettled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(statusColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (loan.type == "DEBTOR") Icons.Filled.ArrowCircleDown else Icons.Filled.ArrowCircleUp,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(SpacingTokens.md))
                    Column {
                        Text(
                            text = loan.personName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "بابت: ${loan.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "مانده: " + formatToman(loan.remainingAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = "کل: " + formatToman(loan.originalAmount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Quick settlement indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تاریخ ثبت: ${formatPersianDate(loan.date)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                Box(
                    modifier = Modifier
                        .clip(ShapeTokens.Small)
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = SpacingTokens.sm, vertical = SpacingTokens.xs)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Expanded view - Payments history list and custom repayment trigger
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SpacingTokens.md),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    Text(
                        text = "📝 تاریخچه بازپرداخت‌ها:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (paymentHistory.isEmpty()) {
                        Text(
                            text = "تاکنون هیچ برگی از بازپرداخت ثبت نشده است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else {
                        paymentHistory.forEach { pm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ShapeTokens.Small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(SpacingTokens.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = formatToman(pm.amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                    if (pm.notes.isNotBlank()) {
                                        Text(
                                            text = pm.notes,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                Text(
                                    text = formatPersianDate(pm.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(SpacingTokens.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .background(FinancialColors.ExpenseRed.copy(alpha = 0.1f), CircleShape)
                                .size(Dimens.AvatarMedium)
                        ) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "حذف قرض", tint = FinancialColors.ExpenseRed)
                        }

                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), CircleShape)
                                .size(Dimens.AvatarMedium)
                        ) {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = "ویرایش قرض", tint = MaterialTheme.colorScheme.primary)
                        }

                        if (!loan.isSettled) {
                            HesabyarButton(
                                onClick = { showRepayDialog = true },
                                modifier = Modifier.weight(1f),
                                text = "ثبت بازپرداخت جديد",
                                icon = Icons.Filled.Payments,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            )
                        }
                    }
                }
            }
        }
    }

    // Repayment dialog
    if (showRepayDialog) {
        var repayAmount by remember { mutableStateOf("") }
        var repayNotes by remember { mutableStateOf("") }
        var repayDate by remember { mutableStateOf(System.currentTimeMillis()) }

        AlertDialog(
            onDismissRequest = { showRepayDialog = false },
            title = {
                Text(
                    "ثبت بازپرداخت",
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
                        value = repayAmount,
                        onValueChange = { repayAmount = it },
                        label = "مبلغ پرداختی (تومان)"
                    )

                    HesabyarInputField(
                        value = repayNotes,
                        onValueChange = { repayNotes = it },
                        label = "توضیحات (مثلا نقدی یا کارت به کارت)"
                    )

                    JalaliDateTimePicker(
                        initialTimestamp = repayDate,
                        onTimestampChanged = { repayDate = it }
                    )
                }
            },
            confirmButton = {
                HesabyarButton(
                    onClick = {
                        val amountToman = repayAmount.toLongOrNull() ?: 0L
                        if (amountToman > 0L) {
                            val amountRial = amountToman * 1000L
                            loanViewModel.makeRepayment(loan.id, amountRial, repayNotes, repayDate)
                            showRepayDialog = false
                        } else {
                            settingsViewModel.showMessage("لطفا مبلغ صحیح وارد کنید")
                        }
                    },
                    text = "پرداخت شد"
                )
            },
            dismissButton = {
                HesabyarButton(
                    onClick = { showRepayDialog = false },
                    text = "انصراف",
                    variant = ButtonVariant.Text
                )
            }
        )
    }
}
