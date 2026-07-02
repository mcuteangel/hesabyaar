package io.github.mojri.hesabyar.ui

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test

class CurrencyFormatterTest {

    @After
    fun reset() {
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    }

    // --- toRial ---

    @Test
    fun `toRial toman multiplies by 10`() {
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
        assertEquals(100_000L, CurrencyFormatter.toRial(10_000L))
    }

    @Test
    fun `toRial rial passes through`() {
        CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
        assertEquals(10_000L, CurrencyFormatter.toRial(10_000L))
    }

    @Test
    fun `toRial zero`() {
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
        assertEquals(0L, CurrencyFormatter.toRial(0L))
    }

    // --- fromRial ---

    @Test
    fun `fromRial toman divides by 10`() {
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
        assertEquals(10_000L, CurrencyFormatter.fromRial(100_000L))
    }

    @Test
    fun `fromRial rial passes through`() {
        CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
        assertEquals(100_000L, CurrencyFormatter.fromRial(100_000L))
    }

    @Test
    fun `fromRial truncates on odd rial`() {
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
        assertEquals(5L, CurrencyFormatter.fromRial(55L))
    }

    // --- setUnit state ---

    @Test
    fun `setUnit updates currentUnit`() {
        CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
        assertEquals(CurrencyUnit.RIAL, CurrencyFormatter.currentUnit)
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
        assertEquals(CurrencyUnit.TOMAN, CurrencyFormatter.currentUnit)
    }

    // --- round-trip consistency ---

    @Test
    fun `toRial then fromRial round-trips for toman`() {
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
        val original = 5_500_000L
        val rial = CurrencyFormatter.toRial(original)
        val back = CurrencyFormatter.fromRial(rial)
        assertEquals(original, back)
    }

    @Test
    fun `toRial then fromRial round-trips for rial`() {
        CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
        val original = 5_500_000L
        val rial = CurrencyFormatter.toRial(original)
        val back = CurrencyFormatter.fromRial(rial)
        assertEquals(original, back)
    }

    // --- unitLabel ---

    @Test
    fun `unitLabel matches enum label`() {
        CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
        assertEquals(CurrencyUnit.RIAL.label, CurrencyFormatter.unitLabel)
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
        assertEquals(CurrencyUnit.TOMAN.label, CurrencyFormatter.unitLabel)
    }

    // --- format includes unit ---

    @Test
    fun `format includes rial unit`() {
        CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
        val result = CurrencyFormatter.format(1_000_000L)
        assertTrue(result.endsWith("ریال"))
    }

    @Test
    fun `format includes toman unit`() {
        CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
        val result = CurrencyFormatter.format(10_000_000L)
        assertTrue(result.endsWith("تومان"))
    }

    // --- fromKey ---

    @Test
    fun `fromKey returns correct unit`() {
        assertEquals(CurrencyUnit.RIAL, CurrencyUnit.fromKey("rial"))
        assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey("toman"))
    }

    @Test
    fun `fromKey falls back to TOMAN`() {
        assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey("unknown"))
        assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey(""))
    }
}
