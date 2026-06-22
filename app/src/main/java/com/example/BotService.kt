package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Фоновая служба (Foreground Service) для обеспечения стабильной и непрерывной работы Discord-бота.
 * Управляет жизненным циклом бота при переходе приложения в фоновый режим и отображает постоянное уведомление с кнопкой остановки.
 */
class BotService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var statusJob: Job? = null

    companion object {
        const val CHANNEL_ID = "discord_bot_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val EXTRA_TOKEN = "com.example.EXTRA_TOKEN"

        /**
         * Запускает фоновую службу бота.
         */
        fun startService(context: Context, token: String) {
            val intent = Intent(context, BotService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TOKEN, token)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Останавливает службу через отправку интента со специальным экшеном.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, BotService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeBotStatus()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopBotAndSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            val token = intent.getStringExtra(EXTRA_TOKEN) ?: ""
            if (token.isNotEmpty()) {
                val currentStatus = DiscordGatewayClient.status.value
                if (currentStatus == BotStatus.STOPPED || currentStatus == BotStatus.ERROR) {
                    DiscordGatewayClient.start(applicationContext, token)
                }
            }
            showForegroundNotification(DiscordGatewayClient.status.value)
        }

        return START_STICKY
    }

    private fun observeBotStatus() {
        statusJob?.cancel()
        statusJob = serviceScope.launch {
            DiscordGatewayClient.status.collect { status ->
                if (status == BotStatus.STOPPED) {
                    stopForegroundAndSelf()
                } else {
                    showForegroundNotification(status)
                }
            }
        }
    }

    private fun showForegroundNotification(status: BotStatus) {
        val stopIntent = Intent(this, BotService::class.java).apply {
            action = ACTION_STOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, flags)

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val mainActivityPendingIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, flags)

        val statusText = when (status) {
            BotStatus.CONNECTING -> "Подключение к Discord..."
            BotStatus.RUNNING -> "Бот успешно запущен и работает"
            BotStatus.ERROR -> "Ошибка подключения к Discord"
            BotStatus.STOPPED -> "Бот остановлен"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Discord Launcher Bot")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // Безопасная системная иконка диалога
            .setContentIntent(mainActivityPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Остановить",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopBotAndSelf() {
        DiscordGatewayClient.stop()
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Фоновая служба бота",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал для уведомлений о состоянии работы Discord-бота"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
