# Plan 026: Return Validation Error Codes Across FFI and Map to error_* Keys in Kotlin

- **Issue:** #586
- **PR:** —
- **Created:** 2026-10-29
- **Author:** Claude Code
- **Status:** IN PROGRESS

## 1. Problem Statement

Rust validation in `rust/hesabyar-core/src/validation.rs` currently returns raw English developer strings (e.g. `"Transaction amount must be positive"`, `"Account[0] has empty name"`, `"Invalid backup version"`). These are log-grade messages per `docs/LOCALIZATION.md`, not UI text.

When backup validation fails on the Rust path (`BackupJsonValidator.kt:68` calling `RustBridge.validateBackupPayloadSync`), `rustResult.errors` is passed directly into `BackupValidationResult.Invalid(rustResult.errors)` without any translation or localization.

In `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SettingsScreen.kt:188-191`, backup-import validation failure displays this raw English string directly to the user:
```kotlin
is BackupOperationState.ValidationFailed -> {
  settingsViewModel.showMessage("خطا در اعتبارسنجی: ${state.errors.first()}")
  backupViewModel.clearOperationState()
}
```

This violates:
1. **Persian-First Application Principle (`AGENTS.md`, `docs/LOCALIZATION.md`):** English technical messages are surfaced directly in a Persian-first UI.
2. **Standardized Resource Architecture (`docs/LOCALIZATION.md` §3):** Validation errors must use stable, reusable resource keys prefixed with `error_*`.
3. **Rust/Kotlin Boundary Segregation:** The FFI layer should return structured, language-agnostic codes rather than hardcoded UI messages in a foreign language.

### In-Scope Files

| File | Current Behavior | Target Behavior |
|---|---|---|
| `rust/hesabyar-core/src/validation.rs` | Returns English error strings across batch & entity validation | Returns stable error code strings (`error_*`) across FFI while preserving English messages for logging/tests |
| `rust/hesabyar-core/src/validation/tests.rs` | Asserts on English error substrings in `result.errors` | Asserts on stable `error_*` code strings |
| `app/src/main/res/values/strings.xml` | Has `backup_validation_*` and `backup_error_*` strings; lacks standardized `error_*` validation keys | Adds/aliases standardized `error_*` keys matching the Rust error codes, plus `backup_validation_error_format` |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/BackupJsonValidator.kt:64-85` | Passes raw `rustResult.errors` directly to `BackupValidationResult.Invalid` | Resolves error codes from `rustResult.errors` using Android `Context` / `strings.xml`, falling back to `error_unknown` |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SettingsScreen.kt:188-191` | Hardcodes `"خطا در اعتبارسنجی: ${state.errors.first()}"` | Uses string resource `R.string.backup_validation_error_format` with the localized validation error |

## 2. Goals & Acceptance Criteria

- [ ] **Criterion 1 (No Raw Rust Strings in UI):**
  - No raw English error message from the Rust core reaches any UI screen, snackbar, or dialog.
- [ ] **Criterion 2 (Stable Error Codes across FFI):**
  - `ValidationResult.errors` returned by `validate_backup_payload` contains stable code strings (e.g. `error_tx_amount_invalid`, `error_backup_version_invalid`).
  - Rust retains an internal `ValidationErrorCode` enum with `.as_code()` and `.default_message()` for developer logging and Rust unit testing.
- [ ] **Criterion 3 (strings.xml Completeness):**
  - Every error code emitted by the Rust core has a matching entry in `app/src/main/res/values/strings.xml` prefixed with `error_*` (per `docs/LOCALIZATION.md`).
  - Unknown or unexpected codes gracefully fall back to `error_unknown` (`"خطای ناشناخته"`).
- [ ] **Criterion 4 (Persian Text on Backup-Import Validation Failure):**
  - Triggering an invalid backup restore on `SettingsScreen` displays a fully localized Persian message (e.g. `"خطا در اعتبارسنجی: نسخه پشتیبان نامعتبر است"`).
- [ ] **Criterion 5 (Clean Build, Rust Module Sizes, and Static Analysis):**
  - `cargo test --manifest-path rust/Cargo.toml` passes with 0 failures.
  - `bash scripts/check-rust-module-size.sh` passes without violating 800 lines / 25 functions.
  - `./gradlew testDebugUnitTest testDebugUnitTestRust --no-daemon` passes with 0 failures.
  - `./gradlew ktlintCheck detekt --no-daemon` passes with 0 findings.
  - `python scripts/check_docs.py` passes with no broken references.

## 3. Technical Architecture & Error Code Mapping

### Rust Core Architecture

In `rust/hesabyar-core/src/validation.rs`:
```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ValidationErrorCode {
    BackupVersionInvalid,
    AccountNameBlank,
    AccountDuplicateId,
    AccountInvalidType,
    TransactionInvalidAccount,
    TransactionInvalidDestinationAccount,
    TransactionInvalidLegacyAccount,
    TransactionInvalidLegacyDestinationAccount,
    TransferNoDestination,
    TransferSameSourceDestination,
    TransactionAmountInvalid,
    TransactionDateInvalid,
    TransactionCategoryInvalid,
    TransactionInvalidCategory,
    LoanPersonNameBlank,
    LoanDateInvalid,
    LoanOriginalAmountInvalid,
    LoanRemainingAmountInvalid,
    LoanTypeInvalid,
    LoanInvalidPerson,
    TransactionInvalidPerson,
    InstallmentTitleBlank,
    InstallmentAmountInvalid,
    InstallmentDueDateInvalid,
    BankLoanNameBlank,
    BankLoanReceivedAmountInvalid,
    BankLoanMonthlyInstallmentAmountInvalid,
    BankLoanInstallmentCountInvalid,
    BankLoanStartDateInvalid,
    BankLoanTotalRepayableAmountInvalid,
    BankLoanRepayableOverflow,
    BankLoanRepayableMismatch,
    BankLoanInterestOverflow,
    BankLoanRepayableBelowReceived,
    BankLoanInterestMismatch,
    PaymentAmountInvalid,
    PaymentDateInvalid,
    PaymentLoanIdInvalid,
    PaymentInvalidLoan,
    PersonNameBlank,
    PersonNormalizedBlank,
    PersonDuplicate,
    PersonDuplicateId,
    PaymentDateBeforeCreation,
    PaymentDateBeforeExisting,
    ParsedAmountInvalid,
    ParsedCategoryEmpty,
    ParsedHourInvalid,
    ParsedMinuteInvalid,
}
```

Each code maps to a standardized `error_*` string:
- `as_code(&self) -> &'static str`: Returns the string code (e.g. `"error_tx_amount_invalid"`).
- `default_message(&self) -> &'static str`: Returns the English developer string for logging/tests (e.g. `"Transaction amount must be positive"`).

`ValidationResult.errors` returned over FFI contains `code.as_code().to_string()`.

### Error Code Mapping Table

| Rust `ValidationErrorCode` | FFI String Code | `strings.xml` Resource Key | Persian Translation |
|---|---|---|---|
| `BackupVersionInvalid` | `error_backup_version_invalid` | `error_backup_version_invalid` | نسخه پشتیبان نامعتبر است |
| `AccountNameBlank` | `error_account_name_blank` | `error_account_name_blank` | نام حساب خالی است |
| `AccountDuplicateId` | `error_account_duplicate_id` | `error_account_duplicate_id` | شناسه حساب تکراری است |
| `AccountInvalidType` | `error_account_invalid_type` | `error_account_invalid_type` | نوع حساب نامعتبر است |
| `TransactionInvalidAccount` | `error_transaction_invalid_account` | `error_transaction_invalid_account` | تراکنش به حساب مبدا ناموجود ارجاع می‌دهد |
| `TransactionInvalidDestinationAccount` | `error_transaction_invalid_destination_account` | `error_transaction_invalid_destination_account` | تراکنش به حساب مقصد ناموجود ارجاع می‌دهد |
| `TransactionInvalidLegacyAccount` | `error_transaction_invalid_legacy_account` | `error_transaction_invalid_legacy_account` | تراکنش به حساب غیرقدیمی ارجاع می‌دهد |
| `TransactionInvalidLegacyDestinationAccount` | `error_transaction_invalid_legacy_destination_account` | `error_transaction_invalid_legacy_destination_account` | تراکنش به حساب مقصد غیرقدیمی ارجاع می‌دهد |
| `TransferNoDestination` | `error_transfer_no_destination` | `error_transfer_no_destination` | تراکنش انتقالی حساب مقصد ندارد |
| `TransferSameSourceDestination` | `error_transfer_same_source_destination` | `error_transfer_same_source_destination` | تراکنش انتقالی مبدا و مقصد یکسان دارند |
| `TransactionAmountInvalid` | `error_tx_amount_invalid` | `error_tx_amount_invalid` | مبلغ تراکنش نامعتبر است |
| `TransactionDateInvalid` | `error_tx_date_invalid` | `error_tx_date_invalid` | تاریخ تراکنش نامعتبر است |
| `TransactionCategoryInvalid` | `error_tx_category_invalid` | `error_tx_category_invalid` | دسته‌بندی تراکنش نامعتبر است |
| `TransactionInvalidCategory` | `error_transaction_invalid_category` | `error_transaction_invalid_category` | تراکنش به دسته‌بندی ناموجود ارجاع می‌دهد |
| `LoanPersonNameBlank` | `error_loan_person_name_blank` | `error_loan_person_name_blank` | نام شخص وام خالی است |
| `LoanDateInvalid` | `error_loan_date_invalid` | `error_loan_date_invalid` | تاریخ وام نامعتبر است |
| `LoanOriginalAmountInvalid` | `error_loan_original_amount_invalid` | `error_loan_original_amount_invalid` | مبلغ اولیه وام نامعتبر است |
| `LoanRemainingAmountInvalid` | `error_loan_remaining_amount_invalid` | `error_loan_remaining_amount_invalid` | مبلغ باقی‌مانده وام نامعتبر است |
| `LoanTypeInvalid` | `error_loan_type_invalid` | `error_loan_type_invalid` | نوع وام نامعتبر است |
| `LoanInvalidPerson` | `error_loan_invalid_person` | `error_loan_invalid_person` | وام به شخص ناموجود ارجاع می‌دهد |
| `TransactionInvalidPerson` | `error_transaction_invalid_person` | `error_transaction_invalid_person` | تراکنش به شخص ناموجود ارجاع می‌دهد |
| `InstallmentTitleBlank` | `error_installment_title_blank` | `error_installment_title_blank` | عنوان قسط خالی است |
| `InstallmentAmountInvalid` | `error_installment_amount_invalid` | `error_installment_amount_invalid` | مبلغ قسط نامعتبر است |
| `InstallmentDueDateInvalid` | `error_installment_due_date_invalid` | `error_installment_due_date_invalid` | تاریخ سررسید قسط نامعتبر است |
| `BankLoanNameBlank` | `error_bank_loan_name_blank` | `error_bank_loan_name_blank` | نام بانک وام خالی است |
| `BankLoanReceivedAmountInvalid` | `error_bank_loan_received_amount_invalid` | `error_bank_loan_received_amount_invalid` | مبلغ دریافتی وام نامعتبر است |
| `BankLoanMonthlyInstallmentAmountInvalid` | `error_bank_loan_monthly_installment_amount_invalid` | `error_bank_loan_monthly_installment_amount_invalid` | مبلغ قسط ماهانه وام نامعتبر است |
| `BankLoanInstallmentCountInvalid` | `error_bank_loan_installment_count_invalid` | `error_bank_loan_installment_count_invalid` | تعداد اقساط وام نامعتبر است |
| `BankLoanStartDateInvalid` | `error_bank_loan_start_date_invalid` | `error_bank_loan_start_date_invalid` | تاریخ شروع وام نامعتبر است |
| `BankLoanTotalRepayableAmountInvalid` | `error_bank_loan_total_repayable_amount_invalid` | `error_bank_loan_total_repayable_amount_invalid` | مبلغ کل بازپرداخت وام نامعتبر است |
| `BankLoanRepayableOverflow` | `error_bank_loan_repayable_overflow` | `error_bank_loan_repayable_overflow` | مبلغ کل بازپرداخت وام در محاسبه سرریز می‌کند |
| `BankLoanRepayableMismatch` | `error_bank_loan_repayable_mismatch` | `error_bank_loan_repayable_mismatch` | مبلغ کل بازپرداخت وام با حاصل ضرب اقساط برابر نیست |
| `BankLoanInterestOverflow` | `error_bank_loan_interest_overflow` | `error_bank_loan_interest_overflow` | محاسبه سود وام سرریز می‌کند |
| `BankLoanRepayableBelowReceived` | `error_bank_loan_repayable_below_received` | `error_bank_loan_repayable_below_received` | مبلغ کل بازپرداخت وام نباید کمتر از مبلغ دریافتی باشد |
| `BankLoanInterestMismatch` | `error_bank_loan_interest_mismatch` | `error_bank_loan_interest_mismatch` | سود وام با مابه‌التفاوت بازپرداخت و مبلغ دریافتی برابر نیست |
| `PaymentAmountInvalid` | `error_payment_amount_invalid` | `error_payment_amount_invalid` | مبلغ پرداخت نامعتبر است |
| `PaymentDateInvalid` | `error_payment_date_invalid` | `error_payment_date_invalid` | تاریخ پرداخت نامعتبر است |
| `PaymentLoanIdInvalid` | `error_payment_loan_id_invalid` | `error_payment_loan_id_invalid` | شناسه وام پرداخت نامعتبر است |
| `PaymentInvalidLoan` | `error_payment_invalid_loan` | `error_payment_invalid_loan` | پرداخت به وام ناموجود ارجاع می‌دهد |
| `PersonNameBlank` | `error_person_name_blank` | `error_person_name_blank` | نام شخص خالی است |
| `PersonNormalizedBlank` | `error_person_normalized_blank` | `error_person_normalized_blank` | نام یکتای شخص خالی است |
| `PersonDuplicate` | `error_person_duplicate` | `error_person_duplicate` | نام یکتای شخص تکراری است |
| `PersonDuplicateId` | `error_person_duplicate_id` | `error_person_duplicate_id` | شناسه شخص تکراری است |
| `PaymentDateBeforeCreation` | `error_payment_date_before_creation` | `error_payment_date_before_creation` | تاریخ پرداخت قبل از تاریخ ایجاد وام است |
| `PaymentDateBeforeExisting` | `error_payment_date_before_existing` | `error_payment_date_before_existing` | تاریخ پرداخت قبل از تاریخ پرداخت‌های قبلی است |
| `ParsedAmountInvalid` | `error_parsed_amount_invalid` | `error_parsed_amount_invalid` | مبلغ تحلیل‌شده نامعتبر است |
| `ParsedCategoryEmpty` | `error_parsed_category_empty` | `error_parsed_category_empty` | دسته‌بندی تحلیل‌شده خالی است |
| `ParsedHourInvalid` | `error_parsed_hour_invalid` | `error_parsed_hour_invalid` | ساعت تحلیل‌شده نامعتبر است |
| `ParsedMinuteInvalid` | `error_parsed_minute_invalid` | `error_parsed_minute_invalid` | دقیقه تحلیل‌شده نامعتبر است |
| `Unknown` (fallback) | `*` | `error_unknown` | خطای ناشناخته |

## 4. Implementation Steps

### Phase 0: Baseline & Test Verification (DONE)
1. [x] Verify working tree is clean.
2. [x] Run baseline unit tests and lints:
   ```bash
   cargo test --manifest-path rust/Cargo.toml
   ./gradlew testDebugUnitTest --no-daemon
   bash scripts/check-rust-module-size.sh
   python scripts/check_docs.py
   ```

### Phase 1: Rust Core Error Codes & Refactor (DONE)
1. Define `ValidationErrorCode` in `rust/hesabyar-core/src/validation.rs` (or submodule `validation/error.rs` to maintain line thresholds under 800 lines).
2. Refactor `validation.rs` functions (`validate_transaction_fields`, `validate_accounts_and_references`, `validate_persons`, `validate_backup_payload`, etc.) to generate `ValidationErrorCode`.
3. In `ValidationResult`, emit stable code strings (`code.as_code().to_string()`).
4. Update Rust unit tests in `rust/hesabyar-core/src/validation/tests.rs` to assert on error codes.
5. Run `cargo test --manifest-path rust/Cargo.toml` and `bash scripts/check-rust-module-size.sh`.

### Phase 2: UniFFI Binding Generation & Verification
1. Regenerate Kotlin bindings:
   ```bash
   ./gradlew --no-daemon :app:generateAndFixBindings
   ```
2. Verify UniFFI checksum and generated wrappers.

### Phase 3: Kotlin String Resources & Resolution
1. In `app/src/main/res/values/strings.xml`:
   - Add/standardize `error_*` string resources matching all error codes.
   - Add `backup_validation_error_format` (`"خطا در اعتبارسنجی: %1$s"`).
2. In `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/BackupJsonValidator.kt`:
   - Implement `resolveValidationErrorCode(code: String): String`.
   - Map each code string to its `@StringRes` ID, defaulting to `R.string.error_unknown` if unrecognized.
   - In `validateBackup`, resolve `rustResult.errors` before constructing `BackupValidationResult.Invalid`.

### Phase 4: UI & Context Wiring
1. In `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SettingsScreen.kt:188-191`:
   - Replace string interpolation with `stringResource(R.string.backup_validation_error_format, state.errors.first())` (or equivalent context resolution).
2. Update unit tests in `BackupJsonValidatorTest.kt`, `BackupJsonValidatorContextTest.kt`, and `BackupImportCoordinatorTest.kt` to verify that localized Persian messages are returned and displayed.

### Phase 5: Verification & Full Suite Checks
1. Run full unit tests:
   ```bash
   ./gradlew testDebugUnitTest --no-daemon
   ./gradlew testDebugUnitTestRust --no-daemon
   ```
2. Run code style and static analysis:
   ```bash
   ./gradlew ktlintFormat ktlintCheck detekt --no-daemon
   ```
3. Run doc reference check:
   ```bash
   python scripts/check_docs.py
   ```
4. Verify Rust module size check:
   ```bash
   bash scripts/check-rust-module-size.sh
   ```

## 5. Evidence and Verification Plan

### Evidence Standard
Per `AGENTS.md` and `docs/agents/testing.md`:
1. Exact file contents of modified code on disk.
2. Raw test-runner outputs showing named tests passing (e.g. `BackupJsonValidatorContextTest`, `RustBridgeTest`, cargo test cases).
3. Zero detekt and ktlint findings without suppressing new rules.
4. Clean `scripts/check_docs.py` and `scripts/check-rust-module-size.sh`.

## 6. Risks, Dependencies, and STOP Conditions

### Risks & Mitigations
- **UniFFI ABI Invariance:** `ValidationResult` continues to return `is_valid: bool` and `errors: Vec<String>`, ensuring backwards and binary compatibility with existing FFI signatures.
- **Module Size Limits:** `validation.rs` is at 759 lines. To prevent exceeding the 800-line threshold, `ValidationErrorCode` definition and mapping methods should live in `rust/hesabyar-core/src/validation/error.rs` <!-- check-docs: ignore --> or `codes.rs`.
- **Test Context Sentinel:** JVM unit tests without Android `Context` must still cleanly receive `<string-res-...>` or resolved keys without crashing (guarding against DI regression).

### STOP Conditions
- **STOP if `scripts/check-rust-module-size.sh` reports any module exceeds 800 lines or 25 functions.**
- **STOP if UniFFI API checksum validation fails during test execution.**
- **STOP if any existing backup validation rule diverges between Kotlin fallback and Rust core.**
