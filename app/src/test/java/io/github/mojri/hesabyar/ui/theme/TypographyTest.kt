package io.github.mojri.hesabyar.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that the Material 3 typography configuration defines all 15 styles
 * with the Persian Vazirmatn font family and correct typography metrics.
 */
class TypographyTest {
  @Test
  fun labelSmallStyleConfiguredWithVazirmatn() {
    val style = Typography.labelSmall
    assertEquals("labelSmall must use Vazirmatn", VazirmatnFontFamily, style.fontFamily)
    assertEquals("labelSmall font size must be 11sp", 11.sp, style.fontSize)
    assertEquals("labelSmall line height must be 16sp", 16.sp, style.lineHeight)
    assertEquals("labelSmall font weight must be Medium", FontWeight.Medium, style.fontWeight)
    assertEquals("labelSmall letter spacing must be 0.5sp", 0.5.sp, style.letterSpacing)
  }

  @Test
  fun allFifteenMaterialTypographyStylesUseVazirmatn() {
    val styles =
      listOf(
        "displayLarge" to Typography.displayLarge,
        "displayMedium" to Typography.displayMedium,
        "displaySmall" to Typography.displaySmall,
        "headlineLarge" to Typography.headlineLarge,
        "headlineMedium" to Typography.headlineMedium,
        "headlineSmall" to Typography.headlineSmall,
        "titleLarge" to Typography.titleLarge,
        "titleMedium" to Typography.titleMedium,
        "titleSmall" to Typography.titleSmall,
        "bodyLarge" to Typography.bodyLarge,
        "bodyMedium" to Typography.bodyMedium,
        "bodySmall" to Typography.bodySmall,
        "labelLarge" to Typography.labelLarge,
        "labelMedium" to Typography.labelMedium,
        "labelSmall" to Typography.labelSmall
      )

    assertEquals("Material 3 defines exactly 15 typography styles", 15, styles.size)

    for ((name, style) in styles) {
      assertEquals(
        "Typography style $name must use VazirmatnFontFamily to avoid system font fallback",
        VazirmatnFontFamily,
        style.fontFamily
      )
    }
  }
}
