package com.example.data.repository

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.data.local.*
import com.example.data.remote.SmsApiClient
import com.example.data.remote.WebhookPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SmsRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    val settingsDao = db.settingsDao()
    val syncedSmsDao = db.syncedSmsDao()
    val tombstoneDao = db.tombstoneDao()
    val smsQueueDao = db.smsQueueDao()
    val logDao = db.logDao()

    val settingsFlow: Flow<GatewaySettings?> = settingsDao.getSettingsFlow()
    val syncedSmsFlow: Flow<List<SyncedSms>> = syncedSmsDao.getAllSyncedFlow()
    val queueFlow: Flow<List<SmsQueueItem>> = smsQueueDao.getAllQueueFlow()
    val tombstonesFlow: Flow<List<Tombstone>> = tombstoneDao.getAllTombstonesFlow()
    val logsFlow: Flow<List<LogEntry>> = logDao.getLogsFlow()

    // Log internally and save to local DB
    suspend fun log(level: String, message: String, tag: String = "SmsRepository") {
        withContext(Dispatchers.IO) {
            logDao.insert(LogEntry(level = level, message = message, tag = tag))
        }
    }

    // Clear history/logs
    suspend fun clearLogs() = logDao.clearLogs()
    suspend fun clearQueueHistory() = smsQueueDao.clearHistory()

    // -------------------------------------------------------------------------
    // SMS READING & DELETION SYNC ENGINE
    // -------------------------------------------------------------------------

    suspend fun syncInboxAndDetectDeletions(): SyncResult = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettings() ?: return@withContext SyncResult(false, "تنظیمات یافت نشد")
        if (!settings.isGatewayEnabled) {
            return@withContext SyncResult(false, "درگاه غیرفعال است")
        }

        log("INFO", "شروع همگام‌سازی پیامک‌ها و تشخیص حذف‌ها...", "InboxSync")

        // 1. Check permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            log("WARN", "مجوز READ_SMS وجود ندارد", "InboxSync")
            return@withContext SyncResult(false, "مجوز دسترسی به پیامک‌ها داده نشده است")
        }

        try {
            val contentResolver = context.contentResolver
            val uri = Uri.parse("content://sms/")

            // Query SMS from last 7 days to avoid heavy load
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val selection = "date >= ?"
            val selectionArgs = arrayOf(sevenDaysAgo.toString())
            val projection = arrayOf("_id", "address", "body", "date", "type")

            val cursor = contentResolver.query(uri, projection, selection, selectionArgs, "date DESC")
            val systemSmsMap = mutableMapOf<Long, SyncedSms>()

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow("_id")
                val addressIdx = it.getColumnIndexOrThrow("address")
                val bodyIdx = it.getColumnIndexOrThrow("body")
                val dateIdx = it.getColumnIndexOrThrow("date")
                val typeIdx = it.getColumnIndexOrThrow("type")

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val address = it.getString(addressIdx) ?: ""
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx) // 1 = Inbox, 2 = Sent

                    // Only track inbox and sent messages
                    if (type == 1 || type == 2) {
                        systemSmsMap[id] = SyncedSms(
                            id = id,
                            address = address,
                            body = body,
                            date = date,
                            simSlot = type // Just record type as simSlot indicator for simplicity
                        )
                    }
                }
            }

            // 2. Load our local cached synced messages list
            val localSyncedList = syncedSmsDao.getAllSynced()
            val localSyncedMap = localSyncedList.associateBy { it.id }

            var newIncomingCount = 0
            var deletedCount = 0

            // 3. Find NEW messages (In system SMS list but NOT in local synced list)
            for ((id, systemSms) in systemSmsMap) {
                if (!localSyncedMap.containsKey(id)) {
                    // Check if this message was previously tombstoned (already deleted)
                    val fingerprint = systemSms.getFingerprint()
                    val isTombstoned = tombstoneDao.isTombstoned(systemSms.address, systemSms.date, fingerprint) > 0

                    if (!isTombstoned) {
                        // This is a new incoming message! Forward it to Webhook
                        log("INFO", "پیام جدید دریافت شد: از ${systemSms.address}", "InboxSync")
                        val payload = WebhookPayload(
                            event = "sms_received",
                            deviceId = settings.deviceId,
                            from = systemSms.address,
                            body = systemSms.body,
                            timestamp = systemSms.date,
                            simSlot = systemSms.simSlot,
                            fingerprint = fingerprint
                        )

                        val isSuccess = SmsApiClient.sendWebhook(settings.webhookUrl, settings.webhookSecret, payload)
                        if (isSuccess) {
                            log("INFO", "پیام با موفقیت به وب‌هوک ارسال شد", "InboxSync")
                        } else {
                            log("WARN", "خطا در ارسال پیام به وب‌هوک", "InboxSync")
                        }

                        // Save in local DB as synced anyway so we don't spam
                        syncedSmsDao.insert(systemSms)
                        newIncomingCount++
                    }
                }
            }

            // 4. Find DELETED messages (In our local synced list but NOT in the system SMS database anymore)
            // Limit to messages newer than 7 days ago to avoid stale comparisons
            for (localSms in localSyncedList) {
                if (localSms.date >= sevenDaysAgo && !systemSmsMap.containsKey(localSms.id)) {
                    // Message was deleted from the device!
                    log("INFO", "تشخیص حذف پیامک: فرستنده ${localSms.address}", "InboxSync")
                    
                    val fingerprint = localSms.getFingerprint()
                    
                    // Create Tombstone so we don't restore it
                    tombstoneDao.insert(
                        Tombstone(
                            address = localSms.address,
                            date = localSms.date,
                            fingerprint = fingerprint
                        )
                    )

                    // Post deletion event to webhook
                    val payload = WebhookPayload(
                        event = "sms_deleted",
                        deviceId = settings.deviceId,
                        from = localSms.address,
                        timestamp = localSms.date,
                        fingerprint = fingerprint
                    )
                    
                    SmsApiClient.sendWebhook(settings.webhookUrl, settings.webhookSecret, payload)

                    // Remove from active synced list
                    syncedSmsDao.deleteById(localSms.id)
                    deletedCount++
                }
            }

            log("INFO", "همگام‌سازی پایان یافت. جدید: $newIncomingCount، حذف شده: $deletedCount", "InboxSync")
            return@withContext SyncResult(true, "همگام‌سازی موفق. پیام‌های جدید: $newIncomingCount، حذف‌شده: $deletedCount")

        } catch (e: Exception) {
            log("ERROR", "خطا در همگام‌سازی پیامک‌ها: ${e.message}", "InboxSync")
            return@withContext SyncResult(false, "خطا در پردازش: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // QUEUE POLLING & MESSAGE SENDING ENGINE
    // -------------------------------------------------------------------------

    suspend fun pollPendingMessagesFromServer(): SyncResult = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettings() ?: return@withContext SyncResult(false, "تنظیمات یافت نشد")
        if (!settings.isGatewayEnabled || settings.serverUrl.isBlank()) {
            return@withContext SyncResult(false, "درگاه یا URL پنل غیرفعال است")
        }

        log("INFO", "در حال دریافت پیامک‌های در صف از پنل وب...", "QueuePoll")
        try {
            val apiService = SmsApiClient.getService(settings.serverUrl)
            val responseList = apiService.getPendingMessages(
                url = "${settings.serverUrl.removeSuffix("/")}/api/messages/pending",
                apiKey = "Bearer ${settings.apiKey}",
                deviceId = settings.deviceId
            )

            var addedCount = 0
            for (msg in responseList) {
                val requestId = msg.getFinalRequestId()
                val phone = msg.getFinalPhoneNumber()
                val body = msg.getFinalBody()

                if (phone.isBlank() || body.isBlank()) continue

                // Avoid duplicate requestId
                val existing = smsQueueDao.getByRequestId(requestId)
                if (existing == null) {
                    val newItem = SmsQueueItem(
                        requestId = requestId,
                        phoneNumber = phone,
                        messageBody = body,
                        simSlot = msg.getFinalSimSlot(),
                        status = "PENDING",
                        scheduledAt = msg.scheduledAt ?: 0,
                        expiresAt = msg.expiresAt ?: 0
                    )
                    smsQueueDao.insert(newItem)
                    addedCount++
                }
            }

            log("INFO", "$addedCount پیام جدید به صف محلی اضافه شد", "QueuePoll")
            return@withContext SyncResult(true, "دریافت موفق. پیام‌های جدید: $addedCount")
        } catch (e: Exception) {
            log("ERROR", "خطا در اتصال به پنل وب: ${e.message}", "QueuePoll")
            return@withContext SyncResult(false, "خطا در دریافت پیام‌ها: ${e.message}")
        }
    }

    suspend fun processOutgoingQueue(): Unit = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettings() ?: return@withContext
        if (!settings.isGatewayEnabled) return@withContext

        // 1. Check Working Hours
        if (!isWithinWorkingHours(settings.workingHoursStart, settings.workingHoursEnd)) {
            log("WARN", "خارج از ساعات کاری تنظیم شده (${settings.workingHoursStart} تا ${settings.workingHoursEnd}). صف متوقف شد.", "SmsSender")
            return@withContext
        }

        // 2. Fetch PENDING items
        val pendingList = smsQueueDao.getPendingToProcess()
        if (pendingList.isEmpty()) return@withContext

        log("INFO", "پردازش صف ارسال پیامک (${pendingList.size} پیام در انتظار)...", "SmsSender")

        for (item in pendingList) {
            // Check Limits
            if (isRateLimitExceeded(settings)) {
                log("WARN", "محدودیت ارسال پیامک در دقیقه/ساعت/روز فراتر رفته است. توقف ارسال.", "SmsSender")
                break
            }

            // Check TTL/Expiration
            if (item.expiresAt > 0 && System.currentTimeMillis() > item.expiresAt) {
                log("WARN", "پیام ${item.id} منقضی شده است. وضعیت لغو شد.", "SmsSender")
                smsQueueDao.update(item.copy(status = "CANCELLED", errorMessage = "پیام منقضی شده است"))
                notifyStatusChange(settings, item.requestId, "CANCELLED", "منقضی شده")
                continue
            }

            // Set state to PROCESSING
            smsQueueDao.update(item.copy(status = "PROCESSING"))

            try {
                if (settings.isTestMode) {
                    // Simulate successfully sent in Test Mode
                    log("INFO", "ارسال شبیه‌سازی شده (Test Mode): به ${item.phoneNumber} با متن: ${item.messageBody}", "SmsSender")
                    smsQueueDao.update(item.copy(status = "SENT"))
                    notifyStatusChange(settings, item.requestId, "SENT", null)
                } else {
                    // Actual Send
                    val sentIntent = PendingIntent.getBroadcast(
                        context,
                        item.id,
                        Intent("com.example.SMS_SENT").putExtra("item_id", item.id),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val deliveredIntent = PendingIntent.getBroadcast(
                        context,
                        item.id,
                        Intent("com.example.SMS_DELIVERED").putExtra("item_id", item.id),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    sendSmsViaManager(
                        phoneNumber = item.phoneNumber,
                        message = item.messageBody,
                        simSlot = if (item.simSlot >= 0) item.simSlot else settings.simSlotSelection,
                        sentIntent = sentIntent,
                        deliveredIntent = deliveredIntent
                    )
                }
            } catch (e: Exception) {
                log("ERROR", "خطا در ارسال پیام ${item.id}: ${e.message}", "SmsSender")
                val retryCount = item.retryCount + 1
                if (retryCount >= 3) {
                    smsQueueDao.update(item.copy(status = "FAILED", retryCount = retryCount, errorMessage = e.message))
                    notifyStatusChange(settings, item.requestId, "FAILED", e.message)
                } else {
                    // Leave PENDING for backoff retry
                    smsQueueDao.update(item.copy(status = "PENDING", retryCount = retryCount, errorMessage = "تلاش مجدد: ${e.message}"))
                }
            }

            // Slight delay to be safe and avoid flooding carrier network
            Thread.sleep(1000)
        }
    }

    // -------------------------------------------------------------------------
    // HELPERS & VALIDATIONS
    // -------------------------------------------------------------------------

    private fun sendSmsViaManager(
        phoneNumber: String,
        message: String,
        simSlot: Int,
        sentIntent: PendingIntent,
        deliveredIntent: PendingIntent
    ) {
        val smsManager = getSmsManagerForSlot(simSlot)
        val parts = smsManager.divideMessage(message)

        if (parts.size > 1) {
            val sentIntents = ArrayList<PendingIntent>().apply { add(sentIntent); for (i in 1 until parts.size) add(sentIntent) }
            val deliveredIntents = ArrayList<PendingIntent>().apply { add(deliveredIntent); for (i in 1 until parts.size) add(deliveredIntent) }
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents)
        } else {
            smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, deliveredIntent)
        }
    }

    private fun getSmsManagerForSlot(simSlot: Int): SmsManager {
        if (simSlot < 0) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val activeList = subscriptionManager.activeSubscriptionInfoList
            val info = activeList?.find { it.simSlotIndex == simSlot }
            if (info != null) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(info.subscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(info.subscriptionId)
                }
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private suspend fun isRateLimitExceeded(settings: GatewaySettings): Boolean {
        // Simple rate check based on queue items in the DB
        val now = System.currentTimeMillis()
        val list = smsQueueDao.getQueueByStatus("SENT") + smsQueueDao.getQueueByStatus("DELIVERED")

        val oneMinAgo = now - 60000
        val oneHourAgo = now - 3600000
        val oneDayAgo = now - 86400000

        val sentInMin = list.count { it.createdAt >= oneMinAgo }
        val sentInHour = list.count { it.createdAt >= oneHourAgo }
        val sentInDay = list.count { it.createdAt >= oneDayAgo }

        return sentInMin >= settings.limitSmsPerMin ||
               sentInHour >= settings.limitSmsPerHour ||
               sentInDay >= settings.limitSmsPerDay
    }

    private fun isWithinWorkingHours(start: String, end: String): Boolean {
        return try {
            val nowStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            nowStr in start..end
        } catch (e: Exception) {
            true
        }
    }

    // Report status change back to Flask Panel Webhook or endpoint
    suspend fun notifyStatusChange(
        settings: GatewaySettings,
        requestId: String,
        status: String,
        error: String?
    ) {
        val payload = WebhookPayload(
            event = "sms_$status".lowercase(),
            deviceId = settings.deviceId,
            requestId = requestId,
            timestamp = System.currentTimeMillis(),
            errorMessage = error
        )
        // 1. Post to Webhook
        SmsApiClient.sendWebhook(settings.webhookUrl, settings.webhookSecret, payload)

        // 2. Or post to dedicated API endpoint if configured
        if (settings.serverUrl.isNotBlank()) {
            try {
                val api = SmsApiClient.getService(settings.serverUrl)
                api.updateMessageStatus(
                    url = "${settings.serverUrl.removeSuffix("/")}/api/messages/status",
                    apiKey = "Bearer ${settings.apiKey}",
                    payload = payload
                )
            } catch (e: Exception) {
                // Squelch background errors but log
                e.printStackTrace()
            }
        }
    }
}

data class SyncResult(val isSuccess: Boolean, val message: String)
