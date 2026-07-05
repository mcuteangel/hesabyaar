use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum TransactionType {
    Expense,
    Income,
    LoanDebtor,
    LoanCreditor,
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
    #[serde(skip_serializing_if = "Option::is_none")]
    pub due_date: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub installment_id: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct Loan {
    pub id: i64,
    pub person_name: String,
    pub loan_type: String,
    pub original_amount: i64,
    pub remaining_amount: i64,
    pub description: String,
    pub date: i64,
    pub is_settled: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
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
pub struct Category {
    pub id: i64,
    pub name: String,
    pub key: String,
    pub icon: String,
    pub color: i64,
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

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
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

#[derive(Debug, Clone, uniffi::Enum)]
pub enum HesabyarError {
    ParseError { message: String },
    InvalidAmount { amount: String },
    InvalidDate { message: String },
    BackupValidation { message: String },
    CalendarError { message: String },
}

impl std::fmt::Display for HesabyarError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::ParseError { message } => write!(f, "Parse error: {}", message),
            Self::InvalidAmount { amount } => write!(f, "Invalid amount: {}", amount),
            Self::InvalidDate { message } => write!(f, "Invalid date: {}", message),
            Self::BackupValidation { message } => write!(f, "Backup validation: {}", message),
            Self::CalendarError { message } => write!(f, "Calendar error: {}", message),
        }
    }
}

impl std::error::Error for HesabyarError {}
