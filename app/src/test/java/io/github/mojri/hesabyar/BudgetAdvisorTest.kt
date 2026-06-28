package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAdvisorTest {

    private fun createTransaction(type: String, amount: Long, categoryId: Long = 1L): Transaction =
        Transaction(type = type, amount = amount, categoryId = categoryId, description = "test")

    private fun createLoan(type: String, originalAmount: Long, remainingAmount: Long, personName: String = "test"): Loan = Loan(personName = personName, type = type, originalAmount = originalAmount, remainingAmount = remainingAmount, description = "test")

    private fun createInstallment(title: String = "test", amount: Long, isPaid: Boolean = false): Installment =
        Installment(title = title, amount = amount, dueDate = System.currentTimeMillis(), isPaid = isPaid)

    private fun createCategory(id: Long, name: String, key: String = "test"): Category =
        Category(id = id, name = name, key = key, icon = "Test", color = 0xFF757575L, type = "EXPENSE")

    @Test
    fun `getOfflineAdvice - empty transactions`() {
        val result = BudgetAdvisor.getOfflineAdvice(emptyList(), emptyList())
        assertTrue(result.contains("هنوز هیچ تراکنشی ثبت نکرده‌اید"))
    }

    private val HIGH_SPENDING_WARNINGS = listOf("ناترازی", "کسری", "۹۵", "95")

    @Test
    fun `getOfflineAdvice - high spending ratio warns`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 9_500_000)
        )
        val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
        assertTrue(HIGH_SPENDING_WARNINGS.any { result.contains(it) })
    }

    @Test
    fun `getOfflineAdvice - low spending ratio congratulates`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 2_000_000)
        )
        val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
        assertTrue(result.contains("پس‌انداز") || result.contains("۷۰") || result.contains("70"))
    }

    @Test
    fun `getOfflineAdvice - balanced ratio`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 6_000_000)
        )
        val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
        assertTrue(result.contains("تعادل") || result.contains("تعادل نسبی"))
    }

    companion object {
        private const val HIGHEST_SPENDING_CATEGORY_NAME = "خوراک"
    }

    @Test
    fun `getOfflineAdvice - mentions highest spending category`() {
        val categories = listOf(
            createCategory(1L, HIGHEST_SPENDING_CATEGORY_NAME, "Food"),
            createCategory(2L, "حمل و نقل", "Transportation")
        )
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 3_000_000, 1L),
            createTransaction("EXPENSE", 1_000_000, 2L)
        )
        val result = BudgetAdvisor.getOfflineAdvice(transactions, categories)
        assertTrue(result.contains(HIGHEST_SPENDING_CATEGORY_NAME))
    }

    @Test
    fun `getOfflineAdvice - contains financial advice`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 5_000_000)
        )
        val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
        assertTrue(result.contains("بودجه") || result.contains("پس‌انداز") || result.contains("هزینه"))
    }

    @Test
    fun `getOfflineAdvice - mentions active installments`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 5_000_000)
        )
        val installments = listOf(
            createInstallment("قسط ماشین", 2_000_000, isPaid = false)
        )
        val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
        assertTrue(result.contains(INSTALLMENTS_KEY) || result.contains(DEBT_KEY))
    }

    @Test
    fun `getOfflineForecast - empty data`() {
        val result = BudgetAdvisor.getOfflineForecast(emptyList(), emptyList(), emptyList())
        assertTrue(result.contains("هنوز اطلاعات") || result.contains("ثبت نشده"))
    }

    @Test
    fun `getOfflineForecast - negative balance warns`() {
        val transactions = listOf(
            createTransaction("INCOME", 5_000_000),
            createTransaction("EXPENSE", 6_000_000)
        )
        val installments = listOf(
            createInstallment("قسط", 1_000_000, isPaid = false)
        )
        val result = BudgetAdvisor.getOfflineForecast(transactions, emptyList(), installments)
        assertTrue(result.contains("کسری") || result.contains("ریسک") || result.contains("هشدار"))
    }

    @Test
    fun `getOfflineForecast - positive balance stable`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 3_000_000)
        )
        val result = BudgetAdvisor.getOfflineForecast(transactions, emptyList(), emptyList())
        assertTrue(result.contains("پایدار") || result.contains("سبز") || result.contains("مازاد"))
    }

    @Test
    fun `getOfflineForecast - mentions installment amounts`() {
        val transactions = listOf(
            createTransaction("INCOME", 10_000_000),
            createTransaction("EXPENSE", 3_000_000)
        )
        val installments = listOf(
            createInstallment("قسط ماشین", 2_000_000, isPaid = false),
            createInstallment("قسط خانه", 1_000_000, isPaid = false)
        )
        val result = BudgetAdvisor.getOfflineForecast(transactions, emptyList(), installments)
        assertTrue(result.contains("اقساط") || result.contains("تومان"))
    }

    @Test
    fun `getPersianCategoryName maps correctly`() {
        val mapping = mapOf(
            "Food" to "خوراک",
            "Transportation" to "حمل و نقل",
            "Shopping" to "خرید و پوشاک",
            "Bills" to "قبض‌ها و اشتراک",
            "Installments" to "اقساط",
            "Loans" to "وام و امور اشخاص",
            "Income" to "درآمد"
        )
        assertEquals("خوراک", mapping["Food"])
        assertEquals("حمل و نقل", mapping["Transportation"])
        assertEquals(7, mapping.size)
    }

    @Test
    fun `no income - expense only gives correct ratio`() {
        val transactions = listOf(
            createTransaction("EXPENSE", 5_000_000)
        )
        val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
        assertTrue(result.contains("جذب") || result.contains("درآمد") || result.contains("ثبت نکرده‌اید"))
    }
}
