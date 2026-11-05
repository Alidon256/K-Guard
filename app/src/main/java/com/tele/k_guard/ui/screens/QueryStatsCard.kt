package com.tele.k_guard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.SafetyCategory
import com.tele.k_guard.ui.components.KGCard
import com.tele.k_guard.ui.theme.KGColors
import com.tele.k_guard.vpn.QueryMonitor

/**
 * Today's DNS activity: how many lookups were checked, how many were blocked, and how many matched each
 * category list. Only counts are shown. In debuggable builds it also lists the latest lookups and timings.
 */
@Composable
fun QueryStatsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Hydrate even when the VPN service never ran in this process; init is idempotent.
    LaunchedEffect(Unit) { QueryMonitor.init(context) }
    val day by QueryMonitor.today.collectAsState()

    KGCard(modifier.fillMaxWidth()) {
        Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${day.queries}", style = MaterialTheme.typography.titleLarge)
                Text("lookups checked", style = MaterialTheme.typography.labelSmall, color = KGColors.inkMuted)
            }
            Column(Modifier.weight(1f)) {
                Text("${day.blocked}", style = MaterialTheme.typography.titleLarge)
                Text("blocked", style = MaterialTheme.typography.labelSmall, color = KGColors.inkMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = KGColors.hairline)
        Spacer(Modifier.height(12.dp))
        if (day.flagged.isEmpty()) {
            Text(
                "Nothing flagged yet. K-Guard only counts lookups that match its site lists.",
                style = MaterialTheme.typography.bodySmall,
                color = KGColors.inkMuted
            )
        } else {
            Text("Flagged by category", style = MaterialTheme.typography.labelLarge)
            day.flagged.entries.sortedByDescending { it.value }.forEach { (id, n) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(SafetyCategory.entries.find { it.id == id }?.label ?: id)
                    Text("$n", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (QueryMonitor.debug) DebugLookups()
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
        color = KGColors.inkMuted
    )
    recent.take(10).forEach { e ->
        Text(
            "${e.outcome.name.take(5)} ${e.type} ${e.domain}${e.category?.let { " [$it]" } ?: ""} +${e.addedMs}ms",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = KGColors.inkMuted
        )
    }
}
