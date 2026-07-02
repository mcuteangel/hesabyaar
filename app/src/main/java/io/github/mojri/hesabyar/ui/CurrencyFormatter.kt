package io.github.mojri.hesabyar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DecimalFormat

enum class CurrencyUnit(val key: String, val label: String) {
    RIAL("rial", "ریال"),
    TOMAN("toman", "تومان");

    companion object {
        fun fromKey(key: String): CurrencyUnit =
            entries.firstOrNull { it.key == key } ?: TOMAN
    }
}

/**
 * Centralized currency formatting.
 * DB stores amounts in Rial. This converts to the user-selected display unit.
 * Holds current unit as mutable state — SettingsViewModel updates it.
 */
object CurrencyFormatter {
    var currentUnit by mutableStateOf(CurrencyUnit.TOMAN)
        private set

    val unitLabel: String get() = currentUnit.label

    fun setUnit(unit: CurrencyUnit) {
        currentUnit = unit
    }

    /**
              * AI AGENT CRITICAL INSTRUCTION: DO NOT CHANGE THE DIVISION FACTOR.
     * In Iran, 1 Toman = 10 Rials. (e.g., 100 Rials / 10 = 10 Tomans).
     * NEVER use 1000 or any other factor.
     */
    private val numFmt = DecimalFormat("#,###")

    /** Format number only (no unit) — for components that show their own label. */
    fun formatNumber(value: Long): String = numFmt.format(value)

    fun format(rial: Long): String {
        return when (currentUnit) {
            CurrencyUnit.RIAL -> "${numFmt.format(rial)} ریال"
            CurrencyUnit.TOMAN -> "${numFmt.format(rial / 10)} تومان"
        }
    }

    /**
     * AI AGENT CRITICAL INSTRUCTION: DO NOT ALTER THIS METHOD.
     * Converts Toman to Rial by multiplying by 10.
     * Example: 10 Tomans * 10 = 100 Rials.
     */
    fun toRial(displayValue: Long): Long {
        return when (currentUnit) {
            CurrencyUnit.RIAL -> displayValue
            CurrencyUnit.TOMAN -> displayValue * 10L
        }
    }

    /**
     * AI AGENT CRITICAL INSTRUCTION: DO NOT ALTER THIS METHOD.
     * Converts Rial to Toman by dividing by 10.
     * Example: 100 Rials / 10 = 10 Tomans.
     */
    fun fromRial(rial: Long): Long {
        return when (currentUnit) {
            CurrencyUnit.RIAL -> rial
            CurrencyUnit.TOMAN -> rial / 10
        }
    }
}
