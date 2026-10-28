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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import io.github.mojri.hesabyar.ui.components.HesabyarFab
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.HesabyarScaffold
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.icon
import io.github.mojri.hesabyar.ui.designsystem.ACCOUNT_PICKER_COLORS
import io.github.mojri.hesabyar.ui.designsystem.DEFAULT_ACCOUNT_COLOR
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor
import io.github.mojri.hesabyar.ui.utils.DisplayAmountInput
import kotlinx.coroutines.launch

private const val COLOR_PICKER_COLUMNS = 8

private data class AccountFormData(
  val name: String,
  val type: AccountType,
  val bankName: String?,
  val cardNumber: String?,
  val accountNumber: String?,
  val iban: String?,
  val initialBalance: Long,
  val color: Long,
)

internal sealed interface AccountDialogState {
  data object None : AccountDialogState

  data class Add(
    val instanceId: Long = System.nanoTime()
  ) : AccountDialogState

  data class Edit(
    val account: AccountEntity
  ) : AccountDialogState

  data class DeleteConfirmation(
    val account: AccountEntity
  ) : AccountDialogState

  data class TransactionWarning(
    val account: AccountEntity
  ) : AccountDialogState

  data class LastAccountWarning(
    val account: AccountEntity
  ) : AccountDialogState

  data class DeleteCheckError(
    val account: AccountEntity,
    val message: String
  ) : AccountDialogState

  data class PendingDelete(
    val account: AccountEntity
  ) : AccountDialogState
}

/**
 * Maps a [AccountViewModel.DeleteCheckResult] to the dialog state to show.
 * The ViewModel reports the concrete deletion-block reason, so the UI never
 * has to infer it from the account list (which could conflate a repository
 * failure with the transaction-block case).
 */
internal fun resolveDeleteDialogState(
  result: AccountViewModel.DeleteCheckResult,
  account: AccountEntity
): AccountDialogState =
  when (result) {
    AccountViewModel.DeleteCheckResult.CanDelete -> AccountDialogState.DeleteConfirmation(account)
    AccountViewModel.DeleteCheckResult.LastActiveAccount -> AccountDialogState.LastAccountWarning(account)
    AccountViewModel.DeleteCheckResult.HasTransactions -> AccountDialogState.TransactionWarning(account)
    is AccountViewModel.DeleteCheckResult.CheckFailed -> AccountDialogState.DeleteCheckError(account, result.message)
  }

/** No account form dialog survives recreation. */
private const val NO_FORM_DIALOG_ID = -1L

/** The add-account form dialog survives recreation. */
private const val ADD_FORM_DIALOG_ID = -2L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
  accountViewModel: AccountViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val accounts by accountViewModel.accounts.collectAsState()
  var formDialogTargetId by rememberSaveable { mutableStateOf(NO_FORM_DIALOG_ID) }
  var dialogState by remember {
    mutableStateOf<AccountDialogState>(
      if (formDialogTargetId == ADD_FORM_DIALOG_ID) {
        AccountDialogState.Add()
      } else {
        AccountDialogState.None
      }
    )
  }
  val scope = rememberCoroutineScope()

  val currentDialog = dialogState
  if (currentDialog is AccountDialogState.PendingDelete) {
    val pendingAccount = currentDialog.account
    LaunchedEffect(pendingAccount) {
      accountViewModel.canDeleteAccount(pendingAccount.id) { result ->
        val current = dialogState
        if (current is AccountDialogState.PendingDelete && current.account.id == pendingAccount.id) {
          dialogState = resolveDeleteDialogState(result, pendingAccount)
        }
      }
    }
  }

  // Replays a restored form dialog once accounts load. The target survives
  // recreation. The dialog itself does not. This effect reopens the dialog.
  LaunchedEffect(formDialogTargetId, accounts) {
    if (formDialogTargetId == NO_FORM_DIALOG_ID) return@LaunchedEffect
    if (dialogState is AccountDialogState.Add || dialogState is AccountDialogState.Edit) return@LaunchedEffect
    if (formDialogTargetId == ADD_FORM_DIALOG_ID) {
      dialogState = AccountDialogState.Add()
    } else {
      val target = accounts.find { it.id == formDialogTargetId }
      if (target != null) {
        dialogState = AccountDialogState.Edit(target)
      } else if (accounts.isNotEmpty()) {
        formDialogTargetId = NO_FORM_DIALOG_ID
      }
    }
  }

  AccountManagementScaffoldSection(
    accounts = accounts,
    dialogState = dialogState,
    modifier = modifier,
    accountViewModel = accountViewModel,
    onBack = onBack,
    onDialogStateChange = { newState ->
      dialogState = newState
      formDialogTargetId =
        when (newState) {
          is AccountDialogState.Add -> ADD_FORM_DIALOG_ID
          is AccountDialogState.Edit -> newState.account.id
          else -> NO_FORM_DIALOG_ID
        }
    },
    scope = scope,
    currentDialogState = { dialogState }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManagementScaffoldSection(
  accounts: List<AccountEntity>,
  dialogState: AccountDialogState,
  modifier: Modifier,
  accountViewModel: AccountViewModel,
  onBack: () -> Unit,
  onDialogStateChange: (AccountDialogState) -> Unit,
  scope: kotlinx.coroutines.CoroutineScope,
  currentDialogState: () -> AccountDialogState = { dialogState },
) {
  HesabyarScaffold(
    modifier = modifier,
    title = "مدیریت حساب‌ها",
    onBack = onBack,
    floatingActionButton = {
      HesabyarFab(
        onClick = { onDialogStateChange(AccountDialogState.Add()) },
        contentDescription = stringResource(id = io.github.mojri.hesabyar.R.string.add_account)
      )
    }
  ) { contentPadding ->
    AccountManagementContent(
      accounts = accounts,
      contentPadding = contentPadding,
      onOverflowEdit = { onDialogStateChange(AccountDialogState.Edit(it)) },
      onOverflowArchive = { accountViewModel.archiveAccount(it) },
      onOverflowDelete = { onDialogStateChange(AccountDialogState.PendingDelete(it)) },
    )
  }

  AccountManagementSaveDialogs(
    dialogState = dialogState,
    accountViewModel = accountViewModel,
    onDialogStateChange = onDialogStateChange,
    scope = scope,
    currentDialogState = currentDialogState
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManagementSaveDialogs(
  dialogState: AccountDialogState,
  accountViewModel: AccountViewModel,
  onDialogStateChange: (AccountDialogState) -> Unit,
  scope: kotlinx.coroutines.CoroutineScope,
  currentDialogState: () -> AccountDialogState,
) {
  // Blocks a second save while addAccount runs.
  // The code sets this flag on the main thread before it launches the save.
  // The finally block enables Save again after success, failure, or cancellation.
  var isSavingAccount by remember { mutableStateOf(false) }

  AccountManagementDialogs(
    dialogState = dialogState,
    isSavingAccount = isSavingAccount,
    onDismiss = { onDialogStateChange(AccountDialogState.None) },
    onSaveAccount = { form ->
      if (!isSavingAccount) {
        isSavingAccount = true
        val initiatingDialog = currentDialogState()
        scope.launch {
          try {
            val result =
              accountViewModel.addAccount(
                name = form.name,
                type = form.type,
                bankName = form.bankName,
                cardNumber = form.cardNumber,
                accountNumber = form.accountNumber,
                iban = form.iban,
                initialBalance = form.initialBalance,
                color = form.color
              )
            // Close only the dialog this save started from. A later dialog,
            // opened while the insert was suspended, must stay open.
            if (result is AccountViewModel.AddAccountResult.Success && currentDialogState() == initiatingDialog) {
              onDialogStateChange(AccountDialogState.None)
            }
          } finally {
            isSavingAccount = false
          }
        }
      }
    },
    onUpdateAccount = { account, form ->
      accountViewModel.updateAccount(
        account.copy(
          name = form.name,
          type = form.type,
          bankName = form.bankName,
          cardNumber = form.cardNumber,
          accountNumber = form.accountNumber,
          iban = form.iban,
          initialBalance = form.initialBalance,
          color = form.color
        )
      )
      onDialogStateChange(AccountDialogState.None)
    },
    onDeleteAccount = { account -> accountViewModel.deleteAccount(account) },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManagementDialogs(
  dialogState: AccountDialogState,
  onDismiss: () -> Unit,
  onSaveAccount: (AccountFormData) -> Unit,
  onUpdateAccount: (AccountEntity, AccountFormData) -> Unit,
  onDeleteAccount: (AccountEntity) -> Unit,
  isSavingAccount: Boolean = false,
) {
  when (dialogState) {
    AccountDialogState.None -> {}
    is AccountDialogState.Add ->
      AccountDialog(
        initialAccount = null,
        onDismiss = onDismiss,
        onSave = onSaveAccount,
        isSaving = isSavingAccount
      )
    is AccountDialogState.Edit ->
      key(dialogState.account.id) {
        AccountDialog(
          initialAccount = dialogState.account,
          onDismiss = onDismiss,
          onSave = { form -> onUpdateAccount(dialogState.account, form) }
        )
      }
    is AccountDialogState.DeleteConfirmation ->
      DeleteAccountConfirmDialog(
        account = dialogState.account,
        onDismiss = onDismiss,
        onConfirmDelete = onDeleteAccount
      )
    is AccountDialogState.TransactionWarning ->
      AccountDeleteWarningDialog(
        message =
          stringResource(
            id = io.github.mojri.hesabyar.R.string.account_delete_transaction_warning_message,
            dialogState.account.name
          ),
        onDismiss = onDismiss
      )
    is AccountDialogState.LastAccountWarning ->
      AccountDeleteWarningDialog(
        message =
          stringResource(
            id = io.github.mojri.hesabyar.R.string.account_delete_last_active_account_warning_message,
            dialogState.account.name
          ),
        onDismiss = onDismiss
      )
    is AccountDialogState.DeleteCheckError ->
      AccountDeleteWarningDialog(
        message = dialogState.message,
        onDismiss = onDismiss
      )
    is AccountDialogState.PendingDelete -> {}
  }
}

@Composable
private fun DeleteAccountConfirmDialog(
  account: AccountEntity,
  onDismiss: () -> Unit,
  onConfirmDelete: (AccountEntity) -> Unit
) {
  ConfirmDialog(
    title = stringResource(id = io.github.mojri.hesabyar.R.string.account_delete_title),
    message =
      stringResource(
        id = io.github.mojri.hesabyar.R.string.account_delete_confirm_message,
        account.name
      ),
    confirmText = stringResource(id = io.github.mojri.hesabyar.R.string.delete_label),
    dismissText = stringResource(id = io.github.mojri.hesabyar.R.string.cancel_label),
    onDismiss = onDismiss,
    onConfirm = {
      onConfirmDelete(account)
      onDismiss()
    }
  )
}

/**
 * Warning shown when a delete is refused — either it has active transactions,
 * it is the last remaining account, or the pre-check itself failed. Only the
 * [message] differs between the cases.
 */
@Composable
private fun AccountDeleteWarningDialog(
  message: String,
  onDismiss: () -> Unit
) {
  ConfirmDialog(
    title = stringResource(id = io.github.mojri.hesabyar.R.string.account_delete_not_allowed_title),
    message = message,
    confirmText = stringResource(id = io.github.mojri.hesabyar.R.string.understood_label),
    dismissText = "",
    onConfirm = onDismiss,
    onDismiss = onDismiss,
    confirmColor = MaterialTheme.colorScheme.primary
  )
}

@Composable
private fun AccountManagementContent(
  accounts: List<AccountEntity>,
  contentPadding: PaddingValues,
  onOverflowEdit: (AccountEntity) -> Unit,
  onOverflowArchive: (AccountEntity) -> Unit,
  onOverflowDelete: (AccountEntity) -> Unit,
) {
  if (accounts.isEmpty()) {
    Box(
      modifier = Modifier.fillMaxSize().padding(contentPadding),
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
      modifier = Modifier.fillMaxSize(),
      contentPadding = contentPadding,
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      items(accounts, key = { it.id }) { account ->
        AccountItem(
          account = account,
          onOverflowEdit = onOverflowEdit,
          onOverflowArchive = onOverflowArchive,
          onOverflowDelete = onOverflowDelete,
        )
      }
      item { Spacer(modifier = Modifier.height(SpacingTokens.lg)) }
    }
  }
}

@Suppress("LongMethod")
@Composable
private fun AccountItem(
  account: AccountEntity,
  onOverflowEdit: (AccountEntity) -> Unit,
  onOverflowArchive: (AccountEntity) -> Unit,
  onOverflowDelete: (AccountEntity) -> Unit,
) {
  val typeIcon = account.type.icon()
  val accountColor = account.color.toComposeColor()
  var menuExpanded by remember { mutableStateOf(false) }

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
        iconSize = Dimens.IconAvatarMedium
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
          onClick = { menuExpanded = true },
          modifier = Modifier.size(Dimens.ButtonHeight)
        ) {
          Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "گزینه‌های بیشتر",
            modifier = Modifier.size(Dimens.IconMenu),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        AccountOverflowMenu(
          expanded = menuExpanded,
          onDismiss = { menuExpanded = false },
          onEdit = {
            menuExpanded = false
            onOverflowEdit(account)
          },
          onArchive = {
            menuExpanded = false
            onOverflowArchive(account)
          },
          onDelete = {
            menuExpanded = false
            onOverflowDelete(account)
          },
        )
      }
    }
  }
}

@Composable
private fun AccountOverflowMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onArchive: () -> Unit,
  onDelete: () -> Unit
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    DropdownMenuItem(
      text = { Text("ویرایش") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Filled.Edit,
          contentDescription = null,
          modifier = Modifier.size(Dimens.IconMenu)
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
          modifier = Modifier.size(Dimens.IconMenu)
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
          modifier = Modifier.size(Dimens.IconMenu),
          tint = MaterialTheme.colorScheme.error
        )
      },
      onClick = { onDelete() }
    )
  }
}

private class AccountDialogFormState(
  val nameState: MutableState<String>,
  val selectedTypeState: MutableState<AccountType>,
  val bankNameState: MutableState<String>,
  val cardNumberState: MutableState<String>,
  val accountNumberState: MutableState<String>,
  val ibanState: MutableState<String>,
  val initialBalanceState: MutableState<String>,
  val selectedColorState: MutableState<Long>,
  val prefilledBalance: String,
)

@Composable
private fun rememberAccountDialogFormState(initialAccount: AccountEntity?): AccountDialogFormState {
  val nameState = rememberSaveable { mutableStateOf(initialAccount?.name.orEmpty()) }
  val selectedTypeState = rememberSaveable { mutableStateOf(initialAccount?.type ?: AccountType.BANK) }
  val bankNameState = rememberSaveable { mutableStateOf(initialAccount?.bankName.orEmpty()) }
  val cardNumberState = rememberSaveable { mutableStateOf(initialAccount?.cardNumber.orEmpty()) }
  val accountNumberState = rememberSaveable { mutableStateOf(initialAccount?.accountNumber.orEmpty()) }
  val ibanState = rememberSaveable { mutableStateOf(initialAccount?.iban.orEmpty()) }
  val prefilledBalance =
    remember { initialAccount?.initialBalance?.let { DisplayAmountInput.toDisplayText(it) } ?: "0" }
  val initialBalanceState = rememberSaveable { mutableStateOf(prefilledBalance) }
  val selectedColorState = rememberSaveable { mutableStateOf(initialAccount?.color ?: DEFAULT_ACCOUNT_COLOR) }

  return remember(initialAccount) {
    AccountDialogFormState(
      nameState = nameState,
      selectedTypeState = selectedTypeState,
      bankNameState = bankNameState,
      cardNumberState = cardNumberState,
      accountNumberState = accountNumberState,
      ibanState = ibanState,
      initialBalanceState = initialBalanceState,
      selectedColorState = selectedColorState,
      prefilledBalance = prefilledBalance,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDialog(
  initialAccount: AccountEntity?,
  onDismiss: () -> Unit,
  onSave: (AccountFormData) -> Unit,
  isSaving: Boolean = false
) {
  val formState = rememberAccountDialogFormState(initialAccount)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (initialAccount != null) "ویرایش حساب" else "افزودن حساب جدید",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      AccountDialogForm(
        formState = formState,
        initialAccount = initialAccount,
        onSave = onSave,
        isSaving = isSaving
      )
    },
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
  formState: AccountDialogFormState,
  initialAccount: AccountEntity?,
  onSave: (AccountFormData) -> Unit,
  isSaving: Boolean = false
) {
  var name by formState.nameState
  var selectedType by formState.selectedTypeState
  var bankName by formState.bankNameState
  var cardNumber by formState.cardNumberState
  var accountNumber by formState.accountNumberState
  var iban by formState.ibanState
  var initialBalance by formState.initialBalanceState
  var selectedColor by formState.selectedColorState
  val prefilledBalance = formState.prefilledBalance
  val parsedBalance =
    DisplayAmountInput.resolveEditedRial(
      text = initialBalance,
      prefilledText = prefilledBalance,
      originalRial = initialAccount?.initialBalance
    )
  val balanceError = initialBalance.isNotBlank() && parsedBalance == null
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
      label =
        stringResource(
          id = io.github.mojri.hesabyar.R.string.opening_balance_label,
          CurrencyFormatter.unitLabel
        ),
      placeholder = "0",
      shape = ShapeTokens.Medium,
      singleLine = true,
      keyboardOptions =
        androidx.compose.foundation.text
          .KeyboardOptions(keyboardType = KeyboardType.Number),
      visualTransformation = VisualTransformation.None,
      supportingText = {
        Text(
          text =
            if (balanceError) {
              stringResource(
                id = io.github.mojri.hesabyar.R.string.balance_invalid_amount
              )
            } else {
              stringResource(
                id = io.github.mojri.hesabyar.R.string.balance_amount_label,
                CurrencyFormatter.unitLabel
              )
            }
        )
      },
      isError = balanceError
    )
    AccountDialogColorPicker(
      selectedColor = selectedColor,
      onColorSelected = { selectedColor = it }
    )
    AccountDialogPreviewRow(name = name, selectedType = selectedType, selectedColor = selectedColor)
    HesabyarButton(
      onClick = {
        onSave(
          AccountFormData(
            name = name.trim(),
            type = selectedType,
            bankName = bankName.trim().ifBlank { null },
            cardNumber = cardNumber.trim().ifBlank { null },
            accountNumber = accountNumber.trim().ifBlank { null },
            iban = iban.trim().ifBlank { null },
            initialBalance = parsedBalance ?: 0L,
            color = selectedColor
          )
        )
      },
      text = "ذخیره",
      variant = ButtonVariant.Filled,
      enabled = name.isNotBlank() && !balanceError && !isSaving
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
      HesabyarInputField(
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
    val colorRows = ACCOUNT_PICKER_COLORS.chunked(COLOR_PICKER_COLUMNS)
    Column(
      modifier = Modifier.height(60.dp),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
    ) {
      colorRows.forEach { rowColors ->
        Row(
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
        ) {
          rowColors.forEach { color ->
            Box(
              modifier =
                Modifier
                  .size(Dimens.ContainerXSmall)
                  .clip(CircleShape)
                  .background(color.toComposeColor())
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
          repeat(COLOR_PICKER_COLUMNS - rowColors.size) {
            Spacer(modifier = Modifier.size(Dimens.ContainerXSmall))
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
    val previewIcon = selectedType.icon()
    val previewColor = selectedColor.toComposeColor()
    IconCircle(
      icon = previewIcon,
      tint = previewColor,
      backgroundColor = previewColor,
      containerSize = Dimens.AvatarMedium,
      iconSize = Dimens.IconAvatarMedium
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
