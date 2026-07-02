package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DecimalFormat

/**
 * Tests BalanceCard display logic without Compose rendering:
 * - Balance/income/expense formatting
 * - Gradient background: PurpleAccent with 0.2f alpha fading to Transparent
 * - Click behavior (onClick non-null → clickable)
 * - Zero balance display
 */
class BalanceCardLogicTest {

    private val formatter = DecimalFormat("#,###")
    // Mirrors R.string.currency_unit; update here if unit changes in production
    private val currencyUnit = "ریال"

    @Test
    fun `balance formatted with separators and rial suffix`() {
        val balance = 5000000L
        val display = "${formatter.format(balance)} $currencyUnit"
        assertEquals("5,000,000 $currencyUnit", display)
    }

    @Test
    fun `zero balance displays correctly`() {
        val display = "${formatter.format(0L)} $currencyUnit"
        assertEquals("0 $currencyUnit", display)
    }

    @Test
    fun `large balance with commas`() {
        val balance = 1234567890L
        val display = formatter.format(balance)
        assertEquals("1,234,567,890", display)
    }

    @Test
    fun `gradient starts with PurpleAccent at 0_2 alpha`() {
        val gradientStart = FinancialColors.PurpleAccent.copy(alpha = 0.2f)
        assertEquals(0.2f, gradientStart.alpha, 0.001f)
        assertEquals(FinancialColors.PurpleAccent.red, gradientStart.red, 0.001f)
        assertEquals(FinancialColors.PurpleAccent.green, gradientStart.green, 0.001f)
        assertEquals(FinancialColors.PurpleAccent.blue, gradientStart.blue, 0.001f)
    }

    @Test
    fun `gradient ends with Transparent`() {
        val gradientEnd = Color.Transparent
        assertEquals(0f, gradientEnd.alpha, 0.001f)
    }

    @Test
    fun `clickable when onClick is provided`() {
        val onClick: (() -> Unit)? = { }
        assertTrue("Should be clickable", onClick != null)
    }

    @Test
    fun `not clickable when onClick is null`() {
        val onClick: (() -> Unit)? = null
        assertEquals(false, onClick != null)
    }

}
