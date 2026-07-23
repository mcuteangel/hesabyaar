package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.ui.utils.extractForecastPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the free helper functions extracted from DashboardScreen:
 * - CurrencyFormatter.format: converts rial to display unit
 * - extractForecastPreview: extracts preview text from markdown forecast
 */
class DashboardHelperTest {
  // --- CurrencyFormatter.format tests (default: تومان) ---

  @Test
  fun `format converts rial to toman`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۱۰٬۰۰۰ تومان", CurrencyFormatter.format(100_000L))
  }

  @Test
  fun `format large amount toman`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۵۰۰٬۰۰۰ تومان", CurrencyFormatter.format(5_000_000L))
  }

  @Test
  fun `format zero toman`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۰ تومان", CurrencyFormatter.format(0L))
  }

  @Test
  fun `format small amount toman rounds down`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۵۰ تومان", CurrencyFormatter.format(500L))
  }

  @Test
  fun `format very large amount toman`() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۱۲۳٬۴۵۶٬۷۸۹ تومان", CurrencyFormatter.format(1_234_567_890L))
  }

  // --- CurrencyFormatter.format tests (ریال) ---

  @Test
  fun `format keeps rial value unchanged`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals("۱۰۰٬۰۰۰ ریال", CurrencyFormatter.format(100_000L))
  }

  @Test
  fun `format large amount rial`() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals("۵٬۰۰۰٬۰۰۰ ریال", CurrencyFormatter.format(5_000_000L))
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
    val line1 = "a".repeat(55)
    val line2 = "b".repeat(55)
    val line3 = "c".repeat(55)
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
