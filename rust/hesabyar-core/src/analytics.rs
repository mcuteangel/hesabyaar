use crate::models::*;
use crate::calendar::{gregorian_to_jalali, get_jalali_days_in_month};
use std::collections::HashMap;

/// Compute analytics data from transactions, loans, installments, and categories.
pub fn compute_analytics(
    transactions: &[Transaction],
    loans: &[Loan],
    installments: &[Installment],
    categories: &[Category],
) -> AnalyticsData {
    let category_map: HashMap<i64, &Category> = categories.iter().map(|c| (c.id, c)).collect();

    // Monthly spending
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

    let mut monthly_spending: Vec<MonthlyData> = monthly_expense
        .iter()
        .map(|(&(y, m), &expense)| {
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
    monthly_spending.sort_by(|a, b| b.jalali_year.cmp(&a.jalali_year).then(b.jalali_month.cmp(&a.jalali_month)));

    let monthly_inc: Vec<MonthlyData> = monthly_income
        .iter()
        .map(|(&(y, m), &income)| {
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

    // Category breakdown
    let total_expense: i64 = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense)
        .map(|t| t.amount)
        .sum();

    let mut cat_totals: HashMap<i64, i64> = HashMap::new();
    for tx in transactions.iter().filter(|t| t.tx_type == TransactionType::Expense) {
        *cat_totals.entry(tx.category_id).or_insert(0) += tx.amount;
    }

    let category_breakdown: Vec<CategoryBreakdown> = cat_totals
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

    // Debt/credit summaries
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
    }
}
