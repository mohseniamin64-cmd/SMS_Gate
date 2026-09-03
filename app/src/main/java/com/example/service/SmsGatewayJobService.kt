package com.example.service

import android.Manifest
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.pm.PackageManager
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
            try {
                val repository = SmsRepository(applicationContext)
                val settings = AppDatabase.getDatabase(applicationContext).settingsDao().getSettings()
                if (settings?.isGatewayEnabled == true) {
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
            } finally {
                jobFinished(params, false)
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
