package com.tele.k_guard.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.CategoryRule
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.ParentAction
import com.tele.k_guard.data.model.SafetyCategory
import com.tele.k_guard.data.model.SafetyEvent
import com.tele.k_guard.ui.components.DecisionBadge
import com.tele.k_guard.ui.components.KGCard
import com.tele.k_guard.ui.components.KGDangerButton
import com.tele.k_guard.ui.components.KGPrimaryButton
import com.tele.k_guard.ui.components.KGSecondaryButton
import com.tele.k_guard.ui.components.KGSectionHeader
import com.tele.k_guard.ui.components.KGStatusTag
import com.tele.k_guard.ui.components.KGTextLink
import com.tele.k_guard.ui.components.KGTextField
import com.tele.k_guard.ui.components.KGToggleRow
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGColors
import com.tele.k_guard.ui.theme.KGShapes
import com.tele.k_guard.viewmodel.SessionViewModel
import com.tele.k_guard.vpn.DomainFilter
import com.tele.k_guard.vpn.KGuardVpnService
import com.tele.k_guard.vpn.Source
import com.tele.k_guard.vpn.VpnConfigStore
import com.tele.k_guard.vpn.VpnPolicy
import com.tele.k_guard.vpn.VpnState
import java.text.DateFormat
import java.util.Date

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
    var tab by remember { mutableIntStateOf(0) }
    val openWeb = events.count { it.contentType == "web" && it.isOpen }
    val context = LocalContext.current
    // One shared filter for the whole screen, reloaded when the configuration it explains changes.
    val customBlock by store.customBlock.collectAsState()
    val customAllow by store.customAllow.collectAsState()
    val edu by store.educationalAllow.collectAsState()
    val ai by store.aiFilter.collectAsState()
    val filter = remember(customBlock, customAllow, edu, ai, child?.categoryRules) {
        runCatching { DomainFilter.load(context) }.getOrNull()
    }

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
                0 -> StatusTab(session, store, filter)
                1 -> CategoriesTab(session, store, filter)
                else -> ActivityTab(session, store)
            }
        }
    }
}

// ---------------------------------------------------------------- Status: on/off + "Check a site"
@Composable
private fun StatusTab(session: SessionViewModel, store: VpnConfigStore, filter: DomainFilter?) {
    val context = LocalContext.current
    val child by session.selectedChild.collectAsState()
    val running by VpnState.running.collectAsState()
    val blocked by VpnState.blockedCount.collectAsState()

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
    val statusColor = if (running) KGColors.success else KGColors.danger

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        if (filter?.hadLoadErrors == true) {
            KGCard(Modifier.fillMaxWidth()) {
                Text(
                    "Some blocklists failed to load, so some categories may not be filtered. Try restarting the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KGColors.danger
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // VPN state hero: one big status statement, signal colour as a glyph, never as a fill.
        KGCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(statusColor.copy(alpha = 0.12f), KGShapes.circle),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(18.dp).background(statusColor, KGShapes.circle))
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (running) "Web protection is on" else "Web protection is off",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        child?.let { "Protecting ${it.name} (${it.age.ageRange}) with this profile's age group and category rules." }
                            ?: "Add a child profile first, so K-Guard knows which rules to apply.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KGColors.inkMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (running) {
                KGSecondaryButton("Turn off", onClick = { KGuardVpnService.stop(context) })
            } else {
                KGPrimaryButton("Turn on web protection", onClick = { turnOn() }, enabled = child != null)
            }
            if (running) {
                Text(
                    "$blocked site(s) blocked this session",
                    style = MaterialTheme.typography.bodySmall,
                    color = KGColors.inkMuted,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            if (running && child?.age != AgeProfile.ADULTS_18_PLUS) {
                Text(
                    "Safe search and YouTube Restricted Mode are on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KGColors.inkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        QueryStatsCard()

        Spacer(Modifier.height(20.dp))
        KGSectionHeader("Check a site", hint = "See what K-Guard would do for this profile, and why.")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KGTextField(
                site,
                { site = it; checked = null },
                "example.com",
                Modifier.weight(1f)
            )
            KGSecondaryButton(
                "Check",
                enabled = site.isNotBlank() && child != null && filter != null,
                onClick = {
                    val c = child ?: return@KGSecondaryButton
                    val f = filter ?: return@KGSecondaryButton
                    val policy = store.load().policy.copy(ageProfile = c.age, rules = c.categoryRules)
                    val domain = site.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
                    val v = f.evaluate(domain, policy)
                    siteBlocked = v.blocked
                    checked = domain to DomainFilter.explain(v, policy)
                }
            )
        }
        checked?.let { (domain, why) ->
            KGCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KGStatusTag(
                        if (siteBlocked) "Blocked" else "Allowed",
                        if (siteBlocked) KGColors.danger else KGColors.success
                    )
                    Text(domain, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (siteBlocked) KGColors.danger else KGColors.success,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
        KGSectionHeader("How it works")
        Text(
            "K-Guard sets up a private connection on this phone that checks website names only. Harmful sites are blocked in every browser and app. " +
                "Nothing is sent to a K-Guard server, and K-Guard cannot see pages, messages or photos. Use the Text and Image checkers for content.",
            style = MaterialTheme.typography.bodySmall, color = KGColors.inkMuted
        )
        Spacer(Modifier.height(8.dp))
        KGSectionHeader("Good to know")
        Text(
            "• It may not stop apps or browsers that use their own encrypted DNS (for example \"Secure DNS\" in Chrome). Turn that off for best results.\n" +
                "• Android shows a permanent notification while it runs, and it can be switched off in Settings.\n" +
                "• The built-in site list is a starting set. Add your own blocks and allows under Categories.",
            style = MaterialTheme.typography.bodySmall, color = KGColors.inkMuted
        )
    }
}

// ---------------------------------------------------------------- Categories: age-aware protection
@Composable
private fun CategoriesTab(session: SessionViewModel, store: VpnConfigStore, filter: DomainFilter?) {
    val child by session.selectedChild.collectAsState()
    val customBlock by store.customBlock.collectAsState()
    val customAllow by store.customAllow.collectAsState()
    val edu by store.educationalAllow.collectAsState()
    val ai by store.aiFilter.collectAsState()
    val busy by session.busy.collectAsState()
    var blockInput by remember { mutableStateOf("") }
    var allowInput by remember { mutableStateOf("") }
    var blockErr by remember { mutableStateOf(false) }
    var allowErr by remember { mutableStateOf(false) }

    val c = child
    if (c == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Add a child profile to set web rules.", style = MaterialTheme.typography.bodyLarge, color = KGColors.inkMuted)
        }
        return
    }
    val policy = VpnPolicy(c.age, c.categoryRules)

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("${c.name}: ${c.age.title} (${c.age.ageRange})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(c.age.description, style = MaterialTheme.typography.bodySmall, color = KGColors.inkMuted)
        Spacer(Modifier.height(4.dp))
        Text(
            "\"Default\" follows the age group. Change a category to Block or Allow for ${c.name}. Sexual content can't be allowed under 18.",
            style = MaterialTheme.typography.bodySmall, color = KGColors.inkMuted
        )
        Spacer(Modifier.height(8.dp))

        SafetyCategory.entries.forEach { cat ->
            val eff = DomainFilter.effective(cat, policy)
            val current = CategoryRule.fromId(c.categoryRules[cat.id])
            val shown = when (current) {
                CategoryRule.BLOCK -> "Block"
                CategoryRule.ALLOW -> if (eff.source == Source.LOCKED) "Block" else "Allow"
                CategoryRule.WARN -> "Warn"
                CategoryRule.BLUR -> "Blur"
                null -> "Default"
            }
            val count = filter?.listSize(cat) ?: 0
            KGCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cat.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$count site(s) listed" + when (eff.source) {
                                Source.AGE -> " · age default"; Source.RULE -> " · parent rule"; Source.LOCKED -> " · locked under 18"
                            },
                            style = MaterialTheme.typography.labelSmall, color = KGColors.inkMuted
                        )
                    }
                    Text(
                        if (eff.blocked) "Blocked" else "Allowed",
                        color = if (eff.blocked) KGColors.danger else KGColors.success,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = shown == "Default", enabled = !busy, onClick = { session.saveChild(c.copy(categoryRules = c.categoryRules - cat.id)) }, label = { Text("Default") })
                    FilterChip(selected = shown == "Block", enabled = !busy, onClick = { session.saveChild(c.copy(categoryRules = c.categoryRules + (cat.id to CategoryRule.BLOCK.id))) }, label = { Text("Block") })
                    if (!cat.lockedAgainstRelaxing || c.age == com.tele.k_guard.data.model.AgeProfile.ADULTS_18_PLUS) {
                        FilterChip(selected = shown == "Warn", enabled = !busy, onClick = { session.saveChild(c.copy(categoryRules = c.categoryRules + (cat.id to CategoryRule.WARN.id))) }, label = { Text("Warn") })
                        FilterChip(selected = shown == "Blur", enabled = !busy, onClick = { session.saveChild(c.copy(categoryRules = c.categoryRules + (cat.id to CategoryRule.BLUR.id))) }, label = { Text("Blur") })
                        FilterChip(selected = shown == "Allow", enabled = !busy, onClick = { session.saveChild(c.copy(categoryRules = c.categoryRules + (cat.id to CategoryRule.ALLOW.id))) }, label = { Text("Allow") })
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

        KGToggleRow(
            title = "Educational sites",
            description = "Always allow recognised learning and health-education sites (such as encyclopedias and bullying-help pages), even if a category would block them.",
            checked = edu,
            onCheckedChange = { store.setEducationalAllow(it) }
        )

        Spacer(Modifier.height(12.dp))

        KGToggleRow(
            title = "AI website filter",
            description = "When ${c.name} opens a site that is on no list, K-Guard judges it on this device — offline — and blocks it if it looks unsuitable for the ${c.age.title} profile. Parent allow/block lists always win.",
            checked = ai,
            onCheckedChange = { store.setAiFilter(it) }
        )

        Spacer(Modifier.height(12.dp))
        DomainEditor("Always allow", "A parent always allows these, whatever the category.", allowInput, { allowInput = it; allowErr = false }, allowErr, customAllow, onAdd = {
            allowErr = !store.addCustomAllow(allowInput); if (!allowErr) allowInput = ""
        }, onRemove = { store.removeCustomAllow(it) })
        Spacer(Modifier.height(12.dp))
        DomainEditor("Always block", "Blocked for ${c.name} on top of the categories above.", blockInput, { blockInput = it; blockErr = false }, blockErr, customBlock, onAdd = {
            blockErr = !store.addCustomBlock(blockInput); if (!blockErr) blockInput = ""
        }, onRemove = { store.removeCustomBlock(it) })
        Spacer(Modifier.height(16.dp))
        Text("Changes apply immediately while protection is on.", style = MaterialTheme.typography.labelSmall, color = KGColors.inkMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DomainEditor(
    title: String, hint: String, value: String, onChange: (String) -> Unit, error: Boolean,
    items: Set<String>, onAdd: () -> Unit, onRemove: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(hint, style = MaterialTheme.typography.bodySmall, color = KGColors.inkMuted)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        KGTextField(
            value,
            onChange,
            "example.com",
            Modifier.weight(1f),
            isError = error,
            supportingText = if (error) "Enter a website like example.com" else null
        )
        KGSecondaryButton("Add", onClick = onAdd)
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.sorted().forEach { d ->
            InputChip(
                selected = false,
                onClick = { onRemove(d) },
                label = { Text(d) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove $d",
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
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
    var pendingAllow by remember { mutableStateOf<String?>(null) }
    val shown = webEvents.filter { !onlyOpen || it.isOpen }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        KGSectionHeader(
            "Alerts for parents",
            hint = "Each blocked site creates an alert (one per site every 10 minutes). Notifications follow your Privacy & Notifications settings."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(selected = onlyOpen, onClick = { onlyOpen = true }, label = { Text("Needs review") })
            FilterChip(selected = !onlyOpen, onClick = { onlyOpen = false }, label = { Text("All") })
        }
        if (shown.isEmpty()) {
            Text(
                if (onlyOpen) "No web alerts need review." else "No web alerts yet.",
                color = KGColors.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        shown.forEach { ev -> WebAlertCard(ev, session, store) }

        pendingAllow?.let { domain ->
            AllowSiteDialog(domain, onConfirm = { store.addCustomAllow(domain); pendingAllow = null }, onDismiss = { pendingAllow = null })
        }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
            KGSectionHeader(
                "Blocked on this phone (this session)",
                hint = "Kept in memory only and cleared when the app closes."
            )
            recent.forEach { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.time)), style = MaterialTheme.typography.labelSmall, color = KGColors.inkMuted)
                    }
                    Text(e.explanation, style = MaterialTheme.typography.bodySmall, color = KGColors.inkMuted)
                    KGSecondaryButton("Allow this site", onClick = { pendingAllow = e.domain }, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebAlertCard(ev: SafetyEvent, session: SessionViewModel, store: VpnConfigStore) {
    var pendingAllow by remember { mutableStateOf<String?>(null) }
    KGCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ev.childName.ifBlank { "Child" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                KGStatusTag(
                    ev.category.replace('_', ' '),
                    KGColors.accent,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            DecisionBadge(ev.parsedDecision)
        }
        Text(ev.explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ev.snippet?.let {
            Text("Site: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
        ev.createdAt?.let {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it.toDate()),
                style = MaterialTheme.typography.labelSmall,
                color = KGColors.inkMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (ev.isOpen) {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KGSecondaryButton("Keep blocked", onClick = { session.review(ev.id, ParentAction.KEEP_BLOCKED, "") })
                if (ev.snippet != null) {
                    KGDangerButton("Allow site", onClick = { pendingAllow = ev.snippet })
                }
                KGTextLink("Dismiss", onClick = { session.review(ev.id, ParentAction.DISMISS, "") })
            }
            if (ev.snippet == null) {
                Text(
                    "The site name isn't stored. Turn on excerpts in Privacy settings to allow sites from here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = KGColors.inkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            Text(
                "Reviewed: " + (ParentAction.entries.find { it.id == ev.parentAction }?.label ?: "done"),
                color = KGColors.success,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    pendingAllow?.let { domain ->
        AllowSiteDialog(
            domain,
            onConfirm = { store.addCustomAllow(domain); session.review(ev.id, ParentAction.ALLOW, "Site allowed"); pendingAllow = null },
            onDismiss = { pendingAllow = null }
        )
    }
}

/** Confirms before a one-tap "allow" permanently lets a site through for this child. */
@Composable
private fun AllowSiteDialog(domain: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = KGShapes.card,
        title = { Text("Always allow $domain?") },
        text = { Text("This site will be allowed in every browser and app, whatever the category rules say. You can remove it again under Categories.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Allow", color = KGColors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = KGColors.inkMuted) } }
    )
}
