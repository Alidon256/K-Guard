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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.AppAccessInfo
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.ui.components.ChildAvatar
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
    onNavigateToAppDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val managedApps by viewModel.managedApps.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(familyId, activeChild?.id) {
        viewModel.setFamilyAndChild(familyId, null)
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
        onNavigateToAppDetail = onNavigateToAppDetail,
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
    onNavigateToAppDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<AppApprovalStatus?>(null) }
    var selectedChildId by remember { mutableStateOf<String?>(activeChild?.id) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var editingAppLimit by remember { mutableStateOf<AppAccessInfo?>(null) }

    // Counts follow the child chosen above, so they match the list underneath.
    val childApps = managedApps.filter { selectedChildId == null || it.childId == selectedChildId }
    val pendingCount = childApps.count { it.approvalStatus == AppApprovalStatus.PENDING_REVIEW }

    val filteredApps = managedApps.filter { app ->
        val statusMatches = selectedFilter == null || app.approvalStatus == selectedFilter
        val childMatches = selectedChildId == null || app.childId == selectedChildId
        val searchMatches = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        statusMatches && childMatches && searchMatches
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
                title = "App Management",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchActiveChange = { isSearchActive = it },
                actions = {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search Apps")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Children WhatsApp-style status row
            if (children.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    item {
                        ChildWhatsAppItem(
                            name = "All Children",
                            isSelected = selectedChildId == null,
                            onClick = { selectedChildId = null },
                            isAll = true
                        )
                    }
                    items(children) { c ->
                        ChildWhatsAppItem(
                            name = c.name,
                            photoUrl = c.photoUrl,
                            isSelected = selectedChildId == c.id,
                            onClick = { selectedChildId = c.id }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // WhatsApp Style Filter Tabs Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                item {
                    WhatsAppFilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = "All (${childApps.size})"
                    )
                }
                item {
                    WhatsAppFilterChip(
                        selected = selectedFilter == AppApprovalStatus.PENDING_REVIEW,
                        onClick = { selectedFilter = AppApprovalStatus.PENDING_REVIEW },
                        label = "Needs Approval ($pendingCount)"
                    )
                }
                item {
                    WhatsAppFilterChip(
                        selected = selectedFilter == AppApprovalStatus.APPROVED,
                        onClick = { selectedFilter = AppApprovalStatus.APPROVED },
                        label = "Approved (${childApps.count { it.approvalStatus == AppApprovalStatus.APPROVED }})"
                    )
                }
                item {
                    WhatsAppFilterChip(
                        selected = selectedFilter == AppApprovalStatus.REJECTED,
                        onClick = { selectedFilter = AppApprovalStatus.REJECTED },
                        label = "Rejected (${childApps.count { it.approvalStatus == AppApprovalStatus.REJECTED }})"
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
                            text = if (searchQuery.isNotEmpty())
                                "No apps found matching '$searchQuery'"
                            else if (selectedFilter == AppApprovalStatus.PENDING_REVIEW)
                                "No pending app install requests."
                            else
                                "No applications found.",
                            style = MaterialTheme.typography.titleMedium,
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
                            onEditLimit = { editingAppLimit = app },
                            onNavigateToDetail = { onNavigateToAppDetail(app.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }

    // Dialog for editing daily limit
    if (editingAppLimit != null) {
        val app = editingAppLimit!!
        var limitSlider by remember { mutableFloatStateOf(app.dailyLimitMinutes.toFloat()) }
        AlertDialog(
            onDismissRequest = { editingAppLimit = null },
            title = { Text("Set Daily Limit for ${app.appName}") },
            text = {
                Column {
                    Text("Choose maximum daily screen time for this application:")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("${limitSlider.toInt()} minutes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Slider(
                        value = limitSlider,
                        onValueChange = { limitSlider = it },
                        valueRange = 10f..180f,
                        steps = 17
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateLimit(app.id, limitSlider.toInt())
                    editingAppLimit = null
                }) {
                    Text("Save Limit")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAppLimit = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChildWhatsAppItem(
    name: String,
    photoUrl: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    isAll: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .width(64.dp)
    ) {
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
            if (isAll) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = "All Children",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                ChildAvatar(
                    name = name,
                    photoUrl = photoUrl,
                    size = 52.dp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

@Composable
private fun ManagedAppCard(
    app: AppAccessInfo,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEditLimit: () -> Unit,
    onNavigateToDetail: () -> Unit
) {
    val isPending = app.approvalStatus == AppApprovalStatus.PENDING_REVIEW
    val cardBorder = if (isPending) Modifier.border(1.5.dp, Color(0xFFE65100), RoundedCornerShape(16.dp)) else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToDetail() }
            .then(cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPending) 4.dp else 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            // Theme surface in both modes (a fixed light colour made the text unreadable in dark mode);
            // the orange border marks a pending request.
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Avatar Initial + Name + Category + KidSafe Block Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    val initialStr = app.appName.take(3).uppercase()
                    val avatarColor = when (app.appName.firstOrNull()?.uppercaseChar() ?: 'A') {
                        in 'A'..'E' -> Color(0xFFEAB308)
                        in 'F'..'J' -> Color(0xFFD946EF)
                        in 'K'..'O' -> Color(0xFF14B8A6)
                        in 'P'..'T' -> Color(0xFF3B82F6)
                        else -> Color(0xFF22C55E)
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(avatarColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initialStr,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
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
                            text = if (app.approvalStatus == AppApprovalStatus.REJECTED) "Blocked by parents" else "${app.category} • Age ${app.minAgeRating}+" +
                                if (app.childName.isNotBlank()) " • ${app.childName}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (app.approvalStatus == AppApprovalStatus.REJECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Block Switch matching KidSafe AppsFragment
                Switch(
                    checked = app.approvalStatus == AppApprovalStatus.REJECTED,
                    onCheckedChange = { isBlocked ->
                        if (isBlocked) {
                            onReject()
                        } else {
                            onApprove()
                        }
                    }
                )
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
                        text = when {
                            !app.installed -> "Uninstalled from the phone"
                            app.dailyLimitMinutes > 0 -> "Daily limit: ${app.dailyLimitMinutes}m"
                            else -> "No app limit"
                        },
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
                        Text("Reject", maxLines = 1, softWrap = false)
                    }
                }

                if (app.approvalStatus != AppApprovalStatus.APPROVED) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
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

                Text("Quick Presets:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { (preset, age) ->
                        FilterChip(
                            selected = appName == preset.first,
                            onClick = {
                                appName = preset.first
                                packageName = preset.second
                                category = preset.third
                                minAgeRating = age
                            },
                            label = { Text(preset.first) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                    onSubmit(selectedChild, appName, packageName, category, minAgeRating, dailyLimit)
                },
                enabled = appName.isNotBlank() && packageName.isNotBlank()
            ) {
                Text("Submit Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AppManagementScreenPreview() {
    KGuardTheme {
        AppManagementContent(
            managedApps = listOf(
                AppAccessInfo("1", "Roblox", "com.roblox.client", "Gaming & Community", 10, 68, AppApprovalStatus.PENDING_REVIEW, 30, "Contains unmoderated user chat."),
                AppAccessInfo("2", "Duolingo", "com.duolingo", "Education", 4, 95, AppApprovalStatus.APPROVED, 60, "Safe educational app with high safety rating.")
            ),
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            children = listOf(
                ChildProfile(id = "1", name = "Mugumya Ali"),
                ChildProfile(id = "2", name = "Aron"),
                ChildProfile(id = "3", name = "Joseph")
            ),
            onApprove = {},
            onReject = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChildrenRowPreview() {
    KGuardTheme {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            item {
                ChildWhatsAppItem(
                    name = "All Children",
                    isSelected = true,
                    onClick = {},
                    isAll = true
                )
            }
            items(
                listOf(
                    ChildProfile(id = "1", name = "Mugumya Ali"),
                    ChildProfile(id = "2", name = "Aron"),
                    ChildProfile(id = "3", name = "Joseph")
                )
            ) { c ->
                ChildWhatsAppItem(
                    name = c.name,
                    photoUrl = c.photoUrl,
                    isSelected = false,
                    onClick = {}
                )
            }
        }
    }
}
