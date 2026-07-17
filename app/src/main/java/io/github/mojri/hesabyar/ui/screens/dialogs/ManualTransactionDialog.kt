package io.github.mojri.hesabyar.ui.screens.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.AmountResolutionInput
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.ForecastUIState
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.LoanViewModel
import io.github.mojri.hesabyar.ui.TransactionAmountResolver
import io.github.mojri.hesabyar.ui.TransactionViewModel
import io.github.mojri.hesabyar.ui.components.AmountQuickFillButtons
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.JalaliDateTimePicker

@Composable
fun ManualTransactionDialog(
  transactionViewModel: TransactionViewModel,
  loanViewModel: LoanViewModel,
  installmentViewModel: InstallmentViewModel,
  categories: List<Category>,
  transactionToEdit: Transaction? = null,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val isEditMode = transactionToEdit != null
  var selectedType by remember { mutableStateOf(transactionToEdit?.type?.name ?: TransactionType.EXPENSE.name) }
  val originalAmountRial by remember { mutableStateOf(transactionToEdit?.amount ?: 0L) }
  var amountValue by remember {
    mutableStateOf(
      TextFieldValue(
        if (isEditMode) {
          CurrencyFormatter
            .fromRial(
              transactionToEdit.amount
            ).toString()
        } else {
          ""
        },
        selection = TextRange(Int.MAX_VALUE)
      )
    )
  }
  var amountModified by remember { mutableStateOf(false) }
  var descriptionText by remember { mutableStateOf(transactionToEdit?.description.orEmpty()) }
  var selectedCategoryId by remember { mutableStateOf(transactionToEdit?.categoryId ?: 0L) }
  var personNameText by remember { mutableStateOf(transactionToEdit?.personName ?: "") }
  var titleText by remember { mutableStateOf(transactionToEdit?.description ?: "") }
  var daysFromNowText by remember { mutableStateOf("30") }
  var customDate by remember { mutableStateOf(transactionToEdit?.date ?: System.currentTimeMillis()) }

  val filteredCategories =
    categories.filter { cat ->
      when (selectedType) {
        TransactionType.INCOME.name -> cat.type == CategoryType.INCOME || cat.type == CategoryType.BOTH
        TransactionType.EXPENSE.name -> cat.type == CategoryType.EXPENSE || cat.type == CategoryType.BOTH
        else -> cat.key == "Loans" || cat.key == "Installments" || cat.key == "Other"
      }
    }

  val typeColor =
    when (selectedType) {
      "INCOME", "LOAN_DEBTOR" -> FinancialColors.IncomeGreen
      "EXPENSE", "LOAN_CREDITOR" -> FinancialColors.ExpenseRed
      else -> FinancialColors.WarningOrange
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties =
      androidx.compose.ui.window
        .DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier =
        Modifier
          .fillMaxWidth(0.92f)
          .wrapContentHeight()
          .padding(vertical = SpacingTokens.xl),
      shape = ShapeTokens.XLarge,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = ElevationTokens.lg
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(SpacingTokens.xl),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (isEditMode) "ویرایش تراکنش" else "ثبت دستی تراکنش جدید",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(Dimens.IconLarge)
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "بستن",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

        // Scrollable content
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
        ) {
          // Type selector
          Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
            Text(
              text = "نوع تراکنش / تعهد مالی:",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            val typeScrollState = rememberScrollState()
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .horizontalScroll(typeScrollState),
              horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
            ) {
              val types =
                listOf(
                  Pair("EXPENSE", "هزینه"),
                  Pair("INCOME", "درآمد"),
                  Pair("LOAN_DEBTOR", "طلب (قرض دادم)"),
                  Pair("LOAN_CREDITOR", "بدهی (قرض گرفتم)"),
                  Pair("INSTALLMENT", "قسط")
                )
              types.forEach { (typeKey, typeLabel) ->
                val isSelected = selectedType == typeKey
                val chipColor =
                  when (typeKey) {
                    "INCOME", "LOAN_DEBTOR" -> FinancialColors.IncomeGreen
                    "EXPENSE", "LOAN_CREDITOR" -> FinancialColors.ExpenseRed
                    else -> FinancialColors.WarningOrange
                  }
                Box(
                  modifier =
                    Modifier
                      .clip(ShapeTokens.Medium)
                      .background(
                        if (isSelected) {
                          chipColor
                        } else {
                          MaterialTheme.colorScheme.surfaceVariant
                            .copy(
                              alpha = 0.5f
                            )
                        }
                      ).clickable {
                        selectedType = typeKey
                        selectedCategoryId =
                          when (typeKey) {
                            "INCOME" -> categories.find { it.key == "Income" }?.id ?: 1L
                            "LOAN_DEBTOR", "LOAN_CREDITOR" ->
                              categories.find { it.key == "Loans" }?.id
                                ?: 1L
                            "INSTALLMENT" -> categories.find { it.key == "Installments" }?.id ?: 1L
                            else -> selectedCategoryId
                          }
                      }.padding(horizontal = 14.dp, vertical = SpacingTokens.sm)
                ) {
                  Text(
                    text = typeLabel,
                    color =
                      if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }

          // Amount input
          Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
            Text(
              text = "مبلغ (${CurrencyFormatter.unitLabel}):",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            OutlinedTextField(
              value = amountValue,
              onValueChange = {
                if (isEditMode && it.text != amountValue.text) {
                  amountModified = true
                }
                amountValue = it
              },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .testTag("manual_amount_input"),
              shape = ShapeTokens.Medium,
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Paid,
                  contentDescription = null,
                  tint = typeColor
                )
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = typeColor,
                  focusedLabelColor = typeColor
                )
            )
            AmountQuickFillButtons(
              amountValue = amountValue,
              onValueChanged = {
                amountValue = it
                if (isEditMode) {
                  amountModified = true
                }
              }
            )
            val amtDisplay = amountValue.text.toLongOrNull() ?: 0L
            if (amtDisplay > 0L) {
              val amtRial = CurrencyFormatter.toRial(amtDisplay)
              Text(
                text = "معادل: ${CurrencyFormatter.format(amtRial)}",
                style = MaterialTheme.typography.bodySmall,
                color = typeColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = SpacingTokens.xs)
              )
            }
          }

          // Category Selector
          if (selectedType == "EXPENSE" || selectedType == "INCOME") {
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
              Text(
                text = "دسته‌بندی مربوطه:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
              )
              val categoryScrollState = rememberScrollState()
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .horizontalScroll(categoryScrollState),
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
              ) {
                filteredCategories.forEach { cat ->
                  val isSelected = selectedCategoryId == cat.id
                  Box(
                    modifier =
                      Modifier
                        .clip(ShapeTokens.Medium)
                        .background(
                          if (isSelected) {
                            MaterialTheme.colorScheme.primary
                          } else {
                            MaterialTheme.colorScheme.surfaceVariant
                              .copy(
                                alpha = 0.5f
                              )
                          }
                        ).clickable { selectedCategoryId = cat.id }
                        .padding(horizontal = 14.dp, vertical = SpacingTokens.sm)
                  ) {
                    Text(
                      text = cat.name,
                      color =
                        if (isSelected) {
                          MaterialTheme.colorScheme.onPrimary
                        } else {
                          MaterialTheme.colorScheme.onSurfaceVariant
                        },
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Medium
                    )
                  }
                }
              }
            }
          }

          // Conditional Person Name for loans
          if (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") {
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
              Text(
                text = "طرف حساب (شخص مربوطه):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
              )
              OutlinedTextField(
                value = personNameText,
                onValueChange = { personNameText = it },
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .testTag("manual_person_input"),
                shape = ShapeTokens.Medium,
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                },
                placeholder = { Text("مثلا: علی محمودی", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true
              )
            }
          }

          // Conditional Installment fields
          if (selectedType == "INSTALLMENT") {
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)) {
              Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
                Text(
                  text = "عنوان قسط:",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                  value = titleText,
                  onValueChange = { titleText = it },
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .testTag("manual_title_input"),
                  shape = ShapeTokens.Medium,
                  placeholder = {
                    Text(
                      "مثلا: قسط بانک مسکن",
                      style = MaterialTheme.typography.bodyMedium
                    )
                  },
                  singleLine = true
                )
              }
              Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
                Text(
                  text = "فاصله تا موعد پرداخت (روز):",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                  value = daysFromNowText,
                  onValueChange = { daysFromNowText = it },
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .testTag("manual_days_input"),
                  shape = ShapeTokens.Medium,
                  placeholder = { Text("مثلا: ۳۰", style = MaterialTheme.typography.bodyMedium) },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true
                )
              }
            }
          }

          // Shamsi Date & Time Picker
          JalaliDateTimePicker(
            initialTimestamp = customDate,
            onTimestampChanged = { customDate = it }
          )

          // Description text field
          Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
            Text(
              text = "شرح یا توضیح تراکنش:",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            OutlinedTextField(
              value = descriptionText,
              onValueChange = { descriptionText = it },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .testTag("manual_description_input"),
              shape = ShapeTokens.Medium,
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              },
              singleLine = true
            )
          }
        }

        Spacer(modifier = Modifier.height(SpacingTokens.sm))

        // Actions block
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            shape = ShapeTokens.Medium
          ) {
            Text("انصراف")
          }

          Button(
            onClick = {
              val finalAmountDisplay = amountValue.text.toLongOrNull() ?: 0L
              if (finalAmountDisplay <= 0L) {
                android.widget.Toast
                  .makeText(
                    context,
                    "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید",
                    android.widget.Toast.LENGTH_SHORT
                  ).show()
                return@Button
              }
              val resolutionResult =
                TransactionAmountResolver.resolveAmount(
                  AmountResolutionInput(
                    displayedAmount = finalAmountDisplay,
                    isEditMode = isEditMode,
                    originalRialAmount = originalAmountRial,
                    userModifiedAmount = amountModified
                  )
                )
              val finalAmountRial = resolutionResult.rialAmount

              if ((selectedType == "INCOME" || selectedType == "EXPENSE") && selectedCategoryId == 0L) {
                android.widget.Toast
                  .makeText(
                    context,
                    "لطفا دسته‌بندی را انتخاب کنید",
                    android.widget.Toast.LENGTH_SHORT
                  ).show()
                return@Button
              }

              when (selectedType) {
                "INCOME", "EXPENSE" -> {
                  val selectedCategoryName =
                    categories.find { it.id == selectedCategoryId }?.name ?: "سایر"
                  val desc = descriptionText.trim().ifEmpty { selectedCategoryName }
                  if (isEditMode) {
                    val updatedTransaction =
                      transactionToEdit.copy(
                        type = TransactionType.valueOf(selectedType),
                        categoryId = selectedCategoryId,
                        amount = finalAmountRial,
                        description = desc,
                        date = customDate
                      )
                    transactionViewModel.updateTransaction(updatedTransaction)
                  } else {
                    transactionViewModel.addTransaction(
                      type = TransactionType.valueOf(selectedType),
                      categoryId = selectedCategoryId,
                      amount = finalAmountRial,
                      description = desc,
                      customDate = customDate
                    )
                  }
                }
                "LOAN_DEBTOR", "LOAN_CREDITOR" -> {
                  val person = personNameText.trim()
                  if (person.isEmpty()) {
                    android.widget.Toast
                      .makeText(
                        context,
                        "لطفا نام شخص مربوطه را وارد کنید",
                        android.widget.Toast.LENGTH_SHORT
                      ).show()
                    return@Button
                  }
                  val desc =
                    descriptionText.trim().ifEmpty {
                      if (selectedType ==
                        "LOAN_DEBTOR"
                      ) {
                        "قرض دادن به $person"
                      } else {
                        "قرض گرفتن از $person"
                      }
                    }
                  loanViewModel.addLoan(
                    personName = person,
                    type = if (selectedType == "LOAN_DEBTOR") LoanType.DEBTOR else LoanType.CREDITOR,
                    amount = finalAmountRial,
                    description = desc,
                    customDate = customDate
                  )
                }
                "INSTALLMENT" -> {
                  val title = titleText.trim()
                  if (title.isEmpty()) {
                    android.widget.Toast
                      .makeText(
                        context,
                        "لطفا عنوان قسط را وارد کنید",
                        android.widget.Toast.LENGTH_SHORT
                      ).show()
                    return@Button
                  }
                  val desc = descriptionText.trim()
                  installmentViewModel.addInstallment(
                    title = title,
                    amount = finalAmountRial,
                    dueDate = customDate,
                    reminderEnabled = true,
                    notes = desc
                  )
                }
              }
              onDismiss()
            },
            modifier = Modifier.weight(1f),
            shape = ShapeTokens.Medium,
            colors = ButtonDefaults.buttonColors(containerColor = typeColor)
          ) {
            Text(
              if (isEditMode) "ذخیره تغییرات" else "ثبت تراکنش",
              color = MaterialTheme.colorScheme.onPrimary
            )
          }
        }
      }
    }
  }
}
