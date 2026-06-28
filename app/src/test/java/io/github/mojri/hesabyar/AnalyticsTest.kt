package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.*
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {

    private fun createTransaction(
        type: String,
        amount: Long,
        categoryId: Long = 1L,
        date: Long = System.currentTimeMillis()
    ): Transaction {
        return Transaction(type = type, amount = amount, categoryId = categoryId, description = "test", date = date)
    }

    private fun createLoan(
        type: String,
        originalAmount: Long,
        remainingAmount: Long,
        personName: String = "test",
        isSettled: Boolean = false
    ): Loan {
        return Loan(personName = personName, type = type, originalAmount = originalAmount, remainingAmount = remainingAmount, description = "test", isSettled = isSettled)
    }

    private fun createInstallment(
        title: String = "test",
        amount: Long,
        isPaid: Boolean = false,
        dueDate: Long = System.currentTimeMillis()
    ): Installment {
        return Installment(title = title, amount = amount, dueDate = dueDate, isPaid = isPaid)
    }

    companion object {
        private const val TEST_KEY = "test"
        private const val TEST_ICON = "Test"
        private const val TYPE_EXPENSE = "EXPENSE"
    }

    private fun createCategory(id: Long, name: String, color: Long = 0xFF757575L): Category {
        return Category(id = id, name = name, key = TEST_KEY, icon = TEST_ICON, color = color, type = TYPE_EXPENSE)
    }
        val now = System.currentTimeMillis()
        val oneMonthMs = 30L * 24 * 60 * 60 * 1000

        val transactions = listOf(
            createTransaction("EXPENSE", 1_000_000, date = now),
            createTransaction("EXPENSE", 2_000_000, date = now),
            createTransaction("EXPENSE", 3_000_000, date = now - oneMonthMs)
        )

        val monthlyMap = mutableMapOf<String, Long>()
        transactions.filter { it.type == "EXPENSE" }.forEach { t ->
            val jd = JalaliCalendarHelper.gregorianToJalali(t.date)
            val key = "${jd.year}_${jd.month}"
            monthlyMap[key] = (monthlyMap[key] ?: 0L) + t.amount
        }

        // Should have 2 months
        assertEquals(2, monthlyMap.size)
    }

    @Test
    fun `category breakdown calculation`() {
        val categories = listOf(
            createCategory(1L, "خوراک"),
            createCategory(2L, "حمل و نقل")
        )

        val transactions = listOf(
            createTransaction("EXPENSE", 1_000_000, categoryId = 1L),
            createTransaction("EXPENSE", 2_000_000, categoryId = 1L),
            createTransaction("EXPENSE", 500_000, categoryId = 2L)
        )

        val categoryTotals = mutableMapOf<Long, Long>()
        transactions.filter { it.type == "EXPENSE" }.forEach { t ->
            categoryTotals[t.categoryId] = (categoryTotals[t.categoryId] ?: 0L) + t.amount
        }

        val totalExpense = categoryTotals.values.sum()
        val breakdown = categoryTotals.map { (catId, total) ->
            val cat = categories.find { it.id == catId }
            CategoryBreakdown(
                categoryId = catId,
                categoryName = cat?.name ?: "سایر",
                color = cat?.color ?: 0xFF757575L,
                total = total,
                percentage = if (totalExpense > 0) total.toFloat() / totalExpense else 0f
            )
        }.sortedByDescending { it.total }

        assertEquals(2, breakdown.size)
        assertEquals("خوراک", breakdown[0].categoryName)
        assertEquals(3_000_000L, breakdown[0].total)
        assertEquals(3_000_000L + 500_000L, totalExpense)
    }

    companion object {
        private const val DEBTOR_TYPE = "DEBTOR"
        private const val ALI_NAME = "علی"
    }

    @Test
    fun `debt summary progress calculation`() {
        val loans = listOf(
            createLoan(DEBTOR_TYPE, 5_000_000, 3_000_000, ALI_NAME),
            createLoan("CREDITOR", 10_000_000, 5_000_000, "محمد")
        )

        val debtors = loans.filter { it.type == DEBTOR_TYPE && !it.isSettled }.map { loan ->
            DebtSummary(
                personName = loan.personName,
                originalAmount = loan.originalAmount,
                remainingAmount = loan.remainingAmount,
                type = loan.type,
                progress = if (loan.originalAmount > 0) {
                    1f - (loan.remainingAmount.toFloat() / loan.originalAmount)
                } else 0f
            )
        }

        assertEquals(1, debtors.size)
        assertEquals(ALI_NAME, debtors[0].personName)
        // Progress should be 40% (2M paid out of 5M)
        assertEquals(0.4f, debtors[0].progress, 0.01f)
    }

    @Test
    fun `installment progress calculation`() {
        val installments = listOf(
            createInstallment("قسط ۱", 1_000_000, isPaid = true),
            createInstallment("قسط ۲", 1_000_000, isPaid = false),
            createInstallment("قسط ۳", 1_000_000, isPaid = true)
        )

        val paidCount = installments.count { it.isPaid }
        val totalCount = installments.size

        assertEquals(2, paidCount)
        assertEquals(3, totalCount)
        assertEquals(2f / 3f, paidCount.toFloat() / totalCount, 0.01f)
    }

    @Test
    fun `empty data handling`() {
        val transactions = emptyList<Transaction>()
        val loans = emptyList<Loan>()
        val installments = emptyList<Installment>()

        val monthlyMap = mutableMapOf<String, Long>()
        val categoryTotals = mutableMapOf<Long, Long>()
        val unsettledLoans = loans.filter { !it.isSettled }

        assertEquals(0, monthlyMap.size)
        assertEquals(0, categoryTotals.size)
        assertEquals(0, unsettledLoans.size)
        assertEquals(0, installments.size)
    }

    @Test
    fun `jalali month name mapping`() {
        val monthNames = mapOf(
            1 to "فروردین",
            2 to "اردیبهشت",
            3 to "خرداد",
            4 to "تیر",
            5 to "مرداد",
            6 to "شهریور",
            7 to "مهر",
            8 to "آبان",
            9 to "آذر",
            10 to "دی",
            11 to "بهمن",
            12 to "اسفند"
        )

        assertEquals("فروردین", monthNames[1])
        assertEquals("اسفند", monthNames[12])
        assertEquals(12, monthNames.size)
    }

    @Test
    fun `total debt and credit calculation`() {
        val loans = listOf(
            createLoan("DEBTOR", 5_000_000, 3_000_000, "علی"),
            createLoan("DEBTOR", 2_000_000, 1_000_000, "حسن"),
            createLoan("CREDITOR", 10_000_000, 5_000_000, "محمد"),
            createLoan("CREDITOR", 3_000_000, 0L, "رضا", isSettled = true)
        )

        val unsettledLoans = loans.filter { !it.isSettled }
        val totalDebt = unsettledLoans.filter { it.type == "DEBTOR" }.sumOf { it.remainingAmount }
        val totalCredit = unsettledLoans.filter { it.type == "CREDITOR" }.sumOf { it.remainingAmount }

        assertEquals(4_000_000L, totalDebt) // 3M + 1M
        assertEquals(5_000_000L, totalCredit) // Only unsettled
    }

    @Test
    fun `loan progress percentage`() {
        val loan = createLoan("DEBTOR", 10_000_000, 4_000_000)

        val progress = if (loan.originalAmount > 0) {
            1f - (loan.remainingAmount.toFloat() / loan.originalAmount)
        } else 0f

        // 6M paid out of 10M = 60%
        assertEquals(0.6f, progress, 0.01f)
    }

    @Test
    fun `category percentage calculation`() {
        val expenses = listOf(
            createTransaction("EXPENSE", 600_000, categoryId = 1L),
            createTransaction("EXPENSE", 400_000, categoryId = 2L)
        )

        val total = expenses.sumOf { it.amount }
        val cat1Percentage = expenses.filter { it.categoryId == 1L }.sumOf { it.amount }.toFloat() / total

        assertEquals(1_000_000L, total)
        assertEquals(0.6f, cat1Percentage, 0.01f)
    }
}
