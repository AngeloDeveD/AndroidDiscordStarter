package com.example

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

/**
 * Модель данных скомпилированной Lua-команды.
 *
 * @property name Имя команды в нижнем регистре (используется для `/имя_команбы` в Discord)
 * @property description Описание команды, отображаемое внутри Discord интерфейса
 * @property optionsArray JSON-список аргументов команды (опций), зарегистрированный в Discord API
 * @property filePath Локальный абсолютный путь к файлу скрипта `.lua`
 * @property fileContent Исходный код скрипта для динамической компиляции при исполнении
 */
data class LuaCommand(
    val name: String,
    val description: String,
    val optionsArray: JSONArray,
    val filePath: String,
    val fileContent: String
)

/**
 * Объект-менеджер, инкапсулирующий всю бизнес-логику работы со сценариями Lua.
 * Отвечает за:
 * 1. Создание стандартных команд при первом запуске (ping, calc, coinflip, roll).
 * 2. Парсинг структуры Lua-таблиц (глобальная таблица `command`).
 * 3. Динамическое выполнение функции `execute(interaction)` в изолированной виртуальной JVM Lua-машине (Luaj).
 * 4. Сохранение и удаление файлов `.lua`.
 */
object LuaCommandManager {
    private const val TAG = "LuaCommandManager"
    private const val COMMANDS_DIR = "commands" // Название поддиректории для хранения скриптов

    private val httpClient = okhttp3.OkHttpClient()
    private var statePrefs: android.content.SharedPreferences? = null

    // Хеш-карта загруженных и прошедших валидацию команд, ключ - имя команды
    private val _commands = mutableMapOf<String, LuaCommand>()
    val commands: Map<String, LuaCommand> get() = _commands

    /**
     * Инициализирует директорию на диске, генерирует дефолтные файлы скриптов (если папка пуста),
     * считывает и компилирует все обнаруженные Lua-сценарии.
     */
    fun initializeAndLoad(context: Context): List<LuaCommand> {
        statePrefs = context.getSharedPreferences("lua_scripts_state", Context.MODE_PRIVATE)
        val dir = getCommandsDir(context)
        LogManager.log(LogLevel.INFO, "System", "Загрузка Lua-команд из: ${dir.absolutePath}")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        // Если в директории отсутствуют файлы скриптов, создаем дефолтные команды
        val files = dir.listFiles()
        if (files == null || files.isEmpty()) {
            createDefaultCommands(dir)
        }

        return loadAllCommands(context)
    }

    /**
     * Возвращает путь к директории хранения команд (внешнее хранилище или внутренняя папка данных приложения).
     */
    fun getCommandsDir(context: Context): File {
        return context.getExternalFilesDir(COMMANDS_DIR) ?: File(context.filesDir, COMMANDS_DIR)
    }

    /**
     * Считывает все файлы с расширением `.lua` из файловой директории,
     * валидирует их синтаксис структуру и обновляет внутренний кэш команд.
     */
    fun loadAllCommands(context: Context): List<LuaCommand> {
        val dir = getCommandsDir(context)
        val files = dir.listFiles { _, name -> name.endsWith(".lua") } ?: emptyArray()

        _commands.clear()
        val loadedList = mutableListOf<LuaCommand>()

        for (file in files) {
            try {
                val content = file.readText()
                val command = parseLuaCommand(file.absolutePath, content)
                if (command != null) {
                    _commands[command.name] = command
                    loadedList.add(command)
                    LogManager.log(LogLevel.SUCCESS, "LuaLoader", "Загружена команда: /${command.name} - ${command.description}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка чтения ${file.name}", e)
                LogManager.log(LogLevel.ERROR, "LuaLoader", "Ошибка в файле ${file.name}: ${e.localizedMessage}")
            }
        }
        return loadedList
    }

    /**
     * Компилирует и парсит структуру скрипта Lua. Считывает глобальную таблицу `command`
     * для извлечения метаданных (name, description, options, choices).
     */
    private fun parseLuaCommand(filePath: String, content: String): LuaCommand? {
        try {
            // Создаем стандартную среду Lua
            val globals = JsePlatform.standardGlobals()
            // Компилируем и запускаем скрипт, чтобы объявить глобальные переменные и функции
            val chunk = globals.load(content)
            chunk.call()

            // Проверяем наличие таблицы "command"
            val commandTable = globals.get("command")
            if (!commandTable.istable()) {
                val fileName = File(filePath).name
                LogManager.log(LogLevel.WARNING, "LuaLoader", "В файле $fileName не найдена глобальная таблица 'command'")
                return null
            }

            val name = commandTable.get("name").tojstring()
            val description = commandTable.get("description").tojstring()

            if (name.isBlank()) {
                val fileName = File(filePath).name
                LogManager.log(LogLevel.WARNING, "LuaLoader", "В файле $fileName поле command.name пусто")
                return null
            }

            // Парсим аргументы (options) из таблицы Lua в JSONArray для последующего запроса к Discord REST
            val optionsArray = JSONArray()
            val optionsVal = commandTable.get("options")
            if (optionsVal.istable()) {
                val length = optionsVal.length()
                for (i in 1..length) {
                    val optionVal = optionsVal.get(i)
                    if (optionVal.istable()) {
                        val optionObj = JSONObject()
                        optionObj.put("name", optionVal.get("name").tojstring())
                        optionObj.put("description", optionVal.get("description").tojstring())
                        optionObj.put("type", optionVal.get("type").toint())
                        optionObj.put("required", optionVal.get("required").toboolean())

                        // Парсим варианты выбора (choices) для аргументов, если они объявлены
                        val choicesVal = optionVal.get("choices")
                        if (choicesVal.istable()) {
                            val choicesArray = JSONArray()
                            val choicesLen = choicesVal.length()
                            for (j in 1..choicesLen) {
                                val choiceVal = choicesVal.get(j)
                                if (choiceVal.istable()) {
                                    val choiceObj = JSONObject()
                                    choiceObj.put("name", choiceVal.get("name").tojstring())
                                    val choiceValVal = choiceVal.get("value")
                                    // Пытаемся сохранить оригинальный тип данных выбора
                                    if (choiceValVal.isint() || choiceValVal.islong()) {
                                        choiceObj.put("value", choiceValVal.tolong())
                                    } else if (choiceValVal.isnumber()) {
                                        choiceObj.put("value", choiceValVal.todouble())
                                    } else {
                                        choiceObj.put("value", choiceValVal.tojstring())
                                    }
                                    choicesArray.put(choiceObj)
                                }
                            }
                            optionObj.put("choices", choicesArray)
                        }
                        optionsArray.put(optionObj)
                    }
                }
            }

            return LuaCommand(
                name = name.lowercase().trim(),
                description = description,
                optionsArray = optionsArray,
                filePath = filePath,
                fileContent = content
            )
        } catch (e: Exception) {
            val fileName = File(filePath).name
            LogManager.log(LogLevel.ERROR, "LuaParser", "Ошибка компиляции Lua в $fileName: ${e.localizedMessage}")
            return null
        }
    }

    /**
     * Вызывает на исполнение функцию `execute(interaction)` конкретного Lua-скрипта.
     * Формирует объект `interaction` в виде таблицы Lua с данными об отправителе, сервере, канале и опциях.
     *
     * @param commandName Имя вызываемой команды
     * @param username Никнейм автора слэш-интеракции из Discord
     * @param userId ID пользователя Discord
     * @param guildId ID Discord-сервера (гильдии), на котором произошел вызов
     * @param channelId ID текстового канала
     * @param options Входящие аргументы команды, переданные пользователем
     */
    fun executeCommand(
        commandName: String,
        username: String,
        userId: String,
        guildId: String?,
        channelId: String?,
        options: Map<String, Any>
    ): ExecutionResult {
        val command = _commands[commandName]
            ?: return ExecutionResult("Команда не найдена.", false)

        try {
            // Инициализация новой чистой среды исполнения скрипта во избежание пересечения состояний
            val globals = JsePlatform.standardGlobals()
            registerGlobals(globals)
            val chunk = globals.load(command.fileContent)
            chunk.call()

            // Ищем глобальную функцию execute
            val executeFunc = globals.get("execute")
            if (!executeFunc.isfunction()) {
                return ExecutionResult("Функция 'execute' не определена в Lua-скрипте.", false)
            }

            // Формируем таблицу 'interaction' для передачи аргументом в execute()
            val interactionTable = LuaValue.tableOf()
            interactionTable.set("user", LuaValue.valueOf(username))
            interactionTable.set("userId", LuaValue.valueOf(userId))
            interactionTable.set("guildId", LuaValue.valueOf(guildId ?: ""))
            interactionTable.set("channelId", LuaValue.valueOf(channelId ?: ""))

            // Маппим аргументы во вложенную таблицу options
            val optionsTable = LuaValue.tableOf()
            for ((key, value) in options) {
                val luaValue = when (value) {
                    is String -> LuaValue.valueOf(value)
                    is Int -> LuaValue.valueOf(value)
                    is Long -> LuaValue.valueOf(value.toDouble())
                    is Double -> LuaValue.valueOf(value)
                    is Boolean -> LuaValue.valueOf(value)
                    else -> LuaValue.valueOf(value.toString())
                }
                optionsTable.set(key, luaValue)
            }
            interactionTable.set("options", optionsTable)

            // Запускаем вызов и логируем результат работы
            LogManager.log(LogLevel.BOT, "LuaInterpreter", "Выполнение команды /$commandName пользователем $username")
            val luaResult = executeFunc.call(interactionTable)

            // Обрабатываем тип возвращаемого значения из Lua
            if (luaResult.istable()) {
                val content = luaResult.get("content").tojstring()
                val ephemeral = luaResult.get("ephemeral").toboolean()
                
                var embedsArray: JSONArray? = null
                val embedVal = luaResult.get("embed")
                if (embedVal.istable()) {
                    embedsArray = JSONArray()
                    embedsArray.put(parseLuaTableToEmbedJson(embedVal.checktable()))
                } else {
                    val embedsVal = luaResult.get("embeds")
                    if (embedsVal.istable()) {
                        embedsArray = JSONArray()
                        val tbl = embedsVal.checktable()
                        val keys = tbl.keys()
                        for (k in keys) {
                            val singleEmbedVal = tbl.get(k)
                            if (singleEmbedVal.istable()) {
                                embedsArray.put(parseLuaTableToEmbedJson(singleEmbedVal.checktable()))
                            }
                        }
                    }
                }
                return ExecutionResult(content, ephemeral, embedsArray)
            } else {
                // Если Lua вернул обычную строку или примитив
                return ExecutionResult(luaResult.tojstring(), false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при выполнении Lua /${commandName}", e)
            LogManager.log(LogLevel.ERROR, "LuaRuntime", "Ошибка в /$commandName: ${e.localizedMessage}")
            return ExecutionResult("⚠️ Внутренняя ошибка при выполнении скрипта: ${e.localizedMessage}", true)
        }
    }

    /**
     * Создает или обновляет Lua-файл команды в директории приложения, после чего вызывает её перезагрузку.
     */
    fun saveCommand(context: Context, name: String, content: String): Boolean {
        try {
            val dir = getCommandsDir(context)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "${name.lowercase().trim()}.lua")
            file.writeText(content)
            loadAllCommands(context)
            return true
        } catch (e: Exception) {
            LogManager.log(LogLevel.ERROR, "Editor", "Ошибка записи: ${e.localizedMessage}")
            return false
        }
    }

    /**
     * Удаляет Lua-файл с диска для удаления соответствующей слэш-команды.
     */
    fun deleteCommand(context: Context, command: LuaCommand): Boolean {
        try {
            val file = File(command.filePath)
            if (file.exists() && file.delete()) {
                loadAllCommands(context)
                LogManager.log(LogLevel.WARNING, "Editor", "Удален файл команды: ${command.name}.lua")
                return true
            }
            return false
        } catch (e: Exception) {
            LogManager.log(LogLevel.ERROR, "Editor", "Ошибка удаления: ${e.localizedMessage}")
            return false
        }
    }

    /**
     * Генерирует дефолтный набор функциональных команд во избежание пустого списка при первом запуске.
     */
    private fun createDefaultCommands(dir: File) {
        // 1. ping.lua
        File(dir, "ping.lua").writeText("""
-- Простая команда-приветствие. Отвечает pong.
command = {
    name = "ping",
    description = "Проверка задержки и работы бота",
    options = {}
}

function execute(interaction)
    return "🏓 Понг! Бот успешно запущен на Android-устройстве пользователя @" .. interaction.user .. "!"
end
        """.trimIndent())

        // 2. calculator.lua
        File(dir, "calculator.lua").writeText("""
-- Калькулятор двух чисел с выбором операции
command = {
    name = "calc",
    description = "Вычисляет выражение из двух чисел",
    options = {
        {
            name = "num1",
            description = "Первое число",
            type = 10, -- NUMBER
            required = true
        },
        {
            name = "operator",
            description = "Математическая операция",
            type = 3, -- STRING
            required = true,
            choices = {
                { name = "Сложить (+)", value = "+" },
                { name = "Вычесть (-)", value = "-" },
                { name = "Умножить (*)", value = "*" },
                { name = "Разделить (/)", value = "/" }
            }
        },
        {
            name = "num2",
            description = "Второе число",
            type = 10, -- NUMBER
            required = true
        }
    }
}

function execute(interaction)
    local n1 = interaction.options["num1"]
    local op = interaction.options["operator"]
    local n2 = interaction.options["num2"]
    
    local res = 0
    if op == "+" then
        res = n1 + n2
    elseif op == "-" then
        res = n1 - n2
    elseif op == "*" then
        res = n1 * n2
    elseif op == "/" then
        if n2 == 0 then
            return { content = "❌ Ошибка: Деление на ноль невозможно!", ephemeral = true }
        end
        res = n1 / n2
    else
        return "Неизвестный оператор: " .. tostring(op)
    end
    
    return "📊 **Калькулятор**\nВыражение: `" .. n1 .. " " .. op .. " " .. n2 .. "`\nРезультат: **" .. res .. "**"
end
        """.trimIndent())

        // 3. coinflip.lua
        File(dir, "coinflip.lua").writeText("""
-- Подбрасывает монетку
command = {
    name = "coinflip",
    description = "Подбрасывает монетку (Орел или Решка)",
    options = {}
}

function execute(interaction)
    math.randomseed(os.time())
    local val = math.random(1, 2)
    local result = ""
    local icon = ""
    if val == 1 then
        result = "Орел"
        icon = "🪙"
    else
        result = "Решка"
        icon = "🪙"
    end
    
    return {
        content = icon .. " Вы подбросили монетку! Выпало: **" .. result .. "**",
        ephemeral = false
    }
end
        """.trimIndent())

        // 4. roll.lua
        File(dir, "roll.lua").writeText("""
-- Кидает игральный кубик (или генерирует случайное число от 1 до max)
command = {
    name = "roll",
    description = "Генерирует случайное число от 1 до указанного значения",
    options = {
        {
            name = "max",
            description = "Максимальное число кубика (по умолчанию 6)",
            type = 4, -- INTEGER
            required = false
        }
    }
}

function execute(interaction)
    local maxVal = interaction.options["max"]
    if maxVal == nil then
        maxVal = 6
    end
    
    if maxVal < 1 then
        return { content = "❌ Нельзя бросить кость с числом граней меньше 1!", ephemeral = true }
    end
    
    math.randomseed(os.time())
    local val = math.random(1, maxVal)
    
    return "🎲 Результат броска: **" .. val .. "** (из " .. maxVal .. ")"
end
        """.trimIndent())

        // 5. crypto.lua
        File(dir, "crypto.lua").writeText("""
-- Получает текущий курс биткоина с CoinDesk API и показывает в Embed
command = {
    name = "crypto",
    description = "Показывает текущий курс Bitcoin (embeds + http_get)",
    options = {}
}

function execute(interaction)
    local raw_data = http_get("https://api.coindesk.com/v1/bpi/currentprice.json")
    if raw_data == nil or raw_data:find("Error") then
        return "⚠️ Не удалось получить данные о курсе криптовалют."
    end
    
    local data = json_parse(raw_data)
    if data == nil or data.bpi == nil then
        return "⚠️ Ошибка парсинга цены биткоина."
    end
    
    local rate = data.bpi.USD.rate
    local updated = data.time.updated
    
    local embed = {
        title = "💰 Курс Bitcoin (BTC)",
        description = "Текущая рыночная стоимость первой криптовалюты по версии CoinDesk.",
        color = "#F7931A", -- Оранжевый цвет Bitcoin
        fields = {
            { name = "Стоимость (USD)", value = "$" .. rate, inline = true },
            { name = "Обновлено", value = updated, inline = false }
        },
        footer = {
            text = "Бот-лаунчер на Android",
            icon_url = "https://w7.pngwing.com/pngs/336/275/png-transparent-bitcoin-cryptocurrency-logo-ethereum-litecoin-cardano-blockchain-physical-coin-thumbnail.png"
        }
    }
    
    return {
        content = "Курс получен успешно!",
        embed = embed,
        ephemeral = false
    }
end
        """.trimIndent())

        // 6. stat.lua
        File(dir, "stat.lua").writeText("""
-- Личный счетчик использования команд пользователем
command = {
    name = "stat",
    description = "Показывает ваш личный счетчик использования команд",
    options = {}
}

function execute(interaction)
    local key = "user_count_" .. interaction.userId
    local val_str = store_get(key)
    local count = 0
    if val_str ~= "" then
        count = tonumber(val_str)
    end
    count = count + 1
    store_set(key, tostring(count))
    
    local embed = {
        title = "📊 Личная статистика бота",
        color = "#9d4edd",
        description = "Привет, @" .. interaction.user .. "! Вы использовали этого бота на этом устройстве уже **" .. count .. "** раз(а).",
        footer = { text = "Данные сохранены локально в памяти Android" }
    }
    
    return {
        content = "",
        embed = embed,
        ephemeral = false
    }
end
        """.trimIndent())
    }

    private fun registerGlobals(globals: Globals) {
        // http_get
        globals.set("http_get", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val url = arg.tojstring()
                return try {
                    val request = okhttp3.Request.Builder().url(url)
                        .header("User-Agent", "DiscordBot-Android-Launcher/1.0").build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            LuaValue.valueOf(response.body?.string() ?: "")
                        } else {
                            LuaValue.valueOf("Error: HTTP " + response.code)
                        }
                    }
                } catch (e: Exception) {
                    LuaValue.valueOf("Error: " + e.message)
                }
            }
        })

        // json_parse
        globals.set("json_parse", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val str = arg.tojstring()
                return try {
                    if (str.trim().startsWith("[")) {
                        jsonToLua(JSONArray(str))
                    } else {
                        jsonToLua(JSONObject(str))
                    }
                } catch (e: Exception) {
                    LuaValue.valueOf("Error: " + e.message)
                }
            }
        })

        // json_stringify
        globals.set("json_stringify", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                return try {
                    LuaValue.valueOf(luaToJson(arg).toString())
                } catch (e: Exception) {
                    LuaValue.valueOf("Error: " + e.message)
                }
            }
        })

        // store_set
        globals.set("store_set", object : org.luaj.vm2.lib.TwoArgFunction() {
            override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
                val key = arg1.tojstring()
                val value = arg2.tojstring()
                statePrefs?.edit()?.putString(key, value)?.apply()
                return LuaValue.NIL
            }
        })

        // store_get
        globals.set("store_get", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val key = arg.tojstring()
                val value = statePrefs?.getString(key, "") ?: ""
                return LuaValue.valueOf(value)
            }
        })
    }

    private fun jsonToLua(json: Any?): LuaValue {
        return when (json) {
            null, JSONObject.NULL -> LuaValue.NIL
            is JSONObject -> {
                val table = LuaValue.tableOf()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    table.set(key, jsonToLua(json.get(key)))
                }
                table
            }
            is JSONArray -> {
                val table = LuaValue.tableOf()
                for (i in 0 until json.length()) {
                    table.set(i + 1, jsonToLua(json.get(i)))
                }
                table
            }
            is Boolean -> LuaValue.valueOf(json)
            is Int -> LuaValue.valueOf(json)
            is Long -> LuaValue.valueOf(json.toDouble())
            is Double -> LuaValue.valueOf(json)
            else -> LuaValue.valueOf(json.toString())
        }
    }

    private fun luaToJson(lua: LuaValue): Any {
        return when {
            lua.isnil() -> JSONObject.NULL
            lua.isboolean() -> lua.toboolean()
            lua.isint() -> lua.toint()
            lua.islong() -> lua.tolong()
            lua.isnumber() -> lua.todouble()
            lua.istable() -> {
                val tableObj = lua.checktable()
                val keys = tableObj.keys()
                var isArray = true
                var maxKey = 0
                val allKeys = mutableListOf<LuaValue>()
                for (k in keys) {
                    allKeys.add(k)
                    if (k.isint()) {
                        if (k.toint() <= 0) {
                            isArray = false
                        } else if (k.toint() > maxKey) {
                            maxKey = k.toint()
                        }
                    } else {
                        isArray = false
                    }
                }
                if (isArray && maxKey > 0 && maxKey == allKeys.size) {
                    val array = JSONArray()
                    for (i in 1..maxKey) {
                        array.put(luaToJson(tableObj.get(i)))
                    }
                    array
                } else {
                    val obj = JSONObject()
                    for (k in allKeys) {
                        obj.put(k.tojstring(), luaToJson(tableObj.get(k)))
                    }
                    obj
                }
            }
            else -> lua.tojstring()
        }
    }

    private fun parseLuaTableToEmbedJson(table: org.luaj.vm2.LuaTable): JSONObject {
        val embed = JSONObject()

        val title = table.get("title")
        if (!title.isnil()) embed.put("title", title.tojstring())

        val description = table.get("description")
        if (!description.isnil()) embed.put("description", description.tojstring())

        val url = table.get("url")
        if (!url.isnil()) embed.put("url", url.tojstring())

        val color = table.get("color")
        if (!color.isnil() && color.isint()) {
            embed.put("color", color.toint())
        } else if (!color.isnil()) {
            val colorStr = color.tojstring()
            if (colorStr.startsWith("#")) {
                try {
                    embed.put("color", colorStr.substring(1).toInt(16))
                } catch (e: Exception) {}
            }
        }

        val timestamp = table.get("timestamp")
        if (!timestamp.isnil()) embed.put("timestamp", timestamp.tojstring())

        // footer
        val footer = table.get("footer")
        if (footer.istable()) {
            val footerObj = JSONObject()
            val fText = footer.get("text")
            val fIcon = footer.get("icon_url")
            if (!fText.isnil()) footerObj.put("text", fText.tojstring())
            if (!fIcon.isnil()) footerObj.put("icon_url", fIcon.tojstring())
            embed.put("footer", footerObj)
        }

        // image
        val image = table.get("image")
        if (image.istable()) {
            val imgObj = JSONObject()
            val imgUrl = image.get("url")
            if (!imgUrl.isnil()) imgObj.put("url", imgUrl.tojstring())
            embed.put("image", imgObj)
        } else if (image.isstring()) {
            val imgObj = JSONObject()
            imgObj.put("url", image.tojstring())
            embed.put("image", imgObj)
        }

        // thumbnail
        val thumbnail = table.get("thumbnail")
        if (thumbnail.istable()) {
            val thumbObj = JSONObject()
            val thumbUrl = thumbnail.get("url")
            if (!thumbUrl.isnil()) thumbObj.put("url", thumbUrl.tojstring())
            embed.put("thumbnail", thumbObj)
        } else if (thumbnail.isstring()) {
            val thumbObj = JSONObject()
            thumbObj.put("url", thumbnail.tojstring())
            embed.put("thumbnail", thumbObj)
        }

        // author
        val author = table.get("author")
        if (author.istable()) {
            val authObj = JSONObject()
            val aName = author.get("name")
            val aUrl = author.get("url")
            val aIcon = author.get("icon_url")
            if (!aName.isnil()) authObj.put("name", aName.tojstring())
            if (!aUrl.isnil()) authObj.put("url", aUrl.tojstring())
            if (!aIcon.isnil()) authObj.put("icon_url", aIcon.tojstring())
            embed.put("author", authObj)
        }

        // fields
        val fields = table.get("fields")
        if (fields.istable()) {
            val fieldsArr = JSONArray()
            val tbl = fields.checktable()
            val keys = tbl.keys()
            for (k in keys) {
                val fVal = tbl.get(k)
                if (fVal.istable()) {
                    val fieldObj = JSONObject()
                    val fName = fVal.get("name")
                    val fValue = fVal.get("value")
                    val fInline = fVal.get("inline")
                    if (!fName.isnil()) fieldObj.put("name", fName.tojstring())
                    if (!fValue.isnil()) fieldObj.put("value", fValue.tojstring())
                    if (!fInline.isnil()) fieldObj.put("inline", fInline.toboolean())
                    fieldsArr.put(fieldObj)
                }
            }
            embed.put("fields", fieldsArr)
        }

        return embed
    }
}

/**
 * Итоговый результат выполнения слэш-интеракции.
 *
 * @property content Ответ в виде форматированного Markdown-текста, который бот отправит в чат Discord
 * @property ephemeral Устанавливает видимость сообщения (true - видит только вызвавший слэш команду)
 * @property embeds Список вложенных богатых карточек (Discord Embeds) в формате JSON
 */
data class ExecutionResult(
    val content: String,
    val ephemeral: Boolean,
    val embeds: JSONArray? = null
)
