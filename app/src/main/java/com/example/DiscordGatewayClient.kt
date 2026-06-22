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

/**
 * Варианты статуса подключения Discord-бота к Gateway API.
 */
enum class BotStatus {
    STOPPED,     // Бот остановлен и выключен
    CONNECTING,  // Выполняется попытка подключения по WebSocket и авторизация
    RUNNING,     // Бот успешно авторизован, подключен и готов обрабатывать команды
    ERROR        // Сбой подключения или ошибка авторизации (например, неверный токен)
}

/**
 * [DiscordGatewayClient] - полностью автономный веб-сокет клиент с прямой поддержкой Discord Gateway API v10.
 * Подключается напрямую к серверам Discord, поддерживает сессию, отправляет пинг-фреймы (Heartbeats)
 * и обрабатывает входящие события (интеракции слэш-команд), передавая их во встроенный Lua-интерпретатор.
 */
object DiscordGatewayClient {
    private const val TAG = "DiscordGatewayClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Реактивный статус подключения для отрисовки статусного бара в UI
    private val _status = MutableStateFlow(BotStatus.STOPPED)
    val status = _status.asStateFlow()

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    
    private var token: String = ""             // Активный токен авторизации Discord-бота
    private var applicationId: String = ""     // ID приложения, извлекаемый из READY-события Discord

    // Фоновые Coroutines задачи для поддержки соединения
    private var heartbeatJob: Job? = null      // Пинг-таймер для удержания связи
    private var reconnectJob: Job? = null      // Задача автоподключения при обрыве сети
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private var lastSequence: Int? = null      // Последний порядковый номер события для Heartbeat
    private var isIntentionalStop = false      // Флаг намеренной остановки бота пользователем

    /**
     * Запуск процесса подключения бота.
     */
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

        // Инициализируем локальные скрипты перед подключением к Discord
        LuaCommandManager.initializeAndLoad(context)

        connectGateway(context)
    }

    /**
     * Намеренная остановка бота, очистка фоновых циклов и отправка закрывающего кадра в веб-сокет.
     */
    fun stop() {
        if (_status.value == BotStatus.STOPPED) return

        isIntentionalStop = true
        LogManager.log(LogLevel.WARNING, "System", "Выключение бота...")
        cleanup()
        _status.value = BotStatus.STOPPED
        LogManager.log(LogLevel.INFO, "System", "Бот остановлен.")
    }

    /**
     * Освобождает ресурсы, останавливает пинги и закрывает веб-сокет.
     */
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

    /**
     * Открывает новое WebSocket подключение к официальному шлюзу Discord Gateway v10.
     */
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
                // Все входящие события от шины Discord перенаправляются в обработчик
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

    /**
     * Плавная попытка переподключения к Gateway при случайном обрыве связи спустя паузу в 5 секунд.
     */
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

    /**
     * Основной парсер пакетов протокола Gateway Discord. Распознает код операции `op`.
     * Подробную диаграмму состояний смотрите на https://discord.com/developers/docs/topics/gateway
     */
    private fun handleGatewayMessage(context: Context, jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val op = json.getInt("op")
            
            // Считываем порядковый номер транзакции для подтверждения доставки в Heartbeat
            if (json.has("s") && !json.isNull("s")) {
                lastSequence = json.getInt("s")
            }

            when (op) {
                10 -> { // Hello payload - отправляется сервером при успешном открытии веб-сокета.
                    val d = json.getJSONObject("d")
                    val heartbeatInterval = d.getLong("heartbeat_interval")
                    LogManager.log(LogLevel.DEBUG, "Gateway", "Получено Hello. Интервал таймера: ${heartbeatInterval}мс")
                    
                    // Запуск таймера для периодических фонового пинга
                    startHeartbeat(heartbeatInterval)
                    // Отправка пакета авторизации в Discord с токеном
                    sendIdentify()
                }
                11 -> { // Heartbeat ACK - сервер Discord успешно принял наш фоновый пинг.
                    LogManager.log(LogLevel.DEBUG, "Gateway", "Подтверждение пинга (Heartbeat ACK)")
                }
                0 -> { // Dispatch events - сервер транслирует события (READY, INTERACTION_CREATE)
                    val t = json.getString("t")
                    val d = json.getJSONObject("d")
                    
                    when (t) {
                        "READY" -> {
                            _status.value = BotStatus.RUNNING
                            val user = d.getJSONObject("user")
                            val username = user.getString("username")
                            // Извлекаем Application ID для REST запросов регистрации команд
                            applicationId = d.getJSONObject("application").getString("id")
                            
                            LogManager.log(LogLevel.SUCCESS, "Bot", "Бот авторизован как: @$username")
                            LogManager.log(LogLevel.INFO, "Bot", "ID Приложения: $applicationId")

                            // Автоматическая заливка/синхронизация локальных Lua слэш-команд в Discord API
                            registerSlashCommands(context)
                        }
                        "INTERACTION_CREATE" -> {
                            // Событие вызова слэш-команды юзером в одном из каналов
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

    /**
     * Создает бесконечный корутин-цикл для постоянной отправки Heartbeat пакетов.
     */
    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                sendHeartbeat()
            }
        }
    }

    /**
     * Отправляет структуру Heartbeat во избежание закрытия веб-сокета сервером Discord по таймауту.
     */
    private fun sendHeartbeat() {
        val json = JSONObject().apply {
            put("op", 1)
            put("d", lastSequence ?: JSONObject.NULL)
        }
        webSocket?.send(json.toString())
        LogManager.log(LogLevel.DEBUG, "Gateway", "Отправлен фоновый пинг (Heartbeat)")
    }

    /**
     * Отправляет структуру IDENTIFY (код операции op = 2) с настройками авторизации,
     * токеном и битовыми флагами (Intents / Намерения).
     */
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
            // Битовая маска: GUILDS (1) + GUILD_MESSAGES (512)
            put("intents", 513) 
        }
        
        val json = JSONObject().apply {
            put("op", 2)
            put("d", d)
        }
        
        webSocket?.send(json.toString())
        LogManager.log(LogLevel.INFO, "Gateway", "Отправка токена авторизации...")
    }

    /**
     * Отправляет HTTP PUT запрос для массовой перезаписи (Bulk Overwrite) глобальных команд бота.
     * Загружает все локальные Lua-файлы, форматирует JSON для Discord REST API и заливает их.
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

    /**
     * Извлекает имя юзера, ID, опции и тип вызываемой команды из интеракции "INTERACTION_CREATE".
     * Затем передает на исполнение в Lua-движок и возвращает ответ обратно в Discord.
     */
    private fun handleInteraction(interaction: JSONObject) {
        try {
            val id = interaction.getString("id")
            val token = interaction.getString("token")
            val type = interaction.getInt("type")

            if (type != 2) return // Обрабатываем только APPLICATION_COMMAND слэш-интеракции

            val data = interaction.getJSONObject("data")
            val commandName = data.getString("name").lowercase().trim()

            // Парсим объект пользователя (member для публичных серверов, либо user для личных переписок)
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

            // Маппим аргументы (options) переданные пользователем внутри Discord
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
                // Исполняем вызов во встроенном песочнице-ранее скомпилированном Lua коде
                val result = LuaCommandManager.executeCommand(
                    commandName = commandName,
                    username = username,
                    userId = userId,
                    guildId = guildId,
                    channelId = channelId,
                    options = optionsMap
                )

                // Отправляем результат выполнения Lua команды обратно в Discord REST API (Interaction Callback)
                sendInteractionResponse(id, token, result)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling interaction", e)
        }
    }

    /**
     * Вещает HTTP-ответ Interaction Callback обратно по HTTP протоколу в Discord.
     * Отвечает типом 4 (CHANNEL_MESSAGE_WITH_SOURCE — моментальное текстовое сообщение).
     */
    private fun sendInteractionResponse(interactionId: String, interactionToken: String, result: ExecutionResult) {
        scope.launch(Dispatchers.IO) {
            val url = "https://discord.com/api/v10/interactions/$interactionId/$interactionToken/callback"

            // Битовый флаг 64 преобразует сообщение в режим "ephemeral" (видно только инициатору)
            val flags = if (result.ephemeral) 64 else 0 

            val dataContent = JSONObject().apply {
                put("content", result.content)
                if (result.embeds != null && result.embeds.length() > 0) {
                    put("embeds", result.embeds)
                }
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
