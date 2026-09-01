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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.GatewaySettings
import com.example.data.local.LogEntry
import com.example.data.local.SmsQueueItem
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SmsViewModel
import java.text.SimpleDateFormat
import java.util.*

import android.telephony.SubscriptionManager

class MainActivity : ComponentActivity() {

    private val viewModel: SmsViewModel by viewModels()

    // Request permissions dynamically
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsRead = permissions[Manifest.permission.READ_SMS] ?: false
        val smsSend = permissions[Manifest.permission.SEND_SMS] ?: false
        val smsReceive = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        val phoneState = permissions[Manifest.permission.READ_PHONE_STATE] ?: false

        if (smsRead && smsSend && smsReceive && phoneState) {
            Toast.makeText(this, "تمام مجوزهای لازم اعطا شد", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "برخی مجوزها برای کارکرد صحیح مورد نیاز است", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestPermissions()

        setContent {
            MyApplicationTheme {
                // Enforce Right-to-Left Layout direction for Farsi
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        SmsGatewayAppScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding),
                            onGrantPermissions = { checkAndRequestPermissions() }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest.toTypedArray())
        }
    }
}

// -------------------------------------------------------------------------
// COMPOSABLE UI IMPLEMENTATIONS
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsGatewayAppScreen(
    viewModel: SmsViewModel,
    modifier: Modifier = Modifier,
    onGrantPermissions: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val syncedSms by viewModel.syncedSmsState.collectAsStateWithLifecycle()
    val queue by viewModel.queueState.collectAsStateWithLifecycle()
    val logs by viewModel.logsState.collectAsStateWithLifecycle()
    val syncing by viewModel.syncingState.collectAsStateWithLifecycle()
    val toastMsg by viewModel.toastMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("داشبورد", "ارسال پیام", "صف ارسال", "تنظیمات", "لاگ‌ها")

    // Show feedback toasts if triggered
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // App Header
        CenterAlignedTopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "سامانه پیامک SMS Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        )

        // Navigation Tabs Row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // Tab Contents Screen Switch
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> DashboardTab(
                    settings = settings,
                    syncedCount = syncedSms.size,
                    queue = queue,
                    syncing = syncing,
                    onToggleGateway = { viewModel.toggleGateway(it) },
                    onSyncNow = { viewModel.triggerManualSync() },
                    onGrantPermissions = onGrantPermissions
                )
                1 -> SendSmsTab(
                    onSendSms = { phone, text, sim -> viewModel.enqueueManualSms(phone, text, sim) },
                    isTestMode = settings.isTestMode
                )
                2 -> QueueTab(
                    queue = queue,
                    onClearHistory = { viewModel.clearHistory() }
                )
                3 -> SettingsTab(
                    settings = settings,
                    onSave = { viewModel.saveSettings(it) }
                )
                4 -> LogsTab(
                    logs = logs,
                    onClearLogs = { viewModel.clearSystemLogs() }
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// TAB 1: DASHBOARD SCREEN
// -------------------------------------------------------------------------

@Composable
fun DashboardTab(
    settings: GatewaySettings,
    syncedCount: Int,
    queue: List<SmsQueueItem>,
    syncing: Boolean,
    onToggleGateway: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onGrantPermissions: () -> Unit
) {
    val context = LocalContext.current
    val hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val hasSendPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gateway Toggle Hero Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (settings.isGatewayEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "وضعیت درگاه پیامک",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (settings.isGatewayEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (settings.isGatewayEnabled) "سرویس پس‌زمینه فعال است" else "درگاه غیرفعال می‌باشد",
                                fontSize = 13.sp,
                                color = if (settings.isGatewayEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = settings.isGatewayEnabled,
                            onCheckedChange = { onToggleGateway(it) }
                        )
                    }

                    if (settings.isGatewayEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "درحال همگام‌سازی خودکار (هر ${settings.syncIntervalSeconds} ثانیه)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Permissions Card Warning
        if (!hasSmsPermission || !hasSendPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مجوزهای دسترسی قطع هستند",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "برای دریافت و ارسال پیامک، سیستم نیازمند تایید دستی مجوزهای گوشی است.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onGrantPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("اعطای مجوزها")
                        }
                    }
                }
            }
        }

        // Stats Summaries Grid
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Synced Count Card
                Card(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "پیام‌های همگام شده", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = syncedCount.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // In Queue Pending Count Card
                Card(
                    modifier = Modifier.weight(1f)
                ) {
                    val pendingCount = queue.count { it.status == "PENDING" || it.status == "PROCESSING" }
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "در انتظار ارسال", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = pendingCount.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (pendingCount > 0) MaterialTheme.colorScheme.primary else Color.Unspecified)
                    }
                }
            }
        }

        // Sync Action and General Metadata Info
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "مشخصات درگاه فعال", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    DetailRow(label = "شناسه دستگاه:", value = settings.deviceId)
                    DetailRow(label = "آدرس سرور پنل:", value = settings.serverUrl.ifBlank { "تنظیم نشده" })
                    DetailRow(label = "حالت تستی:", value = if (settings.isTestMode) "فعال (بدون کسر شارژ)" else "غیرفعال (ارسال واقعی)")
                    DetailRow(label = "بازه ساعت کاری:", value = "${settings.workingHoursStart} تا ${settings.workingHoursEnd}")

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onSyncNow,
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("در حال همگام‌سازی...")
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("همگام‌سازی دستی سریع")
                        }
                    }
                }
            }
        }

        // Device System Capabilities & SIMs status
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "وضعیت سیم‌کارت‌های دستگاه", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val activeList = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
                    } else {
                        emptyList()
                    }

                    if (activeList.isNotEmpty()) {
                        Text(text = "تعداد ${activeList.size} سیم‌کارت فعال شناسایی شد.", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        for (index in activeList.indices) {
                            val subInfo = activeList[index]
                            Text(
                                text = "• سیم‌کارت ${index + 1}: ${subInfo.carrierName} (اسلات شماره ${subInfo.simSlotIndex})",
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(text = "هیچ اطلاعات سیم‌کارتی یافت نشد. (احتمال عدم اعطای مجوز READ_PHONE_STATE یا عدم قرارگیری سیم‌کارت)", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text = label, color = Color.Gray, fontSize = 13.sp)
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// CircularProgressIndicator custom helper for pre-API O compatibility
@Composable
fun CircularProgressIndicator(size: androidx.compose.ui.unit.Dp, color: Color) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = 2.dp
    )
}

// -------------------------------------------------------------------------
// TAB 2: SEND MANUAL SMS TAB
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendSmsTab(
    onSendSms: (String, String, Int) -> Unit,
    isTestMode: Boolean
) {
    var phoneNumber by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var selectedSimSlot by remember { mutableStateOf(-1) } // -1: Default, 0: SIM1, 1: SIM2

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ارسال پیامک جدید دستوری",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "از این بخش می‌توانید پیامک‌های تکی به گوشی یا مخاطبین جهت تست ارسال فرمایید.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        if (isTestMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "سامانه در حالت تست است. پیامکی بصورت واقعی ارسال نشده و تستی شبیه‌سازی خواهد شد.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("شماره موبایل گیرنده") },
                placeholder = { Text("مثال: 09123456789") },
                leadingIcon = { Icon(imageVector = Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("متن پیامک") },
                placeholder = { Text("پیامک فارسی یا انگلیسی خود را اینجا وارد کنید...") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Farsi character details calculation helper
            val charCount = messageText.length
            val isPersian = messageText.any { it.code in 0x0600..0x06FF }
            val limit = if (isPersian) 70 else 160
            val parts = if (charCount == 0) 0 else (charCount - 1) / limit + 1

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(text = "نوع نگارش: ${if (isPersian) "فارسی" else "انگلیسی/Unicode"}", fontSize = 11.sp, color = Color.Gray)
                Text(text = "حروف: $charCount | بخش‌ها: $parts", fontSize = 11.sp, color = Color.Gray)
            }
        }

        // SIM Slot selector Chip row
        item {
            Column {
                Text(text = "انتخاب سیم‌کارت فرستنده:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedSimSlot == -1,
                        onClick = { selectedSimSlot = -1 },
                        label = { Text("پیش‌فرض سیستم") }
                    )
                    FilterChip(
                        selected = selectedSimSlot == 0,
                        onClick = { selectedSimSlot = 0 },
                        label = { Text("سیم‌کارت ۱") }
                    )
                    FilterChip(
                        selected = selectedSimSlot == 1,
                        onClick = { selectedSimSlot = 1 },
                        label = { Text("سیم‌کارت ۲") }
                    )
                }
            }
        }

        // Submit action button
        item {
            Button(
                onClick = {
                    onSendSms(phoneNumber, messageText, selectedSimSlot)
                    phoneNumber = ""
                    messageText = ""
                },
                enabled = phoneNumber.isNotBlank() && messageText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ارسال مستقیم به صف")
            }
        }
    }
}

// -------------------------------------------------------------------------
// TAB 3: SMS OUTBOX / QUEUE MONITOR
// -------------------------------------------------------------------------

@Composable
fun QueueTab(
    queue: List<SmsQueueItem>,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "صف و تاریخچه پیامک‌ها (${queue.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )

            if (queue.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("پاک‌سازی تاریخچه")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (queue.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Outlined.SmsFailed, contentDescription = null, size = 64.dp, tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "صف پیامک خالی است", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(queue) { item ->
                    QueueItemRow(item = item)
                }
            }
        }
    }
}

// Icon size helper to avoid issues
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp, tint: Color) {
    androidx.compose.material3.Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

@Composable
fun QueueItemRow(item: SmsQueueItem) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.status == "PENDING") Icons.Default.Schedule else Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = item.phoneNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Status Badge
                val (badgeColor, label) = when (item.status) {
                    "PENDING" -> Color(0xFFFFA500) to "در انتظار"
                    "PROCESSING" -> Color(0xFF007FFF) to "درحال ارسال"
                    "SENT" -> Color(0xFF2E8B57) to "ارسال شد"
                    "DELIVERED" -> Color(0xFF008000) to "تحویل شد"
                    "FAILED" -> Color(0xFFD32F2F) to "ناموفق"
                    "CANCELLED" -> Color(0xFF7F7F7F) to "لغو شده"
                    else -> Color.Gray to item.status
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = label, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = item.messageBody, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                val dateStr = SimpleDateFormat("HH:mm:ss - yyyy/MM/dd", Locale.US).format(Date(item.createdAt))
                Text(text = "زمان ثبت: $dateStr", fontSize = 11.sp, color = Color.Gray)
                
                val simLabel = if (item.simSlot == -1) "پیش‌فرض" else "سیم ${item.simSlot + 1}"
                Text(text = "فرستنده: $simLabel", fontSize = 11.sp, color = Color.Gray)
            }

            if (!item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "علت خطا: ${item.errorMessage}", color = Color.Red, fontSize = 11.sp)
            }
        }
    }
}

// -------------------------------------------------------------------------
// TAB 4: SETTINGS SCREEN
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    settings: GatewaySettings,
    onSave: (GatewaySettings) -> Unit
) {
    var serverUrl by remember { mutableStateOf(settings.serverUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var deviceId by remember { mutableStateOf(settings.deviceId) }
    var syncInterval by remember { mutableStateOf(settings.syncIntervalSeconds.toString()) }
    var webhookUrl by remember { mutableStateOf(settings.webhookUrl) }
    var webhookSecret by remember { mutableStateOf(settings.webhookSecret) }
    var isTestMode by remember { mutableStateOf(settings.isTestMode) }
    var dailyLimit by remember { mutableStateOf(settings.limitSmsPerDay.toString()) }
    var startHours by remember { mutableStateOf(settings.workingHoursStart) }
    var endHours by remember { mutableStateOf(settings.workingHoursEnd) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "تنظیمات فنی و محدودیت‌ها",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "از این بخش تنظیمات اتصال به پنل وب Flask، وب‌هوک و میزان محدودیت‌ها را ویرایش نمایید.",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        // Test Mode Toggle Setting Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isTestMode) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "حالت تست (شبیه‌سازی ارسال)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "با فعال‌سازی این مورد، هزینه واقعی ارسال پیامک از سیم‌کارت کسر نمی‌شود.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = isTestMode, onCheckedChange = { isTestMode = it })
                }
            }
        }

        item {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("آدرس سرور پنل وب Flask") },
                placeholder = { Text("https://my-sms-panel.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("کلید امنیتی اتصال (API Key)") },
                placeholder = { Text("کلید احراز هویت اتصال به پنل") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                label = { Text("شناسه یکتای دستگاه") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = syncInterval,
                onValueChange = { syncInterval = it },
                label = { Text("بازه زمانی همگام‌سازی خودکار (به ثانیه)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = { webhookUrl = it },
                label = { Text("آدرس ارسال وب‌هوک (رویدادها و دریافت‌ها)") },
                placeholder = { Text("https://my-panel.com/webhook") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = webhookSecret,
                onValueChange = { webhookSecret = it },
                label = { Text("کلید هشدار وب‌هوک (Signature Secret Key)") },
                placeholder = { Text("جهت محاسبه امضای HMAC") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startHours,
                    onValueChange = { startHours = it },
                    label = { Text("ساعت شروع کار") },
                    placeholder = { Text("08:00") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endHours,
                    onValueChange = { endHours = it },
                    label = { Text("ساعت پایان کار") },
                    placeholder = { Text("22:00") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = dailyLimit,
                onValueChange = { dailyLimit = it },
                label = { Text("سقف محدودیت ارسال روزانه پیامک") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = {
                    val updated = settings.copy(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        deviceId = deviceId,
                        syncIntervalSeconds = syncInterval.toIntOrNull() ?: 30,
                        webhookUrl = webhookUrl,
                        webhookSecret = webhookSecret,
                        isTestMode = isTestMode,
                        limitSmsPerDay = dailyLimit.toIntOrNull() ?: 500,
                        workingHoursStart = startHours,
                        workingHoursEnd = endHours
                    )
                    onSave(updated)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ذخیره تنظیمات")
            }
        }
    }
}

// -------------------------------------------------------------------------
// TAB 5: SYSTEM ERROR & DIAGNOSTIC LOGS
// -------------------------------------------------------------------------

@Composable
fun LogsTab(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "لاگ‌های سیستم و عملکرد (${logs.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )

            if (logs.isNotEmpty()) {
                TextButton(
                    onClick = onClearLogs,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Icon(imageVector = Icons.Default.ClearAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("پاک‌سازی لاگ‌ها")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Outlined.Info, contentDescription = null, size = 64.dp, tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "تاریخچه لاگی خالی است", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { log ->
                    LogRow(log = log)
                }
            }
        }
    }
}

@Composable
fun LogRow(log: LogEntry) {
    val levelColor = when (log.level) {
        "ERROR" -> Color(0xFFD32F2F)
        "WARN" -> Color(0xFFFFA500)
        else -> Color(0xFF008000)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(levelColor.copy(alpha = 0.05f))
            .padding(8.dp)
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(levelColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = log.tag, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = levelColor)
                }
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp))
                Text(text = timeStr, fontSize = 10.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = log.message, fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
