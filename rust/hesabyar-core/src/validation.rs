use crate::models::*;

/// Result of batch validation — collects all errors.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ValidationResult {
    pub is_valid: bool,
    pub errors: Vec<String>,
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
    }
    if tx.description.is_empty() {
        return Err("Transaction description must not be empty".into());
    }
    if tx.date <= 0 {
        return Err("Transaction date must be positive".into());
    }
    if tx.category_id <= 0 {
        return Err("Transaction category_id must be positive".into());
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
    if loan.original_amount <= 0 {
        return Err("Loan original_amount must be positive".into());
    }
    if loan.remaining_amount < 0 {
        return Err("Loan remaining_amount must be non-negative".into());
    }
    if loan.remaining_amount > loan.original_amount {
        return Err("Loan remaining_amount must not exceed original_amount".into());
    }
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
    // All five TransactionType variants are valid.
    match result.tx_type {
        TransactionType::Expense
        | TransactionType::Income
        | TransactionType::LoanDebtor
        | TransactionType::LoanCreditor
        | TransactionType::Installment => {}
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

/// Validate an entire backup payload. Collects all errors from all entities.
pub fn validate_backup_payload(payload: &BackupPayload) -> ValidationResult {
    let mut errors = Vec::new();
    if payload.version < 1 {
        errors.push("Invalid backup version".into());
    }
    errors.extend(validate_transaction_batch(&payload.transactions).errors);
    errors.extend(validate_loan_batch(&payload.loans).errors);
    errors.extend(validate_installment_batch(&payload.installments).errors);
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
    fn test_transaction_empty_description_rejected() {
        let err = validate_transaction(&make_tx(50000, "", 1)).unwrap_err();
        assert!(err.contains("description"));
    }

    #[test]
    fn test_transaction_zero_date_rejected() {
        let mut tx = make_tx(50000, "coffee", 1);
        tx.date = 0;
        let err = validate_transaction(&tx).unwrap_err();
        assert!(err.contains("date"));
    }

    #[test]
    fn test_transaction_zero_category_rejected() {
        let err = validate_transaction(&make_tx(50000, "coffee", 0)).unwrap_err();
        assert!(err.contains("category"));
    }

    #[test]
    fn test_transaction_negative_category_rejected() {
        let err = validate_transaction(&make_tx(50000, "coffee", -1)).unwrap_err();
        assert!(err.contains("category"));
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
    fn test_loan_remaining_exceeds_original_rejected() {
        let err = validate_loan(&make_loan(5000000, 6000000, "DEBTOR")).unwrap_err();
        assert!(err.contains("remaining_amount"));
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
            make_tx(50000, "", 1),    // empty desc
            make_tx(-1, "bad3", 1),   // negative amount
            make_tx(50000, "ok", 1),  // valid
        ];
        let result = validate_transaction_batch(&txs);
        assert!(!result.is_valid);
        assert_eq!(result.errors.len(), 3);
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
            categories: vec![],
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
            categories: vec![],
        };
        let result = validate_backup_payload(&payload);
        assert!(!result.is_valid);
        // At least one error from each entity type
        assert!(result.errors.len() >= 3);
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
            categories: vec![],
        };
        let result = validate_backup_payload(&payload);
        assert!(!result.is_valid);
        assert_eq!(result.errors.len(), 1);
    }
}
