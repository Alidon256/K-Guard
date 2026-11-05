package com.tele.k_guard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme

@Composable
fun HomeScreen(
    activeAgeProfile: AgeProfile,
    onNavigateToTextModeration: () -> Unit,
    onNavigateToImageModeration: () -> Unit,
    onNavigateToAgeProfile: () -> Unit,
    onNavigateToScreenTime: () -> Unit,
    onNavigateToAppManagement: () -> Unit,
    onNavigateToFlaggedContent: () -> Unit,
    activeChildName: String? = null,
    openAlertCount: Int = 0,
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToWebProtection: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "K-Guard Dashboard",
                canNavigateBack = false
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAgeProfile() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeChildName != null) "Protecting $activeChildName" else "No child profile yet — tap to add one",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${activeAgeProfile.title} (${activeAgeProfile.ageRange})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Change Profile",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

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
                icon = Icons.Default.Image,
                badgeColor = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToImageModeration
            )

            Spacer(modifier = Modifier.height(8.dp))

            DashboardToolCard(
                title = if (openAlertCount > 0) "Alerts & Review ($openAlertCount open)" else "Alerts & Review",
                description = "Review flagged items, then allow, keep blocked or dismiss.",
                icon = Icons.Default.NotificationsActive,
                badgeColor = Color(0xFFD81B60),
                onClick = onNavigateToFlaggedContent
            )

            Spacer(modifier = Modifier.height(8.dp))

            DashboardToolCard(
                title = "Privacy & Notifications",
                description = "Choose which alerts reach you, quiet hours and what K-Guard keeps.",
                icon = Icons.Default.Lock,
                badgeColor = Color(0xFF546E7A),
                onClick = onNavigateToPrivacy
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "K-Guard combines Screen Time, App Control, and AI Content Moderation for complete digital family safety.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    KGuardTheme {
        HomeScreen(
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            activeChildName = "Alex",
            openAlertCount = 2,
            onNavigateToTextModeration = {},
            onNavigateToImageModeration = {},
            onNavigateToAgeProfile = {},
            onNavigateToScreenTime = {},
            onNavigateToAppManagement = {},
            onNavigateToFlaggedContent = {},
            onNavigateToPrivacy = {},
            onNavigateToWebProtection = {}
        )
    }
}

