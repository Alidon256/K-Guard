package com.tele.k_guard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.SafetyCategory
import com.tele.k_guard.vpn.QueryMonitor

/**
 * Today's DNS activity: how many lookups were checked, how many were blocked, and how many matched each
 * category list. Only counts are shown. In debuggable builds it also lists the latest lookups and timings.
 */
@Composable
fun QueryStatsCard(modifier: Modifier = Modifier) {
    val day by QueryMonitor.today.collectAsState()

    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${day.queries} lookups checked · ${day.blocked} blocked",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            if (day.flagged.isEmpty()) {
                Text(
                    "Nothing flagged yet. K-Guard only counts lookups that match its site lists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Flagged by category", style = MaterialTheme.typography.labelLarge)
                day.flagged.entries.sortedByDescending { it.value }.forEach { (id, n) ->
                    Row {
                        Text(SafetyCategory.entries.find { it.id == id }?.label ?: id, Modifier.weight(1f))
                        Text("$n", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (QueryMonitor.debug) DebugLookups()
        }
    }
}

@Composable
private fun DebugLookups() {
    val recent by QueryMonitor.recent.collectAsState()
    val latency = QueryMonitor.latency()
    Spacer(Modifier.height(12.dp))
    Text("Debug: recent lookups", style = MaterialTheme.typography.labelLarge)
    Text(
        latency?.let { "K-Guard added p50 ${it.first} ms · p95 ${it.second} ms" } ?: "No timings yet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    recent.take(10).forEach { e ->
        Text(
            "${e.outcome.name.take(5)} ${e.type} ${e.domain}${e.category?.let { " [$it]" } ?: ""} +${e.addedMs}ms",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}
