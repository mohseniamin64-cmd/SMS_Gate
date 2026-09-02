package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.local.AppDatabase
import com.example.data.local.SyncedSms
import com.example.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = SmsRepository(context)
                    repo.log("INFO", "پیامک جدید در سیستم دریافت شد. اجرای همگام‌سازی...", "SmsReceiver")

                    // Keep the receiver event observable without persisting PII or message text.
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    if (messages.isNotEmpty()) {
                        repo.log("INFO", "پیامک مستقیم دریافت شد", "SmsReceiver")
                    }

                    // Run the comprehensive alignment sync
                    repo.syncInboxAndDetectDeletions()
                } catch (_: Exception) {
                    // Do not print SMS contents or provider details to logcat.
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
