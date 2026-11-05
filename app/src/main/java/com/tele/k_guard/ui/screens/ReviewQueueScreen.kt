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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.ParentAction
import com.tele.k_guard.data.model.SafetyEvent
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
    var pending by remember { mutableStateOf<Pair<SafetyEvent, ParentAction>?>(null) }

    val shown = events.filter { (!onlyOpen || it.isOpen) && (childFilter == null || it.childId == childFilter) }

    Scaffold(topBar = { KGuardTopAppBar("Alerts & Review", canNavigateBack = true, onNavigateBack = onNavigateBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = onlyOpen, onClick = { onlyOpen = true }, label = { Text("Needs review") })
                FilterChip(selected = !onlyOpen, onClick = { onlyOpen = false }, label = { Text("All") })
            }
            if (children.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(selected = childFilter == null, onClick = { childFilter = null }, label = { Text("Everyone") })
                    children.forEach { c ->
                        FilterChip(selected = childFilter == c.id, onClick = { childFilter = c.id }, label = { Text(c.name) })
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (onlyOpen) "Nothing needs your review right now." else "No safety events recorded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    OutlinedTextField(note, { note = it.take(300) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { onReview(ev.id, action, note); pending = null }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EventCard(ev: SafetyEvent, onAction: (ParentAction) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ev.isOpen) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ev.childName.ifBlank { "Child" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${ev.contentType.replaceFirstChar { it.uppercase() }} · ${ev.category.replace('_', ' ')} · ${ev.context}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DecisionBadge(ev.parsedDecision)
            }
            Spacer(Modifier.height(8.dp))
            Text(ev.explanation, style = MaterialTheme.typography.bodyMedium)
            ev.snippet?.let {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp)) {
                    Text("“$it”", style = MaterialTheme.typography.bodySmall)
                }
            }
            ev.createdAt?.let {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it.toDate()),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text("Reviewed: $done" + (ev.parentNote?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
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
        category = "bullying",
        context = "Pre-Teens profile",
        decision = "WARN",
        explanation = "Contains mild teasing or insensitive language.",
        snippet = "Hey loser, you can't play with us",
        isOpen = true
    )
    KGuardTheme {
        ReviewQueueContent(
            events = listOf(sampleEvent),
            children = emptyList(),
            error = null,
            onReview = { _, _, _ -> },
            onNavigateBack = {}
        )
    }
}
