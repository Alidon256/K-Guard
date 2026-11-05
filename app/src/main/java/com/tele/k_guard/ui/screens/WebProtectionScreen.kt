package com.tele.k_guard.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.CategoryRule
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.ParentAction
import com.tele.k_guard.data.model.SafetyCategory
import com.tele.k_guard.data.model.SafetyEvent
import com.tele.k_guard.ui.components.DecisionBadge
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel
import com.tele.k_guard.vpn.DomainFilter
import com.tele.k_guard.vpn.KGuardVpnService
import com.tele.k_guard.vpn.Source
import com.tele.k_guard.vpn.VpnConfigStore
import com.tele.k_guard.vpn.VpnPolicy
import com.tele.k_guard.vpn.VpnState
import java.text.DateFormat
import java.util.Date

private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)

@Composable
fun WebProtectionScreen(session: SessionViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { VpnConfigStore(context) }
    val child by session.selectedChild.collectAsState()
    val events by session.events.collectAsState()

    // Keep the service's copy of the child's age group and rules current.
    LaunchedEffect(child) { child?.let { store.applyChild(it) } }

    WebProtectionContent(
        session = session,
        store = store,
        child = child,
        events = events,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun WebProtectionContent(
    session: SessionViewModel,
    store: VpnConfigStore,
    child: ChildProfile?,
    events: List<SafetyEvent>,
    onNavigateBack: () -> Unit
) {
    var tab by remember { mutableIntStateOf(runCatching { com.tele.k_guard.DebugPreview.webTab }.getOrDefault(0)) }
    val openWeb = events.count { it.contentType == "web" && it.isOpen }

    Scaffold(topBar = { KGuardTopAppBar("Web Protection", canNavigateBack = true, onNavigateBack = onNavigateBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Status") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Categories") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Activity")
                        if (openWeb > 0) Badge { Text("$openWeb") }
                    }
                })
            }
            when (tab) {
                0 -> StatusTab(session, store)
                1 -> CategoriesTab(session, store)
                else -> ActivityTab(session, store)
            }
        }
    }
}

// ---------------------------------------------------------------- Status: on/off + "Check a site"
@Composable
private fun StatusTab(session: SessionViewModel, store: VpnConfigStore) {
    val context = LocalContext.current
    val child by session.selectedChild.collectAsState()
    val running by VpnState.running.collectAsState()
    val blocked by VpnState.blockedCount.collectAsState()
    val filter = remember { runCatching { DomainFilter.load(context) }.getOrNull() }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) KGuardVpnService.start(context)
    }
    fun turnOn() {
        child?.let { store.applyChild(it) }
        val prepare = VpnService.prepare(context)
        if (prepare != null) consent.launch(prepare) else KGuardVpnService.start(context)
    }

    var site by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf<Pair<String, String>?>(null) }
    var siteBlocked by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(if (running) "Protection is on" else "Protection is off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    child?.let { "Protecting ${it.name} (${it.age.ageRange}) with this profile's age group and category rules." }
                        ?: "Add a child profile first, so K-Guard knows which rules to apply.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                if (running) OutlinedButton(onClick = { KGuardVpnService.stop(context) }) { Text("Turn off") }
                else Button(onClick = { turnOn() }, enabled = child != null) { Text("Turn on web protection") }
                if (running) Text("$blocked site(s) blocked this session", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Check a site", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("See what K-Guard would do for this profile, and why.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(site, { site = it; checked = null }, label = { Text("example.com") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            Button(
                enabled = site.isNotBlank() && child != null && filter != null,
                onClick = {
                    val c = child ?: return@Button
                    val f = filter ?: return@Button
                    val policy = store.load().policy.copy(ageProfile = c.age, rules = c.categoryRules)
                    val domain = site.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
                    val v = f.evaluate(domain, policy)
                    siteBlocked = v.blocked
                    checked = domain to DomainFilter.explain(v, policy)
                }
            ) { Text("Check") }
        }
        checked?.let { (domain, why) ->
            Card(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = (if (siteBlocked) RED else GREEN).copy(alpha = 0.12f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(domain, fontWeight = FontWeight.Bold)
                    Text(why, style = MaterialTheme.typography.bodyMedium, color = if (siteBlocked) RED else GREEN)
                }
            }
        }

        Spacer(Modifier.height(20.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
        Text("How it works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "K-Guard sets up a private connection on this phone that checks website names only. Harmful sites are blocked in every browser and app. " +
                "Nothing is sent to a K-Guard server, and K-Guard cannot see pages, messages or photos. Use the Text and Image checkers for content.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text("Good to know", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "• It may not stop apps or browsers that use their own encrypted DNS (for example \"Secure DNS\" in Chrome). Turn that off for best results.\n" +
                "• Android shows a permanent notification while it runs, and it can be switched off in Settings.\n" +
                "• The built-in site list is a starting set. Add your own blocks and allows under Categories.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- Categories: age-aware protection
@Composable
private fun CategoriesTab(session: SessionViewModel, store: VpnConfigStore) {
    val context = LocalContext.current
    val child by session.selectedChild.collectAsState()
    val customBlock by store.customBlock.collectAsState()
    val customAllow by store.customAllow.collectAsState()
    val edu by store.educationalAllow.collectAsState()
    val filter = remember { runCatching { DomainFilter.load(context) }.getOrNull() }
    val busy by session.busy.collectAsState()
    var blockInput by remember { mutableStateOf("") }
    var allowInput by remember { mutableStateOf("") }
    var blockErr by remember { mutableStateOf(false) }
    var allowErr by remember { mutableStateOf(false) }

    val c = child
    if (c == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Add a child profile to set web rules.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val policy = VpnPolicy(c.age, c.categoryRules)

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("${c.name}: ${c.age.title} (${c.age.ageRange})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(c.age.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "\"Default\" follows the age group. Change a category to Block or Allow for ${c.name}. Sexual content can't be allowed under 18.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        SafetyCategory.entries.forEach { cat ->
            val eff = DomainFilter.effective(cat, policy)
            val current = CategoryRule.fromId(c.categoryRules[cat.id])
            val shown = when (current) { CategoryRule.BLOCK -> "Block"; CategoryRule.ALLOW -> if (eff.source == Source.LOCKED) "Block" else "Allow"; null -> "Default"; else -> "Default" }
            val count = filter?.listSize(cat) ?: 0
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cat.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                "$count site(s) listed" + when (eff.source) {
                                    Source.AGE -> " · age default"; Source.RULE -> " · parent rule"; Source.LOCKED -> " · locked under 18"
                                },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(if (eff.blocked) "Blocked" else "Allowed", color = if (eff.blocked) RED else GREEN, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = shown == "Default", enabled = !busy, onClick = { session.saveChild(c.copy(categoryRules = c.categoryRules - cat.id)) }, label = { Text("Default") })
                        FilterChip(selected = shown == "Block", enabled = !busy, onClick = { session.saveChild(c.copy(categoryRules = c.categoryRules + (cat.id to CategoryRule.BLOCK.id))) }, label = { Text("Block") })
                        if (!cat.lockedAgainstRelaxing || c.age == com.tele.k_guard.data.model.AgeProfile.ADULTS_18_PLUS) {
                            FilterChip(selected = shown == "Allow", enabled = !busy, onClick = { session.saveChild(c.copy(categoryRules = c.categoryRules + (cat.id to CategoryRule.ALLOW.id))) }, label = { Text("Allow") })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

        Text("Educational sites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Always allow recognised learning and health-education sites (such as encyclopedias and bullying-help pages), even if a category would block them.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
            )
            Switch(checked = edu, onCheckedChange = { store.setEducationalAllow(it) })
        }

        Spacer(Modifier.height(12.dp))
        DomainEditor("Always allow", "A parent always allows these, whatever the category.", allowInput, { allowInput = it; allowErr = false }, allowErr, customAllow, onAdd = {
            allowErr = !store.addCustomAllow(allowInput); if (!allowErr) allowInput = ""
        }, onRemove = { store.removeCustomAllow(it) })
        Spacer(Modifier.height(12.dp))
        DomainEditor("Always block", "Blocked for ${c.name} on top of the categories above.", blockInput, { blockInput = it; blockErr = false }, blockErr, customBlock, onAdd = {
            blockErr = !store.addCustomBlock(blockInput); if (!blockErr) blockInput = ""
        }, onRemove = { store.removeCustomBlock(it) })
        Spacer(Modifier.height(16.dp))
        Text("Changes apply immediately while protection is on.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DomainEditor(
    title: String, hint: String, value: String, onChange: (String) -> Unit, error: Boolean,
    items: Set<String>, onAdd: () -> Unit, onRemove: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        OutlinedTextField(value, onChange, label = { Text("example.com") }, singleLine = true, isError = error, modifier = Modifier.weight(1f))
        Button(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) { Text("Add") }
    }
    if (error) Text("Enter a website like example.com", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.sorted().forEach { d ->
            InputChip(selected = false, onClick = { onRemove(d) }, label = { Text(d) }, trailingIcon = { Text("✕") })
        }
    }
}

// ---------------------------------------------------------------- Activity: alerts, explanations, review
@Composable
private fun ActivityTab(session: SessionViewModel, store: VpnConfigStore) {
    val events by session.events.collectAsState()
    val recent by VpnState.recent.collectAsState()
    val webEvents = events.filter { it.contentType == "web" }
    var onlyOpen by remember { mutableStateOf(true) }
    val shown = webEvents.filter { !onlyOpen || it.isOpen }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Alerts for parents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Each blocked site creates an alert (one per site every 10 minutes). Notifications follow your Privacy & Notifications settings.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(selected = onlyOpen, onClick = { onlyOpen = true }, label = { Text("Needs review") })
            FilterChip(selected = !onlyOpen, onClick = { onlyOpen = false }, label = { Text("All") })
        }
        if (shown.isEmpty()) {
            Text(if (onlyOpen) "No web alerts need review." else "No web alerts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        }
        shown.forEach { ev -> WebAlertCard(ev, session, store) }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
            Text("Blocked on this phone (this session)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Kept in memory only and cleared when the app closes.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            recent.forEach { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.time)), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(e.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { store.addCustomAllow(e.domain) }, modifier = Modifier.padding(top = 4.dp)) { Text("Allow this site") }
                }
            }
        }
    }
}

@Composable
private fun WebAlertCard(ev: SafetyEvent, session: SessionViewModel, store: VpnConfigStore) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ev.childName.ifBlank { "Child" }, fontWeight = FontWeight.Bold)
                    Text(ev.category.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DecisionBadge(ev.parsedDecision)
            }
            Text(ev.explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            ev.snippet?.let { Text("Site: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
            ev.createdAt?.let {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it.toDate()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ev.isOpen) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { session.review(ev.id, ParentAction.KEEP_BLOCKED, "") }) { Text("Keep blocked") }
                    if (ev.snippet != null) {
                        OutlinedButton(onClick = { store.addCustomAllow(ev.snippet); session.review(ev.id, ParentAction.ALLOW, "Site allowed") }) { Text("Allow site") }
                    }
                    OutlinedButton(onClick = { session.review(ev.id, ParentAction.DISMISS, "") }) { Text("Dismiss") }
                }
                if (ev.snippet == null) {
                    Text("The site name isn't stored. Turn on excerpts in Privacy settings to allow sites from here.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                Text("Reviewed: " + (ParentAction.entries.find { it.id == ev.parentAction }?.label ?: "done"), color = GREEN, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
fun WebProtectionStatusContent(
    running: Boolean,
    blockedCount: Int,
    childName: String?,
    childAgeRange: String?,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var site by remember { mutableStateOf("") }

    Scaffold(topBar = { KGuardTopAppBar("Web Protection", canNavigateBack = true, onNavigateBack = onNavigateBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (running) "Protection is on" else "Protection is off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (childName != null) "Protecting $childName ($childAgeRange) with this profile's age group and category rules."
                        else "Add a child profile first, so K-Guard knows which rules to apply.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    if (running) OutlinedButton(onClick = onTurnOff) { Text("Turn off") }
                    else Button(onClick = onTurnOn, enabled = childName != null) { Text("Turn on web protection") }
                    if (running) Text("$blockedCount site(s) blocked this session", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Check a site", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("See what K-Guard would do for this profile, and why.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(site, { site = it }, label = { Text("example.com") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(enabled = site.isNotBlank() && childName != null, onClick = {}) { Text("Check") }
            }

            Spacer(Modifier.height(20.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
            Text("How it works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "K-Guard sets up a private connection on this phone that checks website names only. Harmful sites are blocked in every browser and app. " +
                    "Nothing is sent to a K-Guard server, and K-Guard cannot see pages, messages or photos. Use the Text and Image checkers for content.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WebProtectionScreenPreview() {
    KGuardTheme {
        WebProtectionStatusContent(
            running = true,
            blockedCount = 3,
            childName = "Alex",
            childAgeRange = "Ages 8 – 12",
            onTurnOn = {},
            onTurnOff = {},
            onNavigateBack = {}
        )
    }
}
