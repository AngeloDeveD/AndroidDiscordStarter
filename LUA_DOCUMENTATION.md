# 📚 Документация Lua-скриптов для Discord Ботов

Данный документ описывает полный синтаксис, системные функции, объектную модель и возможности интеграции Lua-скриптов в мобильном конструкторе Discord-ботов на Android.

Интеграция выполнена на базе высокопроизводительной, безопасной песочницы **Luaj (JSE Platform)**. Вы можете добавлять, редактировать и удалять скрипты на лету — бот автоматически скомпилирует их и зарегистрирует в виде слэш-команд (Slash Commands) в Discord.

---

## 🧭 Содержание
1. [Общая структура файла](#1-общая-структура-файла)
2. [Определение команды (Таблица `command`)](#2-определение-команды-таблица-command)
3. [Параметры входящего события (Объект `interaction`)](#3-параметры-входящего-события-объект-interaction)
4. [Встроенные API-функции](#4-встроенные-api-функции)
   - [HTTP Запросы (`http_get`)](#http-запросы-http_get)
   - [Работа с JSON (`json_parse` и `json_stringify`)](#работа-с-json-json_parse-и-json_stringify)
   - [Локальное хранилище данных (`store_set` и `store_get`)](#локальное-хранилище-данных-store_set-и-store_get)
5. [Формат возврата данных (Embeds & Ephemeral)](#5-формат-возврата-данных-embeds--ephemeral)
6. [Готовые примеры скриптов](#6-готовые-примеры-скриптов)

---

## 1. Общая структура файла

Каждый скрипт представляет собой автономный модуль `.lua`. Минимальный жизнеспособный скрипт должен содержать:
1. Конфигурационную таблицу `command` для описания метаданных слэш-команды.
2. Глобальную функцию `execute(interaction)`, вызываемую при активации команды на сервере Discord.

**Пример (`hello.lua`):**
```lua
command = {
    name = "hello",
    description = "Поприветствовать пользователя",
    options = {}
}

function execute(interaction)
    return "Привет, @" .. interaction.user .. "! 👋"
end
```

---

## 2. Определение команды (Таблица `command`)

Таблица `command` сообщает приложению, как зарегистрировать команду в API Discord разработчика.

```lua
command = {
    name = "имя_команды",       -- Только строчные латинские буквы, цифры и знаки дефиса/подчеркивания (макс. 32 символа)
    description = "Описание",   -- Текст описания команды в интерфейсе Discord (макс. 100 символов)
    options = {                 -- Список аргументов (необязательно)
        {
            name = "аргумент1",
            description = "Описание аргумента",
            type = 3,           -- Числовой тип аргумента согласно API Discord
            required = true     -- Обязательный ли параметр для ввода пользователя (true / false)
        }
    }
}
```

### Справочник типов аргументов (`type`):
| ID | Тип в Discord API | Описание | Значение в Lua |
| :--- | :--- | :--- | :--- |
| **3** | `STRING` | Текстовая строка | `string` |
| **4** | `INTEGER` | Целое число | `number` (без дробной части) |
| **5** | `BOOLEAN` | Логическое значение | `boolean` (true/false) |
| **10** | `NUMBER` | Число с плавающей запятой или целое | `number` |

---

## 3. Параметры входящего события (Объект `interaction`)

При вызове функции `execute(interaction)` среда передает таблицу `interaction`, которая содержит подробные данные о контексте вызова:

*   `interaction.user` *(string)* — Имя пользователя Discord (username), который ввел команду.
*   `interaction.userId` *(string)* — Уникальный ID пользователя Discord (Snowflake, например, `183492857194829374`).
*   `interaction.guildId` *(string)* — ID сервера Discord, где была введена команда.
*   `interaction.channelId` *(string)* — ID текстового канала, в котором запущена команда.
*   `interaction.options` *(table)* — Ассоциативный массив переданных аргументов. Ключами являются имена из секции `options`, а значениями — введенные данные.

**Пример обработки аргументов:**
```lua
function execute(interaction)
    local user_text = interaction.options["text"] -- Получение string аргумента
    local multiplier = interaction.options["count"] -- Получение number аргумента
    
    if multiplier == nil then
        multiplier = 1
    end
    
    return "Вы повторили: " .. string.rep(user_text .. " ", multiplier)
end
```

---

## 4. Встроенные API-функции

Для расширения базового функционала Lua в среду разработчика интегрированы мощные нативные Android-мосты.

### HTTP Запросы (`http_get`)
Позволяет осуществлять синхронные HTTP GET-запросы к любым публичным API в сети Интернет.
*   **Синтаксис:** `http_get(url_string)`
*   **Возвращает:** `string` (содержимое ответа) или строку с ошибкой `Error: ...` / `nil`.

> ⚠️ **Важно:** Запросы выполняются асинхронно под капотом, не блокируя основной поток UI мобильного телефона. Среда автоматически дождется ответа и вернет его в скрипт.

**Пример:**
```lua
local response = http_get("https://catfact.ninja/fact")
```

### Работа с JSON (`json_parse` и `json_stringify`)
Для удобной интеграции с современными REST API встроены парсеры JSON.

*   `json_parse(json_string)` — Конвертирует строку JSON в структурированную таблицу Lua (поддерживает вложенные объекты и массивы).
*   `json_stringify(lua_table)` — Сериализует таблицу Lua или примитив обратно в JSON-строку.

**Пример парсинга факта о кошках:**
```lua
local raw_json = http_get("https://catfact.ninja/fact")
local data = json_parse(raw_json)
local fact = data.fact -- Доступ к JSON полю {"fact": "..."}
```

### Локальное хранилище данных (`store_set` и `store_get`)
Бот снабжен энергонезависимой базой данных на базе Android `SharedPreferences`. Сохраненные данные сохраняются при перезапуске бота, перезагрузке телефона или обновлении приложения.

*   `store_set(key_string, value_string)` — Сохраняет строковое значение по ключу.
*   `store_get(key_string)` — Возвращает ранее сохраненную строку. Если ключ отсутствует, возвращает пустую строку `""`.

> 💡 **Совет:** Если вам нужно сохранить число или таблицу, используйте стандартные функции приведения или JSON сериализацию:
> ```lua
> -- Сохранение таблицы
> local t = { score = 100, achievements = {"first_win", "rich"} }
> store_set("player_save", json_stringify(t))
> 
> -- Чтение таблицы
> local raw = store_get("player_save")
> local t = json_parse(raw)
> ```

---

## 5. Формат возврата данных (Embeds & Ephemeral)

Функция `execute` может возвращать как простую строку (текстовое сообщение), так и полноценную таблицу для управления поведением ответа в Discord.

### Вариант 1: Возврат простой строки
```lua
return "Простой текстовый ответ"
```

### Вариант 2: Возврат таблицы настроек ответа
Классический формат расширенного ответа:
```lua
return {
    content = "Основной текст сообщения", -- Текст над карточкой (необязательно)
    ephemeral = true,                      -- Скрытый ответ (виден только вызвавшему, необязательно)
    embed = embed_table                    -- Богатая карточка Embed (необязательно)
}
```

### Формат Discord Embed-карточки (`embed`):
Таблица `embed` поддерживает следующие поля:
*   `title` *(string)* — Заголовок карточки.
*   `description` *(string)* — Многострочное описание.
*   `url` *(string)* — Ссылка на заголовок.
*   `color` *(string / number)* — Цвет полоски слева. Поддерживает hex-формат (например, `"#FF0000"` или `"#9d4edd"`) или десятичные числа.
*   `timestamp` *(string)* — Метка времени в формате ISO 8601.
*   `footer` *(table)* — Подвал карточки (`{ text = "Текст", icon_url = "ссылка" }`).
*   `image` *(table / string)* — Основное изображение (`{ url = "ссылка" }` или просто строка-ссылка).
*   `thumbnail` *(table / string)* — Миниатюра в верхнем правом углу (`{ url = "ссылка" }` или просто строка-ссылка).
*   `author` *(table)* — Информация об авторе (`{ name = "Автор", url = "ссылка", icon_url = "ссылка" }`).
*   `fields` *(table)* — Список структурных полей:
    ```lua
    fields = {
        { name = "Имя поля 1", value = "Значение 1", inline = true },
        { name = "Имя поля 2", value = "Значение 2", inline = true }
    }
    ```

---

## 6. Готовые примеры скриптов

### Пример 1: Команда броска костей (`/roll`)
Использует встроенный генератор случайных чисел Lua и парсинг опциональных аргументов.
```lua
command = {
    name = "roll",
    description = "Бросить игральный кубик",
    options = {
        {
            name = "max",
            description = "Максимальное число граней кубика (по умолчанию 6)",
            type = 4, -- INTEGER
            required = false
        }
    }
}

function execute(interaction)
    local maxVal = interaction.options["max"]
    if maxVal == nil or maxVal <= 0 then
        maxVal = 6
    end
    
    -- Инициализация сида случайного числа
    math.randomseed(os.time())
    local val = math.random(1, maxVal)
    
    return "🎲 Результат броска: **" .. val .. "** (из " .. maxVal .. ")"
end
```

### Пример 2: Интеграция с Crypto API (`/crypto`)
Вызывает CoinDesk API, считывает курс Биткоина в реальном времени и выводит стильный Embed.
```lua
command = {
    name = "crypto",
    description = "Показывает текущую рыночную стоимость Bitcoin",
    options = {}
}

function execute(interaction)
    local raw_data = http_get("https://api.coindesk.com/v1/bpi/currentprice.json")
    if raw_data == nil or raw_data:find("Error") then
        return "⚠️ Не удалось получить информацию с API сервера."
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
        color = "#F7931A", -- Брендовый оранжевый цвет Bitcoin
        fields = {
            { name = "Стоимость (USD)", value = "$" .. rate, inline = true },
            { name = "Обновлено", value = updated, inline = false }
        },
        footer = {
            text = "Запущено со смартфона Android",
            icon_url = "https://w7.pngwing.com/pngs/336/275/png-transparent-bitcoin-cryptocurrency-logo-ethereum-litecoin-cardano-blockchain-physical-coin-thumbnail.png"
        }
    }
    
    return {
        content = "Курс криптовалюты успешно получен!",
        embed = embed,
        ephemeral = false
    }
end
```

### Пример 3: Личная статистика пользователя на базе локальной БД (`/stat`)
Использует постоянное хранилище `store_set` / `store_get` для подсчета вызовов.
```lua
command = {
    name = "stat",
    description = "Показывает личный счетчик вызовов бота",
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
        title = "📊 Личная статистика пользователя",
        color = "#9d4edd",
        description = "Привет, @" .. interaction.user .. "!\nВы вызвали слэш-команды на этом боте уже **" .. count .. "** раз(а).",
        footer = { text = "Все данные сохранены в памяти вашего Android устройства" }
    }
    
    return {
        content = "",
        embed = embed,
        ephemeral = false
    }
end
```
