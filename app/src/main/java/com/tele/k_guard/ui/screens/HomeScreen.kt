package com.tele.k_guard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.ui.components.KGCard
import com.tele.k_guard.ui.components.KGSectionHeader
import com.tele.k_guard.ui.components.KGStatusTag
import com.tele.k_guard.ui.components.KGuardLogo
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGColors
import com.tele.k_guard.ui.theme.KGShapes
import com.tele.k_guard.ui.theme.KGuardTheme
import java.util.Calendar

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
    pendingApprovalCount: Int = 0,
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToWebProtection: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToChildren: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "K-Guard Dashboard",
                canNavigateBack = false,
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Greeting block: display-tier headline + active child context. Taps to the
            // age-profile screen, preserving the previous hero card's navigation.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAgeProfile() }
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting(),
                        style = MaterialTheme.typography.labelMedium,
                        color = KGColors.inkMuted
                    )
                    Text(
                        text = activeChildName ?: "No child profile yet",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeAgeProfile.title} (${activeAgeProfile.ageRange}) · Tap to change",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KGColors.inkMuted
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Change Profile",
                    tint = KGColors.inkMuted
                )
            }

            KGSectionHeader(
                title = "Dashboard",
                hint = if (activeChildName != null) {
                    "Everything watching over $activeChildName in one place"
                } else {
                    "Every family-safety tool in one place"
                }
            )

            // 2-column tool grid: Children first (previously unreachable from here).
            val cards = listOf(
                DashboardCardData(
                    title = "Children",
                    description = "Manage child profiles & pairing.",
                    icon = Icons.Default.FamilyRestroom,
                    tint = KGColors.gradientOrange,
                    onClick = onNavigateToChildren
                ),
                DashboardCardData(
                    title = "Screen Time",
                    description = "Live telemetry, app breakdown, and remote switch-off.",
                    icon = Icons.Default.Timer,
                    tint = KGColors.accent,
                    onClick = onNavigateToScreenTime
                ),
                DashboardCardData(
                    title = "App Control",
                    description = if (pendingApprovalCount > 0) {
                        "$pendingApprovalCount approval request(s) waiting."
                    } else {
                        "Review requests, safety scores, and app limits."
                    },
                    icon = Icons.Default.Apps,
                    tint = KGColors.gradientViolet,
                    onClick = onNavigateToAppManagement
                ),
                DashboardCardData(
                    title = "Web Protection",
                    description = "Block harmful websites in every browser and app.",
                    icon = Icons.Default.Public,
                    tint = KGColors.success,
                    onClick = onNavigateToWebProtection
                ),
                DashboardCardData(
                    title = "Review Queue",
                    description = "Audit flagged content and record your decisions.",
                    icon = Icons.Default.NotificationsActive,
                    tint = if (openAlertCount > 0) KGColors.danger else KGColors.warning,
                    openAlertCount = openAlertCount,
                    onClick = onNavigateToFlaggedContent
                ),
                DashboardCardData(
                    title = "Moderate Text",
                    description = "Scan messages, comments, and web terms for age safety.",
                    icon = Icons.Default.Description,
                    tint = KGColors.gradientMagenta,
                    onClick = onNavigateToTextModeration
                ),
                DashboardCardData(
                    title = "Moderate Images",
                    description = "Automatic warnings, blur previews, or blocks on photos.",
                    icon = Icons.Default.Image,
                    tint = KGColors.gradientCoral,
                    onClick = onNavigateToImageModeration
                ),
                DashboardCardData(
                    title = "Privacy",
                    description = "Alert preferences, quiet hours, and data retention.",
                    icon = Icons.Default.Lock,
                    tint = KGColors.inkMuted,
                    onClick = onNavigateToPrivacy
                )
            )
            cards.chunked(2).forEach { rowCards ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowCards.forEach { card ->
                        DashboardToolCard(
                            data = card,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // The single gradient spotlight allowed on this screen: a slim brand banner.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(KGColors.gradientOrange, KGColors.gradientCoral)
                        ),
                        shape = KGShapes.card
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KGuardLogo(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "K-Guard combines Screen Time, App Control, and AI Content Moderation for complete digital family safety.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}

private data class DashboardCardData(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color,
    val openAlertCount: Int = 0,
    val onClick: () -> Unit
)

/** One dashboard tool tile: 40dp tinted icon circle, title, muted description, optional alert tag. */
@Composable
private fun DashboardToolCard(
    data: DashboardCardData,
    modifier: Modifier = Modifier
) {
    KGCard(modifier = modifier, onClick = data.onClick) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, KGShapes.circle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = data.icon,
                contentDescription = null,
                tint = data.tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = data.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = data.description,
            style = MaterialTheme.typography.bodySmall,
            color = KGColors.inkMuted
        )
        if (data.openAlertCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            KGStatusTag(
                text = "${data.openAlertCount} open",
                color = KGColors.danger
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
            activeChildName = "Ama",
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
