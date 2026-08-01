use crate::models::*;

/// Result of batch validation — collects all errors.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ValidationResult {
    pub is_valid: bool,
    pub errors: Vec<String>,
}

impl Default for ValidationResult {
    fn default() -> Self {
        Self { is_valid: true, errors: vec![] }
    }
}

// ===========================================================================
// Single-entity validators (return first error)
// ===========================================================================

/// Validate a single transaction.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_transaction(tx: &Transaction) -> Result<(), String> {
    if tx.amount <= 0 {
        return Err("Transaction amount must be positive".into());
    }
    // All five TransactionType variants are valid — no invalid variant can exist
    // after deserialization, but we validate defensively.
    match tx.tx_type {
        TransactionType::Expense
        | TransactionType::Income
        | TransactionType::LoanDebtor
        | TransactionType::LoanCreditor
        | TransactionType::Installment => {}
        TransactionType::Transfer => {
            if tx.destination_account_id.is_none() {
                return Err("Transfer must have a destination_account_id".into());
            }
            if tx.destination_account_id == Some(tx.account_id) {
                return Err(
                    "Transfer source and destination accounts must differ".into(),
                );
            }
        }
    }
    if tx.date <= 0 {
        return Err("Transaction date must be positive".into());
    }
    // Note: empty descriptions and non-positive category_id are tolerated here
    // (instead of rejected) so that backups exported by older versions of the
    // app — which allowed blank descriptions and defaulted missing category ids
    // to 1 — can still be restored. A negative category_id is still rejected as
    // it can never reference a valid category.
    if tx.category_id < 0 {
        return Err("Transaction category_id must not be negative".into());
    }
    Ok(())
}

/// Validate a single loan.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_loan(loan: &Loan) -> Result<(), String> {
    if loan.person_name.is_empty() {
        return Err("Loan person_name must not be empty".into());
    }
    if loan.date <= 0 {
        return Err("Loan date must be positive".into());
    }
    if loan.original_amount <= 0 {
        return Err("Loan original_amount must be positive".into());
    }
    if loan.remaining_amount < 0 {
        return Err("Loan remaining_amount must be non-negative".into());
    }
    // Note: remaining_amount may exceed original_amount for backups created by
    // older app versions (e.g. after partial manual edits). Tolerated on import
    // rather than hard-rejected so those backups can still be restored.
    if loan.loan_type != "DEBTOR" && loan.loan_type != "CREDITOR" {
        return Err(format!(
            "Loan type must be DEBTOR or CREDITOR, got '{}'",
            loan.loan_type
        ));
    }
    Ok(())
}

/// Validate a single installment.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_installment(inst: &Installment) -> Result<(), String> {
    if inst.title.is_empty() {
        return Err("Installment title must not be empty".into());
    }
    if inst.amount <= 0 {
        return Err("Installment amount must be positive".into());
    }
    if inst.due_date <= 0 {
        return Err("Installment due_date must be positive".into());
    }
    Ok(())
}

/// Validate a single ParsedResult (AI parser output).
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_parsed_result(result: &ParsedResult) -> Result<(), String> {
    if result.amount <= 0 {
        return Err("ParsedResult amount must be positive".into());
    }
    // All six TransactionType variants are valid.
    match result.tx_type {
        TransactionType::Expense
        | TransactionType::Income
        | TransactionType::LoanDebtor
        | TransactionType::LoanCreditor
        | TransactionType::Installment
        | TransactionType::Transfer => {}
    }
    if result.category.is_empty() {
        return Err("ParsedResult category must not be empty".into());
    }
    if let Some(hour) = result.hour {
        if hour < 0 || hour > 23 {
            return Err(format!("ParsedResult hour must be 0-23, got {}", hour));
        }
    }
    if let Some(minute) = result.minute {
        if minute < 0 || minute > 59 {
            return Err(format!("ParsedResult minute must be 0-59, got {}", minute));
        }
    }
    Ok(())
}

// ===========================================================================
// Batch validators (collect all errors)
// ===========================================================================

/// Validate a batch of transactions. Collects all errors.
pub fn validate_transaction_batch(transactions: &[Transaction]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, tx) in transactions.iter().enumerate() {
        if let Err(e) = validate_transaction(tx) {
            errors.push(format!("Transaction[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate a batch of loans. Collects all errors.
pub fn validate_loan_batch(loans: &[Loan]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, loan) in loans.iter().enumerate() {
        if let Err(e) = validate_loan(loan) {
            errors.push(format!("Loan[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate a batch of installments. Collects all errors.
pub fn validate_installment_batch(installments: &[Installment]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, inst) in installments.iter().enumerate() {
        if let Err(e) = validate_installment(inst) {
            errors.push(format!("Installment[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate a single bank loan.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_bank_loan(bl: &BankLoan) -> Result<(), String> {
    if bl.bank_name.trim().is_empty() {
        return Err("BankLoan bank_name must not be empty".into());
    }
    if bl.received_amount <= 0 {
        return Err("BankLoan received_amount must be positive".into());
    }
    if bl.monthly_installment_amount <= 0 {
        return Err("BankLoan monthly_installment_amount must be positive".into());
    }
    if bl.number_of_installments <= 0 {
        return Err("BankLoan number_of_installments must be positive".into());
    }
    if bl.start_date <= 0 {
        return Err("BankLoan start_date must be positive".into());
    }
    if bl.total_repayable_amount <= 0 {
        return Err("BankLoan total_repayable_amount must be positive".into());
    }
    // Repayment/interest relationships, using checked arithmetic to reject overflow.
    let expected_repayable = bl
        .monthly_installment_amount
        .checked_mul(bl.number_of_installments as i64)
        .ok_or("BankLoan repayable amount overflows (monthly_installment_amount * number_of_installments)")?;
    if bl.total_repayable_amount != expected_repayable {
        return Err(
            "BankLoan total_repayable_amount must equal monthly_installment_amount * number_of_installments".into(),
        );
    }
    let expected_interest = bl
        .total_repayable_amount
        .checked_sub(bl.received_amount)
        .ok_or("BankLoan interest calculation overflows")?;
    if expected_interest < 0 {
        return Err("BankLoan total_repayable_amount must not be less than received_amount".into());
    }
    if bl.total_interest != expected_interest {
        return Err("BankLoan total_interest must equal total_repayable_amount - received_amount".into());
    }
    Ok(())
}

/// Validate a batch of bank loans. Collects all errors.
pub fn validate_bank_loan_batch(bank_loans: &[BankLoan]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, bl) in bank_loans.iter().enumerate() {
        if let Err(e) = validate_bank_loan(bl) {
            errors.push(format!("BankLoan[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate a single payment history entry.
///
/// Returns `Ok(())` if valid, or `Err(message)` describing the first violation.
pub fn validate_payment_history(ph: &PaymentHistory) -> Result<(), String> {
    if ph.amount <= 0 {
        return Err("PaymentHistory amount must be positive".into());
    }
    if ph.date <= 0 {
        return Err("PaymentHistory date must be positive".into());
    }
    if ph.loan_id <= 0 {
        return Err("PaymentHistory loan_id must be positive".into());
    }
    Ok(())
}

/// Validate a batch of payment histories. Collects all errors.
pub fn validate_payment_history_batch(payment_histories: &[PaymentHistory]) -> ValidationResult {
    let mut errors = Vec::new();
    for (i, ph) in payment_histories.iter().enumerate() {
        if let Err(e) = validate_payment_history(ph) {
            errors.push(format!("PaymentHistory[{}]: {}", i, e));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

/// Validate an entire backup payload. Collects all errors from all entities.
pub fn validate_backup_payload(payload: &BackupPayload) -> ValidationResult {
    let mut errors = Vec::new();
    if payload.version < 1 {
        errors.push("Invalid backup version".into());
    }
    // Category cross-reference check (mirrors FFI validate_backup).
    // Only check positive IDs — zero is a legacy default tolerated by
    // validate_transaction, so treating it as missing would break old backups.
    if !payload.categories.is_empty() {
        let category_ids: std::collections::HashSet<_> = payload.categories.iter().map(|c| c.id).collect();
        for (i, tx) in payload.transactions.iter().enumerate() {
            if tx.category_id > 0 && !category_ids.contains(&tx.category_id) {
                errors.push(format!(
                    "Transaction[{}] references non-existent category {}",
                    i, tx.category_id
                ));
            }
        }
    }
    errors.extend(validate_transaction_batch(&payload.transactions).errors);
    errors.extend(validate_loan_batch(&payload.loans).errors);
    errors.extend(validate_installment_batch(&payload.installments).errors);
    errors.extend(validate_bank_loan_batch(&payload.bank_loans).errors);
    errors.extend(validate_payment_history_batch(&payload.payment_histories).errors);
    // PaymentHistory cross-reference: positive loan_id must point to an existing loan.
    // Zero is a legacy default tolerated in all cases.
    let loan_ids: std::collections::HashSet<_> = payload.loans.iter().map(|l| l.id).collect();
    for (i, ph) in payload.payment_histories.iter().enumerate() {
        if ph.loan_id > 0 && !loan_ids.contains(&ph.loan_id) {
            errors.push(format!(
                "PaymentHistory[{}] references non-existent loan {}",
                i, ph.loan_id
            ));
        }
    }
    ValidationResult {
        is_valid: errors.is_empty(),
        errors,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_tx(amount: i64, desc: &str, category_id: i64) -> Transaction {
        Transaction {
            id: 1,
            tx_type: TransactionType::Expense,
            category_id,
            amount,
            description: desc.to_string(),
            person_name: None,
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
            loan_type: loan_type.to_string(),
            original_amount: amount,
            remaining_amount: remaining,
            description: "test".to_string(),
            date: 1710000000000,
            is_settled: false,
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
            notes: String::new(),
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
        loan.person_name = String::new();
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
            make_tx(0, "bad1", 1),    // zero amount
            make_tx(50000, "", 1),    // empty desc (tolerated)
            make_tx(-1, "bad3", 1),   // negative amount
            make_tx(50000, "ok", 1),  // valid
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
            transactions: vec![
                make_tx(50000, "good", 1),
                make_tx(0, "bad", 1),
            ],
            loans: vec![],
            installments: vec![],
            bank_loans: vec![],
            payment_histories: vec![],
            categories: vec![],
            accounts: vec![],
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
        };
        let result = validate_backup_payload(&payload);
        assert!(result.is_valid, "Legacy category_id=0 should be tolerated, got: {:?}", result.errors);
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
        };
        let result = validate_backup_payload(&payload);
        assert!(!result.is_valid);
        assert!(result.errors.iter().any(|e| e.contains("non-existent loan")));
    }
}
