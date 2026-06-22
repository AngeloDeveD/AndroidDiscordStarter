package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

@Composable
fun BotDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: BotViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentStatus by viewModel.botStatus.collectAsState()

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        // High Density Header Section (px-4 pt-6 pb-2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
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
                // Inline status indicator with pulsing shadow-like appearance
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val statusDotColor = when (currentStatus) {
                        BotStatus.RUNNING -> Color(0xFF4CAF50)
                        BotStatus.CONNECTING -> Color(0xFFFFA726)
                        else -> Color(0xFFEF5350)
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
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

            // High Density Circle Settings/Help Button
            IconButton(
                onClick = { selectedTab = 2 },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFEADDFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF21005D),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Tab Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> ConsoleTab(viewModel = viewModel)
                1 -> CommandsTab(viewModel = viewModel)
                2 -> GuideTab()
            }
        }

        // High Density Bottom Navigation Bar (h-20 bg-[#F3EDF7] border-t border-[#CAC4D0]/50)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                modifier = Modifier.height(72.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                val items = listOf(
                    Triple("Бот", Icons.Default.PlayArrow, 0),
                    Triple("Команды", Icons.Default.List, 1),
                    Triple("Справка", Icons.Default.Info, 2)
                )
                items.forEach { (title, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = if (selectedTab == index) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTab == index) Color(0xFF1D192B) else Color(0xFF49454F)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0xFFE8DEF8)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: BotStatus) {
    val (color, text) = when (status) {
        BotStatus.STOPPED -> Color(0xFFE57373) to "Остановлен"
        BotStatus.CONNECTING -> Color(0xFFFFB74D) to "Подключение"
        BotStatus.RUNNING -> Color(0xFF81C784) to "Активен"
        BotStatus.ERROR -> Color(0xFFE57373) to "Ошибка"
    }

    // Pulse animation for active or connecting states
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Token Card (Authorization)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "АВТОРИЗАЦИЯ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                OutlinedTextField(
                    value = token,
                    onValueChange = { viewModel.saveToken(it) },
                    placeholder = { Text("Токен Discord Бота") },
                    leadingIcon = { 
                        Icon(
                            imageVector = Icons.Default.Lock, 
                            contentDescription = "Token Lock", 
                            tint = Color(0xFF49454F),
                            modifier = Modifier.size(18.dp)
                        ) 
                    },
                    trailingIcon = {
                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                            Icon(
                                imageVector = Icons.Default.Info, 
                                contentDescription = "Toggle Visibility",
                                tint = Color(0xFF49454F),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = currentStatus == BotStatus.STOPPED || currentStatus == BotStatus.ERROR,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF79747E),
                        unfocusedBorderColor = Color(0xFF79747E).copy(alpha = 0.4f)
                    )
                )
            }
        }

        // Scripts & Controls 2-column grid
        Row(
            modifier = Modifier.fillMaxWidth().height(92.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Script Info Card
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Lua Scripts",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Lua Скрипты",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${commands.size} команд обнаружено",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF49454F),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Right active start/stop trigger button
            val btnBg = when (currentStatus) {
                BotStatus.STOPPED, BotStatus.ERROR -> Color(0xFF6750A4)
                BotStatus.CONNECTING -> Color(0xFFEF6C00)
                BotStatus.RUNNING -> Color(0xFFC62828)
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
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = btnBg),
                contentPadding = PaddingValues(12.dp)
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
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = btnText.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Output Terminal Console Section
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
            border = BorderStroke(1.dp, Color(0xFF49454F))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Technical terminal header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Console",
                            tint = Color(0xFF938F99),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "КОНСОЛЬ ВЫВОДА",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            letterSpacing = 1.5.sp,
                            fontSize = 10.sp
                        )
                    }

                    // Terminal Utilities (copy, clear)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { viewModel.clearLogs() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear logs",
                                tint = Color(0xFF938F99),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
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
                                tint = Color(0xFF938F99),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Inner scrollable console rows
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
                                tint = Color(0xFF49454F),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ожидание нажатия кнопки запуска...",
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                color = Color(0xFF938F99)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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

@Composable
fun LogLineItem(log: LogEntry) {
    val levelColor = when (log.level) {
        LogLevel.INFO -> Color(0xFFD0BCFF)
        LogLevel.WARNING -> Color(0xFFFFB4AB)
        LogLevel.ERROR -> Color(0xFFEF5350)
        LogLevel.DEBUG -> Color(0xFF938F99)
        LogLevel.SUCCESS -> Color(0xFFD0BCFF)
        LogLevel.BOT -> Color(0xFFEADDFF)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // [Time]
        Text(
            text = "[${log.timestamp}]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            ),
            color = Color(0xFF938F99),
            modifier = Modifier.padding(end = 6.dp)
        )

        // [LEVEL/TAG]
        Text(
            text = "${log.level.name}:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            color = levelColor,
            modifier = Modifier.padding(end = 6.dp)
        )

        // Log message
        Text(
            text = log.message,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp
            ),
            color = Color.White
        )
    }
}

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
            
            // Add Command button
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

        // Hot Sync Banner if Bot is Active
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

        // List files scrollable
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

        // Action info at the bottom of directory
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

    // In-app Fullscreen Lua Editor Dialog overlay
    if (isEditorOpen) {
        LuaEditorDialog(
            command = selectedCommand,
            onDismiss = { viewModel.closeCommandEditor() },
            onSave = { name, code -> viewModel.saveCommand(name, code) }
        )
    }
}

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

            // Options summary if present
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
                // Editor TopBar
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
                    // File name inputs
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

                    // Helper macros Row
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
                                // Simple syntax validation with luaj compiles compilation
                                try {
                                    val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
                                    globals.load(codeText)
                                    hasErrorMsg = "✅ Синтаксис Lua корректен!"
                                } catch (e: Exception) {
                                    hasErrorMsg = "❌ Ошибка компиляции Lua: ${e.localizedMessage}"
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

                    // Text Field for Mono Code
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
