package com.example.data.repository

import android.content.Context
import com.example.data.device.CallLogEntry
import com.example.data.device.CallLogReader
import com.example.data.local.AppDatabase
import com.example.data.local.CallLogUploadItem
import com.example.data.local.GatewaySettings
import com.example.data.remote.CallLogSyncRequest
import com.example.data.remote.CallLogUploadPayload
import com.example.data.remote.SmsApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

data class CallLogSyncResult(
    val status: Status,
    val queued: Int = 0,
    val accepted: Int = 0,
    val duplicates: Int = 0,
    val rejected: Int = 0,
    val message: String
) {
    enum class Status {
        SUCCESS,
        NOT_ENABLED,
        UNAVAILABLE,
        FAILED
    }
}

/**
 * Local-first call-log synchronization. The reader uses CallLog.Calls and
 * contacts are deliberately not copied into the upload payload.
 */
class CallLogRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(appContext)
    private val queueDao = database.callLogUploadDao()
    private val reader = CallLogReader(appContext)

    fun pendingCountFlow(deviceId: String): Flow<Int> = queueDao.pendingCountFlow(deviceId)

    suspend fun refreshFromProvider(deviceId: String): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val entries = reader.read()
        if (deviceId.isNotBlank()) {
            queueDao.insertAll(
                entries.map { entry ->
                    CallLogUploadItem(
                        deviceId = deviceId,
                        callId = entry.id.toString(),
                        number = entry.number,
                        // Contact names remain local-only and are omitted from API uploads.
                        contactName = null,
                        timestamp = entry.timestamp,
                        durationSeconds = entry.durationSeconds,
                        type = entry.type.name
                    )
                }
            )
        }
        entries
    }

    suspend fun syncPending(settings: GatewaySettings): CallLogSyncResult = withContext(Dispatchers.IO) {
        if (!settings.callLogSyncEnabled) {
            return@withContext CallLogSyncResult(
                status = CallLogSyncResult.Status.NOT_ENABLED,
                message = "گزارش تماس در تنظیمات فعال نشده است"
            )
        }
        if (settings.serverUrl.isBlank() || settings.deviceId.isBlank()) {
            return@withContext CallLogSyncResult(
                status = CallLogSyncResult.Status.UNAVAILABLE,
                message = "برای همگام‌سازی تماس، سرور و شناسه دستگاه را تنظیم کنید"
            )
        }
        if (settings.apiKey.isBlank()) {
            return@withContext CallLogSyncResult(
                status = CallLogSyncResult.Status.UNAVAILABLE,
                message = "کلید API برای همگام‌سازی تماس وارد نشده است"
            )
        }

        val pending = queueDao.getPending(settings.deviceId)
        if (pending.isEmpty()) {
            return@withContext CallLogSyncResult(
                status = CallLogSyncResult.Status.SUCCESS,
                message = "تماس جدیدی برای گزارش وجود ندارد"
            )
        }

        val ids = pending.map { it.callId }
        return@withContext try {
            val api = SmsApiClient.getService(settings.serverUrl)
            val response = api.syncCallLogs(
                url = settings.serverUrl.removeSuffix("/") + "/api/call-logs/sync",
                apiKey = "Bearer " + settings.apiKey,
                payload = CallLogSyncRequest(
                    deviceId = settings.deviceId,
                    calls = pending.map {
                        CallLogUploadPayload(
                            id = it.callId,
                            number = it.number,
                            contactName = null,
                            timestamp = it.timestamp,
                            durationSeconds = it.durationSeconds,
                            type = it.type
                        )
                    }
                )
            )
            val terminalIds = (response.accepted + response.duplicates + response.rejected.mapNotNull { it.id })
                .distinct()
            if (terminalIds.isNotEmpty()) {
                queueDao.deleteUploaded(settings.deviceId, terminalIds)
            }
            CallLogSyncResult(
                status = if (response.rejected.isEmpty()) {
                    CallLogSyncResult.Status.SUCCESS
                } else {
                    CallLogSyncResult.Status.FAILED
                },
                queued = pending.size,
                accepted = response.accepted.size,
                duplicates = response.duplicates.size,
                rejected = response.rejected.size,
                message = "گزارش تماس تکمیل شد؛ پذیرفته: " + response.accepted.size +
                    "، تکراری: " + response.duplicates.size +
                    "، ردشده: " + response.rejected.size
            )
        } catch (error: HttpException) {
            queueDao.recordFailure(settings.deviceId, ids, "HTTP " + error.code())
            CallLogSyncResult(
                status = CallLogSyncResult.Status.FAILED,
                queued = pending.size,
                message = "سرور گزارش تماس پاسخ معتبر نداد"
            )
        } catch (_: Exception) {
            queueDao.recordFailure(settings.deviceId, ids, "خطای اتصال")
            CallLogSyncResult(
                status = CallLogSyncResult.Status.FAILED,
                queued = pending.size,
                message = "همگام‌سازی تماس انجام نشد؛ در صف محلی باقی ماند"
            )
        }
    }
}
