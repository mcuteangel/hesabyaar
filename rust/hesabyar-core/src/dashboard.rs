use crate::models::*;
use crate::advisory::calculate_debt_to_income_ratio;

/// Compute dashboard data from transactions, loans, and installments.
pub fn compute_dashboard_data(
    transactions: &[Transaction],
    loans: &[Loan],
    installments: &[Installment],
) -> DashboardData {
    let current_balance: i64 = transactions
        .iter()
        .map(|t| match t.tx_type {
            TransactionType::Income => t.amount,
            TransactionType::Expense => -t.amount,
            _ => 0,
        })
        .sum();

    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64;

    // Current month boundaries (approximate: 30 days)
    let month_start = now_ms - 30 * 24 * 60 * 60 * 1000;

    let monthly_income: i64 = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Income && t.date >= month_start)
        .map(|t| t.amount)
        .sum();

    let monthly_expenses: i64 = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense && t.date >= month_start)
        .map(|t| t.amount)
        .sum();

    let debtors_total: i64 = loans
        .iter()
        .filter(|l| l.loan_type == "DEBTOR" && !l.is_settled)
        .map(|l| l.remaining_amount)
        .sum();

    let creditors_total: i64 = loans
        .iter()
        .filter(|l| l.loan_type == "CREDITOR" && !l.is_settled)
        .map(|l| l.remaining_amount)
        .sum();

    let savings_rate = if monthly_income > 0 {
        (monthly_income - monthly_expenses) as f64 / monthly_income as f64
    } else {
        0.0
    };

    let debt_to_income = calculate_debt_to_income_ratio(loans, installments, monthly_income);

    DashboardData {
        current_balance,
        monthly_expenses,
        monthly_income,
        debtors_total,
        creditors_total,
        savings_rate,
        debt_to_income_ratio: debt_to_income,
    }
}
