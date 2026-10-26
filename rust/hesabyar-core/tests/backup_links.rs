//! Backup loan-link fields: Transaction.loanId and
//! PaymentHistory.linkedTransactionId round-trip (issue #419).
//! Integration tests live here (not in models/mod.rs) so the new coverage
//! does not grow the baselined module size.

// Test-only crate: unwrap/expect are loud by design (mirrors lib.rs).
#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use hesabyar_core::models::{BackupPayload, PaymentHistory, Transaction, TransactionType};

#[test]
fn test_backup_payload_valid_round_trip() {
    let original = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id: 10,
            amount: 50000,
            description: "Test".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: None,
            loan_id: Some(7),
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![PaymentHistory {
            id: 1,
            loan_id: 10,
            amount: 200000,
            date: 1710000000000,
            notes: Some("Paid".to_string()),
            linked_transaction_id: Some(3),
        }],
        categories: vec![],
        accounts: vec![],
        ..Default::default()
    };
    let json = serde_json::to_string(&original).unwrap();
    let restored: BackupPayload = serde_json::from_str(&json).unwrap();
    assert_eq!(restored.version, original.version);
    assert_eq!(restored.transactions.len(), 1);
    assert_eq!(restored.transactions[0].amount, 50000);
    assert_eq!(restored.transactions[0].account_id, 1);
    assert_eq!(restored.transactions[0].loan_id, Some(7));
    assert_eq!(restored.payment_histories.len(), 1);
    assert_eq!(restored.payment_histories[0].amount, 200000);
    assert_eq!(restored.payment_histories[0].linked_transaction_id, Some(3));
    // The serialized form must carry both links under their camelCase keys.
    assert!(json.contains("\"loanId\":7"));
    assert!(json.contains("\"linkedTransactionId\":3"));
}

#[test]
fn test_backup_payload_legacy_json_missing_links_parses_to_none() {
    // Backups written before the link fields existed omit both keys.
    // They must still restore, with both links as None.
    let json = r#"{
        "version": 1,
        "timestamp": 1710000000000,
        "appVersion": "1.0.0",
        "transactions": [{
            "id": 1, "type": "EXPENSE", "categoryId": 10, "amount": 50000,
            "description": "Test", "date": 1710000000000, "accountId": 1
        }],
        "loans": [],
        "installments": [],
        "bankLoans": [],
        "paymentHistories": [{"id": 1, "loanId": 10, "amount": 200000, "date": 1710000000000, "notes": "Paid"}],
        "categories": [],
        "accounts": [],
        "persons": []
    }"#;
    let restored: BackupPayload = serde_json::from_str(json).unwrap();
    assert_eq!(restored.transactions.len(), 1);
    assert_eq!(restored.transactions[0].loan_id, None);
    assert_eq!(restored.payment_histories.len(), 1);
    assert_eq!(restored.payment_histories[0].linked_transaction_id, None);
}
