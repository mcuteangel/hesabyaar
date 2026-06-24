package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.GeminiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineParserTest {

    @Test
    fun `parse expense with million`() {
        val result = GeminiParser.parseSentenceOffline("امروز مرغ خریدم 5 میلیون")
        assertEquals("EXPENSE", result.type)
        assertEquals(5_000_000_000L, result.amount)
        assertEquals("Food", result.category)
    }

    @Test
    fun `parse expense with thousand`() {
        val result = GeminiParser.parseSentenceOffline("بنزین خریدم 450 هزار تومان")
        assertEquals("EXPENSE", result.type)
        assertEquals(450_000_000L, result.amount)
        assertEquals("Transportation", result.category)
    }

    @Test
    fun `parse income with million`() {
        val result = GeminiParser.parseSentenceOffline("حقوق گرفتم 20 میلیون")
        assertEquals("INCOME", result.type)
        assertEquals(20_000_000_000L, result.amount)
        assertEquals("Income", result.category)
    }

    @Test
    fun `parse loan creditor`() {
        val result = GeminiParser.parseSentenceOffline("از علی 5 میلیون قرض گرفتم")
        assertEquals("LOAN_CREDITOR", result.type)
        assertEquals(5_000_000_000L, result.amount)
        assertEquals("علی", result.personName)
    }

    @Test
    fun `parse loan debtor`() {
        val result = GeminiParser.parseSentenceOffline("به رضا 2 میلیون قرض دادم")
        assertEquals("LOAN_DEBTOR", result.type)
        assertEquals(2_000_000_000L, result.amount)
    }

    @Test
    fun `parse installment`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین 3 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertEquals(3_000_000_000L, result.amount)
        assertNotNull(result.title)
    }

    @Test
    fun `parse date offset - yesterday`() {
        val result = GeminiParser.parseSentenceOffline("دیروز 500 هزار خرج کردم")
        assertEquals(-1, result.dateOffsetDays)
    }

    @Test
    fun `parse date offset - tomorrow`() {
        val result = GeminiParser.parseSentenceOffline("فردا 1 میلیون واریز می‌کنم")
        assertEquals(1, result.dateOffsetDays)
    }

    @Test
    fun `parse amount without multiplier`() {
        val result = GeminiParser.parseSentenceOffline("1200 تومان خرج کردم")
        assertEquals(1_200_000L, result.amount)
    }

    @Test
    fun `parse shopping expense`() {
        val result = GeminiParser.parseSentenceOffline("لباس خریدم 800 هزار تومان")
        assertEquals("EXPENSE", result.type)
        assertEquals("Shopping", result.category)
    }

    @Test
    fun `parse bill payment`() {
        val result = GeminiParser.parseSentenceOffline("قبض برق دادم 200 هزار")
        assertEquals("EXPENSE", result.type)
        assertEquals("Bills", result.category)
    }

    @Test
    fun `parse haircut expense with thousand`() {
        val result = GeminiParser.parseSentenceOffline("اصلاح کردم 200 هزار تومن")
        assertEquals("EXPENSE", result.type)
        assertEquals(200_000_000L, result.amount)
        assertEquals("Personal Care", result.category)
    }

    @Test
    fun `parse amount with persian numerals`() {
        val result = GeminiParser.parseSentenceOffline("بنزین زدم ۶۰۰ هزار تومان")
        assertEquals("EXPENSE", result.type)
        assertEquals(600_000_000L, result.amount)
    }

    @Test
    fun `parse salon visit`() {
        val result = GeminiParser.parseSentenceOffline("آرایشگاه رفتم ۱۵۰ هزار تومان")
        assertEquals("EXPENSE", result.type)
        assertEquals("Personal Care", result.category)
        assertEquals(150_000_000L, result.amount)
    }

    @Test
    fun `parse description extracted from sentence`() {
        val result = GeminiParser.parseSentenceOffline("اصلاح کردم 200 هزار تومن")
        assertTrue(result.description.isNotBlank())
    }
}
