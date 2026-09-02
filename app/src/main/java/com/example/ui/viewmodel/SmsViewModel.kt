package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.GatewaySettings
import com.example.data.local.SmsQueueItem
import com.example.data.repository.SmsRepository
import com.example.data.repository.SyncResult
import com.example.service.SmsGatewayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = SmsRepository(context)

    // Flows for UI observation
    val settingsState: StateFlow<GatewaySettings> = repository.settingsFlow
        .map { it ?: GatewaySettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GatewaySettings())

    val syncedSmsState = repository.syncedSmsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueState = repository.queueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tombstonesState = repository.tombstonesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logsState = repository.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Direct UI Feedback states
    private val _syncingState = MutableStateFlow(false)
    val syncingState: StateFlow<Boolean> = _syncingState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToast() {
        _toastMessage.value = null
    }

    // -------------------------------------------------------------------------
    // GATEWAY CONTROL ACTIONS
    // -------------------------------------------------------------------------

    fun saveSettings(settings: GatewaySettings) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.settingsDao.saveSettings(settings)
            repository.log("INFO", "تنظیمات جدید ذخیره شد", "ViewModel")
            
            // If gateway was enabled, restart/start the foreground service to apply settings instantly
            if (settings.isGatewayEnabled) {
                startService()
            } else {
                stopService()
            }
        }
    }

    fun toggleGateway(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = settingsState.value
            val updated = current.copy(isGatewayEnabled = enabled)
            repository.settingsDao.saveSettings(updated)
            repository.log("INFO", "درگاه پیامک ${if (enabled) "فعال" else "غیرفعال"} شد", "ViewModel")
            
            if (enabled) {
                startService()
            } else {
                stopService()
            }
        }
    }

    fun startService() {
        try {
            val intent = Intent(context, SmsGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                repository.log("ERROR", "خطا در شروع سرویس پس‌زمینه", "ViewModel")
            }
        }
    }

    fun stopService() {
        try {
            val intent = Intent(context, SmsGatewayService::class.java).apply { action = "STOP" }
            context.startService(intent)
        } catch (_: Exception) {
            // Keep service-start failures out of logcat.
        }
    }

    // -------------------------------------------------------------------------
    // SYNC ACTIONS
    // -------------------------------------------------------------------------

    fun triggerManualSync() {
        if (_syncingState.value) return
        _syncingState.value = true
        viewModelScope.launch {
            repository.log("INFO", "همگام‌سازی دستی توسط کاربر آغاز شد", "ViewModel")
            
            // 1. Sync Inbox deletions
            val inboxResult = repository.syncInboxAndDetectDeletions()
            
            // 2. Poll server for new outgoing SMS
            val pollResult = repository.pollPendingMessagesFromServer()

            // 3. Process outgoing queue
            repository.processOutgoingQueue()

            _syncingState.value = false
            
            val feedback = when {
                inboxResult.isSuccess && pollResult.isSuccess -> "همگام‌سازی با موفقیت انجام شد"
                !inboxResult.isSuccess -> inboxResult.message
                else -> pollResult.message
            }
            _toastMessage.value = feedback
        }
    }

    // -------------------------------------------------------------------------
    // QUEUE & LOG MANAGEMENT
    // -------------------------------------------------------------------------

    fun enqueueManualSms(phoneNumber: String, body: String, simSlot: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (phoneNumber.isBlank() || body.isBlank()) {
                _toastMessage.value = "شماره و متن پیامک نمی‌تواند خالی باشد"
                return@launch
            }

            val requestUUID = "manual_${System.currentTimeMillis()}"
            val newItem = SmsQueueItem(
                requestId = requestUUID,
                phoneNumber = phoneNumber,
                messageBody = body,
                simSlot = simSlot,
                status = "PENDING"
            )
            repository.smsQueueDao.insert(newItem)
            repository.log("INFO", "پیامک دستی به صف اضافه شد", "ViewModel")
            _toastMessage.value = "پیامک با موفقیت در صف ارسال قرار گرفت"

            // Process immediately if service/gateway is enabled
            if (settingsState.value.isGatewayEnabled) {
                repository.processOutgoingQueue()
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearQueueHistory()
            repository.log("INFO", "تاریخچه صف پیامک‌ها پاک‌سازی شد", "ViewModel")
            _toastMessage.value = "تاریخچه پاک شد"
        }
    }

    fun clearSystemLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
            _toastMessage.value = "لاگ‌های سیستم با موفقیت پاک شدند"
        }
    }
}
