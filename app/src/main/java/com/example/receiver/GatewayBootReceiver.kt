package com.example.receiver

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.service.SmsGatewayJobService
import com.example.service.SmsGatewayService
import kotlinx.coroutines.runBlocking

class GatewayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        Thread {
            try {
                val settings = runBlocking { AppDatabase.getDatabase(context).settingsDao().getSettings() } ?: return@Thread
                if (!settings.autostartEnabled || !settings.isGatewayEnabled) return@Thread

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    scheduleSafeJob(context)
                } else {
                    ContextCompat.startForegroundService(context, Intent(context, SmsGatewayService::class.java))
                }
            } catch (_: Exception) {
                // Boot must remain non-fatal; the next foreground app launch can retry.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        const val JOB_ID = 14821

        fun scheduleSafeJob(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, SmsGatewayJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000L)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            scheduler.schedule(job)
        }

        fun cancelSafeJob(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
