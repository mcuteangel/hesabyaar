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
    crate::ffi::catch_unwind_safe(ffi::ensure_initialized).unwrap_or(())
}

/// Full offline sentence parser (ported from GeminiParser.parseSentenceOffline).
#[uniffi::export]
pub fn parse_sentence_offline(raw_sentence: &str) -> Result<ParsedResult, HesabyarError> {
    crate::ffi::catch_unwind_safe(|| parser::nlp::parse_sentence_offline_full(raw_sentence))
        .map_err(|e| HesabyarError::ParseError { detail: format!("{:?}", e) })
}

/// Infer expense category from a Persian sentence (full 200+ keyword version).
#[uniffi::export]
pub fn infer_expense_category(sentence: &str) -> CategoryGuess {
    let r = crate::ffi::catch_unwind_safe(|| {
        let (cat, subcat) = parser::nlp::infer_expense_category_full(sentence);
        CategoryGuess {
            category: cat,
            subcategory: subcat,
        }
    });
    r.unwrap_or_default()
}

/// Convert Gregorian timestamp (ms) to Jalali date.
/// Returns packed i64: (year << 16) | (month << 8) | day.
/// Returns 0 on error (no panic).
#[uniffi::export]
pub fn gregorian_to_jalali(timestamp_ms: i64) -> i64 {
    crate::ffi::catch_unwind_safe(|| calendar::gregorian_to_jalali_packed(timestamp_ms)).unwrap_or(0)
}

/// Convert Jalali date to Gregorian timestamp (ms).
/// Returns i64::MIN on error (no panic) to match the Kotlin Long.MIN_VALUE sentinel.
#[uniffi::export]
pub fn jalali_to_gregorian(year: i32, month: i32, day: i32) -> i64 {
    crate::ffi::catch_unwind_safe(|| {
        calendar::jalali_to_gregorian(year, month, day).unwrap_or(i64::MIN)
    })
    .unwrap_or(i64::MIN)
}

/// Parse a Persian amount sentence and return the amount in Toman.
#[uniffi::export]
pub fn parse_persian_amount(sentence: &str) -> i64 {
    crate::ffi::catch_unwind_safe(|| parser::amount::parse_amount(sentence, true)).unwrap_or(0)
}

/// Parse a backup JSON string into a BackupPayload.
#[uniffi::export]
pub fn parse_backup_json(json: &str) -> Result<BackupPayload, HesabyarError> {
    let r = crate::ffi::catch_unwind_safe(|| {
        serde_json::from_str(json).map_err(|e| HesabyarError::BackupValidation {
            detail: format!("Invalid backup JSON: {}", e),
        })
    })?;
    r
}

/// Validate a backup payload.
#[uniffi::export]
pub fn validate_backup(payload: &BackupPayload) -> Result<(), HesabyarError> {
    let r = crate::ffi::catch_unwind_safe(|| {
        if payload.version < 1 {
            return Err(HesabyarError::BackupValidation {
                detail: "Invalid backup version".to_string(),
            });
        }

        // Full structural validation
        if payload.transactions.is_empty()
            && payload.loans.is_empty()
            && payload.installments.is_empty()
            && payload.categories.is_empty()
        {
            return Err(HesabyarError::BackupValidation {
                detail: "Backup contains no data".to_string(),
            });
        }

        // Validate transactions
        let category_ids: std::collections::HashSet<_> = payload.categories.iter().map(|c| c.id).collect();
        for tx in &payload.transactions {
            if tx.amount <= 0 {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Transaction {} has invalid amount", tx.id),
                });
            }
            if tx.date <= 0 {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Transaction {} has invalid date", tx.id),
                });
            }
            if tx.category_id <= 0 {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Transaction {} has invalid category_id", tx.id),
                });
            }
            if !payload.categories.is_empty() && !category_ids.contains(&tx.category_id) {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Transaction {} references non-existent category {}", tx.id, tx.category_id),
                });
            }
        }

        // Validate loans
        for loan in &payload.loans {
            if loan.original_amount <= 0 {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Loan {} has invalid original_amount", loan.id),
                });
            }
            if loan.remaining_amount < 0 || loan.remaining_amount > loan.original_amount {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Loan {} has invalid remaining_amount", loan.id),
                });
            }
            if loan.date <= 0 {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Loan {} has invalid date", loan.id),
                });
            }
        }

        // Validate installments
        for inst in &payload.installments {
            if inst.amount <= 0 {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Installment {} has invalid amount", inst.id),
                });
            }
            if inst.due_date <= 0 {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Installment {} has invalid due_date", inst.id),
                });
            }
        }

        // Validate categories
        for cat in &payload.categories {
            if cat.id <= 0 {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Category {} has invalid id", cat.id),
                });
            }
            if cat.name.trim().is_empty() {
                return Err(HesabyarError::BackupValidation {
                    detail: format!("Category {} has empty name", cat.id),
                });
            }
        }

        Ok(())
    })?;
    r
}

/// Export a backup payload to JSON.
#[uniffi::export]
pub fn export_backup_json(payload: &BackupPayload) -> Result<String, HesabyarError> {
    let r = crate::ffi::catch_unwind_safe(|| {
        serde_json::to_string_pretty(payload).map_err(|e| HesabyarError::BackupValidation {
            detail: format!("Failed to serialize backup: {}", e),
        })
    })?;
    r
}
