use super::*;

fn make_tx(amount: i64, desc: &str, category_id: i64) -> Transaction {
    Transaction {
        id: 1,
        tx_type: TransactionType::Expense,
        category_id,
        amount,
        description: desc.to_string(),
        person_name: None,
        person_id: None,
        date: 1710000000000,
        due_date: None,
        installment_id: None,
        account_id: 1,
        destination_account_id: None,
    }
}

fn make_loan(amount: i64, remaining: i64, loan_type: &str) -> Loan {
    Loan {
        id: 1,
        person_name: "Ali".to_string(),
        person_id: None,
        loan_type: loan_type.to_string(),
        original_amount: amount,
        remaining_amount: remaining,
        description: "test".to_string(),
        date: 1710000000000,
        is_settled: false,
        tracked: false,
        account_id: None,
    }
}

fn make_inst(amount: i64, title: &str) -> Installment {
    Installment {
        id: 1,
        title: title.to_string(),
        amount,
        due_date: 1710000000000,
        is_paid: false,
        reminder_enabled: true,
        notes: String::default(),
        bank_loan_id: None,
        tracked: false,
        account_id: None,
    }
}

fn make_payment_history(amount: i64, loan_id: i64) -> PaymentHistory {
    PaymentHistory {
        id: 1,
        loan_id,
        amount,
        date: 1710000000000,
        notes: None,
    }
}

fn make_parsed(amount: i64, category: &str) -> ParsedResult {
    ParsedResult {
        tx_type: TransactionType::Expense,
        amount,
        category: category.to_string(),
        person_name: None,
        description: "test".to_string(),
        days_from_now: None,
        title: None,
        date_offset_days: None,
        hour: None,
        minute: None,
        confidence: 0.9,
        notes: None,
    }
}

// =====================================================================
// Transaction validation
// =====================================================================

#[test]
fn test_valid_transaction() {
    assert!(validate_transaction(&make_tx(50000, "coffee", 1)).is_ok());
}

#[test]
fn test_transaction_zero_amount_rejected() {
    let err = validate_transaction(&make_tx(0, "coffee", 1)).unwrap_err();
    assert!(err.contains("amount"));
}

#[test]
fn test_transaction_negative_amount_rejected() {
    let err = validate_transaction(&make_tx(-500, "coffee", 1)).unwrap_err();
    assert!(err.contains("amount"));
}

#[test]
fn test_transaction_empty_description_allowed() {
    // Older backups allowed blank descriptions; tolerate on restore.
    assert!(validate_transaction(&make_tx(50000, "", 1)).is_ok());
}

#[test]
fn test_transaction_zero_date_rejected() {
    let mut tx = make_tx(50000, "coffee", 1);
    tx.date = 0;
    let err = validate_transaction(&tx).unwrap_err();
    assert!(err.contains("date"));
}

#[test]
fn test_transaction_zero_category_allowed() {
    // Older backups defaulted missing category ids to 1; 0 is tolerated.
    assert!(validate_transaction(&make_tx(50000, "coffee", 0)).is_ok());
}

#[test]
fn test_transaction_negative_category_rejected() {
    let err = validate_transaction(&make_tx(50000, "coffee", -1)).unwrap_err();
    assert!(err.contains("category"));
}

#[test]
fn test_transfer_missing_destination_rejected() {
    let tx = Transaction {
        tx_type: TransactionType::Transfer,
        ..make_tx(50000, "transfer", 1)
    };
    // destination_account_id defaults to None via make_tx
    let err = validate_transaction(&tx).unwrap_err();
    assert!(err.contains("destination_account_id"));
}

#[test]
fn test_transfer_same_source_and_destination_rejected() {
    let tx = Transaction {
        tx_type: TransactionType::Transfer,
        account_id: 1,
        destination_account_id: Some(1),
        ..make_tx(50000, "transfer", 1)
    };
    let err = validate_transaction(&tx).unwrap_err();
    assert!(err.contains("differ"));
}

#[test]
fn test_transfer_valid_different_accounts() {
    let tx = Transaction {
        tx_type: TransactionType::Transfer,
        account_id: 1,
        destination_account_id: Some(2),
        ..make_tx(50000, "transfer", 1)
    };
    assert!(validate_transaction(&tx).is_ok());
}

#[test]
fn test_transaction_all_types_valid() {
    for tx_type in [
        TransactionType::Expense,
        TransactionType::Income,
        TransactionType::LoanDebtor,
        TransactionType::LoanCreditor,
        TransactionType::Installment,
    ] {
        let mut tx = make_tx(50000, "test", 1);
        tx.tx_type = tx_type;
        assert!(validate_transaction(&tx).is_ok());
    }
    // Transfer requires a different destination account
    let mut tx = make_tx(50000, "test", 1);
    tx.tx_type = TransactionType::Transfer;
    tx.destination_account_id = Some(2);
    assert!(validate_transaction(&tx).is_ok());
}

// =====================================================================
// Loan validation
// =====================================================================

#[test]
fn test_valid_loan_debtor() {
    assert!(validate_loan(&make_loan(5000000, 3000000, "DEBTOR")).is_ok());
}

#[test]
fn test_valid_loan_creditor() {
    assert!(validate_loan(&make_loan(5000000, 5000000, "CREDITOR")).is_ok());
}

#[test]
fn test_valid_loan_settled() {
    assert!(validate_loan(&make_loan(5000000, 0, "DEBTOR")).is_ok());
}

#[test]
fn test_loan_empty_person_rejected() {
    let mut loan = make_loan(5000000, 3000000, "DEBTOR");
    loan.person_name = String::default();
    let err = validate_loan(&loan).unwrap_err();
    assert!(err.contains("person_name"));
}

#[test]
fn test_loan_zero_amount_rejected() {
    let err = validate_loan(&make_loan(0, 0, "DEBTOR")).unwrap_err();
    assert!(err.contains("original_amount"));
}

#[test]
fn test_loan_negative_amount_rejected() {
    let err = validate_loan(&make_loan(-1000, 0, "DEBTOR")).unwrap_err();
    assert!(err.contains("original_amount"));
}

#[test]
fn test_loan_negative_remaining_rejected() {
    let err = validate_loan(&make_loan(5000000, -1, "DEBTOR")).unwrap_err();
    assert!(err.contains("remaining_amount"));
}

#[test]
fn test_loan_remaining_exceeds_original_allowed() {
    // Older backups could have remaining > original after manual edits.
    assert!(validate_loan(&make_loan(5000000, 6000000, "DEBTOR")).is_ok());
}

#[test]
fn test_loan_invalid_type_rejected() {
    let err = validate_loan(&make_loan(5000000, 3000000, "INVALID")).unwrap_err();
    assert!(err.contains("DEBTOR"));
}

// =====================================================================
// Installment validation
// =====================================================================

#[test]
fn test_valid_installment() {
    assert!(validate_installment(&make_inst(2000000, "Car loan")).is_ok());
}

#[test]
fn test_installment_zero_amount_rejected() {
    let err = validate_installment(&make_inst(0, "Car loan")).unwrap_err();
    assert!(err.contains("amount"));
}

#[test]
fn test_installment_negative_amount_rejected() {
    let err = validate_installment(&make_inst(-500, "Car loan")).unwrap_err();
    assert!(err.contains("amount"));
}

#[test]
fn test_installment_empty_title_rejected() {
    let err = validate_installment(&make_inst(2000000, "")).unwrap_err();
    assert!(err.contains("title"));
}

#[test]
fn test_installment_zero_due_date_rejected() {
    let mut inst = make_inst(2000000, "Car loan");
    inst.due_date = 0;
    let err = validate_installment(&inst).unwrap_err();
    assert!(err.contains("due_date"));
}

// =====================================================================
// ParsedResult validation
// =====================================================================

#[test]
fn test_valid_parsed_result() {
    assert!(validate_parsed_result(&make_parsed(50000, "Food")).is_ok());
}

#[test]
fn test_parsed_result_zero_amount_rejected() {
    let err = validate_parsed_result(&make_parsed(0, "Food")).unwrap_err();
    assert!(err.contains("amount"));
}

#[test]
fn test_parsed_result_negative_amount_rejected() {
    let err = validate_parsed_result(&make_parsed(-100, "Food")).unwrap_err();
    assert!(err.contains("amount"));
}

#[test]
fn test_parsed_result_empty_category_rejected() {
    let err = validate_parsed_result(&make_parsed(50000, "")).unwrap_err();
    assert!(err.contains("category"));
}

#[test]
fn test_parsed_result_hour_out_of_range() {
    let mut pr = make_parsed(50000, "Food");
    pr.hour = Some(25);
    let err = validate_parsed_result(&pr).unwrap_err();
    assert!(err.contains("hour"));
}

#[test]
fn test_parsed_result_hour_negative() {
    let mut pr = make_parsed(50000, "Food");
    pr.hour = Some(-1);
    let err = validate_parsed_result(&pr).unwrap_err();
    assert!(err.contains("hour"));
}

#[test]
fn test_parsed_result_minute_out_of_range() {
    let mut pr = make_parsed(50000, "Food");
    pr.minute = Some(60);
    let err = validate_parsed_result(&pr).unwrap_err();
    assert!(err.contains("minute"));
}

#[test]
fn test_parsed_result_minute_negative() {
    let mut pr = make_parsed(50000, "Food");
    pr.minute = Some(-1);
    let err = validate_parsed_result(&pr).unwrap_err();
    assert!(err.contains("minute"));
}

#[test]
fn test_parsed_result_valid_hour_minute() {
    let mut pr = make_parsed(50000, "Food");
    pr.hour = Some(14);
    pr.minute = Some(30);
    assert!(validate_parsed_result(&pr).is_ok());
}

#[test]
fn test_parsed_result_boundary_hour_zero() {
    let mut pr = make_parsed(50000, "Food");
    pr.hour = Some(0);
    assert!(validate_parsed_result(&pr).is_ok());
}

#[test]
fn test_parsed_result_boundary_hour_23() {
    let mut pr = make_parsed(50000, "Food");
    pr.hour = Some(23);
    assert!(validate_parsed_result(&pr).is_ok());
}

#[test]
fn test_parsed_result_boundary_minute_59() {
    let mut pr = make_parsed(50000, "Food");
    pr.minute = Some(59);
    assert!(validate_parsed_result(&pr).is_ok());
}

// =====================================================================
// Batch validation
// =====================================================================

#[test]
fn test_batch_valid_transactions() {
    let txs = vec![
        make_tx(50000, "coffee", 1),
        make_tx(100000, "lunch", 2),
        make_tx(200000, "dinner", 3),
    ];
    let result = validate_transaction_batch(&txs);
    assert!(result.is_valid);
    assert!(result.errors.is_empty());
}

#[test]
fn test_batch_collects_all_errors() {
    let txs = vec![
        make_tx(0, "bad1", 1),   // zero amount
        make_tx(50000, "", 1),   // empty desc (tolerated)
        make_tx(-1, "bad3", 1),  // negative amount
        make_tx(50000, "ok", 1), // valid
    ];
    let result = validate_transaction_batch(&txs);
    assert!(!result.is_valid);
    assert_eq!(result.errors.len(), 2);
}

#[test]
fn test_batch_empty_is_valid() {
    let result = validate_transaction_batch(&[]);
    assert!(result.is_valid);
    assert!(result.errors.is_empty());
}

#[test]
fn test_batch_single_error() {
    let txs = vec![make_tx(0, "bad", 1)];
    let result = validate_transaction_batch(&txs);
    assert!(!result.is_valid);
    assert_eq!(result.errors.len(), 1);
    assert!(result.errors[0].contains("Transaction[0]"));
}

// =====================================================================
// Backup payload validation
// =====================================================================

#[test]
fn test_backup_valid() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![make_tx(50000, "coffee", 1)],
        loans: vec![make_loan(5000000, 3000000, "DEBTOR")],
        installments: vec![make_inst(2000000, "Car loan")],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(result.is_valid);
}

#[test]
fn test_backup_invalid_version() {
    let payload = BackupPayload {
        version: 0,
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result.errors.iter().any(|e| e.contains("version")));
}

#[test]
fn test_backup_collects_all_entity_errors() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![make_tx(0, "bad", 1)],
        loans: vec![make_loan(0, 0, "INVALID")],
        installments: vec![make_inst(0, "")],
        bank_loans: vec![],
        payment_histories: vec![make_payment_history(0, 1)],
        categories: vec![],
        accounts: vec![],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    // At least one error from each entity type
    assert!(result.errors.len() >= 4);
}

#[test]
fn test_backup_empty_is_valid() {
    let payload = BackupPayload::default();
    let result = validate_backup_payload(&payload);
    assert!(result.is_valid);
}

#[test]
fn test_backup_mixed_valid_and_invalid() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![make_tx(50000, "good", 1), make_tx(0, "bad", 1)],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert_eq!(result.errors.len(), 1);
}

#[test]
fn test_backup_legacy_category_id_zero_not_rejected() {
    // category_id == 0 is a legacy default tolerated by validate_transaction;
    // backup validation must not treat it as a missing category reference.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![make_tx(50000, "groceries", 0)],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![Category {
            id: 1,
            name: "Food".into(),
            key: "food".into(),
            icon: "".into(),
            color: 0,
            category_type: "EXPENSE".into(),
            is_default: false,
        }],
        accounts: vec![],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(
        result.is_valid,
        "Legacy category_id=0 should be tolerated, got: {:?}",
        result.errors
    );
}

fn make_bank_loan(monthly: i64, count: i32, received: i64) -> BankLoan {
    let repayable = monthly * count as i64;
    BankLoan {
        id: 1,
        bank_name: "Bank".into(),
        loan_name: "Loan".into(),
        received_amount: received,
        monthly_installment_amount: monthly,
        number_of_installments: count,
        total_repayable_amount: repayable,
        total_interest: repayable - received,
        start_date: 1710000000000,
        description: "test".into(),
        is_settled: false,
        tracked: false,
        account_id: None,
    }
}

#[test]
fn test_valid_bank_loan() {
    assert!(validate_bank_loan(&make_bank_loan(1_000_000, 12, 10_000_000)).is_ok());
}

#[test]
fn test_bank_loan_repayable_mismatch_rejected() {
    let mut bl = make_bank_loan(1_000_000, 12, 10_000_000);
    bl.total_repayable_amount += 1;
    assert!(validate_bank_loan(&bl).is_err());
}

#[test]
fn test_bank_loan_interest_mismatch_rejected() {
    let mut bl = make_bank_loan(1_000_000, 12, 10_000_000);
    bl.total_interest += 1;
    assert!(validate_bank_loan(&bl).is_err());
}

#[test]
fn test_bank_loan_repayable_overflow_rejected() {
    let mut bl = make_bank_loan(1, 1, 1);
    bl.monthly_installment_amount = i64::MAX;
    bl.number_of_installments = 2;
    assert!(validate_bank_loan(&bl).is_err());
}

#[test]
fn test_bank_loan_received_exceeds_repayable_rejected() {
    // received > repayable -> negative interest
    let mut bl = make_bank_loan(1_000_000, 12, 12_000_000);
    bl.received_amount = 20_000_000;
    bl.total_interest = bl.total_repayable_amount - bl.received_amount;
    assert!(validate_bank_loan(&bl).is_err());
}

// ====================================================================
// PaymentHistory validation
// ====================================================================

#[test]
fn test_valid_payment_history() {
    assert!(validate_payment_history(&make_payment_history(50000, 1)).is_ok());
}

#[test]
fn test_payment_history_zero_amount_rejected() {
    let err = validate_payment_history(&make_payment_history(0, 1)).unwrap_err();
    assert!(err.contains("amount"));
}

#[test]
fn test_payment_history_negative_amount_rejected() {
    let err = validate_payment_history(&make_payment_history(-500, 1)).unwrap_err();
    assert!(err.contains("amount"));
}

#[test]
fn test_payment_history_zero_date_rejected() {
    let mut ph = make_payment_history(50000, 1);
    ph.date = 0;
    let err = validate_payment_history(&ph).unwrap_err();
    assert!(err.contains("date"));
}

#[test]
fn test_payment_history_zero_loan_id_rejected() {
    let mut ph = make_payment_history(50000, 1);
    ph.loan_id = 0;
    let err = validate_payment_history(&ph).unwrap_err();
    assert!(err.contains("loan_id"));
}

#[test]
fn test_payment_history_batch_collects_errors() {
    let histories = vec![
        make_payment_history(50000, 1),
        make_payment_history(0, 1),
        make_payment_history(50000, 0),
    ];
    let result = validate_payment_history_batch(&histories);
    assert!(!result.is_valid);
    assert_eq!(result.errors.len(), 2);
}

#[test]
fn test_backup_payment_history_loan_cross_reference() {
    // paymentHistory referencing a loan that does not exist in the backup
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![],
        loans: vec![make_loan(100000, 40000, "DEBTOR")],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![
            make_payment_history(50000, 99), // loan 99 does not exist
        ],
        categories: vec![],
        accounts: vec![],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result.errors.iter().any(|e| e.contains("non-existent loan")));
}

#[test]
fn test_backup_payment_history_loan_cross_reference_rejects_orphan_when_no_loans() {
    // Even with no loans in the backup, a positive loan_id in payment_histories
    // is always an orphan and must be rejected.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![make_payment_history(50000, 99)],
        categories: vec![],
        accounts: vec![],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result.errors.iter().any(|e| e.contains("non-existent loan")));
}

// =====================================================================
// Account validation
// =====================================================================

#[test]
fn test_backup_rejects_empty_account_name() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![Account {
            id: 1,
            name: String::default(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0xFF4CAF50,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 1710000000000,
            updated_at: 1710000000000,
            stable_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result.errors.iter().any(|e| e.contains("empty name")));
}

#[test]
fn test_backup_rejects_duplicate_account_ids() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![
            Account {
                id: 1,
                name: "Main".to_string(),
                account_type: "BANK".to_string(),
                bank_name: None,
                card_number: None,
                account_number: None,
                iban: None,
                initial_balance: 0,
                color: 0xFF4CAF50,
                icon: None,
                is_archived: false,
                display_order: 0,
                created_at: 1710000000000,
                updated_at: 1710000000000,
                stable_id: None,
            },
            Account {
                id: 1,
                name: "Duplicate".to_string(),
                account_type: "CASH_WALLET".to_string(),
                bank_name: None,
                card_number: None,
                account_number: None,
                iban: None,
                initial_balance: 0,
                color: 0xFF4CAF50,
                icon: None,
                is_archived: false,
                display_order: 0,
                created_at: 1710000000000,
                updated_at: 1710000000000,
                stable_id: None,
            },
        ],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result.errors.iter().any(|e| e.contains("Duplicate account id")));
}

#[test]
fn test_backup_rejects_invalid_account_type() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![Account {
            id: 1,
            name: "Main".to_string(),
            account_type: "INVALID_TYPE".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0xFF4CAF50,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 1710000000000,
            updated_at: 1710000000000,
            stable_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result.errors.iter().any(|e| e.contains("invalid type")));
}

#[test]
fn test_backup_accepts_other_account_type() {
    // Kotlin AccountType has an OTHER variant; backups carrying it must
    // validate rather than being rejected as an unknown type.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![Account {
            id: 1,
            name: "سایر".to_string(),
            account_type: "OTHER".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0xFF4CAF50,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 1710000000000,
            updated_at: 1710000000000,
            stable_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(
        result.is_valid,
        "OTHER account type should be accepted, got: {:?}",
        result.errors
    );
}

#[test]
fn test_backup_accepts_account_only_backup() {
    // A backup holding accounts but no transactions/loans/etc. is NOT "empty":
    // the FFI empty-guard must let it through so the accounts reach
    // validate_accounts_and_references (Kotlin's fallback has no empty-guard,
    // so this also keeps the two validators in parity).
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![Account {
            id: 1,
            name: "حساب اصلی".to_string(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0xFF4CAF50,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 1710000000000,
            updated_at: 1710000000000,
            stable_id: None,
        }],
        ..Default::default()
    };
    crate::validate_backup(&payload).unwrap_or_else(|e| panic!("Account-only backup should be accepted, got: {}", e));
}

#[test]
fn test_backup_rejects_transfer_without_destination_via_fail_fast_ffi() {
    // The fail-fast FFI path (validate_backup) only runs
    // validate_accounts_and_references; a Transfer without a destination
    // must be rejected there too, consistently with validate_backup_payload.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Transfer,
            category_id: 1,
            amount: 50000,
            description: "transfer".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: DEFAULT_ACCOUNT_ID,
            destination_account_id: None,
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![Category {
            id: 1,
            name: "Food".into(),
            key: "food".into(),
            icon: "".into(),
            color: 0,
            category_type: "EXPENSE".into(),
            is_default: false,
        }],
        accounts: vec![],
        ..Default::default()
    };
    let err = crate::validate_backup(&payload).unwrap_err().to_string();
    assert!(err.contains("destination_account_id"), "got: {}", err);
}

#[test]
fn test_backup_rejects_transfer_with_same_source_and_destination_via_fail_fast_ffi() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Transfer,
            category_id: 1,
            amount: 50000,
            description: "transfer".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: DEFAULT_ACCOUNT_ID,
            destination_account_id: Some(DEFAULT_ACCOUNT_ID),
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![Category {
            id: 1,
            name: "Food".into(),
            key: "food".into(),
            icon: "".into(),
            color: 0,
            category_type: "EXPENSE".into(),
            is_default: false,
        }],
        accounts: vec![],
        ..Default::default()
    };
    let err = crate::validate_backup(&payload).unwrap_err().to_string();
    assert!(err.contains("must differ"), "got: {}", err);
}

#[test]
fn test_backup_transfer_missing_destination_not_duplicated() {
    // validate_backup_payload calls BOTH validate_accounts_and_references
    // (which checks Transfer structure) AND validate_transaction_batch
    // (which previously also checked Transfer invariants). After the fix,
    // validate_transaction_batch skips Transfer checks, so a malformed
    // Transfer yields exactly ONE error — not two.
    let tx = Transaction {
        id: 1,
        tx_type: TransactionType::Transfer,
        category_id: 1,
        amount: 50_000,
        description: "transfer".to_string(),
        person_name: None,
        person_id: None,
        date: 1710000000000,
        due_date: None,
        installment_id: None,
        account_id: 1,
        destination_account_id: None,
    };
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "0.2.0".to_string(),
        transactions: vec![tx],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![Account {
            id: 1,
            name: "Main".to_string(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 0,
            updated_at: 0,
            stable_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    let transfer_errors: Vec<&String> = result
        .errors
        .iter()
        .filter(|e| e.contains("destination_account_id"))
        .collect();
    assert_eq!(
        transfer_errors.len(),
        1,
        "expected exactly one destination_account_id error, got: {:?}",
        result.errors
    );
}

#[test]
fn test_backup_transfer_same_source_dest_not_duplicated() {
    // Same as above but for source == destination. Also must produce
    // exactly ONE error (no duplicates from validate_transaction_batch).
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "0.2.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Transfer,
            category_id: 1,
            amount: 50_000,
            description: "transfer".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: Some(1),
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![Account {
            id: 1,
            name: "Main".to_string(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 0,
            updated_at: 0,
            stable_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    let transfer_errors: Vec<&String> = result.errors.iter().filter(|e| e.contains("must differ")).collect();
    assert_eq!(
        transfer_errors.len(),
        1,
        "expected exactly one must-differ error, got: {:?}",
        result.errors
    );
}

// =====================================================================
// Transaction account reference validation
// =====================================================================

#[test]
fn test_backup_rejects_tx_with_nonexistent_source_account() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id: 1,
            amount: 50000,
            description: "coffee".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 99, // no such account
            destination_account_id: None,
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![Account {
            id: 1,
            name: "Main".to_string(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0xFF4CAF50,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 1710000000000,
            updated_at: 1710000000000,
            stable_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result.errors.iter().any(|e| e.contains("non-existent source account")));
}

#[test]
fn test_backup_rejects_tx_with_nonexistent_destination_account() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Transfer,
            category_id: 1,
            amount: 50000,
            description: "transfer".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: Some(99), // no such account
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![Account {
            id: 1,
            name: "Main".to_string(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0xFF4CAF50,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 1710000000000,
            updated_at: 1710000000000,
            stable_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result
        .errors
        .iter()
        .any(|e| e.contains("non-existent destination account")));
}

#[test]
fn test_backup_valid_with_all_accounts_referenced() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Transfer,
            category_id: 1,
            amount: 50000,
            description: "transfer".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: Some(2),
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![
            Account {
                id: 1,
                name: "Main".to_string(),
                account_type: "BANK".to_string(),
                bank_name: None,
                card_number: None,
                account_number: None,
                iban: None,
                initial_balance: 0,
                color: 0xFF4CAF50,
                icon: None,
                is_archived: false,
                display_order: 0,
                created_at: 1710000000000,
                updated_at: 1710000000000,
                stable_id: None,
            },
            Account {
                id: 2,
                name: "Savings".to_string(),
                account_type: "SAVINGS_INVESTMENT".to_string(),
                bank_name: None,
                card_number: None,
                account_number: None,
                iban: None,
                initial_balance: 0,
                color: 0xFF4CAF50,
                icon: None,
                is_archived: false,
                display_order: 0,
                created_at: 1710000000000,
                updated_at: 1710000000000,
                stable_id: None,
            },
        ],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(
        result.is_valid,
        "Expected valid payload, got errors: {:?}",
        result.errors
    );
}

// =====================================================================
// Legacy backup: accounts list empty, transactions must use legacy ID=1
// =====================================================================

#[test]
fn test_backup_rejects_non_legacy_account_id_when_accounts_empty() {
    // Corrupted old backup: accounts=[], tx references account_id=999.
    // Must fail fast to prevent orphaned balances.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id: 1,
            amount: 50000,
            description: "coffee".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 999, // non-legacy, no accounts list to reference
            destination_account_id: None,
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![], // old backup format — no accounts,
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result.errors.iter().any(|e| e.contains("non-legacy account")));
}

#[test]
fn test_backup_accepts_legacy_account_id_when_accounts_empty() {
    // Genuine old backup: accounts=[], tx references account_id=1.
    // This is the single legacy account that existed before multi-account.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id: 1,
            amount: 50000,
            description: "coffee".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1, // legacy default
            destination_account_id: None,
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![], // old backup format,
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(
        result.is_valid,
        "Expected valid legacy payload, got errors: {:?}",
        result.errors
    );
}

#[test]
fn test_backup_rejects_non_legacy_dest_account_when_accounts_empty() {
    // Old backup with transfer to a non-legacy destination account.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Transfer,
            category_id: 1,
            amount: 50000,
            description: "transfer".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: Some(99), // non-legacy
        }],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![], // old backup format,
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(result
        .errors
        .iter()
        .any(|e| e.contains("non-legacy destination account")));
}

#[test]
fn test_backup_rejects_non_existent_source_account_in_modern_backup() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        accounts: vec![Account {
            id: 1,
            name: "Main".to_string(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 0,
            updated_at: 0,
            stable_id: None,
        }],
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id: 1,
            amount: 1000,
            description: "expense".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 999, // non-existent
            destination_account_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(
        result
            .errors
            .iter()
            .any(|e| e.contains("non-existent source account 999")),
        "expected non-existent source account error: {:?}",
        result.errors
    );
}

#[test]
fn test_backup_rejects_non_existent_destination_account_in_modern_backup() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        accounts: vec![Account {
            id: 1,
            name: "Main".to_string(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 0,
            updated_at: 0,
            stable_id: None,
        }],
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Transfer,
            category_id: 1,
            amount: 1000,
            description: "transfer".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: Some(888), // non-existent
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(
        result
            .errors
            .iter()
            .any(|e| e.contains("non-existent destination account 888")),
        "expected non-existent destination account error: {:?}",
        result.errors
    );
}

#[test]
fn test_backup_orders_account_ref_errors_before_transfer_structure_errors() {
    // In the same pass, account reference errors must be produced before
    // transfer structure errors so fail-fast FFI (errors.first()) prioritizes
    // account identity over transfer layout.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        accounts: vec![Account {
            id: 1,
            name: "Main".to_string(),
            account_type: "BANK".to_string(),
            bank_name: None,
            card_number: None,
            account_number: None,
            iban: None,
            initial_balance: 0,
            color: 0,
            icon: None,
            is_archived: false,
            display_order: 0,
            created_at: 0,
            updated_at: 0,
            stable_id: None,
        }],
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Transfer,
            category_id: 1,
            amount: 1000,
            description: "transfer".to_string(),
            person_name: None,
            person_id: None,
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 777,              // non-existent
            destination_account_id: None, // missing dest
        }],
        ..Default::default()
    };
    let errors = validate_accounts_and_references(&payload);
    assert_eq!(errors.len(), 2);
    assert!(errors[0].contains("non-existent source account 777"));
    assert!(errors[1].contains("is a Transfer but has no destination_account_id"));
}

#[test]
fn test_validate_backup_rejects_blank_and_duplicate_persons() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1,
        app_version: "1.0".into(),
        transactions: vec![],
        loans: vec![],
        installments: vec![],
        bank_loans: vec![],
        payment_histories: vec![],
        categories: vec![],
        accounts: vec![],
        persons: vec![
            Person {
                id: 1,
                name: "".into(),
                normalized_name: "ali".into(),
                phone: None,
                notes: None,
                created_at: 0,
                is_archived: false,
            },
            Person {
                id: 2,
                name: "Ali".into(),
                normalized_name: "".into(),
                phone: None,
                notes: None,
                created_at: 0,
                is_archived: false,
            },
            Person {
                id: 3,
                name: "Sara".into(),
                normalized_name: "sara".into(),
                phone: None,
                notes: None,
                created_at: 0,
                is_archived: false,
            },
            Person {
                id: 4,
                // Differs from "Sara" only by case: both names normalize to
                // "sara", so the duplicate must be caught from the derived key.
                name: "SARA".into(),
                normalized_name: "sara2".into(),
                phone: None,
                notes: None,
                created_at: 0,
                is_archived: false,
            },
        ],
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    let joined = result.errors.join(" | ");
    assert!(joined.contains("blank name"), "expected blank name error: {joined}");
    assert!(
        joined.contains("blank normalizedName"),
        "expected blank normalizedName error: {joined}"
    );
    assert!(
        joined.contains("duplicate normalizedName"),
        "expected duplicate normalizedName error: {joined}"
    );
}

#[test]
fn test_validate_backup_derives_person_key_from_name_not_supplied_field() {
    // A tampered normalizedName must not decide identity. Both entries carry
    // the same supplied key, but their names are distinct people, so the
    // payload is valid. The restore path re-derives the key from the name.
    let payload = BackupPayload {
        persons: vec![
            Person {
                id: 1,
                name: "Ali".into(),
                normalized_name: "reza".into(),
                ..person_default()
            },
            Person {
                id: 2,
                name: "Reza".into(),
                normalized_name: "reza".into(),
                ..person_default()
            },
        ],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(
        result.is_valid,
        "names are distinct so the payload must pass: {:?}",
        result.errors
    );
}

#[test]
fn test_validate_backup_rejects_duplicate_person_ids() {
    let payload = BackupPayload {
        persons: vec![
            Person {
                id: 7,
                name: "Ali".into(),
                normalized_name: "ali".into(),
                ..person_default()
            },
            Person {
                id: 7,
                name: "Sara".into(),
                normalized_name: "sara".into(),
                ..person_default()
            },
        ],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(
        result.errors.iter().any(|e| e.contains("duplicate id 7")),
        "expected duplicate id error: {:?}",
        result.errors
    );
}

#[test]
fn test_normalize_person_name_matches_kotlin_contract() {
    // Parity with PersonNameNormalizer.kt: trim, collapse whitespace, strip
    // zero-width, fold Arabic variants to Persian, lowercase.
    assert_eq!(normalize_person_name("  Ali  "), "ali");
    assert_eq!(normalize_person_name("Ali   Reza"), "ali reza");
    assert_eq!(normalize_person_name("علی\u{200B}رضا"), "علیرضا");
    assert_eq!(normalize_person_name("يک"), "یک");
    assert_eq!(normalize_person_name("ة"), "ه");
    assert_eq!(normalize_person_name("\u{200C}\u{200B}"), "");
    // Full Arabic-fold parity (the Kotlin map folds every one of these):
    // remaining yeh variants (alef maksura, yeh with hamza).
    assert_eq!(normalize_person_name("ى"), "ی");
    assert_eq!(normalize_person_name("ئ"), "ی");
    // Every alef variant folds to the plain alef.
    assert_eq!(normalize_person_name("أ"), "ا");
    assert_eq!(normalize_person_name("إ"), "ا");
    assert_eq!(normalize_person_name("آ"), "ا");
    assert_eq!(normalize_person_name("ٱ"), "ا");
    // Waw with hamza folds to plain waw.
    assert_eq!(normalize_person_name("ؤ"), "و");
    // Lam-alef presentation-form ligatures (isolated/final, plain/hamza/madda)
    // expand to the canonical two-char sequence — exactly what the Kotlin
    // map emits, so a Rust-side fold drift fails here.
    assert_eq!(normalize_person_name("ﻵ"), "لا");
    assert_eq!(normalize_person_name("ﻶ"), "لا");
    assert_eq!(normalize_person_name("ﻷ"), "لا");
    assert_eq!(normalize_person_name("ﻸ"), "لا");
    assert_eq!(normalize_person_name("ﻹ"), "لا");
    assert_eq!(normalize_person_name("ﻺ"), "لا");
    assert_eq!(normalize_person_name("ﻻ"), "لا");
    assert_eq!(normalize_person_name("ﻼ"), "لا");
    // Mixed-script round trip: Arabic-typed and Persian-typed spellings of
    // the same name must share one dedup key.
    assert_eq!(normalize_person_name("علي"), normalize_person_name("علی"));

    // NBSP parity: Java `Character.isWhitespace` EXCLUDES NBSP (`U+00A0`),
    // NNBSP (`U+2007`) and NARROW NBSP (`U+202F`). The Kotlin normalizer
    // therefore treats NBSP as a literal character, never as a space to
    // trim or collapse. Rust `char::is_whitespace` would wrongly fold it,
    // so `is_java_whitespace` must keep this divergence.
    // NBSP is preserved verbatim (not collapsed to a normal space).
    assert_eq!(normalize_person_name("محمد\u{00A0}رضا"), "محمد\u{00A0}رضا");
    // A normal ASCII space path collapses to a single space — proving NBSP
    // does NOT take the whitespace branch.
    assert_eq!(normalize_person_name("محمد رضا"), "محمد رضا");
    // NBSP at the edges is NOT trimmed (it is not whitespace).
    assert_eq!(normalize_person_name("\u{00A0}ali"), "\u{00A0}ali");
    assert_eq!(normalize_person_name("ali\u{00A0}"), "ali\u{00A0}");
    // NNBSP and NARROW NBSP are likewise preserved, not folded.
    assert_eq!(normalize_person_name("a\u{2007}b"), "a\u{2007}b");
    assert_eq!(normalize_person_name("a\u{202F}b"), "a\u{202F}b");

    // NEL (`U+0085`) parity: Java `Character.isWhitespace` EXCLUDES NEL
    // (verified on JVM 24 — NEL is category Cc, and Java only flags
    // space-separator/line-separator/paragraph-separator chars plus a
    // fixed list of control codes that does not include NEL). Kotlin's
    // normalizer therefore keeps NEL as a literal character; Rust's
    // `char::is_whitespace` (Unicode White_Space) would wrongly fold it
    // into a separator, so `is_java_whitespace` must exclude it.
    assert_eq!(normalize_person_name("a\u{0085}b"), "a\u{0085}b");
    // A NEL-only name does not trim to empty — it stays non-empty.
    assert!(!normalize_person_name("\u{0085}").is_empty());

    // Case-fold parity: Kotlin `Char.lowercaseChar()` (Java
    // `Character.toLowerCase`) applies the Unicode SIMPLE lowercase. For
    // `İ` (U+0130) that simple mapping is a single `i`, so Kotlin yields
    // "istanbul"; Rust's full `to_lowercase` would expand `İ` to "i\u{307}"
    // and our `count() == 1` guard would wrongly keep it, so we map it
    // explicitly. The result must match Kotlin's `PersonNameNormalizer`.
    assert_eq!(normalize_person_name("İ"), "i");
    assert_eq!(normalize_person_name("İstanbul"), "istanbul");
    // Latin simple fold still works for single-codepoint mappings.
    assert_eq!(normalize_person_name("ALI"), "ali");

    // Single-letter presentation forms (isolated/final/initial/medial,
    // U+FB50-U+FEFF) fold to the same key as the base letter — mirroring
    // the Kotlin map exactly. PDF-pasted text carries these shaped glyphs
    // instead of the base letters.
    assert_eq!(normalize_person_name("\u{FE8D}"), "\u{0627}"); // alef isolated
    assert_eq!(normalize_person_name("\u{FE8E}"), "\u{0627}"); // alef final
    assert_eq!(normalize_person_name("\u{FE8F}"), "\u{0628}"); // beh isolated
    assert_eq!(normalize_person_name("\u{FE95}"), "\u{062A}"); // teh isolated
    assert_eq!(normalize_person_name("\u{FE99}"), "\u{062B}"); // theh isolated
    assert_eq!(normalize_person_name("\u{FE9D}"), "\u{062C}"); // jeem isolated
    assert_eq!(normalize_person_name("\u{FEA1}"), "\u{062D}"); // hah isolated
    assert_eq!(normalize_person_name("\u{FEA5}"), "\u{062E}"); // khah isolated
    assert_eq!(normalize_person_name("\u{FEA9}"), "\u{062F}"); // dal isolated
    assert_eq!(normalize_person_name("\u{FEAB}"), "\u{0630}"); // thal isolated
    assert_eq!(normalize_person_name("\u{FEAD}"), "\u{0631}"); // reh isolated
    assert_eq!(normalize_person_name("\u{FEAF}"), "\u{0632}"); // zain isolated
    assert_eq!(normalize_person_name("\u{FEB1}"), "\u{0633}"); // seen isolated
    assert_eq!(normalize_person_name("\u{FEB5}"), "\u{0634}"); // sheen isolated
    assert_eq!(normalize_person_name("\u{FEB9}"), "\u{0635}"); // sad isolated
    assert_eq!(normalize_person_name("\u{FEBD}"), "\u{0636}"); // dad isolated
    assert_eq!(normalize_person_name("\u{FEC1}"), "\u{0637}"); // tah isolated
    assert_eq!(normalize_person_name("\u{FEC5}"), "\u{0638}"); // zah isolated
    assert_eq!(normalize_person_name("\u{FEC9}"), "\u{0639}"); // ain isolated
    assert_eq!(normalize_person_name("\u{FECD}"), "\u{063A}"); // ghain isolated
    assert_eq!(normalize_person_name("\u{FED1}"), "\u{0641}"); // feh isolated
    assert_eq!(normalize_person_name("\u{FED5}"), "\u{0642}"); // qaf isolated
    assert_eq!(normalize_person_name("\u{FEDD}"), "\u{0644}"); // lam isolated
    assert_eq!(normalize_person_name("\u{FEE1}"), "\u{0645}"); // meem isolated
    assert_eq!(normalize_person_name("\u{FEE5}"), "\u{0646}"); // noon isolated
    assert_eq!(normalize_person_name("\u{FE93}"), "\u{0647}"); // teh marbuta isolated -> heh
    assert_eq!(normalize_person_name("\u{FEE9}"), "\u{0647}"); // heh isolated
    assert_eq!(normalize_person_name("\u{FEED}"), "\u{0648}"); // waw isolated
    assert_eq!(normalize_person_name("\u{FEEF}"), "\u{06CC}"); // alef maksura isolated -> yeh
    assert_eq!(normalize_person_name("\u{FEF1}"), "\u{06CC}"); // yeh isolated -> yeh
    assert_eq!(normalize_person_name("\u{FB50}"), "\u{0627}"); // alef wasla isolated -> alef
                                                               // Variant base letter presentation forms (FE81-FE8C) fold to the
                                                               // same canonical targets as their base variants.
    assert_eq!(normalize_person_name("\u{FE81}"), "\u{0627}"); // alef with madda isolated
    assert_eq!(normalize_person_name("\u{FE83}"), "\u{0627}"); // alef with hamza above isolated
    assert_eq!(normalize_person_name("\u{FE85}"), "\u{0648}"); // waw with hamza isolated
    assert_eq!(normalize_person_name("\u{FE87}"), "\u{0627}"); // alef with hamza below isolated
    assert_eq!(normalize_person_name("\u{FE89}"), "\u{06CC}"); // yeh with hamza isolated
    assert_eq!(normalize_person_name("\u{FE8C}"), "\u{06CC}"); // yeh with hamza medial

    // Kaf presentation forms fold to keheh, matching the base-letter rule.
    assert_eq!(normalize_person_name("\u{FED9}"), "\u{06A9}");
    assert_eq!(normalize_person_name("\u{FEDB}"), "\u{06A9}");
    assert_eq!(normalize_person_name("\u{FB8E}"), "\u{06A9}");
    // Persian-only letters fold back to their bases.
    assert_eq!(normalize_person_name("\u{FB56}"), "\u{067E}"); // peh isolated
    assert_eq!(normalize_person_name("\u{FB7A}"), "\u{0686}"); // tcheh isolated
    assert_eq!(normalize_person_name("\u{FB8A}"), "\u{0698}"); // jeh isolated
    assert_eq!(normalize_person_name("\u{FB92}"), "\u{06AF}"); // gaf isolated
    assert_eq!(normalize_person_name("\u{FBFC}"), "\u{06CC}"); // farsi yeh isolated
    assert_eq!(normalize_person_name("\u{FBE8}"), "\u{06CC}"); // uighur maksura initial
                                                               // The original bug: keyboard kaf vs PDF-pasted presentation-form kaf
                                                               // produced two records for the visually identical name.
    assert_eq!(normalize_person_name("اکبر"), normalize_person_name("ا\u{FED9}بر"));
}

#[test]
fn test_validate_backup_rejects_zero_width_only_person_name() {
    let payload = BackupPayload {
        persons: vec![Person {
            id: 1,
            name: "\u{200C}".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(
        result.errors.iter().any(|e| e.contains("blank normalizedName")),
        "a zero-width-only name must normalize to empty: {:?}",
        result.errors
    );
}

#[test]
fn test_validate_backup_person_reference_accepts_declared_person_on_loan() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        persons: vec![Person {
            id: 1,
            name: "Ali".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        loans: vec![Loan {
            id: 1,
            person_name: "Ali".into(),
            person_id: Some(1),
            loan_type: "DEBTOR".into(),
            original_amount: 5_000_000,
            remaining_amount: 3_000_000,
            description: "test".into(),
            date: 1710000000000,
            is_settled: false,
            tracked: false,
            account_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(
        result.is_valid,
        "declared person_id on loan must be accepted, got: {:?}",
        result.errors
    );
}

#[test]
fn test_validate_backup_person_reference_accepts_declared_person_on_transaction() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        persons: vec![Person {
            id: 1,
            name: "Ali".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id: 1,
            amount: 50_000,
            description: "coffee".into(),
            person_name: None,
            person_id: Some(1),
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(
        result.is_valid,
        "declared person_id on transaction must be accepted, got: {:?}",
        result.errors
    );
}

#[test]
fn test_validate_backup_person_reference_rejects_orphan_loan_person_id() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        persons: vec![Person {
            id: 1,
            name: "Ali".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        loans: vec![Loan {
            id: 1,
            person_name: "Unknown".into(),
            person_id: Some(99), // positive but not declared
            loan_type: "DEBTOR".into(),
            original_amount: 5_000_000,
            remaining_amount: 3_000_000,
            description: "test".into(),
            date: 1710000000000,
            is_settled: false,
            tracked: false,
            account_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(
        result.errors.iter().any(|e| e.contains("non-existent person")),
        "expected orphan person_id error on loan, got: {:?}",
        result.errors
    );
}

#[test]
fn test_validate_backup_person_reference_rejects_orphan_transaction_person_id() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        persons: vec![Person {
            id: 1,
            name: "Ali".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id: 1,
            amount: 50_000,
            description: "coffee".into(),
            person_name: None,
            person_id: Some(99), // positive but not declared
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(!result.is_valid);
    assert!(
        result.errors.iter().any(|e| e.contains("non-existent person")),
        "expected orphan person_id error on transaction, got: {:?}",
        result.errors
    );
}

#[test]
fn test_validate_backup_person_reference_tolerates_null_person_id() {
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        persons: vec![Person {
            id: 1,
            name: "Ali".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        loans: vec![Loan {
            id: 1,
            person_name: "Ali".into(),
            person_id: None, // null is a legacy default, tolerated
            loan_type: "DEBTOR".into(),
            original_amount: 5_000_000,
            remaining_amount: 3_000_000,
            description: "test".into(),
            date: 1710000000000,
            is_settled: false,
            tracked: false,
            account_id: None,
        }],
        transactions: vec![Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id: 1,
            amount: 50_000,
            description: "coffee".into(),
            person_name: None,
            person_id: None, // null is a legacy default, tolerated
            date: 1710000000000,
            due_date: None,
            installment_id: None,
            account_id: 1,
            destination_account_id: None,
        }],
        ..Default::default()
    };
    let result = validate_backup_payload(&payload);
    assert!(
        result.is_valid,
        "null person_id must be tolerated, got: {:?}",
        result.errors
    );
}

#[test]
fn test_validate_backup_validates_persons_in_fail_fast_ffi_path() {
    // validate_backup (FFI path) must also reject a person with a blank name.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        persons: vec![Person {
            id: 1,
            name: "".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        ..Default::default()
    };
    let err = crate::validate_backup(&payload).unwrap_err().to_string();
    assert!(err.contains("blank name"), "got: {}", err);
}

#[test]
fn test_validate_backup_fail_fast_rejects_orphan_person_reference() {
    // validate_backup (FFI path) must also reject a loan referencing a
    // non-existent positive person_id.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        persons: vec![Person {
            id: 1,
            name: "Ali".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        loans: vec![Loan {
            id: 1,
            person_name: "Unknown".into(),
            person_id: Some(99),
            loan_type: "DEBTOR".into(),
            original_amount: 5_000_000,
            remaining_amount: 3_000_000,
            description: "test".into(),
            date: 1710000000000,
            is_settled: false,
            tracked: false,
            account_id: None,
        }],
        ..Default::default()
    };
    let err = crate::validate_backup(&payload).unwrap_err().to_string();
    assert!(err.contains("non-existent person"), "got: {}", err);
}

#[test]
fn test_validate_backup_fail_fast_accepts_person_only_payload() {
    // A backup with only well-formed persons must pass both the empty guard
    // and the person validation in the FFI path.
    let payload = BackupPayload {
        version: 1,
        timestamp: 1710000000000,
        app_version: "1.0".to_string(),
        persons: vec![Person {
            id: 1,
            name: "Ali".into(),
            normalized_name: "ali".into(),
            ..person_default()
        }],
        ..Default::default()
    };
    crate::validate_backup(&payload)
        .unwrap_or_else(|e| panic!("person-only payload should be accepted by FFI path, got: {}", e));
}

fn person_default() -> Person {
    Person {
        id: 0,
        name: "".into(),
        normalized_name: "".into(),
        phone: None,
        notes: None,
        created_at: 0,
        is_archived: false,
    }
}
