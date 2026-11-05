package com.tele.k_guard.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.AppManagementViewModel

@Composable
fun AppManagementScreen(
    viewModel: AppManagementViewModel,
    activeAgeProfile: AgeProfile,
    onNavigateBack: () -> Unit
) {
    val managedApps by viewModel.managedApps.collectAsState()

    AppManagementContent(
        managedApps = managedApps,
        activeAgeProfile = activeAgeProfile,
        onApprove = { viewModel.approveApp(it) },
        onReject = { viewModel.rejectApp(it) },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun AppManagementContent(
    managedApps: List<AppAccessInfo>,
    activeAgeProfile: AgeProfile,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "App Management & Approvals",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Pillar 2: Control App Access",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "App Suitability & Approval Workflow",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Review child app install requests with K-Guard safety scores for ${activeAgeProfile.title}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(managedApps) { app ->
                    ManagedAppCard(
                        app = app,
                        onApprove = { onApprove(app.id) },
                        onReject = { onReject(app.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagedAppCard(
    app: AppAccessInfo,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
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
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
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
                            text = "${app.category} • Age ${app.minAgeRating}+",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val scoreColor = if (app.safetyScore > 75) Color(0xFF2E7D32) else if (app.safetyScore > 50) Color(0xFFE65100) else Color(0xFFC62828)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(scoreColor.copy(alpha = 0.15f))
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

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = app.safetyReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusTag(status = app.approvalStatus)

                Row {
                    if (app.approvalStatus != AppApprovalStatus.REJECTED) {
                        OutlinedButton(
                            onClick = onReject,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject")
                        }
                    }
                    if (app.approvalStatus != AppApprovalStatus.APPROVED) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onApprove,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve")
                        }
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

    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium
    )
}

@Preview(showBackground = true)
@Composable
fun AppManagementScreenPreview() {
    KGuardTheme {
        AppManagementContent(
            managedApps = listOf(
                AppAccessInfo("1", "Roblox", "com.roblox.client", "Games", 10, 68, AppApprovalStatus.PENDING_REVIEW, 30, "Contains unmoderated user chat."),
                AppAccessInfo("2", "Duolingo", "com.duolingo", "Education", 4, 95, AppApprovalStatus.APPROVED, 60, "Safe educational app with high safety rating.")
            ),
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            onApprove = {},
            onReject = {},
            onNavigateBack = {}
        )
    }
}

