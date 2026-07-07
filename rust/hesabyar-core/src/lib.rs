pub mod advisory;
pub mod ai_validation;
pub mod analytics;
pub mod calendar;
pub mod crypto;
pub mod currency;
pub mod dashboard;
pub mod excel;
pub mod ffi;
pub mod models;
pub mod parser;
pub mod search;
pub mod validation;

pub use advisory::*;
pub use ai_validation::*;
pub use analytics::*;
pub use calendar::*;
pub use crypto::*;
pub use currency::*;
pub use dashboard::*;
pub use excel::*;
pub use models::*;
pub use parser::*;
pub use search::*;
pub use validation::*;

uniffi::setup_scaffolding!();

/// Initialize the Rust core. Must be called once from Kotlin after loading the library.
///
/// Installs a panic hook that ensures Rust panics never cross the FFI boundary.
/// Safe to call multiple times (uses `Once` internally).
#[uniffi::export]
pub fn initialize() {
    ffi::ensure_initialized();
}

/// Full offline sentence parser (ported from GeminiParser.parseSentenceOffline).
#[uniffi::export]
pub fn parse_sentence_offline(raw_sentence: &str) -> ParsedResult {
    parser::nlp::parse_sentence_offline_full(raw_sentence)
}

/// Infer expense category from a Persian sentence (full 200+ keyword version).
#[uniffi::export]
pub fn infer_expense_category(sentence: &str) -> CategoryGuess {
    let (cat, subcat) = parser::nlp::infer_expense_category_full(sentence);
    CategoryGuess {
        category: cat,
        subcategory: subcat,
    }
}

/// Convert Gregorian timestamp (ms) to Jalali date.
/// Returns packed i64: (year << 16) | (month << 8) | day.
/// Returns 0 on error (no panic).
#[uniffi::export]
pub fn gregorian_to_jalali(timestamp_ms: i64) -> i64 {
    calendar::gregorian_to_jalali_packed(timestamp_ms)
}

/// Convert Jalali date to Gregorian timestamp (ms).
/// Returns 0 on error (no panic).
#[uniffi::export]
pub fn jalali_to_gregorian(year: i32, month: i32, day: i32) -> i64 {
    calendar::jalali_to_gregorian_packed(year, month, day)
}

/// Parse a Persian amount sentence and return the amount in Toman.
#[uniffi::export]
pub fn parse_persian_amount(sentence: &str) -> i64 {
    parser::amount::parse_amount(sentence, true)
}

/// Parse a backup JSON string into a BackupPayload.
#[uniffi::export]
pub fn parse_backup_json(json: &str) -> Result<BackupPayload, HesabyarError> {
    serde_json::from_str(json).map_err(|e| HesabyarError::BackupValidation {
        detail: format!("Invalid backup JSON: {}", e),
    })
}

/// Validate a backup payload.
#[uniffi::export]
pub fn validate_backup(payload: &BackupPayload) -> Result<(), HesabyarError> {
    if payload.version < 1 {
        return Err(HesabyarError::BackupValidation {
            detail: "Invalid backup version".to_string(),
        });
    }
    Ok(())
}

/// Export a backup payload to JSON.
#[uniffi::export]
pub fn export_backup_json(payload: &BackupPayload) -> Result<String, HesabyarError> {
    serde_json::to_string_pretty(payload).map_err(|e| HesabyarError::BackupValidation {
        detail: format!("Failed to serialize backup: {}", e),
    })
}
