package com.tele.k_guard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.AppAccessInfo
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.AppManagementViewModel

@Composable
fun AppManagementScreen(
    viewModel: AppManagementViewModel,
    activeAgeProfile: AgeProfile,
    activeChild: ChildProfile? = null,
    children: List<ChildProfile> = emptyList(),
    familyId: String? = null,
    onNavigateBack: () -> Unit
) {
    val managedApps by viewModel.managedApps.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(familyId, activeChild?.id) {
        viewModel.setFamilyAndChild(familyId, activeChild?.id)
    }

    AppManagementContent(
        managedApps = managedApps,
        activeAgeProfile = activeAgeProfile,
        activeChild = activeChild,
        children = children,
        busy = busy,
        error = error,
        message = message,
        onClearMessage = { viewModel.clearMessage() },
        onClearError = { viewModel.clearError() },
        onApprove = { appId -> viewModel.approveApp(familyId, appId) },
        onReject = { appId -> viewModel.rejectApp(familyId, appId) },
        onUpdateLimit = { appId, limit -> viewModel.updateAppLimit(familyId, appId, limit) },
        onRequestInstall = { childId, name, pkg, cat, minAge, limit ->
            viewModel.requestAppInstall(familyId, childId, name, pkg, cat, minAge, limit)
        },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun AppManagementContent(
    managedApps: List<AppAccessInfo>,
    activeAgeProfile: AgeProfile,
    activeChild: ChildProfile? = null,
    children: List<ChildProfile> = emptyList(),
    busy: Boolean = false,
    error: String? = null,
    message: String? = null,
    onClearMessage: () -> Unit = {},
    onClearError: () -> Unit = {},
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onUpdateLimit: (String, Int) -> Unit = { _, _ -> },
    onRequestInstall: (String, String, String, String, Int, Int) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateBack: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<AppApprovalStatus?>(null) }
    var selectedChildId by remember { mutableStateOf<String?>(activeChild?.id) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var editingAppLimit by remember { mutableStateOf<AppAccessInfo?>(null) }

    val pendingCount = managedApps.count { it.approvalStatus == AppApprovalStatus.PENDING_REVIEW }

    val filteredApps = managedApps.filter { app ->
        val statusMatches = selectedFilter == null || app.approvalStatus == selectedFilter
        val childMatches = selectedChildId == null || app.childId.isEmpty() || app.childId == selectedChildId
        statusMatches && childMatches
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onClearMessage()
        }
    }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            onClearError()
        }
    }

    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "App Management & Approvals",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showRequestDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Request / Install App") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle & Pillar Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PILLAR 2: APP CONTROLS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Install Approvals & Suitability",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (pendingCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$pendingCount Pending",
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Child filter row if multiple children exist
            if (children.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedChildId == null,
                            onClick = { selectedChildId = null },
                            label = { Text("All Children") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    items(children) { c ->
                        FilterChip(
                            selected = selectedChildId == c.id,
                            onClick = { selectedChildId = c.id },
                            label = { Text(c.name) }
                        )
                    }
                }
            }

            // Approval Status Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All (${managedApps.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == AppApprovalStatus.PENDING_REVIEW,
                        onClick = { selectedFilter = AppApprovalStatus.PENDING_REVIEW },
                        label = { Text("Needs Approval ($pendingCount)") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == AppApprovalStatus.APPROVED,
                        onClick = { selectedFilter = AppApprovalStatus.APPROVED },
                        label = { Text("Approved (${managedApps.count { it.approvalStatus == AppApprovalStatus.APPROVED }})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == AppApprovalStatus.REJECTED,
                        onClick = { selectedFilter = AppApprovalStatus.REJECTED },
                        label = { Text("Rejected (${managedApps.count { it.approvalStatus == AppApprovalStatus.REJECTED }})") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedFilter == AppApprovalStatus.PENDING_REVIEW)
                                "No pending app install requests."
                            else
                                "No applications found.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Request / Install App' below to simulate a kid installing an application.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredApps, key = { it.id.ifEmpty { it.packageName } }) { app ->
                        ManagedAppCard(
                            app = app,
                            onApprove = { onApprove(app.id) },
                            onReject = { onReject(app.id) },
                            onEditLimit = { editingAppLimit = app }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Dialog for requesting an app
    if (showRequestDialog) {
        RequestAppDialog(
            children = children,
            activeChildId = selectedChildId ?: activeChild?.id ?: children.firstOrNull()?.id ?: "",
            onDismiss = { showRequestDialog = false },
            onSubmit = { childId, name, pkg, cat, minAge, limit ->
                onRequestInstall(childId, name, pkg, cat, minAge, limit)
                showRequestDialog = false
            }
        )
    }

    // Dialog for editing daily limit
    editingAppLimit?.let { app ->
        AppLimitDialog(
            app = app,
            onDismiss = { editingAppLimit = null },
            onSave = { newLimit ->
                onUpdateLimit(app.id, newLimit)
                editingAppLimit = null
            }
        )
    }
}

@Composable
private fun ManagedAppCard(
    app: AppAccessInfo,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEditLimit: () -> Unit
) {
    val isPending = app.approvalStatus == AppApprovalStatus.PENDING_REVIEW
    val cardBorder = if (isPending) Modifier.border(1.5.dp, Color(0xFFE65100), RoundedCornerShape(16.dp)) else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPending) 4.dp else 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPending) Color(0xFFFFFDF9) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Icon + Name + Category + Safety Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPending) Color(0xFFFFE0B2) else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = if (isPending) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${app.category} • Age ${app.minAgeRating}+" +
                                if (app.childName.isNotBlank()) " • For ${app.childName}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val scoreColor = if (app.safetyScore > 75) Color(0xFF2E7D32) else if (app.safetyScore > 50) Color(0xFFE65100) else Color(0xFFC62828)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(scoreColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Safety: ${app.safetyScore}%",
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Safety reason / suitability details
            if (app.safetyReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = app.safetyReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Daily limit row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onEditLimit() }
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Daily limit: ${app.dailyLimitMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                StatusTag(status = app.approvalStatus)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (app.approvalStatus != AppApprovalStatus.REJECTED) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(if (isPending) "Decline" else "Revoke", maxLines = 1, softWrap = false)
                    }
                }

                if (app.approvalStatus != AppApprovalStatus.APPROVED) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Approve", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTag(status: AppApprovalStatus) {
    val (color, text) = when (status) {
        AppApprovalStatus.APPROVED -> Color(0xFF2E7D32) to "APPROVED"
        AppApprovalStatus.PENDING_REVIEW -> Color(0xFFE65100) to "PENDING APPROVAL"
        AppApprovalStatus.REJECTED -> Color(0xFFC62828) to "REJECTED"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun RequestAppDialog(
    children: List<ChildProfile>,
    activeChildId: String,
    onDismiss: () -> Unit,
    onSubmit: (childId: String, name: String, pkg: String, cat: String, minAge: Int, limit: Int) -> Unit
) {
    var selectedChild by remember { mutableStateOf(activeChildId.ifEmpty { children.firstOrNull()?.id ?: "" }) }
    var appName by remember { mutableStateOf("Roblox") }
    var packageName by remember { mutableStateOf("com.roblox.client") }
    var category by remember { mutableStateOf("Gaming & Community") }
    var minAgeRating by remember { mutableIntStateOf(10) }
    var dailyLimit by remember { mutableIntStateOf(45) }

    val presets = listOf(
        Triple("Roblox", "com.roblox.client", "Gaming & Community") to 10,
        Triple("Minecraft", "com.mojang.minecraftpe", "Gaming & Community") to 7,
        Triple("TikTok", "com.zhiliaoapp.musically", "Social Media") to 13,
        Triple("YouTube Kids", "com.google.android.apps.youtube.kids", "Video & Entertainment") to 3,
        Triple("Duolingo ABC", "com.duolingo.abc", "Education") to 3
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request / Install New App", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Simulates a kid attempting to install an application. The request is submitted to Firebase and triggers real-time parent notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Presets
                Text("Quick presets:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(presets) { (info, age) ->
                        FilterChip(
                            selected = appName == info.first,
                            onClick = {
                                appName = info.first
                                packageName = info.second
                                category = info.third
                                minAgeRating = age
                            },
                            label = { Text(info.first) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = minAgeRating.toString(),
                        onValueChange = { minAgeRating = it.toIntOrNull() ?: 3 },
                        label = { Text("Min Age") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Daily Limit: ${dailyLimit} minutes", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = dailyLimit.toFloat(),
                    onValueChange = { dailyLimit = it.toInt() },
                    valueRange = 15f..180f,
                    steps = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (appName.isNotBlank() && packageName.isNotBlank()) {
                        onSubmit(selectedChild, appName, packageName, category, minAgeRating, dailyLimit)
                    }
                }
            ) {
                Text("Submit Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AppLimitDialog(
    app: AppAccessInfo,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var limit by remember { mutableFloatStateOf(app.dailyLimitMinutes.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Limit for ${app.appName}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Configure maximum daily screen time allowed for this application.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${limit.toInt()} minutes per day",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = limit,
                    onValueChange = { limit = it },
                    valueRange = 15f..240f,
                    steps = 14
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(limit.toInt()) }) {
                Text("Save Limit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AppManagementScreenPreview() {
    KGuardTheme {
        AppManagementContent(
            managedApps = listOf(
                AppAccessInfo("1", "Roblox", "com.roblox.client", "Gaming", 10, 68, AppApprovalStatus.PENDING_REVIEW, 30, "Contains unmoderated user chat.", "c1", "Ama"),
                AppAccessInfo("2", "Duolingo", "com.duolingo", "Education", 4, 95, AppApprovalStatus.APPROVED, 60, "Safe educational app with high safety rating.", "c1", "Ama")
            ),
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            onApprove = {},
            onReject = {},
            onNavigateBack = {}
        )
    }
}
