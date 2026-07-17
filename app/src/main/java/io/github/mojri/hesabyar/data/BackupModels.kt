package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.BuildConfig

/**
 * Backup format/schema version. Bump ONLY on a breaking change to the serialized
 * backup structure (fields added/removed/renamed or a semantics change).
 * Must stay in sync with `BACKUP_SCHEMA_VERSION` in the Rust core
 * (rust/hesabyar-core/src/models/mod.rs).
 */
const val BACKUP_SCHEMA_VERSION = 1

enum class RestoreMode {
  REPLACE,
  MERGE
}

data class BackupPayload(
  val version: Int = BACKUP_SCHEMA_VERSION,
  val timestamp: Long = System.currentTimeMillis(),
  val appVersion: String = BuildConfig.VERSION_NAME,
  val transactions: List<Transaction> = emptyList(),
  val loans: List<Loan> = emptyList(),
  val installments: List<Installment> = emptyList(),
  val paymentHistories: List<PaymentHistory> = emptyList(),
  val categories: List<Category> = emptyList(),
  val bankLoans: List<BankLoan> = emptyList(),
  val settings: BackupSettings = BackupSettings()
)

data class BackupSettings(
  val darkMode: Boolean = true
)

sealed interface BackupValidationResult {
  object Valid : BackupValidationResult

  data class Invalid(
    val errors: List<String>
  ) : BackupValidationResult
}
