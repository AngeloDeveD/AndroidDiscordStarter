package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Уровни важности событий при логировании.
 * Используются для категоризации и цветовой подсветки логов в терминале управления.
 */
enum class LogLevel {
    INFO,     // Общая системная информация о состоянии подключения
    WARNING,  // Предупреждения (например, задержка ответа или таймаут)
    ERROR,    // Критические ошибки Gateway, сетевые сбои или ошибки компиляции Lua
    DEBUG,    // Отладочные сообщения веб-сокета (детализация фреймов)
    SUCCESS,  // События успешного подключения/авторизации бота
    BOT       // Сообщения, отсылаемые непосредственно из тела Lua скриптов командой execute
}

/**
 * Модель данных одной записи логов для вывода в консоль.
 *
 * @property id Уникальный идентификатор записи (используется как Compose key в LazyColumn)
 * @property timestamp Форматированное время логирования (HH:mm:ss)
 * @property level Категория логирования для стилизации
 * @property tag Системный компонент, от которого пришел лог
 * @property message Текст события
 */
data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

/**
 * Потокобезопасный объект-одиночка (Singleton) для буферизованной записи
 * и распространения логов выполнения Discord-клиента и Lua-сценариев.
 */
object LogManager {
    // Внутренний поток логов, перезаписываемый при новых событиях
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    // Публичный доступный только для чтения поток для подписки во ViewModel
    val logs = _logs.asStateFlow()
    
    // Счетчик для генерации уникальных ID лог-записей
    private var idCounter = 0L

    // Форматтер времени для префиксов логов
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Создает и добавляет лог-запись в буфер.
     * Ограничивает размер списка последними 500 записями для стабильной производительности UI.
     */
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            id = idCounter++,
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        // Добавляем новый лог в конец списка и удерживаем не более 500 записей
        _logs.value = (_logs.value + entry).takeLast(500)
    }

    /**
     * Сброс всех записанных логов.
     */
    fun clear() {
        _logs.value = emptyList()
    }
}
