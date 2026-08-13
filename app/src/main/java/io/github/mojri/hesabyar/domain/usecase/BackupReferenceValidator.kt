package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType

/**
 * Cross-reference checks between backup collections, mirroring the Rust
 * [io.github.mojri.hesabyar.rust.HesabyarCore.validateBackupPayloadSync]
 * behavior: transaction account/destination IDs must resolve to declared
 * accounts, Transfer transactions need a destination different from their
 * source, positive category IDs must resolve, and positive PaymentHistory
 * loan IDs must resolve. Zero IDs are legacy defaults and are tolerated,
 * matching the Rust side.
 *
 * Kept as its own class so [BackupJsonValidator] stays under the detekt
 * TooManyFunctions threshold.
 *
 * User-facing error messages are resolved through [message], which delegates
 * to [BackupJsonValidator] so they stay in sync with localized string resources.
 */
internal class BackupReferenceValidator(
  private val message: (Int, Array<out Any>) -> String
) {
  fun validate(
    backup: BackupPayload,
    errors: MutableList<String>
  ) {
    validateAccountReferences(backup, errors)
    validateTransferStructure(backup.transactions, errors)
    validateCategoryReferences(backup.transactions, backup.categories, errors)
    validateLoanReferences(backup.paymentHistories, backup.loans, errors)
  }

  /**
   * Transaction account/destination IDs must resolve to declared accounts.
   * Legacy backups (no accounts list) may only use the legacy default account ID.
   */
  private fun validateAccountReferences(
    backup: BackupPayload,
    errors: MutableList<String>
  ) {
    // Check for duplicate account IDs (Rust validation.rs:297-299 parity)
    val accountIdCounts = backup.accounts.groupingBy { it.id }.eachCount()
    val duplicateIds = accountIdCounts.filter { it.value > 1 }.keys
    if (duplicateIds.isNotEmpty()) {
      duplicateIds.forEach { id ->
        errors.add(message(R.string.backup_error_duplicate_account_id, arrayOf<Any>(id.toString())))
      }
      return
    }
    val accountIds = backup.accounts.map { it.id }.toSet()
    backup.transactions.forEachIndexed { i, t ->
      if (backup.accounts.isNotEmpty()) {
        if (t.accountId !in accountIds) {
          errors.add(
            message(R.string.backup_error_transaction_invalid_account, arrayOf<Any>(i, t.accountId.toString()))
          )
        }
        t.destinationAccountId?.let { destId ->
          if (destId !in accountIds) {
            errors.add(
              message(R.string.backup_error_transaction_invalid_destination_account, arrayOf<Any>(i, destId.toString()))
            )
          }
        }
      } else {
        // Legacy path: source and destination are checked independently
        // (mirrors Rust validation.rs — a legacy source does NOT excuse a
        // non-legacy destination).
        if (t.accountId != DEFAULT_ACCOUNT_ID) {
          errors.add(
            message(R.string.backup_error_transaction_invalid_legacy_account, arrayOf<Any>(i, t.accountId.toString()))
          )
        }
        t.destinationAccountId?.let { destId ->
          if (destId != DEFAULT_ACCOUNT_ID) {
            errors.add(
              message(
                R.string.backup_error_transaction_invalid_legacy_destination_account,
                arrayOf<Any>(i, destId.toString())
              )
            )
          }
        }
      }
    }
  }

  /**
   * A Transfer must have a destination different from its source account
   * (mirrors validate_accounts_and_references in Rust).
   */
  private fun validateTransferStructure(
    transactions: List<Transaction>,
    errors: MutableList<String>
  ) {
    transactions.forEachIndexed { i, t ->
      if (t.type == TransactionType.TRANSFER) {
        when {
          t.destinationAccountId == null ->
            errors.add(
              message(R.string.backup_error_transfer_no_destination, arrayOf<Any>(i))
            )
          t.destinationAccountId == t.accountId ->
            errors.add(
              message(R.string.backup_error_transfer_same_source_destination, arrayOf<Any>(i))
            )
        }
      }
    }
  }

  /**
   * Category cross-reference — only positive IDs are checked; zero is a
   * legacy default tolerated by older backups.
   */
  private fun validateCategoryReferences(
    transactions: List<Transaction>,
    categories: List<Category>,
    errors: MutableList<String>
  ) {
    if (categories.isEmpty()) return
    val categoryIds = categories.map { it.id }.toSet()
    transactions.forEachIndexed { i, t ->
      if (t.categoryId > 0 && t.categoryId !in categoryIds) {
        errors.add(
          message(R.string.backup_error_transaction_invalid_category, arrayOf<Any>(i, t.categoryId.toString()))
        )
      }
    }
  }

  /**
   * PaymentHistory cross-reference — positive loan IDs must resolve; zero is
   * a legacy default tolerated in all cases.
   */
  private fun validateLoanReferences(
    paymentHistories: List<PaymentHistory>,
    loans: List<Loan>,
    errors: MutableList<String>
  ) {
    val loanIds = loans.map { it.id }.toSet()
    paymentHistories.forEachIndexed { i, payment ->
      if (payment.loanId > 0 && payment.loanId !in loanIds) {
        errors.add(message(R.string.backup_error_payment_invalid_loan, arrayOf<Any>(i, payment.loanId.toString())))
      }
    }
  }
}
