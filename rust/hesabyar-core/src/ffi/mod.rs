/// FFI safety utilities for UniFFI bindings.
///
/// This module ensures that:
/// 1. Rust panics never cross the FFI boundary (converted to HesabyarError)
/// 2. A panic hook is installed to log panics before they are caught
/// 3. All public FFI functions are wrapped for safety
/// 4. Slice-referencing functions (`&[T]`) are wrapped with `Vec<T>` for UniFFI proc macro compatibility

use std::sync::Once;

use crate::excel::WorkbookData;
use crate::models::*;
use crate::search::{SearchQuery, SearchResponse};
use crate::validation::ValidationResult;
use crate::ai_validation::{AiParsedTransaction, AdviceValidation};

static INIT: Once = Once::new();

/// Initialize the FFI layer. Must be called once before any FFI function.
///
/// Installs a panic hook that:
/// - Prints the panic message to stderr (for Android logcat)
/// - Ensures panics are caught by UniFFI's catch_unwind wrapper
pub fn ensure_initialized() {
    INIT.call_once(|| {
        let default_hook = std::panic::take_hook();
        std::panic::set_hook(Box::new(move |panic_info| {
            eprintln!("[hesabyar-core] Rust panic: {}", panic_info);
            default_hook(panic_info);
        }));
    });
}

/// Wrap a closure that may panic into a Result.
///
/// If the closure panics, returns `HesabyarError::ParseError` with the panic message.
pub fn catch_unwind_safe<F, T>(f: F) -> Result<T, HesabyarError>
where
    F: std::panic::UnwindSafe + std::panic::RefUnwindSafe + FnOnce() -> T,
{
    ensure_initialized();
    match std::panic::catch_unwind(f) {
        Ok(result) => Ok(result),
        Err(panic) => {
            let msg = if let Some(s) = panic.downcast_ref::<&str>() {
                s.to_string()
            } else if let Some(s) = panic.downcast_ref::<String>() {
                s.clone()
            } else {
                "Unknown Rust panic".to_string()
            };
            Err(HesabyarError::ParseError { detail: msg })
        }
    }
}

// ===========================================================================
// FFI Wrappers for advisory/analytics/dashboard functions.
//
// These take Vec<T> (not &[T]) because UniFFI proc macros do not support
// slice references across the FFI boundary.
// ===========================================================================

/// Get offline budget advice based on local rules.
#[uniffi::export]
pub fn get_offline_budget_advice(
    transactions: Vec<Transaction>,
    categories: Vec<Category>,
) -> String {
    crate::advisory::get_offline_budget_advice(&transactions, &categories)
}

/// Get offline budget forecast.
#[uniffi::export]
pub fn get_offline_forecast(
    transactions: Vec<Transaction>,
    loans: Vec<Loan>,
    installments: Vec<Installment>,
) -> String {
    crate::advisory::get_offline_forecast(&transactions, &loans, &installments)
}

/// Calculate debt-to-income ratio.
#[uniffi::export]
pub fn calculate_debt_to_income_ratio(
    loans: Vec<Loan>,
    installments: Vec<Installment>,
    monthly_income: i64,
) -> f64 {
    crate::advisory::calculate_debt_to_income_ratio(&loans, &installments, monthly_income)
}

/// Predict time to reach a savings goal.
#[uniffi::export]
pub fn predict_time_to_goal(current_savings: i64, monthly_savings: i64, goal_amount: i64) -> i32 {
    crate::advisory::predict_time_to_goal(current_savings, monthly_savings, goal_amount)
}

/// Calculate financial health score (0-100).
#[uniffi::export]
pub fn calculate_financial_health_score(
    transactions: Vec<Transaction>,
    loans: Vec<Loan>,
    installments: Vec<Installment>,
    categories: Vec<Category>,
) -> i32 {
    crate::advisory::calculate_financial_health_score(
        &transactions,
        &loans,
        &installments,
        &categories,
    )
}

/// Compute analytics data from transactions, loans, installments, and categories.
#[uniffi::export]
pub fn compute_analytics(
    transactions: Vec<Transaction>,
    loans: Vec<Loan>,
    installments: Vec<Installment>,
    categories: Vec<Category>,
) -> AnalyticsData {
    crate::analytics::compute_analytics(&transactions, &loans, &installments, &categories)
}

/// Compute dashboard data from transactions, loans, and installments.
#[uniffi::export]
pub fn compute_dashboard_data(
    transactions: Vec<Transaction>,
    loans: Vec<Loan>,
    installments: Vec<Installment>,
) -> DashboardData {
    crate::dashboard::compute_dashboard_data(&transactions, &loans, &installments)
}

// ===========================================================================
// FFI Wrappers for search functions.
// ===========================================================================

/// Search transactions with the given query.
///
/// Returns matching transactions sorted by relevance.
/// All filters are optional — omit a filter by setting its value to 0/false.
#[uniffi::export]
pub fn search_transactions(
    transactions: Vec<Transaction>,
    query: SearchQuery,
) -> SearchResponse {
    crate::search::search_transactions(&transactions, &query)
}

// ===========================================================================
// FFI Wrappers for crypto functions.
//
// Key management stays on the Kotlin/Android side.
// These functions receive the key as a parameter.
// ===========================================================================

/// Encrypt a JSON backup string using AES-256-GCM.
///
/// Returns encrypted bytes: `[12-byte nonce][ciphertext + 16-byte auth tag]`
#[uniffi::export]
pub fn encrypt_backup(json: &str, key: &[u8]) -> Result<Vec<u8>, HesabyarError> {
    crate::crypto::encrypt_backup(json, key)
}

/// Decrypt an encrypted backup using AES-256-GCM.
///
/// Input: `[12-byte nonce][ciphertext + auth tag]`
/// Returns decrypted JSON string.
#[uniffi::export]
pub fn decrypt_backup(data: &[u8], key: &[u8]) -> Result<String, HesabyarError> {
    crate::crypto::decrypt_backup(data, key)
}

/// Compute SHA-256 checksum of data.
///
/// Returns a 64-character hexadecimal string.
#[uniffi::export]
pub fn compute_checksum(data: &[u8]) -> String {
    crate::crypto::compute_checksum(data)
}

/// Verify SHA-256 checksum of data.
///
/// Uses constant-time comparison to prevent timing attacks.
#[uniffi::export]
pub fn verify_checksum(data: &[u8], expected: &str) -> bool {
    crate::crypto::verify_checksum(data, expected)
}

/// Build an encrypted backup file with header, checksum, and encrypted data.
///
/// File format:
/// ```text
/// HESABYAR_BACKUP_V1\n
/// <64-char SHA-256 hex>\n
/// <encrypted binary data>
/// ```
#[uniffi::export]
pub fn build_encrypted_backup_file(json: &str, key: &[u8]) -> Result<Vec<u8>, HesabyarError> {
    crate::crypto::build_encrypted_backup_file(json, key)
}

/// Parse an encrypted backup file, verifying checksum and decrypting.
///
/// Returns the decrypted JSON string.
#[uniffi::export]
pub fn parse_encrypted_backup_file(data: &[u8], key: &[u8]) -> Result<String, HesabyarError> {
    crate::crypto::parse_encrypted_backup_file(data, key)
}

// ===========================================================================
// FFI Wrappers for validation functions.
// ===========================================================================

/// Validate a single transaction.
///
/// Returns `Ok(())` if valid, or `HesabyarError::ValidationError` if invalid.
#[uniffi::export]
pub fn validate_transaction(transaction: Transaction) -> Result<(), HesabyarError> {
    crate::validation::validate_transaction(&transaction)
        .map_err(|e| HesabyarError::ValidationError { detail: e })
}

/// Validate a single loan.
///
/// Returns `Ok(())` if valid, or `HesabyarError::ValidationError` if invalid.
#[uniffi::export]
pub fn validate_loan(loan: Loan) -> Result<(), HesabyarError> {
    crate::validation::validate_loan(&loan)
        .map_err(|e| HesabyarError::ValidationError { detail: e })
}

/// Validate a single installment.
///
/// Returns `Ok(())` if valid, or `HesabyarError::ValidationError` if invalid.
#[uniffi::export]
pub fn validate_installment(installment: Installment) -> Result<(), HesabyarError> {
    crate::validation::validate_installment(&installment)
        .map_err(|e| HesabyarError::ValidationError { detail: e })
}

/// Validate a ParsedResult (AI parser output).
///
/// Returns `Ok(())` if valid, or `HesabyarError::ValidationError` if invalid.
#[uniffi::export]
pub fn validate_parsed_result(result: ParsedResult) -> Result<(), HesabyarError> {
    crate::validation::validate_parsed_result(&result)
        .map_err(|e| HesabyarError::ValidationError { detail: e })
}

/// Validate an entire backup payload. Collects all errors from all entities.
///
/// Returns a `ValidationResult` with `is_valid` flag and list of error messages.
#[uniffi::export]
pub fn validate_backup_payload(payload: BackupPayload) -> ValidationResult {
    crate::validation::validate_backup_payload(&payload)
}

// ===========================================================================
// FFI Wrappers for Excel generation.
// ===========================================================================

/// Generate an XLSX workbook from pre-formatted sheet data.
///
/// Returns raw XLSX bytes (a valid .xlsx file).
#[uniffi::export]
pub fn generate_excel(workbook: WorkbookData) -> Result<Vec<u8>, HesabyarError> {
    crate::excel::generate_excel(&workbook)
}

// ===========================================================================
// FFI Wrappers for AI validation functions (Phase 6).
// ===========================================================================

/// Parse and validate a raw AI JSON response into a clean `ParsedResult`.
///
/// Validates amount, normalizes category to 8 core DB values, clamps
/// out-of-range fields, and returns repair metadata.
#[uniffi::export]
pub fn parse_ai_transaction_json(json: &str) -> Result<AiParsedTransaction, HesabyarError> {
    crate::ai_validation::parse_ai_transaction_json(json)
}

/// Validate and sanitize free-form AI advice/forecast text.
///
/// Checks minimum/maximum length, strips dangerous HTML tags, and detects
/// Persian content. Returns sanitized text with warnings.
#[uniffi::export]
pub fn validate_ai_advice(text: &str) -> AdviceValidation {
    crate::ai_validation::validate_ai_advice(text)
}
