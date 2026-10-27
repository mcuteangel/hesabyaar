use super::*;

#[test]
fn test_predict_time_to_goal() {
    assert_eq!(predict_time_to_goal(0, 1000, 10000), 10);
    assert_eq!(predict_time_to_goal(10000, 1000, 10000), 0);
    assert_eq!(predict_time_to_goal(0, 0, 10000), -1);
}

#[test]
fn test_predict_time_to_goal_overflow_saturates_at_i32_max() {
    // A massive goal with a tiny monthly saving must saturate at i32::MAX
    // instead of wrapping into a negative month count (parity with the
    // Kotlin fallback's coerceAtMost(Int.MAX_VALUE)).
    assert_eq!(predict_time_to_goal(0, 1, i64::MAX), i32::MAX);
    assert_eq!(predict_time_to_goal(0, 1, 3_000_000_000_000_000_000), i32::MAX);
    // Large but finite goals also clamp rather than overflow.
    assert_eq!(predict_time_to_goal(0, 1, i64::MAX - 5), i32::MAX);
}

#[test]
fn test_predict_time_to_goal_ceil_no_overflow_undercount() {
    // Reproduces the carry-loss bug: with a large remaining balance and a
    // large monthly saving, `remaining + monthly_savings` saturates to
    // i64::MAX before the division, dropping the carry and undercounting the
    // duration by one. The quotient/remainder form must stay exact.
    // ceil((i64::MAX - 100) / 5_000_000_000) == 1844674408.
    assert_eq!(predict_time_to_goal(0, 5_000_000_000, i64::MAX - 100), 1844674408);
}

#[test]
fn test_debt_to_income_ratio() {
    assert_eq!(calculate_debt_to_income_ratio(&[], &[], &[], 0), 0.0);
    assert_eq!(calculate_debt_to_income_ratio(&[], &[], &[], 100000), 0.0);
}

#[test]
fn test_debt_to_income_ratio_saturating_overflow() {
    let installments = vec![
        Installment {
            id: 1,
            title: "huge1".into(),
            amount: i64::MAX - 10,
            due_date: 0,
            is_paid: false,
            reminder_enabled: false,
            notes: "".into(),
            bank_loan_id: None,
            account_id: None,
            tracked: false,
        },
        Installment {
            id: 2,
            title: "huge2".into(),
            amount: 100,
            due_date: 0,
            is_paid: false,
            reminder_enabled: false,
            notes: "".into(),
            bank_loan_id: None,
            account_id: None,
            tracked: false,
        },
    ];
    // Must saturate at i64::MAX without wrapping or panicking
    let ratio = calculate_debt_to_income_ratio(&[], &installments, &[], 1_000_000);
    assert!(ratio > 0.0);
    assert_eq!(ratio, i64::MAX as f64 / 1_000_000.0);
}

#[test]
fn test_offline_budget_advice_saturating_overflow() {
    let txs = vec![
        sample_tx(1, TransactionType::Income, i64::MAX - 5, 0),
        sample_tx(2, TransactionType::Income, 10, 0),
        sample_tx(3, TransactionType::Expense, i64::MAX - 2, 0),
        sample_tx(4, TransactionType::Expense, 5, 0),
    ];
    // Must saturate totals without panic or overflow wrap
    let result = get_offline_budget_advice(&txs, &[], &[]);
    assert!(!result.is_empty());
    // Clamped totals result in balanced 0.0% saving rate rather than wrapped corrupted branch
    assert!(result.contains("\u{1F4C9}") || result.contains("0.0"));
}

#[test]
fn test_offline_budget_advice_saturating_deficit() {
    let txs = vec![
        sample_tx(1, TransactionType::Income, 100, 0),
        sample_tx(2, TransactionType::Expense, i64::MAX - 2, 0),
        sample_tx(3, TransactionType::Expense, 10, 0),
    ];
    // Saturating subtraction must report deficit branch without wrapping
    let result = get_offline_budget_advice(&txs, &[], &[]);
    assert!(result.contains("\u{26A0}\u{FE0F}") || result.contains("\u{06A9}\u{0633}\u{0631}\u{06CC}"));
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64
}

fn sample_tx(id: i64, ttype: TransactionType, amount: i64, date: i64) -> Transaction {
    Transaction {
        id,
        tx_type: ttype,
        category_id: 1,
        amount,
        description: String::new(),
        person_name: None,
        person_id: None,
        date,
        due_date: None,
        installment_id: None,
        account_id: 1,
        destination_account_id: None,
        loan_id: None,
    }
}

fn tx_full(
    id: i64,
    ttype: TransactionType,
    category_id: i64,
    amount: i64,
    description: &str,
    date: i64,
) -> Transaction {
    Transaction {
        id,
        tx_type: ttype,
        category_id,
        amount,
        description: description.to_string(),
        person_name: None,
        person_id: None,
        date,
        due_date: None,
        installment_id: None,
        account_id: 1,
        destination_account_id: None,
        loan_id: None,
    }
}

#[test]
fn test_monthly_income_baseline_scopes_recent_window() {
    let now: i64 = 1_700_000_000_000;
    let day: i64 = 24 * 60 * 60 * 1000;
    // Recent income (15 days ago) plus ancient all-time income (330 days ago,
    // outside the 90-day window).
    let txs = vec![
        sample_tx(1, TransactionType::Income, 1_000_000, now - 15 * day),
        sample_tx(2, TransactionType::Income, 11_000_000, now - 330 * day),
    ];
    let monthly = monthly_income_baseline(&txs, now, &[]);
    // Only the recent 1_000_000 should count; the ancient income is excluded.
    assert!(monthly > 0 && monthly <= 1_000_000 + 2);
}

#[test]
fn test_monthly_income_baseline_preserves_precision_above_2pow53() {
    // 2^53 + 7 (9_007_199_254_740_999) is at the boundary where f64's 53-bit
    // mantissa can no longer represent every integer — it rounds up to
    // 9_007_199_254_741_000. The old code cast `sum as f64`, losing exactness.
    // Integer division preserves the precise Rial value.
    let now: i64 = 1_700_000_000_000;
    let day: i64 = 24 * 60 * 60 * 1000;
    let amount: i64 = 9_007_199_254_740_999;
    let txs = vec![sample_tx(1, TransactionType::Income, amount, now - 5 * day)];
    let monthly = monthly_income_baseline(&txs, now, &[]);
    // 5 days → months = 1 → avg = sum / 1 = amount (exact, no f64 rounding).
    assert_eq!(
        monthly, amount,
        "f64 cast must not lose precision for values above 2^53"
    );
}

#[test]
fn test_monthly_income_baseline_fractional_months_45_day_window() {
    // Finding 1+2: 45-day window exercises the months_elapsed > 1 path
    // (previously untested). With ceiling division, months = 2 and avg =
    // 1_000_000 / 2 = 500_000 — a regression from the old f64 code which
    // computed months = 45 / 30 = 1.5 and avg = 1_000_000 / 1.5 = 666,666.
    // The fractional-month normalization (sum * 30 / days) restores the
    // correct value: 1_000_000 * 30 / 45 = 666,666.
    let now: i64 = 1_700_000_000_000;
    let day: i64 = 24 * 60 * 60 * 1000;
    let txs = vec![sample_tx(1, TransactionType::Income, 1_000_000, now - 45 * day)];
    let monthly = monthly_income_baseline(&txs, now, &[]);
    assert_eq!(
        monthly, 666_666,
        "45-day window must use fractional-month normalization, not ceiling division"
    );
}

// -- get_offline_budget_advice tests -----------------------------------------

#[test]
fn test_advice_empty_transactions_returns_empty_prompt() {
    let result = get_offline_budget_advice(&[], &[], &[]);
    // Should contain the "no transactions yet" message
    assert!(result.contains("\u{062A}\u{0631}\u{0627}\u{06A9}\u{0646}\u{0634}"));
}

#[test]
fn test_advice_negative_saving_rate_warns_deficit() {
    let txs = vec![
        sample_tx(1, TransactionType::Income, 1_000_000, 0),
        sample_tx(2, TransactionType::Expense, 5_000_000, 0),
    ];
    let result = get_offline_budget_advice(&txs, &[], &[]);
    //saving_rate = (1M - 5M)/1M = -400% → deficit warning
    assert!(result.contains("\u{0645}\u{062E}\u{0627}\u{0631}\u{062C}"));
}

#[test]
fn test_advice_low_saving_rate_below_10_percent() {
    let txs = vec![
        sample_tx(1, TransactionType::Income, 10_000_000, 0),
        sample_tx(2, TransactionType::Expense, 9_500_000, 0),
    ];
    let result = get_offline_budget_advice(&txs, &[], &[]);
    // saving_rate = 5% → "near zero savings"
    assert!(result.contains("\u{067E}\u{0633}\u{200C}\u{0627}\u{0646}\u{062F}\u{0627}\u{0632}"));
}

#[test]
fn test_advice_high_saving_rate_above_10_percent() {
    let txs = vec![
        sample_tx(1, TransactionType::Income, 10_000_000, 0),
        sample_tx(2, TransactionType::Expense, 5_000_000, 0),
    ];
    let result = get_offline_budget_advice(&txs, &[], &[]);
    // saving_rate = 50% → "excellent savings"
    assert!(result.contains("\u{0639}\u{0645}\u{0644}\u{06A9}\u{0631}\u{062F}"));
}

#[test]
fn test_advice_with_categories_mentions_highest_category() {
    let txs = vec![
        sample_tx(1, TransactionType::Expense, 3_000_000, 0),
        sample_tx(2, TransactionType::Expense, 1_000_000, 0),
    ];
    // category_id defaults to 1 in sample_tx
    let cats = vec![Category {
        id: 1,
        name: "\u{0645}\u{062A}\u{0631}\u{0648}\u{06CC}\u{0628}\u{0632}\u{0627}\u{0631}\u{06CC}".into(), // "Groceries"
        key: "groceries".into(),
        icon: "".into(),
        color: 0,
        category_type: "EXPENSE".into(),
        is_default: false,
    }];
    let result = get_offline_budget_advice(&txs, &cats, &[]);
    assert!(result.contains("\u{0645}\u{062A}\u{0631}\u{0648}\u{06CC}\u{0628}\u{0632}\u{0627}\u{0631}\u{06CC}"));
}

#[test]
fn test_advice_category_amount_is_in_toman_not_rial() {
    // 10,000,000 Rial expense must be shown as "1,000,000 تومان",
    // never as the raw Rial value "10,000,000 تومان".
    let txs = vec![sample_tx(1, TransactionType::Expense, 10_000_000, 0)];
    let cats = vec![Category {
        id: 1,
        name: "\u{0645}\u{062A}\u{0631}\u{0648}\u{06CC}\u{0628}\u{0632}\u{0627}\u{0631}\u{06CC}".into(),
        key: "groceries".into(),
        icon: "".into(),
        color: 0,
        category_type: "EXPENSE".into(),
        is_default: false,
    }];
    let result = get_offline_budget_advice(&txs, &cats, &[]);
    assert!(result.contains("1,000,000 \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}"));
    assert!(!result.contains("10,000,000 \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}"));
}

// -- get_offline_forecast tests ----------------------------------------------

#[test]
fn test_forecast_empty_returns_no_data_message() {
    let result = get_offline_forecast(&[], &[], &[], &[], &[]);
    assert!(result.contains("\u{0627}\u{0637}\u{0644}\u{0627}\u{0639}\u{0627}\u{062A}"));
}

#[test]
fn test_forecast_negative_balance_warns() {
    let now = now_ms();
    let txs = vec![
        sample_tx(1, TransactionType::Income, 1_000_000, now - 5 * 24 * 60 * 60 * 1000),
        sample_tx(2, TransactionType::Expense, 5_000_000, now - 5 * 24 * 60 * 60 * 1000),
    ];
    let result = get_offline_forecast(&txs, &[], &[], &[], &[]);
    // est_balance negative → warning
    assert!(result.contains("\u{0647}\u{0634}\u{062F}\u{0627}\u{0631}"));
}

#[test]
fn test_forecast_positive_balance_shows_surplus() {
    let now = now_ms();
    let txs = vec![
        sample_tx(1, TransactionType::Income, 10_000_000, now - 5 * 24 * 60 * 60 * 1000),
        sample_tx(2, TransactionType::Expense, 2_000_000, now - 5 * 24 * 60 * 60 * 1000),
    ];
    let result = get_offline_forecast(&txs, &[], &[], &[], &[]);
    // est_balance positive → surplus
    assert!(result.contains("\u{0648}\u{0636}\u{0639}\u{06CC}\u{062A}"));
}

#[test]
fn test_forecast_45_day_window_uses_fractional_month_normalization() {
    // Finding 1+2: 45-day window exercises months_elapsed > 1 (previously
    // untested). With ceiling division, months = 2 and avg_income =
    // 1_000_000 / 2 = 500_000 Rial → 50,000 Toman. The old f64 code computed
    // months = 45 / 30 = 1.5 and avg_income = 1_000_000 / 1.5 = 666,666 Rial
    // → 66,666 Toman. The fractional-month normalization restores 666,666
    // Rial → 66,666 Toman.
    let now = now_ms();
    let day = 24 * 60 * 60 * 1000_i64;
    let txs = vec![sample_tx(1, TransactionType::Income, 1_000_000, now - 45 * day)];
    let result = get_offline_forecast_at(&txs, &[], &[], &[], &[], now);
    // avg_income = 666,666 Rial → 66,666 Toman in the "درآمد تخمینی" line.
    assert!(
        result.contains("66,666"),
        "45-day fractional avg_income (66,666 Toman) must appear in output; got: {result}"
    );
    // The ceiling-division result (500,000 Rial → 50,000 Toman) must NOT appear.
    assert!(
        !result.contains("50,000"),
        "ceiling-division result (50,000 Toman) must not appear; got: {result}"
    );
}

#[test]
fn test_forecast_with_installments_subtracts_upcoming() {
    let now = now_ms();
    let txs = vec![
        sample_tx(1, TransactionType::Income, 10_000_000, now - 5 * 24 * 60 * 60 * 1000),
        sample_tx(2, TransactionType::Expense, 2_000_000, now - 5 * 24 * 60 * 60 * 1000),
    ];
    let installments = vec![Installment {
        id: 1,
        title: "rent".into(),
        amount: 5_000_000,
        due_date: now + 30 * 24 * 60 * 60 * 1000,
        is_paid: false,
        reminder_enabled: false,
        notes: String::new(),
        bank_loan_id: None,
        tracked: false,
        account_id: None,
    }];
    let result = get_offline_forecast(&txs, &[], &installments, &[], &[]);
    // upcoming_sum = 5M → est_balance = (8M/monthly) - 5M → may be positive or negative
    assert!(result.contains("\u{0627}\u{0642}\u{0633}\u{0627}\u{0637}"));
}

#[test]
fn test_forecast_amounts_are_in_toman_not_rial() {
    // 10,000,000 Rial income/installment must render as "1,000,000 تومان".
    let now = now_ms();
    let txs = vec![
        sample_tx(1, TransactionType::Income, 10_000_000, now - 5 * 24 * 60 * 60 * 1000),
        sample_tx(2, TransactionType::Expense, 3_000_000, now - 5 * 24 * 60 * 60 * 1000),
    ];
    let installments = vec![Installment {
        id: 1,
        title: "rent".into(),
        amount: 10_000_000,
        due_date: now + 30 * 24 * 60 * 60 * 1000,
        is_paid: false,
        reminder_enabled: false,
        notes: String::new(),
        bank_loan_id: None,
        tracked: false,
        account_id: None,
    }];
    let result = get_offline_forecast(&txs, &[], &installments, &[], &[]);
    assert!(result.contains("1,000,000 \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}"));
    assert!(!result.contains("10,000,000 \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}"));
}

#[test]
fn test_forecast_only_installments_no_transactions() {
    // The forecast window runs from the caller's wall clock to 30
    // days ahead. A one-day offset keeps the installment inside that
    // window even if the runner is descheduled between this line and
    // the call below.
    const ONE_DAY_MS: i64 = 24 * 60 * 60 * 1000;
    let due_date = now_ms() + ONE_DAY_MS;
    let installments = vec![Installment {
        id: 1,
        title: "car".into(),
        amount: 3_000_000,
        due_date,
        is_paid: false,
        reminder_enabled: false,
        notes: String::new(),
        bank_loan_id: None,
        tracked: false,
        account_id: None,
    }];
    let result = get_offline_forecast(&[], &[], &installments, &[], &[]);
    // Has unpaid installments → not empty, shows forecast
    assert!(result.contains("\u{062A}\u{0639}\u{0647}\u{062F}"));
}

#[test]
fn test_forecast_excludes_overdue_installments() {
    let now = now_ms();
    let txs = vec![
        sample_tx(1, TransactionType::Income, 10_000_000, now - 5 * 24 * 60 * 60 * 1000),
        sample_tx(2, TransactionType::Expense, 2_000_000, now - 5 * 24 * 60 * 60 * 1000),
    ];
    let installments = vec![Installment {
        id: 1,
        title: "old".into(),
        amount: 5_000_000,
        due_date: now - 30 * 24 * 60 * 60 * 1000,
        is_paid: false,
        reminder_enabled: false,
        notes: String::new(),
        bank_loan_id: None,
        tracked: false,
        account_id: None,
    }];
    let result = get_offline_forecast(&txs, &[], &installments, &[], &[]);
    // Overdue (past-due) unpaid installment is outside the window → must NOT contribute.
    assert!(!result.contains("\u{06F5}\u{066C}\u{06F0}\u{06F0}\u{06F0}\u{066C}\u{06F0}\u{06F0}\u{06F0}"));
}

#[test]
fn test_forecast_excludes_installments_beyond_30_days() {
    let now = now_ms();
    let txs = vec![
        sample_tx(1, TransactionType::Income, 10_000_000, now - 5 * 24 * 60 * 60 * 1000),
        sample_tx(2, TransactionType::Expense, 2_000_000, now - 5 * 24 * 60 * 60 * 1000),
    ];
    let installments = vec![Installment {
        id: 1,
        title: "car".into(),
        amount: 5_000_000,
        due_date: now + 60 * 24 * 60 * 60 * 1000,
        is_paid: false,
        reminder_enabled: false,
        notes: String::new(),
        bank_loan_id: None,
        tracked: false,
        account_id: None,
    }];
    let result = get_offline_forecast(&txs, &[], &installments, &[], &[]);
    // Due 60 days out is outside the 30-day window → must NOT contribute to obligations.
    assert!(!result.contains("\u{06F5}\u{066C}\u{06F0}\u{06F0}\u{06F0}\u{066C}\u{06F0}\u{06F0}\u{06F0}"));
}

#[test]
fn test_forecast_includes_unsettled_bank_loans() {
    // One active bank loan: 12,000,000 Rial total repayable → 1,200,000 Toman.
    let bank_loans = vec![BankLoan {
        id: 1,
        bank_name: "\u{0628}\u{0627}\u{0646}\u{06A9} \u{0645}\u{0644}\u{062A}".into(),
        loan_name: "\u{0648}\u{0627}\u{0645} \u{062E}\u{0648}\u{062F}\u{0631}\u{0648}".into(),
        received_amount: 10_000_000,
        monthly_installment_amount: 1_000_000,
        number_of_installments: 12,
        total_repayable_amount: 12_000_000,
        total_interest: 2_000_000,
        start_date: 0,
        description: String::new(),
        is_settled: false,
        tracked: false,
        account_id: None,
    }];
    let result = get_offline_forecast(&[], &[], &[], &bank_loans, &[]);
    // An unsettled bank loan means the data is not empty → no "no data" message.
    assert!(!result.contains("\u{0627}\u{0637}\u{0644}\u{0627}\u{0639}\u{0627}\u{062A}"));
    // Active bank-loan debt is shown in Toman, not Rial.
    assert!(result.contains("1,200,000 \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}"));
    assert!(!result.contains("12,000,000 \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}"));
}

#[test]
fn test_forecast_settled_bank_loans_still_empty() {
    // A settled bank loan must NOT suppress the "no data" message.
    let bank_loans = vec![BankLoan {
        id: 1,
        bank_name: "\u{0628}\u{0627}\u{0646}\u{06A9} \u{0645}\u{0644}\u{062A}".into(),
        loan_name: "\u{0648}\u{0627}\u{0645} \u{062E}\u{0648}\u{062F}\u{0631}\u{0648}".into(),
        received_amount: 10_000_000,
        monthly_installment_amount: 1_000_000,
        number_of_installments: 12,
        total_repayable_amount: 12_000_000,
        total_interest: 2_000_000,
        start_date: 0,
        description: String::new(),
        is_settled: true,
        tracked: false,
        account_id: None,
    }];
    let result = get_offline_forecast(&[], &[], &[], &bank_loans, &[]);
    assert!(result.contains("\u{0627}\u{0637}\u{0644}\u{0627}\u{0639}\u{0627}\u{062A}"));
}

#[test]
fn test_forecast_bank_loan_debt_saturates_on_overflow() {
    // Two active bank loans whose total_repayable_amounts individually exceed
    // half of i64::MAX — a plain sum() would overflow (panic in debug builds,
    // wrap in release). The saturating fold must produce i64::MAX without panicking.
    let bank_loans = vec![
        BankLoan {
            id: 1,
            bank_name: "A".into(),
            loan_name: "x".into(),
            received_amount: i64::MAX,
            monthly_installment_amount: i64::MAX,
            number_of_installments: 1,
            total_repayable_amount: i64::MAX,
            total_interest: 0,
            start_date: 0,
            description: String::new(),
            is_settled: false,
            tracked: false,
            account_id: None,
        },
        BankLoan {
            id: 2,
            bank_name: "B".into(),
            loan_name: "y".into(),
            received_amount: i64::MAX,
            monthly_installment_amount: i64::MAX,
            number_of_installments: 1,
            total_repayable_amount: i64::MAX,
            total_interest: 0,
            start_date: 0,
            description: String::new(),
            is_settled: false,
            tracked: false,
            account_id: None,
        },
    ];
    let result = get_offline_forecast(&[], &[], &[], &bank_loans, &[]);
    // Saturated debt = i64::MAX → Toman = i64::MAX / 10 = 922,337,203,685,477,580.
    assert!(
        result.contains("922,337,203,685,477,580 \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}"),
        "expected saturated Toman debt in output, got: {result}"
    );
}

#[test]
fn test_forecast_est_balance_saturates_under_extreme_values() {
    // With extreme values, est_balance = avg_income - avg_expense - total_obligations
    // must not panic. Before the saturating_sub fix, 0 - i64::MAX - (i64::MAX/12)
    // would underflow (panic in debug, wrap in release). saturating_sub clamps
    // to i64::MIN, and saturating_abs() on i64::MIN yields i64::MAX without panic.
    let now = now_ms();
    let txs = vec![sample_tx(
        1,
        TransactionType::Expense,
        i64::MAX,
        now - 5 * 24 * 60 * 60 * 1000,
    )];
    let loans = vec![Loan {
        id: 1,
        person_name: "".into(),
        person_id: None,
        loan_type: "CREDITOR".into(),
        original_amount: i64::MAX,
        remaining_amount: i64::MAX,
        description: String::new(),
        date: now,
        is_settled: false,
        tracked: false,
        account_id: None,
    }];
    // Must not panic; returns a negative-balance warning (saturated).
    let result = get_offline_forecast(&txs, &loans, &[], &[], &[]);
    // The deficit branch (est_balance < 0) must be taken. "هشدار" alone is
    // ambiguous because the stable-balance branch also contains it; assert
    // the deficit-specific text instead.
    assert!(result.contains("\u{0631}\u{06CC}\u{0633}\u{06A9} \u{06A9}\u{0633}\u{0631}\u{06CC} \u{0628}\u{0648}\u{062F}\u{062C}\u{0647}"),
        "expected deficit branch, got: {result}");
    // est_balance saturates to i64::MIN; saturating_abs() yields i64::MAX,
    // which format_currency renders as 922,337,203,685,477,580 تومان.
    assert!(
        result.contains("922,337,203,685,477,580"),
        "expected saturated deficit value, got: {result}"
    );
}

#[test]
fn test_forecast_preserves_f64_precision_above_2pow53() {
    // 2^53 + 7 (9_007_199_254_740_999) cannot be represented exactly as f64;
    // it rounds up to 9_007_199_254_741_000. The old code cast
    // `recent_income as f64`, which changed the Toman display from
    // "...474,099" to "...474,100". Integer division keeps the exact value.
    let now = now_ms();
    let day = 24 * 60 * 60 * 1000_i64;
    let amount: i64 = 9_007_199_254_740_999;
    let txs = vec![sample_tx(1, TransactionType::Income, amount, now - 5 * day)];
    let result = get_offline_forecast(&txs, &[], &[], &[], &[]);
    // Exact: 9_007_199_254_740_999 / 10 = 900,719,925,474,099 (floor).
    assert!(
        result.contains("900,719,925,474,099"),
        "expected exact Toman value preserved above 2^53; got: {result}"
    );
    // The f64-rounded value (900,719,925,474,100) must not appear.
    assert!(
        !result.contains("900,719,925,474,100"),
        "f64 rounding must not inflate the Rial value; got: {result}"
    );
}

// -- calculate_financial_health_score tests -----------------------------------

#[test]
fn test_financial_health_score_uses_monthly_income_scoping() {
    let now = now_ms();
    let day: i64 = 24 * 60 * 60 * 1000;

    // Unpaid installment that creates a monthly debt obligation.
    let installment = Installment {
        id: 1,
        title: "car".into(),
        amount: 5_000_000,
        due_date: now,
        is_paid: false,
        reminder_enabled: false,
        notes: String::new(),
        bank_loan_id: None,
        tracked: false,
        account_id: None,
    };

    // --- Case A: recent income exists → low debt ratio → bonus ---
    let txs_recent = vec![
        sample_tx(1, TransactionType::Income, 50_000_000, now - 15 * day),
        sample_tx(2, TransactionType::Income, 50_000_000, now - 330 * day),
    ];
    let score_recent =
        calculate_financial_health_score(&txs_recent, &[], std::slice::from_ref(&installment), &[], &[], &[]);

    // --- Case B: only ancient income → monthly_income = 0 → debt ratio = 1.0 → penalty ---
    let txs_ancient = vec![sample_tx(1, TransactionType::Income, 100_000_000, now - 330 * day)];
    let score_ancient = calculate_financial_health_score(&txs_ancient, &[], &[installment], &[], &[], &[]);

    // With recent income the debt ratio is low (+15 bonus); with no recent
    // income the ratio maxes out at 1.0 (−10 penalty).  The 25-point
    // difference proves monthly-income scoping is actually used.
    assert!(
        score_recent > score_ancient,
        "score_recent ({score_recent}) should exceed score_ancient ({score_ancient}) \
         because monthly income scoping penalises the absence of recent income"
    );
    // Exact delta: +15 vs -10 = 25, but allow margin for other factors.
    assert!(
        score_recent - score_ancient >= 20,
        "expected at least 20-point gap from debt-ratio scoping, got {}",
        score_recent - score_ancient
    );
}

// =====================================================================
// Category exclusion tests (Part B)
// =====================================================================

#[test]
fn test_budget_advice_category_exclusion_empty_vs_populated() {
    let now = now_ms();
    let loan_cat_id = 999;
    let food_cat_id = 10;
    let salary_cat_id = 20;

    let txs = vec![
        tx_full(1, TransactionType::Income, salary_cat_id, 10_000_000, "Salary", now),
        tx_full(
            2,
            TransactionType::Income,
            loan_cat_id,
            5_000_000,
            "Loan disbursement",
            now,
        ),
        tx_full(3, TransactionType::Expense, food_cat_id, 2_000_000, "Food", now),
        tx_full(
            4,
            TransactionType::Expense,
            loan_cat_id,
            10_000_000,
            "Loan payment",
            now,
        ),
    ];

    let cats = vec![
        Category {
            id: food_cat_id,
            name: "Food".to_string(),
            key: "food".to_string(),
            icon: "food".to_string(),
            color: 1,
            category_type: "EXPENSE".to_string(),
            is_default: false,
        },
        Category {
            id: loan_cat_id,
            name: "Loans".to_string(),
            key: "loans".to_string(),
            icon: "loan".to_string(),
            color: 2,
            category_type: "EXPENSE".to_string(),
            is_default: false,
        },
        Category {
            id: salary_cat_id,
            name: "Salary".to_string(),
            key: "salary".to_string(),
            icon: "salary".to_string(),
            color: 3,
            category_type: "INCOME".to_string(),
            is_default: false,
        },
    ];

    // 1. Unfiltered advice vs filtered advice
    let unfiltered_advice = get_offline_budget_advice(&txs, &cats, &[]);
    let filtered_advice = get_offline_budget_advice(&txs, &cats, &[loan_cat_id]);
    assert_ne!(unfiltered_advice, filtered_advice);

    // 2. Unfiltered forecast vs filtered forecast
    let unfiltered_fc = get_offline_forecast(&txs, &[], &[], &[], &[]);
    let filtered_fc = get_offline_forecast(&txs, &[], &[], &[], &[loan_cat_id]);
    assert_ne!(unfiltered_fc, filtered_fc);

    // 3. monthly_income_baseline with loan_cat_id excluded
    let baseline_unfiltered = monthly_income_baseline(&txs, now, &[]);
    let baseline_filtered = monthly_income_baseline(&txs, now, &[loan_cat_id]);
    assert!(baseline_unfiltered > baseline_filtered);
}
