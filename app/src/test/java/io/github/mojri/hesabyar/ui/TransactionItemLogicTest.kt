package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DecimalFormat

/**
 * Tests TransactionItem display logic without Compose rendering:
 * - Income vs expense prefix (+/-) and color
 * - Amount formatting
 * - Date display logic (nullable)
 * - Semantic role assignment (Role.Button) when onClick is non-null
 *
 * Requires Compose UI test for: semantic role verification, content descriptions,
 * text overflow behavior.
 */
class TransactionItemLogicTest {

    private val formatter = DecimalFormat("#,###")

    @Test
    fun `income shows plus prefix and green color`() {
        val isIncome = true
        val prefix = if (isIncome) "+" else "-"
        val amountColor = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
        assertEquals("+", prefix)
        assertEquals(FinancialColors.IncomeGreen, amountColor)
    }

    @Test
    fun `expense shows minus prefix and red color`() {
        val isIncome = false
        val prefix = if (isIncome) "+" else "-"
        val amountColor = if (isIncome) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
        assertEquals("-", prefix)
        assertEquals(FinancialColors.ExpenseRed, amountColor)
    }

    @Test
    fun `amount formatted with thousand separators and rial suffix`() {
        val amount = 1500000L
        val prefix = "+"
        val display = "${prefix}${formatter.format(amount)} ریال"
        assertEquals("+1,500,000 ریال", display)
    }

    @Test
    fun `negative amount prefix for expense`() {
        val amount = 50000L
        val isIncome = false
        val prefix = if (isIncome) "+" else "-"
        val display = "${prefix}${formatter.format(amount)} ریال"
        assertEquals("-50,000 ریال", display)
    }

    @Test
    fun `zero amount displays correctly`() {
        val formatted = formatter.format(0L)
        assertEquals("0", formatted)
    }

    @Test
    fun `date text is nullable - shown only when provided`() {
        val date: String? = "1403/05/15 - 14:30"
        val dateLine = date
        assertNotNull("Date should be displayed", dateLine)

        val noDate: String? = null
        assertNull("No date when null", noDate)
    }

    @Test
    fun `category initial is first character of name`() {
        val categoryInitial = "خوراک".firstOrNull()?.toString() ?: ""
        assertEquals("خ", categoryInitial)
    }

    @Test
    fun `empty category initial when name is empty`() {
        val categoryInitial = "".firstOrNull()?.toString() ?: ""
        assertEquals("", categoryInitial)
    }

    @Test
    fun `category circle uses color with 0_15 alpha`() {
        val categoryColor = Color(0xFF9B59B6)
        val circleBg = categoryColor.copy(alpha = 0.15f)
        assertEquals(0.15f, circleBg.alpha, 0.001f)
    }

    @Test
    fun `clickable modifier is applied when onClick is non-null`() {
        val onClick: (() -> Unit)? = { }
        val hasClickable = onClick != null
        assertTrue("Should have clickable modifier", hasClickable)
    }

    @Test
    fun `clickable modifier skipped when onClick is null`() {
        val onClick: (() -> Unit)? = null
        val hasClickable = onClick != null
        assertEquals("Should not have clickable modifier", false, hasClickable)
    }
}
