package com.tele.k_guard.vpn

import android.content.Context
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.CategoryRule
import com.tele.k_guard.data.model.SafetyCategory

/** What the filter needs to know about the protected child. */
data class VpnPolicy(
    val ageProfile: AgeProfile,
    val rules: Map<String, String> = emptyMap(),
    val customBlock: Set<String> = emptySet(),
    val customAllow: Set<String> = emptySet(),
    /** Let recognised educational sites through even if a category list would block them. */
    val educationalAllow: Boolean = true
)

enum class Reason {
    ALLOWED, ALLOWED_PARENT, ALLOWED_EDUCATIONAL, ALLOWED_BY_RULE,
    BLOCKED_CUSTOM, BLOCKED_AGE, BLOCKED_RULE, BLOCKED_LOCKED
}

data class Verdict(
    val blocked: Boolean,
    /** Category that triggered the decision; null for custom lists or an unlisted site. */
    val category: SafetyCategory? = null,
    val custom: Boolean = false,
    val reason: Reason = Reason.ALLOWED
)

/** Where the effective action for a category comes from. */
enum class Source { AGE, RULE, LOCKED }

data class Effective(val blocked: Boolean, val source: Source)

/** Decides whether a DNS name should be blocked. Pure logic, no Android dependencies beyond list loading. */
class DomainFilter(
    private val lists: Map<SafetyCategory, Set<String>>,
    private val educational: Set<String> = emptySet()
) {

    fun listSize(category: SafetyCategory): Int = lists[category]?.size ?: 0

    fun evaluate(rawDomain: String, policy: VpnPolicy): Verdict {
        val domain = normalise(rawDomain)
        if (domain.isEmpty()) return Verdict(false)

        if (matches(domain, policy.customAllow)) return Verdict(false, reason = Reason.ALLOWED_PARENT)
        if (matches(domain, policy.customBlock)) return Verdict(true, custom = true, reason = Reason.BLOCKED_CUSTOM)
        if (policy.educationalAllow && matches(domain, educational)) {
            return Verdict(false, reason = Reason.ALLOWED_EDUCATIONAL)
        }

        for ((category, domains) in lists) {
            if (!matches(domain, domains)) continue
            val e = effective(category, policy)
            val reason = when {
                e.blocked && e.source == Source.RULE -> Reason.BLOCKED_RULE
                e.blocked && e.source == Source.LOCKED -> Reason.BLOCKED_LOCKED
                e.blocked -> Reason.BLOCKED_AGE
                e.source == Source.RULE -> Reason.ALLOWED_BY_RULE
                else -> Reason.ALLOWED
            }
            return Verdict(e.blocked, category, reason = reason)
        }
        return Verdict(false)
    }

    companion object {
        fun normalise(domain: String) = domain.trim().trimEnd('.').lowercase()

        /** The action DNS filtering takes for [category]: parent rule if set, otherwise the age default. */
        fun effective(category: SafetyCategory, policy: VpnPolicy): Effective =
            when (CategoryRule.fromId(policy.rules[category.id])) {
                CategoryRule.BLOCK -> Effective(true, Source.RULE)
                // A parent may allow a category, but never sexual content for under-18s.
                CategoryRule.ALLOW ->
                    if (category == SafetyCategory.SEXUAL && policy.ageProfile != AgeProfile.ADULTS_18_PLUS)
                        Effective(true, Source.LOCKED)
                    else Effective(false, Source.RULE)
                // DNS can only block or allow, so Warn and Blur behave as allow here.
                CategoryRule.WARN, CategoryRule.BLUR -> Effective(false, Source.RULE)
                null -> Effective(defaultBlocks(policy.ageProfile, category), Source.AGE)
            }

        /** Plain-language explanation of a decision, shown to parents and children. */
        fun explain(v: Verdict, policy: VpnPolicy): String {
            val what = v.category?.label?.lowercase() ?: "this category"
            return when (v.reason) {
                Reason.BLOCKED_AGE -> "Blocked: $what isn't suitable for ${policy.ageProfile.ageRange.replaceFirstChar { it.lowercase() }}."
                Reason.BLOCKED_RULE -> "Blocked: a parent chose to block $what."
                Reason.BLOCKED_LOCKED -> "Blocked: sexual content stays blocked under 18, even when a parent allows it."
                Reason.BLOCKED_CUSTOM -> "Blocked: a parent added this site to the block list."
                Reason.ALLOWED_EDUCATIONAL -> "Allowed: recognised educational site."
                Reason.ALLOWED_PARENT -> "Allowed: a parent always allows this site."
                Reason.ALLOWED_BY_RULE -> "Allowed: a parent allows $what for this profile."
                Reason.ALLOWED -> "Allowed: nothing found in K-Guard's site lists. The lists are limited, so this is not a guarantee of safety."
            }
        }

        /** True if [domain] equals a listed entry or is a subdomain of one. */
        fun matches(domain: String, set: Set<String>): Boolean {
            if (set.isEmpty()) return false
            var d = domain
            while (true) {
                if (d in set) return true
                val dot = d.indexOf('.')
                if (dot < 0) return false
                d = d.substring(dot + 1)
            }
        }

        fun defaultBlocks(age: AgeProfile, category: SafetyCategory): Boolean = when (age) {
            AgeProfile.KIDS_3_7, AgeProfile.PRETEENS_8_12 -> true
            AgeProfile.TEENS_13_17 -> category in setOf(
                SafetyCategory.SEXUAL, SafetyCategory.DRUGS, SafetyCategory.HATE,
                SafetyCategory.VIOLENCE, SafetyCategory.SCAM
            )
            AgeProfile.ADULTS_18_PLUS -> category == SafetyCategory.SCAM
        }

        /** Parses one-domain-per-line text, ignoring blanks and `#` comments. */
        fun parseList(text: String): Set<String> =
            text.lineSequence().map { it.substringBefore('#').trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        private fun readAsset(context: Context, name: String): Set<String> =
            runCatching { context.assets.open("blocklists/$name.txt").bufferedReader().use { parseList(it.readText()) } }
                .getOrDefault(emptySet())

        /** Loads `assets/blocklists/<category id>.txt` per category plus `educational.txt`. */
        fun load(context: Context): DomainFilter {
            val map = LinkedHashMap<SafetyCategory, Set<String>>()
            for (c in SafetyCategory.entries) {
                val set = readAsset(context, c.id)
                if (set.isNotEmpty()) map[c] = set
            }
            return DomainFilter(map, readAsset(context, "educational"))
        }
    }
}
