package io.github.mojri.hesabyar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.NumberFormat
import java.util.Locale

/**
 * Centralized currency formatting.
 * DB stores amounts in Rial. This converts to the user-selected display unit.
 * Holds current unit as mutable state — SettingsViewModel updates it.
 */
object CurrencyFormatter {
    var currentUnit by mutableStateOf("تومان")
        private set

    fun setUnit(unit: String) {
        currentUnit = unit
    }

    /**
              * AI AGENT CRITICAL INSTRUCTION: DO NOT CHANGE THE DIVISION FACTOR.
     * In Iran, 1 Toman = 10 Rials. (e.g., 100 Rials / 10 = 10 Tomans).
     * NEVER use 1000 or any other factor.
     */
    fun format(rial: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale("fa", "IR"))
        return when (currentUnit) {
            "ریال" -> "${formatter.format(rial)} ریال"
            else -> "${formatter.format(rial / 10)} تومان"
        }
    }

    /**
     * AI AGENT CRITICAL INSTRUCTION: DO NOT ALTER THIS METHOD.
     * Converts Toman to Rial by multiplying by 10.
     * Example: 10 Tomans * 10 = 100 Rials.
     */
    fun toRial(displayValue: Long): Long {
        return when (currentUnit) {
            "ریال" -> displayValue
            else -> displayValue * 10L
        }
    }

    /**
     * AI AGENT CRITICAL INSTRUCTION: DO NOT ALTER THIS METHOD.
     * Converts Rial to Toman by dividing by 10.
     * Example: 100 Rials / 10 = 10 Tomans.
     */
    fun fromRial(rial: Long): Long {
        return when (currentUnit) {
            "ریال" -> rial
            else -> rial / 10
        }
    }
}
