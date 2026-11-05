# Tasks: Local AI Domain Classifier

- [x] Task: Classifier module — `DomainClassifier` interface + `HeuristicDomainClassifier` v0
  - Acceptance: adult domains score ≥ threshold for KIDS/PRETEENS/TEENS, benign domains return null, punycode flagged, per-age thresholds mirror policy.ts
  - Verify: `./gradlew :app:testDebugUnitTest --tests "com.tele.k_guard.vpn.classifier.*"` — 19/19 pass
  - Files: `vpn/classifier/DomainClassifier.kt`, `vpn/classifier/HeuristicDomainClassifier.kt`, `app/src/test/.../classifier/HeuristicDomainClassifierTest.kt`

- [x] Task: `VerdictCache` with storage abstraction
  - Acceptance: hit/miss/expiry/LRU eviction/generation invalidation/null-caching all pass
  - Verify: 10/10 pass
  - Files: `vpn/classifier/VerdictCache.kt`, `app/src/test/.../classifier/VerdictCacheTest.kt`

- [x] Task: `DomainFilter` integration — `aiFilterEnabled`, new Reasons, DoH list, classifier step
  - Acceptance: unlisted adult domain blocked for kids via classifier; parent allow overrides; toggle off restores old behaviour; doh.txt + use-application-dns.net blocked; explain() covers new reasons
  - Verify: `VpnAiFilterTest` 9/9 pass; existing `VpnLogicTest` untouched and green
  - Files: `vpn/DomainFilter.kt`, `app/src/main/assets/blocklists/doh.txt`, `app/src/test/.../vpn/VpnLogicTest.kt`

- [x] Task: `VpnConfigStore` persistence + shared AI flow
  - Acceptance: ai toggle persists, bumps cache generation, shared StateFlow hydrates like edu
  - Verify: compiles; covered indirectly via filter tests
  - Files: `vpn/VpnConfigStore.kt`

- [x] Task: Web Protection UI toggle
  - Acceptance: AI filter switch in CategoriesTab; toggling reloads the screen filter for "Check a site"
  - Verify: `./gradlew :app:assembleDebug` + clean launch on emulator-5554 (no crash log entries)
  - Files: `ui/screens/WebProtectionScreen.kt`

- [x] Task: Full verification
  - Acceptance: full suite green, assembleDebug succeeds
  - Verify: 78/78 tests pass, build OK, installed on emulator 00:02, pushed as 5db9447
  - Files: —
