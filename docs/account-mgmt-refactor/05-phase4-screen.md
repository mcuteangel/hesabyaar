# Phase 4: Rewrite the Screen as Shell + Dialogs

## Prerequisites

- **Previous phases:** Phase 2 must be complete (the ViewModel is event-based), and Phase 3 must be complete (the components are extracted)
- **Pending decisions:** None

## Context

`AccountManagementScreen.kt` is smaller after Phase 3, but it still uses the old state management (`remember { mutableStateOf }`). `AccountDialogState` is still local. This phase converts the Screen to a ViewModel-connected shell, and it completes the state management.

## Exact Goal of This Phase

Connect `AccountManagementScreen` to `AccountUiState` from the ViewModel, remove the local state, and create separate dialogs. At the end:
- The Screen is at most 100 lines (shell only)
- All the state comes from the ViewModel
- `dialogState` is collected from the ViewModel
- `formState` comes from the ViewModel
- The side effect (Snackbar) is managed by the ViewModel

## Files Involved

### New files
| File | Description |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountFormDialog.kt` | Add/edit dialog |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountDeleteDialog.kt` | Delete dialog |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountArchiveDialog.kt` | Archive dialog |

### File to edit
| File | Change |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | Full rewrite to a simple shell |

## Execution Steps

### Step 4.1: Rewrite AccountManagementScreen

**New structure:**
```kotlin
@Composable
fun AccountManagementScreen(
  accountViewModel: AccountViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by accountViewModel.uiState.collectAsState()

  // SideEffect collection
  LaunchedEffect(Unit) {
    accountViewModel.sideEffect.collect { effect ->
      when (effect) {
        is AccountSideEffect.ShowSnackbar -> { /* show snackbar */ }
      }
    }
  }

  Scaffold(
    topBar = { /* TopAppBar */ },
    floatingActionButton = { /* FAB → onEvent(OnAddAccount) */ }
  ) { innerPadding ->
    AccountManagementContent(
      accounts = uiState.accounts,
      modifier = modifier,
      innerPadding = innerPadding,
      onOverflowClick = { accountViewModel.onEvent(OnAccountOverflow(it)) }
    )
  }

  // Dialog host
  when (val dialog = uiState.dialogState) {
    is AccountDialogState.None -> {}
    is AccountDialogState.Add -> AccountFormDialog(...)
    is AccountDialogState.Edit -> AccountFormDialog(...)
    is AccountDialogState.DeleteConfirmation -> AccountDeleteDialog(...)
    is AccountDialogState.TransactionWarning -> ConfirmDialog(...)
    is AccountDialogState.ArchiveConfirmation -> AccountArchiveDialog(...)
    is AccountDialogState.PendingDelete -> { /* LaunchedEffect for async check */ }
    is AccountDialogState.OverflowMenu -> AccountOverflowMenu(...)
  }
}
```

**Notes:**
- Take `AccountDialogState` from `AccountUiState` (not from `remember`)
- Remove the `dialogState` that uses `remember { mutableStateOf }`
- Remove the `formState` from `remember` — take it from the ViewModel

### Step 4.2: Create AccountFormDialog

```kotlin
@Composable
fun AccountFormDialog(
  isEdit: Boolean,
  formState: AccountFormState,
  onFormChange: (AccountFormState) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
  isSaving: Boolean
) {
  HesabyarDialog(
    title = if (isEdit) "ویرایش حساب" else "افزودن حساب جدید",
    onDismissRequest = onDismiss,
    showCloseButton = true,
    actions = {
      HesabyarButton(onClick = onDismiss, text = "انصراف", variant = ButtonVariant.Text)
      HesabyarButton(
        onClick = onSave,
        text = "ذخیره",
        variant = ButtonVariant.Filled,
        enabled = formState.name.isNotBlank() && !isSaving,
        loading = isSaving
      )
    }
  ) {
    AccountFormContent(formState = formState, onFormChange = onFormChange)
  }
}
```

### Step 4.3: Create AccountDeleteDialog

```kotlin
@Composable
fun AccountDeleteDialog(
  account: AccountEntity,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  ConfirmDialog(
    title = "حذف حساب",
    message = "آیا از حذف حساب «${account.name}» اطمینان دارید؟",
    confirmText = "حذف",
    dismissText = "انصراف",
    onConfirm = onConfirm,
    onDismiss = onDismiss
  )
}
```

### Step 4.4: Create AccountArchiveDialog

```kotlin
@Composable
fun AccountArchiveDialog(
  account: AccountEntity,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  ConfirmDialog(
    title = "آرشیو حساب",
    message = "آیا از آرشیو کردن حساب «${account.name}» اطمینان دارید؟ حساب از داشبورد حذف خواهد شد.",
    confirmText = "آرشیو",
    dismissText = "انصراف",
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    confirmColor = MaterialTheme.colorScheme.primary  // Not error — archive is not destructive
  )
}
```

### Step 4.5: Connect FormState to the ViewModel

- `AccountViewModel.onEvent(OnFormChange(newForm))` → `_uiState.update { it.copy(formState = newForm) }`
- `AccountFormDialog` → `onFormChange = { accountViewModel.onEvent(OnFormChange(it)) }`
- The fields read `formState.name`, `formState.type`, etc. (not `remember`)

### Step 4.6: Connect the Snackbar

```kotlin
// In AccountManagementScreen:
LaunchedEffect(Unit) {
  accountViewModel.sideEffect.collect { effect ->
    when (effect) {
      is AccountSideEffect.ShowSnackbar -> {
        // Explicit duration: destructive actions (delete/archive) stay 7 seconds
        // to match the undo window. SnackbarDuration has no 7s step, so show
        // Indefinite and auto-dismiss after 7_000 ms.
        launch { delay(7_000); snackbarHostState.currentSnackbarData?.dismiss() }
        snackbarHostState.showSnackbar(
          message = effect.message,
          actionLabel = effect.actionLabel,
          duration = SnackbarDuration.Indefinite,
        )
      }
    }
  }
}
```

### Step 4.7: Connect the OverflowMenu

```kotlin
// The OverflowMenu must be inside the Box that surrounds the IconButton (Phase 0 bug #1)
// Or use BoxScope
Box {
  IconButton(onClick = { onOverflow(account) }) {
    Icon(Icons.Filled.MoreVert, ...)
  }
  // Render the DropdownMenu here
}
```

**Note:** This step completes Phase 0 bug #1. If Phase 0 is done, only confirm that the anchoring is correct.

## Special Notes for This Phase

- From the central checklist:
  - **R2** (negative numbers): if `AccountFormDialog` shows a negative amount, use the LRM pattern
  - **R4** (grep): after the rewrite, grep to confirm that `remember { mutableStateOf<AccountDialogState> }` is not in the main file
- **Important:** Keep `LaunchedEffect(currentDialog.account)` for `PendingDelete` — this is the async check for `canDeleteAccount`
- The Snackbar duration for destructive actions (delete/archive) must be 7 seconds

## Acceptance Criteria

- [ ] `AccountManagementScreen.kt` is at most 100 lines
- [ ] `AccountFormDialog.kt`, `AccountDeleteDialog.kt`, and `AccountArchiveDialog.kt` exist
- [ ] `dialogState` is removed from `remember { mutableStateOf }` and is collected from `uiState.dialogState`
- [ ] `formState` is removed from `remember` and comes from `uiState.formState`
- [ ] A Snackbar is shown after the CRUD operations
- [ ] The OverflowMenu renders inside the `Box` that surrounds the `IconButton` (Phase 0 bug #1 finalized)
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → no errors
- [ ] Manual QA: add, edit, delete, and archive — all work

## Rollback

```bash
git log --oneline -10
git revert <phase-4-commits>
# Or
git checkout HEAD -- app/src/main/java/.../ui/screens/account/AccountManagementScreen.kt
rm app/src/main/java/.../ui/screens/account/AccountFormDialog.kt
rm app/src/main/java/.../ui/screens/account/AccountDeleteDialog.kt
rm app/src/main/java/.../ui/screens/account/AccountArchiveDialog.kt
```
