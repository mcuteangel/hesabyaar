# Phase 2: State Architecture (Event, UiState, ViewModel Refactor)

## Prerequisites

- **Previous phase:** Phase 1 must be complete (the use cases are ready)
- **Pending decisions:** No new decisions — all were resolved in Phase 0/1

## Context

The current `AccountViewModel` has scattered state: `accounts` StateFlow in the ViewModel, and `dialogState` plus form state in the composable. There is no loading, no error, and no side effect management. This phase introduces a central and testable state architecture.

## Exact Goal of This Phase

Create `AccountUiState`, `AccountEvent`, and `AccountSideEffect`, then rewrite `AccountViewModel` to use this pattern. At the end, the ViewModel must be the single source of truth for all state, and the UI only renders the state.

## Files Involved

### New files
| File | Description |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountUiState.kt` | State models |
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountEvent.kt` | Event sealed class |
| `app/src/test/java/io/github/mojri/hesabyar/ui/AccountViewModelTest.kt` | Test the ViewModel |

### Files to edit
| File | Change |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountViewModel.kt` | Full rewrite with the event-based pattern |

## Execution Steps

### Step 2.1: Create AccountEvent

```kotlin
sealed interface AccountEvent {
  // List
  data object LoadAccounts : AccountEvent
  data class OnAccountOverflow(val account: AccountEntity) : AccountEvent

  // Add
  data object OnAddAccount : AccountEvent
  data class OnSaveNewAccount(val form: AccountFormState) : AccountEvent

  // Edit
  data class OnEditAccount(val account: AccountEntity) : AccountEvent
  data class OnSaveEditedAccount(val account: AccountEntity, val form: AccountFormState) : AccountEvent

  // Delete
  data class OnRequestDelete(val account: AccountEntity) : AccountEvent
  data class OnConfirmDelete(val account: AccountEntity) : AccountEvent

  // Archive
  data class OnRequestArchive(val account: AccountEntity) : AccountEvent
  data class OnConfirmArchive(val account: AccountEntity) : AccountEvent
  data class OnUnarchiveAccount(val account: AccountEntity) : AccountEvent

  // Form
  data class OnFormChange(val form: AccountFormState) : AccountEvent

  // UI
  data object OnDismissDialog : AccountEvent
}
```

### Step 2.2: Create AccountUiState

```kotlin
data class AccountUiState(
  val accounts: List<AccountEntity> = emptyList(),
  val dialogState: AccountDialogState = AccountDialogState.None,
  val formState: AccountFormState = AccountFormState(),
  val isLoading: Boolean = false,
  val isSaving: Boolean = false,
)

data class AccountFormState(
  val name: String = "",
  val type: AccountType = AccountType.BANK,
  val bankName: String = "",
  val cardNumber: String = "",
  val accountNumber: String = "",
  val iban: String = "",
  val initialBalance: String = "0",
  val color: Long = AccountEntity.DEFAULT_COLOR,
  val errors: Map<String, String> = emptyMap(),
)

sealed interface AccountDialogState {
  data object None : AccountDialogState
  data object Add : AccountDialogState
  data class Edit(val account: AccountEntity) : AccountDialogState
  data class DeleteConfirmation(val account: AccountEntity) : AccountDialogState
  data class TransactionWarning(val account: AccountEntity) : AccountDialogState
  data class ArchiveConfirmation(val account: AccountEntity) : AccountDialogState
  data class PendingDelete(val account: AccountEntity) : AccountDialogState
  data class OverflowMenu(val account: AccountEntity) : AccountDialogState
}

sealed interface AccountSideEffect {
  data class ShowSnackbar(val message: String, val actionLabel: String? = null) : AccountSideEffect
}
```

### Step 2.3: Rewrite AccountViewModel

- The `onEvent(event: AccountEvent)` main method
- `_uiState: MutableStateFlow<AccountUiState>` → `uiState: StateFlow<AccountUiState>`
- `_sideEffect: Channel<AccountSideEffect>` → `sideEffect: Flow<AccountSideEffect>`
- Use `SavedStateHandle` for `formState` (if process death matters)

**General structure:**
```kotlin
@HiltViewModel
class AccountViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val addAccountUseCase: AddAccountUseCase,
  private val updateAccountUseCase: UpdateAccountUseCase,
  private val deleteAccountUseCase: DeleteAccountUseCase,
  private val archiveAccountUseCase: ArchiveAccountUseCase,
  private val getAccountsUseCase: GetAccountsUseCase,
  private val accountValidator: AccountValidator,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val _uiState = MutableStateFlow(AccountUiState())
  val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

  // BUFFERED: the Screen does not collect side effects until Phase 4;
  // a rendezvous channel would suspend the ViewModel on send
  private val _sideEffect = Channel<AccountSideEffect>(Channel.BUFFERED)
  val sideEffect: Flow<AccountSideEffect> = _sideEffect.receiveAsFlow()

  init {
    // Collect accounts from GetAccountsUseCase
  }

  fun onEvent(event: AccountEvent) {
    when (event) {
      // handle each event
    }
  }
}
```

**Side effect management:**
- Emit `AccountSideEffect.ShowSnackbar(message)` after each CRUD operation (one-shot flow; the Screen collects it in Phase 4)
- There is no `snackbarMessage` state — success and error delivery go only through the side-effect channel

### Step 2.4: Add error handling

- Put all the use case calls in `try-catch`
- On error, emit a localized message via `AccountSideEffect.ShowSnackbar(context.getString(R.string.account_operation_error))` through `_sideEffect` (add the string resource; do not hard-code user-visible text)
- Set `isSaving = false` in the finally block

### Step 2.5: Write the ViewModel tests

```kotlin
class AccountViewModelTest {
  // Setup: FakeUseCases, FakeValidator

  @Test fun onAddAccount_showsDialog()
  @Test fun onSaveNewAccount_validForm_insertsAndShowsSnackbar()
  @Test fun onSaveNewAccount_invalidForm_showsErrors()
  @Test fun onDeleteRequest_withTransactions_showsWarning()
  @Test fun onDeleteRequest_withoutTransactions_showsConfirmation()
  @Test fun onConfirmDelete_deletesAndShowsSnackbar()
  @Test fun onConfirmArchive_archivesAndShowsSnackbar()
  @Test fun onFormChange_updatesFormState()
  @Test fun onDismissDialog_clearsDialogState()
}
```

## Special Notes for This Phase

- **Important:** In this phase, the Screen still uses the old state (`dialogState` as `remember`). The Screen refactor happens in Phase 4. This phase only prepares the ViewModel.
- **Compatibility:** The new ViewModel must still export the old API (`accounts: StateFlow<List<AccountEntity>>`) so that the old Screen works. Later, in Phase 4, the Screen switches to `uiState`.
- **Channel vs SharedFlow:** Use `Channel` for side effects (not `SharedFlow`), because each message is consumed only once.

## Acceptance Criteria

- [ ] `AccountEvent.kt` is defined with all the events
- [ ] `AccountUiState.kt` is defined with all the state models
- [ ] `AccountViewModel` has an `onEvent()` method
- [ ] `AccountViewModelTest` → all tests pass
- [ ] The old ViewModel API (`accounts`) still works
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → no errors

## Rollback

```bash
git log --oneline -5  # Phase 2 commits
git revert <commits>
# Or
git checkout HEAD -- app/src/main/java/.../ui/AccountViewModel.kt
rm app/src/main/java/.../ui/AccountUiState.kt app/src/main/java/.../ui/AccountEvent.kt
```
