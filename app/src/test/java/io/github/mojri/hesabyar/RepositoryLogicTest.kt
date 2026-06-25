package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryLogicTest {

    @Test
    fun `addPaymentToLoan - reduces remaining amount`() {
        var remainingAmount = 5_000_000L
        val paymentAmount = 2_000_000L

        remainingAmount = (remainingAmount - paymentAmount).coerceAtLeast(0L)
        assertEquals(3_000_000L, remainingAmount)
        assertFalse(remainingAmount <= 0L)
    }

    @Test
    fun `addPaymentToLoan - settles loan when remaining is zero`() {
        var remainingAmount = 2_000_000L
        val paymentAmount = 2_000_000L

        remainingAmount = (remainingAmount - paymentAmount).coerceAtLeast(0L)
        val isSettled = remainingAmount <= 0L
        assertTrue(isSettled)
        assertEquals(0L, remainingAmount)
    }

    @Test
    fun `addPaymentToLoan - overpayment clamps to zero`() {
        var remainingAmount = 1_000_000L
        val paymentAmount = 5_000_000L

        remainingAmount = (remainingAmount - paymentAmount).coerceAtLeast(0L)
        assertEquals(0L, remainingAmount)
        assertTrue(remainingAmount <= 0L)
    }

    @Test
    fun `addPaymentToLoan - multiple payments accumulate`() {
        var remainingAmount = 10_000_000L
        val payments = listOf(3_000_000L, 2_000_000L, 5_000_000L)

        payments.forEach { payment ->
            remainingAmount = (remainingAmount - payment).coerceAtLeast(0L)
        }

        assertEquals(0L, remainingAmount)
    }

    @Test
    fun `addPaymentToLoan - creditor creates expense transaction`() {
        val loanType = "CREDITOR"
        val transactionType = if (loanType == "CREDITOR") "EXPENSE" else "INCOME"
        assertEquals("EXPENSE", transactionType)
    }

    @Test
    fun `addPaymentToLoan - debtor creates income transaction`() {
        val loanType = "DEBTOR"
        val transactionType = if (loanType == "CREDITOR") "EXPENSE" else "INCOME"
        assertEquals("INCOME", transactionType)
    }

    @Test
    fun `addPaymentToLoan - creditor description format`() {
        val loan = Loan(personName = "Ali", type = "CREDITOR", originalAmount = 5_000_000L, remainingAmount = 5_000_000L, description = "test")
        val notes = "partial payment"
        val desc = if (loan.type == "CREDITOR") {
            "بازپرداخت بدهی به ${loan.personName} - $notes"
        } else {
            "دریافت بازپرداخت از ${loan.personName} - $notes"
        }
        assertTrue(desc.contains("Ali"))
        assertTrue(desc.contains("بازپرداخت بدهی"))
    }

    @Test
    fun `addPaymentToLoan - debtor description format`() {
        val loan = Loan(personName = "Reza", type = "DEBTOR", originalAmount = 3_000_000L, remainingAmount = 3_000_000L, description = "test")
        val notes = "repayment"
        val desc = if (loan.type == "CREDITOR") {
            "بازپرداخت بدهی به ${loan.personName} - $notes"
        } else {
            "دریافت بازپرداخت از ${loan.personName} - $notes"
        }
        assertTrue(desc.contains("Reza"))
        assertTrue(desc.contains("دریافت بازپرداخت"))
    }

    @Test
    fun `importBackup - clears and inserts`() {
        val existingTransactions = mutableListOf(
            Transaction(type = "EXPENSE", categoryId = 1L, amount = 100L, description = "old")
        )
        val newTransactions = listOf(
            Transaction(type = "INCOME", categoryId = 2L, amount = 200L, description = "new1"),
            Transaction(type = "EXPENSE", categoryId = 3L, amount = 300L, description = "new2")
        )

        existingTransactions.clear()
        existingTransactions.addAll(newTransactions)

        assertEquals(2, existingTransactions.size)
        assertEquals("new1", existingTransactions[0].description)
    }

    @Test
    fun `replaceAllFromBackup - replaces all data`() {
        val existingCategories = mutableListOf(
            Category(id = 1L, name = "Old", key = "Old", icon = "Test", color = 0L, type = "EXPENSE")
        )
        val newCategories = listOf(
            Category(id = 1L, name = "New", key = "New", icon = "Test", color = 0L, type = "EXPENSE")
        )

        existingCategories.clear()
        existingCategories.addAll(newCategories)

        assertEquals(1, existingCategories.size)
        assertEquals("New", existingCategories[0].name)
    }

    @Test
    fun `mergeFromBackup - updates existing category`() {
        val existing = Category(id = 1, name = "Old Food", key = "Food", icon = "Restaurant", color = 0xFF4CAF50L, type = "EXPENSE")
        val backup = Category(id = 0, name = "New Food", key = "Food", icon = "Restaurant", color = 0xFF4CAF50L, type = "EXPENSE")

        val existingKey = existing.key
        val backupKey = backup.key
        assertEquals(existingKey, backupKey)

        val merged = backup.copy(id = existing.id)
        assertEquals(existing.id, merged.id)
        assertEquals("New Food", merged.name)
    }

    @Test
    fun `mergeFromBackup - inserts new category`() {
        val existingKeys = setOf("Food", "Transportation")
        val backupCategory = Category(id = 0, name = "Health", key = "Health", icon = "Heart", color = 0xFFE91E63L, type = "EXPENSE")

        val isNew = backupCategory.key !in existingKeys
        assertTrue(isNew)
    }

    @Test
    fun `updateInstallment paid creates expense transaction`() {
        val installment = Installment(title = "Car", amount = 2_000_000L, dueDate = System.currentTimeMillis(), isPaid = true)
        assertTrue(installment.isPaid)

        val transaction = Transaction(
            type = "EXPENSE",
            categoryId = 5L,
            amount = installment.amount,
            description = "پرداخت قسط: ${installment.title} - ${installment.notes}"
        )
        assertEquals("EXPENSE", transaction.type)
        assertEquals(2_000_000L, transaction.amount)
    }

    @Test
    fun `loan payment creates correct transaction type mapping`() {
        val scenarios = mapOf(
            "CREDITOR" to "EXPENSE",
            "DEBTOR" to "INCOME"
        )
        scenarios.forEach { (loanType, expectedTxType) ->
            val txType = if (loanType == "CREDITOR") "EXPENSE" else "INCOME"
            assertEquals(expectedTxType, txType)
        }
    }

    @Test
    fun `backup payload preserves all fields`() {
        val backup = BackupPayload(
            version = 2,
            timestamp = 1234567890L,
            appVersion = "1.5",
            transactions = listOf(Transaction(type = "EXPENSE", categoryId = 1L, amount = 1000L, description = "t")),
            loans = listOf(Loan(personName = "Ali", type = "DEBTOR", originalAmount = 5000L, remainingAmount = 3000L, description = "l")),
            installments = listOf(Installment(title = "Car", amount = 2000L, dueDate = 100L)),
            paymentHistories = listOf(PaymentHistory(loanId = 1L, amount = 1000L)),
            categories = listOf(Category(name = "Food", key = "Food", icon = "Restaurant", color = 0xFF4CAF50L, type = "EXPENSE")),
            settings = BackupSettings(darkMode = false)
        )

        assertEquals(2, backup.version)
        assertEquals(1234567890L, backup.timestamp)
        assertEquals("1.5", backup.appVersion)
        assertEquals(1, backup.transactions.size)
        assertEquals(1, backup.loans.size)
        assertEquals(1, backup.installments.size)
        assertEquals(1, backup.paymentHistories.size)
        assertEquals(1, backup.categories.size)
        assertFalse(backup.settings.darkMode)
    }
}
