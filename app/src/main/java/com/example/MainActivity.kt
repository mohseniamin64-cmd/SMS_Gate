package com.example

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SmsFailed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.device.CallLogEntry
import com.example.data.device.CallLogFormatter
import com.example.data.device.CallType
import com.example.data.device.ContactInfo
import com.example.data.local.GatewaySettings
import com.example.data.local.LogEntry
import com.example.data.local.SmsDirection
import com.example.data.local.SmsQueueItem
import com.example.data.local.SyncedSms
import com.example.data.remote.LanConnectionState
import com.example.data.remote.LanEndpointValidator
import com.example.data.repository.SmsConversation
import com.example.data.repository.SmsConversationGrouper
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CallLogState
import com.example.ui.viewmodel.ContactsState
import com.example.ui.viewmodel.SmsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: SmsViewModel by viewModels()

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val required = listOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE
            )
            val granted = required.all { result[it] == true || hasPermission(this, it) }
            Toast.makeText(
                this,
                if (granted) "مجوزهای پیامک تأیید شد" else "برخی مجوزهای پیامک هنوز داده نشده است",
                if (granted) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
            if (granted) viewModel.triggerManualSync()
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.refreshContacts()
        }

    private val callLogPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.refreshCallLogs()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    SmsGatewayAppScreen(
                        viewModel = viewModel,
                        onGrantPermissions = ::checkAndRequestPermissions,
                        onRequestContacts = ::requestContactsPermission,
                        onRequestCallLog = ::requestCallLogPermission
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val requested = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requested += Manifest.permission.POST_NOTIFICATIONS
        }
        val pending = requested.filterNot { hasPermission(this, it) }
        if (pending.isNotEmpty()) smsPermissionLauncher.launch(pending.toTypedArray())
    }

    private fun requestContactsPermission() {
        if (hasPermission(this, Manifest.permission.READ_CONTACTS)) {
            viewModel.refreshContacts()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun requestCallLogPermission() {
        if (hasPermission(this, Manifest.permission.READ_CALL_LOG)) {
            viewModel.refreshCallLogs()
        } else {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }
}

enum class AppDestination(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val showInDrawer: Boolean = true
) {
    DASHBOARD("dashboard", "داشبورد", Icons.Default.Dashboard),
    INBOX("inbox", "صندوق ورودی", Icons.Outlined.Inbox),
    SENT("sent", "ارسال‌شده", Icons.Outlined.Send),
    SEND("send", "ارسال پیام", Icons.Default.Send),
    QUEUE("queue", "صف ارسال", Icons.Default.ListAlt),
    CONTACTS("contacts", "مخاطبان", Icons.Default.Contacts),
    CALLS("calls", "گزارش تماس", Icons.Outlined.Call),
    GATEWAY("gateway", "وضعیت Gateway", Icons.Default.Router),
    SETTINGS("settings", "تنظیمات", Icons.Default.Settings),
    LOGS("logs", "گزارش‌ها", Icons.Default.Article),
    CONVERSATION("conversation", "گفت‌وگو", Icons.Default.Chat, showInDrawer = false);

    companion object {
        fun from(key: String): AppDestination =
            entries.firstOrNull { it.key == key } ?: DASHBOARD
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsGatewayAppScreen(
    viewModel: SmsViewModel,
    modifier: Modifier = Modifier,
    onGrantPermissions: () -> Unit,
    onRequestContacts: () -> Unit = {},
    onRequestCallLog: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val messages by viewModel.syncedSmsState.collectAsStateWithLifecycle()
    val queue by viewModel.queueState.collectAsStateWithLifecycle()
    val logs by viewModel.logsState.collectAsStateWithLifecycle()
    val syncing by viewModel.syncingState.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()
    val connection by viewModel.lanConnectionState.collectAsStateWithLifecycle()
    val contacts by viewModel.contactsState.collectAsStateWithLifecycle()
    val calls by viewModel.callLogState.collectAsStateWithLifecycle()
    val pendingCallCount by viewModel.callLogPendingCountState.collectAsStateWithLifecycle()

    var navigation by rememberSaveable(
        stateSaver = listSaver(
            save = { it.stack },
            restore = { AppNavigationState(it) }
        )
    ) { mutableStateOf(AppNavigationState()) }
    var selectedConversationKey by rememberSaveable { mutableStateOf("") }
    val selected = AppDestination.from(navigation.currentKey)
    val stateHolder = rememberSaveableStateHolder()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val allConversations = remember(messages, contacts.items) {
        SmsConversationGrouper.group(messages, contacts.items)
    }

    BackHandler(enabled = navigation.stack.size > 1) {
        navigation = navigation.back()
    }
    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    fun openTopLevel(destination: AppDestination) {
        navigation = if (destination == AppDestination.DASHBOARD) {
            navigation.navigateToDashboard()
        } else {
            AppNavigationState(listOf(AppDestination.DASHBOARD.key, destination.key))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(300.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.Sms,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp).size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("SMS Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("مرکز کنترل Gateway", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    GatewayStatusLabel(connection)
                }
                Divider()
                AppDestination.entries.filter { it.showInDrawer }.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.title) },
                        selected = destination == selected,
                        onClick = {
                            openTopLevel(destination)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(selected.title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (navigation.stack.size > 1) {
                            IconButton(onClick = { navigation = navigation.back() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "باز کردن منوی برنامه")
                            }
                        }
                    },
                    actions = {
                        StatusDot(connection is LanConnectionState.Connected)
                        Spacer(Modifier.width(16.dp))
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                stateHolder.SaveableStateProvider(selected.key) {
                    when (selected) {
                        AppDestination.DASHBOARD -> DashboardPage(
                            settings = settings,
                            messageCount = messages.count { it.direction != SmsDirection.UNKNOWN.storageValue },
                            queue = queue,
                            syncing = syncing,
                            connection = connection,
                            pendingCallCount = pendingCallCount,
                            onConnect = viewModel::connectGateway,
                            onDisconnect = viewModel::disconnectGateway,
                            onRetry = viewModel::retryGatewayConnection,
                            onSync = viewModel::triggerManualSync,
                            onPermissions = onGrantPermissions,
                            onNavigate = ::openTopLevel
                        )
                        AppDestination.INBOX, AppDestination.SENT -> {
                            val direction = if (selected == AppDestination.INBOX) {
                                SmsDirection.INCOMING
                            } else {
                                SmsDirection.OUTGOING
                            }
                            MessagesPage(
                                title = selected.title,
                                direction = direction,
                                messages = messages,
                                contacts = contacts.items,
                                permission = hasPermission(context, Manifest.permission.READ_SMS),
                                loading = syncing,
                                onPermission = onGrantPermissions,
                                onOpenConversation = {
                                    selectedConversationKey = it
                                    navigation = navigation.navigate(AppDestination.CONVERSATION.key)
                                }
                            )
                        }
                        AppDestination.CONVERSATION -> ConversationPage(
                            conversation = allConversations.firstOrNull {
                                it.key == selectedConversationKey
                            },
                            onDial = { number -> launchDial(context, number) }
                        )
                        AppDestination.SEND -> SendPage(
                            onSend = viewModel::enqueueManualSms,
                            testMode = settings.isTestMode
                        )
                        AppDestination.QUEUE -> QueuePage(queue, viewModel::clearHistory)
                        AppDestination.CONTACTS -> ContactsPage(
                            state = contacts,
                            permission = hasPermission(context, Manifest.permission.READ_CONTACTS),
                            onPermission = onRequestContacts,
                            onRetry = viewModel::refreshContacts
                        )
                        AppDestination.CALLS -> CallsPage(
                            state = calls,
                            permission = hasPermission(context, Manifest.permission.READ_CALL_LOG),
                            pendingCount = pendingCallCount,
                            syncEnabled = settings.callLogSyncEnabled,
                            onPermission = onRequestCallLog,
                            onRetry = viewModel::refreshCallLogs,
                            onSync = viewModel::syncCallLogsNow,
                            onDial = { number -> launchDial(context, number) }
                        )
                        AppDestination.GATEWAY -> GatewayPage(
                            settings = settings,
                            connection = connection,
                            syncing = syncing,
                            onConnect = viewModel::connectGateway,
                            onDisconnect = viewModel::disconnectGateway,
                            onRetry = viewModel::retryGatewayConnection,
                            onSync = viewModel::triggerManualSync,
                            onPermissions = onGrantPermissions,
                            onSettings = { openTopLevel(AppDestination.SETTINGS) },
                            onRevealApiKey = viewModel::getRevealedApiKey,
                            onRegenerateApiKey = viewModel::regenerateApiKey
                        )
                        AppDestination.SETTINGS -> SettingsPage(settings, viewModel::saveSettings)
                        AppDestination.LOGS -> LogsPage(logs, viewModel::clearSystemLogs)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPage(
    settings: GatewaySettings,
    messageCount: Int,
    queue: List<SmsQueueItem>,
    syncing: Boolean,
    connection: LanConnectionState,
    pendingCallCount: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit,
    onSync: () -> Unit,
    onPermissions: () -> Unit,
    onNavigate: (AppDestination) -> Unit
) {
    val context = LocalContext.current
    val pending = queue.count { it.status == "PENDING" || it.status == "PROCESSING" }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            GatewayHero(connection, onConnect, onDisconnect, onRetry)
        }
        if (!hasPermission(context, Manifest.permission.READ_SMS) ||
            !hasPermission(context, Manifest.permission.SEND_SMS)
        ) {
            item { PermissionCard(onPermissions) }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard("پیام همگام‌شده", messageCount.toString(), Icons.Default.Sync, Modifier.weight(1f))
                MetricCard("در انتظار ارسال", pending.toString(), Icons.Default.HourglassEmpty, Modifier.weight(1f))
            }
        }
        item {
            SectionHeader("دسترسی سریع", "کارهای روزمره")
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickAction("ورودی", Icons.Outlined.Inbox, { onNavigate(AppDestination.INBOX) }, Modifier.weight(1f))
                QuickAction("صف", Icons.Default.ListAlt, { onNavigate(AppDestination.QUEUE) }, Modifier.weight(1f))
                QuickAction("تماس", Icons.Outlined.Call, { onNavigate(AppDestination.CALLS) }, Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = onSync, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
                if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (syncing) "در حال همگام‌سازی" else "همگام‌سازی دستی")
            }
        }
        item {
            SectionHeader("خلاصه اتصال", "وضعیت Gateway و صف محلی")
            Spacer(Modifier.height(8.dp))
            ConnectionSummary(settings, connection, syncing, pendingCallCount)
        }
        item {
            TextButton(onClick = { onNavigate(AppDestination.GATEWAY) }, modifier = Modifier.fillMaxWidth()) {
                Text("مشاهده جزئیات Gateway")
            }
        }
    }
}

@Composable
private fun GatewayPage(
    settings: GatewaySettings,
    connection: LanConnectionState,
    syncing: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit,
    onSync: () -> Unit,
    onPermissions: () -> Unit,
    onSettings: () -> Unit,
    onRevealApiKey: () -> String,
    onRegenerateApiKey: () -> String
) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            GatewayHero(connection, onConnect, onDisconnect, onRetry)
        }
        item { ConnectionStateCard(connection) }
        item {
            ApiKeySecureCard(
                onRevealKey = onRevealApiKey,
                onRegenerateKey = onRegenerateApiKey
            )
        }
        if (!hasPermission(context, Manifest.permission.READ_SMS) ||
            !hasPermission(context, Manifest.permission.SEND_SMS)
        ) {
            item { PermissionCard(onPermissions) }
        }
        item {
            Button(
                onClick = onSync,
                enabled = !syncing && connection is LanConnectionState.Connected,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (syncing) "در حال همگام‌سازی" else "همگام‌سازی دستی")
            }
        }
    }
}

@Composable
private fun GatewayHero(
    connection: LanConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit
) {
    val connected = connection is LanConnectionState.Connected
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (connected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("درگاه پیامک", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(connectionLabel(connection), style = MaterialTheme.typography.bodyMedium)
                }
                StatusDot(connected)
            }
            Divider()
            when (connection) {
                is LanConnectionState.Connected -> {
                    Text("متصل به " + connection.endpoint)
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                        Text("قطع اتصال")
                    }
                }
                LanConnectionState.Connecting -> {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("در حال اتصال...")
                    }
                }
                is LanConnectionState.Error -> {
                    Text(connection.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تلاش دوباره")
                    }
                }
                is LanConnectionState.Offline -> {
                    Text(connection.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("بررسی دوباره شبکه")
                    }
                }
                LanConnectionState.Disconnected -> {
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Router, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("اتصال به Gateway")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStateCard(connection: LanConnectionState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("وضعیت اتصال", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(connection is LanConnectionState.Connected)
                Spacer(Modifier.width(8.dp))
                Text(connectionLabel(connection))
            }
            if (connection is LanConnectionState.Connected) {
                Text("آدرس سرور (Endpoint): " + connection.endpoint, style = MaterialTheme.typography.bodySmall)
                Text("پروتکل انتقال: HTTPS (TLS واقعی)", style = MaterialTheme.typography.bodySmall)
                connection.certificateFingerprint?.let { fp ->
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "اثر انگشت گواهی TLS (SHA-256):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                fp,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "این اثر انگشت را در کلاینت وب/رایانه جهت تطبیق گواهی بررسی کنید.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeySecureCard(
    onRevealKey: () -> String,
    onRegenerateKey: () -> String
) {
    val context = LocalContext.current
    var revealedKey by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(60) }

    LaunchedEffect(revealedKey) {
        if (revealedKey != null) {
            secondsLeft = 60
            while (secondsLeft > 0) {
                delay(1000L)
                secondsLeft--
            }
            revealedKey = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "کلید امن API سرور گوشی",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (revealedKey != null) {
                    Text(
                        "انقضا: $secondsLeft ثانیه",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Text(
                "کلید در Android Keystore با رمزنگاری سخت‌افزاری AES/GCM محافظت می‌شود و در دیتابیس یا گزارش‌ها ذخیره نمی‌شود.",
                style = MaterialTheme.typography.bodySmall
            )

            if (revealedKey == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { revealedKey = onRevealKey() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("نمایش یک‌بارهٔ کلید")
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = revealedKey.orEmpty(),
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val keyToCopy = revealedKey.orEmpty()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("API Key", keyToCopy).apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    description.extras = PersistableBundle().apply {
                                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                    }
                                }
                            }
                            clipboard.setPrimaryClip(clip)
                            revealedKey = null
                            Toast.makeText(context, "کلید کپی و پنهان شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("کپی و پنهان‌سازی")
                    }

                    OutlinedButton(
                        onClick = { revealedKey = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("پنهان‌سازی")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("مجوزهای پیامک کامل نیست", fontWeight = FontWeight.Bold)
                Text("دریافت و ارسال تا تأیید مجوزها غیرفعال می‌ماند.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick) { Text("تأیید") }
        }
    }
}

@Composable
private fun ConnectionSummary(
    settings: GatewaySettings,
    connection: LanConnectionState,
    syncing: Boolean,
    pendingCallCount: Int
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow("Gateway", connectionLabel(connection), connection is LanConnectionState.Connected)
            if (connection is LanConnectionState.Connected) InfoRow("Endpoint", connection.endpoint, true)
            InfoRow("همگام‌سازی پیامک", if (syncing) "در حال اجرا" else "آماده", !syncing)
            InfoRow("گزارش تماس محلی", pendingCallCount.toString() + " مورد در صف", true)
            InfoRow("حالت تست", if (settings.isTestMode) "فعال؛ ارسال واقعی متوقف است" else "غیرفعال", !settings.isTestMode)
        }
    }
}

@Composable
private fun MessagesPage(
    title: String,
    direction: SmsDirection,
    messages: List<SyncedSms>,
    contacts: List<ContactInfo>,
    permission: Boolean,
    loading: Boolean,
    onPermission: () -> Unit,
    onOpenConversation: (String) -> Unit
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val visible = messages.filter { it.direction == direction.storageValue }
    val conversations = remember(visible, contacts) {
        SmsConversationGrouper.group(visible, contacts)
    }
    val filtered = conversations.filter {
        query.isBlank() ||
            it.address.contains(query, true) ||
            it.contactName?.contains(query, true) == true ||
            it.latestMessage.body.contains(query, true)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("جست‌وجوی نام، شماره یا متن") }
        )
        Spacer(Modifier.height(12.dp))
        when {
            !permission -> PermissionState(
                Icons.Default.Sms,
                "صندوق پیامک غیرفعال است",
                "مجوز READ_SMS برای خواندن پیام‌های واقعی دستگاه لازم است.",
                "تأیید مجوز",
                onPermission
            )
            loading && messages.isEmpty() -> LoadingState("در حال خواندن پیامک‌های دستگاه...")
            filtered.isEmpty() -> EmptyState(
                if (direction == SmsDirection.INCOMING) Icons.Outlined.Inbox else Icons.Outlined.Send,
                if (query.isBlank()) title + " خالی است" else "نتیجه‌ای پیدا نشد",
                "پیام‌ها فقط از SMS Provider واقعی و Room نمایش داده می‌شوند."
            )
            else -> {
                Text(
                    filtered.size.toString() + " گفت‌وگو",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.key }) { conversation ->
                        ConversationRow(conversation) { onOpenConversation(conversation.key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: SmsConversation, onClick: () -> Unit) {
    val incoming = conversation.latestMessage.direction == SmsDirection.INCOMING.storageValue
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (incoming) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f)
            }
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        conversation.contactName ?: conversation.address,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (conversation.contactName != null) {
                        Text(conversation.address, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    dateText(conversation.latestMessage.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                conversation.latestMessage.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (incoming) "دریافتی" else "ارسالی",
                style = MaterialTheme.typography.labelSmall,
                color = if (incoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ConversationPage(conversation: SmsConversation?, onDial: (String) -> Unit) {
    if (conversation == null) {
        EmptyState(Icons.Default.Chat, "گفت‌وگو پیدا نشد", "این پیام دیگر در دادهٔ محلی موجود نیست.")
        return
    }
    val ordered = conversation.messages.sortedWith(compareBy<SyncedSms> { it.date }.thenBy { it.id })
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(conversation.contactName ?: conversation.address, fontWeight = FontWeight.Bold)
                    if (conversation.contactName != null) Text(conversation.address, style = MaterialTheme.typography.bodySmall)
                    Text(ordered.size.toString() + " پیام", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { onDial(conversation.address) }) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("شماره‌گیری")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(ordered, key = { it.id }) { message ->
                val incoming = message.direction == SmsDirection.INCOMING.storageValue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (incoming) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (incoming) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.width(280.dp)
                    ) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(message.body)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (incoming) "دریافتی" else "ارسالی", style = MaterialTheme.typography.labelSmall)
                                Text(dateText(message.date), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendPage(onSend: (String, String, Int) -> Unit, testMode: Boolean) {
    var phone by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var sim by rememberSaveable { mutableStateOf(-1) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("ارسال پیام", "پیام ابتدا در صف محلی Room ثبت می‌شود")
        }
        if (testMode) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "حالت تست فعال است؛ ارسال واقعی انجام نمی‌شود و وضعیت SENT ساختگی ثبت نخواهد شد.",
                        Modifier.padding(14.dp)
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("شماره گیرنده") }
            )
        }
        item {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                label = { Text("متن پیام") }
            )
        }
        item {
            Text("تعداد حروف: " + body.length, style = MaterialTheme.typography.labelSmall)
        }
        item {
            Text("سیم‌کارت فرستنده", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sim == -1, onClick = { sim = -1 }, label = { Text("پیش‌فرض") })
                FilterChip(selected = sim == 0, onClick = { sim = 0 }, label = { Text("سیم‌کارت ۱") })
                FilterChip(selected = sim == 1, onClick = { sim = 1 }, label = { Text("سیم‌کارت ۲") })
            }
        }
        item {
            Button(
                onClick = {
                    onSend(phone, body, sim)
                    phone = ""
                    body = ""
                },
                enabled = phone.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("افزودن به صف ارسال")
            }
        }
    }
}

private val queueStatuses = listOf("همه", "PENDING", "PROCESSING", "SENT", "DELIVERED", "FAILED", "CANCELLED")

@Composable
private fun QueuePage(queue: List<SmsQueueItem>, clear: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("همه") }
    val filtered = queue.filter {
        (status == "همه" || it.status == status) &&
            (query.isBlank() || it.phoneNumber.contains(query, true) || it.messageBody.contains(query, true))
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("صف ارسال", queue.size.toString() + " مورد در تاریخچه")
            if (queue.any { it.status in listOf("SENT", "DELIVERED", "FAILED", "CANCELLED") }) {
                TextButton(onClick = clear) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Text("پاک‌سازی تاریخچه")
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("جست‌وجوی شماره یا متن") }
        )
        ScrollableTabRow(
            selectedTabIndex = queueStatuses.indexOf(status),
            edgePadding = 0.dp,
            divider = {}
        ) {
            queueStatuses.forEach { value ->
                Tab(selected = value == status, onClick = { status = value }, text = { Text(statusLabel(value)) })
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Outlined.SmsFailed,
                if (queue.isEmpty()) "صف پیامک خالی است" else "موردی با این فیلتر پیدا نشد",
                "پیام‌های واقعی ثبت‌شده در صف و تاریخچهٔ ارسال اینجا نمایش داده می‌شوند."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { QueueRow(it) }
            }
        }
    }
}

@Composable
private fun QueueRow(item: SmsQueueItem) {
    val color = statusColor(item.status)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.phoneNumber, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
                    Text(
                        statusLabel(item.status),
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = color,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(item.messageBody, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(dateText(item.createdAt), style = MaterialTheme.typography.labelSmall)
            item.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text("علت: " + it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ContactsPage(
    state: ContactsState,
    permission: Boolean,
    onPermission: () -> Unit,
    onRetry: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = state.items.filter {
        query.isBlank() ||
            it.displayName.contains(query, true) ||
            it.phoneNumber.contains(query, true)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("جست‌وجوی مخاطب") }
        )
        Spacer(Modifier.height(12.dp))
        when {
            !permission -> PermissionState(
                Icons.Outlined.ContactPhone,
                "مخاطبان در دسترس نیستند",
                "برای نمایش نام کنار شماره، مجوز READ_CONTACTS لازم است. دادهٔ مخاطبان به API ارسال نمی‌شود.",
                "تأیید مخاطبان",
                onPermission
            )
            state.loading -> LoadingState("در حال خواندن مخاطبان از دستگاه...")
            state.error != null -> ErrorState("خواندن مخاطبان ناموفق بود", state.error, onRetry)
            filtered.isEmpty() -> EmptyState(
                Icons.Outlined.ContactPhone,
                if (query.isBlank()) "مخاطبی ثبت نشده است" else "نتیجه‌ای پیدا نشد",
                "این صفحه فقط از ContactsContract واقعی دستگاه استفاده می‌کند."
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.contactId.toString() + it.phoneNumber }) { contact ->
                    ContactRow(contact)
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactInfo) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(contact.displayName.take(1).ifBlank { "#" }, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(contact.displayName.ifBlank { contact.phoneNumber }, fontWeight = FontWeight.Bold)
                Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CallsPage(
    state: CallLogState,
    permission: Boolean,
    pendingCount: Int,
    syncEnabled: Boolean,
    onPermission: () -> Unit,
    onRetry: () -> Unit,
    onSync: () -> Unit,
    onDial: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<CallType?>(null) }
    val filtered = state.items.filter {
        (filter == null || it.type == filter) &&
            (query.isBlank() || it.displayName.contains(query, true) || it.number.contains(query, true))
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("جست‌وجوی نام یا شماره") }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("همه") })
            FilterChip(selected = filter == CallType.INCOMING, onClick = { filter = CallType.INCOMING }, label = { Text("دریافتی") })
            FilterChip(selected = filter == CallType.OUTGOING, onClick = { filter = CallType.OUTGOING }, label = { Text("گرفته‌شده") })
            FilterChip(selected = filter == CallType.MISSED, onClick = { filter = CallType.MISSED }, label = { Text("بی‌پاسخ") })
        }
        Spacer(Modifier.height(8.dp))
        when {
            !permission -> PermissionState(
                Icons.Outlined.Call,
                "گزارش تماس در دسترس نیست",
                "برای خواندن CallLog.Calls واقعی، مجوز READ_CALL_LOG لازم است. تماس صوتی یا مستقیم از این برنامه انجام نمی‌شود.",
                "تأیید سابقهٔ تماس",
                onPermission
            )
            state.loading -> LoadingState("در حال خواندن سابقهٔ تماس...")
            state.error != null -> ErrorState("خواندن سابقهٔ تماس ناموفق بود", state.error, onRetry)
            else -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (syncEnabled) {
                                pendingCount.toString() + " گزارش تماس در صف همگام‌سازی"
                            } else {
                                "گزارش تماس فقط محلی است؛ همگام‌سازی در Settings خاموش است"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (syncEnabled) {
                            OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Sync, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("همگام‌سازی گزارش‌های تماس")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    EmptyState(
                        Icons.Outlined.Call,
                        if (state.items.isEmpty()) "سابقهٔ تماسی ثبت نشده است" else "نتیجه‌ای پیدا نشد",
                        "ورودی‌ها از CallLog.Calls واقعی دستگاه خوانده می‌شوند."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { call -> CallRow(call, onDial) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRow(call: CallLogEntry, onDial: (String) -> Unit) {
    val color = when (call.type) {
        CallType.INCOMING -> MaterialTheme.colorScheme.primary
        CallType.OUTGOING -> MaterialTheme.colorScheme.secondary
        CallType.MISSED -> MaterialTheme.colorScheme.error
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Call, contentDescription = null, tint = color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(call.displayName, fontWeight = FontWeight.Bold)
                Text(call.number, style = MaterialTheme.typography.bodySmall)
                Text(
                    call.type.label + " · " + CallLogFormatter.dateTime(call.date) +
                        " · " + CallLogFormatter.duration(call.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDial(call.number) }) {
                Icon(Icons.Default.Call, contentDescription = "شماره‌گیری")
            }
        }
    }
}

@Composable
private fun SettingsPage(settings: GatewaySettings, save: (GatewaySettings) -> Unit) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var url by remember(settings) { mutableStateOf(settings.serverUrl) }
    var key by remember(settings) { mutableStateOf(settings.apiKey) }
    var phonePort by remember(settings) { mutableStateOf(settings.phoneServerPort.toString()) }
    var device by remember(settings) { mutableStateOf(settings.deviceId) }
    var interval by remember(settings) { mutableStateOf(settings.syncIntervalSeconds.toString()) }
    var webhook by remember(settings) { mutableStateOf(settings.webhookUrl) }
    var secret by remember(settings) { mutableStateOf(settings.webhookSecret) }
    var test by remember(settings) { mutableStateOf(settings.isTestMode) }
    var autostart by remember(settings) { mutableStateOf(settings.autostartEnabled) }
    var callSync by remember(settings) { mutableStateOf(settings.callLogSyncEnabled) }
    var daily by remember(settings) { mutableStateOf(settings.limitSmsPerDay.toString()) }
    var hourly by remember(settings) { mutableStateOf(settings.limitSmsPerHour.toString()) }
    var perMinute by remember(settings) { mutableStateOf(settings.limitSmsPerMin.toString()) }
    var start by remember(settings) { mutableStateOf(settings.workingHoursStart) }
    var end by remember(settings) { mutableStateOf(settings.workingHoursEnd) }
    val validation = remember(url) { LanEndpointValidator.validate(url) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("تنظیمات", "گزینه‌های عادی جدا از مشخصات اتصال")
        }
        item {
            SettingSwitch(
                "شروع خودکار Gateway",
                "پس از راه‌اندازی دستگاه، سرویس یا Job امن Android اجرا می‌شود.",
                autostart,
                { autostart = it }
            )
        }
        item {
            SettingSwitch(
                "همگام‌سازی گزارش تماس",
                "اختیاری است؛ تماس‌ها محلی می‌مانند و نام مخاطب به API ارسال نمی‌شود.",
                callSync,
                { callSync = it }
            )
        }
        item {
            SettingSwitch(
                "حالت تست",
                "ارسال واقعی عمداً متوقف می‌شود؛ وضعیت SENT ساختگی ثبت نمی‌شود.",
                test,
                { test = it }
            )
        }
        item {
            OutlinedButton(
                onClick = { advanced = !advanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (advanced) "بستن Advanced Settings" else "باز کردن Advanced Settings")
            }
        }
        if (advanced) {
            item {
                Field(
                    "آدرس پنل Flask (اختیاری)",
                    url,
                    { url = it },
                    "اتصال اصلی از طریق سرور خود گوشی است؛ این مسیر فقط برای سازگاری legacy است"
                )
            }
            if (url.isNotBlank() && !validation.isValid) {
                item { Text(validation.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            validation.warning?.let { warning ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Text(warning, Modifier.padding(12.dp))
                    }
                }
            }
            item {
                Field("کلید API", key, { key = it }, "برای احراز هویت اتصال لازم است", true)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("امنیت کلید سرور گوشی", fontWeight = FontWeight.Bold)
                        Text(
                            "کلید API سرور گوشی با AES/GCM داخل Android Keystore محافظت می‌شود و در دیتابیس یا متن ذخیره نمی‌شود. جهت مشاهدهٔ یک‌باره یا کپی امن آن، به تب درگاه پیامک (Gateway) مراجعه کنید.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Field("پورت HTTPS سرور گوشی", phonePort, { phonePort = it }, "بین 1024 و 65535")
            }
            item {
                Field("شناسه یکتای دستگاه", device, { device = it }, "در Contacts یا Call Log استفاده نمی‌شود")
            }
            item { Field("بازهٔ همگام‌سازی (ثانیه)", interval, { interval = it }, "حداقل 5") }
            item { Field("آدرس وب‌هوک پیامک", webhook, { webhook = it }, "اختیاری") }
            item { Field("کلید امضای وب‌هوک", secret, { secret = it }, "اختیاری", true) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("سقف روزانه", daily, { daily = it }, "500", modifier = Modifier.weight(1f))
                    Field("سقف ساعتی", hourly, { hourly = it }, "100", modifier = Modifier.weight(1f))
                    Field("سقف دقیقه‌ای", perMinute, { perMinute = it }, "5", modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("شروع ساعات کاری", start, { start = it }, "00:00", modifier = Modifier.weight(1f))
                    Field("پایان ساعات کاری", end, { end = it }, "23:59", modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Button(
                onClick = {
                    save(
                        settings.copy(
                            serverUrl = url,
                            apiKey = key,
                            phoneServerApiKey = "",
                            phoneServerPort = phonePort.toIntOrNull()?.coerceIn(1024, 65_535)
                                ?: settings.phoneServerPort,
                            deviceId = device,
                            syncIntervalSeconds = interval.toIntOrNull()?.coerceAtLeast(5) ?: 30,
                            webhookUrl = webhook,
                            webhookSecret = secret,
                            isTestMode = test,
                            autostartEnabled = autostart,
                            callLogSyncEnabled = callSync,
                            limitSmsPerDay = daily.toIntOrNull()?.coerceAtLeast(1) ?: settings.limitSmsPerDay,
                            limitSmsPerHour = hourly.toIntOrNull()?.coerceAtLeast(1) ?: settings.limitSmsPerHour,
                            limitSmsPerMin = perMinute.toIntOrNull()?.coerceAtLeast(1) ?: settings.limitSmsPerMin,
                            workingHoursStart = start,
                            workingHoursEnd = end
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ذخیره تنظیمات")
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    message: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supporting: String = "",
    secret: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        supportingText = supporting.takeIf { it.isNotBlank() }?.let { text -> { Text(text) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@Composable
private fun LogsPage(logs: List<LogEntry>, clear: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("گزارش‌ها", logs.size.toString() + " رویداد اخیر")
            if (logs.isNotEmpty()) {
                TextButton(onClick = clear) {
                    Icon(Icons.Default.ClearAll, contentDescription = null)
                    Text("پاک‌سازی")
                }
            }
        }
        if (logs.isEmpty()) {
            EmptyState(
                Icons.Default.Article,
                "گزارشی ثبت نشده است",
                "رویدادهای واقعی Gateway و همگام‌سازی اینجا نمایش داده می‌شوند."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(log.tag, fontWeight = FontWeight.Bold)
                            Text(log.message, style = MaterialTheme.typography.bodySmall)
                            Text(dateText(log.timestamp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    action: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = action),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.58f else 0.25f)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, healthy: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(healthy)
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GatewayStatusLabel(connection: LanConnectionState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(connection is LanConnectionState.Connected)
        Spacer(Modifier.width(8.dp))
        Text(connectionLabel(connection))
    }
}

private fun connectionLabel(connection: LanConnectionState): String = when (connection) {
    LanConnectionState.Disconnected -> "قطع"
    LanConnectionState.Connecting -> "در حال اتصال"
    is LanConnectionState.Connected -> "متصل"
    is LanConnectionState.Error -> "خطا"
    is LanConnectionState.Offline -> "آفلاین"
}

@Composable
private fun StatusDot(healthy: Boolean) {
    Box(
        Modifier.size(10.dp).clip(CircleShape).background(
            if (healthy) Color(0xFF65D391) else MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, message: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PermissionState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    action: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.error)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Button(onClick = action) { Text(actionLabel) }
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ErrorState(title: String, message: String, action: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = action) { Text("تلاش دوباره") }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun launchDial(context: Context, number: String) {
    if (number.isBlank()) return
    try {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number.trim())))
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "برنامهٔ شماره‌گیر در دسترس نیست", Toast.LENGTH_SHORT).show()
    }
}

private fun dateText(value: Long): String =
    SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")).format(Date(value))

private fun statusLabel(value: String): String = when (value) {
    "همه" -> "همه"
    "PENDING" -> "در انتظار"
    "PROCESSING" -> "در حال ارسال"
    "SENT" -> "ارسال شد"
    "DELIVERED" -> "تحویل شد"
    "FAILED" -> "ناموفق"
    "CANCELLED" -> "لغو شد"
    else -> value
}

private fun statusColor(value: String): Color = when (value) {
    "PENDING" -> Color(0xFFFFB74D)
    "PROCESSING" -> Color(0xFF64B5F6)
    "SENT", "DELIVERED" -> Color(0xFF65D391)
    "FAILED" -> Color(0xFFFF7777)
    else -> Color(0xFFB8B8C2)
}
