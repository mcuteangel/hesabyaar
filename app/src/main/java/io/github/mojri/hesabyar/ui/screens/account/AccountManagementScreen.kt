@file:Suppress("TooManyFunctions")

package io.github.mojri.hesabyar.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.ui.AccountViewModel
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

private val ACCOUNT_TYPE_ICONS: Map<AccountType, ImageVector> =
  mapOf(
    AccountType.BANK to Icons.Filled.AccountBalance,
    AccountType.CASH_WALLET to Icons.Filled.Wallet,
    AccountType.SAVINGS_INVESTMENT to Icons.Filled.Savings,
    AccountType.OTHER to Icons.Filled.Payments,
  )

private val ACCOUNT_COLORS =
  listOf(
    0xFF4CAF50L,
    0xFFFF9800L,
    0xFF2196F3L,
    0xFF009688L,
    0xFFF44336L,
    0xFF9C27B0L,
    0xFF757575L,
    0xFFE91E63L,
    0xFF3F51B5L,
    0xFF00BCD4L,
    0xFF8BC34AL,
    0xFFFF5722L,
    0xFF607D8BL,
    0xFF795548L,
    0xFFCDDC39L,
    0xFF03A9F4L,
  )

private const val DEFAULT_ACCOUNT_COLOR = 0xFF4CAF50L
private const val COLOR_PICKER_COLUMNS = 8

private val AccountType.displayName: String
  @Composable
  get() =
    when (this) {
      AccountType.BANK -> "بانک"
      AccountType.CASH_WALLET -> "کیف پول"
      AccountType.SAVINGS_INVESTMENT -> "پس‌انداز و سرمایه‌گذاری"
      AccountType.OTHER -> "سایر"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun AccountManagementScreen(
  accountViewModel: AccountViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val accounts by accountViewModel.accounts.collectAsState()
  var showAddDialog by remember { mutableStateOf(false) }
  var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
  var showDeleteConfirmation by remember { mutableStateOf<AccountEntity?>(null) }
  var showTransactionWarning by remember { mutableStateOf<AccountEntity?>(null) }
  var pendingDeleteAccount by remember { mutableStateOf<AccountEntity?>(null) }
  var overflowMenuAccount by remember { mutableStateOf<AccountEntity?>(null) }

  pendingDeleteAccount?.let { account ->
    androidx.compose.runtime.LaunchedEffect(account) {
      accountViewModel.canDeleteAccount(account.id) { canDelete ->
        pendingDeleteAccount = null
        if (canDelete) {
          showDeleteConfirmation = account
        } else {
          showTransactionWarning = account
        }
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "مدیریت حساب‌ها", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = "بازگشت")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = "افزودن حساب")
      }
    }
  ) { innerPadding ->
    AccountManagementContent(
      accounts = accounts,
      modifier = modifier,
      innerPadding = innerPadding,
      onOverflowClick = { overflowMenuAccount = it }
    )
  }

  AccountManagementDialogs(
    showAddDialog = showAddDialog,
    editingAccount = editingAccount,
    showDeleteConfirmation = showDeleteConfirmation,
    showTransactionWarning = showTransactionWarning,
    overflowMenuAccount = overflowMenuAccount,
    onDismissAddDialog = { showAddDialog = false },
    onDismissEditing = { editingAccount = null },
    onDismissDelete = { showDeleteConfirmation = null },
    onDismissTransactionWarning = { showTransactionWarning = null },
    onDismissOverflow = { overflowMenuAccount = null },
    onSaveAccount = { name, type, bankName, cardNumber, accountNumber, iban, initialBalance, color ->
      accountViewModel.addAccount(
        name = name,
        type = type,
        bankName = bankName,
        cardNumber = cardNumber,
        accountNumber = accountNumber,
        iban = iban,
        initialBalance = initialBalance,
        color = color
      )
      showAddDialog = false
    },
    onUpdateAccount = { name, type, bankName, cardNumber, accountNumber, iban, initialBalance, color ->
      accountViewModel.updateAccount(
        editingAccount!!.copy(
          name = name,
          type = type,
          bankName = bankName,
          cardNumber = cardNumber,
          accountNumber = accountNumber,
          iban = iban,
          initialBalance = initialBalance,
          color = color
        )
      )
      editingAccount = null
    },
    onDeleteAccount = { accountViewModel.deleteAccount(showDeleteConfirmation!!) },
    onEditFromOverflow = {
      editingAccount = overflowMenuAccount
      overflowMenuAccount = null
    },
    onArchiveFromOverflow = {
      accountViewModel.archiveAccount(overflowMenuAccount!!)
      overflowMenuAccount = null
    },
    onDeleteFromOverflow = {
      pendingDeleteAccount = overflowMenuAccount
      overflowMenuAccount = null
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManagementDialogs(
  showAddDialog: Boolean,
  editingAccount: AccountEntity?,
  showDeleteConfirmation: AccountEntity?,
  showTransactionWarning: AccountEntity?,
  overflowMenuAccount: AccountEntity?,
  onDismissAddDialog: () -> Unit,
  onDismissEditing: () -> Unit,
  onDismissDelete: () -> Unit,
  onDismissTransactionWarning: () -> Unit,
  onDismissOverflow: () -> Unit,
  onSaveAccount: (String, AccountType, String?, String?, String?, String?, Long, Long) -> Unit,
  onUpdateAccount: (String, AccountType, String?, String?, String?, String?, Long, Long) -> Unit,
  onDeleteAccount: () -> Unit,
  onEditFromOverflow: () -> Unit,
  onArchiveFromOverflow: () -> Unit,
  onDeleteFromOverflow: () -> Unit,
) {
  val account = overflowMenuAccount
  if (showAddDialog) {
    AccountDialog(
      initialAccount = null,
      onDismiss = onDismissAddDialog,
      onSave = onSaveAccount
    )
  }
  if (editingAccount != null) {
    AccountDialog(
      initialAccount = editingAccount,
      onDismiss = onDismissEditing,
      onSave = onUpdateAccount
    )
  }
  if (showDeleteConfirmation != null) {
    ConfirmDialog(
      title = "حذف حساب",
      message = "آیا از حذف حساب «${showDeleteConfirmation.name}» اطمینان دارید؟",
      confirmText = "حذف",
      dismissText = "انصراف",
      onDismiss = onDismissDelete,
      onConfirm = {
        onDeleteAccount()
        onDismissDelete()
      }
    )
  }
  if (showTransactionWarning != null) {
    TransactionWarningDialog(
      accountName = showTransactionWarning.name,
      onDismiss = onDismissTransactionWarning
    )
  }
  if (account != null) {
    AccountOverflowMenu(
      onDismiss = onDismissOverflow,
      onEdit = onEditFromOverflow,
      onArchive = onArchiveFromOverflow,
      onDelete = onDeleteFromOverflow
    )
  }
}

@Composable
private fun AccountManagementContent(
  accounts: List<AccountEntity>,
  modifier: Modifier,
  innerPadding: PaddingValues,
  onOverflowClick: (AccountEntity) -> Unit
) {
  if (accounts.isEmpty()) {
    Box(
      modifier = modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "هنوز حسابی ثبت نشده است.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  } else {
    LazyColumn(
      modifier = modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(bottom = 80.dp),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      items(accounts, key = { it.id }) { account ->
        AccountItem(account = account, onOverflow = onOverflowClick)
      }
      item { Spacer(modifier = Modifier.height(SpacingTokens.lg)) }
    }
  }
}

@Suppress("LongMethod")
@Composable
private fun AccountItem(
  account: AccountEntity,
  onOverflow: (AccountEntity) -> Unit
) {
  val typeIcon = ACCOUNT_TYPE_ICONS[account.type] ?: Icons.Filled.AccountBalance
  val accountColor = Color(account.color)

  HesabyarCard(
    modifier = Modifier.fillMaxWidth().padding(horizontal = SpacingTokens.lg),
    shape = ShapeTokens.Medium,
    contentPadding = PaddingValues(SpacingTokens.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      IconCircle(
        icon = typeIcon,
        tint = accountColor,
        backgroundColor = accountColor,
        containerSize = Dimens.AvatarMedium,
        iconSize = 20.dp
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = account.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = account.type.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = accountColor,
            fontWeight = FontWeight.Medium
          )
          account.bankName?.let { bankName ->
            Text(
              text = bankName,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Text(
          text = CurrencyFormatter.format(account.initialBalance),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Box {
        IconButton(
          onClick = { onOverflow(account) },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "گزینه‌های بیشتر",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun AccountOverflowMenu(
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onArchive: () -> Unit,
  onDelete: () -> Unit
) {
  DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
    DropdownMenuItem(
      text = { Text("ویرایش") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Filled.Edit,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
      },
      onClick = { onEdit() }
    )
    DropdownMenuItem(
      text = { Text("آرشیو") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Filled.Archive,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
      },
      onClick = { onArchive() }
    )
    DropdownMenuItem(
      text = { Text("حذف") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.error
        )
      },
      onClick = { onDelete() }
    )
  }
}

@Composable
private fun TransactionWarningDialog(
  accountName: String,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "امکان حذف حساب",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Text(
        text =
          "حساب «$accountName» دارای تراکنش‌های فعال است " +
            "و امکان حذف آن وجود ندارد. برای غیرفعال کردن حساب، " +
            "از گزینه آرشیو استفاده کنید."
      )
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(text = "متوجه شدم")
      }
    },
    dismissButton = null
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDialog(
  initialAccount: AccountEntity?,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    type: AccountType,
    bankName: String?,
    cardNumber: String?,
    accountNumber: String?,
    iban: String?,
    initialBalance: Long,
    color: Long
  ) -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (initialAccount != null) "ویرایش حساب" else "افزودن حساب جدید",
        fontWeight = FontWeight.Bold
      )
    },
    text = { AccountDialogForm(initialAccount = initialAccount, onSave = onSave) },
    confirmButton = {},
    dismissButton = {
      HesabyarButton(onClick = onDismiss, text = "انصراف", variant = ButtonVariant.Text)
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
private fun AccountDialogForm(
  initialAccount: AccountEntity?,
  onSave: (
    name: String,
    type: AccountType,
    bankName: String?,
    cardNumber: String?,
    accountNumber: String?,
    iban: String?,
    initialBalance: Long,
    color: Long
  ) -> Unit
) {
  var name by remember { mutableStateOf(initialAccount?.name.orEmpty()) }
  var selectedType by remember { mutableStateOf(initialAccount?.type ?: AccountType.BANK) }
  var bankName by remember { mutableStateOf(initialAccount?.bankName.orEmpty()) }
  var cardNumber by remember { mutableStateOf(initialAccount?.cardNumber.orEmpty()) }
  var accountNumber by remember { mutableStateOf(initialAccount?.accountNumber.orEmpty()) }
  var iban by remember { mutableStateOf(initialAccount?.iban.orEmpty()) }
  var initialBalance by remember { mutableStateOf(initialAccount?.initialBalance?.toString() ?: "0") }
  var selectedColor by remember { mutableStateOf(initialAccount?.color ?: DEFAULT_ACCOUNT_COLOR) }
  var typeDropdownExpanded by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    HesabyarInputField(
      value = name,
      onValueChange = { name = it },
      label = "نام حساب",
      placeholder = "مثلاً: حساب جاری بانک",
      shape = ShapeTokens.Medium,
      singleLine = true,
      isError = name.isBlank()
    )
    AccountDialogTypeField(
      selectedType = selectedType,
      onTypeSelected = { selectedType = it },
      dropdownExpanded = typeDropdownExpanded,
      onDropdownExpandedChange = { typeDropdownExpanded = it }
    )
    AccountDialogBankDetailsFields(
      bankName = bankName,
      onBankNameChange = { bankName = it },
      cardNumber = cardNumber,
      onCardNumberChange = { cardNumber = it },
      accountNumber = accountNumber,
      onAccountNumberChange = { accountNumber = it },
      iban = iban,
      onIbanChange = { iban = it }
    )
    HesabyarInputField(
      value = initialBalance,
      onValueChange = { initialBalance = it },
      label = "موجودی اولیه (ریال)",
      placeholder = "0",
      shape = ShapeTokens.Medium,
      singleLine = true,
      keyboardOptions =
        androidx.compose.foundation.text
          .KeyboardOptions(keyboardType = KeyboardType.Number),
      visualTransformation = VisualTransformation.None,
      supportingText = "مبلغ ریال"
    )
    AccountDialogColorPicker(
      selectedColor = selectedColor,
      onColorSelected = { selectedColor = it }
    )
    AccountDialogPreviewRow(name = name, selectedType = selectedType, selectedColor = selectedColor)
    HesabyarButton(
      onClick = {
        val trimmedName = name.trim()
        val trimmedBalance = initialBalance.trim()
        val balanceLong = trimmedBalance.toLongOrNull() ?: 0L
        onSave(
          trimmedName,
          selectedType,
          bankName.trim().ifBlank { null },
          cardNumber.trim().ifBlank { null },
          accountNumber.trim().ifBlank { null },
          iban.trim().ifBlank { null },
          balanceLong,
          selectedColor
        )
      },
      text = "ذخیره",
      variant = ButtonVariant.Filled,
      enabled = name.isNotBlank()
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDialogTypeField(
  selectedType: AccountType,
  onTypeSelected: (AccountType) -> Unit,
  dropdownExpanded: Boolean,
  onDropdownExpandedChange: (Boolean) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
    Text(
      text = "نوع حساب:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ExposedDropdownMenuBox(
      expanded = dropdownExpanded,
      onExpandedChange = onDropdownExpandedChange
    ) {
      OutlinedTextField(
        value = selectedType.displayName,
        onValueChange = {},
        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        readOnly = true,
        shape = ShapeTokens.Medium,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) }
      )
      ExposedDropdownMenu(
        expanded = dropdownExpanded,
        onDismissRequest = { onDropdownExpandedChange(false) }
      ) {
        AccountType.entries.forEach { type ->
          DropdownMenuItem(
            text = { Text(type.displayName) },
            onClick = {
              onTypeSelected(type)
              onDropdownExpandedChange(false)
            }
          )
        }
      }
    }
  }
}

@Composable
private fun AccountDialogBankDetailsFields(
  bankName: String,
  onBankNameChange: (String) -> Unit,
  cardNumber: String,
  onCardNumberChange: (String) -> Unit,
  accountNumber: String,
  onAccountNumberChange: (String) -> Unit,
  iban: String,
  onIbanChange: (String) -> Unit,
) {
  HesabyarInputField(
    value = bankName,
    onValueChange = onBankNameChange,
    label = "نام بانک (اختیاری)",
    placeholder = "مثلاً: بانک ملی",
    shape = ShapeTokens.Medium,
    singleLine = true
  )
  HesabyarInputField(
    value = cardNumber,
    onValueChange = onCardNumberChange,
    label = "شماره کارت (اختیاری)",
    placeholder = "مثلاً: 603799...",
    shape = ShapeTokens.Medium,
    singleLine = true,
    keyboardOptions =
      androidx.compose.foundation.text
        .KeyboardOptions(keyboardType = KeyboardType.Number)
  )
  HesabyarInputField(
    value = accountNumber,
    onValueChange = onAccountNumberChange,
    label = "شماره حساب (اختیاری)",
    placeholder = "شماره حساب بانکی",
    shape = ShapeTokens.Medium,
    singleLine = true
  )
  HesabyarInputField(
    value = iban,
    onValueChange = onIbanChange,
    label = "شماره IBAN (اختیاری)",
    placeholder = "مثلا: IR...",
    shape = ShapeTokens.Medium,
    singleLine = true
  )
}

@Composable
private fun AccountDialogColorPicker(
  selectedColor: Long,
  onColorSelected: (Long) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
    Text(
      text = "رنگ:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LazyVerticalGrid(
      columns = GridCells.Fixed(COLOR_PICKER_COLUMNS),
      modifier = Modifier.height(60.dp),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
    ) {
      items(ACCOUNT_COLORS) { color ->
        Box(
          modifier =
            Modifier
              .size(
                28.dp
              ).clip(CircleShape)
              .background(Color(color))
              .clickable { onColorSelected(color) },
          contentAlignment = Alignment.Center
        ) {
          if (selectedColor == color) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(Dimens.IconSmall)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AccountDialogPreviewRow(
  name: String,
  selectedType: AccountType,
  selectedColor: Long
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(
          ShapeTokens.Medium
        ).background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(SpacingTokens.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    val previewIcon = ACCOUNT_TYPE_ICONS[selectedType] ?: Icons.Filled.AccountBalance
    val previewColor = Color(selectedColor)
    IconCircle(
      icon = previewIcon,
      tint = previewColor,
      backgroundColor = previewColor,
      containerSize = Dimens.AvatarMedium,
      iconSize = 20.dp
    )
    Column {
      Text(
        text = name.ifBlank { "نام حساب" },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
      )
      Text(text = selectedType.displayName, style = MaterialTheme.typography.labelSmall, color = previewColor)
    }
  }
}
