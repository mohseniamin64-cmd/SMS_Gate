package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SmsFailed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SmsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: SmsViewModel by viewModels()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val required = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE)
        val granted = required.all { result[it] == true || ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        Toast.makeText(this, if (granted) "تمام مجوزهای لازم اعطا شد" else "برخی مجوزها برای کارکرد صحیح مورد نیاز است", if (granted) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { MyApplicationTheme { androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { SmsGatewayAppScreen(viewModel, onGrantPermissions = ::checkAndRequestPermissions) } } }
    }
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions += Manifest.permission.POST_NOTIFICATIONS
        val pending = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (pending.isNotEmpty()) permissionLauncher.launch(pending.toTypedArray())
    }
}

enum class AppDestination(val key: String, val title: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "داشبورد", Icons.Default.Dashboard), INBOX("inbox", "صندوق ورودی", Icons.Outlined.Inbox), SENT("sent", "ارسال‌شده", Icons.Outlined.Send),
    SEND("send", "ارسال پیام", Icons.Default.Send), QUEUE("queue", "صف ارسال", Icons.Default.ListAlt), CONTACTS("contacts", "مخاطبان", Icons.Default.Contacts),
    GATEWAY("gateway", "وضعیت Gateway", Icons.Default.Router), SETTINGS("settings", "تنظیمات", Icons.Default.Settings), LOGS("logs", "گزارش‌ها", Icons.Default.Article);
    companion object { fun from(key: String) = entries.firstOrNull { it.key == key } ?: DASHBOARD }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsGatewayAppScreen(viewModel: SmsViewModel, modifier: Modifier = Modifier, onGrantPermissions: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val messages by viewModel.syncedSmsState.collectAsStateWithLifecycle()
    val queue by viewModel.queueState.collectAsStateWithLifecycle()
    val logs by viewModel.logsState.collectAsStateWithLifecycle()
    val syncing by viewModel.syncingState.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()
    var selectedKey by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD.key) }
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope(); val selected = AppDestination.from(selectedKey)
    LaunchedEffect(toast) { toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearToast() } }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.width(300.dp)) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Sms, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp).size(28.dp)) }
                    Spacer(Modifier.width(12.dp)); Column { Text("SMS Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("مرکز کنترل Gateway", style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(18.dp)); GatewayStatusLabel(settings.isGatewayEnabled)
            }
            Divider()
            AppDestination.entries.forEach { destination -> NavigationDrawerItem(label = { Text(destination.title) }, selected = destination == selected, onClick = { selectedKey = destination.key; scope.launch { drawer.close() } }, icon = { Icon(destination.icon, null) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
        }
    }) {
        Scaffold(modifier.fillMaxSize(), topBar = {
            CenterAlignedTopAppBar(title = { Text(selected.title, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "باز کردن منوی برنامه") } }, actions = { StatusDot(settings.isGatewayEnabled); Spacer(Modifier.width(16.dp)) }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }) { padding ->
            Box(Modifier.padding(padding)) {
                when (selected) {
                    AppDestination.DASHBOARD -> DashboardPage(settings, messages.size, queue, syncing, viewModel::toggleGateway, viewModel::triggerManualSync, onGrantPermissions) { selectedKey = it.key }
                    AppDestination.INBOX -> MessagesPage("صندوق ورودی", messages.filter { it.simSlot == 1 }, Icons.Outlined.Inbox, hasPermission(context, Manifest.permission.READ_SMS))
                    AppDestination.SENT -> MessagesPage("ارسال‌شده", messages.filter { it.simSlot == 2 }, Icons.Outlined.Send, hasPermission(context, Manifest.permission.READ_SMS))
                    AppDestination.SEND -> SendPage(viewModel::enqueueManualSms, settings.isTestMode, settings.isGatewayEnabled)
                    AppDestination.QUEUE -> QueuePage(queue, viewModel::clearHistory)
                    AppDestination.CONTACTS -> EmptyState(Icons.Outlined.ContactPhone, "مخاطبان هنوز آماده نیست", "مدل مخاطب و منبع داده در قرارداد فعلی وجود ندارد.", "TODO: پس از تعریف Contacts data source اضافه شود.")
                    AppDestination.GATEWAY -> GatewayPage(settings, syncing, viewModel::toggleGateway, viewModel::triggerManualSync, onGrantPermissions) { selectedKey = AppDestination.SETTINGS.key }
                    AppDestination.SETTINGS -> SettingsPage(settings, viewModel::saveSettings)
                    AppDestination.LOGS -> LogsPage(logs, viewModel::clearSystemLogs)
                }
            }
        }
    }
}

@Composable
private fun DashboardPage(settings: GatewaySettings, count: Int, queue: List<SmsQueueItem>, syncing: Boolean, onToggle: (Boolean) -> Unit, onSync: () -> Unit, onPermissions: () -> Unit, onNavigate: (AppDestination) -> Unit) {
    val context = LocalContext.current; val pending = queue.count { it.status == "PENDING" || it.status == "PROCESSING" }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(4.dp)); GatewayHero(settings, onToggle) }
        if (!hasPermission(context, Manifest.permission.READ_SMS) || !hasPermission(context, Manifest.permission.SEND_SMS)) item { PermissionCard(onPermissions) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("پیام همگام‌شده", count.toString(), Icons.Default.CloudSync, Modifier.weight(1f)); MetricCard("در انتظار ارسال", pending.toString(), Icons.Default.HourglassEmpty, Modifier.weight(1f)) } }
        item { SectionHeader("دسترسی سریع", "کارهای روزمره"); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { QuickAction("ورودی", Icons.Outlined.Inbox, { onNavigate(AppDestination.INBOX) }, Modifier.weight(1f)); QuickAction("صف", Icons.Default.ListAlt, { onNavigate(AppDestination.QUEUE) }, Modifier.weight(1f)); QuickAction("همگام‌سازی", Icons.Default.Refresh, onSync, Modifier.weight(1f), !syncing) } }
        item { SectionHeader("خلاصه اتصال", "وضعیت فعلی دستگاه و پنل"); Spacer(Modifier.height(8.dp)); ConnectionSummary(settings, syncing) }
        item { TextButton({ onNavigate(AppDestination.GATEWAY) }, Modifier.fillMaxWidth()) { Text("مشاهده جزئیات Gateway") } }
    }
}

@Composable
private fun GatewayPage(settings: GatewaySettings, syncing: Boolean, onToggle: (Boolean) -> Unit, onSync: () -> Unit, onPermissions: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(4.dp)); GatewayHero(settings, onToggle) }
        if (settings.isGatewayEnabled && settings.serverUrl.isBlank()) item { ErrorState("اتصال پنل کامل نیست", "آدرس سرور پنل در تنظیمات وارد نشده است.", onSettings) }
        if (!hasPermission(context, Manifest.permission.READ_SMS) || !hasPermission(context, Manifest.permission.SEND_SMS)) item { PermissionCard(onPermissions) }
        item { ConnectionSummary(settings, syncing) }
        item { Button(onSync, enabled = !syncing && settings.isGatewayEnabled, modifier = Modifier.fillMaxWidth()) { if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (syncing) "در حال همگام‌سازی" else "همگام‌سازی دستی") } }
    }
}

@Composable
private fun GatewayHero(settings: GatewaySettings, onToggle: (Boolean) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = if (settings.isGatewayEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { Text("درگاه پیامک", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (settings.isGatewayEnabled) "سرویس پس‌زمینه فعال است" else "درگاه فعلاً خاموش است", style = MaterialTheme.typography.bodyMedium) }; Switch(settings.isGatewayEnabled, onToggle) }
            Divider(); Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(settings.isGatewayEnabled); Spacer(Modifier.width(8.dp)); Text(if (settings.isGatewayEnabled) "آماده دریافت و پردازش صف" else "برای شروع، درگاه را فعال کنید") }
        }
    }
}

@Composable private fun PermissionCard(onClick: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("مجوزهای پیامک کامل نیست", fontWeight = FontWeight.Bold); Text("دریافت و ارسال تا تأیید مجوزها غیرفعال می‌ماند.", style = MaterialTheme.typography.bodySmall) }; TextButton(onClick) { Text("تأیید") } } } }
@Composable private fun ConnectionSummary(settings: GatewaySettings, syncing: Boolean) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { InfoRow("پنل وب", settings.serverUrl.ifBlank { "تنظیم نشده" }, settings.serverUrl.isNotBlank()); InfoRow("همگام‌سازی", if (syncing) "در حال اجرا" else "هر ${settings.syncIntervalSeconds} ثانیه", !syncing); InfoRow("حالت تست", if (settings.isTestMode) "فعال" else "غیرفعال", true) } } }

@Composable
private fun MessagesPage(title: String, messages: List<SyncedSms>, icon: ImageVector, permission: Boolean) {
    var query by rememberSaveable(title) { mutableStateOf("") }; val filtered = messages.filter { query.isBlank() || it.address.contains(query, true) || it.body.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
        Spacer(Modifier.height(4.dp)); OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("جست‌وجوی شماره یا متن") }, label = { Text(title) }); Spacer(Modifier.height(12.dp))
        if (!permission) DisabledState("صندوق پیامک غیرفعال است", "مجوز READ_SMS برای نمایش پیام‌ها لازم است.")
        else if (filtered.isEmpty()) EmptyState(icon, if (query.isBlank()) "$title خالی است" else "نتیجه‌ای پیدا نشد", "پیام‌های همگام‌شده در این بخش نمایش داده می‌شوند.")
        else { Text("${filtered.size} پیام", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) { items(filtered, key = { it.id }) { MessageRow(it) } } }
    }
}
@Composable private fun MessageRow(message: SyncedSms) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(message.address, fontWeight = FontWeight.Bold); Text("همگام‌شده", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }; Text(message.body, maxLines = 3, overflow = TextOverflow.Ellipsis); Text(dateText(message.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable
private fun SendPage(onSend: (String, String, Int) -> Unit, testMode: Boolean, gateway: Boolean) {
    var phone by rememberSaveable { mutableStateOf("") }; var body by rememberSaveable { mutableStateOf("") }; var sim by rememberSaveable { mutableStateOf(-1) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(4.dp)); SectionHeader("ارسال پیام", "پیام ابتدا در صف محلی ثبت می‌شود") }
        if (!gateway) item { DisabledState("ارسال موقتاً غیرفعال است", "ابتدا Gateway را فعال کنید.") }
        if (testMode) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) { Text("حالت تست فعال است؛ ارسال واقعی انجام نمی‌شود.", Modifier.padding(14.dp)) } }
        item { OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("شماره گیرنده") }) }
        item { OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), minLines = 5, label = { Text("متن پیام") }) }
        item { Text("تعداد حروف: " + body.length, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("سیم‌کارت فرستنده", style = MaterialTheme.typography.labelLarge); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(sim == -1, { sim = -1 }, label = { Text("پیش‌فرض") }); FilterChip(sim == 0, { sim = 0 }, label = { Text("سیم‌کارت ۱") }); FilterChip(sim == 1, { sim = 1 }, label = { Text("سیم‌کارت ۲") }) } }
        item { Button({ onSend(phone, body, sim); phone = ""; body = "" }, enabled = gateway && phone.isNotBlank() && body.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("افزودن به صف ارسال") } }
    }
}

private val statuses = listOf("همه", "PENDING", "PROCESSING", "SENT", "DELIVERED", "FAILED", "CANCELLED")
@Composable
private fun QueuePage(queue: List<SmsQueueItem>, clear: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }; var status by rememberSaveable { mutableStateOf("همه") }
    val filtered = queue.filter { (status == "همه" || it.status == status) && (query.isBlank() || it.phoneNumber.contains(query, true) || it.messageBody.contains(query, true)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
        Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionHeader("صف ارسال", "${queue.size} مورد در تاریخچه"); if (queue.isNotEmpty()) TextButton(clear) { Icon(Icons.Default.DeleteSweep, null); Text("پاک‌سازی") } }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("جست‌وجوی شماره یا متن") })
        ScrollableTabRow(statuses.indexOf(status), edgePadding = 0.dp, divider = {}) { statuses.forEach { value -> Tab(value == status, { status = value }, text = { Text(statusLabel(value)) }) } }
        if (filtered.isEmpty()) EmptyState(Icons.Outlined.SmsFailed, if (queue.isEmpty()) "صف پیامک خالی است" else "موردی با این فیلتر پیدا نشد", "پیام‌های جدید و تاریخچه ارسال اینجا نمایش داده می‌شوند.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) { items(filtered, key = { it.id }) { QueueRow(it) } }
    }
}
@Composable private fun QueueRow(item: SmsQueueItem) { val color = statusColor(item.status); Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(item.phoneNumber, fontWeight = FontWeight.Bold); Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .16f)) { Text(statusLabel(item.status), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelSmall) } }; Text(item.messageBody, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(dateText(item.createdAt), style = MaterialTheme.typography.labelSmall); item.errorMessage?.takeIf { it.isNotBlank() }?.let { Text("علت خطا: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } } }

@Composable
private fun SettingsPage(settings: GatewaySettings, save: (GatewaySettings) -> Unit) {
    var url by remember(settings) { mutableStateOf(settings.serverUrl) }; var key by remember(settings) { mutableStateOf(settings.apiKey) }; var device by remember(settings) { mutableStateOf(settings.deviceId) }; var interval by remember(settings) { mutableStateOf(settings.syncIntervalSeconds.toString()) }; var webhook by remember(settings) { mutableStateOf(settings.webhookUrl) }; var secret by remember(settings) { mutableStateOf(settings.webhookSecret) }; var test by remember(settings) { mutableStateOf(settings.isTestMode) }; var daily by remember(settings) { mutableStateOf(settings.limitSmsPerDay.toString()) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)); SectionHeader("تنظیمات", "اتصال پنل، وب‌هوک و محدودیت‌های ارسال") }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (test) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("حالت تست", fontWeight = FontWeight.Bold); Text("ارسال واقعی انجام نمی‌شود.", style = MaterialTheme.typography.bodySmall) }; Switch(test, { test = it }) } } }
        item { Field("آدرس سرور پنل وب Flask", url, { url = it }, "https://my-sms-panel.com") }; item { Field("کلید امنیتی اتصال", key, { key = it }, "کلید احراز هویت", true) }; item { Field("شناسه یکتای دستگاه", device, { device = it }) }; item { Field("بازه همگام‌سازی (ثانیه)", interval, { interval = it }, "30") }; item { Field("آدرس وب‌هوک", webhook, { webhook = it }, "https://my-panel.com/webhook") }; item { Field("کلید امضای وب‌هوک", secret, { secret = it }, "HMAC secret", true) }; item { Field("سقف ارسال روزانه", daily, { daily = it }, "500") }
        item { Button({ save(settings.copy(serverUrl = url, apiKey = key, deviceId = device, syncIntervalSeconds = interval.toIntOrNull() ?: 30, webhookUrl = webhook, webhookSecret = secret, isTestMode = test, limitSmsPerDay = daily.toIntOrNull() ?: 500)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("ذخیره تنظیمات") } }
    }
}
@Composable private fun Field(label: String, value: String, change: (String) -> Unit, hint: String = "", secret: Boolean = false) { OutlinedTextField(value, change, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, placeholder = { Text(hint) }, visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None) }

@Composable
private fun LogsPage(logs: List<LogEntry>, clear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).windowInsetsPadding(WindowInsets.navigationBars)) { Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionHeader("گزارش‌ها", "${logs.size} رویداد اخیر"); if (logs.isNotEmpty()) TextButton(clear) { Icon(Icons.Default.ClearAll, null); Text("پاک‌سازی") } }; if (logs.isEmpty()) EmptyState(Icons.Default.Article, "گزارشی ثبت نشده است", "رویدادهای Gateway و همگام‌سازی اینجا نمایش داده می‌شوند.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) { items(logs, key = { it.id }) { log -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(log.tag, fontWeight = FontWeight.Bold); Text(log.message, style = MaterialTheme.typography.bodySmall); Text(dateText(log.timestamp), style = MaterialTheme.typography.labelSmall) } } } } }
}
@Composable private fun SectionHeader(title: String, subtitle: String? = null) { Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun MetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier) { Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f))) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun QuickAction(label: String, icon: ImageVector, action: () -> Unit, modifier: Modifier, enabled: Boolean = true) { Surface(modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled, onClick = action), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) .58f else .25f)) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun InfoRow(label: String, value: String, healthy: Boolean) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(healthy); Spacer(Modifier.width(6.dp)); Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
@Composable private fun GatewayStatusLabel(enabled: Boolean) { Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(enabled); Spacer(Modifier.width(8.dp)); Text(if (enabled) "Gateway فعال" else "Gateway خاموش") } }
@Composable private fun StatusDot(enabled: Boolean) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (enabled) Color(0xFF65D391) else MaterialTheme.colorScheme.outline)) }
@Composable private fun EmptyState(icon: ImageVector, title: String, message: String, todo: String? = null) { Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(52.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center); todo?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center) } } } }
@Composable private fun DisabledState(title: String, message: String) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Bold); Text(message, style = MaterialTheme.typography.bodySmall) } } } }
@Composable private fun ErrorState(title: String, message: String, action: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ErrorOutline, null); Spacer(Modifier.width(8.dp)); Text(title, fontWeight = FontWeight.Bold) }; Text(message, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall); TextButton(action) { Text("رفتن به تنظیمات") } } } }
private fun hasPermission(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
private fun dateText(value: Long) = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")).format(Date(value))
private fun statusLabel(value: String) = when (value) { "همه" -> "همه"; "PENDING" -> "در انتظار"; "PROCESSING" -> "در حال ارسال"; "SENT" -> "ارسال شد"; "DELIVERED" -> "تحویل شد"; "FAILED" -> "ناموفق"; "CANCELLED" -> "لغو شد"; else -> value }
private fun statusColor(value: String) = when (value) { "PENDING" -> Color(0xFFFFB74D); "PROCESSING" -> Color(0xFF64B5F6); "SENT", "DELIVERED" -> Color(0xFF65D391); "FAILED" -> Color(0xFFFF7777); else -> Color(0xFFB8B8C2) }
@Composable fun Greeting(name: String) { Text("سلام، $name") }
