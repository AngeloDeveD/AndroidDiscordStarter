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

class BotViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)

    // Discord Bot Token
    private val _token = MutableStateFlow("")
    val token = _token.asStateFlow()

    // Active command list
    private val _commandsList = MutableStateFlow<List<LuaCommand>>(emptyList())
    val commandsList = _commandsList.asStateFlow()

    // Editor state
    private val _selectedCommand = MutableStateFlow<LuaCommand?>(null)
    val selectedCommand = _selectedCommand.asStateFlow()

    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen = _isEditorOpen.asStateFlow()

    // Live gateway status
    val botStatus: StateFlow<BotStatus> = DiscordGatewayClient.status

    // Dynamic work logs
    val logs: StateFlow<List<LogEntry>> = LogManager.logs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Load persist token
        _token.value = prefs.getString("discord_token", "") ?: ""
        
        // Initialize Lua commands directory and read initial scripts
        loadCommands()
    }

    fun saveToken(newToken: String) {
        _token.value = newToken.trim()
        prefs.edit().putString("discord_token", newToken.trim()).apply()
    }

    fun startBot() {
        viewModelScope.launch {
            DiscordGatewayClient.start(context, _token.value)
        }
    }

    fun stopBot() {
        DiscordGatewayClient.stop()
    }

    fun loadCommands() {
        viewModelScope.launch {
            val list = LuaCommandManager.initializeAndLoad(context)
            _commandsList.value = list
        }
    }

    fun syncCommandsToDiscord() {
        DiscordGatewayClient.registerSlashCommands(context)
    }

    fun openCommandEditor(command: LuaCommand?) {
        _selectedCommand.value = command
        _isEditorOpen.value = true
    }

    fun closeCommandEditor() {
        _selectedCommand.value = null
        _isEditorOpen.value = false
    }

    fun saveCommand(name: String, code: String): Boolean {
        val success = LuaCommandManager.saveCommand(context, name, code)
        if (success) {
            loadCommands()
            closeCommandEditor()
        }
        return success
    }

    fun deleteCommand(command: LuaCommand) {
        viewModelScope.launch {
            val success = LuaCommandManager.deleteCommand(context, command)
            if (success) {
                loadCommands()
            }
        }
    }

    fun clearLogs() {
        LogManager.clear()
    }
}
