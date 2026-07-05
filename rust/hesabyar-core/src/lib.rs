pub mod advisory;
pub mod analytics;
pub mod calendar;
pub mod currency;
pub mod dashboard;
pub mod models;
pub mod parser;

pub use advisory::*;
pub use analytics::*;
pub use calendar::*;
pub use currency::*;
pub use dashboard::*;
pub use models::*;
pub use parser::*;

/// Full offline sentence parser (ported from GeminiParser.parseSentenceOffline).
pub fn parse_sentence_offline(raw_sentence: &str) -> ParsedResult {
    parser::nlp::parse_sentence_offline_full(raw_sentence)
}

/// Infer expense category from a Persian sentence (full 200+ keyword version).
pub fn infer_expense_category(sentence: &str) -> (String, String) {
    parser::nlp::infer_expense_category_full(sentence)
}

/// Parse a backup JSON string into a BackupPayload.
pub fn parse_backup_json(json: &str) -> Result<BackupPayload, HesabyarError> {
    serde_json::from_str(json).map_err(|e| HesabyarError::BackupValidation {
        message: format!("Invalid backup JSON: {}", e),
    })
}

/// Validate a backup payload.
pub fn validate_backup(payload: &BackupPayload) -> Result<(), HesabyarError> {
    if payload.version < 1 {
        return Err(HesabyarError::BackupValidation {
            message: "Invalid backup version".to_string(),
        });
    }
    Ok(())
}

/// Export a backup payload to JSON.
pub fn export_backup_json(payload: &BackupPayload) -> Result<String, HesabyarError> {
    serde_json::to_string_pretty(payload).map_err(|e| HesabyarError::BackupValidation {
        message: format!("Failed to serialize backup: {}", e),
    })
}
