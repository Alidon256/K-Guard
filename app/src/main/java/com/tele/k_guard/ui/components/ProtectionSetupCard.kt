package com.tele.k_guard.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tele.k_guard.receiver.KGuardDeviceAdminReceiver
import com.tele.k_guard.service.KGuardCompanionService
import com.tele.k_guard.util.OsUsageTracker

/** One permission the child's phone needs, with the reason shown to the person setting it up. */
private data class SetupStep(val title: String, val why: String, val done: Boolean, val open: () -> Unit)

/**
 * Child's phone: everything Android requires the person holding the phone to allow before screen time
 * and locks can work. Android does not let any app grant these remotely. Re-checks when the child comes
 * back from Settings, and tells the companion service so the parent sees the change at once.
 * Shows nothing once every step is done.
 */
@Composable
fun ProtectionSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        KGuardCompanionService.requestSync(context)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    // Must be started from this activity: the device-admin screen will not open as a new task.
    val deviceAdmin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh++
        KGuardCompanionService.requestSync(context)
    }

    val steps = remember(refresh) {
        steps(
            context,
            askNotifications = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
            askDeviceAdmin = { runCatching { deviceAdmin.launch(KGuardDeviceAdminReceiver.createAdminRequestIntent(context)) } }
        )
    }
    val missing = steps.filterNot { it.done }
    if (missing.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Finish setting up protection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${steps.size - missing.size} of ${steps.size} done. Android needs someone holding this phone to allow these. Your parent can see what's left.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            missing.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(step.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(step.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = step.open) { Text("Allow") }
                }
            }
        }
    }
}

private fun steps(context: Context, askNotifications: () -> Unit, askDeviceAdmin: () -> Unit): List<SetupStep> = buildList {
    add(SetupStep("Usage access", "Measures screen time and sees which app is open.", OsUsageTracker.hasUsageStatsPermission(context)) {
        OsUsageTracker.openUsageAccessSettings(context)
    })
    add(SetupStep("Display over other apps", "Shows the lock screen at bedtime, at the limit, or when your parent locks the phone.", OsUsageTracker.canDrawOverlays(context)) {
        OsUsageTracker.openOverlaySettings(context)
    })
    add(SetupStep("Device admin", "Switches the screen off when a lock starts, and stops K-Guard being removed unnoticed.", KGuardDeviceAdminReceiver.isAdminActive(context), askDeviceAdmin))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        add(SetupStep("Notifications", "Warns you 10 minutes before bedtime or the end of your screen time.", granted, askNotifications))
    }
    add(SetupStep("Unrestricted battery", "Stops Android pausing protection to save battery.", OsUsageTracker.isIgnoringBatteryOptimizations(context)) {
        OsUsageTracker.openBatterySettings(context)
    })
}
