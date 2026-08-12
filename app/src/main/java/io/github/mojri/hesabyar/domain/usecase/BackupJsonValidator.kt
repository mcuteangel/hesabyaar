package io.github.mojri.hesabyar.domain.usecase

import android.util.Log
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.rust.RustBridge
import io.github.mojri.hesabyar.rust.RustMappers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "BackupJsonValidator"

/**
 * Validates parsed backup payloads (Rust-first with a Kotlin fallback, matching
 * the parser's strategy). Field-level checks live in per-collection private
 * helpers so the class stays small.
 */
class BackupJsonValidator(
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  suspend fun validateBackup(backup: BackupPayload): BackupValidationResult =
    withContext(dispatcher) {
      if (RustBridge.isAvailable) {
        try {
          val rustResult = RustBridge.validateBackupPayloadSync(backup.toRustPayload())

          if (rustResult.isValid) {
            BackupValidationResult.Valid
          } else {
            BackupValidationResult.Invalid(rustResult.errors)
          }
        } catch (e: IllegalArgumentException) {
          // Mapping to Rust payload failed (e.g. from mapAccounts/mapCategories);
          // fall back to Kotlin validation instead of escaping as an unhandled exception.
          Log.w(TAG, "Rust→Kotlin mapping failed during validation, falling back to Kotlin", e)
          validateBackupKotlin(backup)
        }
      } else {
        // Rust unavailable — fall back to local Kotlin validation
        validateBackupKotlin(backup)
      }
    }

  private fun validateBackupKotlin(backup: BackupPayload): BackupValidationResult {
    val errors = mutableListOf<String>()

    if (backup.version <= 0) errors.add("نسخه پشتیبان نامعتبر است")
    if (backup.appVersion.isBlank()) errors.add("نسخه برنامه پشتیبان نامعتبر است")
    if (backup.timestamp <= 0) errors.add("زمان تهیه پشتیبان نامعتبر است")

    validateBackupTransactions(backup.transactions, errors)
    validateBackupLoans(backup.loans, errors)
    validateBackupInstallments(backup.installments, errors)
    validateBackupCategories(backup.categories, errors)
    validateBackupPaymentHistories(backup.paymentHistories, errors)
    validateBackupBankLoans(backup.bankLoans, errors)
    validateBackupAccounts(backup.accounts, errors)

    return if (errors.isEmpty()) {
      BackupValidationResult.Valid
    } else {
      BackupValidationResult.Invalid(errors)
    }
  }

  private fun validateBackupTransactions(
    transactions: List<Transaction>,
    errors: MutableList<String>
  ) {
    transactions.forEachIndexed { i, t ->
      if (t.amount <= 0) errors.add("مبلغ تراکنش #$i نامعتبر است")
      if (t.date <= 0) errors.add("تاریخ تراکنش #$i نامعتبر است")
    }
  }

  private fun validateBackupLoans(
    loans: List<Loan>,
    errors: MutableList<String>
  ) {
    loans.forEachIndexed { i, l ->
      if (l.personName.isBlank()) errors.add("نام شخص وام #$i خالی است")
      if (l.date <= 0) errors.add("تاریخ وام #$i نامعتبر است")
      if (l.originalAmount <= 0) errors.add("مبلغ اولیه وام #$i نامعتبر است")
      if (l.remainingAmount < 0) errors.add("مبلغ باقی‌مانده وام #$i نامعتبر است")
    }
  }

  private fun validateBackupInstallments(
    installments: List<Installment>,
    errors: MutableList<String>
  ) {
    installments.forEachIndexed { i, installment ->
      if (installment.title.isBlank()) errors.add("عنوان قسط #$i خالی است")
      if (installment.amount <= 0) errors.add("مبلغ قسط #$i نامعتبر است")
      if (installment.dueDate <= 0) errors.add("تاریخ سررسید قسط #$i نامعتبر است")
    }
  }

  private fun validateBackupCategories(
    categories: List<Category>,
    errors: MutableList<String>
  ) {
    categories.forEachIndexed { i, category ->
      if (category.name.isBlank()) errors.add("نام دسته‌بندی #$i خالی است")
    }
  }

  private fun validateBackupPaymentHistories(
    payments: List<PaymentHistory>,
    errors: MutableList<String>
  ) {
    payments.forEachIndexed { i, payment ->
      if (payment.amount <= 0) errors.add("مبلغ پرداخت #$i نامعتبر است")
      if (payment.date <= 0) errors.add("تاریخ پرداخت #$i نامعتبر است")
    }
  }

  private fun validateBackupBankLoans(
    bankLoans: List<BankLoan>,
    errors: MutableList<String>
  ) {
    bankLoans.forEachIndexed { i, bankLoan ->
      if (bankLoan.bankName.isBlank()) errors.add("نام بانک وام #$i خالی است")
      if (bankLoan.receivedAmount <= 0) errors.add("مبلغ دریافتی وام #$i نامعتبر است")
      if (bankLoan.numberOfInstallments <= 0) errors.add("تعداد اقساط وام #$i نامعتبر است")
      if (bankLoan.monthlyInstallmentAmount <= 0) errors.add("مبلغ قسط ماهانه وام #$i نامعتبر است")
      if (bankLoan.startDate <= 0) errors.add("تاریخ شروع وام #$i نامعتبر است")
    }
  }

  private fun validateBackupAccounts(
    accounts: List<AccountEntity>,
    errors: MutableList<String>
  ) {
    accounts.forEachIndexed { i, account ->
      if (account.name.isBlank()) errors.add("نام حساب #$i خالی است")
      // createdAt == 0 is a legacy sentinel from the v6→v7 migration
      // (MIGRATION_6_7 used DEFAULT 0 for accounts that existed before
      // timestamps were tracked). Accept it as valid.
    }
  }

  private fun BackupPayload.toRustPayload(): io.github.mojri.hesabyar.rust.BackupPayload =
    io.github.mojri.hesabyar.rust.BackupPayload(
      version = version,
      timestamp = timestamp,
      appVersion = appVersion,
      transactions = RustMappers.mapTransactions(transactions),
      loans = RustMappers.mapLoans(loans),
      installments = RustMappers.mapInstallments(installments),
      paymentHistories = RustMappers.mapPaymentHistories(paymentHistories),
      bankLoans = RustMappers.mapBankLoans(bankLoans),
      categories = RustMappers.mapCategories(categories),
      accounts = RustMappers.mapAccounts(accounts)
    )
}
