// RUST-LINT-BASELINE: `budget.rs` + `budget/tests.rs` is the
// child-module pattern for the split test module. The lint prefers
// `budget/mod.rs`, but the split keeps the production file path stable.
#![allow(clippy::self_named_module_files)]

use crate::currency::format_currency;
use crate::models::{
    is_tx_category_excluded, BankLoan, Category, CurrencyUnit, Installment, Loan, Transaction, TransactionType,
};

/// Get offline budget advice based on local rules.
pub fn get_offline_budget_advice(
    transactions: &[Transaction],
    categories: &[Category],
    excluded_category_ids: &[i64],
) -> String {
    let (total_income, total_expense) = transactions
        .iter()
        .filter(|t| !is_tx_category_excluded(t, excluded_category_ids))
        .fold((0i64, 0i64), |(income, expense), t| match t.tx_type {
            TransactionType::Income => (income.saturating_add(t.amount), expense),
            TransactionType::Expense => (income, expense.saturating_add(t.amount)),
            _ => (income, expense),
        });

    let category_totals: std::collections::HashMap<i64, i64> = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense && !is_tx_category_excluded(t, excluded_category_ids))
        .fold(std::collections::HashMap::new(), |mut acc, t| {
            let total = acc.entry(t.category_id).or_insert(0);
            *total = total.saturating_add(t.amount);
            acc
        });

    let highest_cat_id = category_totals.iter().max_by_key(|(_, &v)| v).map(|(&k, _)| k);

    let mut sb = String::new();
    sb.push_str("### \u{1F4A1} \u{062A}\u{0648}\u{0635}\u{06CC}\u{0647}\u{0647}\u{0627}\u{06CC} \u{0647}\u{0648}\u{0634}\u{0645}\u{0646}\u{062F} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} (\u{062A}\u{062D}\u{0644}\u{06CC}\u{0644} \u{0627}\u{0633}\u{062A}\u{0641}\u{0627}\u{062F}\u{0647} \u{0645}\u{062D}\u{0644}\u{06CC})\n\n");

    // Plan 011 D2: gate on the filtered set, so a wallet whose transactions
    // are all excluded gets the no-data message instead of a zero analysis.
    if transactions
        .iter()
        .all(|t| is_tx_category_excluded(t, excluded_category_ids))
    {
        sb.push_str("\u{0634}\u{0645}\u{0627} \u{0647}\u{0646}\u{0648}\u{0632} \u{0647}\u{06CC}\u{0686} \u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634} \u{06CC} \u{0646}\u{06A9}\u{0631}\u{062F}\u{0647}\u{0627}\u{06CC}\u{062F}. \u{0628}\u{0631}\u{0627}\u{06CC} \u{062F}\u{0631}\u{06CC}\u{0627}\u{0641}\u{062A} \u{062A}\u{062D}\u{0644}\u{06CC}\u{0644} \u{0648}\u{0636}\u{0639}\u{06CC}\u{062A} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} \u{0627}\u{0632} \u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634}\u{0647}\u{0627}\u{06CC} \u{062B}\u{0628}\u{062A} \u{0634}\u{062F}\u{0647} \u{0627}\u{0633}\u{062A}.");
        return sb;
    }

    sb.push_str("\u{0628}\u{0631} \u{0627}\u{0633}\u{0627}\u{0633} \u{062A}\u{062D}\u{0644}\u{06CC}\u{0644} \u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634}\u{0647}\u{0627}\u{06CC} \u{062B}\u{0628}\u{062A} \u{0634}\u{062F}\u{0647} \u{0634}\u{0645}\u{0627} \u{062F}\u{0631} \u{062D}\u{0633}\u{0627}\u{0628}\u{06CC}\u{0627}\u{0631} \u{06AF}\u{0632}\u{0627}\u{0631}\u{0634} \u{0634}\u{062F}\u{0647} \u{0627}\u{0633}\u{062A}:\n\n");

    if total_income > 0 {
        let saving_rate = (total_income.saturating_sub(total_expense)) as f64 / total_income as f64 * 100.0;
        if saving_rate < 0.0 {
            sb.push_str(&format!(
                "\u{26A0}\u{FE0F} **\u{06A9}\u{0646}\u{062A}\u{0631}\u{0644} \u{062A}\u{0631}\u{0627}\u{0632} \u{0645}\u{062E}\u{0627}\u{0631}\u{062C}:** \u{0645}\u{062A}\u{0627}\u{0633}\u{0641}\u{0627}\u{0646}\u{0647} \u{0645}\u{062E}\u{0627}\u{0631}\u{062C} \u{0634}\u{0645}\u{0627} \u{062F}\u{0631} \u{0627}\u{06CC}\u{0646} \u{062F}\u{0648}\u{0631}\u{0647} \u{0628}\u{06CC}\u{0634} \u{0627}\u{0632} \u{062F}\u{0631}\u{0627}\u{0645}\u{062F}\u{062A}\u{0627}\u{0646} \u{0628}\u{0648}\u{062F}\u{0647} \u{0627}\u{0633}\u{062A} ({:.1}\u{066C} \u{06A9}\u{0633}\u{0631}\u{06CC}).\n\n",
                saving_rate
            ));
        } else if saving_rate < 10.0 {
            sb.push_str(&format!(
                "\u{1F4C9} **\u{0628}\u{0647}\u{0628}\u{0631}\u{0633}\u{0627}\u{0646}\u{06CC} \u{0646}\u{0631}\u{062E} \u{067E}\u{0633}\u{200C}\u{0627}\u{0646}\u{062F}\u{0627}\u{0632}:** \u{0634}\u{0645}\u{0627} \u{062D}\u{062F}\u{0648}\u{062F} {:.1}\u{066C} \u{0627}\u{0632} \u{062F}\u{0631}\u{0622}\u{0645}\u{062F} \u{062E}\u{0648}\u{062F} \u{0631}\u{0627} \u{067E}\u{0633}\u{06CC}\u{0627}\u{0646}\u{062F}\u{0647}\u{0627}\u{06CC}\u{062F}.\n\n",
                saving_rate
            ));
        } else {
            sb.push_str(&format!(
                "\u{1F389} **\u{0639}\u{0645}\u{0644}\u{06A9}\u{0631}\u{062F} \u{0639}\u{0627}\u{0644}\u{06CC} \u{067E}\u{0633}\u{200C}\u{0627}\u{0646}\u{062F}\u{0627}\u{0632}:** \u{0622}\u{0641}\u{0631}\u{06CC}\u{0646}! \u{0634}\u{0645}\u{0627} \u{062A}\u{0648}\u{0627}\u{0646}\u{0633}\u{062A}\u{0647}\u{0627}\u{06CC}\u{062F} \u{0628}\u{06CC}\u{0634} \u{0627}\u{0632} {:.1}\u{066C} \u{0627}\u{0632} \u{062F}\u{0631}\u{0622}\u{0645}\u{062F} \u{062E}\u{0648}\u{062F} \u{0631}\u{0627} \u{067E}\u{0633}\u{06CC}\u{0627}\u{0646}\u{062F}\u{0647}\u{0627}\u{06CC}\u{062F}.\n\n",
                saving_rate
            ));
        }
    }

    if let Some(cat_id) = highest_cat_id {
        if let Some(cat) = categories.iter().find(|c| c.id == cat_id) {
            let cat_expense = category_totals.get(&cat_id).unwrap_or(&0);
            sb.push_str(&format!(
                "\u{1F4CA} **\u{0628}\u{0632}\u{0631}\u{06AF}\u{062A}\u{0631}\u{06CC}\u{0646} \u{06A9}\u{0627}\u{0646}\u{0648}\u{0646} \u{0647}\u{0632}\u{06CC}\u{0646}\u{0647}:** \u{062F}\u{0633}\u{062A}\u{0647} **{}** \u{0628}\u{0627} \u{0645}\u{062C}\u{0645}\u{0648}\u{0639} {}.\n\n",
                cat.name, format_currency(*cat_expense, CurrencyUnit::Toman)
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
    bank_loans: &[BankLoan],
    excluded_category_ids: &[i64],
) -> String {
    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64;
    get_offline_forecast_at(
        transactions,
        loans,
        installments,
        bank_loans,
        excluded_category_ids,
        now_ms,
    )
}

/// Internal helper for offline budget forecast with an explicit reference timestamp in milliseconds.
#[allow(clippy::too_many_lines)] // RUST-LINT-BASELINE
fn get_offline_forecast_at(
    transactions: &[Transaction],
    loans: &[Loan],
    installments: &[Installment],
    bank_loans: &[BankLoan],
    excluded_category_ids: &[i64],
    now_ms: i64,
) -> String {
    let thirty_days_ms = 30 * 24 * 60 * 60 * 1000;
    let upcoming_installments: Vec<&Installment> = installments
        .iter()
        .filter(|i| !i.is_paid && i.due_date >= now_ms && i.due_date <= now_ms.saturating_add(thirty_days_ms))
        .collect();
    let upcoming_sum: i64 = upcoming_installments
        .iter()
        .map(|i| i.amount)
        .fold(0, |total, amount| total.saturating_add(amount));

    let unsettled_creditor_loan_monthly: i64 = loans
        .iter()
        .filter(|l| !l.is_settled && l.loan_type == "CREDITOR")
        .map(|l| l.remaining_amount / 12)
        .fold(0, |total, amount| total.saturating_add(amount));
    let total_obligations = upcoming_sum.saturating_add(unsettled_creditor_loan_monthly);

    // Active (unsettled) bank-loan count and debt, summed as
    // total_repayable_amount. Mirrors Kotlin buildLocalOfflineForecast: bank
    // loans guard the "no data" message and are rendered as an active-debt line.
    let (active_bank_loan_count, bank_loan_debt) = bank_loans
        .iter()
        .filter(|b| !b.is_settled)
        .fold((0usize, 0i64), |(count, debt), b| {
            (count + 1, debt.saturating_add(b.total_repayable_amount))
        });

    // Plan 011 D2: "no data" means nothing left to forecast from once the
    // excluded categories are dropped, not merely an empty raw table.
    let has_included_txs = transactions
        .iter()
        .any(|t| !is_tx_category_excluded(t, excluded_category_ids));
    if !has_included_txs && total_obligations == 0 && active_bank_loan_count == 0 {
        return "\u{0647}\u{0646}\u{0648}\u{0632} \u{0627}\u{0637}\u{0644}\u{0627}\u{0639}\u{0627}\u{062A} \u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634} \u{06CC} \u{0642}\u{0633}\u{0637} \u{062F}\u{0631} \u{062D}\u{0633}\u{0627}\u{0628}\u{06CC}\u{0627}\u{0631} \u{062B}\u{0628}\u{062A} \u{0646}\u{0634}\u{062F}\u{0647} \u{0627}\u{0633}\u{062A}. \u{0644}\u{0637}\u{0641}\u{0627} \u{062E}\u{0637}\u{0627} \u{0648} \u{062E}\u{0631}\u{062C} \u{0647}\u{0627}\u{06CC} \u{0631}\u{0648}\u{0632}\u{0627}\u{0646}\u{0647} \u{062E}\u{0648}\u{062F} \u{0631}\u{0627} \u{0648}\u{0627}\u{0631}\u{062F} \u{06A9}\u{0646}\u{06CC}\u{062F}.".to_string();
    }

    let window_start = now_ms.saturating_sub(90 * 24 * 60 * 60 * 1000);
    let recent: Vec<&Transaction> = transactions
        .iter()
        .filter(|t| t.date >= window_start && t.date <= now_ms && !is_tx_category_excluded(t, excluded_category_ids))
        .collect();

    let recent_income: i64 = recent
        .iter()
        .filter(|t| t.tx_type == TransactionType::Income)
        .map(|t| t.amount)
        .fold(0, |total, amount| total.saturating_add(amount));
    let recent_expense: i64 = recent
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense)
        .map(|t| t.amount)
        .fold(0, |total, amount| total.saturating_add(amount));

    let ms_per_day: i64 = 24 * 60 * 60 * 1000;
    let days_span = if !recent.is_empty() {
        let oldest_date = recent.iter().map(|t| t.date).min().unwrap_or(now_ms);
        // Ceiling division into whole days, minimum 1. Avoids f64, which cannot
        // represent Rial amounts above 2^53 exactly.
        (now_ms.saturating_sub(oldest_date) + ms_per_day - 1) / ms_per_day
    } else {
        1
    };
    // Fractional-month normalization: avg = sum * 30 / days_span (days clamped to min 30).
    // i128 keeps Rial exact above 2^53; result always ≤ sum, so the as i64 cast is safe.
    let normalization_days: i128 = days_span.max(30) as i128;

    let avg_income = if recent.iter().any(|t| t.tx_type == TransactionType::Income) {
        ((recent_income as i128 * 30) / normalization_days) as i64
    } else {
        0
    };
    let avg_expense = if recent.iter().any(|t| t.tx_type == TransactionType::Expense) {
        ((recent_expense as i128 * 30) / normalization_days) as i64
    } else {
        0
    };
    // Use saturating_sub to prevent overflow/wrap under extreme values.
    // All inputs are non-negative i64; underflow would silently wrap in release mode.
    let est_balance = avg_income.saturating_sub(avg_expense).saturating_sub(total_obligations);

    let mut sb = String::new();
    sb.push_str("### \u{1F52E} \u{067E}\u{06CC}\u{0634}\u{0628}\u{06CC}\u{0646}\u{06CC} \u{0647}\u{0648}\u{0634}\u{0645}\u{0646}\u{062F} \u{0648}\u{0636}\u{0639}\u{06CC}\u{062A} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} \u{0645}\u{0627}\u{0647} \u{0622}\u{06CC}\u{0646}\u{062F}\u{0647}\n\n");
    sb.push_str(&format!("- \u{1F4B5} **\u{062F}\u{0631}\u{0622}\u{0645}\u{062F} \u{062A}\u{062E}\u{0645}\u{06CC}\u{0646}\u{06CC}:** {}\n", format_currency(avg_income, CurrencyUnit::Toman)));
    sb.push_str(&format!("- \u{1F4B8} **\u{0645}\u{062E}\u{0627}\u{0631}\u{062C} \u{062A}\u{062E}\u{0645}\u{06CC}\u{0646}\u{06CC}:** {}\n", format_currency(avg_expense, CurrencyUnit::Toman)));
    sb.push_str(&format!(
        "- \u{1F4C5} **\u{062A}\u{0639}\u{0647}\u{062F} \u{0627}\u{0642}\u{0633}\u{0627}\u{0637}:** {}\n",
        format_currency(total_obligations, CurrencyUnit::Toman)
    ));
    if active_bank_loan_count > 0 {
        sb.push_str(&format!(
            "- **\u{0628}\u{062F}\u{0647}\u{06CC}\u{0647}\u{0627}\u{06CC} \u{0641}\u{0639}\u{0627}\u{0644}:** {} \u{0645}\u{0648}\u{0631}\u{062F} \u{0628}\u{0647} \u{0645}\u{0628}\u{0644}\u{063A} {}\n",
            active_bank_loan_count,
            format_currency(bank_loan_debt, CurrencyUnit::Toman)
        ));
    }

    if est_balance < 0 {
        sb.push_str(&format!(
            "\n### \u{1F6A8} \u{0647}\u{0634}\u{062F}\u{0627}\u{0631} \u{0647}\u{0648}\u{0634}\u{0645}\u{0646}\u{062F}: \u{0631}\u{06CC}\u{0633}\u{06A9} \u{06A9}\u{0633}\u{0631}\u{06CC} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} \u{062F}\u{0631} \u{0645}\u{0627}\u{0647} \u{0628}\u{0639}\u{062F}!\n\u{0628}\u{0627} \u{0646}\u{06AF}\u{0631}\u{0627}\u{0646}\u{06CC} \u{062E}\u{0641}\u{06CC}\u{0641} \u{062A}\u{0631}\u{0627}\u{0632} \u{0646}\u{0642}\u{062F}\u{06CC} \u{0634}\u{0645}\u{0627} \u{062F}\u{0631} \u{0645}\u{0627}\u{0647} \u{0622}\u{06CC}\u{0646}\u{062F}\u{0647} \u{0628}\u{0627} **\u{06A9}\u{0633}\u{0631}\u{06CC} \u{062D}\u{062F}\u{0648}\u{062F} {}** \u{0631}\u{0648}\u{0628}\u{0631}\u{0648} \u{062E}\u{0648}\u{0627}\u{0647}\u{062F}.\n\n",
            format_currency(est_balance.saturating_abs(), CurrencyUnit::Toman)
        ));
    } else {
        sb.push_str(&format!(
            "\n### \u{1F7E2} \u{0647}\u{0634}\u{062F}\u{0627}\u{0631} \u{0647}\u{0648}\u{0634}\u{0645}\u{0646}\u{062F}: \u{0648}\u{0636}\u{0639}\u{06CC}\u{062A} \u{0645}\u{0627}\u{0644}\u{06CC} \u{067E}\u{0627}\u{06CC}\u{062F}\u{0627}\u{0631}\n\u{0628}\u{0631}\u{0627}\u{0633}\u{0627}\u{0633} \u{0627}\u{0644}\u{06AF}\u{0648}\u{06CC} \u{062F}\u{062E}\u{0644} \u{0648} \u{062E}\u{0631}\u{062C} \u{0634}\u{0645}\u{0627} \u{062F}\u{0631} \u{0645}\u{0627}\u{0647} \u{0622}\u{06CC}\u{0646}\u{062F}\u{0647} \u{0628}\u{0627} **\u{0645}\u{0627}\u{0632}\u{0627}\u{062F} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647} {}** \u{067E}\u{0637} \u{0633}\u{0628}\u{06A9} \u{0628}\u{06AF}\u{0631}\u{062F}\u{06CC}\u{062F}.\n\n",
            format_currency(est_balance, CurrencyUnit::Toman)
        ));
    }

    sb
}

/// Monthly income baseline over the trailing 90-day window, bounded by `now_ms`.
///
/// Future-dated transactions are excluded so scheduled income does not inflate
/// the baseline. Returns 0 when there is no income in the window.
fn monthly_income_baseline(transactions: &[Transaction], now_ms: i64, excluded_category_ids: &[i64]) -> i64 {
    let window_start = now_ms - 90 * 24 * 60 * 60 * 1000;
    let recent: Vec<&Transaction> = transactions
        .iter()
        .filter(|t| {
            t.tx_type == TransactionType::Income
                && t.date >= window_start
                && t.date <= now_ms
                && !is_tx_category_excluded(t, excluded_category_ids)
        })
        .collect();
    if recent.is_empty() {
        return 0;
    }
    let ms_per_day: i64 = 24 * 60 * 60 * 1000;
    let oldest = recent.iter().map(|t| t.date).min().unwrap_or(now_ms);
    let days = (now_ms.saturating_sub(oldest) + ms_per_day - 1) / ms_per_day;
    // Fractional-month baseline: avg = sum * 30 / days (i128 to avoid 2^53 loss).
    let sum: i64 = recent.iter().map(|t| t.amount).fold(0, |acc, a| acc.saturating_add(a));
    ((sum as i128 * 30) / (days.max(30) as i128)) as i64
}

/// Calculate debt-to-income ratio.
pub fn calculate_debt_to_income_ratio(
    loans: &[Loan],
    installments: &[Installment],
    // Intentionally excluded from DTI (matches Kotlin local impl; only
    // consumer loans and installments count toward the ratio).
    _bank_loans: &[BankLoan],
    monthly_income: i64,
) -> f64 {
    let unpaid_installments: i64 = installments
        .iter()
        .filter(|i| !i.is_paid)
        .fold(0i64, |acc, i| acc.saturating_add(i.amount));
    let unpaid_creditors: i64 = loans
        .iter()
        .filter(|l| !l.is_settled && l.loan_type == "CREDITOR")
        .fold(0i64, |acc, l| acc.saturating_add(l.remaining_amount / 12));
    let monthly_debt_payments: i64 = unpaid_installments.saturating_add(unpaid_creditors);

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
    let remaining = goal_amount.saturating_sub(current_savings);
    if remaining <= 0 {
        0
    } else {
        // months = ceil(remaining / monthly_savings)
        // Compute the quotient and remainder directly so the numerator never
        // overflows: the old `(remaining + monthly_savings - 1) / monthly_savings`
        // saturated `remaining + monthly_savings` to i64::MAX for huge goals,
        // dropping the carry and undercounting the duration. Clamp to i32::MAX to
        // mirror the Kotlin fallback's `coerceAtMost(Int.MAX_VALUE)` so the Rust
        // and Kotlin paths never diverge (a raw `as i32` would wrap a huge count
        // into a negative value).
        let q = remaining / monthly_savings;
        let months = if remaining % monthly_savings == 0 {
            q
        } else {
            q.saturating_add(1)
        };
        (months.clamp(0, i32::MAX as i64)) as i32
    }
}

/// Calculate financial health score (0-100).
#[allow(clippy::too_many_lines)] // RUST-LINT-BASELINE
pub fn calculate_financial_health_score(
    transactions: &[Transaction],
    loans: &[Loan],
    installments: &[Installment],
    bank_loans: &[BankLoan],
    _categories: &[Category],
    excluded_category_ids: &[i64],
) -> i32 {
    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64;

    // Plan 011 D2: score a wallet with no included transactions as 0 (no
    // data) instead of scoring a zero-budget snapshot of raw rows that are
    // all excluded from every KPI.
    if transactions
        .iter()
        .all(|t| is_tx_category_excluded(t, excluded_category_ids))
    {
        return 0;
    }

    let total_income: i64 = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Income && !is_tx_category_excluded(t, excluded_category_ids))
        .map(|t| t.amount)
        .fold(0, |total, amount| total.saturating_add(amount));
    let total_expense: i64 = transactions
        .iter()
        .filter(|t| t.tx_type == TransactionType::Expense && !is_tx_category_excluded(t, excluded_category_ids))
        .map(|t| t.amount)
        .fold(0, |total, amount| total.saturating_add(amount));
    let balance = total_income.saturating_sub(total_expense);

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
    // Scope income to a monthly baseline (trailing 90d window) so the
    // all-time accumulated income does not understate the ratio relative to
    // the monthly debt/installment obligations.
    let monthly_income = monthly_income_baseline(transactions, now_ms, excluded_category_ids);
    let debt_ratio = calculate_debt_to_income_ratio(loans, installments, bank_loans, monthly_income);
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
        .filter(|t| t.tx_type == TransactionType::Expense && !is_tx_category_excluded(t, excluded_category_ids))
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
mod tests;
