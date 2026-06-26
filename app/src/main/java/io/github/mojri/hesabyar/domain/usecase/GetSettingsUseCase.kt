package io.github.mojri.hesabyar.domain.usecase

import android.content.SharedPreferences
import io.github.mojri.hesabyar.ui.AppLogger

class GetSettingsUseCase(
    private val sharedPrefs: SharedPreferences
) {
    fun isDarkMode(): Boolean = sharedPrefs.getBoolean("dark_mode", true)

    fun setDarkMode(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun getAiLogs(): List<AppLogger.LogEntry> = AppLogger.getAiLogs()

    fun clearLogs() = AppLogger.clear()
}
