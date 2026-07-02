package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.GeminiParser
import io.github.mojri.hesabyar.api.MoneyDetector
import io.github.mojri.hesabyar.api.PersianAmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineParserTest {

    @Test
    fun `parse expense with million`() {
        val result = GeminiParser.parseSentenceOffline("امروز مرغ خریدم 5 میلیون")
        assertEquals("EXPENSE", result.type)
        assertEquals(50_000_000L, result.amount)
        assertEquals("Food", result.category)
    }

    @Test
    fun `parse expense with thousand`() {
        val result = GeminiParser.parseSentenceOffline("بنزین خریدم 450 هزار تومان")
        assertEquals("EXPENSE", result.type)
        assertEquals(4_500_000L, result.amount)
        assertEquals("Transportation", result.category)
    }

    @Test
    fun `parse income with million`() {
        val result = GeminiParser.parseSentenceOffline("حقوق گرفتم 20 میلیون")
        assertEquals("INCOME", result.type)
        assertEquals(200_000_000L, result.amount)
        assertEquals("Income", result.category)
    }

    @Test
    fun `parse loan creditor`() {
        val result = GeminiParser.parseSentenceOffline("از علی 5 میلیون قرض گرفتم")
        assertEquals("LOAN_CREDITOR", result.type)
        assertEquals(50_000_000L, result.amount)
        assertEquals("علی", result.personName)
    }

    @Test
    fun `parse loan debtor`() {
        val result = GeminiParser.parseSentenceOffline("به رضا 2 میلیون قرض دادم")
        assertEquals("LOAN_DEBTOR", result.type)
        assertEquals(20_000_000L, result.amount)
    }

    @Test
    fun `parse installment`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین 3 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertEquals(30_000_000L, result.amount)
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
        assertEquals(12_000L, result.amount)
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
        assertEquals(2_000_000L, result.amount)
        assertEquals("Other", result.category)
    }

    @Test
    fun `parse amount with persian numerals`() {
        val result = GeminiParser.parseSentenceOffline("بنزین زدم ۶۰۰ هزار تومان")
        assertEquals("EXPENSE", result.type)
        assertEquals(6_000_000L, result.amount)
    }

    @Test
    fun `parse salon visit`() {
        val result = GeminiParser.parseSentenceOffline("آرایشگاه رفتم ۱۵۰ هزار تومان")
        assertEquals("EXPENSE", result.type)
        assertEquals("Other", result.category)
        assertEquals(1_500_000L, result.amount)
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
        assertTrue("daysFromNow should be positive", (result.daysFromNow ?: 0) > 0)
        assertTrue("daysFromNow should be less than 365", (result.daysFromNow ?: 0) < 365)
    }

    @Test
    fun `installment with mordad month extracts days`() {
        val result = GeminiParser.parseSentenceOffline("قسط خانه 15 مرداد 5 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertNotNull(result.daysFromNow)
        assertTrue("daysFromNow should be positive", (result.daysFromNow ?: 0) > 0)
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
        assertTrue("daysFromNow should be positive", (result.daysFromNow ?: 0) > 0)
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
        assertEquals(100_000_000L, result.amount)
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
        assertEquals(850_000L, result.amount)
    }

    @Test
    fun `parse internet package as expense not income`() {
        val result = GeminiParser.parseSentenceOffline("دیروز بسته ایترنت گرفتم 109 هزار و 800 تومن")
        assertEquals("EXPENSE", result.type)
        assertEquals(1_098_000L, result.amount)
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

    // ============================================================
    // Category inference tests
    // ============================================================

    @Test
    fun `category inference - food keywords`() {
        val foodSentences = listOf(
            "مرغ خریدم 80 هزار تومن",
            "گوشت گرفتم 150 هزار",
            "غذا خریدم 100 هزار",
            "میوه خریدم 50 هزار",
            "رستوران رفتم 200 هزار",
            "نان خریدم 10 هزار",
            "شیر خریدم 15 هزار",
            "چای خریدم 20 هزار",
            "قهوه خریدم 30 هزار",
            "کباب خریدم 120 هزار",
            "پیتزا خریدم 90 هزار"
        )
        foodSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Food for: $sentence", "Food", result.category)
        }
    }

    @Test
    fun `category inference - transportation keywords`() {
        val transportSentences = listOf(
            "بنزین زدم 200 هزار",
            "اسنپ گرفتم 50 هزار",
            "کرایه تاکسی 30 هزار",
            "مترو رفتم 10 هزار"
        )
        transportSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Transportation for: $sentence", "Transportation", result.category)
        }
    }

    @Test
    fun `category inference - shopping keywords`() {
        val shoppingSentences = listOf(
            "لباس خریدم 500 هزار",
            "کفش خریدم 300 هزار",
            "کیف خریدم 200 هزار"
        )
        shoppingSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Shopping for: $sentence", "Shopping", result.category)
        }
    }

    @Test
    fun `category inference - bills keywords`() {
        val billSentences = listOf(
            "قبض برق دادم 200 هزار",
            "قبض گاز پرداخت کردم 100 هزار"
        )
        billSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Bills for: $sentence", "Bills", result.category)
        }
    }

    @Test
    fun `category inference - personal care keywords`() {
        val personalSentences = listOf(
            "اصلاح کردم 100 هزار",
            "آرایشگاه رفتم 150 هزار",
            "عطر خریدم 300 هزار"
        )
        personalSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Other for: $sentence", "Other", result.category)
        }
    }

    @Test
    fun `category inference - education keywords`() {
        val educationSentences = listOf(
            "کلاس ثبت نام کردم 500 هزار",
            "شهریه دانشگاه پرداخت کردم 2 میلیون"
        )
        educationSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Other for: $sentence", "Other", result.category)
        }
    }

    @Test
    fun `category inference - income keywords`() {
        val incomeSentences = listOf(
            "حقوق گرفتم 20 میلیون",
            "درآمد داشتم 5 میلیون",
            "واریز شد 10 میلیون"
        )
        incomeSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Income for: $sentence", "Income", result.category)
        }
    }

    @Test
    fun `category inference - loans keywords`() {
        val loanSentences = listOf(
            "قرض دادم 5 میلیون",
            "قرض گرفتم 10 میلیون"
        )
        loanSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Loans for: $sentence", "Loans", result.category)
        }
    }

    @Test
    fun `category inference - default to Other`() {
        val otherSentences = listOf(
            "چیز عجیبی خریدم 50 هزار"
        )
        otherSentences.forEach { sentence ->
            val result = GeminiParser.parseSentenceOffline(sentence)
            assertEquals("Expected Other for: $sentence", "Other", result.category)
        }
    }

    // ============================================================
    // Confidence calculation tests
    // ============================================================

    @Test
    fun `confidence - multiple factors increase confidence`() {
        val result = GeminiParser.parseSentenceOffline("دیروز مرغ خریدم 80 هزار تومن به علی")
        assertTrue("Confidence should be >= 0.85", result.confidence >= 0.85f)
    }

    @Test
    fun `confidence - amount only gives moderate confidence`() {
        val result = GeminiParser.parseSentenceOffline("500 هزار تومان")
        assertTrue("Confidence should be >= 0.70", result.confidence >= 0.70f)
    }

    @Test
    fun `confidence - no money keywords gives low confidence`() {
        val result = GeminiParser.parseSentenceOffline("متن بدون پول")
        assertTrue("Confidence should be <= 0.65", result.confidence <= 0.65f)
    }

    // ============================================================
    // classifyInstallment paid vs pending tests
    // ============================================================

    @Test
    fun `paid installment with tasvie returns expense`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین را تسویه کردم 3 میلیون")
        assertEquals("EXPENSE", result.type)
        assertEquals("Installments", result.category)
        assertNull(result.daysFromNow)
    }

    @Test
    fun `paid installment with pardakht returns expense`() {
        val result = GeminiParser.parseSentenceOffline("قسط خانه پرداخت کردم 5 میلیون")
        assertEquals("EXPENSE", result.type)
        assertEquals("Installments", result.category)
        assertNull(result.daysFromNow)
    }

    @Test
    fun `paid installment with dadam returns expense`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین دادم 3 میلیون")
        assertEquals("EXPENSE", result.type)
        assertEquals("Installments", result.category)
        assertNull(result.daysFromNow)
    }

    @Test
    fun `pending installment without paid keyword returns installment type`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین 3 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertEquals("Installments", result.category)
        assertNotNull(result.daysFromNow)
    }

    @Test
    fun `installment with variz does not force expense`() {
        val result = GeminiParser.parseSentenceOffline("قسط ماشین واریز شد 3 میلیون")
        assertEquals("INSTALLMENT", result.type)
        assertEquals("Installments", result.category)
    }

    @Test
    fun `category OTHER expense description does not include subject in parentheses`() {
        val result = GeminiParser.parseSentenceOffline("چیز عجیبی خریدم 50 هزار")
        assertEquals("EXPENSE", result.type)
        assertEquals("Other", result.category)
        assertFalse("Description should not contain parentheses with subject", result.description.contains("(چیز عجیبی)"))
        assertTrue("Description should contain the subject directly", result.description.contains("چیز عجیبی"))
    }

    @Test
    fun `category OTHER expense uses base description without formatting`() {
        val result = GeminiParser.parseSentenceOffline("خرج غیرمعمول 100 هزار")
        assertEquals("EXPENSE", result.type)
        assertEquals("Other", result.category)
        assertFalse("Description should not have formatted subject in parentheses", result.description.matches(Regex(".*\\(.*\\).*")))
    }

    @Test
    fun `non-OTHER category expense includes subject in parentheses`() {
        val result = GeminiParser.parseSentenceOffline("مرغ خریدم 80 هزار")
        assertEquals("EXPENSE", result.type)
        assertEquals("Food", result.category)
        assertTrue("Description should include subject in parentheses", result.description.contains("(") && result.description.contains(")"))
    }

    // ============================================================
    // parseJsonResultOffline tests
    // ============================================================

    @Test
    fun `parseJsonResultOffline - valid expense JSON`() {
        val json = """{"type":"EXPENSE","amount":5000000,"category":"Food","description":"مرغ","personName":null,"dateOffsetDays":0}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertEquals("EXPENSE", result!!.type)
        assertEquals(5_000_000L, result.amount)
        assertEquals("Food", result.category)
        assertEquals("مرغ", result.description)
        assertNull(result.personName)
        assertEquals(0, result.dateOffsetDays)
    }

    @Test
    fun `parseJsonResultOffline - valid income JSON`() {
        val json = """{"type":"INCOME","amount":20000000,"category":"Income","description":"حقوق","personName":null,"dateOffsetDays":0}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertEquals("INCOME", result!!.type)
        assertEquals(20_000_000L, result.amount)
        assertEquals("Income", result.category)
    }

    @Test
    fun `parseJsonResultOffline - valid loan with personName`() {
        val json = """{"type":"LOAN_CREDITOR","amount":10000000,"category":"Loans","description":"قرض به علی","personName":"علی","dateOffsetDays":0}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertEquals("LOAN_CREDITOR", result!!.type)
        assertEquals("علی", result.personName)
    }

    @Test
    fun `parseJsonResultOffline - valid installment`() {
        val json = """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","description":"قسط ماشین","personName":null,"dateOffsetDays":0,"daysFromNow":30,"title":"قسط ماشین","notes":"قسط در انتظار پرداخت"}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertEquals("INSTALLMENT", result!!.type)
        assertEquals(3_000_000L, result.amount)
        assertEquals("قسط ماشین", result.title)
    }

    @Test
    fun `parseJsonResultOffline - valid with confidence`() {
        val json = """{"type":"EXPENSE","amount":1000000,"category":"Food","description":"نان","confidence":0.95}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertEquals(0.95f, result!!.confidence, 0.01f)
    }

    @Test
    fun `parseJsonResultOffline - valid without categoryAlias`() {
        val json = """{"type":"EXPENSE","amount":500000,"category":"Other","description":"test"}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertEquals("Other", result!!.category)
    }

    @Test
    fun `parseJsonResultOffline - null input returns null`() {
        assertNull(GeminiParser.parseJsonResultOffline("null"))
    }

    @Test
    fun `parseJsonResultOffline - empty string returns null`() {
        assertNull(GeminiParser.parseJsonResultOffline(""))
    }

    @Test
    fun `parseJsonResultOffline - malformed JSON returns null`() {
        assertNull(GeminiParser.parseJsonResultOffline("{invalid json"))
    }

    @Test
    fun `parseJsonResultOffline - missing required fields returns null`() {
        assertNull(GeminiParser.parseJsonResultOffline("""{"type":"EXPENSE"}"""))
    }

    @Test
    fun `parseJsonResultOffline - random text returns null`() {
        assertNull(GeminiParser.parseJsonResultOffline("just some text"))
    }

    @Test
    fun `parseJsonResultOffline - zero amount returns null`() {
        val json = """{"type":"EXPENSE","amount":0,"category":"Food","description":"test"}"""
        assertNull(GeminiParser.parseJsonResultOffline(json))
    }

    @Test
    fun `parseJsonResultOffline - negative amount returns null`() {
        val json = """{"type":"EXPENSE","amount":-5000,"category":"Food","description":"test"}"""
        assertNull(GeminiParser.parseJsonResultOffline(json))
    }

    @Test
    fun `parseJsonResultOffline - missing amount returns null`() {
        val json = """{"type":"EXPENSE","category":"Food","description":"test"}"""
        assertNull(GeminiParser.parseJsonResultOffline(json))
    }

    @Test
    fun `parseJsonResultOffline - null daysFromNow preserved as null`() {
        val json = """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","description":"قسط","daysFromNow":null}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertNull("Null JSON null should be null, not 0", result!!.daysFromNow)
    }

    @Test
    fun `parseJsonResultOffline - missing daysFromNow preserved as null`() {
        val json = """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","description":"قسط"}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertNull("Missing key should be null", result!!.daysFromNow)
    }

    @Test
    fun `parseJsonResultOffline - non-numeric daysFromNow preserved as null`() {
        val json = """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","description":"قسط","daysFromNow":"abc"}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertNull("Non-numeric string should be null, not 30", result!!.daysFromNow)
    }

    @Test
    fun `parseJsonResultOffline - daysFromNow zero is preserved`() {
        val json = """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","description":"قسط","daysFromNow":0}"""
        val result = GeminiParser.parseJsonResultOffline(json)
        assertNotNull(result)
        assertEquals(0, result!!.daysFromNow)
    }
}
