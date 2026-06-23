package io.github.mojri.hesabyar.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.ui.FinanceViewModel
import io.github.mojri.hesabyar.ui.HesabyarViewModel
import io.github.mojri.hesabyar.ui.theme.ExpenseRed
import io.github.mojri.hesabyar.ui.theme.IncomeGreen
import io.github.mojri.hesabyar.ui.theme.WarningOrange
import java.util.*

@Composable
fun InstallmentScreen(
    financeViewModel: FinanceViewModel,
    viewModel: HesabyarViewModel,
    modifier: Modifier = Modifier
) {
    val installments by financeViewModel.installments.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var listFilterState by remember { mutableStateOf("UNPAID") } // "UNPAID", "PAID", "ALL"

    // Process lists
    val unpaid = installments.filter { !it.isPaid }
    val paid = installments.filter { it.isPaid }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("add_installment_button")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("ثبت قسط", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Horizontal filter bar (Unpaid / Paid / All)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val filterButtons = listOf(
                "UNPAID" to "پرداخت‌نشده",
                "PAID" to "پرداخت شده",
                "ALL" to "همه اقساط"
            )

            filterButtons.forEach { (filterMode, title) ->
                Button(
                    onClick = { listFilterState = filterMode },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (listFilterState == filterMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (listFilterState == filterMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = null,
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        val displayList = when (listFilterState) {
            "UNPAID" -> unpaid
            "PAID" -> paid
            else -> installments
        }

        if (displayList.isEmpty()) {
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
                        imageVector = Icons.Filled.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = when (listFilterState) {
                            "UNPAID" -> "هیچ قسط پرداخت‌نشده پیش‌رویی وجود ندارد."
                            "PAID" -> "هنوز قسطی پرداخت نشده است."
                            else -> "هیچ قسطی ثبت نشده است."
                        },
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
                items(displayList) { installment ->
                    InstallmentListItem(
                        installment = installment,
                        financeViewModel = financeViewModel
                    )
                }
            }
        }
    }

    // Add Installment Dialog
    if (showAddDialog) {
        val calendar = Calendar.getInstance()
        val context = LocalContext.current

        var title by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var dateInMillis by remember { mutableStateOf(calendar.timeInMillis) }
        var datePrompt by remember { mutableStateOf("انتخاب تاریخ سررسید") }
        var reminderEnabled by remember { mutableStateOf(true) }
        var notes by remember { mutableStateOf("") }

        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                dateInMillis = calendar.timeInMillis
                datePrompt = "$year/${month + 1}/$dayOfMonth"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان قسط (مثلا قسط ماشین)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("مبلغ قسط (تومان)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date Picker Button inside manual add dialog
                    Button(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(datePrompt)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            onCheckedChange = { reminderEnabled = it }
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("شرح و یادداشت اضافی") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountToman = amountText.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank() && amountToman > 0.0) {
                            val amountRial = (amountToman * 1000).toLong()
                            financeViewModel.addInstallment(title, amountRial, dateInMillis, reminderEnabled, notes)
                            showAddDialog = false
                        } else {
                            viewModel.showMessage("لطفا فیلدهای اولیه قسط را کامل کنید")
                        }
                    }
                ) {
                    Text("ذخیره قسط")
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
fun InstallmentListItem(
    installment: Installment,
    financeViewModel: FinanceViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val colorAccent = if (installment.isPaid) IncomeGreen else WarningOrange

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (installment.isPaid) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            .background(colorAccent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (installment.isPaid) Icons.Filled.CheckCircle else Icons.Filled.PendingActions,
                            contentDescription = null,
                            tint = colorAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(
                    text = formatToman(installment.amount),
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (installment.reminderEnabled && !installment.isPaid) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "زنگ فعال",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "زنگ سررسید فعال",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Button(
                    onClick = { financeViewModel.toggleInstallmentPaid(installment) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (installment.isPaid) MaterialTheme.colorScheme.surfaceVariant else colorAccent,
                        contentColor = if (installment.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    if (installment.notes.isNotBlank()) {
                        Text(
                            text = "📝 یادداشت: ${installment.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { financeViewModel.deleteInstallment(installment) },
                            modifier = Modifier
                                .background(ExpenseRed.copy(alpha = 0.1f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "حذف قسط", modifier = Modifier.size(18.dp), tint = ExpenseRed)
                        }
                    }
                }
            }
        }
    }
}
