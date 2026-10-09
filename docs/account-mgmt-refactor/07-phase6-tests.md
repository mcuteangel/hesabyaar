# Phase 6: Tests and Accessibility

## Prerequisites

- **Previous phase:** Phase 5 must be complete (all the features are ready)
- **Pending decisions:**
  - Decision #3 (test tooling): before the start, confirm that Roborazzi and Detekt are really installed/configured

## Context

After Phase 5, all the features are ready, but the test coverage is weak: AccountViewModel and AccountManagementScreen are not tested at all. This phase completes the test coverage and improves accessibility.

## Exact Goal of This Phase

Reach at least 90% line coverage for the ViewModel and the use cases, write Compose UI tests for all the user flows, and add accessibility semantics to all the components.

## Files Involved

### New files (tests)
| File | Description |
|---|---|
| `app/src/test/java/io/github/mojri/hesabyar/ui/AccountViewModelTest.kt` | Full ViewModel test (if Phase 2 was incomplete) |
| `app/src/test/java/io/github/mojri/hesabyar/ui/AccountManagementScreenTest.kt` | Compose UI tests |
| `app/src/test/java/io/github/mojri/hesabyar/ui/AccountFormDialogTest.kt` | Form test |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/account/ArchiveAccountUseCaseTest.kt` | Archive test |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/account/UnarchiveAccountUseCaseTest.kt` | Restore test |
| `app/src/test/java/io/github/mojri/hesabyar/domain/validation/AccountValidatorTest.kt` | Full validator test (if Phase 1 was incomplete) |

### Files to edit (accessibility) <!-- check-docs: planned -->
| File | Change |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountListCard.kt` | contentDescription, semantics, testTag |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountColorPicker.kt` | contentDescription for each swatch |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountTypeDropdown.kt` | testTag |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountBankFields.kt` | labelFor semantics |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | testTag for FAB, list, dialogs |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountFormDialog.kt` | testTag, announceForAccessibility |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountDeleteDialog.kt` | testTag |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountArchiveDialog.kt` | testTag |

## Execution Steps

### Step 6.0: Confirm the test tooling

```bash
grep -rn "roborazzi\|detekt" app/build.gradle.kts build.gradle.kts
```
- If Roborazzi is not installed: skip the snapshot tests
- If Detekt is not installed: skip the detekt check (use only ktlint)

### Step 6.1: Complete AccountViewModelTest

```kotlin
// Necessary tests:
- onAddAccount_showsAddDialog
- onEditAccount_showsEditDialogWithData
- onSaveNewAccount_validForm_insertsAndShowsSuccessSnackbar
- onSaveNewAccount_invalidForm_showsErrorsAndNoInsert
- onSaveEditedAccount_validForm_updatesAndShowsSnackbar
- onDeleteRequest_noTransactions_showsDeleteConfirmation
- onDeleteRequest_withTransactions_showsTransactionWarning
- onConfirmDelete_deletesAndShowsSnackbarWithUndo
- onConfirmArchive_archivesAndShowsSnackbarWithUndo
- onUndoDelete_reInsertsAccount
- onUndoArchive_restoresAccount
- onFormChange_updatesFormState
- onDismissDialog_clearsDialogState
- onDismissSnackbar_clearsSnackbarMessage
- onUnarchiveAccount_restoresAccount
```

### Step 6.2: Complete the Compose UI tests

```kotlin
// AccountManagementScreenTest:
- emptyState_showsCorrectMessageAndAction
- list_showsAccountItems
- fab_opensAddDialog
- overflowMenu_showsEditArchiveDelete
- overflowEdit_opensEditDialog
- overflowArchive_showsConfirmationDialog
- overflowDelete_showsDeleteDialogOrWarning

// AccountFormDialogTest:
- emptyForm_showsAllFields
- fillForm_enablesSaveButton
- emptyName_disablesSaveButton
- typeDropdown_showsAllTypes
- bankFields_visibleForBankType
- bankFields_hiddenForOtherTypes
- colorPicker_selectsColor
- previewRow_updatesWithForm
- save_emitsFormWithCorrectData
```

### Step 6.3: Accessibility semantics

**AccountListCard:**
```kotlin
Modifier.semantics {
  contentDescription = "حساب «${account.name}»، ${account.type.displayName}، ${formattedBalance}"
}
```

**AccountColorPicker:**
```kotlin
// For each swatch:
Modifier.semantics {
  contentDescription = "رنگ ${colorName}"
  selected = isSelected
  role = Role.Button
}
```

**AccountFormDialog:**
```kotlin
Modifier.semantics {
  liveRegion = LiveRegion.Polite  // To announce error messages
}
```

**AccountManagementScreen:**
```kotlin
FAB:
Modifier.testTag("addAccountFab")
  .semantics { contentDescription = "افزودن حساب" }

Empty State:
Modifier.testTag("emptyState")

Account List:
Modifier.testTag("accountList")
```

### Step 6.4: Snapshot tests (if Roborazzi is installed)

```kotlin
// AccountManagementScreenSnapshotTest:
- emptyState
- listWith3Accounts
- addDialogEmptyForm
- addDialogFilledForm
- editDialog
- deleteConfirmation
- transactionWarning
- archiveConfirmation
```

### Step 6.5: Final test run

```bash
./gradlew test --rerun-tasks --no-daemon
```

## Special Notes for This Phase

- From the central checklist:
  - **R3** (test confirmation): record the exact output of `./gradlew test --rerun-tasks --no-daemon`
  - **R4** (grep): confirm a claim that "all accessibility was added" with an independent grep
  - **R2** (negative numbers): if a snapshot test is added, include a negative balance scenario
- **Important:** `forkEvery = 1` must be enabled in `build.gradle.kts` (for Rust JNI isolation)
- Test naming convention: camelCase (not backtick) — as in AGENTS.md

## Acceptance Criteria

- [ ] `AccountViewModelTest` → all tests pass
- [ ] `AccountManagementScreenTest` → all tests pass
- [ ] `AccountFormDialogTest` → all tests pass
- [ ] `ArchiveAccountUseCaseTest` → all tests pass
- [ ] `UnarchiveAccountUseCaseTest` → all tests pass
- [ ] `AccountValidatorTest` → all tests pass
- [ ] All the components have a `contentDescription` (confirmed by grep)
- [ ] All the components have a `testTag` (confirmed by grep)
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → no errors (if detekt is installed)
- [ ] Test count: count all the new tests and record them in the log

## Rollback

```bash
git log --oneline -5
git revert <phase-6-commits>
# Or delete the new test files
rm app/src/test/java/.../AccountViewModelTest.kt
rm app/src/test/java/.../AccountManagementScreenTest.kt
# ... etc
# Revert the accessibility changes in the component files
```
