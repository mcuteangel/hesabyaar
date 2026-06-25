package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.GeminiParser
import io.github.mojri.hesabyar.api.MoneyDetector
import io.github.mojri.hesabyar.api.PersianAmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `installment description is future-oriented not paid`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین 3 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertEquals("قسط آینده", result.description)
    }

    @Test
    fun `installment notes indicate pending status`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین 3 میلیون")
        assertEquals("قسط در انتظار پرداخت", result.notes)
    }

    @Test
    fun `installment with specific jalali date calculates correct days`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین 25 تیر 10 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertNotNull(result.daysFromNow)
        assertTrue("daysFromNow should be positive", result.daysFromNow!! > 0)
        assertTrue("daysFromNow should be less than 365", result.daysFromNow!! < 365)
    }

    @Test
    fun `installment with mordad month extracts days`() {
        val result = GeminiParser.parseSentenceOffline("قسط خانه 15 مرداد 5 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertNotNull(result.daysFromNow)
        assertTrue("daysFromNow should be positive", result.daysFromNow!! > 0)
    }

    @Test
    fun `installment without specific date defaults to 30`() {
        val result = GeminiParser.parseSentenceOffline("قسط جدید 2 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertEquals(30, result.daysFromNow)
    }

    @Test
    fun `installment with persian numerals in date`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین ۱۰ تیر ۸ میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertNotNull(result.daysFromNow)
        assertTrue("daysFromNow should be positive", result.daysFromNow!! > 0)
    }

    @Test
    fun `installment title is extracted correctly`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین 25 تیر 10 میلیون")
        assertEquals("قسط ماشین", result.title)
    }

    @Test
    fun `installment for mortgage loan extracts correct title`() {
        val result = GeminiParser.parseSentenceOffline("قسط وام مسکن 10 مرداد 5 میلیون")
        assertEquals("قسط وام مسکن", result.title)
    }

    @Test
    fun `installment amount is correct`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین 25 تیر 10 میلیون")
        assertEquals(10_000_000_000L, result.amount)
    }

    // ============================================================
    // MoneyDetector tests
    // ============================================================

    @Test
    fun `MoneyDetector - detects unit words`() {
        assertTrue(MoneyDetector.containsMoney("5 میلیون"))
        assertTrue(MoneyDetector.containsMoney("450 هزار"))
        assertTrue(MoneyDetector.containsMoney("1200 تومان"))
        assertTrue(MoneyDetector.containsMoney("200 تومن"))
        assertTrue(MoneyDetector.containsMoney("3 میلیارد"))
    }

    @Test
    fun `MoneyDetector - detects context keywords`() {
        assertTrue(MoneyDetector.containsMoney("لباس خریدم 5000"))
        assertTrue(MoneyDetector.containsMoney("حقوق گرفتم 20000"))
        assertTrue(MoneyDetector.containsMoney("قرض دادم 1000"))
    }

    @Test
    fun `MoneyDetector - rejects non-money sentences`() {
        assertFalse(MoneyDetector.containsMoney("ساعت 5 و 40 دقیقه"))
        assertFalse(MoneyDetector.containsMoney("رمز کارت 1234"))
        assertFalse(MoneyDetector.containsMoney("کد تایید 567890"))
        assertFalse(MoneyDetector.containsMoney("امروز هوا خوبه"))
    }

    // ============================================================
    // PersianAmountParser - explicit units
    // ============================================================

    @Test
    fun `PersianAmountParser - single million`() {
        assertEquals(5_000_000L, PersianAmountParser.parseAmount("5 میلیون"))
    }

    @Test
    fun `PersianAmountParser - million and thousand explicit`() {
        assertEquals(5_400_000L, PersianAmountParser.parseAmount("5 میلیون و 400 هزار"))
    }

    @Test
    fun `PersianAmountParser - billion million thousand explicit`() {
        assertEquals(1_140_300_000L, PersianAmountParser.parseAmount("1 میلیارد و 140 میلیون و 300 هزار"))
    }

    @Test
    fun `PersianAmountParser - single thousand`() {
        assertEquals(450_000L, PersianAmountParser.parseAmount("450 هزار"))
    }

    @Test
    fun `PersianAmountParser - number with toman unit`() {
        assertEquals(1200L, PersianAmountParser.parseAmount("1200 تومان"))
    }

    @Test
    fun `PersianAmountParser - number with tuuman unit`() {
        assertEquals(200L, PersianAmountParser.parseAmount("200 تومن"))
    }

    @Test
    fun `PersianAmountParser - persian digits with thousand`() {
        assertEquals(600_000L, PersianAmountParser.parseAmount("۶۰۰ هزار تومان"))
    }

    @Test
    fun `PersianAmountParser - compound three units with explicit`() {
        assertEquals(3_250_000_000L, PersianAmountParser.parseAmount("3 میلیارد و 250 میلیون"))
    }

    @Test
    fun `PersianAmountParser - number followed by million tuman`() {
        assertEquals(5_000_000L, PersianAmountParser.parseAmount("5 میلیون تومان"))
    }

    @Test
    fun `PersianAmountParser - persian numerals explicit units`() {
        assertEquals(5_400_000L, PersianAmountParser.parseAmount("۵ میلیون و ۴۰۰ هزار"))
    }

    @Test
    fun `PersianAmountParser - persian numerals billion and million`() {
        assertEquals(3_025_000_000L, PersianAmountParser.parseAmount("۳ میلیارد و ۲۵ میلیون"))
    }

    // ============================================================
    // PersianAmountParser - shorthand (needs context keyword)
    // ============================================================

    @Test
    fun `PersianAmountParser - shorthand two parts in context`() {
        assertEquals(5_400_000L, PersianAmountParser.parseAmount("لباس خریدم 5 و 400"))
    }

    @Test
    fun `PersianAmountParser - shorthand three parts in context`() {
        assertEquals(1_140_300_000L, PersianAmountParser.parseAmount("به علی 1 و 140 و 300 قرض دادم"))
    }

    @Test
    fun `PersianAmountParser - shorthand two parts variant in context`() {
        assertEquals(12_050_000L, PersianAmountParser.parseAmount("حقوق گرفتم 12 و 50"))
    }

    @Test
    fun `PersianAmountParser - shorthand without context returns zero`() {
        assertEquals(0L, PersianAmountParser.parseAmount("5 و 400"))
    }

    @Test
    fun `PersianAmountParser - bare number without context returns zero`() {
        assertEquals(0L, PersianAmountParser.parseAmount("900"))
    }

    @Test
    fun `PersianAmountParser - large bare number without context returns zero`() {
        assertEquals(0L, PersianAmountParser.parseAmount("5000000"))
    }

    // ============================================================
    // PersianAmountParser - no-money sentences return zero
    // ============================================================

    @Test
    fun `PersianAmountParser - time sentence returns zero`() {
        assertEquals(0L, PersianAmountParser.parseAmount("ساعت 5 و 40 دقیقه"))
    }

    @Test
    fun `PersianAmountParser - card pin returns zero`() {
        assertEquals(0L, PersianAmountParser.parseAmount("رمز کارت 1234"))
    }

    @Test
    fun `PersianAmountParser - verification code returns zero`() {
        assertEquals(0L, PersianAmountParser.parseAmount("کد تایید 567890"))
    }

    // ============================================================
    // PersianAmountParser - mixed sentences
    // ============================================================

    @Test
    fun `PersianAmountParser - mixed sentence extracts amount`() {
        assertEquals(5_400_000L, PersianAmountParser.parseAmount("لباس خریدم 5 میلیون و 400 هزار"))
    }

    @Test
    fun `PersianAmountParser - normalize arabic indic digits`() {
        assertEquals(5_400_000L, PersianAmountParser.parseAmount("لباس خریدم ٥ میلیون و ٤٠٠ هزار"))
    }

    @Test
    fun `PersianAmountParser - normalize with thousand separators`() {
        assertEquals(5_400_000L, PersianAmountParser.parseAmount("لباس خریدم ۵٬۴۰۰٬۰۰۰ تومان"))
    }

    @Test
    fun `PersianAmountParser - shorthand disabled returns zero for bare`() {
        val result = PersianAmountParser.parseAmount("5 و 400", shorthandMode = false)
        assertEquals(0L, result)
    }

    @Test
    fun `PersianAmountParser - 5 million 400 thousand without va`() {
        assertEquals(5_400_000L, PersianAmountParser.parseAmount("5 میلیون 400 هزار"))
    }

    @Test
    fun `PersianAmountParser - soda 85 thousand toman`() {
        val toman = PersianAmountParser.parseAmount("نوشابه گرفتم 85 هزار تومن")
        assertEquals(85_000L, toman)
    }

    @Test
    fun `PersianAmountParser - internet package 109800 toman`() {
        val toman = PersianAmountParser.parseAmount("دیروز بسته ایترنت گرفتم 109 هزار و 800 تومن")
        assertEquals(109_800L, toman)
    }

    @Test
    fun `parse soda purchase as expense not income`() {
        val result = GeminiParser.parseSentenceOffline("نوشابه گرفتم 85 هزار تومن")
        assertEquals("EXPENSE", result.type)
        assertEquals(85_000_000L, result.amount)
    }

    @Test
    fun `parse internet package as expense not income`() {
        val result = GeminiParser.parseSentenceOffline("دیروز بسته ایترنت گرفتم 109 هزار و 800 تومن")
        assertEquals("EXPENSE", result.type)
        assertEquals(109_800_000L, result.amount)
    }

    @Test
    fun `income description includes subject`() {
        val result = GeminiParser.parseSentenceOffline("بابت فروش پرتقال ها 200 هزار تومن گرفتم")
        assertEquals("INCOME", result.type)
        assertTrue("Description should mention subject", result.description.contains("پرتقال"))
    }

    @Test
    fun `expense description includes subject`() {
        val result = GeminiParser.parseSentenceOffline("بسته اینترنت خریدم 100 هزار تومن")
        assertEquals("EXPENSE", result.type)
        assertTrue("Description should mention subject", result.description.contains("بسته اینترنت"))
    }

    @Test
    fun `expense description for food includes item`() {
        val result = GeminiParser.parseSentenceOffline("مرغ خریدم 80 هزار تومن")
        assertEquals("EXPENSE", result.type)
        assertTrue("Description should mention food item", result.description.contains("مرغ"))
    }

    @Test
    fun `soda purchase description includes soda`() {
        val result = GeminiParser.parseSentenceOffline("نوشابه خریدم 85 هزار تومن")
        assertEquals("EXPENSE", result.type)
        assertTrue("Description should mention نوشابه", result.description.contains("نوشابه"))
    }

    @Test
    fun `soda with time word excludes time from subject`() {
        val result = GeminiParser.parseSentenceOffline("دیشب نوشابه گرفتم")
        assertEquals("EXPENSE", result.type)
        assertTrue("Description should not contain دیشب", !result.description.contains("دیشب"))
        assertTrue("Description should contain نوشابه", result.description.contains("نوشابه"))
    }
}
