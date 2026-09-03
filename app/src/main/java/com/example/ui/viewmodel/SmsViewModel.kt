package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.device.CallLogEntry
import com.example.data.device.CallLogReader
import com.example.data.device.ContactInfo
import com.example.data.device.ContactsReader
import com.example.data.local.GatewaySettings
import com.example.data.repository.CallLogRepository
import com.example.data.repository.CallLogSyncResult
import com.example.data.repository.SmsRepository
import com.example.data.repository.SyncResult
import com.example.data.remote.LanConnectionState
import com.example.data.remote.LanEndpointValidator
import com.example.data.remote.PhoneServerKeyStore
import com.example.data.remote.PhoneServerCors
import com.example.data.remote.PhoneServerStatusStore
import com.example.receiver.GatewayBootReceiver
import com.example.service.SmsGatewayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ContactsState(
    val loading: Boolean = false,
    val items: List<ContactInfo> = emptyList(),
    val error: String? = null
)

data class CallLogState(
    val loading: Boolean = false,
    val items: List<CallLogEntry> = emptyList(),
    val error: String? = null
)

class SmsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repository = SmsRepository(context)
    private val contactsReader = ContactsReader(context)
    private val callLogRepository = CallLogRepository(context)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val settingsState: StateFlow<GatewaySettings> = repository.settingsFlow
        .map { it ?: GatewaySettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GatewaySettings())
    val syncedSmsState = repository.syncedSmsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val queueState = repository.queueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tombstonesState = repository.tombstonesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val logsState = repository.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lanConnectionState = MutableStateFlow<LanConnectionState>(LanConnectionState.Disconnected)
    val lanConnectionState: StateFlow<LanConnectionState> = _lanConnectionState.asStateFlow()

    private val _contactsState = MutableStateFlow(ContactsState())
    val contactsState: StateFlow<ContactsState> = _contactsState.asStateFlow()

    private val _callLogState = MutableStateFlow(CallLogState())
    val callLogState: StateFlow<CallLogState> = _callLogState.asStateFlow()

    val callLogPendingCountState: StateFlow<Int> = settingsState
        .map { it.deviceId }
        .distinctUntilChanged()
        .flatMapLatest { callLogRepository.pendingCountFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _syncingState = MutableStateFlow(false)
    val syncingState: StateFlow<Boolean> = _syncingState.asStateFlow()
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (settingsState.value.isGatewayEnabled &&
                !PhoneServerStatusStore.state.value.running
            ) {
                startService()
            }
        }

        override fun onLost(network: Network) {
            if (settingsState.value.isGatewayEnabled && !PhoneServerStatusStore.state.value.running) {
                _lanConnectionState.value = LanConnectionState.Offline(
                    "شبکهٔ LAN در دسترس نیست؛ اتصال پس از بازگشت شبکه دوباره بررسی می‌شود"
                )
            }
        }
    }

    init {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        viewModelScope.launch {
            PhoneServerStatusStore.state.collectLatest { server ->
                _lanConnectionState.value = when {
                    server.error != null -> LanConnectionState.Error(server.error)
                    server.running -> LanConnectionState.Connected(
                        server.primaryEndpoint ?: "IP در دسترس نیست:${server.port}"
                    )
                    settingsState.value.isGatewayEnabled -> LanConnectionState.Connecting
                    else -> LanConnectionState.Disconnected
                }
            }
        }
        viewModelScope.launch {
            settingsState.collectLatest { settings ->
                if (settings.isGatewayEnabled && !PhoneServerStatusStore.state.value.running) {
                    startService()
                }
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun connectGateway() {
        if (_lanConnectionState.value is LanConnectionState.Connecting) return
        viewModelScope.launch {
            val settings = settingsState.value
            val updated = settings.copy(
                phoneServerPort = settings.phoneServerPort.coerceIn(1024, 65_535),
                isGatewayEnabled = true
            )
            repository.settingsDao.saveSettings(updated)
            repository.log("INFO", "سرور HTTP گوشی فعال شد", "Gateway")
            _lanConnectionState.value = LanConnectionState.Connecting
            startService()
        }
    }

    fun retryGatewayConnection() {
        connectGateway()
    }

    fun disconnectGateway() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.settingsDao.getSettings() ?: settingsState.value
            repository.settingsDao.saveSettings(current.copy(isGatewayEnabled = false))
            repository.log("INFO", "درگاه پیامک قطع شد", "Gateway")
            _lanConnectionState.value = LanConnectionState.Disconnected
            stopService()
            _toastMessage.value = "اتصال Gateway قطع شد"
        }
    }

    fun toggleGateway(enabled: Boolean) {
        if (enabled) connectGateway() else disconnectGateway()
    }

    fun saveSettings(settings: GatewaySettings) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.settingsDao.getSettings() ?: settingsState.value
            val validation = if (settings.serverUrl.isBlank()) null else LanEndpointValidator.validate(settings.serverUrl)
            if (settings.serverUrl.isNotBlank() && validation?.isValid != true) {
                _lanConnectionState.value = LanConnectionState.Error(
                    validation?.errorMessage ?: "آدرس پنل Flask معتبر نیست"
                )
                return@launch
            }
            val normalizedOrigin = if (settings.phoneServerAllowedOrigin.isBlank()) {
                ""
            } else {
                PhoneServerCors.normalizeOrigin(settings.phoneServerAllowedOrigin)
                    ?: run {
                        _lanConnectionState.value = LanConnectionState.Error("Origin مجاز CORS معتبر نیست")
                        return@launch
                    }
            }
            if (settings.phoneServerApiKey.isNotBlank()) {
                try {
                    PhoneServerKeyStore(context).store(settings.phoneServerApiKey.trim())
                } catch (_: Exception) {
                    _lanConnectionState.value = LanConnectionState.Error("ذخیرهٔ امن کلید سرور گوشی انجام نشد")
                    return@launch
                }
            }
            val updated = settings.copy(
                serverUrl = validation?.normalizedBaseUrl ?: "",
                phoneServerPort = settings.phoneServerPort.coerceIn(1024, 65_535),
                phoneServerApiKey = "",
                phoneServerAllowedOrigin = normalizedOrigin,
                isGatewayEnabled = current.isGatewayEnabled
            )
            repository.settingsDao.saveSettings(updated)
            repository.log("INFO", "تنظیمات دستگاه ذخیره شد", "Settings")
            if (updated.isGatewayEnabled) startService()
            scheduleBackgroundWork(updated)
            _toastMessage.value = "تنظیمات ذخیره شد"
        }
    }

    private fun scheduleBackgroundWork(settings: GatewaySettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (settings.autostartEnabled && settings.isGatewayEnabled) {
                GatewayBootReceiver.scheduleSafeJob(context)
            } else {
                GatewayBootReceiver.cancelSafeJob(context)
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
        } catch (_: Exception) {
            _lanConnectionState.value = LanConnectionState.Error(
                "سرویس پس‌زمینه شروع نشد؛ برنامه را باز نگه دارید و دوباره تلاش کنید"
            )
        }
    }

    fun stopService() {
        runCatching {
            context.stopService(Intent(context, SmsGatewayService::class.java))
            GatewayBootReceiver.cancelSafeJob(context)
        }
    }

    fun triggerManualSync() {
        if (_syncingState.value) return
        _syncingState.value = true
        viewModelScope.launch {
            try {
                repository.log("INFO", "همگام‌سازی دستی آغاز شد", "ViewModel")
                val inboxResult = repository.syncInboxAndDetectDeletions()
                val settings = repository.settingsDao.getSettings() ?: settingsState.value
                val gatewayResult = if (settings.isGatewayEnabled) {
                    val poll = if (settings.serverUrl.isNotBlank() && settings.apiKey.isNotBlank()) {
                        repository.pollPendingMessagesFromServer()
                    } else {
                        SyncResult(true, "سرور گوشی فعال است؛ صف محلی بررسی شد")
                    }
                    repository.processOutgoingQueue()
                    poll
                } else {
                    SyncResult(true, "Gateway خاموش است؛ همگام‌سازی محلی انجام شد")
                }

                val callResult = if (settings.callLogSyncEnabled) {
                    if (hasPermission(Manifest.permission.READ_CALL_LOG)) {
                        callLogRepository.refreshFromProvider(settings.deviceId)
                        callLogRepository.syncPending(settings)
                    } else {
                        CallLogSyncResult(
                            status = CallLogSyncResult.Status.UNAVAILABLE,
                            message = "برای گزارش تماس، مجوز سابقهٔ تماس لازم است"
                        )
                    }
                } else {
                    null
                }

                _toastMessage.value = when {
                    !inboxResult.isSuccess -> inboxResult.message
                    settings.isGatewayEnabled && !gatewayResult.isSuccess -> gatewayResult.message
                    callResult != null && callResult.status == CallLogSyncResult.Status.FAILED -> callResult.message
                    callResult != null && callResult.status == CallLogSyncResult.Status.UNAVAILABLE -> callResult.message
                    else -> "همگام‌سازی با موفقیت انجام شد"
                }
            } catch (_: Exception) {
                _toastMessage.value = "همگام‌سازی انجام نشد؛ وضعیت محلی حفظ شد"
            } finally {
                _syncingState.value = false
            }
        }
    }

    fun refreshContacts() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            _contactsState.value = ContactsState(error = "مجوز مخاطبان داده نشده است")
            return
        }
        _contactsState.value = _contactsState.value.copy(loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _contactsState.value = ContactsState(items = contactsReader.read())
            } catch (_: Exception) {
                _contactsState.value = ContactsState(error = "خواندن مخاطبان از دستگاه انجام نشد")
            }
        }
    }

    fun refreshCallLogs() {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            _callLogState.value = CallLogState(error = "مجوز سابقهٔ تماس داده نشده است")
            return
        }
        _callLogState.value = _callLogState.value.copy(loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = callLogRepository.refreshFromProvider(settingsState.value.deviceId)
                _callLogState.value = CallLogState(items = entries)
            } catch (_: Exception) {
                _callLogState.value = CallLogState(error = "خواندن سابقهٔ تماس از دستگاه انجام نشد")
            }
        }
    }

    fun syncCallLogsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
                _toastMessage.value = "برای گزارش تماس، ابتدا مجوز سابقهٔ تماس را بدهید"
                return@launch
            }
            val settings = repository.settingsDao.getSettings() ?: settingsState.value
            callLogRepository.refreshFromProvider(settings.deviceId)
            _toastMessage.value = callLogRepository.syncPending(settings).message
        }
    }

    fun enqueueManualSms(phoneNumber: String, body: String, simSlot: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val phone = phoneNumber.trim()
            val text = body.trim()
            if (phone.isBlank() || text.isBlank()) {
                _toastMessage.value = "شماره و متن پیامک نمی‌تواند خالی باشد"
                return@launch
            }
            repository.enqueueSms("manual_" + UUID.randomUUID(), phone, text, simSlot)
            repository.log("INFO", "پیامک دستی در صف محلی ثبت شد", "ViewModel")
            _toastMessage.value = "پیامک در صف محلی ثبت شد"
            if (settingsState.value.isGatewayEnabled) repository.processOutgoingQueue()
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearQueueHistory()
            _toastMessage.value = "تاریخچهٔ تکمیل‌شدهٔ صف پاک شد"
        }
    }

    fun clearSystemLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
            _toastMessage.value = "گزارش‌ها پاک شدند"
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }
}
