package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidationTest {

    private const val INVALID_BACKUP_MSG = "نسخه پشتیبان نامعتبر است"
    private const val INVALID_TRANSACTION_AMOUNT_MSG = "مبلغ تراکنش نامعتبر: %s"
    private const val INVALID_TRANSACTION_TYPE_MSG = "نوع تراکنش نامعتبر: %s"
    private const val INVALID_LOAN_AMOUNT_MSG = "مبلغ وام نامعتبر: %s"
    private const val INVALID_LOAN_TYPE_MSG = "نوع وام نامعتبر: %s"
    private const val INVALID_INSTALLMENT_AMOUNT_MSG = "مبلغ قسط نامعتبر: %s"
    private fun validateBackup(backup: BackupPayload): BackupValidationResult {
        val errors = ArrayList<String>()

        if (backup.version < 1) {
            errors.add(INVALID_BACKUP_MSG)
        }

        for (tx in backup.transactions) {
            if (tx.amount <= 0) {
                errors.add(String.format(INVALID_TRANSACTION_AMOUNT_MSG, tx.description))
            }
            if (tx.type !in listOf("EXPENSE", "INCOME")) {
                errors.add(String.format(INVALID_TRANSACTION_TYPE_MSG, tx.type))
            }
        }

        for (loan in backup.loans) {
            if (loan.originalAmount <= 0) {
                errors.add(String.format(INVALID_LOAN_AMOUNT_MSG, loan.personName))
            }
            if (loan.type !in listOf("DEBTOR", "CREDITOR")) {
                errors.add(String.format(INVALID_LOAN_TYPE_MSG, loan.type))
            }
        }

        for (inst in backup.installments) {
            if (inst.amount <= 0) {
                errors.add(String.format(INVALID_INSTALLMENT_AMOUNT_MSG, inst.title))
            }
        }

        if (errors.isNotEmpty()) {
            return BackupValidationResult.Invalid(errors)
        }
        return BackupValidationResult.Valid
    }

    @Test
    fun `valid backup returns Valid`() {
        val backup = BackupPayload(
            transactions = listOf(
                Transaction(type = "EXPENSE", categoryId = 1L, amount = 1_000_000L, description = "test")
            ),
            loans = listOf(
                Loan(personName = "Ali", type = "DEBTOR", originalAmount = 5_000_000L, remainingAmount = 3_000_000L, description = "loan")
            ),
            installments = listOf(
                Installment(title = "Car loan", amount = 2_000_000L, dueDate = System.currentTimeMillis())
            )
        )
        assertTrue(validateBackup(backup) is BackupValidationResult.Valid)
    }

    @Test
    fun `empty backup is valid`() {
        val backup = BackupPayload()
        assertTrue(validateBackup(backup) is BackupValidationResult.Valid)
    }

    companion object {
        private const val TRANSACTION_TYPE_LABEL = "نوع تراکنش"
    }

    @Test
    fun `invalid transaction type returns error`() {
        val backup = BackupPayload(
            transactions = listOf(
                Transaction(type = "INVALID", categoryId = 1L, amount = 1_000_000L, description = "test")
            )
        )
        val result = validateBackup(backup)
        assertTrue(result is BackupValidationResult.Invalid)
        assertEquals(1, (result as BackupValidationResult.Invalid).errors.size)
        assertTrue(result.errors[0].contains(TRANSACTION_TYPE_LABEL))
    }

    @Test
    fun `negative transaction amount returns error`() {
        val backup = BackupPayload(
            transactions = listOf(
                Transaction(type = "EXPENSE", categoryId = 1L, amount = -500L, description = "test")
            )
        )
        val result = validateBackup(backup)
        assertTrue(result is BackupValidationResult.Invalid)
        assertEquals(1, (result as BackupValidationResult.Invalid).errors.size)
    }

    @Test
    fun `zero transaction amount returns error`() {
        val backup = BackupPayload(
            transactions = listOf(
                Transaction(type = "EXPENSE", categoryId = 1L, amount = 0L, description = "test")
            )
        )
        val result = validateBackup(backup)
        assertTrue(result is BackupValidationResult.Invalid)
    }

    @Test
    fun `invalid loan type returns error`() {
        val backup = BackupPayload(
            loans = listOf(
                Loan(personName = "Ali", type = "INVALID", originalAmount = 5_000_000L, remainingAmount = 5_000_000L, description = "loan")
            )
        )
        val result = validateBackup(backup)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).errors[0].contains("نوع وام"))
    }

    @Test
    fun `negative loan amount returns error`() {
        val backup = BackupPayload(
            loans = listOf(
                Loan(personName = "Ali", type = "DEBTOR", originalAmount = -1000L, remainingAmount = -1000L, description = "loan")
            )
        )
        val result = validateBackup(backup)
        assertTrue(result is BackupValidationResult.Invalid)
    }

    @Test
    fun `negative installment amount returns error`() {
        val backup = BackupPayload(
            installments = listOf(
                Installment(title = "Car", amount = -500L, dueDate = System.currentTimeMillis())
            )
        )
        val result = validateBackup(backup)
        assertTrue(result is BackupValidationResult.Invalid)
    }

    @Test
    fun `multiple errors collected`() {
        val backup = BackupPayload(
            transactions = listOf(
                Transaction(type = "BAD", categoryId = 1L, amount = -100L, description = "t1"),
                Transaction(type = "ALSO_BAD", categoryId = 1L, amount = 0L, description = "t2")
            ),
            loans = listOf(
                Loan(personName = "X", type = "INVALID", originalAmount = -500L, remainingAmount = 0L, description = "l1")
            )
        )
        val result = validateBackup(backup)
        assertTrue(result is BackupValidationResult.Invalid)
        val errors = (result as BackupValidationResult.Invalid).errors
        assertTrue(errors.size >= 3)
    }

    @Test
    fun `valid DEBTOR and CREDITOR loan types pass`() {
        val backup = BackupPayload(
            loans = listOf(
                Loan(personName = "Ali", type = "DEBTOR", originalAmount = 1_000_000L, remainingAmount = 500_000L, description = "d"),
                Loan(personName = "Reza", type = "CREDITOR", originalAmount = 2_000_000L, remainingAmount = 2_000_000L, description = "c")
            )
        )
        assertTrue(validateBackup(backup) is BackupValidationResult.Valid)
    }

    @Test
    fun `BackupPayload default values`() {
        val backup = BackupPayload()
        assertEquals(1, backup.version)
        assertTrue(backup.transactions.isEmpty())
        assertTrue(backup.loans.isEmpty())
        assertTrue(backup.installments.isEmpty())
        assertTrue(backup.paymentHistories.isEmpty())
        assertTrue(backup.categories.isEmpty())
        assertEquals("1.0", backup.appVersion)
    }

    @Test
    fun `BackupSettings default dark mode is true`() {
        val settings = BackupSettings()
        assertTrue(settings.darkMode)
    }
}
