package com.example.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CallType(val providerValue: Int, val label: String) {
    INCOMING(CallLog.Calls.INCOMING_TYPE, "دریافتی"),
    OUTGOING(CallLog.Calls.OUTGOING_TYPE, "گرفته‌شده"),
    MISSED(CallLog.Calls.MISSED_TYPE, "بی‌پاسخ");

    companion object {
        fun fromProviderValue(value: Int): CallType? =
            entries.firstOrNull { it.providerValue == value }
    }
}

data class CallLogEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val date: Long,
    val durationSeconds: Long,
    val type: CallType
) {
    val timestamp: Long
        get() = date
    val displayName: String
        get() = cachedName?.trim().takeUnless { it.isNullOrBlank() }
            ?: number.trim().takeUnless { it.isBlank() }
            ?: "شماره ناشناس"
}

object CallLogFormatter {
    fun duration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val remainingSeconds = safeSeconds % 60
        return if (minutes > 0) {
            "$minutes دقیقه و $remainingSeconds ثانیه"
        } else {
            "$remainingSeconds ثانیه"
        }
    }

    fun dateTime(timestamp: Long): String =
        SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")).format(Date(timestamp))
}

/** Reads only the local Android Call Log; it has no networking dependency. */
class CallLogReader(private val context: Context) {
    fun read(): List<CallLogEntry> {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("READ_CALL_LOG permission is not granted")
        }

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )
        val calls = mutableListOf<CallLogEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            while (cursor.moveToNext()) {
                val type = CallType.fromProviderValue(cursor.getInt(typeIndex)) ?: continue
                calls += CallLogEntry(
                    id = cursor.getLong(idIndex),
                    number = cursor.getString(numberIndex).orEmpty(),
                    cachedName = cursor.getString(nameIndex),
                    date = cursor.getLong(dateIndex),
                    durationSeconds = cursor.getLong(durationIndex),
                    type = type
                )
            }
        }
        return calls
    }
}
