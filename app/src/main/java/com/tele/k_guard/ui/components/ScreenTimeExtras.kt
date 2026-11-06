package com.tele.k_guard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.repository.AppActivity
import com.tele.k_guard.screentime.ScreenPolicy
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val WARN = Color(0xFFB45309)
private val BAD = Color(0xFFB91C1C)

/**
 * Parent's phone: is the child's phone actually able to enforce the rules? Shows nothing when everything
 * is in order, so it only appears when the parent needs to act.
 */
@Composable
fun ChildPhoneHealthCard(device: ChildDevice, modifier: Modifier = Modifier) {
    val name = device.childName.ifBlank { "your child" }
    val (title, lines, tone) = when {
        !device.hasReported -> Triple(
            "Waiting for $name's phone",
            listOf("Install K-Guard on $name's phone, choose Child Device Setup, and enter the pairing code from the Children screen. Rules you set now apply as soon as it connects."),
            WARN
        )
        !device.isOnline -> Triple(
            "$name's phone is offline",
            listOf("Last seen ${lastSeen(device)}. It keeps enforcing the last rules it received, and picks up changes when it reconnects."),
            WARN
        )
        device.missingProtections.isNotEmpty() -> Triple(
            "Protection is incomplete on $name's phone",
            listOf("These still need to be allowed on the phone (open K-Guard there to finish setup):") + device.missingProtections.map { "• $it" },
            BAD
        )
        else -> return
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.10f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = tone)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

fun lastSeen(device: ChildDevice): String {
    val at = device.lastSyncedAtMillis ?: return "never"
    val mins = device.lastSeenMinutesAgo
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 24 * 60 -> "at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
        else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
    }
}

/** Picks a start and end time ("21:00" to "07:00"); end may be on the next day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeDialog(
    title: String,
    start: String,
    end: String,
    onDismiss: () -> Unit,
    onSave: (start: String, end: String) -> Unit
) {
    var step by remember { mutableIntStateOf(0) } // 0 = start, 1 = end
    val s = ScreenPolicy.parseHm(start) ?: 21 * 60
    val e = ScreenPolicy.parseHm(end) ?: 7 * 60
    val startState = rememberTimePickerState(initialHour = s / 60, initialMinute = s % 60, is24Hour = true)
    val endState = rememberTimePickerState(initialHour = e / 60, initialMinute = e % 60, is24Hour = true)
    fun fmt(h: Int, m: Int) = String.format(Locale.US, "%02d:%02d", h, m)
    val same = startState.hour == endState.hour && startState.minute == endState.minute

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (step == 0) "Starts at" else "Ends at", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (step == 0) TimeInput(state = startState) else TimeInput(state = endState)
                if (step == 1) {
                    Text(
                        "${fmt(startState.hour, startState.minute)} to ${fmt(endState.hour, endState.minute)}" +
                            if (endState.hour * 60 + endState.minute <= startState.hour * 60 + startState.minute && !same) " (next day)" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (same) Text("Start and end can't be the same.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (step == 0) Button(onClick = { step = 1 }) { Text("Next") }
            else Button(enabled = !same, onClick = {
                onSave(fmt(startState.hour, startState.minute), fmt(endState.hour, endState.minute))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { if (step == 1) step = 0 else onDismiss() }) { Text(if (step == 1) "Back" else "Cancel") }
        }
    )
}

/** Installs and uninstalls on the child's phone. */
@Composable
fun AppActivitySection(activity: List<AppActivity>, childName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Installs & uninstalls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "New apps on $childName's phone stay blocked until you approve them in App Management.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (activity.isEmpty()) {
                Text("No installs or uninstalls yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            activity.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.appName, fontWeight = FontWeight.SemiBold)
                        val what = when (a.type) {
                            "installed" -> when (a.status) {
                                "PENDING_REVIEW" -> "Installed · sent to you for approval"
                                "APPROVED" -> "Reinstalled · already approved"
                                "REJECTED" -> "Reinstalled · you had not approved it"
                                else -> "Installed"
                            }
                            "uninstalled" -> "Uninstalled"
                            else -> a.type
                        }
                        Text(what, style = MaterialTheme.typography.bodySmall, color = if (a.type == "uninstalled") WARN else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    a.atMillis?.let {
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
