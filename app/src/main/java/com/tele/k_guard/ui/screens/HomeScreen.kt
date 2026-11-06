package com.tele.k_guard.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
import com.tele.k_guard.R
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.repository.ScreenTimeRepository
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@Composable
fun HomeScreen(
    activeAgeProfile: AgeProfile,
    onNavigateToTextModeration: () -> Unit,
    onNavigateToImageModeration: () -> Unit,
    onNavigateToAgeProfile: () -> Unit,
    onNavigateToScreenTime: () -> Unit,
    onNavigateToAppManagement: () -> Unit,
    onNavigateToFlaggedContent: () -> Unit,
    user: FirebaseUser? = null,
    activeChildName: String? = null,
    activeChildPhotoUrl: String? = null,
    children: List<ChildProfile> = emptyList(),
    familyId: String? = null,
    selectedChild: ChildProfile? = null,
    onSelectChild: (ChildProfile) -> Unit = {},
    openAlertCount: Int = 0,
    pendingApprovalCount: Int = 0,
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToWebProtection: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onAddChild: () -> Unit = onNavigateToAgeProfile,
    onOpenAssistant: () -> Unit = {}
) {
    var quickActionsOpen by com.tele.k_guard.ui.components.rememberQuickActionsState()
    Scaffold(
        floatingActionButton = {
            com.tele.k_guard.ui.components.QuickActionsFab(
                actions = com.tele.k_guard.ui.components.parentQuickActions(
                    pendingApprovals = pendingApprovalCount,
                    onWebProtection = onNavigateToWebProtection,
                    onAppApprovals = onNavigateToAppManagement,
                    onDigitalHabits = onNavigateToScreenTime,
                    onAddChild = onAddChild,
                    onAskAssistant = onOpenAssistant
                ),
                expanded = quickActionsOpen,
                onExpandedChange = { quickActionsOpen = it }
            )
        },
        topBar = {
            KGuardTopAppBar(
                title = "K-Guard Dashboard",
                canNavigateBack = false,
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        if (!user?.photoUrl?.toString().isNullOrBlank()) {
                            AsyncImage(
                                model = user.photoUrl,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
      Box(Modifier.fillMaxSize().padding(innerPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ScreenTimeHeroCard(
                children = children,
                familyId = familyId,
                selectedChild = selectedChild,
                onSelectChild = onSelectChild,
                onNavigateToScreenTime = onNavigateToScreenTime,
                onNavigateToAppManagement = onNavigateToAppManagement,
                onNavigateToAgeProfile = onNavigateToAgeProfile
            )

            Spacer(modifier = Modifier.height(16.dp))

            AssistantEntryCard(childName = activeChildName, onClick = onOpenAssistant)

            Spacer(modifier = Modifier.height(20.dp))

            PillarHeader(pillar = "MANAGE", title = "Digital Habits")
            Spacer(modifier = Modifier.height(8.dp))
            DashboardToolCard(
                title = "Screen Time & Device Controls",
                description = "Live phone telemetry, app breakdown, and remote screen switch-off.",
                icon = Icons.Default.Timer,
                badgeColor = Color(0xFF1E88E5),
                onClick = onNavigateToScreenTime
            )

            Spacer(modifier = Modifier.height(16.dp))

            PillarHeader(pillar = "CONTROL", title = "App Access")
            Spacer(modifier = Modifier.height(8.dp))
            DashboardToolCard(
                title = "App Approvals & Safety Ratings",
                description = "Review app requests, safety scores, and app-specific limits.",
                icon = Icons.Default.Apps,
                badgeColor = Color(0xFF8E24AA),
                pendingCount = pendingApprovalCount,
                onClick = onNavigateToAppManagement
            )

            Spacer(modifier = Modifier.height(16.dp))

            PillarHeader(pillar = "PROTECT (FLAGSHIP)", title = "AI Online Content Safety")
            Spacer(modifier = Modifier.height(8.dp))
            DashboardToolCard(
                title = "AI Text Content Moderation",
                description = "Scan text messages, comments, and web terms for age safety.",
                icon = Icons.Default.Description,
                badgeColor = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToTextModeration
            )

            Spacer(modifier = Modifier.height(8.dp))

            DashboardToolCard(
                title = "Web Protection (VPN)",
                description = "Block harmful websites in every browser and app on this phone.",
                icon = Icons.Default.Shield,
                badgeColor = Color(0xFF00897B),
                onClick = onNavigateToWebProtection
            )

            Spacer(modifier = Modifier.height(8.dp))

            DashboardToolCard(
                title = "AI Image Content Moderation",
                description = "Scan photos with automatic warnings, blur previews, or blocks.",
                icon = Icons.Default.ImageIcon,
                badgeColor = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToImageModeration
            )

            Spacer(modifier = Modifier.height(16.dp))

            PillarHeader(pillar = "PARENT OVERSIGHT", title = "Alerts & Privacy")
            Spacer(modifier = Modifier.height(8.dp))
            DashboardToolCard(
                title = "Alerts & Parent Review",
                description = if (openAlertCount > 0) "$openAlertCount event(s) waiting for your review." else "Audit flagged content and record allow/keep-blocked actions.",
                icon = Icons.Default.NotificationsActive,
                badgeColor = if (openAlertCount > 0) Color(0xFFC62828) else Color(0xFF5E35B1),
                pendingCount = openAlertCount,
                onClick = onNavigateToFlaggedContent
            )

            Spacer(modifier = Modifier.height(8.dp))

            DashboardToolCard(
                title = "Privacy & Notification Settings",
                description = "Choose which alerts notify you, quiet hours, and data retention.",
                icon = Icons.Default.Lock,
                badgeColor = Color(0xFF37474F),
                onClick = onNavigateToPrivacy
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_inapp),
                        contentDescription = "K-Guard Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "K-Guard combines Screen Time, App Control, and AI Content Moderation for complete digital family safety.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Room for the + button so the last card isn't hidden behind it.
            Spacer(modifier = Modifier.height(88.dp))
        }
        com.tele.k_guard.ui.components.QuickActionsScrim(quickActionsOpen) { quickActionsOpen = false }
      }
    }
}

/** Entry to the K-Guard Assistant, which answers questions about features and supporting a child online. */
@Composable
private fun AssistantEntryCard(childName: String?, onClick: () -> Unit) {
    val first = childName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF0EA5E9))))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Ask K-Guard Assistant", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (first != null) "How a feature works, or how to support $first online" else "How a feature works, or how to support your child online",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun PillarHeader(pillar: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = pillar,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DashboardToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    badgeColor: Color,
    pendingCount: Int = 0,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(badgeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (pendingCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE65100))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$pendingCount",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatHoursAndMins(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun lastSyncedText(device: ChildDevice): String {
    if (!device.hasReported) return "Not connected yet"
    val mins = device.lastSeenMinutesAgo
    return when {
        mins < 1 -> "Synced just now"
        mins < 60 -> "Synced $mins min ago"
        else -> "Synced ${mins / 60}h ago"
    }
}

@Composable
private fun ScreenTimeHeroCard(
    children: List<ChildProfile>,
    familyId: String?,
    selectedChild: ChildProfile?,
    onSelectChild: (ChildProfile) -> Unit,
    onNavigateToScreenTime: () -> Unit,
    onNavigateToAppManagement: () -> Unit,
    onNavigateToAgeProfile: () -> Unit
) {
    var expandedSpinner by remember { mutableStateOf(false) }

    // Observe live devices for all children
    val liveDevices = remember(familyId, children) {
        if (!familyId.isNullOrBlank() && children.isNotEmpty()) {
            combine(
                children.map { c -> ScreenTimeRepository.device(familyId, c.id, c.name) }
            ) { devicesArray ->
                devicesArray.toList()
            }
        } else {
            flowOf(emptyList<ChildDevice>())
        }
    }.collectAsState(initial = emptyList()).value

    // Automatically default to an online child if available
    LaunchedEffect(liveDevices, children) {
        val onlineDevice = liveDevices.firstOrNull { it.isOnline }
        if (onlineDevice != null) {
            val onlineChild = children.find { it.id == onlineDevice.childId }
            if (onlineChild != null && selectedChild?.id != onlineChild.id) {
                onSelectChild(onlineChild)
            }
        }
    }

    val currentChild = selectedChild ?: children.firstOrNull()
    val liveDevice = liveDevices.find { it.childId == currentChild?.id } ?: ChildDevice()

    val usedMinutes = liveDevice.usedScreenTimeMinutes
    val limitMinutes = liveDevice.effectiveLimitMinutes
    val remainingMinutes = liveDevice.remainingMinutes

    val h = usedMinutes / 60
    val m = usedMinutes % 60

    val remainingText = if (limitMinutes > 0) "${formatHoursAndMins(remainingMinutes)} left" else "unlimited"
    val limitText = if (limitMinutes > 0) "of ${formatHoursAndMins(limitMinutes)} daily" else "no daily limit"
    val progressFraction = if (limitMinutes > 0) (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f) else 0f

    val statusMessage = when {
        liveDevice.isLocked -> liveDevice.lockReason ?: "Daily screen time limit reached."
        !liveDevice.hasReported -> "Install K-Guard on this phone to start tracking."
        limitMinutes > 0 && remainingMinutes > 30 -> "There's still room for a little downtime."
        limitMinutes > 0 && remainingMinutes in 1..30 -> "Screen time is almost up for today."
        limitMinutes > 0 && remainingMinutes <= 0 -> "Daily screen time limit reached."
        else -> "No daily screen time limit set."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Child Avatar + Name Spinner + Sync Subtitle + Status Pill Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    com.tele.k_guard.ui.components.ChildAvatar(
                        name = currentChild?.name ?: "Child",
                        photoUrl = currentChild?.photoUrl,
                        size = 44.dp,
                        modifier = Modifier.clickable { onNavigateToAgeProfile() }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { expandedSpinner = true }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentChild?.name ?: "Child",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                run {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select child",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = expandedSpinner,
                                onDismissRequest = { expandedSpinner = false }
                            ) {
                                children.forEach { child ->
                                    val isChildOnline = liveDevices.find { it.childId == child.id }?.isOnline == true
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                com.tele.k_guard.ui.components.ChildAvatar(
                                                    name = child.name,
                                                    photoUrl = child.photoUrl,
                                                    size = 28.dp
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = child.name,
                                                    fontWeight = if (child.id == currentChild?.id) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (isChildOnline) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF16A34A))
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            onSelectChild(child)
                                            expandedSpinner = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Add or manage children", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        expandedSpinner = false
                                        onNavigateToAgeProfile()
                                    }
                                )
                            }
                        }

                        Text(
                            text = if (liveDevice.deviceName.isNotBlank()) "${liveDevice.deviceName} · ${lastSyncedText(liveDevice)}"
                            else if (liveDevice.deviceModel.isNotBlank()) "${liveDevice.deviceModel} · ${lastSyncedText(liveDevice)}"
                            else lastSyncedText(liveDevice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge (Connected / Offline / Not Paired)
                val (badgeBg, badgeTextColor, badgeLabel) = when {
                    !liveDevice.hasReported -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "Not Paired")
                    liveDevice.isOnline -> Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), "Connected")
                    else -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "Offline")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inner Card Box for Screen Time Today Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header: "Screen time today" + Sun icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Screen time today",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Big Time Numbers: e.g. 1h 45m
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (h > 0) {
                            Text(
                                text = "$h",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 42.sp
                            )
                            Text(
                                text = "h ",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = "$m",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 42.sp
                        )
                        Text(
                            text = "m",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Rounded Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    if (liveDevice.isLocked) Color(0xFFDC2626)
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Subtitle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = limitText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToScreenTime,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("View analytics", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onNavigateToAppManagement,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Manage limits", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    KGuardTheme {
        HomeScreen(
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            activeChildName = "Ama",
            children = listOf(ChildProfile(id = "1", name = "Maya")),
            openAlertCount = 2,
            pendingApprovalCount = 1,
            onNavigateToTextModeration = {},
            onNavigateToImageModeration = {},
            onNavigateToAgeProfile = {},
            onNavigateToScreenTime = {},
            onNavigateToAppManagement = {},
            onNavigateToFlaggedContent = {}
        )
    }
}
