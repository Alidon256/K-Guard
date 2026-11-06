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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.protection.DeviceProtection
import com.tele.k_guard.ui.components.KGCard
import com.tele.k_guard.ui.components.KGSectionHeader
import com.tele.k_guard.ui.components.KGuardLogo
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGColors
import com.tele.k_guard.ui.theme.KGShapes
import com.tele.k_guard.ui.theme.KGuardTheme

/**
 * The child's own view of K-Guard: friendly, calm, legible. One gradient spotlight hero carries
 * the promise ("You're protected"); every protection fact below sits on a kit card with a 40dp
 * signal-tinted icon circle. Content is static by design — no live data is invented here.
 */
@Composable
fun ChildDashboardScreen(
    childProfile: ChildProfile?,
    onNavigateToProfile: () -> Unit = {}
) {
    // Uninstall protection lives outside Compose state (DevicePolicyManager); re-check on each entry.
    val context = LocalContext.current
    val adminActive = remember { DeviceProtection.isAdminActive(context) }
    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "K-Guard Child Protection",
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero: the single gradient spotlight card on this screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(KGShapes.spotlight)
                    .background(
                        Brush.linearGradient(
                            listOf(KGColors.gradientViolet, KGColors.accent)
                        )
                    )
                    .padding(24.dp)
            ) {
                KGuardLogo(modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = childProfile?.let { "You're protected, ${it.name}" }
                        ?: "You're protected",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This phone is watched over by K-Guard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(KGShapes.pill)
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "VPN firewall & AI safety active",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            KGSectionHeader(
                title = "Protection on this phone",
                hint = "What K-Guard does for you every day"
            )

            ProtectionFactCard(
                icon = Icons.Default.Timer,
                tint = KGColors.accent,
                title = "Screen Time Today",
                description = "Screen-time limits set by your parent apply to this device."
            )
            ProtectionFactCard(
                icon = Icons.Default.Apps,
                tint = KGColors.warning,
                title = "App Access & Web Firewall",
                description = "Your parent's protection rules block harmful sites and apps on this device."
            )
            if (adminActive) {
                ProtectionFactCard(
                    icon = Icons.Default.Lock,
                    tint = KGColors.success,
                    title = "Uninstall Protection Active",
                    description = "This device is supervised by K-Guard. The app cannot be removed while protection is on."
                )
            } else {
                // Weakened state: the card itself is the fix — tapping launches system activation.
                ProtectionFactCard(
                    icon = Icons.Default.Security,
                    tint = KGColors.warning,
                    title = "Uninstall protection is off",
                    description = "Tap to turn it back on so this app cannot be removed.",
                    onClick = { context.startActivity(DeviceProtection.activationIntent(context)) }
                )
            }
        }
    }
}

/** One protection fact: kit card, 40dp signal-tinted icon circle, calm two-line copy. */
@Composable
private fun ProtectionFactCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null
) {
    KGCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(KGShapes.circle)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChildDashboardPreview() {
    KGuardTheme {
        ChildDashboardScreen(childProfile = ChildProfile(name = "Ama"))
    }
}
