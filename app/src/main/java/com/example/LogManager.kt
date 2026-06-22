package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO, WARNING, ERROR, DEBUG, SUCCESS, BOT
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()
    private var idCounter = 0L

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            id = idCounter++,
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        // Keep at most 500 logs
        _logs.value = (_logs.value + entry).takeLast(500)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
