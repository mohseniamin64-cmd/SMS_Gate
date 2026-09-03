package com.example.data.repository

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.data.local.GatewaySettings
import com.example.data.local.SmsDirection
import com.example.data.local.SmsQueueItem
import com.example.data.local.SyncedSms
import com.example.data.local.Tombstone
import com.example.data.remote.HealthResponse
import com.example.data.remote.LanEndpointValidator
import com.example.data.remote.SmsApiClient
import com.example.data.remote.WebhookPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
    val logsFlow = logDao.getLogsFlow()

    suspend fun log(level: String, message: String, tag: String = "SmsRepository") {
        withContext(Dispatchers.IO) {
            logDao.insert(com.example.data.local.LogEntry(level = level, message = message, tag = tag))
        }
    }

    suspend fun clearLogs() = logDao.clearLogs()
    suspend fun clearQueueHistory() = smsQueueDao.clearHistory()

    suspend fun enqueueSms(
        requestId: String,
        phoneNumber: String,
        messageBody: String,
        simSlot: Int
    ): SmsQueueItem = withContext(Dispatchers.IO) {
        val safeRequestId = requestId.trim().ifBlank { "phone_" + UUID.randomUUID() }
        val existing = smsQueueDao.getByRequestId(safeRequestId)
        if (existing != null) return@withContext existing
        val item = SmsQueueItem(
            requestId = safeRequestId,
            phoneNumber = phoneNumber.trim(),
            messageBody = messageBody.trim(),
            simSlot = simSlot,
            status = "PENDING"
        )
        smsQueueDao.insert(item)
        log("INFO", "پیامک از API سرور گوشی در صف ثبت شد", "PhoneServer")
        smsQueueDao.getByRequestId(safeRequestId) ?: item
    }

    /**
     * Reads the complete real Telephony provider snapshot. The provider type
     * is the only source of direction; SIM slot is read independently.
     */
    suspend fun syncInboxAndDetectDeletions(): SyncResult = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettings() ?: GatewaySettings()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            log("WARN", "مجوز خواندن پیامک وجود ندارد", "InboxSync")
            return@withContext SyncResult(false, "مجوز دسترسی به پیامک‌ها داده نشده است")
        }

        log("INFO", "همگام‌سازی واقعی پیامک‌ها آغاز شد", "InboxSync")
        val providerMessages = linkedMapOf<Long, SyncedSms>()
        try {
            val projection = arrayOf("_id", "address", "body", "date", "type", "sub_id")
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "date DESC"
            ) ?: return@withContext SyncResult(false, "snapshot پیامک‌ها معتبر نیست")

            try {
                val idIndex = cursor.getColumnIndexOrThrow("_id")
                val addressIndex = cursor.getColumnIndexOrThrow("address")
                val bodyIndex = cursor.getColumnIndexOrThrow("body")
                val dateIndex = cursor.getColumnIndexOrThrow("date")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val subscriptionIndex = cursor.getColumnIndex("sub_id")
                while (cursor.moveToNext()) {
                    val direction = SmsDirection.fromTelephonyType(cursor.getInt(typeIndex))
                    if (direction == SmsDirection.UNKNOWN) continue
                    val subscriptionId = if (subscriptionIndex >= 0 && !cursor.isNull(subscriptionIndex)) {
                        cursor.getInt(subscriptionIndex)
                    } else {
                        -1
                    }
                    val message = SyncedSms(
                        id = cursor.getLong(idIndex),
                        address = cursor.getString(addressIndex) ?: "",
                        body = cursor.getString(bodyIndex) ?: "",
                        date = cursor.getLong(dateIndex),
                        simSlot = resolveSimSlot(subscriptionId),
                        direction = direction.storageValue
                    )
                    providerMessages[message.id] = message
                }
            } finally {
                cursor.close()
            }
        } catch (_: Exception) {
            log("WARN", "snapshot پیامک‌ها کامل خوانده نشد؛ حذف بررسی نشد", "InboxSync")
            return@withContext SyncResult(false, "snapshot پیامک‌ها معتبر نیست")
        }

        val localMessages = syncedSmsDao.getAllSynced()
        val tombstoneKeys = tombstoneDao.getAllTombstones()
            .mapTo(mutableSetOf()) { SmsTombstoneKey(it.address, it.date, it.fingerprint) }
        val plan = SmsSyncPlanner.plan(
            localMessages = localMessages,
            providerSnapshot = SmsProviderSnapshot(providerMessages, isComplete = true),
            tombstones = tombstoneKeys
        )

        var incomingCount = 0
        for (message in plan.newMessages) {
            if (message.direction == SmsDirection.INCOMING.storageValue) {
                incomingCount++
                if (settings.webhookUrl.isNotBlank()) {
                    SmsApiClient.sendWebhook(
                        settings.webhookUrl,
                        settings.webhookSecret,
                        WebhookPayload(
                            event = "sms_received",
                            deviceId = settings.deviceId,
                            from = message.address,
                            body = message.body,
                            timestamp = message.date,
                            simSlot = message.simSlot,
                            fingerprint = message.getFingerprint()
                        )
                    )
                }
            }
        }

        val activeMessages = providerMessages.values.filterNot { it.matchesTombstone(tombstoneKeys) }
        syncedSmsDao.insertAll(activeMessages)

        var deletedCount = 0
        for (localMessage in plan.deletedMessages) {
            val fingerprint = localMessage.getFingerprint()
            tombstoneDao.insert(
                Tombstone(
                    address = localMessage.address,
                    date = localMessage.date,
                    fingerprint = fingerprint
                )
            )
            if (settings.webhookUrl.isNotBlank()) {
                SmsApiClient.sendWebhook(
                    settings.webhookUrl,
                    settings.webhookSecret,
                    WebhookPayload(
                        event = "sms_deleted",
                        deviceId = settings.deviceId,
                        from = localMessage.address,
                        timestamp = localMessage.date,
                        fingerprint = fingerprint
                    )
                )
            }
            syncedSmsDao.deleteById(localMessage.id)
            deletedCount++
        }

        log(
            "INFO",
            "همگام‌سازی پیامک پایان یافت؛ جدید: " + plan.newMessages.size + "، ورودی: " +
                incomingCount + "، حذف‌شده: " + deletedCount,
            "InboxSync"
        )
        SyncResult(
            true,
            "همگام‌سازی موفق؛ پیام‌های جدید: " + plan.newMessages.size + "، حذف‌شده: " + deletedCount
        )
    }

    private fun resolveSimSlot(subscriptionId: Int): Int {
        if (subscriptionId < 0 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        ) {
            return -1
        }
        return try {
            val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            manager.activeSubscriptionInfoList
                ?.firstOrNull { it.subscriptionId == subscriptionId }
                ?.simSlotIndex ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    suspend fun checkPanelConnection(settings: GatewaySettings): SyncResult = withContext(Dispatchers.IO) {
        val validation = LanEndpointValidator.validate(settings.serverUrl)
        if (!validation.isValid) {
            return@withContext SyncResult(false, validation.error ?: "آدرس سرور معتبر نیست")
        }
        if (settings.apiKey.isBlank()) {
            return@withContext SyncResult(false, "کلید API وارد نشده است")
        }
        try {
            val response: HealthResponse = SmsApiClient.getService(validation.normalizedUrl).getHealth(
                url = validation.normalizedUrl.removeSuffix("/") + "/api/health",
                apiKey = "Bearer " + settings.apiKey
            )
            if (response.success) {
                SyncResult(true, "اتصال به " + validation.displayEndpoint + " برقرار شد")
            } else {
                SyncResult(false, "سرویس Gateway پاسخ آماده ندارد")
            }
        } catch (error: HttpException) {
            when (error.code()) {
                401, 403 -> SyncResult(false, "احراز هویت پنل ناموفق بود")
                else -> SyncResult(false, "پنل وب در دسترس نیست")
            }
        } catch (_: Exception) {
            SyncResult(false, "اتصال به پنل برقرار نشد")
        }
    }

    suspend fun pollPendingMessagesFromServer(): SyncResult = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettings() ?: GatewaySettings()
        if (!settings.isGatewayEnabled) {
            return@withContext SyncResult(false, "درگاه غیرفعال است")
        }
        val validation = LanEndpointValidator.validate(settings.serverUrl)
        if (!validation.isValid) {
            return@withContext SyncResult(false, validation.error ?: "آدرس سرور معتبر نیست")
        }
        if (settings.apiKey.isBlank()) {
            return@withContext SyncResult(false, "کلید API وارد نشده است")
        }
        try {
            val api = SmsApiClient.getService(validation.normalizedUrl)
            val responseList = api.getPendingMessages(
                url = validation.normalizedUrl.removeSuffix("/") + "/api/messages/pending",
                apiKey = "Bearer " + settings.apiKey,
                deviceId = settings.deviceId
            )
            var addedCount = 0
            responseList.forEach { message ->
                val requestId = message.getFinalRequestId()
                val phone = message.getFinalPhoneNumber()
                val body = message.getFinalBody()
                if (requestId.isBlank() || phone.isBlank() || body.isBlank()) return@forEach
                if (smsQueueDao.getByRequestId(requestId) == null) {
                    smsQueueDao.insert(
                        SmsQueueItem(
                            requestId = requestId,
                            phoneNumber = phone,
                            messageBody = body,
                            simSlot = message.getFinalSimSlot(),
                            status = "PENDING",
                            scheduledAt = message.scheduledAt ?: 0,
                            expiresAt = message.expiresAt ?: 0
                        )
                    )
                    addedCount++
                }
            }
            SyncResult(true, "دریافت صف انجام شد؛ پیام جدید: " + addedCount)
        } catch (error: HttpException) {
            if (error.code() == 401 || error.code() == 403) {
                SyncResult(false, "احراز هویت صف پیامک ناموفق بود")
            } else {
                SyncResult(false, "دریافت صف پیامک از پنل انجام نشد")
            }
        } catch (_: Exception) {
            SyncResult(false, "اتصال به صف پیامک برقرار نشد")
        }
    }

    suspend fun processOutgoingQueue() = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettings() ?: return@withContext
        if (!settings.isGatewayEnabled) return@withContext
        if (!isWithinWorkingHours(settings.workingHoursStart, settings.workingHoursEnd)) {
            log("WARN", "صف خارج از ساعات کاری متوقف شد", "SmsSender")
            return@withContext
        }

        val pending = smsQueueDao.getPendingToProcess()
        for (item in pending) {
            if (isRateLimitExceeded(settings)) break
            if (item.expiresAt > 0 && System.currentTimeMillis() > item.expiresAt) {
                smsQueueDao.update(item.copy(status = "CANCELLED", errorMessage = "پیام منقضی شده است"))
                notifyStatusChange(settings, item.requestId, "CANCELLED", "پیام منقضی شده است")
                continue
            }
            smsQueueDao.update(item.copy(status = "PROCESSING", errorMessage = null))
            if (settings.isTestMode) {
                // Test mode is an explicit safety stop; it never fabricates SENT.
                smsQueueDao.update(
                    item.copy(
                        status = "PENDING",
                        errorMessage = "حالت تست فعال است؛ ارسال واقعی انجام نشد"
                    )
                )
                continue
            }
            try {
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
                    item.phoneNumber,
                    item.messageBody,
                    if (item.simSlot >= 0) item.simSlot else settings.simSlotSelection,
                    sentIntent,
                    deliveredIntent
                )
            } catch (_: Exception) {
                val retryCount = item.retryCount + 1
                if (retryCount >= 3) {
                    smsQueueDao.update(
                        item.copy(
                            status = "FAILED",
                            retryCount = retryCount,
                            errorMessage = "ارسال پیامک ناموفق بود"
                        )
                    )
                    notifyStatusChange(settings, item.requestId, "FAILED", "ارسال پیامک ناموفق بود")
                } else {
                    smsQueueDao.update(
                        item.copy(
                            status = "PENDING",
                            retryCount = retryCount,
                            errorMessage = "تلاش مجدد برای ارسال"
                        )
                    )
                }
            }
            delay(1000)
        }
    }

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
            val sentIntents = ArrayList<PendingIntent>().apply {
                repeat(parts.size) { add(sentIntent) }
            }
            val deliveredIntents = ArrayList<PendingIntent>().apply {
                repeat(parts.size) { add(deliveredIntent) }
            }
            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                parts,
                sentIntents,
                deliveredIntents
            )
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
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val info = subscriptionManager.activeSubscriptionInfoList
                    ?.firstOrNull { it.simSlotIndex == simSlot }
                if (info != null) {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                            .createForSubscriptionId(info.subscriptionId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(info.subscriptionId)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    private suspend fun isRateLimitExceeded(settings: GatewaySettings): Boolean {
        val now = System.currentTimeMillis()
        val sent = smsQueueDao.getQueueByStatus("SENT") + smsQueueDao.getQueueByStatus("DELIVERED")
        return sent.count { it.createdAt >= now - 60_000 } >= settings.limitSmsPerMin ||
            sent.count { it.createdAt >= now - 3_600_000 } >= settings.limitSmsPerHour ||
            sent.count { it.createdAt >= now - 86_400_000 } >= settings.limitSmsPerDay
    }

    private fun isWithinWorkingHours(start: String, end: String): Boolean {
        fun parse(value: String): Int? {
            val parts = value.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()
            val minute = parts.getOrNull(1)?.toIntOrNull()
            return if (hour != null && minute != null && hour in 0..23 && minute in 0..59) hour * 60 + minute else null
        }
        val startMinute = parse(start) ?: return true
        val endMinute = parse(end) ?: return true
        val now = SimpleDateFormat("HH:mm", Locale.US).format(Date()).split(":")
        val current = now[0].toInt() * 60 + now[1].toInt()
        return if (startMinute <= endMinute) {
            current in startMinute..endMinute
        } else {
            current >= startMinute || current <= endMinute
        }
    }

    suspend fun notifyStatusChange(settings: GatewaySettings, requestId: String, status: String, error: String?) {
        if (requestId.isBlank()) return
        val payload = WebhookPayload(
            event = "sms_" + status.lowercase(Locale.US),
            deviceId = settings.deviceId,
            requestId = requestId,
            timestamp = System.currentTimeMillis(),
            errorMessage = error
        )
        SmsApiClient.sendWebhook(settings.webhookUrl, settings.webhookSecret, payload)
        if (settings.serverUrl.isNotBlank() && settings.apiKey.isNotBlank()) {
            try {
                val validation = LanEndpointValidator.validate(settings.serverUrl)
                if (validation.isValid) {
                    SmsApiClient.getService(validation.normalizedUrl).updateMessageStatus(
                        url = validation.normalizedUrl.removeSuffix("/") + "/api/messages/status",
                        apiKey = "Bearer " + settings.apiKey,
                        payload = payload
                    )
                }
            } catch (_: Exception) {
                // Background delivery failures remain local and PII-free.
            }
        }
    }
}

data class SyncResult(val isSuccess: Boolean, val message: String)
