package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.repository.SmsRepository
import kotlinx.coroutines.*

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
                repository.log("INFO", "تشخیص تغییر در دیتابیس پیامک‌های سیستم. شروع همگام‌سازی...", "ContentObserver")
                repository.syncInboxAndDetectDeletions()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = SmsRepository(this)
        createNotificationChannel()

        // Register ContentObserver on system SMS database
        try {
            contentResolver.registerContentObserver(
                Uri.parse("content://sms/"),
                true,
                smsObserver
            )
            serviceScope.launch {
                repository.log("INFO", "شنودگر تغییرات دیتابیس پیامک (ContentObserver) با موفقیت ثبت شد", "Service")
            }
        } catch (e: Exception) {
            serviceScope.launch {
                repository.log("ERROR", "خطا در ثبت ContentObserver: ${e.message}", "Service")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopService()
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

        // Make notification click return to app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("درگاه پیامک SMS Center فعال است")
            .setContentText("در حال شنود دیتابیس و پردازش صف پیامک‌ها در پس‌زمینه...")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "غیرفعال‌سازی درگاه", stopPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            repository.log("INFO", "سرویس پس‌زمینه درگاه پیامک راه‌اندازی شد", "Service")
            while (isActive) {
                val settings = repository.settingsDao.getSettings()
                if (settings != null && settings.isGatewayEnabled) {
                    // 1. Fetch pending from Web Panel
                    repository.pollPendingMessagesFromServer()

                    // 2. Process Outbox Send
                    repository.processOutgoingQueue()

                    // 3. Keep Inbox fully synced
                    repository.syncInboxAndDetectDeletions()

                    val delaySecs = if (settings.syncIntervalSeconds > 5) settings.syncIntervalSeconds else 30
                    delay(delaySecs * 1000L)
                } else {
                    // Idle if gateway is disabled
                    delay(10000)
                }
            }
        }
    }

    private fun stopService() {
        serviceScope.launch {
            repository.log("INFO", "سرویس پس‌زمینه متوقف شد", "Service")
            repository.settingsDao.getSettings()?.let {
                repository.settingsDao.saveSettings(it.copy(isGatewayEnabled = false))
            }
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(smsObserver)
        syncJob?.cancel()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "سرویس درگاه پیامک",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "SmsGatewayServiceChannel"
        const val NOTIFICATION_ID = 45678
    }
}
