# Phase 5: UX Improvements

## Prerequisites

- **Previous phase:** Phase 4 must be complete (the Screen is rewritten, and the state comes from the ViewModel)
- **Pending decisions:** None

## Context

After Phase 4, the architecture is complete. This phase only adds UX improvements: restore an archived account, undo for delete/archive, and a better empty state. None of them needs an architecture change — they are only feature additions.

## Exact Goal of This Phase

Add 5 UX capabilities:
1. **Unarchive** — restore an archived account
2. **Undo Delete** — revert a delete from the Snackbar
3. **Undo Archive** — revert an archive from the Snackbar
4. **Empty state improvement** — an explanation and a CTA
5. **Progressive disclosure** — show the bank fields only for the bank type

## Files Involved

### New files
| File | Description |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/UnarchiveAccountUseCase.kt` | Restore an account |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/RestoreAccountUseCase.kt` | Re-insert a deleted account (undo) |

### Files to edit <!-- check-docs: planned -->
| File | Change |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountEvent.kt` | Add OnUnarchiveAccount |
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountViewModel.kt` | Process undo + unarchive |
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountUiState.kt` | Add lastDeletedAccount for undo |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | Better empty state + Snackbar undo |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountOverflowMenu.kt` | The "فعال‌سازی مجدد" option for an archived account |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountListCard.kt` | StatusBadge for an archived account |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountBankFields.kt` | Conditional rendering |

## Execution Steps

### Step 5.1: Unarchive

- `UnarchiveAccountUseCase`: `suspend fun invoke(account: AccountEntity)` → `account.copy(isArchived = false, updatedAt = now)` → update
- `AccountEvent.OnUnarchiveAccount(account)`
- In `AccountOverflowMenu`: if the account is archived, show the "فعال‌سازی مجدد" option (instead of "آرشیو")
- Add `AccountStatusBadge` to `AccountListCard` — only if `isArchived` is true

### Step 5.2: Undo Delete

- In `AccountUiState`: add `lastDeletedAccount: AccountEntity? = null`
- After the delete: set `lastDeletedAccount = account`, the snackbar message is "حساب «{name}» حذف شد", and actionLabel is "واگردانی"
- In the Screen: if the snackbar action "واگردانی" is clicked → `OnUndoDelete` event
- `OnUndoDelete`: `lastDeletedAccount?.let { restoreAccountUseCase(it) }` → `lastDeletedAccount = null`. `RestoreAccountUseCase` (`suspend operator fun invoke(account: AccountEntity)`) re-inserts the cached entity through the repository, preserving all its fields (`isArchived`, display order, and the generated ID) — `AddAccountUseCase` takes `AccountFormModel` and cannot restore them
- **Note:** for undo, cache the account completely before the delete. `lastDeletedAccount` does this.
- **Limitation:** only the last delete can be undone (a 7 second window)

### Step 5.3: Undo Archive

- Similar to Undo Delete
- In `AccountUiState`: `lastArchivedAccount: AccountEntity? = null`
- After the archive: show a snackbar, and actionLabel is "واگردانی"
- `OnUndoArchive`: `lastArchivedAccount?.let { unarchiveAccountUseCase(it) }`

### Step 5.4: Improve the empty state

- Use the `EmptyState` component (from `ui/components/EmptyState.kt` — it already exists)
- Content:
  - Icon: `Icons.Filled.AccountBalance`
  - Title: "حسابی ثبت نشده است"
  - Description: "حساب‌ها به شما کمک می‌کنند تراکنش‌ها را دسته‌بندی کنید و موجودی هر حساب را جداگانه مدیریت کنید."
  - Action: "ایجاد حساب" → `onEvent(OnAddAccount)`

### Step 5.5: Progressive disclosure of the bank fields

- In `AccountFormContent`: show the `AccountBankFields` fields only when `formState.type == AccountType.BANK`
- For the other types (CASH_WALLET, SAVINGS_INVESTMENT, OTHER): hide the bank fields
- Use `AnimatedVisibility` or a simple `if`

### Step 5.6: StatusBadge component

- New file `ui/components/account/AccountStatusBadge.kt`
- Show an "آرشیو" badge on archived accounts
- Show it only in the management list (not in the dashboard)
- Color: `MaterialTheme.colorScheme.surfaceVariant` with `onSurfaceVariant` text

## Special Notes for This Phase

- From the central checklist:
  - **R2** (negative numbers): if a new "calculated balance" is added (for example in `AccountListCard`), use the LRM pattern and sign/amount separation
  - **R1** (Rust/Kotlin alignment): Unarchive must activate the dashboard sync — confirm that the Room Flow emits automatically after the update
- **Important:** Clear `lastDeletedAccount` and `lastArchivedAccount` after the Snackbar is dismissed
- Undo window: 7 seconds (the Snackbar duration)
- If the user closes the app before the Snackbar is dismissed, the undo is lost — this is acceptable

## Acceptance Criteria

- [ ] `UnarchiveAccountUseCase` exists and is tested
- [ ] `AccountOverflowMenu` has the "فعال‌سازی مجدد" option for an archived account
- [ ] After a delete, a Snackbar is shown with "واگردانی"
- [ ] After an archive, a Snackbar is shown with "واگردانی"
- [ ] A click on "واگردانی" restores the account
- [ ] The empty state has a title, a description, and a CTA button
- [ ] The bank fields are shown only for `AccountType.BANK`
- [ ] `AccountStatusBadge` is shown on archived accounts
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → no errors
- [ ] Manual QA: archive → restore, delete → undo, and the new empty state

## Rollback

```bash
git log --oneline -5
git revert <phase-5-commits>
# Or delete the new files and revert the edited files
```
