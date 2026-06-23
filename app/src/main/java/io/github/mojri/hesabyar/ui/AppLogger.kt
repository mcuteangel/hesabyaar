package io.github.mojri.hesabyar.ui

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLogger {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_LOGS = 200

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val level: String,
        val message: String
    ) {
        fun formatted(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            return "${sdf.format(Date(timestamp))} [$level/$tag] $message"
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addLog(tag, "D", message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addLog(tag, "I", message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addLog(tag, "W", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val fullMessage = if (throwable != null) "$message: ${throwable.message}" else message
        addLog(tag, "E", fullMessage)
    }

    private fun addLog(tag: String, level: String, message: String) {
        logs.add(LogEntry(tag = tag, level = level, message = message))
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun getLogsForTag(tag: String): List<LogEntry> = logs.filter { it.tag == tag }

    fun getAiLogs(): List<LogEntry> = logs.filter {
        it.tag in listOf("AiConfigManager", "GeminiParser", "BudgetAdvisor", "AiProvider", "AiAssistantViewModel")
    }

    fun clear() {
        logs.clear()
    }
}
