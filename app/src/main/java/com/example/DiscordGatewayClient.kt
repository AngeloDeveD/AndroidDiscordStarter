package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class BotStatus {
    STOPPED,
    CONNECTING,
    RUNNING,
    ERROR
}

object DiscordGatewayClient {
    private const val TAG = "DiscordGatewayClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val _status = MutableStateFlow(BotStatus.STOPPED)
    val status = _status.asStateFlow()

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var token: String = ""
    private var applicationId: String = ""

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private var lastSequence: Int? = null
    private var isIntentionalStop = false

    fun start(context: Context, botToken: String) {
        if (_status.value != BotStatus.STOPPED && _status.value != BotStatus.ERROR) {
            LogManager.log(LogLevel.WARNING, "System", "Бот уже запущен или запускается.")
            return
        }

        token = botToken.trim()
        if (token.isEmpty()) {
            _status.value = BotStatus.ERROR
            LogManager.log(LogLevel.ERROR, "System", "Токен не может быть пустым!")
            return
        }

        isIntentionalStop = false
        _status.value = BotStatus.CONNECTING
        LogManager.log(LogLevel.INFO, "System", "Инициализация Discord соединения...")

        // Force load local commands first
        LuaCommandManager.initializeAndLoad(context)

        connectGateway(context)
    }

    fun stop() {
        if (_status.value == BotStatus.STOPPED) return

        isIntentionalStop = true
        LogManager.log(LogLevel.WARNING, "System", "Выключение бота...")
        cleanup()
        _status.value = BotStatus.STOPPED
        LogManager.log(LogLevel.INFO, "System", "Бот остановлен.")
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null

        try {
            webSocket?.close(1000, "User stopped the bot")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing websocket", e)
        }
        webSocket = null
        client = null
        lastSequence = null
    }

    private fun connectGateway(context: Context) {
        cleanup()

        client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url("wss://gateway.discord.gg/?v=10&encoding=json")
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                LogManager.log(LogLevel.DEBUG, "Gateway", "WebSocket соединение установлено!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleGatewayMessage(context, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                LogManager.log(LogLevel.WARNING, "Gateway", "Discord закрывает соединение: $reason ($code)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                LogManager.log(LogLevel.WARNING, "Gateway", "Соединение закрыто.")
                if (!isIntentionalStop) {
                    attemptReconnect(context)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                LogManager.log(LogLevel.ERROR, "Gateway", "Сбой сети: ${t.localizedMessage}")
                Log.e(TAG, "WS Failure", t)
                if (!isIntentionalStop) {
                    _status.value = BotStatus.ERROR
                    attemptReconnect(context)
                } else {
                    _status.value = BotStatus.STOPPED
                }
            }
        })
    }

    private fun attemptReconnect(context: Context) {
        if (isIntentionalStop) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _status.value = BotStatus.CONNECTING
            LogManager.log(LogLevel.WARNING, "System", "Повторная попытка подключения через 5 секунд...")
            delay(5000)
            if (!isIntentionalStop) {
                connectGateway(context)
            }
        }
    }

    private fun handleGatewayMessage(context: Context, jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val op = json.getInt("op")
            
            if (json.has("s") && !json.isNull("s")) {
                lastSequence = json.getInt("s")
            }

            when (op) {
                10 -> { // Hello payload
                    val d = json.getJSONObject("d")
                    val heartbeatInterval = d.getLong("heartbeat_interval")
                    LogManager.log(LogLevel.DEBUG, "Gateway", "Получено Hello. Интервал таймера: ${heartbeatInterval}мс")
                    
                    startHeartbeat(heartbeatInterval)
                    sendIdentify()
                }
                11 -> { // Heartbeat ACK
                    LogManager.log(LogLevel.DEBUG, "Gateway", "Подтверждение пинга (Heartbeat ACK)")
                }
                0 -> { // Dispatch events (READY, INTERACTION_CREATE, etc.)
                    val t = json.getString("t")
                    val d = json.getJSONObject("d")
                    
                    when (t) {
                        "READY" -> {
                            _status.value = BotStatus.RUNNING
                            val user = d.getJSONObject("user")
                            val username = user.getString("username")
                            applicationId = d.getJSONObject("application").getString("id")
                            
                            LogManager.log(LogLevel.SUCCESS, "Bot", "Бот авторизован как: @$username")
                            LogManager.log(LogLevel.INFO, "Bot", "ID Приложения: $applicationId")

                            // Auto-register slash commands
                            registerSlashCommands(context)
                        }
                        "INTERACTION_CREATE" -> {
                            handleInteraction(d)
                        }
                    }
                }
                9 -> { // Invalid Session
                    LogManager.log(LogLevel.WARNING, "Gateway", "Невалидная сессия. Переподключение...")
                    attemptReconnect(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing gateway message: $jsonStr", e)
        }
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                sendHeartbeat()
            }
        }
    }

    private fun sendHeartbeat() {
        val json = JSONObject().apply {
            put("op", 1)
            put("d", lastSequence ?: JSONObject.NULL)
        }
        webSocket?.send(json.toString())
        LogManager.log(LogLevel.DEBUG, "Gateway", "Отправлен фоновый пинг (Heartbeat)")
    }

    private fun sendIdentify() {
        val properties = JSONObject().apply {
            put("os", "android")
            put("browser", "luabot")
            put("device", "android")
        }
        
        val d = JSONObject().apply {
            put("token", token)
            put("properties", properties)
            put("compress", false)
            put("intents", 513) // GUILDS (1) + GUILD_MESSAGES (512) for general integration
        }
        
        val json = JSONObject().apply {
            put("op", 2)
            put("d", d)
        }
        
        webSocket?.send(json.toString())
        LogManager.log(LogLevel.INFO, "Gateway", "Отправка токена авторизации...")
    }

    /**
     * Bulk overwrite global application commands
     */
    fun registerSlashCommands(context: Context) {
        if (applicationId.isEmpty() || token.isEmpty()) {
            LogManager.log(LogLevel.ERROR, "Register", "Не удалось обновить: бот еще не авторизован")
            return
        }

        scope.launch(Dispatchers.IO) {
            val commandsList = LuaCommandManager.loadAllCommands(context)
            LogManager.log(LogLevel.INFO, "Register", "Отправка ${commandsList.size} команд в Discord...")

            val requestArray = JSONArray()
            for (comm in commandsList) {
                val commJson = JSONObject().apply {
                    put("name", comm.name)
                    put("description", comm.description)
                    put("options", comm.optionsArray)
                }
                requestArray.put(commJson)
            }

            val bodyText = requestArray.toString()
            val url = "https://discord.com/api/v10/applications/$applicationId/commands"

            val request = Request.Builder()
                .url(url)
                .put(bodyText.toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bot $token")
                .build()

            val client = OkHttpClient()
            try {
                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        LogManager.log(LogLevel.SUCCESS, "Register", "Слэш-команды успешно зарегистрированы в Discord!")
                    } else {
                        Log.e(TAG, "Failed command registration: $respBody")
                        LogManager.log(LogLevel.ERROR, "Register", "Ошибка регистрации команд API: Код ${response.code}\n$respBody")
                    }
                }
            } catch (e: IOException) {
                LogManager.log(LogLevel.ERROR, "Register", "Сбой сети при отправке команд: ${e.localizedMessage}")
            }
        }
    }

    private fun handleInteraction(interaction: JSONObject) {
        try {
            val id = interaction.getString("id")
            val token = interaction.getString("token")
            val type = interaction.getInt("type")

            if (type != 2) return // Only handle APPLICATION_COMMAND (slash commands)

            val data = interaction.getJSONObject("data")
            val commandName = data.getString("name").lowercase().trim()

            // Resolve triggering user
            var username = "unknown"
            var userId = "0"
            if (interaction.has("member")) {
                val member = interaction.getJSONObject("member")
                val user = member.getJSONObject("user")
                username = user.getString("username")
                userId = user.getString("id")
            } else if (interaction.has("user")) {
                val user = interaction.getJSONObject("user")
                username = user.getString("username")
                userId = user.getString("id")
            }

            val guildId = if (interaction.has("guild_id")) interaction.getString("guild_id") else null
            val channelId = if (interaction.has("channel_id")) interaction.getString("channel_id") else null

            // Parse option/argument values
            val optionsMap = mutableMapOf<String, Any>()
            if (data.has("options")) {
                val optionsArr = data.getJSONArray("options")
                for (i in 0 until optionsArr.length()) {
                    val opt = optionsArr.getJSONObject(i)
                    val optName = opt.getString("name")
                    val optVal = opt.get("value")
                    optionsMap[optName] = optVal
                }
            }

            scope.launch {
                // Execute command inside the Lua script interpreter
                val result = LuaCommandManager.executeCommand(
                    commandName = commandName,
                    username = username,
                    userId = userId,
                    guildId = guildId,
                    channelId = channelId,
                    options = optionsMap
                )

                // Dispatch callback REST POST back to Discord
                sendInteractionResponse(id, token, result)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling interaction", e)
        }
    }

    private fun sendInteractionResponse(interactionId: String, interactionToken: String, result: ExecutionResult) {
        scope.launch(Dispatchers.IO) {
            val url = "https://discord.com/api/v10/interactions/$interactionId/$interactionToken/callback"

            val flags = if (result.ephemeral) 64 else 0 // 64 is EPHEMERAL flag

            val dataContent = JSONObject().apply {
                put("content", result.content)
                if (flags != 0) {
                    put("flags", flags)
                }
            }

            val bodyJson = JSONObject().apply {
                put("type", 4) // CHANNEL_MESSAGE_WITH_SOURCE
                put("data", dataContent)
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val client = OkHttpClient()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        Log.e(TAG, "Failed interaction response callback: code ${response.code} body: $body")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error on interaction response callback", e)
            }
        }
    }
}
