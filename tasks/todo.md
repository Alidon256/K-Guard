# Tasks: Local AI Domain Classifier

- [ ] Task: Classifier module — `DomainClassifier` interface + `HeuristicDomainClassifier` v0
  - Acceptance: adult domains score ≥ threshold for KIDS/PRETEENS/TEENS, benign domains return null, punycode flagged, per-age thresholds mirror policy.ts
  - Verify: `./gradlew test --tests "*HeuristicDomainClassifier*"`
  - Files: `vpn/classifier/DomainClassifier.kt`, `vpn/classifier/HeuristicDomainClassifier.kt`, `app/src/test/.../classifier/HeuristicDomainClassifierTest.kt`

- [ ] Task: `VerdictCache` with storage abstraction
  - Acceptance: hit/miss/expiry/LRU eviction/generation invalidation/null-caching all pass
  - Verify: `./gradlew test --tests "*VerdictCache*"`
  - Files: `vpn/classifier/VerdictCache.kt`, `app/src/test/.../classifier/VerdictCacheTest.kt`

- [ ] Task: `DomainFilter` integration — `aiFilterEnabled`, new Reasons, DoH list, classifier step
  - Acceptance: unlisted adult domain blocked for kids via classifier; parent allow overrides; toggle off restores old behaviour; doh.txt + use-application-dns.net blocked; explain() covers new reasons
  - Verify: `./gradlew test --tests "com.tele.k_guard.vpn.VpnLogicTest"`
  - Files: `vpn/DomainFilter.kt`, `app/src/main/assets/blocklists/doh.txt`, `app/src/test/.../vpn/VpnLogicTest.kt`

- [ ] Task: `VpnConfigStore` persistence + shared AI flow
  - Acceptance: ai toggle persists, bumps cache generation, shared StateFlow hydrates like edu
  - Verify: compiles; covered indirectly via filter tests
  - Files: `vpn/VpnConfigStore.kt`

- [ ] Task: Web Protection UI toggle
  - Acceptance: AI filter switch in CategoriesTab; toggling reloads the screen filter for "Check a site"
  - Verify: `./gradlew :app:assembleDebug` + manual on device
  - Files: `ui/screens/WebProtectionScreen.kt`

- [ ] Task: Full verification
  - Acceptance: `./gradlew test` all green; assembleDebug succeeds
  - Verify: full test run + build
  - Files: —
