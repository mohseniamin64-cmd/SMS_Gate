package com.example.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.example.data.local.AppDatabase
import com.example.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SmsSentDeliveredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getIntExtra("item_id", -1)
        if (itemId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val repo = SmsRepository(context)
                val item = db.smsQueueDao().getPendingToProcess().find { it.id == itemId }
                    ?: db.smsQueueDao().getAllQueueFlow().firstOrNull()?.find { it.id == itemId }

                if (item == null) {
                    pendingResult.finish()
                    return@launch
                }

                val settings = db.settingsDao().getSettings() ?: return@launch

                when (intent.action) {
                    "com.example.SMS_SENT" -> {
                        val resultCode = resultCode
                        if (resultCode == Activity.RESULT_OK) {
                            repo.log("INFO", "وضعیت ارسال موفق برای پیام ${item.id} ثبت شد", "SentDeliveredReceiver")
                            db.smsQueueDao().updateStatus(itemId, "SENT", null)
                            repo.notifyStatusChange(settings, item.requestId, "SENT", null)
                        } else {
                            val errorMsg = getSentErrorString(resultCode)
                            repo.log("ERROR", "خطا در ارسال پیام ${item.id}: $errorMsg", "SentDeliveredReceiver")
                            db.smsQueueDao().updateStatus(itemId, "FAILED", errorMsg)
                            repo.notifyStatusChange(settings, item.requestId, "FAILED", errorMsg)
                        }
                    }
                    "com.example.SMS_DELIVERED" -> {
                        repo.log("INFO", "گزارش تحویل پیام ${item.id} دریافت شد", "SentDeliveredReceiver")
                        db.smsQueueDao().updateStatus(itemId, "DELIVERED", null)
                        repo.notifyStatusChange(settings, item.requestId, "DELIVERED", null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun getSentErrorString(resultCode: Int): String {
        return when (resultCode) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "خطای عمومی سامانه تلفن همراه"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "شبکه تلفن همراه در دسترس نیست"
            SmsManager.RESULT_ERROR_NULL_PDU -> "خطا در قالب‌بندی پیام"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "حالت پرواز فعال است یا آنتن در دسترس نیست"
            else -> "خطای ناشناخته (کد $resultCode)"
        }
    }
}
