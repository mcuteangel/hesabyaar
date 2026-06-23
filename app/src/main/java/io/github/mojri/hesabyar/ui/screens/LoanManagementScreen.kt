package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.mojri.hesabyar.ui.HesabyarViewModel
import io.github.mojri.hesabyar.ui.FinanceViewModel
import io.github.mojri.hesabyar.ui.theme.ExpenseRed
import io.github.mojri.hesabyar.ui.theme.IncomeGreen
import io.github.mojri.hesabyar.ui.theme.WarningOrange
import kotlinx.coroutines.flow.firstOrNull
import java.util.*

@Composable
fun LoanManagementScreen(
    financeViewModel: FinanceViewModel,
    viewModel: HesabyarViewModel,
    modifier: Modifier = Modifier
) {
    val loans by financeViewModel.loans.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var termState by remember { mutableStateOf("DEBTOR") } // "DEBTOR" = they owe me, "CREDITOR" = I owe them

    // Filtered lists
    val debtors = loans.filter { it.type == "DEBTOR" }
    val creditors = loans.filter { it.type == "CREDITOR" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("add_loan_button")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("ثبت جدید", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Tab selection (Debtors vs Creditors)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            Button(
                onClick = { termState = "DEBTOR" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (termState == "DEBTOR") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (termState == "DEBTOR") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = null
            ) {
                Text("طلب‌های من (بدهکاران)", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = { termState = "CREDITOR" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (termState == "CREDITOR") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (termState == "CREDITOR") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = null
            ) {
                Text("بدهی‌های من (طلبکاران)", style = MaterialTheme.typography.labelLarge)
            }
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activeList) { loan ->
                    LoanListItem(
                        loan = loan,
                        financeViewModel = financeViewModel,
                        viewModel = viewModel,
                        onDelete = { financeViewModel.deleteLoan(loan) }
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Type selector Inside Dialog
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp)
                    ) {
                        Button(
                            onClick = { loanType = "DEBTOR" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (loanType == "DEBTOR") IncomeGreen else Color.Transparent,
                                contentColor = if (loanType == "DEBTOR") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("من قرض دادم", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { loanType = "CREDITOR" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (loanType == "CREDITOR") ExpenseRed else Color.Transparent,
                                contentColor = if (loanType == "CREDITOR") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("من قرض گرفتم", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it },
                        label = { Text("نام شخص طرف حساب") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("مبلغ قرض (تومان)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("توضیحات و بابت چی...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountToman = amountText.toDoubleOrNull() ?: 0.0
                        if (personName.isNotBlank() && amountToman > 0.0) {
                            val amountRial = (amountToman * 1000).toLong()
                            financeViewModel.addLoan(personName, loanType, amountRial, description)
                            showAddDialog = false
                        } else {
                            viewModel.showMessage("لطفا اطلاعات را کامل و صحیح پر کنید")
                        }
                    }
                ) {
                    Text("ثبت و ذخیره")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun LoanListItem(
    loan: Loan,
    financeViewModel: FinanceViewModel,
    viewModel: HesabyarViewModel,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRepayDialog by remember { mutableStateOf(false) }
    val paymentHistory by financeViewModel.getPaymentHistory(loan.id).collectAsState(initial = emptyList())

    val statusColor = if (loan.isSettled) MaterialTheme.colorScheme.primary else if (loan.type == "DEBTOR") IncomeGreen else ExpenseRed
    val statusText = if (loan.isSettled) "تسویه شده" else if (loan.type == "DEBTOR") "طلب وصول‌نشده" else "جای بازپرداخت باقی‌مانده"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (loan.isSettled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Spacer(modifier = Modifier.width(12.dp))
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
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
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
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(8.dp),
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

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .background(ExpenseRed.copy(alpha = 0.1f), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "حذف قرض", tint = ExpenseRed)
                        }

                        if (!loan.isSettled) {
                            Button(
                                onClick = { showRepayDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Icon(imageVector = Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ثبت بازپرداخت جديد")
                            }
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = repayAmount,
                        onValueChange = { repayAmount = it },
                        label = { Text("مبلغ پرداختی (تومان)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = repayNotes,
                        onValueChange = { repayNotes = it },
                        label = { Text("توضیحات (مثلا نقدی یا کارت به کارت)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountToman = repayAmount.toDoubleOrNull() ?: 0.0
                        if (amountToman > 0.0) {
                            val amountRial = (amountToman * 1000).toLong()
                            financeViewModel.makeRepayment(loan.id, amountRial, repayNotes)
                            showRepayDialog = false
                        } else {
                            viewModel.showMessage("لطفا مبلغ صحیح وارد کنید")
                        }
                    }
                ) {
                    Text("پرداخت شد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepayDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
