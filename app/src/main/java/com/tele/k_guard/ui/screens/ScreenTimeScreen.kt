package com.tele.k_guard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.DeviceLockState
import com.tele.k_guard.data.model.RemoteAction
import com.tele.k_guard.data.model.RemoteCommand
import com.tele.k_guard.data.model.ScreenTimeConfig
import com.tele.k_guard.ui.components.KGDangerButton
import com.tele.k_guard.ui.components.KGCard
import com.tele.k_guard.ui.components.KGEmptyState
import com.tele.k_guard.ui.components.KGPrimaryButton
import com.tele.k_guard.ui.components.KGSectionHeader
import com.tele.k_guard.ui.components.KGSecondaryButton
import com.tele.k_guard.ui.components.KGStatusTag
import com.tele.k_guard.ui.components.KGTextLink
import com.tele.k_guard.ui.components.KGTextField
import com.tele.k_guard.ui.components.KGToggleRow
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGColors
import com.tele.k_guard.ui.theme.KGShapes
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
    onNavigateBack: () -> Unit,
    onNavigateToChildren: () -> Unit = {}
) {
    val device by viewModel.currentDevice.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()
    val feedbackMessage by viewModel.actionFeedback.collectAsState()

    val childrenFromSession = session?.children?.collectAsState()?.value
    val selectedChildFromSession = session?.selectedChild?.collectAsState()?.value
    val familyId = session?.familyId?.collectAsState()?.value

    val children = childrenFromSession.orEmpty()

    // Sync family and selected child from session
    LaunchedEffect(familyId, selectedChildFromSession) {
        val child = selectedChildFromSession ?: children.firstOrNull()
        if (child != null) {
            viewModel.setFamilyAndChild(familyId, child.id, child.name)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    // Key on the feedback id (not the message string) so repeated identical actions re-show the snackbar.
    LaunchedEffect(feedbackMessage?.id) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.clearFeedback()
        }
    }

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
        if (children.isEmpty()) {
            // Honest empty state: without a paired child there is no telemetry and no family
            // to command, so the whole dashboard stays hidden instead of showing zeros.
            KGEmptyState(
                icon = Icons.Default.Devices,
                title = "No connected device",
                description = "Pair a child's device to see live screen-time data and send remote commands.",
                action = { KGPrimaryButton("Go to children", onClick = onNavigateToChildren) },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
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
                onRemoteSwitchOff = { reason -> viewModel.remoteSwitchOff(device.childId, reason) },
                onRemoteUnlock = { viewModel.remoteUnlock(device.childId) },
                onRemoteSoundAlarm = { viewModel.remoteSoundAlarm(device.childId) },
                onAddBonusTime = { minutes -> viewModel.addBonusTime(device.childId, minutes) },
                onUpdateDailyLimit = { limit -> viewModel.updateDailyLimit(device.childId, limit) },
                onApplyAgeRecommendation = { viewModel.applyAgeRecommendation(device.childId, activeAgeProfile) },
                onToggleBedtime = { enabled -> viewModel.updateBedtimeSchedule(device.childId, device.bedtimeStart, device.bedtimeEnd, enabled) },
                onToggleSchoolMode = { enabled -> viewModel.updateSchoolSchedule(device.childId, device.schoolStart, device.schoolEnd, enabled) },
                onNavigateBack = onNavigateBack,
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@Composable
fun ScreenTimeDashboardContent(
    device: ChildDevice,
    children: List<ChildProfile>,
    selectedChildId: String,
    activeAgeProfile: AgeProfile,
    commandHistory: List<RemoteCommand>,
    onSelectChild: (ChildProfile) -> Unit,
    onRemoteSwitchOff: (String) -> Unit,
    onRemoteUnlock: () -> Unit,
    onRemoteSoundAlarm: () -> Unit,
    onAddBonusTime: (Int) -> Unit,
    onUpdateDailyLimit: (Int) -> Unit,
    onApplyAgeRecommendation: () -> Unit,
    onToggleBedtime: (Boolean) -> Unit,
    onToggleSchoolMode: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var showSwitchOffDialog by rememberSaveable { mutableStateOf(false) }
    var selectedReason by rememberSaveable { mutableStateOf("Dinner Time") }
    var customReason by rememberSaveable { mutableStateOf("") }
    var activeTab by rememberSaveable { mutableIntStateOf(0) } // 0: Controls & Status, 1: Apps & Breakdown, 2: Schedules, 3: History

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
            // 1. Connected Children Device Switcher
            ChildrenDeviceSelector(
                children = children,
                selectedChildId = selectedChildId,
                onSelectChild = onSelectChild
            )

            Column(modifier = Modifier.padding(16.dp)) {

                // 2. Connected Phone Telemetry & Live Status Card
                ConnectedPhoneStatusCard(
                    device = device,
                    onSoundAlarm = onRemoteSoundAlarm
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. The screen's single gradient spotlight: the remote switch-off control.
                RemoteSwitchOffHeroCard(
                    device = device,
                    onOpenSwitchOffDialog = { showSwitchOffDialog = true },
                    onRemoteUnlock = onRemoteUnlock,
                    onAddBonusTime = onAddBonusTime
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Row for Deep Management
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Screen Limits", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Apps Today", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("Schedules", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        text = { Text("Command Log", fontWeight = FontWeight.SemiBold) }
                    )
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
                    }
                    2 -> {
                        // Schedules & Focus Modes
                        SchedulesSection(
                            device = device,
                            onToggleBedtime = onToggleBedtime,
                            onToggleSchoolMode = onToggleSchoolMode
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
                        .clip(KGShapes.circle)
                        .background(KGColors.danger.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = KGColors.danger,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Switch off ${device.childName}'s screen?",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column {
                    Text(
                        text = "This will remotely turn off the screen, lock ${device.deviceModel}, and pause app access immediately.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "REASON FOR LOCKOUT",
                        style = MaterialTheme.typography.labelSmall,
                        color = KGColors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    quickReasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = { Text(reason, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    KGTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = "Or add a custom note",
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                KGDangerButton(
                    text = "Switch off now",
                    onClick = {
                        val finalReason = customReason.trim().ifEmpty { selectedReason }
                        onRemoteSwitchOff(finalReason)
                        showSwitchOffDialog = false
                    }
                )
            },
            dismissButton = {
                KGTextLink(text = "Cancel", onClick = { showSwitchOffDialog = false })
            }
        )
    }
}

// ---------------- UI COMPONENTS ----------------

@Composable
fun ChildrenDeviceSelector(
    children: List<ChildProfile>,
    selectedChildId: String,
    onSelectChild: (ChildProfile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        children.forEach { child ->
            val isSelected = child.id == selectedChildId
            Surface(
                onClick = { onSelectChild(child) },
                shape = KGShapes.pill,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(KGShapes.circle)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    // NOTE: no per-child online indicator here — the ViewModel only exposes live
                    // telemetry for the currently selected child, so fabricating presence for the
                    // other children would be dishonest. The status card shows real online state.
                }
            }
        }
    }
}

@Composable
fun ConnectedPhoneStatusCard(
    device: ChildDevice,
    onSoundAlarm: () -> Unit
) {
    val batteryColor = if (device.batteryPercent > 20) KGColors.success else KGColors.danger
    val lockStateColor = when (device.lockState) {
        DeviceLockState.REMOTE_LOCKED -> KGColors.danger
        DeviceLockState.ACTIVE_UNLOCKED -> KGColors.success
        else -> KGColors.warning
    }

    KGCard(modifier = Modifier.fillMaxWidth()) {
        // Header: identity + live connectivity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(KGShapes.circle)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = device.deviceName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${device.deviceModel} · ${device.androidVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KGColors.inkMuted
                    )
                }
            }

            KGStatusTag(
                text = if (device.isOnline) "Online" else "Offline",
                color = if (device.isOnline) KGColors.success else KGColors.inkMuted
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Battery row with level bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (device.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    contentDescription = null,
                    tint = batteryColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Battery", style = MaterialTheme.typography.bodySmall, color = KGColors.inkMuted)
            }
            Text(
                text = "${device.batteryPercent}%${if (device.isCharging) " · charging" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { device.batteryPercent / 100f },
            color = batteryColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(KGShapes.pill)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Network + Pickups telemetry
        Row(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Network", style = MaterialTheme.typography.labelSmall, color = KGColors.inkMuted)
                    Text(
                        device.networkName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Pickups", style = MaterialTheme.typography.labelSmall, color = KGColors.inkMuted)
                    Text(
                        "${device.pickupsCount} today",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live activity strip
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = KGShapes.cardSmall,
            color = lockStateColor.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(KGShapes.circle)
                        .background(lockStateColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (device.lockState) {
                        DeviceLockState.REMOTE_LOCKED -> "Screen is switched off · locked remotely"
                        DeviceLockState.ACTIVE_UNLOCKED -> "Screen is on · using ${device.currentForegroundApp ?: "apps"}"
                        else -> device.lockState.label
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = lockStateColor,
                    modifier = Modifier.weight(1f)
                )

                // Sound chime action
                KGTextLink("Ring phone", onClick = onSoundAlarm)
            }
        }
    }
}

/**
 * The screen's single gradient spotlight card (DESIGN.md allows one per screen): the remote
 * switch-off control, violet→magenta with white ink and an inverse white pill CTA.
 */
@Composable
fun RemoteSwitchOffHeroCard(
    device: ChildDevice,
    onOpenSwitchOffDialog: () -> Unit,
    onRemoteUnlock: () -> Unit,
    onAddBonusTime: (Int) -> Unit
) {
    val isLocked = device.lockState == DeviceLockState.REMOTE_LOCKED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(KGColors.gradientViolet, KGColors.gradientMagenta)
                ),
                shape = KGShapes.spotlight
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(KGShapes.circle)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isLocked) "Device switched off & locked" else "Remote screen switch-off",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = if (isLocked) "Locked for: “${device.lockReason ?: "Parental lock"}”"
                        else "Turn off the screen and pause the phone instantly",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inverse white pill — the spotlight card's paired CTA per DESIGN.md.
            Surface(
                onClick = if (isLocked) onRemoteUnlock else onOpenSwitchOffDialog,
                shape = KGShapes.pill,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isLocked) Icons.Default.LockOpen else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLocked) "Unlock & resume ${device.childName}'s phone" else "Switch off phone",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick bonus screen time rewards — translucent pills on the spotlight ground.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 30).forEach { mins ->
                    Surface(
                        onClick = { onAddBonusTime(mins) },
                        shape = KGShapes.pill,
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MoreTime,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+$mins min bonus",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
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
    KGSectionHeader(
        title = "Daily screen time",
        hint = "Set how long ${device.childName} can use the device today."
    )

    KGCard(modifier = Modifier.fillMaxWidth()) {
        // Usage header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Today's screen time", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "${device.usedScreenTimeMinutes}m / ${device.dailyLimitMinutes}m",
                style = MaterialTheme.typography.titleMedium,
                color = if (device.remainingMinutes == 0) KGColors.danger else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Visual progress bar — signal colour only as the day runs out.
        val progress = device.usageProgress
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(KGShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(10.dp)
                    .clip(KGShapes.pill)
                    .background(
                        when {
                            progress >= 1f -> KGColors.danger
                            progress >= 0.8f -> KGColors.warning
                            else -> KGColors.success
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
                text = "${device.remainingMinutes} mins remaining today",
                style = MaterialTheme.typography.bodySmall,
                color = KGColors.inkMuted
            )
            Text(
                text = "${(progress * 100).toInt()}% used",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Slider — holds a local pending value during the drag and only commits to the
        // repository on release, avoiding a Firestore write batch per drag step and the
        // thumb snap-back during round-trips.
        var pendingLimit by remember(device.dailyLimitMinutes) { mutableStateOf<Float?>(null) }
        val sliderValue = pendingLimit ?: device.dailyLimitMinutes.toFloat()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Daily limit", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${sliderValue.toInt()} min (${sliderValue.toInt() / 60}h ${sliderValue.toInt() % 60}m)",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { pendingLimit = it },
            onValueChangeFinished = {
                pendingLimit?.let { onUpdateDailyLimit(it.toInt()) }
                pendingLimit = null
            },
            valueRange = 15f..360f,
            steps = 22
        )

        // Quick preset limit chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(45, 60, 90, 120, 150, 180).forEach { mins ->
                val isSelected = device.dailyLimitMinutes == mins
                Surface(
                    shape = KGShapes.pill,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { onUpdateDailyLimit(mins) }
                ) {
                    Text(
                        text = "${mins}m",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Age-Aware Recommendation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = KGShapes.cardSmall,
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "K-Guard pediatric recommendation",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
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
                KGSecondaryButton(
                    text = "Apply recommended limit",
                    onClick = onApplyAgeRecommendation,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun AppUsageBreakdownSection(device: ChildDevice) {
    KGSectionHeader(
        title = "Apps today",
        hint = "Where ${device.childName}'s screen time went."
    )

    KGCard(modifier = Modifier.fillMaxWidth()) {
        if (device.appUsages.isEmpty()) {
            Text(
                text = "No app usage recorded yet today.",
                style = MaterialTheme.typography.bodySmall,
                color = KGColors.inkMuted
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
                                    .clip(KGShapes.cardSmall)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app.appName.take(1),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (app.isCurrentlyActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        KGStatusTag("Active", KGColors.success)
                                    }
                                }
                                Text(
                                    app.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KGColors.inkMuted
                                )
                            }
                        }
                        Text(
                            text = "${app.usedMinutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(KGShapes.pill)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(appProgress)
                                .height(6.dp)
                                .clip(KGShapes.pill)
                                .background(MaterialTheme.colorScheme.primary)
                        )
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
    onToggleSchoolMode: (Boolean) -> Unit
) {
    KGSectionHeader(
        title = "Schedules",
        hint = "Automatic downtime and focus windows."
    )

    // Bedtime card
    KGCard(modifier = Modifier.fillMaxWidth()) {
        KGToggleRow(
            title = "Bedtime downtime",
            description = "${device.bedtimeStart} to ${device.bedtimeEnd}",
            checked = device.bedtimeEnabled,
            onCheckedChange = onToggleBedtime
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Automatically powers off the screen and locks all apps overnight so your child can get healthy sleep.",
            style = MaterialTheme.typography.bodySmall,
            color = KGColors.inkMuted
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // School Mode card
    KGCard(modifier = Modifier.fillMaxWidth()) {
        KGToggleRow(
            title = "School hours focus",
            description = "${device.schoolStart} to ${device.schoolEnd}",
            checked = device.schoolEnabled,
            onCheckedChange = onToggleSchoolMode
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Restricts gaming, video and social media apps during class hours while keeping approved educational apps available.",
            style = MaterialTheme.typography.bodySmall,
            color = KGColors.inkMuted
        )
    }
}

/** Signal colour for a command row, by action type. */
@Composable
private fun commandActionColor(action: RemoteAction): Color = when (action) {
    RemoteAction.SWITCH_OFF_LOCK -> KGColors.danger
    RemoteAction.UNLOCK_DEVICE -> KGColors.success
    RemoteAction.SOUND_ALARM -> KGColors.warning
    RemoteAction.ADD_BONUS_TIME -> KGColors.gradientViolet
    else -> KGColors.inkMuted
}

/** Signal colour for a command's delivery status. */
@Composable
private fun commandStatusColor(status: String): Color {
    val s = status.lowercase()
    return when {
        s.contains("fail") || s.contains("error") -> KGColors.danger
        s.contains("pend") || s.contains("send") || s.contains("queue") -> KGColors.warning
        else -> KGColors.success
    }
}

@Composable
fun RemoteCommandHistorySection(commandHistory: List<RemoteCommand>) {
    KGSectionHeader(
        title = "Command log",
        hint = "History of remote lockouts, screen time bonuses, and device actions."
    )

    KGCard(modifier = Modifier.fillMaxWidth()) {
        if (commandHistory.isEmpty()) {
            Text(
                text = "No remote commands sent yet.",
                style = MaterialTheme.typography.bodySmall,
                color = KGColors.inkMuted
            )
        } else {
            val dateFormat = SimpleDateFormat("HH:mm · MMM d", Locale.getDefault())
            commandHistory.forEach { cmd ->
                val actionTint = commandActionColor(cmd.action)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(KGShapes.circle)
                            .background(actionTint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (cmd.action) {
                                RemoteAction.SWITCH_OFF_LOCK -> Icons.Default.PowerSettingsNew
                                RemoteAction.UNLOCK_DEVICE -> Icons.Default.LockOpen
                                RemoteAction.SOUND_ALARM -> Icons.Default.VolumeUp
                                RemoteAction.ADD_BONUS_TIME -> Icons.Default.MoreTime
                                RemoteAction.SET_DAILY_LIMIT -> Icons.Default.Timer
                                else -> Icons.Default.History
                            },
                            contentDescription = null,
                            tint = actionTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = cmd.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = dateFormat.format(Date(cmd.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = KGColors.inkMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    KGStatusTag(text = cmd.status, color = commandStatusColor(cmd.status))
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
