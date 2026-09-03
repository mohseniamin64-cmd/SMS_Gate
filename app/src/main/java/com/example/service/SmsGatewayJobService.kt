package com.example.service

import android.Manifest
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.data.repository.CallLogRepository
import com.example.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android 15-safe fallback for boot and periodic background work. It is
 * bounded by JobScheduler and never starts a dataSync foreground service from
 * BOOT_COMPLETED on Android 15+.
 */
class SmsGatewayJobService : JobService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = scope.launch {
            var needsRetry = false
            try {
                val settings = AppDatabase.getDatabase(applicationContext).settingsDao().getSettings()
                if (settings?.isGatewayEnabled == true) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        try {
                            ContextCompat.startForegroundService(
                                applicationContext,
                                Intent(applicationContext, SmsGatewayService::class.java)
                            )
                        } catch (e: Throwable) {
                            if (JobRetryPolicy.shouldRetry(e)) {
                                needsRetry = true
                            }
                        }
                    }
                    val repository = SmsRepository(applicationContext)
                    repository.syncInboxAndDetectDeletions()
                    if (settings.serverUrl.isNotBlank() && settings.apiKey.isNotBlank()) {
                        repository.pollPendingMessagesFromServer()
                    }
                    repository.processOutgoingQueue()
                    if (
                        settings.callLogSyncEnabled &&
                        ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.READ_CALL_LOG
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val calls = CallLogRepository(applicationContext)
                        calls.refreshFromProvider(settings.deviceId)
                        calls.syncPending(settings)
                    }
                }
            } catch (e: Throwable) {
                if (JobRetryPolicy.shouldRetry(e)) {
                    needsRetry = true
                }
            } finally {
                jobFinished(params, needsRetry)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        return true
    }

    override fun onDestroy() {
        runningJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }
}
