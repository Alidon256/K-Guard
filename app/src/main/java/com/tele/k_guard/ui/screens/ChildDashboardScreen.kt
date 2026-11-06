package com.tele.k_guard.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.tele.k_guard.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme

import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.compose.AsyncImage
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.repository.ScreenTimeRepository
import com.tele.k_guard.screentime.LockEvaluator
import com.tele.k_guard.ui.components.ProtectionSetupCard
import com.tele.k_guard.data.repository.UserPreferencesRepository
import com.tele.k_guard.receiver.KGuardDeviceAdminReceiver
import com.tele.k_guard.service.KGuardCompanionService
import com.tele.k_guard.util.OsUsageTracker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue

@Composable
fun ChildDashboardScreen(
    childProfile: ChildProfile?,
    familyId: String? = null,
    childDevice: ChildDevice? = null,
    onNavigateToProfile: () -> Unit = {},
    onPairThisPhone: () -> Unit = {},
    onLaunchLiveAi: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val userPrefs = remember { UserPreferencesRepository(context.applicationContext) }
    var showRemoveDialog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val removeScope = androidx.compose.runtime.rememberCoroutineScope()
    // The companion service brings K-Guard to the front with this prompt when someone tries to remove it.
    val removePromptRequested by com.tele.k_guard.screentime.UninstallGuard.promptRequested.collectAsState()
    LaunchedEffect(removePromptRequested) {
        if (removePromptRequested) {
            showRemoveDialog = true
            com.tele.k_guard.screentime.UninstallGuard.promptRequested.value = false
        }
    }

    // What this phone last reported, and the rules the parent set (see KGuardCompanionService).
    val liveDevice by remember(familyId, childProfile?.id) {
        if (!familyId.isNullOrBlank() && childProfile != null) {
            ScreenTimeRepository.device(familyId, childProfile.id, childProfile.name)
        } else {
            flowOf(childDevice ?: ChildDevice())
        }
    }.collectAsState(initial = childDevice ?: ChildDevice())

    val usedMinutes = liveDevice.usedScreenTimeMinutes
    val limit = liveDevice.effectiveLimitMinutes
    val screenTimeSubtext = when {
        !liveDevice.hasReported -> "Measuring… finish the setup steps above if this doesn't update."
        liveDevice.isLocked -> liveDevice.lockReason ?: "This phone is locked."
        limit > 0 -> "${LockEvaluator.formatMinutes(usedMinutes)} used · ${LockEvaluator.formatMinutes(liveDevice.remainingMinutes)} left today"
        else -> "${LockEvaluator.formatMinutes(usedMinutes)} used today · no daily limit"
    }
    val scheduleText = buildList {
        if (liveDevice.bedtimeEnabled) add("Bedtime ${liveDevice.bedtimeStart}–${liveDevice.bedtimeEnd}")
        if (liveDevice.schoolEnabled) add("School hours ${liveDevice.schoolStart}–${liveDevice.schoolEnd} (games, video and social apps paused)")
    }.joinToString(separator = "\n").ifEmpty { "No bedtime or school hours set." }

    LaunchedEffect(familyId, childProfile?.id) {
        if (!familyId.isNullOrBlank()) {
            userPrefs.saveFamilyId(familyId)
        }
        if (!isPreview) {
            KGuardCompanionService.startCompanionService(context, familyId, childProfile?.id)
        }
    }

    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "K-Guard Child Protection",
                canNavigateBack = false,
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        if (!childProfile?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = childProfile.photoUrl,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Protected Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (childProfile != null) {
                        com.tele.k_guard.ui.components.ChildAvatar(childProfile.name, childProfile.photoUrl, 48.dp)
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.logo_inapp),
                            contentDescription = "K-Guard Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        val webOn by com.tele.k_guard.vpn.VpnState.running.collectAsState()
                        Text(
                            text = childProfile?.let { "${it.name}'s phone" } ?: "This phone isn't paired yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = when {
                                childProfile == null -> "Ask your parent for the 6-digit pairing code from their K-Guard app."
                                liveDevice.isLocked -> "Locked by your family's rules"
                                webOn -> "Your family's screen time and web protection rules are on"
                                else -> "Your family's screen time rules are on"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        if (childProfile == null) {
                            androidx.compose.material3.Button(
                                onClick = onPairThisPhone,
                                modifier = Modifier.padding(top = 8.dp)
                            ) { Text("Enter pairing code") }
                        }
                    }
                }
            }

            if (!isPreview) ProtectionSetupCard()

            val handleCompanionToggle = {
                KGuardCompanionService.startCompanionService(context, familyId, childProfile?.id)
                onLaunchLiveAi()
            }

            // K-Guard AI Safety Companion Hero Card
            Card(
                onClick = { handleCompanionToggle() },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF6366F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✨", fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "K-Guard Live",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "See what is protecting this phone right now",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Protection running",
                                color = Color(0xFF34D399),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { handleCompanionToggle() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Go live",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Screen Time Today Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E88E5).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen Time Today",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = screenTimeSubtext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // App Access & Firewall Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF8E24AA).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = Color(0xFF8E24AA)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Schedules",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = scheduleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Uninstall Protection Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔒 Supervised by your family",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your parent sets the screen time rules for this phone. If protection is switched off, or this phone stops checking in, your parent is told.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isPreview) {
                androidx.compose.material3.TextButton(
                    onClick = { showRemoveDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Remove K-Guard from this phone (parent PIN)") }
            }
        }
    }

    if (showRemoveDialog) {
        RemoveKGuardDialog(
            verify = { pin -> userPrefs.verifyParentPin(pin) },
            onDismiss = { showRemoveDialog = false },
            onVerified = {
                showRemoveDialog = false
                removeScope.launch { com.tele.k_guard.screentime.UninstallGuard.releaseAndUninstall(context) }
            }
        )
    }
}

/** Asks for the parent's PIN before K-Guard may be removed; see UninstallGuard. */
@Composable
private fun RemoveKGuardDialog(verify: suspend (String) -> Boolean, onDismiss: () -> Unit, onVerified: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pin by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var error by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var checking by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent PIN required") },
        text = {
            Column {
                Text(
                    "Removing K-Guard switches off this phone's protection, and your parent will be told. After the PIN, tap Uninstall on the next screen.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8); error = null },
                    label = { Text("Parent PIN") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { e -> { Text(e) } },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    )
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = pin.length >= 4 && !checking,
                onClick = {
                    val guard = com.tele.k_guard.screentime.UninstallGuard
                    val wait = guard.lockoutRemaining(context)
                    if (wait > 0) { error = "Too many tries. Wait ${(wait + 999) / 1000} seconds."; return@TextButton }
                    checking = true
                    scope.launch {
                        val ok = verify(pin)
                        checking = false
                        if (ok) {
                            guard.recordSuccess(context)
                            onVerified()
                        } else {
                            pin = ""
                            val left = guard.recordFailure(context)
                            error = if (left == 0) "Too many tries. Wait ${guard.LOCKOUT_MS / 1000} seconds."
                            else "That PIN isn't right. $left tr${if (left == 1) "y" else "ies"} left."
                        }
                    }
                }
            ) { Text("Continue") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Preview(showBackground = true)
@Composable
fun ChildDashboardPreview() {
    KGuardTheme {
        ChildDashboardScreen(childProfile = ChildProfile(name = "Ama"))
    }
}
