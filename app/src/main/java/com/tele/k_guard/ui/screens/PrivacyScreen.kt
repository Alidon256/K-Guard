package com.tele.k_guard.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.FamilyPrivacy
import com.tele.k_guard.data.model.QuietHours
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.components.RequestPermission
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel
import java.util.TimeZone

private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

@Composable
fun PrivacyScreen(session: SessionViewModel, onNavigateBack: () -> Unit) {
    val saved by session.privacy.collectAsState()
    val busy by session.busy.collectAsState()
    val user by session.user.collectAsState()
    // The form must not seed itself from defaults before the family doc (or demo data) is streaming.
    val familyId by session.familyId.collectAsState()

    PrivacyContent(
        saved = saved,
        busy = busy,
        userEmail = user?.email,
        formReady = familyId != null,
        // Demo sessions have no Firebase user; exiting the demo replaces a real sign-out there.
        demoMode = user == null,
        onSavePrivacy = { session.savePrivacy(it) },
        onSignOut = { if (user == null) session.exitDemo() else session.signOut() },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun PrivacyContent(
    saved: FamilyPrivacy,
    busy: Boolean,
    userEmail: String?,
    formReady: Boolean = true,
    demoMode: Boolean = false,
    onSavePrivacy: (FamilyPrivacy) -> Unit,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var notifyOn by rememberSaveable { mutableStateOf(saved.notifyOn) }
    var includeSnippet by rememberSaveable { mutableStateOf(saved.includeSnippet) }
    var retainDays by rememberSaveable { mutableStateOf(saved.retainDays) }
    var quiet by rememberSaveable { mutableStateOf(saved.quietHours.enabled) }
    var qStart by rememberSaveable { mutableStateOf(saved.quietHours.start) }
    var qEnd by rememberSaveable { mutableStateOf(saved.quietHours.end) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    // Seed the form from the remote document exactly ONCE per screen instance. Later remote
    // changes must not clobber settings the parent is mid-way through editing; the seeded
    // form fields themselves survive rotation via rememberSaveable.
    LaunchedEffect(saved, formReady) {
        if (!initialized && formReady) {
            notifyOn = saved.notifyOn
            includeSnippet = saved.includeSnippet
            retainDays = saved.retainDays
            quiet = saved.quietHours.enabled
            qStart = saved.quietHours.start
            qEnd = saved.quietHours.end
            initialized = true
        }
    }

    var notificationsDeclined by remember { mutableStateOf(false) }
    val requestNotifications = RequestPermission(Manifest.permission.POST_NOTIFICATIONS) { granted ->
        notificationsDeclined = !granted
    }

    val timesOk = !quiet || (TIME.matches(qStart) && TIME.matches(qEnd))

    Scaffold(topBar = { KGuardTopAppBar("Privacy & Notifications", canNavigateBack = true, onNavigateBack = onNavigateBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Section("Which alerts should notify you?")
            listOf(
                FamilyPrivacy.NOTIFY_ALL to "Everything flagged (including Warn)",
                FamilyPrivacy.NOTIFY_WARN_AND_ABOVE to "Warn, Blur, Block and Review",
                FamilyPrivacy.NOTIFY_BLOCK_ONLY to "Only blocked content"
            ).forEach { (id, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = notifyOn == id, role = Role.RadioButton) { notifyOn = id }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { RadioButton(selected = notifyOn == id, onClick = null); Spacer(Modifier.width(8.dp)); Text(label) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = requestNotifications) { Text("Allow notifications on this phone") }
                if (notificationsDeclined) {
                    Text(
                        "Notifications were declined. You can enable them later in your phone's settings for K-Guard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            Section("Quiet hours")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hold non-blocking alerts during quiet hours. Blocked content always notifies.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = quiet, onCheckedChange = { quiet = it })
            }
            if (quiet) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(qStart, { qStart = it.take(5) }, label = { Text("From (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f), isError = !TIME.matches(qStart), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(qEnd, { qEnd = it.take(5) }, label = { Text("Until (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f), isError = !TIME.matches(qEnd), shape = RoundedCornerShape(12.dp))
                }
            }

            Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            Section("What K-Guard keeps")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Store a short text excerpt with each alert", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Off by default. When off, alerts record the category and explanation but not the content itself, and uploaded images are deleted right after checking.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = includeSnippet, onCheckedChange = { includeSnippet = it })
            }
            Spacer(Modifier.height(8.dp))
            Text("Delete alerts after", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { d -> FilterChip(selected = retainDays == d, onClick = { retainDays = d }, label = { Text("$d days") }) }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSavePrivacy(
                        FamilyPrivacy(
                            notifyOn = notifyOn, includeSnippet = includeSnippet, retainDays = retainDays,
                            quietHours = QuietHours(quiet, qStart, qEnd, TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000)
                        )
                    )
                },
                enabled = timesOk && !busy, modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Saving…" else "Save settings") }

            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
            Text("Signed in as ${userEmail ?: "—"}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onSignOut, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (demoMode) "Exit demo" else "Sign out")
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Preview(showBackground = true)
@Composable
fun PrivacyScreenPreview() {
    KGuardTheme {
        PrivacyContent(
            saved = FamilyPrivacy(),
            busy = false,
            userEmail = "parent@example.com",
            onSavePrivacy = {},
            onSignOut = {},
            onNavigateBack = {}
        )
    }
}
