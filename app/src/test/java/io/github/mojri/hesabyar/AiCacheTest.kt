package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCacheTest {

    private fun createTransaction(type: String, amount: Long, categoryId: Long = 1L): Transaction = Transaction(type = type, amount = amount, categoryId = categoryId, description = "test")

    private fun createLoan(type: String, originalAmount: Long, remainingAmount: Long): Loan =
        Loan(personName = "test", type = type, originalAmount = originalAmount, remainingAmount = remainingAmount, description = "test")

    private fun createInstallment(amount: Long, isPaid: Boolean = false): Installment =
        Installment(title = "test", amount = amount, dueDate = System.currentTimeMillis(), isPaid = isPaid)

    private fun createCategory(id: Long, name: String): Category =
        Category(id = id, name = name, key = "test", icon = "Test", color = 0xFF757575L, type = "EXPENSE")

    private fun createCategory(id: Long, name: String): Category {
        return Category(id = id, name = name, key = TEST_KEY, icon = "Test", color = 0xFF757575L, type = "EXPENSE")
    }
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>
    ): String {
        val txCount = transactions.size
        val txTotal = transactions.sumOf { it.amount }
        val loanCount = loans.size
        val instCount = installments.size
        val catCount = categories.size
        return "$txCount|$txTotal|$loanCount|$instCount|$catCount"
    }

    private fun computeAdviceSignature(
        transactions: List<Transaction>,
        categories: List<Category>
    ): String {
        val txCount = transactions.size
        val txTotal = transactions.sumOf { it.amount }
        val catCount = categories.size
        return "$txCount|$txTotal|$catCount"
    }

    companion object {
        private const val CATEGORY_NAME = "خوراک"
    }

    @Test
    fun `data signature - same data produces same signature`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 3_000_000)
        )
        val loans = listOf(createLoan("DEBTOR", 5_000_000, 3_000_000))
        val installments = listOf(createInstallment(1_000_000))
        val categories = listOf(createCategory(1L, CATEGORY_NAME))

        val sig1 = computeDataSignature(transactions, loans, installments, categories)
        val sig2 = computeDataSignature(transactions, loans, installments, categories)

        assertEquals(sig1, sig2)
    }

    @Test
    fun `data signature - changes when transaction amount changes`() {
        val transactions1 = listOf(createTransaction("INCOME", 10_000_000))
        val transactions2 = listOf(createTransaction("INCOME", 15_000_000))
        val emptyLists = Triple(emptyList<Loan>(), emptyList<Installment>(), emptyList<Category>())

        val sig1 = computeDataSignature(transactions1, emptyLists.first, emptyLists.second, emptyLists.third)
        val sig2 = computeDataSignature(transactions2, emptyLists.first, emptyLists.second, emptyLists.third)

        assertNotEquals(sig1, sig2)
    }

    @Test
    fun `data signature - changes when transaction count changes`() {
        val transactions1 = listOf(createTransaction("INCOME", 10_000_000))
        val transactions2 = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 2_000_000)
        )
        val emptyLists = Triple(emptyList<Loan>(), emptyList<Installment>(), emptyList<Category>())

        val sig1 = computeDataSignature(transactions1, emptyLists.first, emptyLists.second, emptyLists.third)
        val sig2 = computeDataSignature(transactions2, emptyLists.first, emptyLists.second, emptyLists.third)

        assertNotEquals(sig1, sig2)
    }

    @Test
    fun `data signature - changes when loan added`() {
        val transactions = listOf(createTransaction("INCOME", 10_000_000))
        val loans1 = emptyList<Loan>()
        val loans2 = listOf(createLoan("DEBTOR", 5_000_000, 3_000_000))
        val emptyInst = emptyList<Installment>()
        val emptyCat = emptyList<Category>()

        val sig1 = computeDataSignature(transactions, loans1, emptyInst, emptyCat)
        val sig2 = computeDataSignature(transactions, loans2, emptyInst, emptyCat)

        assertNotEquals(sig1, sig2)
    }

    @Test
    fun `data signature - changes when installment added`() {
        val transactions = listOf(createTransaction("INCOME", 10_000_000))
        val emptyLoans = emptyList<Loan>()
        val installments1 = emptyList<Installment>()
        val installments2 = listOf(createInstallment(1_000_000))
        val emptyCat = emptyList<Category>()

        val sig1 = computeDataSignature(transactions, emptyLoans, installments1, emptyCat)
        val sig2 = computeDataSignature(transactions, emptyLoans, installments2, emptyCat)

        assertNotEquals(sig1, sig2)
    }

    @Test
    fun `data signature - empty data produces valid signature`() {
        val sig = computeDataSignature(
            emptyList(), emptyList(), emptyList(), emptyList()
        )
        assertEquals("0|0|0|0|0", sig)
    }

    @Test
    fun `advice signature - same data produces same signature`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 3_000_000)
        )
        val categories = listOf(createCategory(1L, "خوراک"))

        val sig1 = computeAdviceSignature(transactions, categories)
        val sig2 = computeAdviceSignature(transactions, categories)

        assertEquals(sig1, sig2)
    }

    @Test
    fun `advice signature - changes when transaction changes`() {
        val transactions1 = listOf(createTransaction("INCOME", 10_000_000))
        val transactions2 = listOf(createTransaction("INCOME", 20_000_000))
        val categories = listOf(createCategory(1L, "خوراک"))

        val sig1 = computeAdviceSignature(transactions1, categories)
        val sig2 = computeAdviceSignature(transactions2, categories)

        assertNotEquals(sig1, sig2)
    }

    @Test
    fun `advice signature - ignores loans and installments`() {
        val transactions = listOf(createTransaction("INCOME", 10_000_000))
        val categories = listOf(createCategory(1L, "خوراک"))

        val sig1 = computeAdviceSignature(transactions, categories)
        val sig2 = computeAdviceSignature(transactions, categories)

        assertEquals(sig1, sig2)
    }

    @Test
    fun `format last fetch time - zero returns not updated`() {
        val result = formatLastFetchTime(0L)
        assertEquals("هنوز به‌روز نشده", result)
    }

    @Test
    fun `format last fetch time - recent time returns minutes ago`() {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000) - 1000
        val result = formatLastFetchTime(fiveMinutesAgo)
        assertTrue("Expected 'دقیقه پیش' but got: $result", result.contains("دقیقه پیش"))
    }

    @Test
    fun `format last fetch time - one minute returns singular`() {
        val oneMinuteAgo = System.currentTimeMillis() - (60 * 1000) - 1000
        val result = formatLastFetchTime(oneMinuteAgo)
        assertEquals("۱ دقیقه پیش", result)
    }

    @Test
    fun `format last fetch time - just now returns now`() {
        val now = System.currentTimeMillis() - 500
        val result = formatLastFetchTime(now)
        assertEquals("همین الان", result)
    }

    @Test
    fun `format last fetch time - hours ago`() {
        val twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000) - 1000
        val result = formatLastFetchTime(twoHoursAgo)
        assertTrue("Expected 'ساعت پیش' but got: $result", result.contains("ساعت پیش"))
    }

    @Test
    fun `format last fetch time - one hour returns singular`() {
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000) - 1000
        val result = formatLastFetchTime(oneHourAgo)
        assertEquals("۱ ساعت پیش", result)
    }

    private fun formatLastFetchTime(timestamp: Long): String {
        if (timestamp == 0L) return "هنوز به‌روز نشده"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = (diff / 60000).toInt()
        return when {
            minutes < 1 -> "همین الان"
            minutes == 1 -> "۱ دقیقه پیش"
            minutes < 60 -> "$minutes دقیقه پیش"
            else -> {
                val hours = minutes / 60
                if (hours == 1) "۱ ساعت پیش" else "$hours ساعت پیش"
            }
        }
    }

    @Test
    fun `cache key format - pipe separated`() {
        val transactions = listOf(createTransaction("INCOME", 10_000_000))
        val loans = listOf(createLoan("DEBTOR", 5_000_000, 3_000_000))
        val installments = listOf(createInstallment(1_000_000))
        val categories = listOf(createCategory(1L, "خوراک"))

        val sig = computeDataSignature(transactions, loans, installments, categories)
        val parts = sig.split("|")

        assertEquals(5, parts.size)
        assertEquals("1", parts[0]) // txCount
        assertEquals("10000000", parts[1]) // txTotal
        assertEquals("1", parts[2]) // loanCount
        assertEquals("1", parts[3]) // instCount
        assertEquals("1", parts[4]) // catCount
    }

    @Test
    fun `cache invalidated when any data component changes`() {
        val transactions = listOf(createTransaction("INCOME", 10_000_000))
        val loans = emptyList<Loan>()
        val installments = emptyList<Installment>()
        val categories = listOf(createCategory(1L, "خوراک"))

        val originalSig = computeDataSignature(transactions, loans, installments, categories)

        // Change transactions
        val newTransactions = listOf(createTransaction("INCOME", 10_000_000), createTransaction("EXPENSE", 1_000_000))
        val sigAfterTxChange = computeDataSignature(newTransactions, loans, installments, categories)
        assertNotEquals("Signature should change when transactions change", originalSig, sigAfterTxChange)

        // Change loans
        val newLoans = listOf(createLoan("CREDITOR", 2_000_000, 2_000_000))
        val sigAfterLoanChange = computeDataSignature(transactions, newLoans, installments, categories)
        assertNotEquals("Signature should change when loans change", originalSig, sigAfterLoanChange)

        // Change installments
        val newInstallments = listOf(createInstallment(500_000))
        val sigAfterInstChange = computeDataSignature(transactions, loans, newInstallments, categories)
        assertNotEquals("Signature should change when installments change", originalSig, sigAfterInstChange)

        // Change category count
        val newCategories = listOf(createCategory(1L, "خوراک"), createCategory(2L, "حمل و نقل"))
        val sigAfterCatChange = computeDataSignature(transactions, loans, installments, newCategories)
        assertNotEquals("Signature should change when category count changes", originalSig, sigAfterCatChange)
    }
}
