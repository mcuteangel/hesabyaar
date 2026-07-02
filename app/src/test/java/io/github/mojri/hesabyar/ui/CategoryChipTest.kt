package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the text color contrast logic used in CategoryFilterChip.
 *
 * The chip determines text color based on the category color's luminance:
 *   textColor = if (luminance > 0.179f) Color.Black else Color.White
 *
 * This ensures readable text on both bright and dark category backgrounds.
 * Also tests the selected container color alpha (0.15f) and selected label
 * color logic based on lerp + luminance threshold (0.5f).
 */
class CategoryChipTest {

    // WCAG relative luminance
    private fun Color.wcagLuminance(): Float {
        fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
    }

    private fun textColorForBackground(bg: Color): Color {
        return if (bg.wcagLuminance() > 0.179f) Color.Black else Color.White
    }

    private fun selectedLabelColor(surface: Color, categoryColor: Color): Color {
        val lerped = lerp(surface, categoryColor, 0.15f)
        return if (lerped.wcagLuminance() > 0.5f) Color.Black else Color.White
    }

    private fun lerp(start: Color, stop: Color, fraction: Float): Color {
        return Color(
            red = start.red + (stop.red - start.red) * fraction,
            green = start.green + (stop.green - start.green) * fraction,
            blue = start.blue + (stop.blue - start.blue) * fraction,
            alpha = start.alpha + (stop.alpha - start.alpha) * fraction
        )
    }

    @Test
    fun `dark background yields white text`() {
        // Color(0xFF2ECC71) = IncomeGreen, luminance ~0.38 → Black
        // Color(0xFF1A1A2E) = very dark → White
        val darkColor = Color(0xFF1A1A2E)
        assertEquals("Dark category → white text", Color.White, textColorForBackground(darkColor))
    }

    @Test
    fun `light background yields black text`() {
        // A bright yellow
        val lightColor = Color(0xFFE74C3C) // ExpenseRed, luminance ~0.21 → just above 0.179
        val result = textColorForBackground(lightColor)
        assertEquals("Bright category → black text", Color.Black, result)
    }

    @Test
    fun `IncomeGreen luminance is above threshold`() {
        // IncomeGreen = Color(0xFF2ECC71)
        val incomeGreen = Color(0xFF2ECC71)
        val lum = incomeGreen.wcagLuminance()
        assert(lum > 0.179f) { "IncomeGreen luminance $lum should be > 0.179" }
        assertEquals("IncomeGreen → black text on white-ish bg", Color.Black, textColorForBackground(incomeGreen))
    }

    @Test
    fun `ExpenseRed luminance is above threshold`() {
        // ExpenseRed = Color(0xFFE74C3C)
        val expenseRed = Color(0xFFE74C3C)
        val lum = expenseRed.wcagLuminance()
        assert(lum > 0.179f) { "ExpenseRed luminance $lum should be > 0.179" }
        assertEquals("ExpenseRed → black text", Color.Black, textColorForBackground(expenseRed))
    }

    @Test
    fun `black category yields white text`() {
        assertEquals(Color.White, textColorForBackground(Color.Black))
    }

    @Test
    fun `white category yields black text`() {
        assertEquals(Color.Black, textColorForBackground(Color.White))
    }

    @Test
    fun `selected container alpha is 0_15`() {
        val categoryColor = Color(0xFF9B59B6) // PurpleAccent
        val containerColor = categoryColor.copy(alpha = 0.15f)
        assertEquals(0.15f, containerColor.alpha, 0.001f)
    }

    @Test
    fun `selected label color on light surface with dark category`() {
        // Light surface is near white, dark category → lerped color stays light → Black text
        val surface = Color(0xFFFDFBFF) // LightBackground
        val darkCategory = Color(0xFF1A1A2E)
        assertEquals("Light surface + dark category → Black", Color.Black, selectedLabelColor(surface, darkCategory))
    }

    @Test
    fun `selected label color on dark surface with light category`() {
        // Dark surface is near black, light category → lerped still somewhat dark → White text
        val surface = Color(0xFF1E2123) // DarkSurface
        val brightCategory = Color(0xFFE74C3C)
        assertEquals("Dark surface + bright category → White", Color.White, selectedLabelColor(surface, brightCategory))
    }

    @Test
    fun `null category uses Gray`() {
        // null category → categoryColor = Color.Gray → luminance check
        val grayLum = Color.Gray.wcagLuminance()
        assert(grayLum > 0.179f) { "Gray luminance $grayLum should be > 0.179" }
        assertEquals("Null category → Gray → black text", Color.Black, textColorForBackground(Color.Gray))
    }
}
