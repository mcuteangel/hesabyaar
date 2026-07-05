use crate::models::{Category, Installment, Loan, Transaction, TransactionType};
use crate::currency::format_number;

/// Get offline budget advice based on local rules.
pub fn get_offline_budget_advice(
    transactions: &[Transaction],
    categories: &[Category],
) -> String {
    let total_income: i64 = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Income)
        .map(|t| t.amount)
        .sum();
    let total_expense: i64 = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense)
        .map(|t| t.amount)
        .sum();

    let category_totals: std::collections::HashMap<i64, i64> = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense)
        .fold(std::collections::HashMap::new(), |mut acc, t| {
            *acc.entry(t.category_id).or_insert(0) += t.amount;
            acc
        });

    let highest_cat_id = category_totals
        .iter()
        .max_by_key(|(_, &v)| v)
        .map(|(&k, _)| k);

    let mut sb = String::new();
    sb.push_str("### \u{1F4A1} \u{062A}\u{0648}\u{0635}\u{06CC}\u{0647}\u{0647}\u{0627}\u{06CC} \u{0647}\u{0648}\u{0634}\u{0645}\u{0646}\u{062F} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} (\u{062A}\u{062D}\u{0644}\u{06CC}\u{0644} \u{0627}\u{0633}\u{062A}\u{0641}\u{0627}\u{062F}\u{0647} \u{0645}\u{062D}\u{0644}\u{06CC})\n\n");

    if transactions.is_empty() {
        sb.push_str("\u{0634}\u{0645}\u{0627} \u{0647}\u{0646}\u{0648}\u{0632} \u{0647}\u{06CC}\u{0686} \u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634} \u{06CC} \u{0646}\u{06A9}\u{0631}\u{062F}\u{0647}\u{0627}\u{06CC}\u{062F}. \u{0628}\u{0631}\u{0627}\u{06CC} \u{062F}\u{0631}\u{06CC}\u{0627}\u{0641}\u{062A} \u{062A}\u{062D}\u{0644}\u{06CC}\u{0644} \u{0648}\u{0636}\u{0639}\u{06CC}\u{062A} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} \u{0627}\u{0632} \u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634}\u{0647}\u{0627}\u{06CC} \u{062B}\u{0628}\u{062A} \u{0634}\u{062F}\u{0647} \u{0627}\u{0633}\u{062A}.");
        return sb;
    }

    sb.push_str("\u{0628}\u{0631} \u{0627}\u{0633}\u{0627}\u{0633} \u{062A}\u{062D}\u{0644}\u{06CC}\u{0644} \u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634}\u{0647}\u{0627}\u{06CC} \u{062B}\u{0628}\u{062A} \u{0634}\u{062F}\u{0647} \u{0634}\u{0645}\u{0627} \u{062F}\u{0631} \u{062D}\u{0633}\u{0627}\u{0628}\u{06CC}\u{0627}\u{0631} \u{06AF}\u{0632}\u{0627}\u{0631}\u{0634} \u{0634}\u{062F}\u{0647} \u{0627}\u{0633}\u{062A}:\n\n");

    if total_income > 0 {
        let saving_rate = (total_income - total_expense) as f64 / total_income as f64 * 100.0;
        if saving_rate < 0.0 {
            sb.push_str(&format!(
                "\u{26A0}\u{FE0F} **\u{06A9}\u{0646}\u{062A}\u{0631}\u{0644} \u{062A}\u{0631}\u{0627}\u{0632} \u{0645}\u{062E}\u{0627}\u{0631}\u{062C}:** \u{0645}\u{062A}\u{0627}\u{0633}\u{0641}\u{0627}\u{0646}\u{0647} \u{0645}\u{062E}\u{0627}\u{0631}\u{062C} \u{0634}\u{0645}\u{0627} \u{062F}\u{0631} \u{0627}\u{06CC}\u{0646} \u{062F}\u{0648}\u{0631}\u{0647} \u{0628}\u{06CC}\u{0634} \u{0627}\u{0632} \u{062F}\u{0631}\u{0627}\u{0645}\u{062F}\u{062A}\u{0627}\u{0646} \u{0628}\u{0648}\u{062F}\u{0647} \u{0627}\u{0633}\u{062A} ({:.1}\u{066C} \u{06A9}\u{0633}\u{0631}\u{06CC}).\n\n",
                saving_rate
            ));
        } else if saving_rate < 10.0 {
            sb.push_str(&format!(
                "\u{1F4C9} **\u{0628}\u{0647}\u{0628}\u{0631}\u{0633}\u{0627}\u{0646}\u{06CC} \u{0646}\u{0631}\u{062E} \u{067E}\u{0633}\u{06CC}\u{0627}\u{0646}\u{062F}\u{0627}\u{0632}:** \u{0634}\u{0645}\u{0627} \u{062D}\u{062F}\u{0648}\u{062F} {:.1}\u{066C} \u{0627}\u{0632} \u{062F}\u{0631}\u{0622}\u{0645}\u{062F} \u{062E}\u{0648}\u{062F} \u{0631}\u{0627} \u{067E}\u{0633}\u{06CC}\u{0627}\u{0646}\u{062F}\u{0647}\u{0627}\u{06CC}\u{062F}.\n\n",
                saving_rate
            ));
        } else {
            sb.push_str(&format!(
                "\u{1F389} **\u{0639}\u{0645}\u{0644}\u{06A9}\u{0631}\u{062F} \u{0639}\u{0627}\u{0644}\u{06CC} \u{067E}\u{0633}\u{06CC}\u{0627}\u{0646}\u{062F}\u{0627}\u{0632}:** \u{0622}\u{0641}\u{0631}\u{06CC}\u{0646}! \u{0634}\u{0645}\u{0627} \u{062A}\u{0648}\u{0627}\u{0646}\u{0633}\u{062A}\u{0647}\u{0627}\u{06CC}\u{062F} \u{0628}\u{06CC}\u{0634} \u{0627}\u{0632} {:.1}\u{066C} \u{0627}\u{0632} \u{062F}\u{0631}\u{0622}\u{0645}\u{062F} \u{062E}\u{0648}\u{062F} \u{0631}\u{0627} \u{067E}\u{0633}\u{06CC}\u{0627}\u{0646}\u{062F}\u{0647}\u{0627}\u{06CC}\u{062F}.\n\n",
                saving_rate
            ));
        }
    }

    if let Some(cat_id) = highest_cat_id {
        if let Some(cat) = categories.iter().find(|c| c.id == cat_id) {
            let cat_expense = category_totals.get(&cat_id).unwrap_or(&0);
            sb.push_str(&format!(
                "\u{1F4CA} **\u{0628}\u{0632}\u{0631}\u{06AF}\u{062A}\u{0631}\u{06CC}\u{0646} \u{06A9}\u{0627}\u{0646}\u{0648}\u{0646} \u{0647}\u{0632}\u{06CC}\u{0646}\u{0647}:** \u{062F}\u{0633}\u{062A}\u{0647} **{}** \u{0628}\u{0627} \u{0645}\u{062C}\u{0645}\u{0648}\u{0639} {}.\n\n",
                cat.name, format_number(*cat_expense)
            ));
        }
    }

    sb.push_str("\u{1F4CC} **\u{0642}\u{0648}\u{0627}\u{0646}\u{06CC}\u{0646} \u{0648} \u{0631}\u{0627}\u{0647}\u{06A9}\u{0627}\u{0631}\u{0647}\u{0627}:**\n");
    sb.push_str("- **\u{0627}\u{0633}\u{062A}\u{0631}\u{0627}\u{062A}\u{069C}\u{06CC} 50-30-20:** \u{0646}\u{06CC}\u{0645}\u{06CC} \u{0627}\u{0632} \u{062F}\u{0631}\u{0622}\u{0645}\u{062F} \u{0631}\u{0627} \u{0628}\u{0647} \u{0627}\u{062C}\u{0627}\u{0631}\u{0647} \u{0648} \u{0646}\u{06CC}\u{0627}\u{0632}\u{0647}\u{0627}\u{06CC} \u{0627}\u{0633}\u{0627}\u{0633}\u{06CC} \u{0628}\u{0631}\u{0633}\u{0627}\u{0646}\u{062F} \u{0648} 20 \u{062F}\u{0631}\u{0635}\u{062F} \u{0628}\u{0627}\u{0642}\u{06CC}\u{0645}\u{0647} \u{0631}\u{0627} \u{0628}\u{0647} \u{067E}\u{0633}\u{06CC}\u{0627}\u{0646}\u{062F} \u{06CC}\u{0627} \u{062A}\u{0633}\u{0648}\u{06CC}\u{0647} \u{0628}\u{0647} \u{062A}\u{062E}\u{0635}\u{06CC}\u{0635} \u{062F}\u{0647}\u{06CC}\u{062F}.\n");
    sb.push_str("- **\u{067E}\u{06CC}\u{0634}\u{06AF}\u{06CC}\u{0631}\u{06CC} \u{0627}\u{0632} \u{0641}\u{0631}\u{0627}\u{0631} \u{0645}\u{062E}\u{0627}\u{0631}\u{062C}:** \u{06A9}\u{0648}\u{0686}\u{06A9}\u{062A}\u{0631}\u{06CC}\u{0646} \u{0641}\u{0627}\u{06A9}\u{062A}\u{0648}\u{0631}\u{0647}\u{0627} \u{0631}\u{0627} \u{0647}\u{0645} \u{062F}\u{0631} \u{062D}\u{0633}\u{0627}\u{0628}\u{06CC}\u{0627}\u{0631} \u{062B}\u{0628}\u{062A} \u{06A9}\u{0646}\u{06CC}\u{062F}.\n");
    sb.push_str("- **\u{0627}\u{06CC}\u{062C}\u{0627}\u{062F} \u{0635}\u{0646}\u{062F}\u{0648}\u{0642} \u{0627}\u{0636}\u{062A}\u{0631}\u{0627}\u{0637}\u{064A}:** \u{0647}\u{0645}\u{06CC}\u{0634}\u{0647} \u{0645}\u{0639}\u{0627}\u{062F}\u{0644} 3 \u{0627}\u{0644}\u{06CC} 6 \u{0628}\u{0627}\u{0631}\u{0628} \u{0645}\u{062E}\u{0627}\u{0631}\u{062C} \u{0645}\u{0627}\u{0647}\u{0627}\u{0646}\u{0647} \u{062E}\u{0648}\u{062F} \u{0631}\u{0627} \u{062F}\u{0631} \u{06CC}\u{06A9} \u{062D}\u{0633}\u{0627}\u{0628} \u{0645}\u{062C}\u{0632}\u{0627} \u{0628}\u{0631}\u{0627}\u{06CC} \u{0628}\u{0631}\u{0648}\u{0632}\u{0634} \u{063A}\u{06CC}\u{0631}\u{0645}\u{062A}\u{0642}\u{0628}\u{0647} \u{0630}\u{062E}\u{06CC}\u{0631}\u{0647} \u{06A9}\u{0646}\u{06CC}\u{062F}.\n");

    sb
}

/// Get offline budget forecast.
pub fn get_offline_forecast(
    transactions: &[Transaction],
    loans: &[Loan],
    installments: &[Installment],
) -> String {
    let unpaid_installments: Vec<&Installment> = installments.iter().filter(|i| !i.is_paid).collect();
    let upcoming_sum: i64 = unpaid_installments.iter().map(|i| i.amount).sum();

    if transactions.is_empty() && unpaid_installments.is_empty() {
        return "\u{0647}\u{0646}\u{0648}\u{0632} \u{0627}\u{0637}\u{0644}\u{0627}\u{0639}\u{0627}\u{062A} \u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634} \u{06CC} \u{0642}\u{0633}\u{0637} \u{062F}\u{0631} \u{062D}\u{0633}\u{0627}\u{0628}\u{06CC}\u{0627}\u{0631} \u{062B}\u{0628}\u{062A} \u{0646}\u{0634}\u{062F}\u{0647} \u{0627}\u{0633}\u{062A}. \u{0644}\u{0637}\u{0641}\u{0627} \u{062E}\u{0637}\u{0627} \u{0648} \u{062E}\u{0631}\u{062C} \u{0647}\u{0627}\u{06CC} \u{0631}\u{0648}\u{0632}\u{0627}\u{0646}\u{0647} \u{062E}\u{0648}\u{062F} \u{0631}\u{0627} \u{0648}\u{0627}\u{0631}\u{062F} \u{06A9}\u{0646}\u{06CC}\u{062F}.".to_string();
    }

    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64;
    let window_start = now_ms - 90 * 24 * 60 * 60 * 1000;
    let recent: Vec<&Transaction> = transactions.iter().filter(|t| t.date >= window_start).collect();

    let recent_income: i64 = recent.iter().filter(|t| t.tx_type == TransactionType::Income).map(|t| t.amount).sum();
    let recent_expense: i64 = recent.iter().filter(|t| t.tx_type == TransactionType::Expense).map(|t| t.amount).sum();

    let avg_income = if recent.iter().any(|t| t.tx_type == TransactionType::Income) { recent_income / 3 } else { 0 };
    let avg_expense = if recent.iter().any(|t| t.tx_type == TransactionType::Expense) { recent_expense / 3 } else { 0 };
    let est_balance = avg_income - avg_expense - upcoming_sum;

    let mut sb = String::new();
    sb.push_str("### \u{1F52E} \u{067E}\u{06CC}\u{0634}\u{0628}\u{06CC}\u{0646}\u{06CC} \u{0647}\u{0648}\u{0634}\u{0645}\u{0646}\u{062F} \u{0648}\u{0636}\u{0639}\u{06CC}\u{062A} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} \u{0645}\u{0627}\u{0647} \u{0622}\u{06CC}\u{0646}\u{062F}\u{0647}\n\n");
    sb.push_str(&format!("- \u{1F4B5} **\u{062F}\u{0631}\u{0622}\u{0645}\u{062F} \u{062A}\u{062E}\u{0645}\u{06CC}\u{0646}\u{06CC}:** {}\n", format_number(avg_income)));
    sb.push_str(&format!("- \u{1F4B8} **\u{0645}\u{062E}\u{0627}\u{0631}\u{062C} \u{062A}\u{062E}\u{0645}\u{06CC}\u{0646}\u{06CC}:** {}\n", format_number(avg_expense)));
    sb.push_str(&format!("- \u{1F4C5} **\u{062A}\u{0639}\u{0647}\u{062F} \u{0627}\u{0642}\u{0633}\u{0627}\u{0637}:** {}\n", format_number(upcoming_sum)));

    if est_balance < 0 {
        sb.push_str(&format!(
            "\n### \u{1F6A8} \u{0647}\u{0634}\u{062F}\u{0627}\u{0631} \u{0647}\u{0648}\u{0634}\u{0645}\u{0646}\u{062F}: \u{0631}\u{06CC}\u{0633}\u{06A9} \u{06A9}\u{0633}\u{0631}\u{06CC} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} \u{062F}\u{0631} \u{0645}\u{0627}\u{0647} \u{0628}\u{0639}\u{062F}!\n\u{0628}\u{0627} \u{0646}\u{06AF}\u{0631}\u{0627}\u{0646}\u{06CC} \u{062E}\u{0641}\u{06CC}\u{0641} \u{062A}\u{0631}\u{0627}\u{0632} \u{0646}\u{0642}\u{062F}\u{06CC} \u{0634}\u{0645}\u{0627} \u{062F}\u{0631} \u{0645}\u{0627}\u{0647} \u{0622}\u{06CC}\u{0646}\u{062F}\u{0647} \u{0628}\u{0627} **\u{06A9}\u{0633}\u{0631}\u{06CC} \u{062D}\u{062F}\u{0648}\u{062F} {}** \u{0631}\u{0648}\u{0628}\u{0631}\u{0647} \u{062E}\u{0648}\u{0647}\u{062F}.\n\n",
            format_number(est_balance.abs())
        ));
    } else {
        sb.push_str(&format!(
            "\n### \u{1F7E2} \u{0647}\u{0634}\u{062F}\u{0627}\u{0631} \u{0647}\u{0648}\u{0634}\u{0645}\u{0646}\u{062F}: \u{0648}\u{0636}\u{0639}\u{06CC}\u{062A} \u{0645}\u{0627}\u{0644}\u{06CC} \u{067E}\u{0627}\u{06CC}\u{062F}\u{0627}\u{0631}\n\u{0628}\u{0631}\u{0627}\u{0633}\u{0627}\u{0633} \u{0627}\u{0644}\u{06AF}\u{0648}\u{06CC} \u{062F}\u{062E}\u{0644} \u{0648} \u{062E}\u{0631}\u{062C} \u{0634}\u{0645}\u{0627} \u{062F}\u{0631} \u{0645}\u{0627}\u{0647} \u{0622}\u{06CC}\u{0646}\u{062F}\u{0647} \u{0628}\u{0627} **\u{0645}\u{0627}\u{0632}\u{0627}\u{062F} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} {}** \u{067E}\u{0637} \u{0633}\u{0628}\u{06A9} \u{0628}\u{06AF}\u{0631}\u{062F}\u{06CC}\u{062F}.\n\n",
            format_number(est_balance)
        ));
    }

    sb
}

/// Calculate debt-to-income ratio.
pub fn calculate_debt_to_income_ratio(
    loans: &[Loan],
    installments: &[Installment],
    monthly_income: i64,
) -> f64 {
    let monthly_debt_payments: i64 = installments
        .iter()
        .filter(|i| !i.is_paid)
        .map(|i| i.amount)
        .sum::<i64>()
        + loans
            .iter()
            .filter(|l| !l.is_settled && l.loan_type == "CREDITOR")
            .map(|l| l.remaining_amount / 12)
            .sum::<i64>();

    if monthly_income <= 0 && monthly_debt_payments > 0 {
        return 1.0;
    }
    if monthly_income <= 0 {
        return 0.0;
    }
    monthly_debt_payments as f64 / monthly_income as f64
}

/// Predict time to reach a savings goal.
pub fn predict_time_to_goal(current_savings: i64, monthly_savings: i64, goal_amount: i64) -> i32 {
    if monthly_savings <= 0 {
        return -1;
    }
    let remaining = goal_amount - current_savings;
    if remaining <= 0 {
        0
    } else {
        ((remaining + monthly_savings - 1) / monthly_savings) as i32
    }
}

/// Calculate financial health score (0-100).
pub fn calculate_financial_health_score(
    transactions: &[Transaction],
    loans: &[Loan],
    installments: &[Installment],
    _categories: &[Category],
) -> i32 {
    if transactions.is_empty() {
        return 0;
    }

    let total_income: i64 = transactions.iter().filter(|t| t.tx_type == TransactionType::Income).map(|t| t.amount).sum();
    let total_expense: i64 = transactions.iter().filter(|t| t.tx_type == TransactionType::Expense).map(|t| t.amount).sum();
    let balance = total_income - total_expense;

    let mut score: i32 = 50;

    // Savings rate (max +25)
    if total_income > 0 {
        let savings_rate = balance as f64 / total_income as f64;
        score += if savings_rate >= 0.3 {
            25
        } else if savings_rate >= 0.2 {
            20
        } else if savings_rate >= 0.1 {
            10
        } else if savings_rate >= 0.0 {
            0
        } else {
            -15
        };
    }

    // Debt-to-income (max +15)
    let debt_ratio = calculate_debt_to_income_ratio(loans, installments, total_income);
    score += if debt_ratio <= 0.1 {
        15
    } else if debt_ratio <= 0.2 {
        10
    } else if debt_ratio <= 0.3 {
        5
    } else if debt_ratio <= 0.4 {
        0
    } else {
        -10
    };

    // Category diversification (+10 if 3+ categories)
    let expense_cats: std::collections::HashSet<i64> = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense)
        .map(|t| t.category_id)
        .collect();
    score += if expense_cats.len() >= 5 {
        10
    } else if expense_cats.len() >= 3 {
        5
    } else {
        0
    };

    score.clamp(0, 100)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_predict_time_to_goal() {
        assert_eq!(predict_time_to_goal(0, 1000, 10000), 10);
        assert_eq!(predict_time_to_goal(10000, 1000, 10000), 0);
        assert_eq!(predict_time_to_goal(0, 0, 10000), -1);
    }

    #[test]
    fn test_debt_to_income_ratio() {
        assert_eq!(calculate_debt_to_income_ratio(&[], &[], 0), 0.0);
        assert_eq!(calculate_debt_to_income_ratio(&[], &[], 100000), 0.0);
    }
}
