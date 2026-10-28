# Plan 024: [UX / Consistency] Unified Dialog Policy, Standard Button Slots, and Raw AlertDialog Migration

- **Issue:** #350
- **PR:** —
- **Created:** 2026-10-24
- **Author:** Claude Code
- **Status:** DONE

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

---

## 1. Problem Statement

Issue #350, Phase 2 of `plans/013-ui-ux-overhaul.md`, and the UI/UX audit (`hesabyar-ux-audit.md` findings C1, H6, and Section 3 steps 1.1, 1.2, 2.1) identify severe inconsistencies across the app's dialog system:

1. **Five Divergent Dismiss Styles Used Side-by-Side:**
   - `ButtonVariant.Text`: `InstallmentScreen.kt:293`, `CategoryManagementScreen.kt:583`, `AddPersonLoanDialog.kt:89`, `AccountManagementScreen.kt:551`.
   - `ButtonVariant.Outlined`: `BankLoanScreen.kt:248`, `SmartAssistantScreen.kt:1284`, `CustomTimePickerDialog.kt:453`, `ReportsScreen.kt:483`.
   - Raw Compose `OutlinedButton`: `ManualTransactionDialog.kt:108`, `JalaliDatePickerDialog.kt:577`.
   - Raw Compose `TextButton`: `ConfirmDialog.kt:66`, `TransactionDetailDialog.kt:264`.
   - Header Close Icon (`IconButton(Close)`): `HesabyarDialog.kt:114` renders an "X" close icon on small confirmation/input dialogs, violating M3 guidelines where close icons belong on bottom sheets/full-screen surfaces, not alert/confirm dialogs.

2. **Inconsistent Button Order and Emphasis:**
   - In `HesabyarDialog.kt:141-149`, `actions` is an untyped, free-form `RowScope.() -> Unit` slot. Calling screens arbitrarily decide button ordering, spacing, and variant emphasis.
   - In RTL layouts, standard Material 3 places the primary action (`confirmButton`) at the trailing edge (far left in RTL) and the secondary action (`dismissButton`) adjacent to it. Because `HesabyarDialog` lacks structured button slots, different dialogs invert this order or mix `Outlined` with `Filled` buttons.

3. **Proliferation of Raw `AlertDialog` Calls in Feature Screens:**
   - `AddPersonLoanDialog.kt:55` was created with a raw `AlertDialog` call instead of the design-system `HesabyarDialog`.
   - Across `app/src/main/java/io/github/mojri/hesabyar/ui/screens/`, 8 active feature screens make direct calls to Compose `AlertDialog` (`AccountManagementScreen.kt:540`, `SmartAssistantScreen.kt:1254`, `InstallmentScreen.kt:206`, `BankLoanScreen.kt:228`, `CategoryManagementScreen.kt:371`, `TransactionDetailDialog.kt:59`, `SettingsScreen.kt:198,686,755`, `AddPersonLoanDialog.kt:55`), preventing centralized styling, elevation, and layout enforcement.

4. **Buried Actions and Layout Bugs in Core Overlays:**
   - **`AccountDialog` in `AccountManagementScreen.kt:540-667`:** `confirmButton` in `AlertDialog` is left completely empty (`confirmButton = {}` at `:549`). The actual primary save button (`HesabyarButton("ذخیره")`) is placed at line 649 at the very bottom of a `heightIn(max = 500.dp)` scrollable form column. On small or medium screens, users must scroll past all 6 form fields before the save button becomes visible.
   - **`ConfirmationDialog` in `SmartAssistantScreen.kt:1254-1284`:** Both `confirmButton` and `dismissButton` declare `Modifier.fillMaxWidth()` inside an M3 dialog button row, causing cramped horizontal clipping on typical phone viewports.
   - **`ConfirmDialog.kt:65-69` Blank Button Bug:** When `dismissText` is passed as `""` (e.g. `AccountDeleteWarningDialog` in `AccountManagementScreen.kt:348`), `ConfirmDialog` unconditionally renders `TextButton { Text("") }`, resulting in a phantom clickable button on screen.

5. **Hardcoded Labels and Lack of Standardized `action_*` String Resources:**
   - Per `docs/LOCALIZATION.md`, reusable UI action verbs must use the `action_*` key prefix (`action_confirm`, `action_cancel`, `action_save`, `action_delete`, `action_close`, `action_understood`).
   - Dialogs currently use a mix of hardcoded Persian strings (`"انصراف"`, `"ذخیره"`, `"تایید"`, `"بستن"`) or legacy keys (`R.string.cancel_label`, `R.string.understood_label`).

---

## 2. Goals & Acceptance Criteria

- [x] **Criterion 1 (Standardized `HesabyarDialog` Slots & Policy):**
  - `HesabyarDialog` in `ui/components/HesabyarDialog.kt` adds structured `confirmButton: (@Composable () -> Unit)? = null` and `dismissButton: (@Composable () -> Unit)? = null` slots while preserving `actions: (@Composable RowScope.() -> Unit)? = null` for backward compatibility.
  - In RTL layouts, `confirmButton` is placed at the end (far left) and `dismissButton` is adjacent to it (right of confirm).
  - `showCloseButton` defaults to `false` for standard dialogs.
  - Dialog content area is bounded with `Modifier.heightIn(max = 0.85f * screenHeight)` or equivalent so dialogs size to content and do not grow unbounded.
- [x] **Criterion 2 (Standardized `ConfirmDialog` Component):**
  - `ConfirmDialog.kt` checks `if (dismissText.isNotBlank())` before rendering the dismiss button slot, eliminating the blank clickable button when `dismissText = ""`.
  - Default labels use `R.string.action_confirm` ("تأیید") and `R.string.action_cancel` ("انصراف").
  - Confirm button is Filled (`HesabyarButton` with `confirmColor`, default `error`); dismiss button is always `ButtonVariant.Text`.
- [x] **Criterion 3 (Standardized `action_*` String Keys in `strings.xml`):**
  - Add standardized keys per `docs/LOCALIZATION.md`: `action_cancel` ("انصراف"), `action_confirm` ("تأیید"), `action_save` ("ذخیره"), `action_delete` ("حذف"), `action_close` ("بستن"), `action_understood` ("متوجه شدم").
  - Retain existing `cancel_label`, `delete_label`, and `understood_label` as aliases or maintain them to ensure zero regression across other untouched screens.
- [x] **Criterion 4 (Fix Buried & Broken Actions in AccountDialog and SmartAssistant):**
  - `AccountManagementScreen.kt`: The "ذخیره" button is moved out of the scrollable form body into `confirmButton` (Filled). "انصراف" is wired into `dismissButton` (Text). The dialog actions remain visible at all times without scrolling.
  - `SmartAssistantScreen.kt`: `Modifier.fillMaxWidth()` is removed from both dialog buttons in `ConfirmationDialog`. Dismiss button uses `ButtonVariant.Text`.
- [x] **Criterion 5 (Raw `AlertDialog` Elimination Outside `ui/components`):**
  - `AddPersonLoanDialog.kt`, `AccountManagementScreen.kt:AccountDialog`, `SmartAssistantScreen.kt:ConfirmationDialog`, `InstallmentScreen.kt:AddInstallmentDialog`, `BankLoanScreen.kt:BankLoanDialog`, `CategoryManagementScreen.kt:CategoryDialog`, `TransactionDetailDialog.kt`, and `SettingsScreen.kt` (3 PIN/restore dialogs) are migrated to `HesabyarDialog`.
  - `rg '\bAlertDialog\(' app/src/main` produces 0 matches in `ui/screens/` (excluding dead code `LoanManagementScreen.kt` tracked under #340).
- [x] **Criterion 6 (Dismiss and Confirm Button Styling Compliance):**
  - Across all 12 dialogs/surfaces highlighted in issue #350, all dismiss actions use `ButtonVariant.Text` (or `TextButton`), all confirm actions use `ButtonVariant.Filled`, and destructive confirm actions use `Filled` with `colorScheme.error`.
  - Raw `OutlinedButton` and `ButtonVariant.Outlined` are replaced on dialog dismiss actions in `CustomTimePickerDialog.kt`, `JalaliDatePickerDialog.kt`, `ManualTransactionDialog.kt`, and `ReportsScreen.kt`.
- [x] **Criterion 7 (Automated Unit & Regression Tests):**
  - New unit tests in `HesabyarDialogTest.kt` verify structured confirm/dismiss slot layout, callbacks, and `showCloseButton` behavior.
  - New unit tests in `ConfirmDialogTest.kt` verify confirm/dismiss callbacks, error color styling, and verify that empty `dismissText = ""` renders no dismiss node.
  - Existing tests (`AddPersonLoanDialogTest`, `AccountManagementScreenDeleteDialogTest`, etc.) remain green.
- [x] **Criterion 8 (Static Verification & Codebase Hygiene):**
  - `./gradlew ktlintCheck detekt --no-daemon` passes with 0 findings; no detekt baseline edits.
  - `./gradlew testDebugUnitTest --no-daemon` passes with 0 failures.
  - `python3 scripts/check_docs.py` passes with 0 broken references.

---

## 3. Implementation Steps

### Phase 1 — Foundations: `HesabyarDialog` Slots, `ConfirmDialog` Bug Fix, and String Resources <!-- check-docs: planned -->

- [x] **Step 1.1: Add standard `action_*` strings in `res/values/strings.xml`**
  - Add `action_cancel` ("انصراف"), `action_confirm` ("تأیید"), `action_save` ("ذخیره"), `action_delete` ("حذف"), `action_close` ("بستن"), `action_understood` ("متوجه شدم").
  - Keep `cancel_label`, `delete_label`, `understood_label` intact for existing callers.

- [x] **Step 1.2: Upgrade `ui/components/HesabyarDialog.kt` API**
  - Change default `showCloseButton: Boolean = false`.
  - Add structured parameters:
    ```kotlin
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    ```
  - When `confirmButton != null || dismissButton != null`, render a standardized bottom action row:
    ```kotlin
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = SpacingTokens.sm),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (dismissButton != null) {
        dismissButton()
        if (confirmButton != null) {
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
        }
      }
      if (confirmButton != null) {
        confirmButton()
      }
    }
    ```
  - When `actions != null`, render `actions` for backward compatibility.
  - Apply max height capping on body column using `Modifier.weight(1f, fill = false)`.

- [x] **Step 1.3: Fix `ui/components/ConfirmDialog.kt`**
  - Render `dismissButton` only if `dismissText.isNotBlank()`:
    ```kotlin
    dismissButton = if (dismissText.isNotBlank()) {
      {
        TextButton(onClick = onDismiss) {
          Text(text = dismissText)
        }
      }
    } else null
    ```
  - Use `stringResource(R.string.action_confirm)` and `stringResource(R.string.action_cancel)` for default strings.
  - Migrate internal `AlertDialog` to `HesabyarDialog` or enforce filled confirm button and text dismiss button.

- [x] **Step 1.4: Add unit tests for dialog components**
  - Create `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarDialogTest.kt` verifying:
    - Title and content rendering.
    - Confirm button and dismiss button invocation.
    - `showCloseButton = false` by default; renders icon when `true`.
  - Create `app/src/test/java/io/github/mojri/hesabyar/ui/components/ConfirmDialogTest.kt` verifying:
    - Confirm and dismiss callbacks.
    - Passing `dismissText = ""` renders no dismiss button node.
    - Confirm button uses semantic error color by default.

---

### Phase 2 — Fix Buried Actions & Button Cramping in Core Overlays

- [x] **Step 2.1: Fix `AccountDialog` in `ui/screens/account/AccountManagementScreen.kt`**
  - Migrate `AccountDialog` to `HesabyarDialog`.
  - Remove save button from inside `AccountDialogForm` Column.
  - Provide `confirmButton = { HesabyarButton(onClick = { onSave(...) }, text = stringResource(R.string.action_save), variant = ButtonVariant.Filled, enabled = isFormValid) }`.
  - Provide `dismissButton = { HesabyarButton(onClick = onDismiss, text = stringResource(R.string.action_cancel), variant = ButtonVariant.Text) }`.
  - Form content scrolls independently; action buttons remain sticky and always visible.

- [x] **Step 2.2: Fix `ConfirmationDialog` in `ui/screens/SmartAssistantScreen.kt`**
  - Migrate `ConfirmationDialog` to `HesabyarDialog`.
  - Remove `Modifier.fillMaxWidth()` from confirm and dismiss buttons.
  - Change dismiss button variant from `ButtonVariant.Outlined` to `ButtonVariant.Text`.
  - Confirm button remains `ButtonVariant.Filled`.

---

### Phase 3 — Migrate Feature Screen Dialogs to `HesabyarDialog` and Standardize Styles

- [x] **Step 3.1: Migrate `AddPersonLoanDialog.kt`**
  - Replace raw `AlertDialog` with `HesabyarDialog(title = stringResource(if (type == LoanType.DEBTOR) R.string.add_person_loan_debtor_title else R.string.add_person_loan_creditor_title, personName), onDismissRequest = { if (!isSubmitting) onDismiss() }, confirmButton = { ... }, dismissButton = { ... })`.
  - Confirm: `HesabyarButton(text = stringResource(R.string.person_loan_submit), variant = ButtonVariant.Filled)`.
  - Dismiss: `HesabyarButton(text = stringResource(R.string.action_cancel), variant = ButtonVariant.Text)`.

- [x] **Step 3.2: Migrate `InstallmentScreen.kt` add installment dialog**
  - Replace raw `AlertDialog` at line 206 with `HesabyarDialog(title = "افزودن قسط جدید", ...)`.
  - Confirm: `HesabyarButton(text = "ذخیره قسط", variant = ButtonVariant.Filled)`.
  - Dismiss: `HesabyarButton(text = stringResource(R.string.action_cancel), variant = ButtonVariant.Text)`.

- [x] **Step 3.3: Migrate `BankLoanScreen.kt` bank loan dialog**
  - Replace raw `AlertDialog` at line 228 with `HesabyarDialog(title = "ثبت وام بانکی", ...)`.
  - Change dismiss button from `ButtonVariant.Outlined` to `ButtonVariant.Text`.
  - Confirm: `HesabyarButton(text = "ثبت", variant = ButtonVariant.Filled)`.

- [x] **Step 3.4: Migrate `CategoryManagementScreen.kt` category dialog**
  - Replace raw `AlertDialog` at line 371 with `HesabyarDialog(title = if (isEditing) "ویرایش دسته‌بندی" else "افزودن دسته‌بندی جدید", ...)`.
  - Confirm: `HesabyarButton(text = stringResource(R.string.action_save), variant = ButtonVariant.Filled)`.
  - Dismiss: `HesabyarButton(text = stringResource(R.string.action_cancel), variant = ButtonVariant.Text)`.

- [x] **Step 3.5: Migrate `TransactionDetailDialog.kt`**
  - Replace raw `AlertDialog` at line 59 with `HesabyarDialog(title = "جزئیات تراکنش", ...)`.
  - Fix title alignment: use `TextAlign.Start` instead of `TextAlign.Right` (Phase 2.4 of plan 013).
  - Dismiss button: `HesabyarButton(text = stringResource(R.string.action_close), variant = ButtonVariant.Text)`.
  - Delete button: `HesabyarButton(text = stringResource(R.string.action_delete), variant = ButtonVariant.Filled, colors = errorColors)`.

- [x] **Step 3.6: Migrate `SettingsScreen.kt` dialogs**
  - Replace Restore dialog raw `AlertDialog` at line 198 with `HesabyarDialog`.
  - Replace Verify PIN dialog raw `AlertDialog` at line 686 with `HesabyarDialog`.
  - Replace Set PIN dialog raw `AlertDialog` at line 755 with `HesabyarDialog`.

- [x] **Step 3.7: Standardize dismiss buttons in picker/filter dialogs**
  - In `CustomTimePickerDialog.kt:453`: change "انصراف" from `ButtonVariant.Outlined` to `ButtonVariant.Text`.
  - In `JalaliDatePickerDialog.kt:577`: change "انصراف" from raw `OutlinedButton` to `HesabyarButton(text = stringResource(R.string.action_cancel), variant = ButtonVariant.Text)`.
  - In `ManualTransactionDialog.kt:108`: change "انصراف" from raw `OutlinedButton` to `HesabyarButton(variant = ButtonVariant.Text)`, and confirm button from raw `Button` to `HesabyarButton(variant = ButtonVariant.Filled)`.
  - In `ReportsScreen.kt:483`: change "بستن" from `ButtonVariant.Outlined` to `ButtonVariant.Text`.

---

### Phase 4 — Verification, Quality Gates & Doc Sync

- [x] **Step 4.1: Verify raw `AlertDialog` elimination**
  - Run `rg '\bAlertDialog\(' app/src/main`.
  - Verify 0 matches in `ui/screens/` (except dead code `LoanManagementScreen.kt` per issue #340).
- [x] **Step 4.2: Run test suite**
  - `./gradlew testDebugUnitTest --no-daemon`
  - Ensure all 1212+ unit tests pass with 0 failures.
- [x] **Step 4.3: Run lint and static analysis**
  - `./gradlew ktlintCheck detekt --no-daemon`
  - Ensure 0 findings and no baseline edits.
- [x] **Step 4.4: Check doc path references**
  - `python3 scripts/check_docs.py`
  - Ensure 0 broken path references.

---

## 4. Evidence & Verification Plan

Per the repository's evidence standard (`AGENTS.md` and `docs/agents/testing.md`), the executor must provide:

1. **Exact Code Snippets:**
   - Paste the exact updated declarations of `HesabyarDialog`, `ConfirmDialog`, `AccountDialog`, `ConfirmationDialog`, and `AddPersonLoanDialog`.
2. **Raw Test Output:**
   - Raw `<testcase>` or test-runner output for:
     - `HesabyarDialogTest` (structured slots, close button, callback tests).
     - `ConfirmDialogTest` (confirm/dismiss callbacks, empty dismiss text suppression).
     - `AddPersonLoanDialogTest` (input validation and callback tests).
     - `AccountManagementScreenDeleteDialogTest` (verification dialogs).
3. **Raw Grep Output for AlertDialog:**
   - Run `rg '\bAlertDialog\(' app/src/main` showing that no active screens in `ui/screens` invoke `AlertDialog`.
4. **Tool Verification Command Lines & Outputs:**
   - `./gradlew ktlintCheck detekt --no-daemon`
   - `./gradlew testDebugUnitTest --no-daemon`
   - `python3 scripts/check_docs.py`

---

## 5. Risks, Dependencies & STOP Conditions

### Risks & Mitigations
- **Form State Scope in `AccountDialog`:** Moving "ذخیره" out of `AccountDialogForm` into `AccountDialog`'s `confirmButton` requires the form state (name, initialBalance, errors) to be hoisted or accessible to the confirm action callback.
  - *Mitigation:* Keep state in the dialog composable or expose an `isValid` and `formData` callback/holder, matching existing patterns in `AddPersonLoanDialog` and `ManualTransactionDialog`.
- **Keyboard Dismissal & BackPress:** `HesabyarDialog` uses Compose `Dialog`. Ensure `onDismissRequest` handles back press correctly across all migrated dialogs.
- **Dead Code in `LoanManagementScreen.kt`:** `LoanManagementScreen.kt:306,615,697,868` contains 4 raw `AlertDialog` calls but is unreferenced dead code tracked under issue #340. Touching it risks unintended regressions in unlinked code.
  - *Mitigation:* Explicitly leave `LoanManagementScreen.kt` untouched per plan 013 policy.

### Dependencies
- Issue #349: Closed (`ConfirmDialog` destructive styling and touch targets).
- Issue #358: Localization cleanup for `action_*` keys (co-aligned in Phase 1).
- Plan 013: UI/UX Overhaul Phase 1 (1.1, 1.2) and Phase 2 (2.1, 2.3, 2.4).

### STOP Conditions
1. If moving the save button in `AccountDialog` requires a schema change or alters `AccountEntity` persistence logic, STOP and ask the user.
2. If any dialog change alters financial amounts or bypasses `CurrencyFormatter`, STOP and ask the user.
3. If Room migrations or database tables are affected, STOP immediately.
