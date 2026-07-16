use crate::models::*;
use crate::calendar::{gregorian_to_jalali, get_jalali_days_in_month};
use std::collections::HashMap;

/// Compute analytics data from transactions, loans, installments, and categories.
///
/// - Monthly aggregates use the **Jalali calendar** (not Gregorian).
/// - Category breakdown includes percentage-based burn rates.
/// - Debt/credit summaries include progress toward settlement.

/// Build per-bank-loan summary rows, computing the remaining outstanding debt.
///
/// Until per-loan installment linkage is tracked in Rust, `remaining_debt` is
/// `0` when the loan is settled and the full `total_repayable_amount` otherwise.
pub(crate) fn build_bank_loan_summaries(
    bank_loans: &[BankLoan],
    _installments: &[Installment],
) -> Vec<BankLoanSummary> {
    bank_loans
        .iter()
        .map(|b| BankLoanSummary {
            bank_name: b.bank_name.clone(),
            loan_name: b.loan_name.clone(),
            received_amount: b.received_amount,
            total_repayable_amount: b.total_repayable_amount,
            total_interest: b.total_interest,
            number_of_installments: b.number_of_installments,
            is_settled: b.is_settled,
            remaining_debt: if b.is_settled { 0 } else { b.total_repayable_amount },
        })
        .collect()
}

pub fn compute_analytics(
    transactions: &[Transaction],
    loans: &[Loan],
    installments: &[Installment],
    categories: &[Category],
    bank_loans: &[BankLoan],
) -> AnalyticsData {
    let category_map: HashMap<i64, &Category> = categories.iter().map(|c| (c.id, c)).collect();

    // --- Monthly income/expense aggregation ---
    let mut monthly_expense: HashMap<(i32, i32), i64> = HashMap::new();
    let mut monthly_income: HashMap<(i32, i32), i64> = HashMap::new();

    for tx in transactions {
        if let Ok(jdate) = gregorian_to_jalali(tx.date) {
            let key = (jdate.year, jdate.month);
            match tx.tx_type {
                TransactionType::Income => {
                    *monthly_income.entry(key).or_insert(0) += tx.amount;
                }
                TransactionType::Expense => {
                    *monthly_expense.entry(key).or_insert(0) += tx.amount;
                }
                _ => {}
            }
        }
    }

    // Merge all Jalali months seen across income and expense
    let mut all_months: Vec<(i32, i32)> = monthly_expense
        .keys()
        .chain(monthly_income.keys())
        .copied()
        .collect();
    all_months.sort_unstable();
    all_months.dedup();

    let monthly_spending: Vec<MonthlyData> = all_months
        .iter()
        .map(|&(y, m)| {
            let expense = monthly_expense.get(&(y, m)).copied().unwrap_or(0);
            let income = monthly_income.get(&(y, m)).copied().unwrap_or(0);
            let days = get_jalali_days_in_month(y, m);
            MonthlyData {
                jalali_year: y,
                jalali_month: m,
                label: format!("{}/{} ({} days)", y, m, days),
                income,
                expense,
            }
        })
        .collect();

    let monthly_inc: Vec<MonthlyData> = all_months
        .iter()
        .map(|&(y, m)| {
            let income = monthly_income.get(&(y, m)).copied().unwrap_or(0);
            let expense = monthly_expense.get(&(y, m)).copied().unwrap_or(0);
            MonthlyData {
                jalali_year: y,
                jalali_month: m,
                label: format!("{}/{}", y, m),
                income,
                expense,
            }
        })
        .collect();

    // --- Category breakdown (expenses only) ---
    let total_expense: i64 = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense)
        .map(|t| t.amount)
        .sum();

    let mut cat_totals: HashMap<i64, i64> = HashMap::new();
    for tx in transactions.iter().filter(|t| t.tx_type == TransactionType::Expense) {
        *cat_totals.entry(tx.category_id).or_insert(0) += tx.amount;
    }

    let mut category_breakdown: Vec<CategoryBreakdown> = cat_totals
        .iter()
        .map(|(&cat_id, &total)| {
            let cat = category_map.get(&cat_id);
            CategoryBreakdown {
                category_id: cat_id,
                category_name: cat.map(|c| c.name.clone()).unwrap_or_default(),
                color: cat.map(|c| c.color).unwrap_or(0),
                total,
                percentage: if total_expense > 0 {
                    (total as f32 / total_expense as f32) * 100.0
                } else {
                    0.0
                },
            }
        })
        .collect();
    // Sort by total descending for consistent UI ordering
    category_breakdown.sort_by(|a, b| b.total.cmp(&a.total));

    // --- Debt/credit summaries ---
    let debtors: Vec<DebtSummary> = loans
        .iter()
        .filter(|l| l.loan_type == "DEBTOR")
        .map(|l| DebtSummary {
            person_name: l.person_name.clone(),
            original_amount: l.original_amount,
            remaining_amount: l.remaining_amount,
            debt_type: "DEBTOR".to_string(),
            progress: if l.original_amount > 0 {
                (l.original_amount - l.remaining_amount) as f32 / l.original_amount as f32
            } else {
                0.0
            },
        })
        .collect();

    let creditors: Vec<DebtSummary> = loans
        .iter()
        .filter(|l| l.loan_type == "CREDITOR")
        .map(|l| DebtSummary {
            person_name: l.person_name.clone(),
            original_amount: l.original_amount,
            remaining_amount: l.remaining_amount,
            debt_type: "CREDITOR".to_string(),
            progress: if l.original_amount > 0 {
                (l.original_amount - l.remaining_amount) as f32 / l.original_amount as f32
            } else {
                0.0
            },
        })
        .collect();

    let total_debt: i64 = debtors.iter().map(|d| d.remaining_amount).sum();
    let total_credit: i64 = creditors.iter().map(|d| d.remaining_amount).sum();
    let total_installments = installments.len() as i32;
    let paid_installments = installments.iter().filter(|i| i.is_paid).count() as i32;

    let bank_loans = build_bank_loan_summaries(bank_loans, installments);
    let bank_loans_total_debt: i64 = bank_loans.iter().map(|b| b.remaining_debt).sum();

    AnalyticsData {
        monthly_spending,
        monthly_income: monthly_inc,
        category_breakdown,
        debtors,
        creditors,
        total_debt,
        total_credit,
        total_installments,
        paid_installments,
        bank_loans,
        bank_loans_total_debt,
    }
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

    fn category(id: i64, name: &str) -> Category {
        Category {
            id,
            name: name.to_string(),
            key: name.to_lowercase().replace(' ', "_"),
            icon: String::new(),
            color: 0xFF0000 + id,
            category_type: "expense".to_string(),
            is_default: false,
        }
    }

    fn loan(id: i64, loan_type: &str, person: &str, original: i64, remaining: i64) -> Loan {
        Loan {
            id,
            person_name: person.to_string(),
            loan_type: loan_type.to_string(),
            original_amount: original,
            remaining_amount: remaining,
            description: String::new(),
            date: 0,
            is_settled: false,
        }
    }

    fn installment(id: i64, amount: i64, paid: bool) -> Installment {
        Installment {
            id,
            title: format!("Installment {}", id),
            amount,
            due_date: 0,
            is_paid: paid,
            reminder_enabled: false,
            notes: String::new(),
        }
    }

    fn now_ms() -> i64 {
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_millis() as i64
    }

    // =====================================================================
    // Empty input — should not panic
    // =====================================================================

    #[test]
    fn test_empty_all() {
        let result = compute_analytics(&[], &[], &[], &[], &[]);
        assert!(result.monthly_spending.is_empty());
        assert!(result.monthly_income.is_empty());
        assert!(result.category_breakdown.is_empty());
        assert!(result.debtors.is_empty());
        assert!(result.creditors.is_empty());
        assert_eq!(result.total_debt, 0);
        assert_eq!(result.total_credit, 0);
        assert_eq!(result.total_installments, 0);
        assert_eq!(result.paid_installments, 0);
    }

    // =====================================================================
    // Monthly aggregation
    // =====================================================================

    #[test]
    fn test_monthly_expense_grouping() {
        let now = now_ms();
        let txs = vec![
            tx(1, TransactionType::Expense, 100_000, now, 1),
            tx(2, TransactionType::Expense, 200_000, now, 1),
        ];
        let result = compute_analytics(&txs, &[], &[], &[], &[]);
        // Both transactions are in the current Jalali month → single MonthlyData
        assert_eq!(result.monthly_spending.len(), 1);
        assert_eq!(result.monthly_spending[0].expense, 300_000);
    }

    #[test]
    fn test_monthly_income_and_expense_separate() {
        let now = now_ms();
        let txs = vec![
            tx(1, TransactionType::Income, 1_000_000, now, 1),
            tx(2, TransactionType::Expense, 400_000, now, 1),
        ];
        let result = compute_analytics(&txs, &[], &[], &[], &[]);
        assert_eq!(result.monthly_spending.len(), 1);
        assert_eq!(result.monthly_spending[0].income, 1_000_000);
        assert_eq!(result.monthly_spending[0].expense, 400_000);
    }

    #[test]
    fn test_loan_types_excluded_from_monthly() {
        let now = now_ms();
        let txs = vec![
            tx(1, TransactionType::Income, 500_000, now, 1),
            tx(2, TransactionType::LoanDebtor, 300_000, now, 1),
            tx(3, TransactionType::LoanCreditor, 200_000, now, 1),
            tx(4, TransactionType::Installment, 100_000, now, 1),
        ];
        let result = compute_analytics(&txs, &[], &[], &[], &[]);
        // Only Income contributes to monthly_income
        assert_eq!(result.monthly_spending[0].income, 500_000);
        assert_eq!(result.monthly_spending[0].expense, 0);
    }

    #[test]
    fn test_monthly_label_includes_jalali_month_days() {
        let now = now_ms();
        let txs = vec![tx(1, TransactionType::Expense, 100, now, 1)];
        let result = compute_analytics(&txs, &[], &[], &[], &[]);
        let label = &result.monthly_spending[0].label;
        // Label should be like "1404/4 (31 days)"
        assert!(label.contains("days)"), "Label should include days: {}", label);
    }

    // =====================================================================
    // Category breakdown
    // =====================================================================

    #[test]
    fn test_category_breakdown_expenses_only() {
        let now = now_ms();
        let cats = vec![category(1, "Food"), category(2, "Transport")];
        let txs = vec![
            tx(1, TransactionType::Expense, 300_000, now, 1),
            tx(2, TransactionType::Expense, 100_000, now, 1),
            tx(3, TransactionType::Expense, 200_000, now, 2),
            tx(4, TransactionType::Income, 500_000, now, 1), // income — excluded
        ];
        let result = compute_analytics(&txs, &[], &[], &cats, &[]);
        assert_eq!(result.category_breakdown.len(), 2);

        // Food: 300k + 100k = 400k, Transport: 200k
        let food = result.category_breakdown.iter().find(|c| c.category_id == 1).unwrap();
        let transport = result.category_breakdown.iter().find(|c| c.category_id == 2).unwrap();
        assert_eq!(food.total, 400_000);
        assert_eq!(transport.total, 200_000);
    }

    #[test]
    fn test_category_percentages_sum_to_100() {
        let now = now_ms();
        let cats = vec![category(1, "A"), category(2, "B"), category(3, "C")];
        let txs = vec![
            tx(1, TransactionType::Expense, 500, now, 1),
            tx(2, TransactionType::Expense, 300, now, 2),
            tx(3, TransactionType::Expense, 200, now, 3),
        ];
        let result = compute_analytics(&txs, &[], &[], &cats, &[]);
        let total_pct: f32 = result.category_breakdown.iter().map(|c| c.percentage).sum();
        assert!((total_pct - 100.0).abs() < 0.01, "Percentages should sum to ~100, got {}", total_pct);
    }

    #[test]
    fn test_category_unknown_id_gets_empty_name() {
        let now = now_ms();
        let txs = vec![tx(1, TransactionType::Expense, 500, now, 999)];
        let result = compute_analytics(&txs, &[], &[], &[], &[]);
        assert_eq!(result.category_breakdown.len(), 1);
        assert_eq!(result.category_breakdown[0].category_name, "");
        assert_eq!(result.category_breakdown[0].category_id, 999);
    }

    #[test]
    fn test_category_breakdown_sorted_by_total_desc() {
        let now = now_ms();
        let cats = vec![category(1, "A"), category(2, "B")];
        let txs = vec![
            tx(1, TransactionType::Expense, 100, now, 2),
            tx(2, TransactionType::Expense, 500, now, 1),
        ];
        let result = compute_analytics(&txs, &[], &[], &cats, &[]);
        assert_eq!(result.category_breakdown[0].total, 500);
        assert_eq!(result.category_breakdown[1].total, 100);
    }

    #[test]
    fn test_zero_total_expense_gives_zero_percentages() {
        let txs = vec![tx(1, TransactionType::Income, 1000, now_ms(), 1)];
        let result = compute_analytics(&txs, &[], &[], &[], &[]);
        // No expenses → category_breakdown is empty
        assert!(result.category_breakdown.is_empty());
    }

    // =====================================================================
    // Debt/Credit summaries
    // =====================================================================

    #[test]
    fn test_debtors_and_creditors() {
        let loans = vec![
            loan(1, "DEBTOR", "Ali", 1_000_000, 400_000),
            loan(2, "CREDITOR", "Reza", 2_000_000, 1_000_000),
        ];
        let result = compute_analytics(&[], &loans, &[], &[], &[]);
        assert_eq!(result.debtors.len(), 1);
        assert_eq!(result.creditors.len(), 1);
        assert_eq!(result.total_debt, 400_000);
        assert_eq!(result.total_credit, 1_000_000);
    }

    #[test]
    fn test_debt_progress_calculation() {
        // Original 1M, remaining 400k → paid 600k → progress = 60%
        let loans = vec![loan(1, "DEBTOR", "Ali", 1_000_000, 400_000)];
        let result = compute_analytics(&[], &loans, &[], &[], &[]);
        let d = &result.debtors[0];
        assert!((d.progress - 0.6).abs() < 1e-5);
    }

    #[test]
    fn test_zero_original_amount_gives_zero_progress() {
        let loans = vec![loan(1, "DEBTOR", "Ali", 0, 0)];
        let result = compute_analytics(&[], &loans, &[], &[], &[]);
        assert_eq!(result.debtors[0].progress, 0.0);
    }

    // =====================================================================
    // Installment tracking
    // =====================================================================

    #[test]
    fn test_installment_counts() {
        let installments = vec![
            installment(1, 100_000, true),
            installment(2, 200_000, false),
            installment(3, 300_000, true),
        ];
        let result = compute_analytics(&[], &[], &installments, &[], &[]);
        assert_eq!(result.total_installments, 3);
        assert_eq!(result.paid_installments, 2);
    }

    #[test]
    fn test_empty_installments() {
        let result = compute_analytics(&[], &[], &[], &[], &[]);
        assert_eq!(result.total_installments, 0);
        assert_eq!(result.paid_installments, 0);
    }

    // =====================================================================
    // Cross-module correctness: currency rule
    // =====================================================================

    #[test]
    fn test_amounts_preserved_in_rial() {
        // All amounts are stored in Rial. Analytics must not convert them.
        let now = now_ms();
        let txs = vec![tx(1, TransactionType::Expense, 42, now, 1)];
        let cats = vec![category(1, "Test")];
        let result = compute_analytics(&txs, &[], &[], &cats, &[]);
        assert_eq!(result.category_breakdown[0].total, 42);
        assert_eq!(result.monthly_spending[0].expense, 42);
    }

    // =====================================================================
    // Large dataset stress test
    // =====================================================================

    #[test]
    fn test_large_dataset_no_panic() {
        let now = now_ms();
        let mut txs = Vec::new();
        for i in 0..10_000 {
            let tx_type = if i % 3 == 0 { TransactionType::Income } else { TransactionType::Expense };
            let cat_id = (i % 5) as i64 + 1;
            txs.push(tx(i as i64, tx_type, (i as i64) * 100, now, cat_id));
        }
        let cats: Vec<Category> = (1..=5).map(|id| category(id, &format!("Cat{}", id))).collect();
        let result = compute_analytics(&txs, &[], &[], &cats, &[]);
        assert_eq!(result.category_breakdown.len(), 5);
        // Percentages should still sum to ~100
        let total_pct: f32 = result.category_breakdown.iter().map(|c| c.percentage).sum();
        assert!((total_pct - 100.0).abs() < 0.1);
    }
}
