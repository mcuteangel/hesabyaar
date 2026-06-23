package io.github.mojri.hesabyar.data

enum class RestoreMode {
    REPLACE,
    MERGE
}

data class BackupPayload(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val transactions: List<Transaction> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val installments: List<Installment> = emptyList(),
    val paymentHistories: List<PaymentHistory> = emptyList(),
    val categories: List<Category> = emptyList(),
    val settings: BackupSettings = BackupSettings()
)

data class BackupSettings(
    val darkMode: Boolean = true
)

sealed interface BackupValidationResult {
    object Valid : BackupValidationResult
    data class Invalid(val errors: List<String>) : BackupValidationResult
}
