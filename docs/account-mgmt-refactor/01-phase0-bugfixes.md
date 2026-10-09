# Phase 0: Fix Real Bugs (No Architecture Change)

## Prerequisites

- **Previous phase:** None — this is the first phase
- **Pending decisions:** Decision #4 (unify `DEFAULT_ACCOUNT_COLOR`) must be confirmed before the start

## Context

The Gap Analysis report identified four real bugs in Account Management. No bug needs an architecture change. Each bug is a small, low-risk fix in an existing file. Each bug needs a separate commit.

## Exact Goal of This Phase

Fix the four listed bugs without an architecture change. The result is four clean commits on the `feature/multi-account-wallet` branch.

## Files Involved

| File | Change |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | Bug #1 (overflow anchoring) + bug #2 (archive confirmation) |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountTypeIcon.kt` | Bug #3 ('icon OTHER' — it can need no change) |
| `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/FinancialColors.kt` | Bug #4 (the main source) |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageBackupUseCase.kt` | Bug #4 (remove the duplicate copy) |

## Execution Steps

### Bug #1: Fix the incorrect anchoring of AccountOverflowMenu

**Problem:** `AccountOverflowMenu` (DropdownMenu) renders in `AccountManagementDialogs`, which is a sibling of the Scaffold content. It is not inside the `Box` that surrounds the `IconButton`. A DropdownMenu must anchor to its own parent `Box`.

**File path:** `AccountManagementScreen.kt`  
**Current code path:** lines 383-395 (IconButton inside Box) and lines 188-228 (AccountManagementDialogs)

**Solution:**
- Render `AccountOverflowMenu` directly inside the `Box` that surrounds the `IconButton` in `AccountItem`
- Move the `OverflowMenu(account)` state from `AccountManagementDialogs` to `AccountItem`
- Use one of two ways:
  - a) Pass `dialogState` to `AccountItem` and render the overflow menu inside the `Box`
  - b) Make `AccountOverflowMenu` a direct child of the `IconButton` wrapper

**Check after the change:**
```bash
grep -n "AccountOverflowMenu" app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt
# Must be inside the Box/Column of AccountItem, not at Scaffold level
```

**Commit:** `fix(ui): anchor AccountOverflowMenu to overflow button instead of Scaffold`

---

### Bug #2: Add a confirmation dialog before archive

**Problem:** A tap archives the account (line 224) with no confirmation. An archived account disappears from the dashboard and has no easy recovery path.

**File path:** `AccountManagementScreen.kt`  
**Current code path:** line 224 — `OverflowAction.ARCHIVE -> accountViewModel.archiveAccount(account)`

**Solution:**
- Add a new state `ArchiveConfirmation(account: AccountEntity)` to `AccountDialogState`
- In `onOverflowAction`, when `OverflowAction.ARCHIVE` is selected, set `dialogState = ArchiveConfirmation(account)` instead of `archiveAccount`
- In `AccountManagementDialogs`, add a new `ArchiveConfirmation` case with a `ConfirmDialog`
- Confirmation message: `آیا از آرشیو کردن حساب «{name}» اطمینان دارید؟ حساب از داشبورد حذف خواهد شد.`
- Confirm button: "آرشیو" with `MaterialTheme.colorScheme.primary` color (not error — archive is not destructive)

**Check after the change:**
```bash
grep -n "ArchiveConfirmation" app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt
# Must have at least 3 references: sealed interface, when case, ConfirmDialog call
```

**Commit:** `fix(ui): add confirmation dialog before archiving an account`

---

### Bug #3: Fix the OTHER icon map mismatch

**Problem:** The `AccountType → icon` mapping exists in two places, and it is different for the `OTHER` type:
- `AccountManagementScreen.kt:91` → `ACCOUNT_TYPE_ICONS[OTHER] = Icons.Filled.Payments`
- `AccountTypeIcon.kt:30` → `AccountType.icon(OTHER) = Icons.Filled.MoreHoriz`

**File paths:**
- `AccountManagementScreen.kt` — lines 86-92 (`ACCOUNT_TYPE_ICONS`)
- `ui/components/AccountTypeIcon.kt` — lines 25-31 (`AccountType.icon()`)

**Solution:**
- Remove the `ACCOUNT_TYPE_ICONS` map in `AccountManagementScreen.kt`
- Reference `AccountType.icon()` everywhere that used `ACCOUNT_TYPE_ICONS` (lines 331, 712)
- Choose one of the two icons: `MoreHoriz` (from `AccountTypeIcon.kt`) or `Payments`
- **Recommendation:** `MoreHoriz` is better. It gives the meaning of "other" more clearly, and the app already uses it elsewhere

**Check after the change:**
```bash
grep -n "ACCOUNT_TYPE_ICONS" app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt
# Must return 0 results (removed)
grep -n "AccountType.icon\|\.icon()" app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt
# Must have at least 2 references
```

**Commit:** `fix(ui): remove duplicate icon mapping, use AccountType.icon() consistently`

---

### Bug #4: Unify DEFAULT_ACCOUNT_COLOR

**Problem:** `DEFAULT_ACCOUNT_COLOR` is defined in three places. All three values are `0xFF4CAF50L`, but there is no single source.

**File paths:**
- `ui/designsystem/FinancialColors.kt:37` — `const val DEFAULT_ACCOUNT_COLOR = 0xFF4CAF50L`
- `domain/usecase/ManageBackupUseCase.kt:30` — `const val DEFAULT_ACCOUNT_COLOR = 0xFF4CAF50L`
- `rust/hesabyar-core/src/models/mod.rs:116` — `fn default_color() -> i64 { 0xFF4CAF50 }`

**Solution:**
1. Confirm the exact value of all three (they must be the same)
2. Delete `ManageBackupUseCase.kt:30` and add an import of `FinancialColors.DEFAULT_ACCOUNT_COLOR`
3. The Rust value must not change (remove only the Kotlin-side duplicate)
> **Architecture note:** Rust is the single source of truth for business logic. This fix only removes a duplicate on the Kotlin side. Rust remains the primary reference. Never add new logic or calculations to Kotlin.

**Check after the change:**
```bash
grep -rn "DEFAULT_ACCOUNT_COLOR" app/src/main/java/ | grep -v "build/"
# Must return only FinancialColors.kt and its imports
```

**Commit:** `fix: remove duplicate DEFAULT_ACCOUNT_COLOR in ManageBackupUseCase`

---

## Special Notes for This Phase

- From the central checklist: confirm **decision #4** (color unification) before bug #4 starts
- From the central checklist: **R1** (Rust/Kotlin alignment) — bug #4 only removes the Kotlin-side duplicate. The Rust value does not change, so R1 is not broken. Note: this is only the correction of an existing fallback — it is not guidance to add new logic in Kotlin.
- Each bug needs a **separate commit** so that rollback is easy
- After each bug, run `./gradlew test --no-daemon`

## Acceptance Criteria

- [ ] Bug #1: `AccountOverflowMenu` renders inside the `Box` that surrounds the `IconButton` (not at Scaffold level)
- [ ] Bug #2: Archive needs a confirmation dialog
- [ ] Bug #3: The `ACCOUNT_TYPE_ICONS` map is removed and `AccountType.icon()` is used
- [ ] Bug #4: `ManageBackupUseCase.kt` no longer has a separate `DEFAULT_ACCOUNT_COLOR`
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → no errors
- [ ] Four separate commits on the branch
- [ ] A grep confirms that no old reference remains

## Rollback

Each bug can be rolled back separately:
```bash
git log --oneline  # Find the commit hash that you want
git revert <commit-hash>
```
Or roll back all bugs together:
```bash
git diff HEAD~4..HEAD --stat  # Confirm the last 4 commits
git reset --hard HEAD~4
```
