// RUST-LINT-BASELINE: `validation.rs` + `validation/tests.rs` is the
// child-module pattern for the split test module. The lint prefers
// `validation/mod.rs`, but the split keeps the production file path stable.
#![allow(clippy::self_named_module_files)]

use crate::models::*;

// WARNING: Must match Kotlin AccountType enum exactly.
// Add new entries here when AccountType gains new variants in the app.
const VALID_ACCOUNT_TYPES: &[&str] = &["BANK", "CASH_WALLET", "SAVINGS_INVESTMENT", "OTHER"];

/// Result of batch validation — collects all errors.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ValidationResult {
    pub is_valid: bool,
    pub errors: Vec<String>,
}

impl Default for ValidationResult {
    fn default() -> Self {
        Self {
            is_valid: true,
            errors: vec![],
        }
    }
}

// ===========================================================================
// Single-entity validators (return first error)
// ===========================================================================

/// Validate the shared transaction fields (amount, date, category_id) that
/// apply to every transaction type. Transfer-specific invariants (destination
/// present, destination != source) are checked by the shared
/// [check_transfer_structure] helper, called from both [validate_transaction]
/// and [validate_accounts_and_references], so they are not duplicated (or
/// forgotten) when `validate_backup_payload` runs both paths.
#[inline(always)]
fn validate_transaction_fields(tx: &Transaction) -> Result<(), String> {
    if tx.amount <= 0 {
        return Err("Transaction amount must be positive".into());
    }
    if tx.date <= 0 {
        return Err("Transaction date must be positive".into());
    }
    // Note: empty descriptions and non-positive category_id are tolerated here
    // (instead of rejected) so that backups exported by older versions of the
    // app — which allowed blank descriptions and defaulted missing category ids
    // to 1 — can still be restored. A negative category_id is still rejected as
    // it can never reference a valid category.
    if tx.category_id < 0 {
        return Err("Transaction category_id must not be negative".into());
    }
    Ok(())
}

/// Violation of a Transfer's structural invariants, shared between
/// [`validate_transaction`] (fail-fast FFI path) and
/// [`validate_accounts_and_references`] (collect-all batch path) so the
/// destination/source rules cannot drift when one is modified.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum TransferIssue {
    MissingDestination,
    SameSourceAndDestination,
}

/// Check Transfer-specific structural invariants (destination present,
/// destination != source) shared by both validation paths.
///
/// Returns `Some(issue)` for the first violation found, or `None` for a valid
/// transaction (including non-Transfer types, which are accepted here).
#[inline(always)]
fn check_transfer_structure(tx: &Transaction) -> Option<TransferIssue> {
    if tx.tx_type != TransactionType::Transfer {
        return None;
    }
    if tx.destination_account_id.is_none() {
        return Some(TransferIssue::MissingDestination);
    }
    if tx.destination_account_id == Some(tx.account_id) {
        return Some(TransferIssue::SameSourceAndDestination);
    }
    None
}

/// Validate a single transaction.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
#[inline(always)]
pub fn validate_transaction(tx: &Transaction) -> Result<(), String> {
    validate_transaction_fields(tx)?;
    // The five non-Transfer types are accepted unconditionally; Transfer
    // requires a valid destination account.
    if let Some(issue) = check_transfer_structure(tx) {
        let msg = match issue {
            TransferIssue::MissingDestination => "Transfer must have a destination_account_id",
            TransferIssue::SameSourceAndDestination => "Transfer source and destination accounts must differ",
        };
        return Err(msg.into());
    }
    Ok(())
}

/// Validate a single loan.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_loan(loan: &Loan) -> Result<(), String> {
    if loan.person_name.is_empty() {
        return Err("Loan person_name must not be empty".into());
    }
    if loan.date <= 0 {
        return Err("Loan date must be positive".into());
    }
    if loan.original_amount <= 0 {
        return Err("Loan original_amount must be positive".into());
    }
    if loan.remaining_amount < 0 {
        return Err("Loan remaining_amount must be non-negative".into());
    }
    // Note: remaining_amount may exceed original_amount for backups created by
    // older app versions (e.g. after partial manual edits). Tolerated on import
    // rather than hard-rejected so those backups can still be restored.
    if loan.loan_type != "DEBTOR" && loan.loan_type != "CREDITOR" {
        return Err(format!(
            "Loan type must be DEBTOR or CREDITOR, got '{}'",
            loan.loan_type
        ));
    }
    Ok(())
}

/// Validate a loan payment date.
///
/// Keeps recorded payments chronologically ordered. A payment must not predate
/// the loan creation date or any already-recorded payment date. Backdated
/// payments that cross these bounds corrupt point-in-time balances.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_loan_payment_date(
    loan_creation_date: i64,
    existing_payment_dates: &[i64],
    new_payment_date: i64,
) -> Result<(), String> {
    if new_payment_date <= 0 {
        return Err("Loan payment date must be positive".into());
    }
    if new_payment_date < loan_creation_date {
        return Err("Loan payment date is before the loan creation date".into());
    }
    if existing_payment_dates.iter().any(|d| *d > new_payment_date) {
        return Err("Loan payment date is before an existing payment date".into());
    }
    Ok(())
}

/// Validate a single installment.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_installment(inst: &Installment) -> Result<(), String> {
    if inst.title.is_empty() {
        return Err("Installment title must not be empty".into());
    }
    if inst.amount <= 0 {
        return Err("Installment amount must be positive".into());
    }
    if inst.due_date <= 0 {
        return Err("Installment due_date must be positive".into());
    }
    Ok(())
}

/// Validate a single ParsedResult (AI parser output).
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_parsed_result(result: &ParsedResult) -> Result<(), String> {
    if result.amount <= 0 {
        return Err("ParsedResult amount must be positive".into());
    }
    // All six TransactionType variants are valid.
    match result.tx_type {
        TransactionType::Expense
        | TransactionType::Income
        | TransactionType::LoanDebtor
        | TransactionType::LoanCreditor
        | TransactionType::Installment
        | TransactionType::Transfer => {}
    }
    if result.category.is_empty() {
        return Err("ParsedResult category must not be empty".into());
    }
    if let Some(hour) = result.hour {
        if !(0..=23).contains(&hour) {
            return Err(format!("ParsedResult hour must be 0-23, got {}", hour));
        }
    }
    if let Some(minute) = result.minute {
        if !(0..=59).contains(&minute) {
            return Err(format!("ParsedResult minute must be 0-59, got {}", minute));
        }
    }
    Ok(())
}

// ===========================================================================
// Batch validators (collect all errors)
// ===========================================================================

/// Validate a batch of transactions. Collects all errors.
///
/// Transfer-specific invariants (destination present, destination != source)
/// are intentionally NOT checked here — `validate_accounts_and_references`
/// (called first by `validate_backup_payload`) covers them via the shared
/// [check_transfer_structure] helper. Checking them again would produce
/// duplicate errors for the same violation.
#[inline]
pub fn validate_transaction_batch(transactions: &[Transaction]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, tx) in transactions.iter().enumerate() {
        if let Err(e) = validate_transaction_fields(tx) {
            errors.push(format!("Transaction[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate a batch of loans. Collects all errors.
pub fn validate_loan_batch(loans: &[Loan]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, loan) in loans.iter().enumerate() {
        if let Err(e) = validate_loan(loan) {
            errors.push(format!("Loan[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate a batch of installments. Collects all errors.
pub fn validate_installment_batch(installments: &[Installment]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, inst) in installments.iter().enumerate() {
        if let Err(e) = validate_installment(inst) {
            errors.push(format!("Installment[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate a single bank loan.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_bank_loan(bl: &BankLoan) -> Result<(), String> {
    if bl.bank_name.trim().is_empty() {
        return Err("BankLoan bank_name must not be empty".into());
    }
    if bl.received_amount <= 0 {
        return Err("BankLoan received_amount must be positive".into());
    }
    if bl.monthly_installment_amount <= 0 {
        return Err("BankLoan monthly_installment_amount must be positive".into());
    }
    if bl.number_of_installments <= 0 {
        return Err("BankLoan number_of_installments must be positive".into());
    }
    if bl.start_date <= 0 {
        return Err("BankLoan start_date must be positive".into());
    }
    if bl.total_repayable_amount <= 0 {
        return Err("BankLoan total_repayable_amount must be positive".into());
    }
    // Repayment/interest relationships, using checked arithmetic to reject overflow.
    let expected_repayable = bl
        .monthly_installment_amount
        .checked_mul(bl.number_of_installments as i64)
        .ok_or("BankLoan repayable amount overflows (monthly_installment_amount * number_of_installments)")?;
    if bl.total_repayable_amount != expected_repayable {
        return Err(
            "BankLoan total_repayable_amount must equal monthly_installment_amount * number_of_installments".into(),
        );
    }
    let expected_interest = bl
        .total_repayable_amount
        .checked_sub(bl.received_amount)
        .ok_or("BankLoan interest calculation overflows")?;
    if expected_interest < 0 {
        return Err("BankLoan total_repayable_amount must not be less than received_amount".into());
    }
    if bl.total_interest != expected_interest {
        return Err("BankLoan total_interest must equal total_repayable_amount - received_amount".into());
    }
    Ok(())
}

/// Validate a batch of bank loans. Collects all errors.
pub fn validate_bank_loan_batch(bank_loans: &[BankLoan]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, bl) in bank_loans.iter().enumerate() {
        if let Err(e) = validate_bank_loan(bl) {
            errors.push(format!("BankLoan[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate a single payment history entry.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_payment_history(ph: &PaymentHistory) -> Result<(), String> {
    if ph.amount <= 0 {
        return Err("PaymentHistory amount must be positive".into());
    }
    if ph.date <= 0 {
        return Err("PaymentHistory date must be positive".into());
    }
    if ph.loan_id <= 0 {
        return Err("PaymentHistory loan_id must be positive".into());
    }
    Ok(())
}

/// Validate a batch of payment histories. Collects all errors.
pub fn validate_payment_history_batch(payment_histories: &[PaymentHistory]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, ph) in payment_histories.iter().enumerate() {
        if let Err(e) = validate_payment_history(ph) {
            errors.push(format!("PaymentHistory[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate accounts and cross-reference transactions against them.
///
/// This is the single source of truth for account validation and
/// referential-integrity checks on transaction account fields. Both
/// `validate_backup_payload` (collect-all-errors path) and `validate_backup`
/// in `lib.rs` (fail-fast FFI path) call this function to avoid drift.
///
/// Legacy backups (pre-multi-account) omit the `accounts` list entirely.
/// When `accounts` is empty but transactions exist, every transaction must
/// reference the legacy default account ID (1). This prevents corrupted
/// backups from injecting arbitrary/non-existent account IDs that would
/// create orphaned balances after restore.
#[allow(clippy::too_many_lines, clippy::cognitive_complexity)] // RUST-LINT-BASELINE
pub fn validate_accounts_and_references(payload: &BackupPayload) -> Vec<String> {
    let mut errors = Vec::new();

    // --- Account structural validation ---
    let mut seen_ids: std::collections::HashSet<i64> = std::collections::HashSet::with_capacity(payload.accounts.len());
    for (i, acc) in payload.accounts.iter().enumerate() {
        if acc.name.trim().is_empty() {
            errors.push(format!("Account[{}] has empty name", i));
        }
        if !seen_ids.insert(acc.id) {
            errors.push(format!("Duplicate account id {}", acc.id));
        }
        if !VALID_ACCOUNT_TYPES.contains(&acc.account_type.as_str()) {
            errors.push(format!("Account[{}] has invalid type '{}'", i, acc.account_type));
        }
    }

    // --- Transaction account reference + transfer structure validation ---
    // Account-reference errors are collected first; transfer-structure errors
    // are buffered and appended afterwards so that errors.first() (used by
    // validate_backup's fail-fast FFI path) sees the same class ordering as
    // the original two-pass implementation. The two collections are still
    // produced in a single traversal of the transaction slice — only one
    // allocation (Option<HashSet>) is skipped when accounts is empty.
    if !payload.transactions.is_empty() {
        let account_ids: Option<std::collections::HashSet<i64>> = if !payload.accounts.is_empty() {
            Some(payload.accounts.iter().map(|a| a.id).collect())
        } else {
            None
        };

        let mut transfer_errors: Vec<String> = Vec::new();

        for (i, tx) in payload.transactions.iter().enumerate() {
            if let Some(ref ids) = account_ids {
                // Modern backup: validate against declared accounts.
                if !ids.contains(&tx.account_id) {
                    errors.push(format!(
                        "Transaction[{}] references non-existent source account {}",
                        i, tx.account_id
                    ));
                }
                if let Some(dest_id) = tx.destination_account_id {
                    if !ids.contains(&dest_id) {
                        errors.push(format!(
                            "Transaction[{}] references non-existent destination account {}",
                            i, dest_id
                        ));
                    }
                }
            } else {
                // Legacy backup (no accounts list): every transaction must
                // reference the legacy default account ID. This is the single
                // account that existed before multi-account support. Rejecting
                // arbitrary IDs here prevents orphaned balances from tampered
                // old backups.
                if tx.account_id != DEFAULT_ACCOUNT_ID {
                    errors.push(format!(
                        "Transaction[{}] references non-legacy account {} (accounts list is empty; expected {})",
                        i, tx.account_id, DEFAULT_ACCOUNT_ID
                    ));
                }
                if let Some(dest_id) = tx.destination_account_id {
                    if dest_id != DEFAULT_ACCOUNT_ID {
                        errors.push(format!(
                            "Transaction[{}] references non-legacy destination account {} (accounts list is empty; expected {})",
                            i, dest_id, DEFAULT_ACCOUNT_ID
                        ));
                    }
                }
            }

            // Transfer structure uses the shared check_transfer_structure
            // helper so these invariants cannot drift from validate_transaction
            // (the fail-fast FFI path). Each caller formats its own message.
            // Errors are buffered and appended after the account-ref loop to
            // preserve the ordering expected by validate_backup (errors.first()).
            if let Some(issue) = check_transfer_structure(tx) {
                transfer_errors.push(format!(
                    "Transaction[{}] {}",
                    i,
                    match issue {
                        TransferIssue::MissingDestination => "is a Transfer but has no destination_account_id",
                        TransferIssue::SameSourceAndDestination =>
                            "Transfer source and destination accounts must differ",
                    }
                ));
            }
        }

        errors.extend(transfer_errors);
    }

    errors
}

/// Validate person records and cross-reference loans/transactions against them.
///
/// Both `validate_backup_payload` (collect-all path) and `validate_backup`
/// (fail-fast FFI path) call this so person validation cannot be skipped by
/// either entry point. Field checks: blank name, blank derived key, duplicate
/// derived key, duplicate source ID. Cross-reference checks: positive
/// `person_id` on loans and transactions must point to a declared person.
#[allow(clippy::too_many_lines, clippy::cognitive_complexity)] // RUST-LINT-BASELINE
pub fn validate_persons(payload: &BackupPayload) -> Vec<String> {
    let mut errors = Vec::new();

    if !payload.persons.is_empty() {
        // Field checks: blank name, blank derived key, duplicate derived key,
        // and duplicate source IDs. The key is derived from `name` and never
        // read from the backup-supplied `normalized_name` (mirrors the Kotlin
        // fallback in BackupJsonValidator so both paths agree).
        let mut seen_person_keys = std::collections::HashSet::with_capacity(payload.persons.len());
        let mut person_id_counts: std::collections::HashMap<i64, usize> =
            std::collections::HashMap::with_capacity(payload.persons.len());

        for (i, p) in payload.persons.iter().enumerate() {
            // Mirror Kotlin `p.name.isBlank()` (Character.isWhitespace || isSpaceChar) —
            // see `is_java_whitespace`. `str::trim` uses Unicode White_Space, which
            // would diverge, so check every char explicitly.
            let name_is_blank = p.name.is_empty()
                || p.name.chars().all(|c| {
                    is_java_whitespace(c) || matches!(c, '\u{200B}' | '\u{200C}' | '\u{200D}' | '\u{2060}' | '\u{FEFF}')
                });
            if name_is_blank {
                errors.push(format!("Person[{}] has a blank name", i));
            }
            let key = normalize_person_name(&p.name);
            if key.is_empty() {
                errors.push(format!("Person[{}] has a blank normalizedName", i));
            } else if !seen_person_keys.insert(key) {
                errors.push(format!("Person[{}] has a duplicate normalizedName", i));
            }
            *person_id_counts.entry(p.id).or_insert(0) += 1;
        }

        // Duplicate source IDs: the restore path maps source IDs to local rows with
        // `associate`, so a later entry silently overwrites the earlier mapping and
        // loans/transactions referencing that ID resolve to the wrong person.
        let mut duplicate_person_ids: Vec<i64> = person_id_counts
            .into_iter()
            .filter(|(_, count)| *count > 1)
            .map(|(id, _)| id)
            .collect();
        // Sort so the reported errors are deterministic across runs.
        duplicate_person_ids.sort_unstable();
        for id in duplicate_person_ids {
            errors.push(format!("Person has a duplicate id {}", id));
        }

        // Cross-reference: positive person_id must point to a declared person.
        // Zero is a legacy default tolerated in all cases.
        let person_ids: std::collections::HashSet<_> = payload.persons.iter().map(|p| p.id).collect();
        for (i, loan) in payload.loans.iter().enumerate() {
            if let Some(pid) = loan.person_id {
                if pid > 0 && !person_ids.contains(&pid) {
                    errors.push(format!("Loan[{}] references non-existent person {}", i, pid));
                }
            }
        }
        for (i, tx) in payload.transactions.iter().enumerate() {
            if let Some(pid) = tx.person_id {
                if pid > 0 && !person_ids.contains(&pid) {
                    errors.push(format!("Transaction[{}] references non-existent person {}", i, pid));
                }
            }
        }
    } else {
        // Fast path for legacy or person-free backups: no persons declared, so
        // any positive person_id in loans or transactions is an orphan.
        for (i, loan) in payload.loans.iter().enumerate() {
            if let Some(pid) = loan.person_id {
                if pid > 0 {
                    errors.push(format!("Loan[{}] references non-existent person {}", i, pid));
                }
            }
        }
        for (i, tx) in payload.transactions.iter().enumerate() {
            if let Some(pid) = tx.person_id {
                if pid > 0 {
                    errors.push(format!("Transaction[{}] references non-existent person {}", i, pid));
                }
            }
        }
    }

    errors
}

/// Mirrors the Kotlin `PersonNameNormalizer` (app/…/domain/utils/PersonNameNormalizer.kt)
/// for validation purposes only.
///
/// The stored dedup key is produced by the Kotlin util, which ADR-001 lists as a
/// permanent Kotlin fallback. This function exists so the Rust validation path
/// judges a payload by the same key the restore path will derive from `name`,
/// instead of trusting the backup-supplied `normalized_name`. A tampered pair
/// such as `name = "Ali", normalized_name = "reza"` would otherwise either bind
/// Ali's records to Reza's identity or trip a false duplicate error.
///
/// It must EXACTLY mirror `PersonNameNormalizer.normalize` (Kotlin):
/// - whitespace uses Java `Character.isWhitespace` (see `is_java_whitespace`),
///   which EXCLUDES NBSP/NNBSP/NARROW-NBSP — Rust's `char::is_whitespace`
///   (Unicode White_Space) would fold NBSP to a space and diverge from Kotlin;
/// - case folding mirrors Kotlin `Char.lowercaseChar` (Java
///   `Character.toLowerCase`): the Unicode SIMPLE lowercase (a single code
///   point) is applied, or the char is kept unchanged when no single-char
///   lowercase exists. `İ` (U+0130) is the common char whose simple
///   lowercase is a single `i` while Rust's full `to_lowercase` expands to
///   two code points, so it is mapped explicitly to stay in parity.
///
/// Used only to reject or accept a payload. It never writes a key, so any drift
/// from the Kotlin util costs a wrong accept/reject, never data corruption.
#[allow(clippy::too_many_lines)] // RUST-LINT-BASELINE
fn normalize_person_name(name: &str) -> String {
    let mut out = String::with_capacity(name.len());
    let mut pending_space = false;
    for raw in name.chars() {
        // Lam-alef presentation-form ligatures (FEF5-FEFC) fold to the two-char
        // canonical lam + alef sequence, mirroring the Kotlin map exactly.
        match raw {
            '\u{FEF5}' | '\u{FEF6}' | '\u{FEF7}' | '\u{FEF8}' | '\u{FEF9}' | '\u{FEFA}' | '\u{FEFB}' | '\u{FEFC}' => {
                if pending_space && !out.is_empty() {
                    out.push(' ');
                }
                pending_space = false;
                out.push_str("\u{0644}\u{0627}");
                continue;
            }
            _ => {}
        }
        // Single-character Arabic-script fold to the Persian counterpart.
        let folded =
            match raw {
                // yeh variants -> Persian yeh
                '\u{064A}' | '\u{0649}' | '\u{0626}' => '\u{06CC}',
                // kaf -> Persian keheh
                '\u{0643}' => '\u{06A9}',
                // teh marbuta -> heh
                '\u{0629}' => '\u{0647}',
                // alef variants -> plain alef
                '\u{0623}' | '\u{0625}' | '\u{0622}' | '\u{0671}' => '\u{0627}',
                // waw with hamza -> waw
                '\u{0624}' => '\u{0648}',
                // Presentation forms of the individual letters (isolated/final/
                // initial/medial, U+FB50-U+FEFF). PDF-pasted or shaped-render text
                // can carry these instead of the base letters; they fold to the
                // same targets as the base letters so both sides share one key
                // (mirrors the Kotlin map in PersonNameNormalizer.kt).
                '\u{FE80}' => '\u{0621}',
                '\u{FB50}' | '\u{FB51}' | '\u{FE81}' | '\u{FE82}' | '\u{FE83}' | '\u{FE84}' | '\u{FE87}'
                | '\u{FE88}' | '\u{FE8D}' | '\u{FE8E}' => '\u{0627}',
                '\u{FE8F}' | '\u{FE90}' | '\u{FE91}' | '\u{FE92}' => '\u{0628}',
                '\u{FE95}' | '\u{FE96}' | '\u{FE97}' | '\u{FE98}' => '\u{062A}',
                '\u{FE99}' | '\u{FE9A}' | '\u{FE9B}' | '\u{FE9C}' => '\u{062B}',
                '\u{FE9D}' | '\u{FE9E}' | '\u{FE9F}' | '\u{FEA0}' => '\u{062C}',
                '\u{FEA1}' | '\u{FEA2}' | '\u{FEA3}' | '\u{FEA4}' => '\u{062D}',
                '\u{FEA5}' | '\u{FEA6}' | '\u{FEA7}' | '\u{FEA8}' => '\u{062E}',
                '\u{FEA9}' | '\u{FEAA}' => '\u{062F}',
                '\u{FEAB}' | '\u{FEAC}' => '\u{0630}',
                '\u{FEAD}' | '\u{FEAE}' => '\u{0631}',
                '\u{FEAF}' | '\u{FEB0}' => '\u{0632}',
                '\u{FEB1}' | '\u{FEB2}' | '\u{FEB3}' | '\u{FEB4}' => '\u{0633}',
                '\u{FEB5}' | '\u{FEB6}' | '\u{FEB7}' | '\u{FEB8}' => '\u{0634}',
                '\u{FEB9}' | '\u{FEBA}' | '\u{FEBB}' | '\u{FEBC}' => '\u{0635}',
                '\u{FEBD}' | '\u{FEBE}' | '\u{FEBF}' | '\u{FEC0}' => '\u{0636}',
                '\u{FEC1}' | '\u{FEC2}' | '\u{FEC3}' | '\u{FEC4}' => '\u{0637}',
                '\u{FEC5}' | '\u{FEC6}' | '\u{FEC7}' | '\u{FEC8}' => '\u{0638}',
                '\u{FEC9}' | '\u{FECA}' | '\u{FECB}' | '\u{FECC}' => '\u{0639}',
                '\u{FECD}' | '\u{FECE}' | '\u{FECF}' | '\u{FED0}' => '\u{063A}',
                '\u{FED1}' | '\u{FED2}' | '\u{FED3}' | '\u{FED4}' => '\u{0641}',
                '\u{FED5}' | '\u{FED6}' | '\u{FED7}' | '\u{FED8}' => '\u{0642}',
                '\u{FEDD}' | '\u{FEDE}' | '\u{FEDF}' | '\u{FEE0}' => '\u{0644}',
                '\u{FEE1}' | '\u{FEE2}' | '\u{FEE3}' | '\u{FEE4}' => '\u{0645}',
                '\u{FEE5}' | '\u{FEE6}' | '\u{FEE7}' | '\u{FEE8}' => '\u{0646}',
                '\u{FE93}' | '\u{FE94}' | '\u{FEE9}' | '\u{FEEA}' | '\u{FEEB}' | '\u{FEEC}' => '\u{0647}',
                '\u{FE85}' | '\u{FE86}' | '\u{FEED}' | '\u{FEEE}' => '\u{0648}',
                '\u{FB56}' | '\u{FB57}' | '\u{FB58}' | '\u{FB59}' => '\u{067E}',
                '\u{FB7A}' | '\u{FB7B}' | '\u{FB7C}' | '\u{FB7D}' => '\u{0686}',
                '\u{FB8A}' | '\u{FB8B}' => '\u{0698}',
                '\u{FB8E}' | '\u{FB8F}' | '\u{FB90}' | '\u{FB91}' | '\u{FED9}' | '\u{FEDA}' | '\u{FEDB}'
                | '\u{FEDC}' => '\u{06A9}',
                '\u{FB92}' | '\u{FB93}' | '\u{FB94}' | '\u{FB95}' => '\u{06AF}',
                '\u{FBFC}' | '\u{FBFD}' | '\u{FBFE}' | '\u{FBFF}' | '\u{FBE8}' | '\u{FBE9}' | '\u{FE89}'
                | '\u{FE8A}' | '\u{FE8B}' | '\u{FE8C}' | '\u{FEEF}' | '\u{FEF0}' | '\u{FEF1}' | '\u{FEF2}'
                | '\u{FEF3}' | '\u{FEF4}' => '\u{06CC}',
                other => other,
            };
        match folded {
            // Zero width: ZWSP, ZWNJ, ZWJ, word joiner, BOM.
            '\u{200B}' | '\u{200C}' | '\u{200D}' | '\u{2060}' | '\u{FEFF}' => {}
            c if is_java_whitespace(c) => {
                pending_space = !out.is_empty();
            }
            c => {
                if pending_space && !out.is_empty() {
                    out.push(' ');
                }
                pending_space = false;
                // Simple (single-codepoint) case fold to mirror Kotlin
                // `Char.lowercaseChar()` (Java `Character.toLowerCase`): the
                // Unicode SIMPLE lowercase is used when it is a single code
                // point, otherwise the original char is kept unchanged.
                // Rust `char::to_lowercase()` is the FULL mapping and can yield
                // several code points; for `İ` (U+0130) it yields "i\u{307}"
                // while Kotlin's simple mapping yields a single `i`, so it is
                // mapped explicitly to stay in parity.
                if c == '\u{0130}' {
                    out.push('i');
                } else {
                    let lowered: String = c.to_lowercase().collect();
                    if lowered.chars().count() == 1 {
                        out.push_str(&lowered);
                    } else {
                        out.push(c);
                    }
                }
            }
        }
    }
    out
}

/// Mirrors Kotlin `Char.isWhitespace` on JVM (`Character.isWhitespace`), which the
/// Kotlin normalizer delegates to. Java/Kotlin `isWhitespace` EXCLUDES NBSP variants
/// (`U+00A0`, `U+2007`, `U+202F`) AND NEL (`U+0085`) — verified on JVM 24:
/// `Character.isWhitespace('\u{0085}')` is false, because NEL has Unicode category
/// Cc (control), not Zs/Zl/Zp, and is not one of the control codes Java flags.
/// Rust `char::is_whitespace` (Unicode White_Space) INCLUDES NEL, so NEL must be
/// excluded explicitly alongside the NBSP variants; otherwise a name containing
/// NEL would normalize to a different key than Kotlin's runtime dedup key (see
/// test_normalize_person_name_matches_kotlin_contract).
fn is_java_whitespace(c: char) -> bool {
    matches!(
        c,
        '\u{0009}'
            | '\u{000A}'
            | '\u{000B}'
            | '\u{000C}'
            | '\u{000D}'
            | '\u{001C}'
            | '\u{001D}'
            | '\u{001E}'
            | '\u{001F}'
    ) || (c.is_whitespace() && c != '\u{00A0}' && c != '\u{2007}' && c != '\u{202F}' && c != '\u{0085}')
}

/// Validate an entire backup payload. Collects all errors from all entities.
pub fn validate_backup_payload(payload: &BackupPayload) -> ValidationResult {
    let mut errors = Vec::new();
    if payload.version < 1 {
        errors.push("Invalid backup version".into());
    }

    // Account validation + transaction account reference checks.
    // Single shared helper — both FFI and internal paths enforce the same rules.
    errors.extend(validate_accounts_and_references(payload));

    // Category cross-reference check (mirrors FFI validate_backup).
    // Only check positive IDs — zero is a legacy default tolerated by
    // validate_transaction, so treating it as missing would break old backups.
    if !payload.categories.is_empty() {
        let category_ids: std::collections::HashSet<_> = payload.categories.iter().map(|c| c.id).collect();
        for (i, tx) in payload.transactions.iter().enumerate() {
            if tx.category_id > 0 && !category_ids.contains(&tx.category_id) {
                errors.push(format!(
                    "Transaction[{}] references non-existent category {}",
                    i, tx.category_id
                ));
            }
        }
    }
    errors.extend(validate_transaction_batch(&payload.transactions).errors);
    errors.extend(validate_loan_batch(&payload.loans).errors);
    errors.extend(validate_installment_batch(&payload.installments).errors);
    errors.extend(validate_bank_loan_batch(&payload.bank_loans).errors);
    errors.extend(validate_payment_history_batch(&payload.payment_histories).errors);
    // Person validation: field checks + cross-references. Single shared
    // function so both FFI and internal paths enforce identical rules.
    errors.extend(validate_persons(payload));
    // PaymentHistory cross-reference: positive loan_id must point to an existing loan.
    // Zero is a legacy default tolerated in all cases.
    if !payload.payment_histories.is_empty() {
        let loan_ids: std::collections::HashSet<_> = payload.loans.iter().map(|l| l.id).collect();
        for (i, ph) in payload.payment_histories.iter().enumerate() {
            if ph.loan_id > 0 && !loan_ids.contains(&ph.loan_id) {
                errors.push(format!(
                    "PaymentHistory[{}] references non-existent loan {}",
                    i, ph.loan_id
                ));
            }
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

#[cfg(test)]
mod tests;
