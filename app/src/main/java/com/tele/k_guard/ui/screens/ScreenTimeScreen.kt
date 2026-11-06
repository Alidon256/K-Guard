package com.tele.k_guard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import com.tele.k_guard.ui.components.PhoneLockDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.AppUsageItem
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.DeviceLockState
import com.tele.k_guard.data.model.RemoteAction
import com.tele.k_guard.data.model.RemoteCommand
import com.tele.k_guard.data.model.ScreenTimeConfig
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.ScreenTimeViewModel
import com.tele.k_guard.viewmodel.SessionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenTimeScreen(
    viewModel: ScreenTimeViewModel,
    session: SessionViewModel? = null,
    activeAgeProfile: AgeProfile = AgeProfile.PRETEENS_8_12,
    onNavigateToChildProfile: (String) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val device by viewModel.currentDevice.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()
    val feedbackMessage by viewModel.actionFeedback.collectAsState()

    val childrenFromSession = session?.children?.collectAsState()?.value
    val selectedChildFromSession = session?.selectedChild?.collectAsState()?.value
    val familyId = session?.familyId?.collectAsState()?.value

    val children = childrenFromSession.orEmpty()
    val appActivity by viewModel.appActivity.collectAsState()

    // Follow the child selected in the session; write age-based starting rules the first time.
    LaunchedEffect(familyId, selectedChildFromSession?.id) {
        val child = selectedChildFromSession ?: children.firstOrNull()
        if (child != null && familyId != null) {
            viewModel.setFamilyAndChild(familyId, child.id, child.name)
            viewModel.initializeDefaultsIfNeeded(child.age)
        }
    }

    if (children.isEmpty()) {
        Scaffold(topBar = { KGuardTopAppBar(title = "Screen Time", canNavigateBack = true, onNavigateBack = onNavigateBack) }) { p ->
            Box(Modifier.fillMaxSize().padding(p).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Add a child profile and pair their phone to manage screen time.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    ScreenTimeDashboardContent(
        device = device,
        children = children,
        selectedChildId = device.childId,
        activeAgeProfile = activeAgeProfile,
        commandHistory = commandHistory,
        onSelectChild = { child ->
            viewModel.selectChild(child.id, child.name)
            session?.selectChild(child.id)
        },
        onNavigateToChildProfile = onNavigateToChildProfile,
        onRemoteSwitchOff = { reason -> viewModel.remoteSwitchOff(device.childId, reason) },
        onRemoteTimedLock = { h, m -> viewModel.remoteTimedLock(device.childId, h, m) },
        onRemoteUnlock = { viewModel.remoteUnlock(device.childId) },
        onRemoteSoundAlarm = { viewModel.remoteSoundAlarm(device.childId) },
        onAddBonusTime = { minutes -> viewModel.addBonusTime(device.childId, minutes) },
        onUpdateDailyLimit = { limit -> viewModel.updateDailyLimit(device.childId, limit) },
        onApplyAgeRecommendation = { viewModel.applyAgeRecommendation(device.childId, activeAgeProfile) },
        onToggleBedtime = { enabled -> viewModel.updateBedtimeSchedule(device.childId, device.bedtimeStart, device.bedtimeEnd, enabled) },
        onToggleSchoolMode = { enabled -> viewModel.updateSchoolSchedule(device.childId, device.schoolStart, device.schoolEnd, enabled) },
        onChangeBedtime = { start, end -> viewModel.updateBedtimeSchedule(device.childId, start, end, true) },
        onChangeSchoolHours = { start, end -> viewModel.updateSchoolSchedule(device.childId, start, end, true) },
        appActivity = appActivity,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun ScreenTimeDashboardContent(
    device: ChildDevice,
    children: List<ChildProfile>,
    selectedChildId: String,
    activeAgeProfile: AgeProfile,
    commandHistory: List<RemoteCommand>,
    onSelectChild: (ChildProfile) -> Unit,
    onNavigateToChildProfile: (String) -> Unit = {},
    onRemoteSwitchOff: (String) -> Unit,
    onRemoteTimedLock: (Int, Int) -> Unit = { _, _ -> },
    onRemoteUnlock: () -> Unit,
    onRemoteSoundAlarm: () -> Unit,
    onAddBonusTime: (Int) -> Unit,
    onUpdateDailyLimit: (Int) -> Unit,
    onApplyAgeRecommendation: () -> Unit,
    onToggleBedtime: (Boolean) -> Unit,
    onToggleSchoolMode: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onChangeBedtime: (String, String) -> Unit = { _, _ -> },
    onChangeSchoolHours: (String, String) -> Unit = { _, _ -> },
    appActivity: List<com.tele.k_guard.data.repository.AppActivity> = emptyList()
) {
    var showSwitchOffDialog by remember { mutableStateOf(false) }
    var showTimedLockDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("Dinner Time") }
    var customReason by remember { mutableStateOf("") }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Controls & Status, 1: Apps & Breakdown, 2: Schedules, 3: History

    val selectedChild = children.find { it.id == selectedChildId } ?: children.firstOrNull()

    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "Child Screen Management",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Connected Children Device Switcher (only shown when multiple children exist)
            ChildrenDeviceSelector(
                children = children,
                selectedChildId = selectedChildId,
                selectedOnline = device.isOnline,
                onSelectChild = onSelectChild,
                onNavigateToChildProfile = onNavigateToChildProfile
            )

            Column(modifier = Modifier.padding(16.dp)) {

                com.tele.k_guard.ui.components.ChildPhoneHealthCard(device = device, modifier = Modifier.padding(bottom = 16.dp))

                // 2. Connected Phone Telemetry & Live Status Card
                ConnectedPhoneStatusCard(
                    device = device,
                    childProfile = selectedChild,
                    onNavigateToChildProfile = onNavigateToChildProfile,
                    onSoundAlarm = onRemoteSoundAlarm
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. REMOTE SCREEN SWITCH-OFF & HERO CONTROL PANEL
                RemoteSwitchOffHeroCard(
                    device = device,
                    onOpenSwitchOffDialog = { showSwitchOffDialog = true },
                    onOpenTimedLockDialog = { showTimedLockDialog = true },
                    onRemoteUnlock = onRemoteUnlock,
                    onAddBonusTime = onAddBonusTime
                )

                Spacer(modifier = Modifier.height(16.dp))

                // WhatsApp Style Filter Tabs Row for Deep Management
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    item {
                        WhatsAppFilterChip(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            label = "Screen Limits"
                        )
                    }
                    item {
                        WhatsAppFilterChip(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            label = "Apps Today"
                        )
                    }
                    item {
                        WhatsAppFilterChip(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            label = "Schedules"
                        )
                    }
                    item {
                        WhatsAppFilterChip(
                            selected = activeTab == 3,
                            onClick = { activeTab = 3 },
                            label = "Command Log"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (activeTab) {
                    0 -> {
                        // Daily Screen Time Progress & Limit Controls
                        DailyLimitManagementSection(
                            device = device,
                            activeAgeProfile = activeAgeProfile,
                            onUpdateDailyLimit = onUpdateDailyLimit,
                            onApplyAgeRecommendation = onApplyAgeRecommendation
                        )
                    }
                    1 -> {
                        // Live Apps Breakdown
                        AppUsageBreakdownSection(device = device)
                        Spacer(modifier = Modifier.height(16.dp))
                        com.tele.k_guard.ui.components.AppActivitySection(appActivity, device.childName)
                    }
                    2 -> {
                        // Schedules & Focus Modes
                        SchedulesSection(
                            device = device,
                            onToggleBedtime = onToggleBedtime,
                            onToggleSchoolMode = onToggleSchoolMode,
                            onChangeBedtime = onChangeBedtime,
                            onChangeSchoolHours = onChangeSchoolHours
                        )
                    }
                    3 -> {
                        // Audit Log of Remote Commands
                        RemoteCommandHistorySection(commandHistory = commandHistory)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Switch Off Confirmation Dialog
    if (showSwitchOffDialog) {
        val quickReasons = listOf("Dinner Time", "Bedtime / Sleep", "Study & Homework", "Screen Time Exceeded")
        AlertDialog(
            onDismissRequest = { showSwitchOffDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Switch Off ${device.childName}'s Screen?",
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column {
                    Text(
                        text = "${device.childName}'s phone switches its screen off and shows a lock screen until you unlock it. Emergency calls stay available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SELECT REASON FOR LOCKOUT:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    quickReasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = reason }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                label = { Text(reason) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text("Or custom note...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalReason = customReason.trim().ifEmpty { selectedReason }
                        onRemoteSwitchOff(finalReason)
                        showSwitchOffDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Switch Off Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchOffDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Timed Screen Lockout Dialog
    if (showTimedLockDialog) {
        PhoneLockDialog(
            childName = device.childName,
            onDismiss = { showTimedLockDialog = false },
            onLockConfirm = { hours, minutes, isImmediate ->
                if (isImmediate) {
                    onRemoteSwitchOff("Immediate Remote Screen Lock")
                } else {
                    onRemoteTimedLock(hours, minutes)
                }
            }
        )
    }
}

@Composable
private fun WhatsAppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------- UI COMPONENTS ----------------

@Composable
fun ChildrenDeviceSelector(
    children: List<ChildProfile>,
    selectedChildId: String,
    /** Whether the selected child's phone is connected; other children's status is not loaded here. */
    selectedOnline: Boolean = false,
    onSelectChild: (ChildProfile) -> Unit,
    onNavigateToChildProfile: (String) -> Unit = {}
) {
    if (children.size <= 1) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(children) { child ->
            val isSelected = child.id == selectedChildId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (isSelected) {
                            onNavigateToChildProfile(child.id)
                        } else {
                            onSelectChild(child)
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .width(68.dp)
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .padding(if (isSelected) 3.dp else 0.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        com.tele.k_guard.ui.components.ChildAvatar(
                            name = child.name,
                            photoUrl = child.photoUrl,
                            size = 52.dp
                        )
                    }

                    if (isSelected && selectedOnline) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4ADE80))
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = child.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ConnectedPhoneStatusCard(
    device: ChildDevice,
    childProfile: ChildProfile? = null,
    onNavigateToChildProfile: (String) -> Unit = {},
    onSoundAlarm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Child Avatar + Model & Online Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Takes the space left by the badges; a long device name is shortened rather than squeezing them.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                val targetId = childProfile?.id ?: device.childId
                                if (targetId.isNotBlank()) onNavigateToChildProfile(targetId)
                            },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            com.tele.k_guard.ui.components.ChildAvatar(
                                name = childProfile?.name ?: device.childName.ifBlank { "Child" },
                                photoUrl = childProfile?.photoUrl,
                                size = 48.dp
                            )
                        }

                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = device.deviceName.ifBlank { "${device.childName.ifBlank { "Child" }}'s phone" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (device.hasReported) "${device.deviceModel} · ${device.androidVersion}" else "Not connected yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status badges: Device Admin & Online
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Admin Protection badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (device.isDeviceAdminActive) Color(0xFFE0E7FF) else Color(0xFFFEF3C7)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (device.isDeviceAdminActive) Icons.Default.Shield else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (device.isDeviceAdminActive) Color(0xFF4F46E5) else Color(0xFFD97706),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (!device.hasReported) "NOT PAIRED" else if (device.isDeviceAdminActive) "ADMIN" else "NO ADMIN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (device.isDeviceAdminActive) Color(0xFF4338CA) else Color(0xFFB45309),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Online badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (device.isOnline) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (device.isOnline) Color(0xFF16A34A) else Color(0xFF64748B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (device.isOnline) "ONLINE" else "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = if (device.isOnline) Color(0xFF15803D) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry: battery, network, unlocks. Icons and colours follow what the phone reported;
            // when it is offline the values are marked with the time they were reported.
            val known = device.hasReported
            val stale = known && !device.isOnline
            val grey = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            val batteryIcon = when {
                !known || device.batteryPercent < 0 -> Icons.Default.BatteryUnknown
                device.isCharging -> Icons.Default.BatteryChargingFull
                device.batteryPercent <= 15 -> Icons.Default.BatteryAlert
                device.batteryPercent <= 40 -> Icons.Default.Battery3Bar
                device.batteryPercent <= 70 -> Icons.Default.Battery5Bar
                else -> Icons.Default.BatteryFull
            }
            val batteryTint = when {
                !known || device.batteryPercent < 0 || stale -> grey
                device.isCharging -> Color(0xFF16A34A)
                device.batteryPercent <= 15 -> Color(0xFFDC2626)
                device.batteryPercent <= 30 -> Color(0xFFD97706)
                else -> Color(0xFF16A34A)
            }
            val net = device.networkName
            val networkIcon = when {
                !known -> Icons.Default.SignalCellularOff
                net.startsWith("Wi-Fi") -> Icons.Default.Wifi
                net.startsWith("Mobile") -> Icons.Default.SignalCellularAlt
                net == "Offline" -> Icons.Default.WifiOff
                else -> Icons.Default.Smartphone
            }
            if (stale) {
                Text(
                    "As of ${com.tele.k_guard.ui.components.lastSeen(device)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Battery
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            batteryIcon,
                            contentDescription = null,
                            tint = batteryTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Battery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (!known || device.batteryPercent < 0) "—" else "${device.batteryPercent}%${if (device.isCharging) " · charging" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Network
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            networkIcon,
                            contentDescription = null,
                            tint = if (!known || stale || net == "Offline") grey else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Network", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (!known) "—" else net.ifBlank { "—" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Pickups
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Unlocks today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (!known) "—" else "${device.pickupsCount}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current Activity Indicator
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = when (device.lockState) {
                    DeviceLockState.REMOTE_LOCKED -> Color(0xFFFEE2E2)
                    DeviceLockState.ACTIVE_UNLOCKED -> Color(0xFFF0FDF4)
                    else -> MaterialTheme.colorScheme.surface
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !device.hasReported || !device.isOnline -> Color(0xFF94A3B8)
                                        device.lockState == DeviceLockState.REMOTE_LOCKED -> Color(0xFFDC2626)
                                        device.lockState == DeviceLockState.ACTIVE_UNLOCKED -> Color(0xFF16A34A)
                                        else -> Color(0xFFEAB308)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !device.hasReported -> "No report from the phone yet"
                                !device.isOnline -> "Offline · last seen ${com.tele.k_guard.ui.components.lastSeen(device)}"
                                device.lockState == DeviceLockState.REMOTE_LOCKED -> "Locked by you"
                                device.lockState == DeviceLockState.BEDTIME_LOCKED -> "Locked for bedtime"
                                device.lockState == DeviceLockState.LIMIT_REACHED -> "Locked · daily limit reached"
                                device.currentForegroundApp != null -> "Using ${device.currentForegroundApp}"
                                else -> "Unlocked · no app open"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = when (device.lockState) {
                                DeviceLockState.REMOTE_LOCKED -> Color(0xFF991B1B)
                                DeviceLockState.ACTIVE_UNLOCKED -> Color(0xFF166534)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    // Sound Chime Button
                    TextButton(
                        onClick = onSoundAlarm,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ring Phone", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteSwitchOffHeroCard(
    device: ChildDevice,
    onOpenSwitchOffDialog: () -> Unit,
    onOpenTimedLockDialog: () -> Unit = {},
    onRemoteUnlock: () -> Unit,
    onAddBonusTime: (Int) -> Unit
) {
    val isLocked = device.isLocked || device.screenLock.isLocked
    val until = device.lockUntilMillis ?: device.remoteLockUntilMillis
    fun hm(t: Long) = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(t))
    // Bedtime and limit reasons already say when they end; only a parent's timed lock needs the time added.
    val untilText = if (device.lockState == DeviceLockState.REMOTE_LOCKED || device.lockState == DeviceLockState.ACTIVE_UNLOCKED) {
        until?.let { " · until ${hm(it)}" } ?: ""
    } else ""
    val pausedUntil = device.overrideUntilMillis?.takeIf { it > System.currentTimeMillis() }
    val lockTitle = when (device.lockState) {
        DeviceLockState.BEDTIME_LOCKED -> "Bedtime lock is on"
        DeviceLockState.LIMIT_REACHED -> "Daily limit reached"
        DeviceLockState.REMOTE_LOCKED -> "Phone locked by you"
        else -> if (device.screenLock.isLocked) "Lock sent · waiting for the phone" else "Phone locked"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        border = if (isLocked) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isLocked) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isLocked) Icons.Default.Lock else Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isLocked) lockTitle else "Lock ${device.childName}'s phone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocked) Color(0xFF991B1B) else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = when {
                                isLocked -> (device.lockReason ?: device.screenLock.lockReason ?: "Locked") + untilText
                                pausedUntil != null -> "Bedtime and the daily limit are paused until ${hm(pausedUntil)}"
                                else -> "Switch the screen off and lock it now, or set a timed lock"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLocked) Color(0xFFB91C1C) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Primary Remote Trigger Button
            if (isLocked) {
                Button(
                    onClick = onRemoteUnlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (device.lockState == DeviceLockState.BEDTIME_LOCKED || device.lockState == DeviceLockState.LIMIT_REACHED) "Unlock for 1 hour" else "Unlock ${device.childName}'s phone",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenSwitchOffDialog,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Switch Off Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenTimedLockDialog,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Timed Lock", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Bonus Screen Time Rewards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onAddBonusTime(15) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.MoreTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+15 min today", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = { onAddBonusTime(30) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.MoreTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+30 min today", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun DailyLimitManagementSection(
    device: ChildDevice,
    activeAgeProfile: AgeProfile,
    onUpdateDailyLimit: (Int) -> Unit,
    onApplyAgeRecommendation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Usage Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Today's Screen Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = if (device.effectiveLimitMinutes > 0) "${device.usedScreenTimeMinutes}m / ${device.effectiveLimitMinutes}m" else "${device.usedScreenTimeMinutes}m · no limit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (device.effectiveLimitMinutes > 0 && device.remainingMinutes == 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visual Progress Bar
            val progress = device.usageProgress
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                progress >= 1f -> Color(0xFFDC2626)
                                progress >= 0.8f -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        device.effectiveLimitMinutes <= 0 -> "No daily limit set"
                        device.bonusMinutesToday > 0 -> "${device.remainingMinutes} min left today (includes ${device.bonusMinutesToday} min bonus)"
                        else -> "${device.remainingMinutes} min left today"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(progress * 100).toInt()}% used",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Slider
            // Drag locally and save once on release; each save is sent to the child's phone.
            var draft by remember(device.dailyLimitMinutes) { mutableStateOf(device.dailyLimitMinutes.coerceIn(15, 360).toFloat()) }
            val draftMinutes = (draft / 15f).toInt() * 15
            Text(
                text = if (device.dailyLimitMinutes <= 0) "Daily limit: none · drag to set one"
                else "Daily limit: ${com.tele.k_guard.screentime.LockEvaluator.formatMinutes(draftMinutes)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = { if (draftMinutes != device.dailyLimitMinutes) onUpdateDailyLimit(draftMinutes) },
                valueRange = 15f..360f,
                steps = 22
            )

            // Quick Preset Limit Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0, 60, 90, 120, 150, 180).forEach { mins ->
                    val isSelected = device.dailyLimitMinutes == mins
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { onUpdateDailyLimit(mins) }
                            .padding(2.dp)
                    ) {
                        Text(
                            text = if (mins == 0) "None" else "${mins}m",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Age-Aware AI Guidance Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "K-Guard Pediatric Recommendation",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ScreenTimeConfig.getRecommendationForAge(activeAgeProfile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onApplyAgeRecommendation,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Apply Recommended Limit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AppUsageBreakdownSection(device: ChildDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Live App Breakdown Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Track which apps your child spends the most time on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (device.appUsages.isEmpty()) {
                Text(
                    text = "No app usage recorded yet today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                device.appUsages.forEach { app ->
                    val appProgress = (app.usedMinutes.toFloat() / (app.dailyLimitMinutes ?: 60)).coerceIn(0f, 1f)

                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.appName.take(1),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(app.appName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        if (app.isCurrentlyActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFDCFCE7)
                                            ) {
                                                Text(
                                                    "ACTIVE NOW",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF15803D),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                    Text(app.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text = "${app.usedMinutes} mins",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(appProgress)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SchedulesSection(
    device: ChildDevice,
    onToggleBedtime: (Boolean) -> Unit,
    onToggleSchoolMode: (Boolean) -> Unit,
    onChangeBedtime: (String, String) -> Unit = { _, _ -> },
    onChangeSchoolHours: (String, String) -> Unit = { _, _ -> }
) {
    var editing by remember { mutableStateOf<String?>(null) } // "bedtime" | "school"
    when (editing) {
        "bedtime" -> com.tele.k_guard.ui.components.TimeRangeDialog("Bedtime", device.bedtimeStart, device.bedtimeEnd, { editing = null }) { s, e ->
            editing = null; onChangeBedtime(s, e)
        }
        "school" -> com.tele.k_guard.ui.components.TimeRangeDialog("School hours", device.schoolStart, device.schoolEnd, { editing = null }) { s, e ->
            editing = null; onChangeSchoolHours(s, e)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Bedtime Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF312E81)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Bedtime Downtime", fontWeight = FontWeight.Bold)
                            Text(
                                "${device.bedtimeStart} to ${device.bedtimeEnd}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = device.bedtimeEnabled,
                        onCheckedChange = onToggleBedtime
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Every night the phone switches its screen off and stays locked until the end time. ${device.childName.ifBlank { "Your child" }} gets a warning 10 minutes before. Emergency calls stay available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { editing = "bedtime" }) { Text("Change times") }
            }
        }

        // School Mode Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("School Hours Focus", fontWeight = FontWeight.Bold)
                            Text(
                                "${device.schoolStart} to ${device.schoolEnd}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = device.schoolEnabled,
                        onCheckedChange = onToggleSchoolMode
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Monday to Friday, games, video, music and social apps are paused during these hours. Calls, messages, maps and learning apps keep working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { editing = "school" }) { Text("Change times") }
            }
        }
    }
}

@Composable
fun RemoteCommandHistorySection(commandHistory: List<RemoteCommand>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remote Activity & Command Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "History of remote lockouts, screen time bonuses, and device actions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (commandHistory.isEmpty()) {
                Text(
                    text = "No remote commands sent yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val dateFormat = SimpleDateFormat("HH:mm · MMM d", Locale.getDefault())
                commandHistory.forEach { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    when (cmd.action) {
                                        RemoteAction.SWITCH_OFF_LOCK -> Color(0xFFFEE2E2)
                                        RemoteAction.UNLOCK_DEVICE -> Color(0xFFDCFCE7)
                                        RemoteAction.SOUND_ALARM -> Color(0xFFFEF3C7)
                                        RemoteAction.ADD_BONUS_TIME -> Color(0xFFEDE9FE)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when (cmd.action) {
                                    RemoteAction.SWITCH_OFF_LOCK -> Icons.Default.PowerSettingsNew
                                    RemoteAction.UNLOCK_DEVICE -> Icons.Default.LockOpen
                                    RemoteAction.SOUND_ALARM -> Icons.Default.VolumeUp
                                    RemoteAction.ADD_BONUS_TIME -> Icons.Default.MoreTime
                                    else -> Icons.Default.History
                                },
                                contentDescription = null,
                                tint = when (cmd.action) {
                                    RemoteAction.SWITCH_OFF_LOCK -> Color(0xFFDC2626)
                                    RemoteAction.UNLOCK_DEVICE -> Color(0xFF16A34A)
                                    RemoteAction.SOUND_ALARM -> Color(0xFFD97706)
                                    RemoteAction.ADD_BONUS_TIME -> Color(0xFF7C3AED)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cmd.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${dateFormat.format(Date(cmd.timestamp))} · ${cmd.status}",
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

// ---------------- BACKWARD COMPATIBLE SCREEN TIME CONTENT PREVIEW ----------------

@Preview(showBackground = true)
@Composable
fun ScreenTimeDashboardPreview() {
    KGuardTheme {
        ScreenTimeDashboardContent(
            device = ChildDevice(
                deviceId = "dev_1",
                childId = "c1",
                childName = "Ama",
                deviceName = "Ama's Galaxy A14",
                deviceModel = "Samsung Galaxy A14 5G",
                androidVersion = "Android 14",
                isOnline = true,
                batteryPercent = 78,
                networkName = "Home Wi-Fi (5 GHz)",
                currentForegroundApp = "YouTube Kids",
                usedScreenTimeMinutes = 45,
                dailyLimitMinutes = 90
            ),
            children = listOf(ChildProfile("c1", "Ama")),
            selectedChildId = "c1",
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            commandHistory = emptyList(),
            onSelectChild = {},
            onRemoteSwitchOff = {},
            onRemoteUnlock = {},
            onRemoteSoundAlarm = {},
            onAddBonusTime = {},
            onUpdateDailyLimit = {},
            onApplyAgeRecommendation = {},
            onToggleBedtime = {},
            onToggleSchoolMode = {},
            onNavigateBack = {}
        )
    }
}
