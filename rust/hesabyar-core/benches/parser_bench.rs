use criterion::{criterion_group, criterion_main, Criterion};
use hesabyar_core::parser::amount::parse_amount;
use hesabyar_core::parser::money_detector::contains_money;
use hesabyar_core::calendar::{gregorian_to_jalali, jalali_to_gregorian};
use hesabyar_core::advisory::{calculate_financial_health_score, get_offline_budget_advice};
use hesabyar_core::models::*;

fn bench_parse_amount(c: &mut Criterion) {
    c.bench_function("parse_500k_toman", |b| {
        b.iter(|| parse_amount("\u{06F5}\u{06F0}\u{06F0} \u{0647}\u{0632}\u{0627}\u{0631} \u{062A}\u{0648}\u{0645}\u{0646} \u{0628}\u{0627}\u{0628}\u{062A} \u{0628}\u{0631}\u{0642}", true))
    });
}

fn bench_money_detector(c: &mut Criterion) {
    c.bench_function("contains_money", |b| {
        b.iter(|| contains_money("\u{0655}\u{06F0}\u{06F0}\u{06F0} \u{0647}\u{0632}\u{0627}\u{0631} \u{062A}\u{0648}\u{0645}\u{0646}"))
    });
}

fn bench_jalali_calendar(c: &mut Criterion) {
    c.bench_function("gregorian_to_jalali", |b| {
        b.iter(|| gregorian_to_jalali(1711000000000))
    });
    c.bench_function("jalali_to_gregorian", |b| {
        b.iter(|| jalali_to_gregorian(1403, 1, 1))
    });
}

fn bench_budget_advice(c: &mut Criterion) {
    let transactions: Vec<Transaction> = (0..100)
        .map(|i| Transaction {
            id: i,
            tx_type: if i % 3 == 0 { TransactionType::Income } else { TransactionType::Expense },
            category_id: (i % 8) as i64,
            amount: (i as i64 + 1) * 10000,
            description: format!("Transaction {}", i),
            person_name: None,
            date: 1711000000000 - (i as i64 * 86400000),
            due_date: None,
            installment_id: None,
        })
        .collect();

    let categories: Vec<Category> = (0..8)
        .map(|i| Category {
            id: i,
            name: format!("Category {}", i),
            key: format!("cat{}", i),
            icon: "Paid".to_string(),
            color: 0xFF000000 + i as i64,
            category_type: "EXPENSE".to_string(),
            is_default: true,
        })
        .collect();

    c.bench_function("offline_budget_advice_100tx", |b| {
        b.iter(|| get_offline_budget_advice(&transactions, &categories))
    });

    c.bench_function("financial_health_score_100tx", |b| {
        b.iter(|| calculate_financial_health_score(&transactions, &[], &[], &categories))
    });
}

criterion_group!(
    benches,
    bench_parse_amount,
    bench_money_detector,
    bench_jalali_calendar,
    bench_budget_advice
);
criterion_main!(benches);
