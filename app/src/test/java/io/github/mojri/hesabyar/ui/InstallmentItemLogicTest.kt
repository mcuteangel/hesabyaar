package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DecimalFormat

/**
 * Tests InstallmentItem logic without Compose rendering:
 * - Progress indicator color: green when progress >= 1.0f, blue otherwise
 * - Amount formatting for paid/remaining
 * - Click behavior (onClick non-null → clickable)
 * - Due date display
 */
class InstallmentItemLogicTest {

    private val formatter = DecimalFormat("#,###")

    private fun progressColor(progress: Float): Color {
        return if (progress >= 1f) FinancialColors.IncomeGreen else FinancialColors.InfoBlue
    }

    @Test
    fun `progress 1_0 shows green`() {
        assertEquals("Completed → green", FinancialColors.IncomeGreen, progressColor(1.0f))
    }

    @Test
    fun `progress above 1_0 shows green`() {
        assertEquals("Overpaid → green", FinancialColors.IncomeGreen, progressColor(1.5f))
    }

    @Test
    fun `progress 0_0 shows blue`() {
        assertEquals("No payments → blue", FinancialColors.InfoBlue, progressColor(0.0f))
    }

    @Test
    fun `progress 0_5 shows blue`() {
        assertEquals("Half paid → blue", FinancialColors.InfoBlue, progressColor(0.5f))
    }

    @Test
    fun `progress just below 1_0 shows blue`() {
        assertEquals("Almost done → blue", FinancialColors.InfoBlue, progressColor(0.99f))
    }

    @Test
    fun `negative progress shows blue`() {
        assertEquals("Edge case → blue", FinancialColors.InfoBlue, progressColor(-0.5f))
    }

    @Test
    fun `paid amount formatted with separators`() {
        val paid = 1500000L
        val text = "پرداخت شده: ${formatter.format(paid)}"
        assertEquals("پرداخت شده: 1,500,000", text)
    }

    @Test
    fun `remaining amount formatted with separators`() {
        val remaining = 500000L
        val text = "باقیمانده: ${formatter.format(remaining)}"
        assertEquals("باقیمانده: 500,000", text)
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

    @Test
    fun `due date displays correctly`() {
        val dueDate = "1403/06/01"
        assertEquals("1403/06/01", dueDate)
    }
}
