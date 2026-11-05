# Plan: Local AI Domain Classifier (docs/spec-web-classifier.md)

## Reality checks from code exploration

- The decision point is `DomainFilter.evaluate()`; `KGuardVpnService` needs **no changes** —
  it already re-reads `VpnPolicy` per query, so a new `aiFilterEnabled` field flows through.
- `DomainFilter` must stay Android-context-free (tests construct it directly); list loading
  stays in `companion.load(context)`.
- The classifier runs only when a domain matches **no** list (matched lists decide as today);
  parent allow/block and educational lists keep their current priority.
- `SharedPreferences` is not available in JVM unit tests → `VerdictCache` takes a tiny
  storage interface; production binding lives in a one-line factory.

## Components

1. `vpn/classifier/DomainClassifier.kt` — interface + `ClassifierVerdict(category, confidence, riskScore, label)`.
2. `vpn/classifier/HeuristicDomainClassifier.kt` — v0 scorer: tokenised keyword weights per
   `SafetyCategory`, benign-token damping, punycode suspicion, per-age risk thresholds mirroring
   `functions/src/policy.ts` (KIDS 0.50 / PRETEENS 0.55 / TEENS 0.70 / ADULTS 0.92),
   confidence floor 0.80 → returns `null` when unsure (domain stays allowed).
3. `vpn/classifier/VerdictCache.kt` — LRU (4,096) + 24h TTL, JSON blob in `"web_protection"`
   prefs, invalidated by a `cache_gen` counter that `VpnConfigStore` bumps on age/rule/AI changes.
4. `DomainFilter` — `VpnPolicy.aiFilterEnabled = true`, new reasons `BLOCKED_AI`, `ALLOWED_AI`,
   `BLOCKED_DOH`; DoH set from new `blocklists/doh.txt`; hardcoded NXDOMAIN for
   `use-application-dns.net`; classifier step for unlisted domains via `effective(category, policy)`
   so parent category rules still govern.
5. `VpnConfigStore` — `aiFilterEnabled` persistence + shared `StateFlow` + gen bumps.
6. `WebProtectionScreen` CategoriesTab — "AI website filter" toggle next to the educational toggle;
   filter `remember` key gains the toggle so "Check a site" reflects it.

## Order

1 → 2 (tests) → 3 (tests) → 4 (tests) → 5 → 6 → full suite + assembleDebug → sync Mac testbed.

## Risks

- False positives on education/hobby domains → benign token damping + confidence floor; tune after device testing.
- Heuristic scorer is not "AI" in the neural sense → interface is the contract; ONNX model swaps in later (spec boundary: ask first before adding the dependency).
