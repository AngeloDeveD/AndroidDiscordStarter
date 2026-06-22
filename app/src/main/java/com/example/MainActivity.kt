package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*

/**
 * [MainActivity] — главная точка входа в Android-приложение Discord Bot Launcher.
 * Наследуется от [ComponentActivity].
 * 
 * В методе [onCreate] вызывается [enableEdgeToEdge] для активации отрисовки интерфейса под
 * системными элементами управления (StatusBar, NavigationBar) для современного безрамочного отображения.
 * С помощью Jetpack Compose верстается основной контейнер [Scaffold], передающий внутренние отступы [innerPadding]
 * в корневой компонент [BotDashboardScreen].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Отрисовка "от края до края"
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    BotDashboardScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

/**
 * [BotDashboardScreen] — корневой экран панели управления ботом.
 * Разделен на три логических вкладки (Tabs):
 * 1. Консоль / Панель ("Бот") — управление сессией, токеном, просмотр терминала логов.
 * 2. Команды Lua ("Команды") — список активных Lua скриптов, их создание, редактирование и удаление.
 * 3. Руководство ("Справка") — иллюстрированные карточки с инструкцией по настройке бота и синтаксису Lua.
 *
 * @param modifier Дополнительные модификаторы разметки
 * @param viewModel Логическая модель представления, предоставляемая Compose ViewModel Provider
 */
@Composable
fun BotDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: BotViewModel = viewModel()
) {
    // Индекс текущей выбранной вкладки в нижнем баре навигации
    var selectedTab by remember { mutableIntStateOf(0) }
    // Реактивное наблюдение за статусом подключения бота через Kotlin StateFlow
    val currentStatus by viewModel.botStatus.collectAsState()

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        // Шапка приложения (Header Section) с высокой плотностью элементов
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.screenPadding,
                    end = AppDimens.screenPadding,
                    top = AppDimens.headerTopPadding,
                    bottom = AppDimens.headerBottomPadding
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BotRunner",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                // Индикатор состояния статуса бота (зеленый/оранжевый/красный) с мелкой надписью
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.smallSpacing + 2.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val statusDotColor = when (currentStatus) {
                        BotStatus.RUNNING -> TerminalGreen
                        BotStatus.CONNECTING -> TerminalOrange
                        else -> TerminalRed
                    }
                    Box(
                        modifier = Modifier
                            .size(AppDimens.statusDotSize)
                            .background(statusDotColor, CircleShape)
                    )
                    val statusLabel = when (currentStatus) {
                        BotStatus.RUNNING -> "Статус: Активен"
                        BotStatus.CONNECTING -> "Статус: Подключение"
                        else -> "Статус: Отключен"
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Круглая кнопка быстрого перехода к справке / настройкам
            IconButton(
                onClick = { selectedTab = 2 },
                modifier = Modifier
                    .size(AppDimens.circleButtonSize)
                    .background(LightPurpleVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = DarkPurple,
                    modifier = Modifier.size(AppDimens.normalIconSize)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.mediumSpacing))

        // Основная область контента со вкладками
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = AppDimens.screenPadding)
        ) {
            when (selectedTab) {
                0 -> ConsoleTab(viewModel = viewModel) // Вкладка консоли
                1 -> CommandsTab(viewModel = viewModel) // Вкладка Lua-команд
                2 -> GuideTab() // Вкладка инструкций
            }
        }

        // Нижняя панель навигации (NavigationBar) со скругленными верхними углами
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = AppDimens.highDensityCorner, topEnd = AppDimens.highDensityCorner),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                modifier = Modifier.height(AppDimens.bottomBarHeight),
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                val items = listOf(
                    Triple("Бот", Icons.Default.PlayArrow, 0),
                    Triple("Команды", Icons.Default.List, 1),
                    Triple("Справка", Icons.Default.Info, 2)
                )
                val darkTheme = isSystemInDarkTheme()
                items.forEach { (title, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (darkTheme) Color(0xFFD0BCFF) else BrandPurple,
                            selectedTextColor = if (darkTheme) Color(0xFFD0BCFF) else BrandPurple,
                            unselectedIconColor = if (darkTheme) Color.White.copy(alpha = 0.5f) else Color(0xFF79747E),
                            unselectedTextColor = if (darkTheme) Color.White.copy(alpha = 0.5f) else Color(0xFF79747E),
                            indicatorColor = if (darkTheme) Color(0xFF4A4458) else LightPurpleSecVariant
                        )
                    )
                }
            }
        }
    }
}

/**
 * [StatusBadge] — анимированный бейдж статуса бота в консоли терминала.
 * При активности бота или в момент подключения точка плавно пульсирует с помощью бесконечной транзакции Compose.
 */
@Composable
fun StatusBadge(status: BotStatus) {
    val (color, text) = when (status) {
        BotStatus.STOPPED -> Color(0xFFE57373) to "Остановлен"
        BotStatus.CONNECTING -> Color(0xFFFFB74D) to "Подключение"
        BotStatus.RUNNING -> Color(0xFF81C784) to "Активен"
        BotStatus.ERROR -> Color(0xFFE57373) to "Ошибка"
    }

    // Инициализация пульсации прозрачности для визуальной динамики процесса
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    color.copy(
                        alpha = if (status == BotStatus.RUNNING || status == BotStatus.CONNECTING) alpha else 1.0f
                    )
                )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * [ConsoleTab] — первая вкладка ("Бот"), реализующая управление токеном сессии,
 * быстрый запуск/остановку соединения шлюза и отображение системного терминала с логами.
 */
@Composable
fun ConsoleTab(viewModel: BotViewModel) {
    val token by viewModel.token.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val commands by viewModel.commandsList.collectAsState()
    val currentStatus by viewModel.botStatus.collectAsState()
    var isTokenVisible by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AppDimens.extraLargeSpacing)
    ) {
        // Карточка настроек авторизации бота (ввод токена с маскированием)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimens.highDensityCorner),
            colors = AppStyles.standardCardColors(),
            border = AppStyles.standardBorder()
        ) {
            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                Text(
                    text = "АВТОРИЗАЦИЯ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandPurple,
                    modifier = Modifier.padding(bottom = AppDimens.mediumSpacing - 2.dp)
                )
                
                OutlinedTextField(
                    value = token,
                    onValueChange = { viewModel.saveToken(it) },
                    placeholder = { Text("Токен Discord Бота") },
                    leadingIcon = { 
                        Icon(
                            imageVector = Icons.Default.Lock, 
                            contentDescription = "Token Lock", 
                            tint = TextMuted,
                            modifier = Modifier.size(AppDimens.smallIconSize)
                        ) 
                    },
                    trailingIcon = {
                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                            Icon(
                                imageVector = if (isTokenVisible) Icons.Default.Info else Icons.Default.Info, 
                                contentDescription = "Toggle Visibility",
                                tint = TextMuted,
                                modifier = Modifier.size(AppDimens.smallIconSize)
                            )
                        }
                    },
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = currentStatus == BotStatus.STOPPED || currentStatus == BotStatus.ERROR,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.mediumCorner),
                    colors = AppStyles.textFieldColors()
                )
            }
        }

        // Блок управления "Скрипты и Запуск" в виде строки с двумя интерактивными карточками
        Row(
            modifier = Modifier.fillMaxWidth().height(AppDimens.scriptCardHeight),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.largeSpacing)
        ) {
            // Левая карточка — сводка по Lua скриптам
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(AppDimens.highDensityCorner),
                colors = CardDefaults.cardColors(containerColor = LightPurpleSecVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(AppDimens.cardPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Lua Scripts",
                        tint = DarkPurple,
                        modifier = Modifier.size(AppDimens.normalIconSize + 2.dp)
                    )
                    Column {
                        Text(
                            text = "Lua Скрипты",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkPurple,
                            fontSize = AppDimens.titleFontSize
                        )
                        Text(
                            text = "${commands.size} команд обнаружено",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = AppDimens.smallLabelFontSize
                        )
                    }
                }
            }

            // Правая динамическая кнопка Запуск / Остановка
            val btnBg = when (currentStatus) {
                BotStatus.STOPPED, BotStatus.ERROR -> BrandPurple
                BotStatus.CONNECTING -> TerminalOrange
                BotStatus.RUNNING -> TerminalRed
            }
            val btnText = when (currentStatus) {
                BotStatus.STOPPED, BotStatus.ERROR -> "Запустить"
                BotStatus.CONNECTING -> "Подключение"
                BotStatus.RUNNING -> "Остановить"
            }
            val btnIcon = when (currentStatus) {
                BotStatus.STOPPED, BotStatus.ERROR -> Icons.Default.PlayArrow
                BotStatus.CONNECTING -> Icons.Default.Refresh
                BotStatus.RUNNING -> Icons.Default.Close
            }

            Button(
                onClick = {
                    if (currentStatus == BotStatus.STOPPED || currentStatus == BotStatus.ERROR) {
                        viewModel.startBot()
                    } else {
                        viewModel.stopBot()
                    }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(AppDimens.highDensityCorner),
                colors = ButtonDefaults.buttonColors(containerColor = btnBg),
                contentPadding = PaddingValues(AppDimens.largeSpacing)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = btnIcon,
                        contentDescription = btnText,
                        tint = Color.White,
                        modifier = Modifier.size(AppDimens.largeIconSize)
                    )
                    Spacer(modifier = Modifier.height(AppDimens.smallSpacing))
                    Text(
                        text = btnText.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = AppDimens.letterSpacingWide,
                        color = Color.White,
                        fontSize = AppDimens.smallLabelFontSize
                    )
                }
            }
        }

        // Секция черного технического терминала вывода логов
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = AppDimens.mediumSpacing),
            shape = RoundedCornerShape(AppDimens.highDensityCorner),
            colors = AppStyles.terminalCardColors(),
            border = AppStyles.terminalBorder()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Строка заголовка терминала с утилитами управления
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppDimens.extraLargeSpacing,
                            end = AppDimens.extraLargeSpacing,
                            top = AppDimens.largeSpacing,
                            bottom = AppDimens.smallSpacing
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.smallSpacing + 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Console",
                            tint = TerminalGrey,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "КОНСОЛЬ ВЫВОДА",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TerminalBlue,
                            letterSpacing = AppDimens.letterSpacingHigh,
                            fontSize = AppDimens.smallLabelFontSize
                        )
                    }

                    // Кнопки очистки и экспорта логов терминала
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.smallSpacing)) {
                        IconButton(
                            onClick = { viewModel.clearLogs() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear logs",
                                tint = TerminalGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(AppDimens.smallSpacing))
                        IconButton(
                            onClick = {
                                val fullLog = logs.joinToString("\n") { "[${it.timestamp}] [${it.level.name}] [${it.tag}] ${it.message}" }
                                if (fullLog.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(fullLog))
                                    LogManager.log(LogLevel.INFO, "System", "Логи скопированы в буфер обмена")
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy logs",
                                tint = TerminalGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Свиток логов с автоскроллом к последнему элементу
                val listState = rememberLazyListState()

                LaunchedEffect(logs.size) {
                    if (logs.isNotEmpty()) {
                        listState.animateScrollToItem(logs.size - 1)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                ) {
                    if (logs.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No logs",
                                tint = TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(AppDimens.smallSpacing + 2.dp))
                            Text(
                                text = "Ожидание нажатия кнопки запуска...",
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = AppDimens.terminalFontSize),
                                color = TerminalGrey
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.smallSpacing)
                        ) {
                            items(logs, key = { it.id }) { log ->
                                LogLineItem(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [LogLineItem] — компонент отображения одной строки логов в консоли терминала.
 * Настраивает шрифт Monospace и кастомные цвета на основе [LogLevel].
 */
@Composable
fun LogLineItem(log: LogEntry) {
    val levelColor = when (log.level) {
        LogLevel.INFO -> TerminalBlue
        LogLevel.WARNING -> TerminalWarn
        LogLevel.ERROR -> TerminalRed
        LogLevel.DEBUG -> TerminalGrey
        LogLevel.SUCCESS -> TerminalBlue
        LogLevel.BOT -> LightPurpleVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // [Время]
        Text(
            text = "[${log.timestamp}]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = AppDimens.terminalFontSize,
                fontWeight = FontWeight.Normal
            ),
            color = TerminalGrey,
            modifier = Modifier.padding(end = 6.dp)
        )

        // [УРОВЕНЬ/ТЕГ]
        Text(
            text = "${log.level.name}:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = AppDimens.terminalFontSize,
                fontWeight = FontWeight.Bold
            ),
            color = levelColor,
            modifier = Modifier.padding(end = 6.dp)
        )

        // Текст сообщения
        Text(
            text = log.message,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = AppDimens.terminalFontSize,
                lineHeight = 14.sp
            ),
            color = Color.White
        )
    }
}

/**
 * [CommandsTab] — вторая вкладка управления ("Команды"), выводящая список всех
 * загруженных скриптов. Позволяет создавать новые скрипты по шаблонам, редактировать существующие
 * и отправлять Bulk Overwrite для моментальной горячей синхронизации без перезапуска.
 */
@Composable
fun CommandsTab(viewModel: BotViewModel) {
    val commands by viewModel.commandsList.collectAsState()
    val isEditorOpen by viewModel.isEditorOpen.collectAsState()
    val selectedCommand by viewModel.selectedCommand.collectAsState()
    val context = LocalContext.current
    val currentStatus by viewModel.botStatus.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Зарегистрированные команды",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${commands.size} Lua-файлов загружено / Имя должно быть строчными буквами",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Кнопка быстрого создания новой шелл-команды Lua
            IconButton(
                onClick = { viewModel.openCommandEditor(null) }
            ) {
                Icon(
                    imageVector = Icons.Default.Add, 
                    contentDescription = "New command file",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Баннер Горячей синхронизации (виден, когда бот активен и запущен)
        AnimatedVisibility(visible = currentStatus == BotStatus.RUNNING) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Бот запущен!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Вы добавили или изменили файлы? Нажмите кнопку синхронизации, чтобы обновить команды прямо сейчас в Discord без перезапуска.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.syncCommandsToDiscord() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Обновить", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Прогрузка списка Lua команд, если он пуст — вывод анимации заглушки
        if (commands.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "No commands",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "В каталоге нет Lua-команд",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Нажмите '+' для создания или положите *.lua в Android/data/.../files/commands",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(commands, key = { it.filePath }) { item ->
                    CommandItemCard(
                        command = item,
                        onEdit = { viewModel.openCommandEditor(item) },
                        onDelete = { viewModel.deleteCommand(item) }
                    )
                }
            }
        }

        // Информационная плашка с точным путем расположения Lua скриптов во внутреннем хранилище устройства
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info, 
                    contentDescription = "Folder info",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Директория размещения команд на диске:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android/data/com.aistudio.discordbot.lucqzx/files/commands",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Overlay встроенного полноэкранного редактора с проверкой синтаксиса
    if (isEditorOpen) {
        LuaEditorDialog(
            command = selectedCommand,
            onDismiss = { viewModel.closeCommandEditor() },
            onSave = { name, code -> viewModel.saveCommand(name, code) }
        )
    }
}

/**
 * [CommandItemCard] — интерактивная M3 карточка с описанием, именем
 * и параметрами конкретной Lua слэш-команды. Содержит кнопки редактирования и анимации удаления.
 */
@Composable
fun CommandItemCard(
    command: LuaCommand,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "/${command.name}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${command.name}.lua",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit command file", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showConfirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = command.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            // Сводный список аргументов (options), если они определены в Lua скрипте
            if (command.optionsArray.length() > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Параметры:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    for (i in 0 until command.optionsArray.length()) {
                        val opt = command.optionsArray.getJSONObject(i)
                        val optName = opt.getString("name")
                        val req = opt.getBoolean("required")
                        Text(
                            text = "$optName${if (req) "*" else ""}",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
                            modifier = Modifier
                                .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }

    // Софт-диалог для подтверждения удаления файла
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Удалить файл команды?") },
            text = { Text("Вы действительно хотите полностью удалить файл команды ${command.name}.lua? Это действие необратимо.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        onDelete()
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * [LuaEditorDialog] — встроенной графический IDE-редактор Lua скриптов.
 * Позволяет писать код, проверять синтаксис Lua на компилируемость виртуальной машиной Luaj
 * и создавать структуру аргументов Slash-команд по шаблонам прямо на телефоне.
 */
@Composable
fun LuaEditorDialog(
    command: LuaCommand?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Boolean
) {
    var fileName by remember { mutableStateOf(command?.name ?: "") }
    var codeText by remember { mutableStateOf(command?.fileContent ?: "") }
    val isNew = command == null
    var hasErrorMsg by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TopBar ИНТЕРФЕЙСА РЕДАКТОРА
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close editor")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isNew) "Создание Lua команды" else "Редактор Lua",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (fileName.trim().isEmpty()) {
                                hasErrorMsg = "Имя команды не может быть пустым!"
                                return@Button
                            }
                            if (codeText.trim().isEmpty()) {
                                hasErrorMsg = "Код команды не может быть пустым!"
                                return@Button
                            }
                            val validName = fileName.lowercase().trim().replace(" ", "")
                            val ok = onSave(validName, codeText)
                            if (!ok) {
                                hasErrorMsg = "Не удалось сохранить Lua-скрипт."
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сохранить")
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Текстовое поле ввода имени команды
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Имя слэш-команды (латиницей, строчными)") },
                        placeholder = { Text("например: profile") },
                        singleLine = true,
                        enabled = isNew,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (hasErrorMsg.isNotEmpty()) {
                        Text(
                            text = hasErrorMsg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // Панель быстрого макро-заполнения (быстрая вставка шаблонов)
                    Text(
                        text = "Вставить код:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val template = """
command = {
    name = "${if (fileName.isEmpty()) "test" else fileName.lowercase().trim()}",
    description = "Описание вашей команды",
    options = {}
}

function execute(interaction)
    return "Привет, @" .. interaction.user .. "! Это ответ Lua-бот команды!"
end
                                """.trimIndent()
                                codeText = template
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("+ Шаблон", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }

                        Button(
                            onClick = {
                                val optionSnippet = """
        {
            name = "username",
            description = "Имя пользователя для поиска",
            type = 3, -- 3 - STRING, 4 - INT, 10 - NUMBER (Double)
            required = true
        },
                                """
                                codeText = codeText.replace("options = {}", "options = {\n$optionSnippet\n}")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("+ Добавить Опцию", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }

                        Button(
                            onClick = {
                                // Валидация синтаксиса Lua прямо на лету с компиляцией в рантайме Luaj
                                try {
                                    val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
                                    globals.load(codeText)
                                    hasErrorMsg = "✅ Синтаксис Lua корректен!"
                                } catch (e: Exception) {
                                    hasErrorMsg = "❌ Ошибка компиляции: ${e.localizedMessage}"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Проверить код", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Файл скрипта Lua (.lua)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Основная печатная область написания Lua кода с поддержкой Monospace шрифта
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        placeholder = { Text("-- Напишите Lua-код прямо здесь") },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }
    }
}

/**
 * [GuideTab] — вкладка справочных материалов ("Справка").
 * Оформлена в виде вертикального списка (LazyColumn) картонных руководств, обучающая
 * правильному созданию, переносу скриптов и пониманию спецификации Discord Slash Commands API.
 */
@Composable
fun GuideTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚀 Как запустить Discord Бота?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Перейдите в Discord Developer Portal (discord.com/developers/applications)\n" +
                               "2. Создайте Приложение, перейдите в раздел 'Bot' и скопируйте 'Token' бота.\n" +
                               "3. Вставьте скопированный токен в текстовое поле во вкладке 'Панель' и нажмите 'Запустить бота'.\n" +
                               "4. Пригласите своего бота на сервер Discord с правами слэш-команд (permissions integer scopes: bot + applications.commands).",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📂 Где лежат файлы команд и как их перенести?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Программа автоматически синхронизируется со следующей папкой внешнего хранилища Android:\n\n" +
                               "📁 Android/data/com.aistudio.discordbot.lucqzx/files/commands\n\n" +
                               "Вы можете перекидывать туда готовые .lua файлы следующими путями:\n" +
                               "• Подключив Android-устройство по USB к компьютеру\n" +
                               "• Используя любой файловый менеджер (напр. 'Solid Explorer' или 'ZArchiver') прямо на телефоне\n" +
                               "• Создавая и редактируя команды напрямую в приложении через графический редактор во вкладке 'Команды Lua'.",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📝 Инструкция к синтаксису Lua",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Каждая команда описывается в отдельном *.lua файле и ОБЯЗАТЕЛЬНО должна содержать две вещи:\n\n" +
                               "1. Глобальную таблицу command с её спецификацией (имя, описание, параметры).\n" +
                               "2. Глобальную функцию execute(interaction), которая принимает контекст вызова и возвращает либо строку ответа, либо таблицу с флагом ephemeral.\n\n" +
                               "Коды типов параметров Discord:\n" +
                               "• 3 (STRING) – Текст\n" +
                               "• 4 (INTEGER) – Целое число\n" +
                               "• 10 (NUMBER) – Дробное число\n" +
                               "• 5 (BOOLEAN) – Логический тип (true/false)",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF2E2E33))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Lua example", tint = Color(0xFFA78BFA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Код примера (say.lua)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA78BFA)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = """
command = {
    name = "say",
    description = "Повторить введенный пользователем текст",
    options = {
        {
            name = "text",
            description = "Текст сообщения",
            type = 3, -- STRING
            required = true
        }
    }
}

function execute(interaction)
    -- Получение параметров
    local text = interaction.options["text"]
    local user = interaction.user
    
    return "Пользователь @" .. user .. " повторяет: " .. text
end
                        """.trimIndent(),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
                        color = Color(0xFFF4F4F5)
                    )
                }
            }
        }
    }
}
