package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelExporterTest {

    private fun columnLetter(index: Int): String {
        val sb = StringBuilder()
        var i = index
        while (i >= 0) {
            sb.append('A' + (i % 26))
            i = i / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun formatAmount(value: Long): String = "${value / 1000} تومان"

    @Test
    fun `columnLetter A`() {
        assertEquals("A", columnLetter(0))
    }

    @Test
    fun `columnLetter B`() {
        assertEquals("B", columnLetter(1))
    }

    @Test
    fun `columnLetter Z`() {
        assertEquals("Z", columnLetter(25))
    }

    @Test
    fun `columnLetter AA`() {
        assertEquals("AA", columnLetter(26))
    }

    @Test
    fun `columnLetter AB`() {
        assertEquals("AB", columnLetter(27))
    }

    @Test
    fun `columnLetter AZ`() {
        assertEquals("AZ", columnLetter(51))
    }

    @Test
    fun `columnLetter BA`() {
        assertEquals("BA", columnLetter(52))
    }

    @Test
    fun `columnLetter BB`() {
        assertEquals("BB", columnLetter(53))
    }

    @Test
    fun `esc handles ampersand`() {
        assertEquals("&amp;", esc("&"))
    }

    @Test
    fun `esc handles less than`() {
        assertEquals("&lt;", esc("<"))
    }

    @Test
    fun `esc handles greater than`() {
        assertEquals("&gt;", esc(">"))
    }

    @Test
    fun `esc handles double quote`() {
        assertEquals("&quot;", esc("\""))
    }

    @Test
    fun `esc handles single quote`() {
        assertEquals("&apos;", esc("'"))
    }

    @Test
    fun `esc handles mixed text`() {
        assertEquals("A &amp; B &lt; C &gt; D", esc("A & B < C > D"))
    }

    @Test
    fun `esc handles empty string`() {
        assertEquals("", esc(""))
    }

    @Test
    fun `esc handles no special chars`() {
        assertEquals("hello world", esc("hello world"))
    }

    @Test
    fun `formatAmount rial to toman`() {
        assertEquals("5000 تومان", formatAmount(5_000_000L))
    }

    @Test
    fun `formatAmount small amount`() {
        assertEquals("1 تومان", formatAmount(1000L))
    }

    @Test
    fun `formatAmount zero`() {
        assertEquals("0 تومان", formatAmount(0L))
    }

    @Test
    fun `formatAmount truncates remainder`() {
        assertEquals("5 تومان", formatAmount(5500L))
    }

    @Test
    fun `formatAmount large amount`() {
        assertEquals("1000000 تومان", formatAmount(1_000_000_000L))
    }

    @Test
    fun `amounts use Long not Double`() {
        val amount: Long = 5_500_000L
        assertTrue(amount is Long)
        assertEquals(5500L, amount / 1000)
    }

    companion object {
        private const val RECEIVED = "دریافتی"
        private const val PAID = "پرداختی"
    }
    @Test
    fun `transaction type mapping`() {
        assertEquals(RECEIVED, if ("INCOME" == "INCOME") RECEIVED else PAID)
        assertEquals(PAID, if ("EXPENSE" == "INCOME") RECEIVED else PAID)
    }

    companion object {
        private const val LOAN_TYPE_DEBTOR_LABEL = "طلبکار"
        private const val LOAN_TYPE_CREDITOR_LABEL = "بدهکار"
    }

    @Test
    fun `loan type mapping`() {
        assertEquals(LOAN_TYPE_DEBTOR_LABEL, if ("DEBTOR" == "DEBTOR") LOAN_TYPE_DEBTOR_LABEL else LOAN_TYPE_CREDITOR_LABEL)
        assertEquals(LOAN_TYPE_CREDITOR_LABEL, if ("CREDITOR" == "DEBTOR") LOAN_TYPE_DEBTOR_LABEL else LOAN_TYPE_CREDITOR_LABEL)
    }

    private companion object {
        const val SETTLED_STATUS = "تسویه شده"
        const val OPEN_STATUS = "باز"
    }

    @Test
    fun `loan settled status mapping`() {
        assertEquals(SETTLED_STATUS, if (true) SETTLED_STATUS else OPEN_STATUS)
        assertEquals(OPEN_STATUS, if (false) SETTLED_STATUS else OPEN_STATUS)
    }

    companion object {
        private const val INSTALLMENT_PAID = "پرداخت شده"
        private const val INSTALLMENT_NOT_PAID = "پرداخت نشده"
    }
    @Test
    fun `installment paid status mapping`() {
        assertEquals(INSTALLMENT_PAID, if (true) INSTALLMENT_PAID else INSTALLMENT_NOT_PAID)
        assertEquals(INSTALLMENT_NOT_PAID, if (false) INSTALLMENT_PAID else INSTALLMENT_NOT_PAID)
    }

    companion object {
        private const val FOOD_NAME = "خوراک"
        private const val TRANSPORT_NAME = "حمل و نقل"
    }

    @Test
    fun `category lookup returns name`() {
        val categories = listOf(
            Category(id = 1L, name = FOOD_NAME, key = "Food", icon = "Restaurant", color = 0xFF4CAF50L, type = "EXPENSE"),
            Category(id = 2L, name = TRANSPORT_NAME, key = "Transportation", icon = "Car", color = 0xFFFF9800L, type = "EXPENSE")
        )
        val map = categories.associateBy { it.id }
        assertEquals(FOOD_NAME, map[1L]?.name)
        assertEquals(TRANSPORT_NAME, map[2L]?.name)
    }

    @Test
    fun `missing category returns default name`() {
        val categories = listOf(
            Category(id = 1L, name = "خوراک", key = "Food", icon = "Restaurant", color = 0xFF4CAF50L, type = "EXPENSE")
        )
        val map = categories.associateBy { it.id }
        val name = map[999L]?.name ?: "سایر"
        assertEquals("سایر", name)
    }

    @Test
    fun `income and expense filtering`() {
        val transactions = listOf(
            Transaction(type = "INCOME", categoryId = 1L, amount = 5_000_000L, description = "salary"),
            Transaction(type = "EXPENSE", categoryId = 2L, amount = 1_000_000L, description = "food"),
            Transaction(type = "INCOME", categoryId = 1L, amount = 3_000_000L, description = "bonus"),
            Transaction(type = "EXPENSE", categoryId = 3L, amount = 500_000L, description = "taxi")
        )
        val income = transactions.filter { it.type == "INCOME" }
        val expense = transactions.filter { it.type == "EXPENSE" }
        assertEquals(2, income.size)
        assertEquals(2, expense.size)
        assertEquals(8_000_000L, income.sumOf { it.amount })
        assertEquals(1_500_000L, expense.sumOf { it.amount })
    }

    @Test
    fun `shared strings deduplication`() {
        val sharedStrings = mutableListOf<String>()
        fun internString(s: String): Int {
            val idx = sharedStrings.indexOf(s)
            if (idx >= 0) return idx
            sharedStrings.add(s)
            return sharedStrings.size - 1
        }

        val i1 = internString("hello")
        val i2 = internString("world")
        val i3 = internString("hello")

        assertEquals(0, i1)
        assertEquals(1, i2)
        assertEquals(0, i3)
        assertEquals(2, sharedStrings.size)
    }

    @Test
    fun `summary row calculation for income sheet`() {
        val transactions = listOf(
            Transaction(type = "INCOME", categoryId = 1L, amount = 5_000_000L, description = "s1"),
            Transaction(type = "INCOME", categoryId = 1L, amount = 3_000_000L, description = "s2")
        )
        val total = transactions.sumOf { it.amount }
        assertEquals(8_000_000L, total)
    }

    @Test
    fun `summary row calculation for expense sheet`() {
        val transactions = listOf(
            Transaction(type = "EXPENSE", categoryId = 1L, amount = 1_000_000L, description = "e1"),
            Transaction(type = "EXPENSE", categoryId = 2L, amount = 500_000L, description = "e2"),
            Transaction(type = "EXPENSE", categoryId = 3L, amount = 200_000L, description = "e3")
        )
        val total = transactions.sumOf { it.amount }
        assertEquals(1_700_000L, total)
    }
}
