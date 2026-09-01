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

                    // Also extract direct info from intent to ensure immediate processing
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    if (messages.isNotEmpty()) {
                        val firstMsg = messages[0]
                        val sender = firstMsg.originatingAddress ?: ""
                        val fullBody = messages.joinToString("") { it.messageBody ?: "" }
                        val timestamp = System.currentTimeMillis()

                        repo.log("INFO", "دریافت مستقیم از: $sender", "SmsReceiver")
                    }

                    // Run the comprehensive alignment sync
                    repo.syncInboxAndDetectDeletions()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
