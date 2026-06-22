package com.example

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

data class LuaCommand(
    val name: String,
    val description: String,
    val optionsArray: JSONArray,
    val filePath: String,
    val fileContent: String
)

object LuaCommandManager {
    private const val TAG = "LuaCommandManager"
    private const val COMMANDS_DIR = "commands"

    private val _commands = mutableMapOf<String, LuaCommand>()
    val commands: Map<String, LuaCommand> get() = _commands

    /**
     * Initializes directory and populates default command files if they don't exist
     */
    fun initializeAndLoad(context: Context): List<LuaCommand> {
        val dir = getCommandsDir(context)
        LogManager.log(LogLevel.INFO, "System", "Загрузка Lua-команд из: ${dir.absolutePath}")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        // Create default command files if directory is empty
        val files = dir.listFiles()
        if (files == null || files.isEmpty()) {
            createDefaultCommands(dir)
        }

        return loadAllCommands(context)
    }

    fun getCommandsDir(context: Context): File {
        return context.getExternalFilesDir(COMMANDS_DIR) ?: File(context.filesDir, COMMANDS_DIR)
    }

    /**
     * Loads/Refreshes all Lua commands from the files directory
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
     * Parses name, description, and options from Lua content using Luaj globals
     */
    private fun parseLuaCommand(filePath: String, content: String): LuaCommand? {
        try {
            val globals = JsePlatform.standardGlobals()
            val chunk = globals.load(content)
            chunk.call()

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
     * Executes the Lua execute(interaction) function and returns interaction response data
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
            val globals = JsePlatform.standardGlobals()
            val chunk = globals.load(command.fileContent)
            chunk.call()

            val executeFunc = globals.get("execute")
            if (!executeFunc.isfunction()) {
                return ExecutionResult("Функция 'execute' не определена в Lua-скрипте.", false)
            }

            // Assemble 'interaction' argument as a Lua table
            val interactionTable = LuaValue.tableOf()
            interactionTable.set("user", LuaValue.valueOf(username))
            interactionTable.set("userId", LuaValue.valueOf(userId))
            interactionTable.set("guildId", LuaValue.valueOf(guildId ?: ""))
            interactionTable.set("channelId", LuaValue.valueOf(channelId ?: ""))

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

            // Invoke direct call
            LogManager.log(LogLevel.BOT, "LuaInterpreter", "Выполнение команды /$commandName пользователем $username")
            val luaResult = executeFunc.call(interactionTable)

            if (luaResult.istable()) {
                val content = luaResult.get("content").tojstring()
                val ephemeral = luaResult.get("ephemeral").toboolean()
                return ExecutionResult(content, ephemeral)
            } else {
                return ExecutionResult(luaResult.tojstring(), false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при выполнении Lua /${commandName}", e)
            LogManager.log(LogLevel.ERROR, "LuaRuntime", "Ошибка в /$commandName: ${e.localizedMessage}")
            return ExecutionResult("⚠️ Внутренняя ошибка при выполнении скрипта: ${e.localizedMessage}", true)
        }
    }

    /**
     * Save or update an in-app edited command
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
     * Delete a command
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
    }
}

data class ExecutionResult(
    val content: String,
    val ephemeral: Boolean
)
