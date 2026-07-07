use criterion::{criterion_group, criterion_main, Criterion};
use hesabyar_core::parser::amount::parse_amount;
use hesabyar_core::parser::money_detector::contains_money;
use hesabyar_core::calendar::{gregorian_to_jalali, jalali_to_gregorian};
use hesabyar_core::advisory::{calculate_financial_health_score, get_offline_budget_advice};
use hesabyar_core::search::{search_transactions, SearchQuery};
use hesabyar_core::crypto::{encrypt_backup, decrypt_backup, compute_checksum, build_encrypted_backup_file, KEY_LEN};
use hesabyar_core::validation::{validate_transaction, validate_backup_payload};
use hesabyar_core::ai_validation::{parse_ai_transaction_json, validate_ai_advice};
use hesabyar_core::models::*;

fn bench_parse_amount(c: &mut Criterion) {
    c.bench_function("parse_500k_toman", |b| {
        b.iter(|| parse_amount("\u{06F5}\u{06F0}\u{06F0} \u{0647}\u{0632}\u{0627}\u{0631} \u{062A}\u{0648}\u{0645}\u{0646} \u{0628}\u{0627}\u{0628}\u{062A} \u{0628}\u{0631}\u{0642}", true))
    });
}

fn bench_money_detector(c: &mut Criterion) {
    c.bench_function("contains_money", |b| {
        b.iter(|| contains_money("\u{06F5}\u{06F0}\u{06F0}\u{06F0} \u{0647}\u{0632}\u{0627}\u{0631} \u{062A}\u{0648}\u{0645}\u{0646}"))
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

fn bench_search(c: &mut Criterion) {
    let transactions: Vec<Transaction> = (0..1000)
        .map(|i| Transaction {
            id: i,
            tx_type: if i % 3 == 0 { TransactionType::Income } else { TransactionType::Expense },
            category_id: (i % 8) as i64,
            amount: (i as i64 + 1) * 10000,
            description: format!("\u{062E}\u{0631}\u{06CC}\u{062F} \u{0628}\u{0631}\u{0642} {}", i),
            person_name: if i % 5 == 0 { Some(format!("Person {}", i)) } else { None },
            date: 1711000000000 - (i as i64 * 86400000),
            due_date: None,
            installment_id: None,
        })
        .collect();

    let text_query = SearchQuery {
        text: "\u{062E}\u{0631}\u{06CC}\u{062F}".to_string(), // "خرید"
        min_amount: 0,
        max_amount: 0,
        start_date: 0,
        end_date: 0,
        category_id: 0,
        tx_type: TransactionType::Expense,
        use_type_filter: false,
    };

    let filtered_query = SearchQuery {
        text: String::new(),
        min_amount: 500_000,
        max_amount: 5_000_000,
        start_date: 0,
        end_date: 0,
        category_id: 2,
        tx_type: TransactionType::Expense,
        use_type_filter: true,
    };

    c.bench_function("search_text_1000tx", |b| {
        b.iter(|| search_transactions(&transactions, &text_query))
    });

    c.bench_function("search_filtered_1000tx", |b| {
        b.iter(|| search_transactions(&transactions, &filtered_query))
    });
}

fn bench_crypto(c: &mut Criterion) {
    let key: [u8; KEY_LEN] = [
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
        0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20,
    ];

    // Small backup (typical)
    let small_json = r#"{"version":1,"timestamp":1710000000000,"app_version":"1.0","transactions":[],"loans":[],"installments":[],"categories":[]}"#;

    // Large backup (1000 transactions)
    let large_json: String = format!(
        r#"{{"version":1,"timestamp":1710000000000,"app_version":"1.0","transactions":[{}],"loans":[],"installments":[],"categories":[]}}"#,
        (0..1000)
            .map(|i| format!(r#"{{"id":{},"type":"EXPENSE","categoryId":1,"amount":{},"description":"Transaction {}","date":1710000000000}}"#, i, i * 10000, i))
            .collect::<Vec<_>>()
            .join(",")
    );

    c.bench_function("encrypt_backup_small", |b| {
        b.iter(|| encrypt_backup(small_json, &key).unwrap())
    });

    c.bench_function("decrypt_backup_small", |b| {
        let encrypted = encrypt_backup(small_json, &key).unwrap();
        b.iter(|| decrypt_backup(&encrypted, &key).unwrap())
    });

    c.bench_function("encrypt_backup_large", |b| {
        b.iter(|| encrypt_backup(&large_json, &key).unwrap())
    });

    c.bench_function("decrypt_backup_large", |b| {
        let encrypted = encrypt_backup(&large_json, &key).unwrap();
        b.iter(|| decrypt_backup(&encrypted, &key).unwrap())
    });

    c.bench_function("checksum_small", |b| {
        b.iter(|| compute_checksum(small_json.as_bytes()))
    });

    c.bench_function("checksum_large", |b| {
        b.iter(|| compute_checksum(large_json.as_bytes()))
    });

    c.bench_function("encrypted_backup_file_roundtrip", |b| {
        b.iter(|| {
            let file = build_encrypted_backup_file(small_json, &key).unwrap();
            hesabyar_core::crypto::parse_encrypted_backup_file(&file, &key).unwrap()
        })
    });
}

fn bench_validation(c: &mut Criterion) {
    let tx = Transaction {
        id: 1,
        tx_type: TransactionType::Expense,
        category_id: 1,
        amount: 50000,
        description: "test transaction".to_string(),
        person_name: None,
        date: 1710000000000,
        due_date: None,
        installment_id: None,
    };

    c.bench_function("validate_transaction", |b| {
        b.iter(|| validate_transaction(&tx))
    });

    // Batch validation with 1000 transactions
    let transactions: Vec<Transaction> = (0..1000)
        .map(|i| Transaction {
            id: i,
            tx_type: TransactionType::Expense,
            category_id: 1,
            amount: 50000 + i as i64,
            description: format!("transaction {}", i),
            person_name: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
        })
        .collect();

    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions,
        loans: vec![],
        installments: vec![],
        categories: vec![],
    };

    c.bench_function("validate_backup_payload_1000_tx", |b| {
        b.iter(|| validate_backup_payload(&payload))
    });
}

fn bench_ai_validation(c: &mut Criterion) {
    let valid_json = r#"{
        "type": "EXPENSE",
        "amount": 500000,
        "category": "Food",
        "personName": "علی",
        "description": "پرداخت قبض برق",
        "daysFromNow": 0,
        "dateOffsetDays": 0,
        "hour": 14,
        "minute": 30,
        "confidence": 0.9,
        "notes": null
    }"#;

    let minimal_json = r#"{"type": "INCOME", "amount": 20000000, "category": "Income"}"#;

    c.bench_function("parse_ai_json_full", |b| {
        b.iter(|| parse_ai_transaction_json(valid_json))
    });

    c.bench_function("parse_ai_json_minimal", |b| {
        b.iter(|| parse_ai_transaction_json(minimal_json))
    });

    let short_advice = "شما در ماه گذشته ۲۰٪ از درآمد خود را پس‌انداز کرده‌اید. این عملکرد عالی است.";
    let long_advice = "س".repeat(5000);

    c.bench_function("validate_ai_advice_short", |b| {
        b.iter(|| validate_ai_advice(short_advice))
    });

    c.bench_function("validate_ai_advice_long", |b| {
        b.iter(|| validate_ai_advice(&long_advice))
    });
}

criterion_group!(
    benches,
    bench_parse_amount,
    bench_money_detector,
    bench_jalali_calendar,
    bench_budget_advice,
    bench_search,
    bench_crypto,
    bench_validation,
    bench_ai_validation
);
criterion_main!(benches);
