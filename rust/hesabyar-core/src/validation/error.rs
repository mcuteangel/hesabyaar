//! Standardized validation error codes across FFI.

use std::fmt;

/// Standardized validation error codes.
///
/// Each variant represents a validation failure and maps to a stable
/// string code returned across the FFI boundary.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ValidationErrorCode {
    BackupVersionInvalid,
    AccountNameBlank,
    AccountDuplicateId,
    AccountInvalidType,
    TransactionInvalidAccount,
    TransactionInvalidDestinationAccount,
    TransactionInvalidLegacyAccount,
    TransactionInvalidLegacyDestinationAccount,
    TransferNoDestination,
    TransferSameSourceDestination,
    TransactionAmountInvalid,
    TransactionDateInvalid,
    TransactionCategoryInvalid,
    TransactionInvalidCategory,
    LoanPersonNameBlank,
    LoanDateInvalid,
    LoanOriginalAmountInvalid,
    LoanRemainingAmountInvalid,
    LoanTypeInvalid,
    LoanInvalidPerson,
    TransactionInvalidPerson,
    InstallmentTitleBlank,
    InstallmentAmountInvalid,
    InstallmentDueDateInvalid,
    BankLoanNameBlank,
    BankLoanReceivedAmountInvalid,
    BankLoanMonthlyInstallmentAmountInvalid,
    BankLoanInstallmentCountInvalid,
    BankLoanStartDateInvalid,
    BankLoanTotalRepayableAmountInvalid,
    BankLoanRepayableOverflow,
    BankLoanRepayableMismatch,
    BankLoanInterestOverflow,
    BankLoanRepayableBelowReceived,
    BankLoanInterestMismatch,
    PaymentAmountInvalid,
    PaymentDateInvalid,
    PaymentLoanIdInvalid,
    PaymentInvalidLoan,
    PersonNameBlank,
    PersonNormalizedBlank,
    PersonDuplicate,
    PersonDuplicateId,
    PaymentDateBeforeCreation,
    PaymentDateBeforeExisting,
    ParsedAmountInvalid,
    ParsedCategoryEmpty,
    ParsedHourInvalid,
    ParsedMinuteInvalid,
}

impl ValidationErrorCode {
    /// Return the stable FFI string code matching strings.xml keys.
    #[must_use]
    pub const fn as_code(&self) -> &'static str {
        match self {
            Self::BackupVersionInvalid => "error_backup_version_invalid",
            Self::AccountNameBlank => "error_account_name_blank",
            Self::AccountDuplicateId => "error_account_duplicate_id",
            Self::AccountInvalidType => "error_account_invalid_type",
            Self::TransactionInvalidAccount => "error_transaction_invalid_account",
            Self::TransactionInvalidDestinationAccount => "error_transaction_invalid_destination_account",
            Self::TransactionInvalidLegacyAccount => "error_transaction_invalid_legacy_account",
            Self::TransactionInvalidLegacyDestinationAccount => "error_transaction_invalid_legacy_destination_account",
            Self::TransferNoDestination => "error_transfer_no_destination",
            Self::TransferSameSourceDestination => "error_transfer_same_source_destination",
            Self::TransactionAmountInvalid => "error_tx_amount_invalid",
            Self::TransactionDateInvalid => "error_tx_date_invalid",
            Self::TransactionCategoryInvalid => "error_tx_category_invalid",
            Self::TransactionInvalidCategory => "error_transaction_invalid_category",
            Self::LoanPersonNameBlank => "error_loan_person_name_blank",
            Self::LoanDateInvalid => "error_loan_date_invalid",
            Self::LoanOriginalAmountInvalid => "error_loan_original_amount_invalid",
            Self::LoanRemainingAmountInvalid => "error_loan_remaining_amount_invalid",
            Self::LoanTypeInvalid => "error_loan_type_invalid",
            Self::LoanInvalidPerson => "error_loan_invalid_person",
            Self::TransactionInvalidPerson => "error_transaction_invalid_person",
            Self::InstallmentTitleBlank => "error_installment_title_blank",
            Self::InstallmentAmountInvalid => "error_installment_amount_invalid",
            Self::InstallmentDueDateInvalid => "error_installment_due_date_invalid",
            Self::BankLoanNameBlank => "error_bank_loan_name_blank",
            Self::BankLoanReceivedAmountInvalid => "error_bank_loan_received_amount_invalid",
            Self::BankLoanMonthlyInstallmentAmountInvalid => "error_bank_loan_monthly_installment_amount_invalid",
            Self::BankLoanInstallmentCountInvalid => "error_bank_loan_installment_count_invalid",
            Self::BankLoanStartDateInvalid => "error_bank_loan_start_date_invalid",
            Self::BankLoanTotalRepayableAmountInvalid => "error_bank_loan_total_repayable_amount_invalid",
            Self::BankLoanRepayableOverflow => "error_bank_loan_repayable_overflow",
            Self::BankLoanRepayableMismatch => "error_bank_loan_repayable_mismatch",
            Self::BankLoanInterestOverflow => "error_bank_loan_interest_overflow",
            Self::BankLoanRepayableBelowReceived => "error_bank_loan_repayable_below_received",
            Self::BankLoanInterestMismatch => "error_bank_loan_interest_mismatch",
            Self::PaymentAmountInvalid => "error_payment_amount_invalid",
            Self::PaymentDateInvalid => "error_payment_date_invalid",
            Self::PaymentLoanIdInvalid => "error_payment_loan_id_invalid",
            Self::PaymentInvalidLoan => "error_payment_invalid_loan",
            Self::PersonNameBlank => "error_person_name_blank",
            Self::PersonNormalizedBlank => "error_person_normalized_blank",
            Self::PersonDuplicate => "error_person_duplicate",
            Self::PersonDuplicateId => "error_person_duplicate_id",
            Self::PaymentDateBeforeCreation => "error_payment_date_before_creation",
            Self::PaymentDateBeforeExisting => "error_payment_date_before_existing",
            Self::ParsedAmountInvalid => "error_parsed_amount_invalid",
            Self::ParsedCategoryEmpty => "error_parsed_category_empty",
            Self::ParsedHourInvalid => "error_parsed_hour_invalid",
            Self::ParsedMinuteInvalid => "error_parsed_minute_invalid",
        }
    }

    /// Return the English developer message for logging.
    #[must_use]
    pub const fn default_message(&self) -> &'static str {
        match self {
            Self::BackupVersionInvalid => "Invalid backup version",
            Self::AccountNameBlank => "Account has empty name",
            Self::AccountDuplicateId => "Duplicate account id",
            Self::AccountInvalidType => "Account has invalid type",
            Self::TransactionInvalidAccount => "Transaction references non-existent source account",
            Self::TransactionInvalidDestinationAccount => "Transaction references non-existent destination account",
            Self::TransactionInvalidLegacyAccount => "Transaction references non-legacy account",
            Self::TransactionInvalidLegacyDestinationAccount => "Transaction references non-legacy destination account",
            Self::TransferNoDestination => "Transfer must have a destination_account_id",
            Self::TransferSameSourceDestination => "Transfer source and destination accounts must differ",
            Self::TransactionAmountInvalid => "Transaction amount must be positive",
            Self::TransactionDateInvalid => "Transaction date must be positive",
            Self::TransactionCategoryInvalid => "Transaction category_id must not be negative",
            Self::TransactionInvalidCategory => "Transaction references non-existent category",
            Self::LoanPersonNameBlank => "Loan person_name must not be empty",
            Self::LoanDateInvalid => "Loan date must be positive",
            Self::LoanOriginalAmountInvalid => "Loan original_amount must be positive",
            Self::LoanRemainingAmountInvalid => "Loan remaining_amount must be non-negative",
            Self::LoanTypeInvalid => "Loan type must be DEBTOR or CREDITOR",
            Self::LoanInvalidPerson => "Loan references non-existent person",
            Self::TransactionInvalidPerson => "Transaction references non-existent person",
            Self::InstallmentTitleBlank => "Installment title must not be empty",
            Self::InstallmentAmountInvalid => "Installment amount must be positive",
            Self::InstallmentDueDateInvalid => "Installment due_date must be positive",
            Self::BankLoanNameBlank => "BankLoan bank_name must not be empty",
            Self::BankLoanReceivedAmountInvalid => "BankLoan received_amount must be positive",
            Self::BankLoanMonthlyInstallmentAmountInvalid => "BankLoan monthly_installment_amount must be positive",
            Self::BankLoanInstallmentCountInvalid => "BankLoan number_of_installments must be positive",
            Self::BankLoanStartDateInvalid => "BankLoan start_date must be positive",
            Self::BankLoanTotalRepayableAmountInvalid => "BankLoan total_repayable_amount must be positive",
            Self::BankLoanRepayableOverflow => "BankLoan repayable amount overflows",
            Self::BankLoanRepayableMismatch => {
                "BankLoan total_repayable_amount must equal monthly_installment_amount * number_of_installments"
            }
            Self::BankLoanInterestOverflow => "BankLoan interest calculation overflows",
            Self::BankLoanRepayableBelowReceived => {
                "BankLoan total_repayable_amount must not be less than received_amount"
            }
            Self::BankLoanInterestMismatch => {
                "BankLoan total_interest must equal total_repayable_amount - received_amount"
            }
            Self::PaymentAmountInvalid => "PaymentHistory amount must be positive",
            Self::PaymentDateInvalid => "PaymentHistory date must be positive",
            Self::PaymentLoanIdInvalid => "PaymentHistory loan_id must be positive",
            Self::PaymentInvalidLoan => "PaymentHistory references non-existent loan",
            Self::PersonNameBlank => "Person has a blank name",
            Self::PersonNormalizedBlank => "Person has a blank normalizedName",
            Self::PersonDuplicate => "Person has a duplicate normalizedName",
            Self::PersonDuplicateId => "Person has a duplicate id",
            Self::PaymentDateBeforeCreation => "Loan payment date is before the loan creation date",
            Self::PaymentDateBeforeExisting => "Loan payment date is before an existing payment date",
            Self::ParsedAmountInvalid => "ParsedResult amount must be positive",
            Self::ParsedCategoryEmpty => "ParsedResult category must not be empty",
            Self::ParsedHourInvalid => "ParsedResult hour must be 0-23",
            Self::ParsedMinuteInvalid => "ParsedResult minute must be 0-59",
        }
    }
}

impl fmt::Display for ValidationErrorCode {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_code())
    }
}

impl From<ValidationErrorCode> for String {
    fn from(code: ValidationErrorCode) -> Self {
        code.as_code().to_string()
    }
}
