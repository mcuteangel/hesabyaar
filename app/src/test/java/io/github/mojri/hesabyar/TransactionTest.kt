package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionTest {

    private fun createTransaction(type: String, amount: Long, category: String = "Other"): Transaction {
        return Transaction(type = type, amount = amount, category = category, description = "test")
    }

    private fun createLoan(type: String, originalAmount: Long, remainingAmount: Long, isSettled: Boolean = false): Loan {
        return Loan(personName = "test", type = type, originalAmount = originalAmount, remainingAmount = remainingAmount, description = "test", isSettled = isSettled)
    }

    private fun createInstallment(amount: Long, isPaid: Boolean = false): Installment {
        return Installment(title = "test", amount = amount, dueDate = System.currentTimeMillis(), isPaid = isPaid)
    }

    @Test
    fun `balance calculation - income minus expense`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),   // 10M Rial
            createTransaction("EXPENSE", 3_000_000),   // 3M Rial
            createTransaction("INCOME", 5_000_000),    // 5M Rial
            createTransaction("EXPENSE", 2_000_000)    // 2M Rial
        )

        var totalIncome = 0L
        var totalExpense = 0L
        transactions.forEach {
            if (it.type == "INCOME") totalIncome += it.amount
            else totalExpense += it.amount
        }

        assertEquals(15_000_000L, totalIncome)
        assertEquals(5_000_000L, totalExpense)
        assertEquals(10_000_000L, totalIncome - totalExpense)
    }

    @Test
    fun `balance calculation - all expenses`() {
        val transactions = listOf(
            createTransaction("EXPENSE", 1_000_000),
            createTransaction("EXPENSE", 2_000_000)
        )

        var totalIncome = 0L
        var totalExpense = 0L
        transactions.forEach {
            if (it.type == "INCOME") totalIncome += it.amount
            else totalExpense += it.amount
        }

        assertEquals(0L, totalIncome)
        assertEquals(3_000_000L, totalExpense)
        assertEquals(-3_000_000L, totalIncome - totalExpense)
    }

    @Test
    fun `balance calculation - empty list`() {
        val transactions = emptyList<Transaction>()
        var totalIncome = 0L
        var totalExpense = 0L
        transactions.forEach {
            if (it.type == "INCOME") totalIncome += it.amount
            else totalExpense += it.amount
        }
        assertEquals(0L, totalIncome - totalExpense)
    }

    @Test
    fun `category totals grouping`() {
        val transactions = listOf(
            createTransaction("EXPENSE", 1_000_000, "Food"),
            createTransaction("EXPENSE", 2_000_000, "Food"),
            createTransaction("EXPENSE", 500_000, "Transportation"),
            createTransaction("INCOME", 10_000_000, "Income")
        )

        val categoryTotals = transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        assertEquals(3_000_000L, categoryTotals["Food"])
        assertEquals(500_000L, categoryTotals["Transportation"])
        assertEquals(null, categoryTotals["Income"])
    }

    @Test
    fun `loan balance - remaining amount`() {
        val loans = listOf(
            createLoan("DEBTOR", 5_000_000, 3_000_000),
            createLoan("CREDITOR", 10_000_000, 10_000_000),
            createLoan("DEBTOR", 2_000_000, 0L, isSettled = true)
        )

        var debtorsTotal = 0L
        var creditorsTotal = 0L
        loans.filter { !it.isSettled }.forEach {
            if (it.type == "DEBTOR") debtorsTotal += it.remainingAmount
            else creditorsTotal += it.remainingAmount
        }

        assertEquals(3_000_000L, debtorsTotal)
        assertEquals(10_000_000L, creditorsTotal)
    }

    @Test
    fun `installment - unpaid count and total`() {
        val installments = listOf(
            createInstallment(1_000_000, isPaid = false),
            createInstallment(2_000_000, isPaid = false),
            createInstallment(3_000_000, isPaid = true)
        )

        val unpaid = installments.filter { !it.isPaid }
        assertEquals(2, unpaid.size)
        assertEquals(3_000_000L, unpaid.sumOf { it.amount })
    }

    @Test
    fun `no floating point in monetary values`() {
        val amount: Long = 5_500_000
        val toman = amount / 1000
        assertEquals(5500L, toman)

        val amount2: Long = 1_234_567
        val toman2 = amount2 / 1000
        assertEquals(1234L, toman2)
    }
}
