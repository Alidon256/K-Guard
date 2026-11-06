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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.ParentAction
import com.tele.k_guard.data.model.SafetyEvent
import com.tele.k_guard.ui.components.ChildAvatar
import com.tele.k_guard.ui.components.DecisionBadge
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel
import java.text.DateFormat

/** Live list of moderation events for the family. Parents record Allow / Keep blocked / Dismiss with a note. */
@Composable
fun ReviewQueueScreen(session: SessionViewModel, onNavigateBack: () -> Unit) {
    val events by session.events.collectAsState()
    val children by session.children.collectAsState()
    val error by session.error.collectAsState()

    ReviewQueueContent(
        events = events,
        children = children,
        error = error,
        onReview = { eventId, action, note -> session.review(eventId, action, note) },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ReviewQueueContent(
    events: List<SafetyEvent>,
    children: List<ChildProfile>,
    error: String?,
    onReview: (String, ParentAction, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var onlyOpen by remember { mutableStateOf(true) }
    var childFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<SafetyEvent, ParentAction>?>(null) }

    val openCount = events.count { it.isOpen }

    val shown = events.filter { ev ->
        val statusMatches = !onlyOpen || ev.isOpen
        val childMatches = childFilter == null || ev.childId == childFilter
        val searchMatches = searchQuery.isBlank() ||
                ev.childName.contains(searchQuery, ignoreCase = true) ||
                ev.category.contains(searchQuery, ignoreCase = true) ||
                ev.explanation.contains(searchQuery, ignoreCase = true) ||
                (ev.snippet?.contains(searchQuery, ignoreCase = true) == true)
        statusMatches && childMatches && searchMatches
    }

    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "Alerts & Review",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchActiveChange = { isSearchActive = it },
                actions = {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search Alerts")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // Children WhatsApp-style status row
            if (children.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    item {
                        ChildWhatsAppItem(
                            name = "Everyone",
                            isSelected = childFilter == null,
                            onClick = { childFilter = null },
                            isAll = true
                        )
                    }
                    items(children) { c ->
                        ChildWhatsAppItem(
                            name = c.name,
                            photoUrl = c.photoUrl,
                            isSelected = childFilter == c.id,
                            onClick = { childFilter = c.id }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // WhatsApp Style Filter Tabs Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                item {
                    WhatsAppFilterChip(
                        selected = onlyOpen,
                        onClick = { onlyOpen = true },
                        label = "Needs review ($openCount)"
                    )
                }
                item {
                    WhatsAppFilterChip(
                        selected = !onlyOpen,
                        onClick = { onlyOpen = false },
                        label = "All (${events.size})"
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))

            if (shown.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No alerts found for '$searchQuery'"
                            else if (onlyOpen) "All Clear — No Pending Alerts"
                            else "No Safety Events Recorded Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (onlyOpen) "All flagged items have been reviewed. K-Guard is continuously monitoring your children's devices."
                            else "No safety guardrail triggers or flagged content recorded. Device activity is safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.id }) { ev ->
                        EventCard(ev, onAction = { pending = ev to it })
                    }
                }
            }
        }
    }

    pending?.let { (ev, action) ->
        var note by remember(ev.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(action.label) },
            text = {
                Column {
                    Text("Add an optional note for your records (visible only to parents).")
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(300) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onReview(ev.id, action, note); pending = null }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
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
            .width(68.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
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
                        contentDescription = "Everyone",
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
private fun EventCard(ev: SafetyEvent, onAction: (ParentAction) -> Unit) {
    var isUnblurred by remember(ev.id) { mutableStateOf(ev.parsedDecision == com.tele.k_guard.data.model.ModerationDecision.ALLOW) }

    val cardBg = if (ev.isOpen) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val cardBorder = if (ev.isOpen) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    } else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(cardBorder),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        elevation = CardDefaults.cardElevation(if (ev.isOpen) 2.dp else 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    if (ev.isOpen) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = ev.childName.ifBlank { "Child" },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${ev.contentType.replaceFirstChar { it.uppercase() }} · ${ev.category.replace('_', ' ')} · ${ev.context}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DecisionBadge(ev.parsedDecision)
            }
            Spacer(Modifier.height(8.dp))
            Text(ev.explanation, style = MaterialTheme.typography.bodyMedium)

            ev.snippet?.let { snippetText ->
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isUnblurred) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                        .padding(10.dp)
                ) {
                    if (isUnblurred) {
                        Column {
                            Text("“$snippetText”", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { isUnblurred = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("🔒 Blur snippet", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🔒 Sensitive snippet blurred for privacy",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = { isUnblurred = true }) {
                                Text("Reveal", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            ev.createdAt?.let {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            if (ev.isOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParentAction.entries.forEach { a -> OutlinedButton(onClick = { onAction(a) }) { Text(a.label) } }
                }
            } else {
                val done = ParentAction.entries.find { it.id == ev.parentAction }?.label ?: "Reviewed"
                Text(
                    "Reviewed: $done" + (ev.parentNote?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReviewQueueScreenPreview() {
    val sampleEvent = SafetyEvent(
        id = "1",
        childName = "Alex",
        contentType = "text",
        category = "cyberbullying",
        context = "Pre-Teens profile",
        decision = "WARN",
        explanation = "Contains mild teasing or insensitive language.",
        snippet = "Hey loser, you can't play with us",
        status = "open"
    )
    KGuardTheme {
        ReviewQueueContent(
            events = listOf(sampleEvent),
            children = listOf(ChildProfile(id = "1", name = "Mugumya Ali"), ChildProfile(id = "2", name = "Joseph Walusimbi")),
            error = null,
            onReview = { _, _, _ -> },
            onNavigateBack = {}
        )
    }
}

