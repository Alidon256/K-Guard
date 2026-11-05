package com.tele.k_guard.vpn

import android.content.Context
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ChildProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnConfig(
    val enabled: Boolean = false,
    val childId: String? = null,
    val policy: VpnPolicy = VpnPolicy(AgeProfile.KIDS_3_7) // strictest until a child is chosen
)

/** Keeps the filter settings where the service can read them without a network or Firestore connection. */
class VpnConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("web_protection", Context.MODE_PRIVATE)

    fun load(): VpnConfig = VpnConfig(
        enabled = prefs.getBoolean("enabled", false),
        childId = prefs.getString("childId", null),
        policy = VpnPolicy(
            ageProfile = AgeProfile.fromId(prefs.getString("age", AgeProfile.KIDS_3_7.id)),
            rules = (prefs.getString("rules", "") ?: "").split(',').mapNotNull {
                val p = it.split('='); if (p.size == 2) p[0] to p[1] else null
            }.toMap(),
            customBlock = prefs.getStringSet("customBlock", emptySet()) ?: emptySet(),
            customAllow = prefs.getStringSet("customAllow", emptySet()) ?: emptySet(),
            educationalAllow = prefs.getBoolean("edu", true)
        )
    )

    /** Copies the child's age group and category rules; custom domain lists are kept. */
    fun applyChild(child: ChildProfile) {
        prefs.edit()
            .putString("childId", child.id)
            .putString("age", child.ageProfile)
            .putString("rules", child.categoryRules.entries.joinToString(",") { "${it.key}=${it.value}" })
            .apply()
        _customBlock.value = load().policy.customBlock
    }

    fun setEnabled(enabled: Boolean) { prefs.edit().putBoolean("enabled", enabled).apply() }

    fun addCustomBlock(domain: String): Boolean {
        val d = cleanDomain(domain) ?: return false
        val next = (load().policy.customBlock + d)
        prefs.edit().putStringSet("customBlock", next).putStringSet("customAllow", load().policy.customAllow - d).apply()
        _customBlock.value = next
        _customAllow.value = load().policy.customAllow
        return true
    }

    fun addCustomAllow(domain: String): Boolean {
        val d = cleanDomain(domain) ?: return false
        val next = (load().policy.customAllow + d)
        prefs.edit().putStringSet("customAllow", next).putStringSet("customBlock", load().policy.customBlock - d).apply()
        _customAllow.value = next
        _customBlock.value = load().policy.customBlock
        return true
    }

    fun removeCustomAllow(domain: String) {
        val next = load().policy.customAllow - domain
        prefs.edit().putStringSet("customAllow", next).apply()
        _customAllow.value = next
    }

    fun setEducationalAllow(on: Boolean) {
        prefs.edit().putBoolean("edu", on).apply()
        _educational.value = on
    }

    private fun cleanDomain(raw: String): String? {
        val d = DomainFilter.normalise(raw.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/'))
        return d.takeIf { DOMAIN.matches(it) }
    }

    fun removeCustomBlock(domain: String) {
        val next = load().policy.customBlock - domain
        prefs.edit().putStringSet("customBlock", next).apply()
        _customBlock.value = next
    }

    private val _customBlock = MutableStateFlow(load().policy.customBlock)
    val customBlock: StateFlow<Set<String>> = _customBlock.asStateFlow()
    private val _customAllow = MutableStateFlow(load().policy.customAllow)
    val customAllow: StateFlow<Set<String>> = _customAllow.asStateFlow()
    private val _educational = MutableStateFlow(load().policy.educationalAllow)
    val educationalAllow: StateFlow<Boolean> = _educational.asStateFlow()

    private companion object {
        val DOMAIN = Regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$")
    }
}

data class BlockedEntry(val domain: String, val label: String, val explanation: String, val time: Long)

/** Live status shown in the app. Recent domains are kept in memory only and never written to disk. */
object VpnState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _recent = MutableStateFlow<List<BlockedEntry>>(emptyList())
    val recent: StateFlow<List<BlockedEntry>> = _recent.asStateFlow()

    fun setRunning(v: Boolean) { _running.value = v }
    fun recordBlock(domain: String, label: String, explanation: String) {
        _blockedCount.value += 1
        _recent.value = (listOf(BlockedEntry(domain, label, explanation, System.currentTimeMillis())) + _recent.value).take(20)
    }
}
