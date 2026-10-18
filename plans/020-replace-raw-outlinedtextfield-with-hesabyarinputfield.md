# Plan 020: Replace 16 Raw OutlinedTextField Uses With HesabyarInputField

- **Issue:** #457
- **PR:** —
- **Created:** 2026-10-18
- **Author:** Claude Code
- **Status:** IN PROGRESS (implementation + verification green; PR pending)

## 1. Problem Statement

`HesabyarInputField` (`app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarInputField.kt:15`) is the shared, styled text-input wrapper around `OutlinedTextField`. It applies the design-system border colors (`ShapeTokens.Large`, `MaterialTheme.colorScheme.primary`/`outline`/`error`) and `Modifier.fillMaxWidth()`. It is already used 35 times across the app, but 13 call sites bypass it by instantiating raw `OutlinedTextField` directly, so the design-system styling drifts per call site.

Issue #457 text: "Migrate all 13 to `HesabyarInputField`, extending it if a legitimate variant (e.g. multiline, prefix) is missing. No raw `OutlinedTextField` should remain in `ui/screens/`."

### Call sites to migrate

All in call-site parameter form `OutlinedTextField(...)` (raw usage). `HesabyarInputField.kt:31` itself wraps `OutlinedTextField` and is the only legitimate remaining import of that symbol in `ui/` after migration.

| # | File | Line(s) | Notes / variant needed |
|---|------|---------|------------------------|
| 1 | `ui/screens/dashboard/dialogs/ManualTransactionFormSections.kt` | 107 | `TransactionAmountInput` — uses `TextFieldValue` (not `String`), dynamic `leadingIcon` tint by `typeColor`, custom `colors` (focusedBorderColor=`typeColor`), `shape=ShapeTokens.Medium`, `keyboardType.Number`, `.testTag` |  |
| 2 | `ui/screens/dashboard/dialogs/ManualTransactionFormSections.kt` | 190 | `LoanPersonNameInput` — `String` value, `leadingIcon`, `placeholder`, `shape=ShapeTokens.Medium`, `.testTag` |  |
| 3 | `ui/screens/dashboard/dialogs/ManualTransactionFormSections.kt` | 225 | `InstallmentFormFields` title — `String`, `placeholder`, `shape=ShapeTokens.Medium`, `.testTag` |  |
| 4 | `ui/screens/dashboard/dialogs/ManualTransactionFormSections.kt` | 243 | `InstallmentFormFields` days — `String`, `placeholder`, `shape=ShapeTokens.Medium`, `.testTag`, `keyboardType.Number` |  |
| 5 | `ui/screens/dashboard/dialogs/ManualTransactionFormSections.kt` | 270 | `TransactionDescriptionInput` — `String`, `leadingIcon`, `shape=ShapeTokens.Medium`, `.testTag` |  |
| 6 | `ui/screens/SettingsScreen.kt` | 1296 | Provider dropdown anchor — `String`, `readOnly=true`, `onValueChange={}`, `menuAnchor`, `trailingIcon`, `shape=ShapeTokens.Large` (dropdown menus need `readOnly`; see risk note) |  |
| 7 | `ui/screens/SettingsScreen.kt` | 1414 | Model dropdown anchor — `String`, `readOnly=false` (editable), `menuAnchor`, `trailingIcon`, `shape=ShapeTokens.Large`, `placeholder={Text}` |  |
| 8 | `ui/screens/account/AccountManagementScreen.kt` | 671 | Account type dropdown anchor — `String`, `readOnly=true`, `onValueChange={}`, `menuAnchor`, `trailingIcon`, `shape=ShapeTokens.Medium` |  |
| 9 | `ui/screens/SmartAssistantScreen.kt` | 1016 | Parsed amount input — `String`, dynamic `leadingIcon` tint by `typeColor`, custom `colors`, `shape=ShapeTokens.Medium`, `.testTag("parsed_amount_input")`, `keyboardType.Number` |  |
| 10 | `ui/screens/PersonsScreen.kt` | 238 | Search field — `String`, `leadingIcon`, `label={Text}`, `placeholder={Text}`, custom `colors` (matches HesabyarInputField defaults), `Modifier.weight`, **no** `fillMaxWidth` |  |
| 11 | `ui/screens/CategoryManagementScreen.kt` | 403 | Category type dropdown anchor — `String`, `readOnly=true`, `onValueChange={}`, `menuAnchor`, `trailingIcon`, `shape=ShapeTokens.Medium` |  |
| 12 | `ui/screens/AddPersonLoanDialog.kt` | 108 | Amount input — `String`, `label={Text}`, `.filterDigits()` in `onValueChange`, `singleLine`, **no** `shape` arg (defaults to `ShapeTokens.FiExSmall`? check) |  |
| 13 | `ui/screens/AddPersonLoanDialog.kt` | 115 | Description input — `String`, `label={Text}`, `supportingText={Text}` (char count), `keyboardOptions`, **no** `shape` arg |  |
| 14 | `ui/components/ImportPassphraseDialog.kt` | 102 | Passphrase field — `String`, `label={Text(stringResource)}`, `visualTransformation=PasswordVisualTransformation`, `singleLine`, `enabled=!isCryptoInProgress`, `isError`, `supportingText={Text(...)}` lambda, `.testTag("passphrase_field")` (used by `ImportPassphraseDialogTest`) |  |
| 15-16 | `ui/components/ExportPassphraseDialog.kt` | 99, 107 | Two passphrase fields — `String`, `label`, `PasswordVisualTransformation`, `singleLine`; field 2 adds `isError` + `supportingText={Text(...)}` lambda |  |

Scope note (updated 2026-10-18): the user approved adding the two `ui/components/` passphrase dialogs to this plan, beyond issue #457's 13 `ui/screens/` occurrences. Total in scope: **16 raw call sites across 9 files**. After migration, the only file in `app/src/main` that imports `OutlinedTextField` is `HesabyarInputField.kt` (its wrapper body).

## 2. Goals & Acceptance Criteria

- [ ] **Criterion 1 (Migrate all 16 raw `OutlinedTextField` call sites):** Each of the 16 occurrences listed in §1 (13 in `ui/screens/` + 2 passphrase dialogs in `ui/components/`) replaces its `OutlinedTextField(...)` call with `HesabyarInputField(...)`, importing the shared component rather than `OutlinedTextField`/`OutlinedTextFieldDefaults`.
  - In-scope passphrase dialogs: `ImportPassphraseDialog.kt` (1 call site), `ExportPassphraseDialog.kt` (2 call sites).
  - After migration, `grep -rn "OutlinedTextField(" app/src/main/java/io/github/mojri/hesabyar/ui/screens/` returns **zero** matches; `grep -rn "OutlinedTextField(" app/src/main/` returns only the wrapper body at `HesabyarInputField.kt:31`.
- [ ] **Criterion 2 (`HesabyarInputField` gains required variants):** The shared composable grows these parameters (all defaulting to preserve existing behavior):
  - `leadingIcon: @Composable (() -> Unit)? = null`
  - `readOnly: Boolean = false` (dropdown anchors)
  - `colors: TextFieldColors? = null` (dynamic tint, e.g. `typeColor`)
  - `supportingText: @Composable (() -> Unit)? = null` (Composable — passphrase char-count and error text). *Note:* the existing `String?` overload is replaced; the 3 existing `String`-form callers (`BankLoanScreen.kt:310,318`, `SettingsScreen.kt:692,756`) are converted to Composable lambdas in the same PR.
- [ ] **Criterion 3 (No behavior change):** Visual output, `testTag`s, and keyboard types preserved. Specifically:
  - `manual_amount_input`, `parsed_amount_input`, and `passphrase_field` test tags remain on the migrated fields.
  - Number/Number password keyboard types preserved.
  - Dynamic `typeColor` tinting preserved via `colors`/`leadingIcon` tint.
  - The dropdown anchor fields (`readOnly=true`, no-op `onValueChange={}`) keep behavior via the new `readOnly` parameter.
- [ ] **Criterion 4 (Static checks):** `./gradlew ktlintFormat --no-daemon` then `./gradlew ktlintCheck detekt --no-daemon` — BUILD SUCCESSFUL, 0 findings. No new detekt baseline entries.
- [ ] **Criterion 5 (Tests green):** `./gradlew testDebugUnitTest --no-daemon` — BUILD SUCCESSFUL, 0 failures. Specifically `ImportPassphraseDialogTest` (`passphrase_field`), `SmartAssistantScreenConfirmFlowTest` (`parsed_amount_input`), and any `HesabyarInputField`-scoped tests still resolve their nodes.

## 3. Implementation Steps

### Phase 0: Re-audit at execution time (guard against drift)

1. Re-confirm the exact line numbers and the `HesabyarInputField` signature against the current `main`/`feat/...` branch (the `gitStatus` snapshot was taken 2026-10-18; a parallel PR may have landed).
2. Collect exact current signatures of each call site with `grep -n "OutlinedTextField" app/src/main/java/io/github/mojri/hesabyar/ui/screens/ -r`.
3. Done criteria: the 13 occurrences from §1 match the grep output (possibly shifted line numbers); `HesabyarInputField.kt` signature confirmed unchanged or extended as needed.

### Phase 1: Extend `HesabyarInputField` with the missing variants

Dependency: Phase 0.

1. In `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarInputField.kt`:
   - Add `leadingIcon: @Composable (() -> Unit)? = null`.
   - Add `readOnly: Boolean = false` (needed for dropdown anchor fields). `OutlinedTextField` already accepts `readOnly`.
   - Reconcile `supportingText`: the passphrase dialogs pass a `@Composable () -> Unit` lambda; `HesabyarInputField` currently takes `supportingText: String?`. Add `supportingTextBuilder: @Composable (() -> Unit)? = null` OR change `supportingText` to accept a composable. Decision: keep `supportingText: String?` for the common path and add `supportingTextComposable: @Composable (() -> Unit)? = null` so passphrase dialogs (currently deferred) can pass their lambda; this avoids string conversion of dynamic char-count text. *Unless* the passphrase dialog is migrated in this plan — see scope in §1. Since §1 defers the passphrase dialogs, this parameter is added only if a screens/ call site needs a composable supporting text (none do currently; `AddPersonLoanDialog` description uses `supportingText = { Text("...") }` — a composable). So: change `supportingText` from `String?` to `supportingText: @Composable (() -> Unit)? = null` and update all existing call sites that rely on `HesabyarInputField`'s `String` overload.
2. Update existing `HesabyarInputField` call sites that pass `supportingText` as a `String?` (search `HesabyarInputField(` usages to find them — e.g., SettingsScreen `HesabyarInputField(... supportingText = ...)` if any). If none pass a `String`, the change is a no-op risk-wise.
3. Verify `SingleValueMatchTest` / `HesabyarInputField` has any existing test — if `app/src/test/.../ui/components/...InputFieldTest` exists, keep it green.
4. Done criteria: `HesabyarInputField` compiles with the new signature, all existing `HesabyarInputField` callers compile, `./gradlew compileDebugKotlin`.

### Phase 2: Migrate the 16 call sites

Dependency: Phase 1. Do field-by-field; each call site is independently verifiable.

2a. `ManualTransactionFormSections.kt` (lines 107, 190, 225, 243, 270):
   - `TransactionAmountInput` (107): this holds a `TextFieldValue`, not a `String`. `HesabyarInputField` is `String`-typed.
   - Decision (per issue's "extending it if a legitimate variant is missing"): add an overload `fun HesabyarInputField(value: TextFieldValue, ...)` mirroring the `String` signature, sharing one impl. This is a legitimate variant (amount inputs use `TextFieldValue` for cursor control). Keep `.testTag("manual_amount_input")` by passing `modifier = Modifier.fillMaxWidth().testTag(...)`.
   - `LoanPersonNameInput` (190), `InstallmentFormFields` title (225), `InstallmentFormFields` days (243), `TransactionDescriptionInput` (270): straight `String` migration. Pass `leadingIcon`, `placeholder`, `singleLine`, `keyboardOptions`, `shape=ShapeTokens.Medium`, and `modifier` with the existing `.testTag(...)`. Custom `colors` (per-field tint) → pass via `colors: TextFieldColors? = null` param added to `HesabyarInputField`.
   - Done criteria for this file: 0 raw `OutlinedTextField` in it; 5 `HesabyarInputField` calls; `TransactionAmountInput` keeps cursor-correct `TextFieldValue` behavior via the overload.

2b. `SettingsScreen.kt` (1296, 1414):
   - 1296: provider dropdown anchor — `readOnly=true`, no-op `onValueChange={}`, `trailingIcon` = `ExposedDropdownMenuDefaults.TrailingIcon`, `menuAnchor`.
   - 1414: model dropdown anchor — editable (`readOnly=false`), `placeholder={Text(...)}`.
   - Done criteria: 0 raw `OutlinedTextField` in SettingsScreen.

2c. `AccountManagementScreen.kt` (671):
   - Account type dropdown anchor — `readOnly=true`, `trailingIcon`, `menuAnchor`.
   - Done criteria: 0 raw `OutlinedTextField` in the file.

2d. `SmartAssistantScreen.kt` (1016):
   - `parsed_amount_input` — `String` amount, dynamic `leadingIcon` tint + custom `colors` (focusedBorderColor=`typeColor`), `keyboardType.Number`, `.testTag("parsed_amount_input")`.
   - Done criteria: 0 raw `OutlinedTextField` in the file.

2e. `PersonsScreen.kt` (238):
   - Search field — `String`, `leadingIcon`, `label`/`placeholder` (as `Text` lambdas), custom `colors` that **match** `HesabyarInputField` defaults exactly, `Modifier.weight(1f)` (no `fillMaxWidth`), `singleLine`, `keyboardOptions`. Keep `modifier.fillMaxWidth()` as-is; callers passing `.weight(1f)` already rely on the field being `fillMaxWidth`-based inside a `Row`, so passing `Modifier.weight(1f)` as the `modifier` arg yields `weight(1f).fillMaxWidth()` which Compose resolves to fill the weighted slot.
   - Done criteria: 0 raw `OutlinedTextField` in the file; `persons_search` test tag preserved on the wrapper.

2f. `CategoryManagementScreen.kt` (403):
   - Category type dropdown anchor — `readOnly=true`, `trailingIcon`, `menuAnchor`. Same as 2b/2c.
   - Done criteria: 0 raw `OutlinedTextField` in the file.

2g. `AddPersonLoanDialog.kt` (108, 115):
   - 108: amount — `String`, `label={Text}`, `singleLine`, `.filterDigits()` in `onValueChange`. Pass `shape = ShapeTokens.Medium` to match sibling dialogs.
   - 115: description — `String`, `label={Text}`, `supportingText={Text(...)}` (char count composable), `singleLine=false` (multiline).
   - Done criteria: 0 raw `OutlinedTextField` in the file.

2h. `ImportPassphraseDialog.kt` (102):
   - Passphrase field — `String`, `visualTransformation = PasswordVisualTransformation()`, `singleLine = true`, `enabled = !isCryptoInProgress`, `isError = errorMessage != null && showError`, `supportingText = if (errorMessage != null && showError) { { Text(errorMessage, ...) } } else null`, `modifier = Modifier.fillMaxWidth().testTag("passphrase_field")`.
   - Done criteria: 0 raw `OutlinedTextField` in the file; `ImportPassphraseDialogTest` still passes.

2i. `ExportPassphraseDialog.kt` (99, 107):
   - Field 1 (passphrase): `visualTransformation = PasswordVisualTransformation()`, `singleLine = true`.
   - Field 2 (confirmPassphrase): `visualTransformation = PasswordVisualTransformation()`, `singleLine = true`, `isError = confirmPassphrase.isNotEmpty() && !passwordsMatch`, `supportingText = if (...) { { Text(...) } } else null`.
   - Done criteria: 0 raw `OutlinedTextField` in the file.

3. Cross-file: ensure imports are updated (remove `import ...OutlinedTextField` / `OutlinedTextFieldDefaults` where now unused; IDEs/detekt will flag unused imports). `ktlintFormat` removes unused imports automatically.

### Phase 3: Verification and Evidence

Dependency: Phase 2.

1. `./gradlew ktlintFormat --no-daemon` then `./gradlew ktlintCheck detekt --no-daemon` — BUILD SUCCESSFUL.
2. `./gradlew compileDebugKotlin --no-daemon` — succeeds.
3. `./gradlew testDebugUnitTest --no-daemon` — 0 failures. Capture raw `<testcase>` lines for `ImportPassphraseDialogTest` and `SmartAssistantScreenConfirmFlowTest` (both touch migrated screens' test tags).
4. Grep evidence: `grep -rn "OutlinedTextField(" app/src/main/` → only the wrapper body at `HesabyarInputField.kt:31` (1 occurrence total in the whole app).
5. Done criteria: all commands pass; evidence ready for PR body per AGENTS.md.

## 4. Verification

```bash
./gradlew ktlintFormat --no-daemon
./gradlew ktlintCheck detekt --no-daemon
./gradlew --no-daemon compileDebugKotlin
./gradlew testDebugUnitTest --no-daemon
```

Evidence: paste post-change `HesabyarInputField.kt` contents, the raw `<testcase>` lines for the two migration-affected tests, and the grep results showing 0 raw `OutlinedTextField` in `ui/screens/`.

## 5. Risks, Dependencies, and STOP Conditions

- **Dropdown anchor fields (readOnly, menuAnchor):** `HesabyarInputField` is `String`-typed and wraps `Modifier.fillMaxWidth()`. Dropdown anchors need `readOnly=true` (new Phase 1 param) and a `menuAnchor()` modifier. The internal `modifier.fillMaxWidth()` is acceptable for `PrimaryNotEditable`/`PrimaryScrollable` anchors as long as the caller provides `.menuAnchor(...)` in the `modifier` arg. If Compose layout rejects `weight+fillMaxWidth` chaining for `PersonsScreen`, fall back to letting `HesabyarInputField` not force `fillMaxWidth()` — but that changes the 35 existing call sites' layout. **STOP** if `compileDebugKotlin` or a UI test fails on a dropdown anchor; surface the specific layout error.
- **`supportingText` lambda vs String change:** changing `HesabyarInputField.supportingText` from `String?` to `@Composable (() -> Unit)?` is a source break for the 35 existing `String`-passing callers. Find them via `grep -rn "HesabyarInputField(" app/src` and convert each `supportText = "x"` → `supportingText = { Text("x") }` (or keep both overloads). **STOP** if detekt/ktlint flags unresolved `String` args.
- **`TextFieldValue` overload:** adding `fun HesabyarInputField(value: TextFieldValue, ...)` introduces a second overload; ensure the two overloads share a single private implementation to avoid logic duplication (DRY). **STOP** if detekt `LargerClass`/`LongMethod` triggers on the overload.
- **Person search `Modifier.weight` + `fillMaxWidth`:** Compose applies both; `weight` in a `Row` with `fillMaxWidth()` yields the weighted fill. If the search field visually overflows or the layout breaks, **STOP** and split: keep `PersonsScreen` raw OR make `fillMaxWidth` conditional.
- **Scope discipline:** issue #457 lists 13 call sites in `ui/screens/`; the user expanded scope to 16, adding the 2 passphrase dialogs in `ui/components/` (`ImportPassphraseDialog.kt`, `ExportPassphraseDialog.kt`). After migration, the only file in `app/src/main` that references `OutlinedTextField` is `HesabyarInputField.kt` (its wrapper body). If the user narrows scope back to 13 during execution, re-verify the `grep` evidence excludes `ui/components/`.
- **Hard constraints (auto-satisfied, no Kotlin business logic added):** no money `Float`/`Double` touched, no Room schema change, no secrets, Jalali/offline untouched, no `GlobalScope`, no new Kotlin business logic (this is a UI wrapper-only refactor).
- **Branch:** start from `main` (or the current in-progress branch; the `feat/issue-457-...` branch is the cleanest). Do not stack on Plan 019's branch since that is unrelated design-token cleanup.