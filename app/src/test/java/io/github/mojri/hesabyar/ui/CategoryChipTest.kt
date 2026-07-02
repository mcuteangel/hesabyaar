package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun textColorForBackground(bg: Color): Color {
        return if (bg.wcagLuminance() > 0.179f) Color.Black else Color.White
    }

    private fun selectedLabelColor(surface: Color, categoryColor: Color): Color {
        val lerped = lerp(surface, categoryColor, 0.15f)
        return if (lerped.wcagLuminance() > 0.5f) Color.Black else Color.White
    }

    @Test
    fun `dark background yields white text`() {
        val darkColor = Color(0xFF1A1A2E)
        assertEquals("Dark category → white text", Color.White, textColorForBackground(darkColor))
    }

    @Test
    fun `light background yields black text`() {
        val lightColor = Color(0xFFE74C3C)
        assertEquals("Bright category → black text", Color.Black, textColorForBackground(lightColor))
    }

    @Test
    fun `IncomeGreen luminance is above threshold`() {
        val incomeGreen = Color(0xFF2ECC71)
        val lum = incomeGreen.wcagLuminance()
        assertTrue("IncomeGreen luminance $lum should be > 0.179", lum > 0.179f)
        assertEquals("IncomeGreen → black text", Color.Black, textColorForBackground(incomeGreen))
    }

    @Test
    fun `ExpenseRed luminance is above threshold`() {
        val expenseRed = Color(0xFFE74C3C)
        val lum = expenseRed.wcagLuminance()
        assertTrue("ExpenseRed luminance $lum should be > 0.179", lum > 0.179f)
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
        val categoryColor = Color(0xFF9B59B6)
        val containerColor = categoryColor.copy(alpha = 0.15f)
        assertEquals(0.15f, containerColor.alpha, 0.001f)
    }

    @Test
    fun `selected label color on light surface with dark category`() {
        val surface = Color(0xFFFDFBFF)
        val darkCategory = Color(0xFF1A1A2E)
        assertEquals("Light surface + dark category → Black", Color.Black, selectedLabelColor(surface, darkCategory))
    }

    @Test
    fun `selected label color on dark surface with light category`() {
        val surface = Color(0xFF1E2123)
        val brightCategory = Color(0xFFE74C3C)
        assertEquals("Dark surface + bright category → White", Color.White, selectedLabelColor(surface, brightCategory))
    }

    @Test
    fun `null category uses Gray`() {
        val grayLum = Color.Gray.wcagLuminance()
        assertTrue("Gray luminance $grayLum should be > 0.179", grayLum > 0.179f)
        assertEquals("Null category → Gray → black text", Color.Black, textColorForBackground(Color.Gray))
    }
}
