package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.repository.CallLogRepository
import com.example.data.repository.SmsRepository
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SmsGatewayService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var repository: SmsRepository
    private var syncJob: Job? = null
    private var isRunning = false

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            serviceScope.launch(Dispatchers.IO) {
                repository.log("INFO", "تغییر Provider پیامک تشخیص داده شد", "ContentObserver")
                repository.syncInboxAndDetectDeletions()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = SmsRepository(this)
        createNotificationChannel()
        try {
            contentResolver.registerContentObserver(Uri.parse("content://sms/"), true, smsObserver)
            serviceScope.launch {
                repository.log("INFO", "شنودگر Provider پیامک ثبت شد", "Service")
            }
        } catch (_: Exception) {
            serviceScope.launch { repository.log("ERROR", "ثبت شنودگر پیامک ناموفق بود", "Service") }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopGatewayService()
            return START_NOT_STICKY
        }
        if (!isRunning) {
            isRunning = true
            startForegroundNotification()
            startPeriodicSync()
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val stopIntent = Intent(this, SmsGatewayService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("درگاه پیامک SMS Center فعال است")
            .setContentText("در حال همگام‌سازی Provider و صف پیامک")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(launchPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "غیرفعال‌سازی", stopPendingIntent)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            repository.log("INFO", "سرویس پس‌زمینه Gateway شروع شد", "Service")
            while (isActive) {
                val settings = repository.settingsDao.getSettings()
                if (settings?.isGatewayEnabled == true) {
                    repository.pollPendingMessagesFromServer()
                    repository.processOutgoingQueue()
                    repository.syncInboxAndDetectDeletions()
                    if (
                        settings.callLogSyncEnabled &&
                        ContextCompat.checkSelfPermission(
                            this@SmsGatewayService,
                            Manifest.permission.READ_CALL_LOG
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val calls = CallLogRepository(this@SmsGatewayService)
                        calls.refreshFromProvider(settings.deviceId)
                        calls.syncPending(settings)
                    }
                    val delaySeconds = settings.syncIntervalSeconds.coerceAtLeast(5)
                    delay(delaySeconds * 1000L)
                } else {
                    delay(10_000)
                }
            }
        }
    }

    private fun stopGatewayService() {
        serviceScope.launch {
            repository.settingsDao.getSettings()?.let {
                repository.settingsDao.saveSettings(it.copy(isGatewayEnabled = false))
            }
            repository.log("INFO", "سرویس پس‌زمینه متوقف شد", "Service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int) {
        syncJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        syncJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(smsObserver) }
        syncJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سرویس درگاه پیامک",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "SmsGatewayServiceChannel"
        const val NOTIFICATION_ID = 45678
    }
}
