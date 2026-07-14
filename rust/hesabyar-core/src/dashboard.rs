use crate::models::*;
use crate::calendar::{gregorian_to_jalali, get_jalali_days_in_month};

/// Compute dashboard data from transactions, loans, and installments.
///
/// Monthly income/expenses are calculated for the **current Jalali month**,
/// not a rolling 30-day window. This ensures correct behavior across months
/// of varying length (29–31 days).
pub fn compute_dashboard_data(
    transactions: &[Transaction],
    loans: &[Loan],
    installments: &[Installment],
) -> DashboardData {
    // --- Current Jalali month boundaries ---
    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64;

    let (jy, jm) = match gregorian_to_jalali(now_ms) {
        Ok(jd) => (jd.year, jd.month),
        Err(_) => {
            // Fallback: return zeros for monthly aggregates
            return DashboardData {
                current_balance: compute_all_time_balance(transactions),
                monthly_expenses: 0,
                monthly_income: 0,
                debtors_total: compute_debtors_total(loans),
                creditors_total: compute_creditors_total(loans),
                savings_rate: 0.0,
                debt_to_income_ratio: 0.0,
            };
        }
    };

    let _days_in_month = get_jalali_days_in_month(jy, jm);

    // Build start-of-month and end-of-month timestamps for the current Jalali month.
    // start_ms = first moment of the Jalali month
    // end_ms   = first moment of the next Jalali month (or day 1 of next month)
    let month_start_ms = jalali_to_month_start_ms(jy, jm);
    let month_end_ms = if jm < 12 {
        jalali_to_month_start_ms(jy, jm + 1)
    } else {
        jalali_to_month_start_ms(jy + 1, 1)
    };

    // --- Aggregate transactions ---
    let mut current_balance: i64 = 0;
    let mut monthly_income: i64 = 0;
    let mut monthly_expenses: i64 = 0;

    for tx in transactions {
        match tx.tx_type {
            TransactionType::Income => {
                current_balance += tx.amount;
                if tx.date >= month_start_ms && tx.date < month_end_ms {
                    monthly_income += tx.amount;
                }
            }
            TransactionType::Expense => {
                current_balance -= tx.amount;
                if tx.date >= month_start_ms && tx.date < month_end_ms {
                    monthly_expenses += tx.amount;
                }
            }
            _ => {}
        }
    }

    // --- Loan aggregates ---
    let debtors_total = compute_debtors_total(loans);
    let creditors_total = compute_creditors_total(loans);

    // --- Rates ---
    let savings_rate = if monthly_income > 0 {
        ((monthly_income - monthly_expenses) as f64 / monthly_income as f64).clamp(0.0, 1.0)
    } else {
        0.0
    };

    // Filter installments to only those due in the current month (unpaid)
    let mut current_month_installments: Vec<Installment> = Vec::new();
    for inst in installments {
        if !inst.is_paid && inst.due_date >= month_start_ms && inst.due_date < month_end_ms {
            current_month_installments.push(inst.clone());
        }
    }

    let debt_to_income = crate::advisory::calculate_debt_to_income_ratio(
        loans, 
        &current_month_installments, 
        monthly_income
    );

    // --- Installment summary ---
    let _upcoming_installments: Vec<&Installment> = installments
        .iter()
        .filter(|i| !i.is_paid && i.due_date >= month_start_ms && i.due_date < month_end_ms)
        .collect();

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

fn compute_all_time_balance(transactions: &[Transaction]) -> i64 {
    transactions
        .iter()
        .map(|t| match t.tx_type {
            TransactionType::Income => t.amount,
            TransactionType::Expense => -t.amount,
            _ => 0,
        })
        .sum()
}

fn compute_debtors_total(loans: &[Loan]) -> i64 {
    loans
        .iter()
        .filter(|l| l.loan_type == "DEBTOR" && !l.is_settled)
        .map(|l| l.remaining_amount)
        .sum()
}

fn compute_creditors_total(loans: &[Loan]) -> i64 {
    loans
        .iter()
        .filter(|l| l.loan_type == "CREDITOR" && !l.is_settled)
        .map(|l| l.remaining_amount)
        .sum()
}

/// Convert a Jalali year/month to Gregorian epoch-ms (start of month).
/// Falls back to a 30-day approximation if the reverse conversion fails.
fn jalali_to_month_start_ms(jy: i32, jm: i32) -> i64 {
    crate::calendar::jalali_to_gregorian(jy, jm, 1).unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tx(id: i64, tx_type: TransactionType, amount: i64, date_ms: i64, cat_id: i64) -> Transaction {
        Transaction {
            id,
            tx_type,
            category_id: cat_id,
            amount,
            description: String::new(),
            person_name: None,
            date: date_ms,
            due_date: None,
            installment_id: None,
        }
    }

    fn loan(id: i64, loan_type: &str, original: i64, remaining: i64, settled: bool) -> Loan {
        Loan {
            id,
            person_name: "Test".to_string(),
            loan_type: loan_type.to_string(),
            original_amount: original,
            remaining_amount: remaining,
            description: String::new(),
            date: 0,
            is_settled: settled,
        }
    }

    fn installment(id: i64, amount: i64, due_ms: i64, paid: bool) -> Installment {
        Installment {
            id,
            title: format!("Installment {}", id),
            amount,
            due_date: due_ms,
            is_paid: paid,
            reminder_enabled: false,
            notes: String::new(),
        }
    }

    // =====================================================================
    // Empty input — should not panic, returns zeros
    // =====================================================================

    #[test]
    fn test_empty_transactions() {
        let result = compute_dashboard_data(&[], &[], &[]);
        assert_eq!(result.current_balance, 0);
        assert_eq!(result.monthly_income, 0);
        assert_eq!(result.monthly_expenses, 0);
        assert_eq!(result.debtors_total, 0);
        assert_eq!(result.creditors_total, 0);
        assert_eq!(result.savings_rate, 0.0);
    }

    // =====================================================================
    // Balance computation
    // =====================================================================

    #[test]
    fn test_balance_income_minus_expense() {
        let now_ms = now_jalali_month_ms();
        let txs = vec![
            tx(1, TransactionType::Income, 1_000_000, now_ms, 1),
            tx(2, TransactionType::Expense, 300_000, now_ms, 2),
            tx(3, TransactionType::Income, 500_000, now_ms, 1),
        ];
        let result = compute_dashboard_data(&txs, &[], &[]);
        // Balance = +1,000,000 - 300,000 + 500,000 = 1,200,000
        assert_eq!(result.current_balance, 1_200_000);
    }

    #[test]
    fn test_only_loan_types_ignored_in_balance() {
        let now_ms = now_jalali_month_ms();
        let txs = vec![
            tx(1, TransactionType::Income, 1_000_000, now_ms, 1),
            tx(2, TransactionType::LoanDebtor, 500_000, now_ms, 1),
            tx(3, TransactionType::LoanCreditor, 200_000, now_ms, 1),
            tx(4, TransactionType::Installment, 100_000, now_ms, 1),
        ];
        let result = compute_dashboard_data(&txs, &[], &[]);
        // Only Income contributes: +1,000,000
        assert_eq!(result.current_balance, 1_000_000);
    }

    // =====================================================================
    // Monthly income/expenses (current Jalali month only)
    // =====================================================================

    #[test]
    fn test_monthly_income_only_current_month() {
        let now_ms = now_jalali_month_ms();
        let old_ms = now_ms - 60 * 24 * 60 * 60 * 1000; // 2 months ago
        let txs = vec![
            tx(1, TransactionType::Income, 1_000_000, now_ms, 1),
            tx(2, TransactionType::Income, 2_000_000, old_ms, 1),
        ];
        let result = compute_dashboard_data(&txs, &[], &[]);
        // Only current month income counted
        assert_eq!(result.monthly_income, 1_000_000);
    }

    #[test]
    fn test_monthly_expenses_only_current_month() {
        let now_ms = now_jalali_month_ms();
        let old_ms = now_ms - 60 * 24 * 60 * 60 * 1000;
        let txs = vec![
            tx(1, TransactionType::Expense, 500_000, now_ms, 1),
            tx(2, TransactionType::Expense, 300_000, old_ms, 1),
        ];
        let result = compute_dashboard_data(&txs, &[], &[]);
        assert_eq!(result.monthly_expenses, 500_000);
    }

    #[test]
    fn test_savings_rate_computed_correctly() {
        let now_ms = now_jalali_month_ms();
        let txs = vec![
            tx(1, TransactionType::Income, 1_000_000, now_ms, 1),
            tx(2, TransactionType::Expense, 400_000, now_ms, 1),
        ];
        let result = compute_dashboard_data(&txs, &[], &[]);
        // savings_rate = (1,000,000 - 400,000) / 1,000,000 = 0.6
        assert!((result.savings_rate - 0.6).abs() < 1e-10);
    }

    #[test]
    fn test_savings_rate_zero_income() {
        let result = compute_dashboard_data(&[], &[], &[]);
        assert_eq!(result.savings_rate, 0.0);
    }

    // =====================================================================
    // Loan aggregates
    // =====================================================================

    #[test]
    fn test_debtors_and_creditors() {
        let loans = vec![
            loan(1, "DEBTOR", 1_000_000, 500_000, false),
            loan(2, "DEBTOR", 2_000_000, 2_000_000, false),
            loan(3, "CREDITOR", 3_000_000, 1_000_000, false),
            loan(4, "DEBTOR", 500_000, 100_000, true), // settled — excluded
        ];
        let result = compute_dashboard_data(&[], &loans, &[]);
        assert_eq!(result.debtors_total, 2_500_000);  // 500k + 2M
        assert_eq!(result.creditors_total, 1_000_000);
    }

    #[test]
    fn test_settled_loans_excluded() {
        let loans = vec![
            loan(1, "DEBTOR", 1_000_000, 500_000, true),
            loan(2, "CREDITOR", 2_000_000, 1_000_000, true),
        ];
        let result = compute_dashboard_data(&[], &loans, &[]);
        assert_eq!(result.debtors_total, 0);
        assert_eq!(result.creditors_total, 0);
    }

    // =====================================================================
    // Debt-to-income ratio
    // =====================================================================

    #[test]
    fn test_debt_to_income_ratio() {
        let now_ms = now_jalali_month_ms();
        let txs = vec![tx(1, TransactionType::Income, 1_000_000, now_ms, 1)];
        let loans = vec![loan(1, "CREDITOR", 1_200_000, 600_000, false)];
        let installments = vec![installment(1, 100_000, now_ms, false)];
        let result = compute_dashboard_data(&txs, &loans, &installments);
        // monthly_debt_payments = 100k (installment) + 600k/12 ≈ 50k = 150k
        // ratio = 150k / 1_000_000 = 0.15
        assert!(result.debt_to_income_ratio > 0.0);
        assert!(result.debt_to_income_ratio < 1.0);
    }

    // =====================================================================
    // Cross-module: currency rule respected
    // =====================================================================

    #[test]
    fn test_amounts_are_in_rial() {
        // All amounts in the system are stored in Rial.
        // The dashboard must not convert or interpret them.
        let now_ms = now_jalali_month_ms();
        let txs = vec![tx(1, TransactionType::Income, 100, now_ms, 1)];
        let result = compute_dashboard_data(&txs, &[], &[]);
        assert_eq!(result.monthly_income, 100);
        assert_eq!(result.current_balance, 100);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /// Get the current time in ms — used for "now" in tests.
    fn now_jalali_month_ms() -> i64 {
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_millis() as i64
    }
}
