package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [BotViewModel] является связующим звеном между пользовательским интерфейсом (UI) и логикой
 * работы Discord-бота. Наследуется от [AndroidViewModel] для безопасного доступа к контексту приложения.
 *
 * Основные задачи ViewModel:
 * 1. Сохранение и загрузка токена авторизации Discord через [SharedPreferences].
 * 2. Управление жизненным циклом бота (запуск, остановка, синхронизация слэш-команд).
 * 3. Работа с Lua-скриптами (создание, редактирование, удаление, чтение списка команд).
 * 4. Предоставление логов терминала в реальном времени.
 */
class BotViewModel(application: Application) : AndroidViewModel(application) {
    // Безопасный доступ к контексту приложения во избежание утечек памяти
    private val context: Context get() = getApplication()
    
    // Инициализация SharedPreferences для хранения токена и настроек бота
    private val prefs = context.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)

    // Discord Bot Token: Хранимый в памяти токен бота, доступный для чтения интерфейсу
    private val _token = MutableStateFlow("")
    val token = _token.asStateFlow()

    // Список проверенных и загруженных Lua-команд для отображения во вкладке "Команды"
    private val _commandsList = MutableStateFlow<List<LuaCommand>>(emptyList())
    val commandsList = _commandsList.asStateFlow()

    // Состояние выбранной для редактирования команды во встроенном редакторе (null, если команда создается с нуля)
    private val _selectedCommand = MutableStateFlow<LuaCommand?>(null)
    val selectedCommand = _selectedCommand.asStateFlow()

    // Флаг, определяющий, открыто ли диалоговое окно встроенного редактора скриптов Lua
    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen = _isEditorOpen.asStateFlow()

    // Текущий статус подключения Discord бота (RUNNING, CONNECTING, STOPPED, ERROR)
    val botStatus: StateFlow<BotStatus> = DiscordGatewayClient.status

    // Динамический поток логов терминала в реальном времени, преобразованный в StateFlow
    val logs: StateFlow<List<LogEntry>> = LogManager.logs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Загрузка сохраненного токена из SharedPreferences при старте приложения
        _token.value = prefs.getString("discord_token", "") ?: ""
        
        // Инициализация директории команд в локальном хранилище устройства и загрузка базовых скриптов
        loadCommands()
    }

    /**
     * Сохраняет обновленный пользователем Discord токен в SharedPreferences и обновляет поток состояния.
     */
    fun saveToken(newToken: String) {
        _token.value = newToken.trim()
        prefs.edit().putString("discord_token", newToken.trim()).apply()
    }

    /**
     * Запускает фоновый процесс подключения бота к серверам Discord по веб-сокетам (Gateway API v10).
     */
    fun startBot() {
        viewModelScope.launch {
            DiscordGatewayClient.start(context, _token.value)
        }
    }

    /**
     * Останавливает сессию веб-сокета, отключает бота из сети Discord и переводит приложение в режим ожидания.
     */
    fun stopBot() {
        DiscordGatewayClient.stop()
    }

    /**
     * Вызывает сканирование локальной директории скриптов и обновляет список доступных Lua-команд.
     */
    fun loadCommands() {
        viewModelScope.launch {
            val list = LuaCommandManager.initializeAndLoad(context)
            _commandsList.value = list
        }
    }

    /**
     * Отправляет HTTP POST запрос в Discord API для регистрации всех локальных Lua слэш-команд на глобальном уровне.
     */
    fun syncCommandsToDiscord() {
        DiscordGatewayClient.registerSlashCommands(context)
    }

    /**
     * Открывает окно редактора Lua кода для указанной команды (или устанавливает null для новой).
     */
    fun openCommandEditor(command: LuaCommand?) {
        _selectedCommand.value = command
        _isEditorOpen.value = true
    }

    /**
     * Закрывает редактор и сбрасывает выбранную команду.
     */
    fun closeCommandEditor() {
        _selectedCommand.value = null
        _isEditorOpen.value = false
    }

    /**
     * Валидирует и записывает отредактированный текст/имя скрипта Lua в локальный файл.
     * Возвращает true, если операция записи успешна.
     */
    fun saveCommand(name: String, code: String): Boolean {
        val success = LuaCommandManager.saveCommand(context, name, code)
        if (success) {
            loadCommands() // Перезагружаем список команд после успешного сохранения
            closeCommandEditor() // Закрываем редактор кода
        }
        return success
    }

    /**
     * Удаляет файл скрипта команды из локального дискового пространства и обновляет список.
     */
    fun deleteCommand(command: LuaCommand) {
        viewModelScope.launch {
            val success = LuaCommandManager.deleteCommand(context, command)
            if (success) {
                loadCommands() // Перезагружаем список, чтобы удаленная команда исчезла из UI
            }
        }
    }

    /**
     * Очищает буфер логов для разгрузки памяти и визуальной чистоты терминала в UI.
     */
    fun clearLogs() {
        LogManager.clear()
    }
}
