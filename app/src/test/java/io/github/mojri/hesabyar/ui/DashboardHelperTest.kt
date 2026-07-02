package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.ui.screens.formatToman
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the free helper functions extracted from DashboardScreen:
 * - formatToman: converts rial to toman (÷1000) with formatting
 * - extractForecastPreview: extracts preview text from markdown forecast
 */
class DashboardHelperTest {

    // Replicate extractForecastPreview logic from DashboardScreen
    private fun extractForecastPreview(forecast: String): String {
        val lines = forecast.lines()
        val contentLines = lines.filter { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith("#")
        }.map { line ->
            line.trim().removePrefix("-").removePrefix("*").trim()
        }.filter { it.isNotEmpty() }

        if (contentLines.isEmpty()) return "گزارش آماده است"

        val preview = contentLines.take(3).joinToString(" | ") { line ->
            if (line.length > 60) line.substring(0, 60).substringBeforeLast(" ") + "..." else line
        }

        return if (preview.length > 150) {
            preview.substring(0, 150).substringBeforeLast(" ") + "..."
        } else {
            preview
        }
    }

    // --- formatToman tests ---

    @Test
    fun `formatToman converts rial to toman`() {
        assertEquals("100 تومان", formatToman(100_000L))
    }

    @Test
    fun `formatToman large amount`() {
        assertEquals("5,000 تومان", formatToman(5_000_000L))
    }

    @Test
    fun `formatToman zero`() {
        assertEquals("0 تومان", formatToman(0L))
    }

    @Test
    fun `formatToman small amount rounds down`() {
        // 500 rial / 1000 = 0.5 → integer truncation = 0
        assertEquals("0 تومان", formatToman(500L))
    }

    @Test
    fun `formatToman very large amount`() {
        assertEquals("1,234,567 تومان", formatToman(1_234_567_890L))
    }

    // --- extractForecastPreview tests ---

    @Test
    fun `extractForecastPreview strips markdown headers`() {
        val forecast = "# گزارش\nمبلغ خرج شده: 5 میلیون\nباقیمانده: 2 میلیون"
        val preview = extractForecastPreview(forecast)
        assertEquals("مبلغ خرج شده: 5 میلیون | باقیمانده: 2 میلیون", preview)
    }

    @Test
    fun `extractForecastPreview strips bullet markers`() {
        val forecast = "- خرج اول\n- خرج دوم\n- خرج سوم"
        val preview = extractForecastPreview(forecast)
        assertEquals("خرج اول | خرج دوم | خرج سوم", preview)
    }

    @Test
    fun `extractForecastPreview takes only first 3 lines`() {
        val forecast = "خط اول\nخط دوم\nخط سوم\nخط چهارم\nخط پنجم"
        val preview = extractForecastPreview(forecast)
        assertEquals("خط اول | خط دوم | خط سوم", preview)
    }

    @Test
    fun `extractForecastPreview returns default for empty content`() {
        val forecast = "# title\n"
        val preview = extractForecastPreview(forecast)
        assertEquals("گزارش آماده است", preview)
    }

    @Test
    fun `extractForecastPreview returns default for blank input`() {
        val preview = extractForecastPreview("")
        assertEquals("گزارش آماده است", preview)
    }

    @Test
    fun `extractForecastPreview truncates long lines at 60 chars`() {
        val longLine = "a".repeat(80)
        val forecast = longLine
        val preview = extractForecastPreview(forecast)
        assertTrue("Should be truncated with ...", preview.endsWith("..."))
        assertTrue("Truncated length <= 63", preview.length <= 63)
    }

    @Test
    fun `extractForecastPreview truncates total preview at 150 chars`() {
        // 3 lines each ~55 chars, joined by " | " → ~170 total, exceeding 150-char limit
        val line1 = "a".repeat(55) // 55 chars
        val line2 = "b".repeat(55) // 55 chars
        val line3 = "c".repeat(55) // 55 chars
        // total before truncation: 55 + 3 + 55 + 3 + 55 = 171
        val forecast = "$line1\n$line2\n$line3"
        val preview = extractForecastPreview(forecast)
        assertTrue("Total preview <= 153 (150 + ...)", preview.length <= 153)
        assertTrue("Should end with ...", preview.endsWith("..."))
    }

    @Test
    fun `extractForecastPreview handles asterisk bullets`() {
        val forecast = "* آیتم اول\n* آیتم دوم"
        val preview = extractForecastPreview(forecast)
        assertEquals("آیتم اول | آیتم دوم", preview)
    }
}
