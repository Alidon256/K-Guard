package com.tele.k_guard.vpn

import com.google.firebase.functions.FirebaseFunctions
import com.tele.k_guard.data.model.SafetyCategory
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports blocked lookups to the `reportWebBlock` Cloud Function so parents get an alert.
 * Throttled so a page that fires many lookups produces one alert, not hundreds.
 */
class WebBlockReporter(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    private val lastSent = ConcurrentHashMap<String, Long>()
    private val hourly = ArrayDeque<Long>()

    /** Returns true if a report was attempted. Failures are ignored: blocking never depends on reporting. */
    suspend fun report(childId: String, category: SafetyCategory?, domain: String): Boolean {
        val now = System.currentTimeMillis()
        val key = "$childId|$domain"
        val prev = lastSent[key]
        if (prev != null && now - prev < DOMAIN_COOLDOWN_MS) return false
        synchronized(hourly) {
            while (hourly.isNotEmpty() && now - hourly.first() > HOUR_MS) hourly.removeFirst()
            if (hourly.size >= MAX_PER_HOUR) return false
            hourly.addLast(now)
        }
        lastSent[key] = now
        return runCatching {
            functions.getHttpsCallable("reportWebBlock").call(
                mapOf("childId" to childId, "category" to (category?.id ?: "custom"), "domain" to domain)
            ).await()
        }.isSuccess
    }

    private companion object {
        const val DOMAIN_COOLDOWN_MS = 10 * 60 * 1000L
        const val HOUR_MS = 60 * 60 * 1000L
        const val MAX_PER_HOUR = 20
    }
}
