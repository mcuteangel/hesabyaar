# Phase 3: Extract the UI Components

## Prerequisites

- **Previous phase:** Phase 2 must be complete (AccountUiState and AccountEvent are ready)
- **Pending decisions:** None

## Context

The current `AccountManagementScreen.kt` has 731 lines and holds 12+ private components. This phase extracts the components from the main file and moves them to separate files. At the end, `AccountManagementScreen.kt` must be at most 200 lines.

## Exact Goal of This Phase

Extract 7 components from `AccountManagementScreen.kt` to separate files in `ui/components/account/`, and add a `@Preview` to each one.

## Files Involved

### New files
| File | Description |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountListCard.kt` | Account list card |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountFormContent.kt` | Form body (shared by Add/Edit) |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountColorPicker.kt` | Color picker |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountTypeDropdown.kt` | Account type picker |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountBankFields.kt` | Bank fields (conditional) |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountPreviewRow.kt` | Live preview |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountOverflowMenu.kt` | Overflow menu |

### File to edit
| File | Change |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | Remove the private composables, import the new components |

## Execution Steps

### Step 3.1: Create the directory and the empty files
```bash
mkdir -p app/src/main/java/io/github/mojri/hesabyar/ui/components/account
```

### Step 3.2: Extract AccountListCard

**Source:** `AccountManagementScreen.kt` lines 326-397 (`AccountItem`)  
**Parameters:** `account: AccountEntity`, `onOverflowEdit: (AccountEntity) -> Unit`, `onOverflowArchive: (AccountEntity) -> Unit`, `onOverflowDelete: (AccountEntity) -> Unit`  
**Structure:** `HesabyarCard` → `Row` → `IconCircle` + `Column` + `IconButton`  
**@Preview:** with a sample `AccountEntity`

### Step 3.3: Extract AccountFormContent

**Source:** `AccountManagementScreen.kt` lines 468-551 (`AccountDialogForm`)  
**Parameters:** `initialAccount: AccountEntity?`, `onSave: (AccountFormData) -> Unit`  
**Note:** The form keeps its `remember`-based state in this phase. Phase 4 switches it to the ViewModel-backed `AccountFormState`.  
**@Preview:** an empty form and a filled form

### Step 3.4: Extract AccountColorPicker

**Source:** `AccountManagementScreen.kt` lines 646-693  
**Parameters:** `selectedColor: Long`, `palette: List<Long>`, `columns: Int`, `onColorSelected: (Long) -> Unit`  
**@Preview:** with sample colors

### Step 3.5: Extract AccountTypeDropdown

**Source:** `AccountManagementScreen.kt` lines 554-596  
**Parameters:** `selectedType: AccountType`, `onTypeSelected: (AccountType) -> Unit`  
**@Preview:** with the default type

### Step 3.6: Extract AccountBankFields

**Source:** `AccountManagementScreen.kt` lines 598-644  
**Parameters:** bankName, cardNumber, accountNumber, iban + callbacks  
**@Preview:** with sample values

### Step 3.7: Extract AccountPreviewRow

**Source:** `AccountManagementScreen.kt` lines 695-730  
**Parameters:** `name: String`, `type: AccountType`, `color: Long`  
**@Preview:** with sample values

### Step 3.8: Extract AccountOverflowMenu

**Source:** `AccountManagementScreen.kt` lines 400-443  
**Parameters:** `onEdit`, `onArchive`, `onDelete`, `onDismiss`  
**@Preview:** with expanded=true

### Step 3.9: Update AccountManagementScreen

- Remove all the private composables
- Add the new imports
- The file must be at most 200 lines
- Remove the `ACCOUNT_TYPE_ICONS` map (if Phase 0 did not already remove it)

### Step 3.10: Add ColorPickerGrid (Generic)

- New file `ui/components/shared/ColorPickerGrid.kt`
- `AccountColorPicker` uses this component
- **Reusable:** for the category colors, tags, and every other color picker

## Special Notes for This Phase

- From the central checklist:
  - **R2** (negative number display): if `AccountListCard` shows a negative amount, use the LRM pattern
  - **R4** (grep confirmation): after you remove the composables from the main file, grep to confirm that no old reference remains
- **Important:** In this phase, the state is still `remember`-based. The goal is only the physical extraction of the files, not a change to the state architecture (that is Phase 4).
- Each composable must have a `Modifier` parameter for testability
- Each composable must have a `@Preview` function

## Acceptance Criteria

- [ ] 7 new files exist in `ui/components/account/`
- [ ] 1 new file `ColorPickerGrid.kt` exists in `ui/components/shared/`
- [ ] `AccountManagementScreen.kt` is at most 200 lines
- [ ] Each new file has a `@Preview` function
- [ ] A grep confirms: `AccountItem`, `AccountDialogForm`, `AccountDialogColorPicker`, `AccountDialogTypeField`, `AccountDialogBankDetailsFields`, `AccountDialogPreviewRow`, and `AccountOverflowMenu` are not in the main file
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → no errors
- [ ] The previews render in Android Studio

## Rollback

```bash
# Roll back the whole Phase 3:
git log --oneline -10
git revert <phase-3-commits>

# Or roll back manually:
# Delete the new files
rm app/src/main/java/.../ui/components/account/*.kt
rm app/src/main/java/.../ui/components/shared/ColorPickerGrid.kt
# Restore the main file from git
git checkout HEAD -- app/src/main/java/.../ui/screens/account/AccountManagementScreen.kt
```
