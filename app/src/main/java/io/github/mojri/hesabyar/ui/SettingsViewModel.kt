package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs = application.getSharedPreferences("hesabyar_prefs", android.content.Context.MODE_PRIVATE)

    var isDarkMode = mutableStateOf(sharedPrefs.getBoolean("dark_mode", true))
        private set

    fun toggleDarkMode() {
        isDarkMode.value = !isDarkMode.value
        sharedPrefs.edit().putBoolean("dark_mode", isDarkMode.value).apply()
    }

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage = _uiMessage.asSharedFlow()

    fun showMessage(message: String) {
        viewModelScope.launch {
            _uiMessage.emit(message)
        }
    }

    fun getAiLogs(): List<AppLogger.LogEntry> = AppLogger.getAiLogs()
    fun clearLogs() = AppLogger.clear()
}
