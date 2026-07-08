use serde::{Deserialize, Serialize};

/// Deserialize an i64 where 0 means None (sentinel for null from Kotlin exports).
fn deserialize_zero_as_none<'de, D>(deserializer: D) -> Result<Option<i64>, D::Error>
where
    D: serde::Deserializer<'de>,
{
    let val = i64::deserialize(deserializer)?;
    Ok(if val == 0 { None } else { Some(val) })
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum TransactionType {
    #[serde(alias = "Expense")]
    Expense,
    #[serde(alias = "Income")]
    Income,
    #[serde(alias = "LoanDebtor")]
    LoanDebtor,
    #[serde(alias = "LoanCreditor")]
    LoanCreditor,
    #[serde(alias = "Installment")]
    Installment,
}

impl TransactionType {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Expense => "EXPENSE",
            Self::Income => "INCOME",
            Self::LoanDebtor => "LOAN_DEBTOR",
            Self::LoanCreditor => "LOAN_CREDITOR",
            Self::Installment => "INSTALLMENT",
        }
    }

    pub fn from_str(s: &str) -> Self {
        match s {
            "INCOME" => Self::Income,
            "LOAN_DEBTOR" => Self::LoanDebtor,
            "LOAN_CREDITOR" => Self::LoanCreditor,
            "INSTALLMENT" => Self::Installment,
            _ => Self::Expense,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct Transaction {
    pub id: i64,
    #[serde(rename = "type")]
    pub tx_type: TransactionType,
    pub category_id: i64,
    pub amount: i64,
    pub description: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub person_name: Option<String>,
    pub date: i64,
    #[serde(default, skip_serializing_if = "Option::is_none", deserialize_with = "deserialize_zero_as_none")]
    pub due_date: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none", deserialize_with = "deserialize_zero_as_none")]
    pub installment_id: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct Loan {
    pub id: i64,
    pub person_name: String,
    #[serde(rename = "type")]
    pub loan_type: String,
    pub original_amount: i64,
    pub remaining_amount: i64,
    pub description: String,
    pub date: i64,
    pub is_settled: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct Installment {
    pub id: i64,
    pub title: String,
    pub amount: i64,
    pub due_date: i64,
    pub is_paid: bool,
    pub reminder_enabled: bool,
    pub notes: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct Category {
    pub id: i64,
    pub name: String,
    pub key: String,
    pub icon: String,
    pub color: i64,
    #[serde(rename = "type")]
    pub category_type: String,
    pub is_default: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ParsedResult {
    pub tx_type: TransactionType,
    pub amount: i64,
    pub category: String,
    pub person_name: Option<String>,
    pub description: String,
    pub days_from_now: Option<i32>,
    pub title: Option<String>,
    pub date_offset_days: Option<i32>,
    pub hour: Option<i32>,
    pub minute: Option<i32>,
    pub confidence: f32,
    pub notes: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct JalaliDate {
    pub year: i32,
    pub month: i32,
    pub day: i32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum CurrencyUnit {
    Rial,
    Toman,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DashboardData {
    pub current_balance: i64,
    pub monthly_expenses: i64,
    pub monthly_income: i64,
    pub debtors_total: i64,
    pub creditors_total: i64,
    pub savings_rate: f64,
    pub debt_to_income_ratio: f64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct MonthlyData {
    pub jalali_year: i32,
    pub jalali_month: i32,
    pub label: String,
    pub income: i64,
    pub expense: i64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct CategoryBreakdown {
    pub category_id: i64,
    pub category_name: String,
    pub color: i64,
    pub total: i64,
    pub percentage: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DebtSummary {
    pub person_name: String,
    pub original_amount: i64,
    pub remaining_amount: i64,
    pub debt_type: String,
    pub progress: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct InstallmentProgress {
    pub id: i64,
    pub title: String,
    pub amount: i64,
    pub due_date: i64,
    pub is_paid: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AnalyticsData {
    pub monthly_spending: Vec<MonthlyData>,
    pub monthly_income: Vec<MonthlyData>,
    pub category_breakdown: Vec<CategoryBreakdown>,
    pub debtors: Vec<DebtSummary>,
    pub creditors: Vec<DebtSummary>,
    pub total_debt: i64,
    pub total_credit: i64,
    pub total_installments: i32,
    pub paid_installments: i32,
}

/// Backup payload for JSON export/import.
///
/// Serde's default behavior silently ignores unknown fields during
/// deserialization. This means Kotlin can pass a JSON containing extra keys
/// (e.g. `paymentHistories`, `budgets`, or any future field) and Rust will
/// parse it without error — the extra fields are simply discarded.
///
/// Missing fields default to empty collections via `#[serde(default)]`.
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct BackupPayload {
    pub version: i32,
    pub timestamp: i64,
    pub app_version: String,
    #[serde(default)]
    pub transactions: Vec<Transaction>,
    #[serde(default)]
    pub loans: Vec<Loan>,
    #[serde(default)]
    pub installments: Vec<Installment>,
    #[serde(default)]
    pub categories: Vec<Category>,
}

impl Default for BackupPayload {
    fn default() -> Self {
        Self {
            version: 1,
            timestamp: 0,
            app_version: "1.0".to_string(),
            transactions: Vec::new(),
            loans: Vec::new(),
            installments: Vec::new(),
            categories: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum HesabyarError {
    ParseError { detail: String },
    InvalidAmount { amount: String },
    InvalidDate { detail: String },
    BackupValidation { detail: String },
    CalendarError { detail: String },
    CryptoError { detail: String },
    ValidationError { detail: String },
}

impl std::fmt::Display for HesabyarError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::ParseError { detail } => write!(f, "Parse error: {}", detail),
            Self::InvalidAmount { amount } => write!(f, "Invalid amount: {}", amount),
            Self::InvalidDate { detail } => write!(f, "Invalid date: {}", detail),
            Self::BackupValidation { detail } => write!(f, "Backup validation: {}", detail),
            Self::CalendarError { detail } => write!(f, "Calendar error: {}", detail),
            Self::CryptoError { detail } => write!(f, "Crypto error: {}", detail),
            Self::ValidationError { detail } => write!(f, "Validation error: {}", detail),
        }
    }
}

impl std::error::Error for HesabyarError {}

#[derive(Debug, Clone, uniffi::Record)]
pub struct CategoryGuess {
    pub category: String,
    pub subcategory: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_backup_payload_ignores_unknown_fields() {
        // Kotlin may include `paymentHistories` or other future fields.
        // Rust must parse without error — extra fields are silently discarded.
        let json = r#"{
            "version": 1,
            "timestamp": 1710000000000,
            "appVersion": "1.0.0",
            "transactions": [],
            "loans": [],
            "installments": [],
            "categories": [],
            "paymentHistories": [{"id": 1, "amount": 50000}],
            "budgets": [{"monthly_limit": 1000000}],
            "futureField": "hello"
        }"#;
        let payload: BackupPayload = serde_json::from_str(json).unwrap();
        assert_eq!(payload.version, 1);
        assert_eq!(payload.app_version, "1.0.0");
        assert!(payload.transactions.is_empty());
    }

    #[test]
    fn test_backup_payload_defaults_missing_collections() {
        // If Kotlin omits collection fields entirely, they default to empty Vecs.
        let json = r#"{
            "version": 2,
            "timestamp": 1710000000000,
            "appVersion": "2.0.0"
        }"#;
        let payload: BackupPayload = serde_json::from_str(json).unwrap();
        assert_eq!(payload.version, 2);
        assert!(payload.transactions.is_empty());
        assert!(payload.loans.is_empty());
        assert!(payload.installments.is_empty());
        assert!(payload.categories.is_empty());
    }

    #[test]
    fn test_backup_payload_valid_round_trip() {
        let original = BackupPayload {
            version: 1,
            timestamp: 1710000000000,
            app_version: "1.0.0".to_string(),
            transactions: vec![Transaction {
                id: 1,
                tx_type: TransactionType::Expense,
                category_id: 10,
                amount: 50000,
                description: "Test".to_string(),
                person_name: None,
                date: 1710000000000,
                due_date: None,
                installment_id: None,
            }],
            loans: vec![],
            installments: vec![],
            categories: vec![],
        };
        let json = serde_json::to_string(&original).unwrap();
        let restored: BackupPayload = serde_json::from_str(&json).unwrap();
        assert_eq!(restored.version, original.version);
        assert_eq!(restored.transactions.len(), 1);
        assert_eq!(restored.transactions[0].amount, 50000);
    }

    #[test]
    fn test_backup_payload_parses_camel_case_export() {
        // The app's Kotlin exporter (ManageBackupUseCase.exportBackupJson) writes
        // camelCase keys. Rust must parse that exact shape, not just its own output.
        let json = r#"{
            "version": 1,
            "timestamp": 1710000000000,
            "appVersion": "1.0",
            "categories": [
                {"id": 1, "name": "Food", "key": "food", "icon": "ic", "color": 0, "type": "EXPENSE", "isDefault": true}
            ],
            "transactions": [
                {"id": 1, "type": "EXPENSE", "categoryId": 10, "amount": 50000, "description": "Lunch", "personName": "", "date": 1710000000000, "dueDate": 0, "installmentId": 0}
            ],
            "loans": [
                {"id": 2, "personName": "Bob", "type": "DEBTOR", "originalAmount": 100000, "remainingAmount": 40000, "description": "Loan", "date": 1710000000000, "isSettled": false}
            ],
            "installments": [
                {"id": 3, "title": "Rent", "amount": 2000000, "dueDate": 1710000000000, "isPaid": false, "reminderEnabled": true, "notes": ""}
            ]
        }"#;
        let payload: BackupPayload = serde_json::from_str(json).unwrap();
        assert_eq!(payload.categories[0].category_type, "EXPENSE");
        assert_eq!(payload.transactions[0].category_id, 10);
        assert_eq!(payload.loans[0].loan_type, "DEBTOR");
        assert_eq!(payload.loans[0].original_amount, 100000);
        assert!(!payload.installments[0].is_paid);
    }

    #[test]
    fn test_backup_payload_rejects_invalid_version() {
        let json = r#"{
            "version": 0,
            "timestamp": 0,
            "appVersion": "0.0.1"
        }"#;
        let payload: BackupPayload = serde_json::from_str(json).unwrap();
        assert_eq!(crate::validate_backup(&payload).unwrap_err().to_string(), "Backup validation: Invalid backup version");
    }
}
