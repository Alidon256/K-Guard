# K-Guard — Agent Build Plan

You are the coding agent for **K-Guard**, a child-safety Android app built at the UCC Testbed Hackathon 2026 (Track 3: content safety and age-appropriate access). The event runs two days, and code freezes at **14:00 on Day 2**. Your human lead is Able. Build in small, verifiable steps and keep the app demo-ready after every milestone.

## 1. What we are building

A parent's phone is often shared with a child. K-Guard turns on **child mode**, which runs a local, DNS-only VPN on the phone. Every app and browser's DNS lookups pass through it, and for each domain K-Guard decides **Allow, Warn, Restrict or Block** based on the domain's category and the child's age tier. It also forces safe modes (Google SafeSearch, YouTube Restricted Mode, Bing strict, DuckDuckGo safe). Domains not on any list are sent (domain name only) to our AI classifier on the testbed, and the verdict applies from the next lookup. Children get a short, kind explanation as a notification, in English or Luganda.

Later phases add share-to-check, a notification shield, and a media watcher (section 9). Do not start them until Phase 1 is done.

## 2. Hard rules

- **Write all code yourself.** Never copy code from other DNS or VPN filter apps (Blokada, NetGuard, RethinkDNS, personalDNSfilter, Trawl or similar). Ideas are fine; code is not.
- **Declare everything.** Every library, model, list and AI tool (including you, the agent) goes in `docs/DECLARATIONS.md` with name, version, licence and purpose, at the moment you add it.
- **Allowed dependencies:** Kotlin stdlib, kotlinx-coroutines, kotlinx-serialization, AndroidX (core, lifecycle, activity, Compose, DataStore, WorkManager), OkHttp, and dnsjava. For domain registrable-part logic, use Guava's `InternetDomainName` (android flavour) or a bundled Public Suffix List file. Ask Able before adding anything else.
- **Privacy:**
  - Release builds store no domain history, only counts per category per day.
  - The only data that leaves the phone is a bare registrable domain (e.g. `example.co.ug`) sent to our classifier. Never send full URLs, device IDs, user IDs or the child's age.
  - Domain-level debug logging exists only in debug builds behind `BuildConfig.DEBUG`.
- **Content:** tests and demos use domain names and our own mock pages only. Never fetch, store or display real harmful content on the phone.
- **Ask Able first** before changing the API contract, file formats in section 6, or permissions beyond VPN, notifications and network.

## 3. Tech stack and project setup

- Kotlin, Jetpack Compose, Gradle Kotlin DSL, coroutines.
- Application ID `ug.kguard.app`. minSdk 26, target and compile against the latest stable SDK installed.
- Must run well on a 2 GB Android Go phone: no heavy libraries, low memory, low battery use.
- Repo layout (you work in `android/` and `docs/` only unless asked):

```
k-guard/
  android/            # this app
  api/                # testbed API (teammate)
  ml/                 # models and evaluation (teammate)
  docs/               # ARCHITECTURE.md, EVALUATION.md, SAFETY.md, DECLARATIONS.md
  README.md
```

## 4. Architecture

```
Apps and browsers ──DNS──▶ KGuardVpnService (TUN, DNS-only route)
                               │
                               ▼
                           DnsHandler ──▶ DomainMatcher (lists + allowlist)
                               │              │
                               │              ▼
                               │          PolicyEngine (category × age tier → decision)
                               │              │
                               ▼              ▼
                     UpstreamResolver     Notifier (EN / LG explanations)
                               │
                               ▼
                      ClassifierClient ──HTTPS/HTTP──▶ Testbed API (unknown domains only)
                               │
                               ▼
                         VerdictCache (memory LRU + DataStore, 24 h)
```

Suggested packages: `vpn`, `dns`, `policy`, `lists`, `classifier`, `notify`, `ui`, `data`.

## 5. Component specs

### 5.1 VPN service (DNS-only)

- `VpnService.Builder`:
  - `addAddress("10.215.173.1", 32)`
  - `addDnsServer("10.215.173.2")` and `addRoute("10.215.173.2", 32)`, so **only DNS** enters the tunnel. All other traffic goes direct.
  - `addDisallowedApplication(ownPackage)` so the app's own API calls and upstream DNS bypass the tunnel (no loops).
  - IPv4 only for the first version.
- Read packets from the TUN file descriptor on a coroutine. Parse the IPv4 and UDP headers by hand, handle UDP port 53 only, and drop anything else. Build replies with a correct IPv4 header checksum (a UDP checksum of 0 is valid for IPv4).
- Consent flow via `VpnService.prepare()`. Handle `onRevoke()` by notifying that protection stopped and incrementing a "protection turned off" counter.
- Show a persistent status notification while protection is on (the child must always see it). Check current Android docs for the foreground-service type rules that apply to VPN services on the target SDK, and follow them.
- In setup, guide the parent to Settings → VPN to turn on **Always-on VPN** and **Block connections without VPN** (`Settings.ACTION_VPN_SETTINGS`), so internet stops if protection is switched off.
- If the phone's Private DNS is set to a specific hostname (read `Settings.Global` `private_dns_mode` if available), warn the parent that it can bypass K-Guard and should be set to Off or Automatic.

### 5.2 DNS handling

- Use dnsjava to parse questions and build answers.
- **Upstream resolver:** capture the underlying network's DNS servers (`ConnectivityManager` → `LinkProperties.dnsServers`) before the VPN starts, and use them first. Fall back to `1.1.1.1`, then `8.8.8.8`. Make this configurable in settings. Timeout per query: 2 s, then try the next upstream, then reply SERVFAIL.
- **Blocked answer:** `A` → `0.0.0.0`, `AAAA` → `::`, other types → NXDOMAIN. TTL 60.
- **Safe-mode rewrites:** answer with a CNAME to the target plus the target's resolved records.
  - Google: `www.google.com`, `google.com` and country domains including `www.google.co.ug` → `forcesafesearch.google.com`
  - YouTube: `www.youtube.com`, `m.youtube.com`, `youtubei.googleapis.com`, `youtube.googleapis.com`, `www.youtube-nocookie.com` → `restrict.youtube.com` (under 16) or `restrictmoderate.youtube.com` (16–17)
  - Bing: `www.bing.com` → `strict.bing.com`
  - DuckDuckGo: `duckduckgo.com`, `www.duckduckgo.com` → `safe.duckduckgo.com`
- **Bypass hardening:** answer NXDOMAIN for `use-application-dns.net` (Firefox then disables its default encrypted DNS), and block the domains in `doh_bypass.txt`.
- **Answer cache:** LRU keyed by name and type, honouring TTL.
- **Unverified answers:** domains forwarded while their classification is pending get their TTL clamped to 30 s, so the verdict applies quickly.
- Record added latency per query in debug builds (p50 and p95, shown on the diagnostics screen).

### 5.3 Domain matching

- Lowercase, strip a trailing dot, then walk suffixes (`a.b.example.co.ug` → `b.example.co.ug` → `example.co.ug`) so subdomains inherit their parent's category.
- Precedence: `educational_allow` → `demo_hosts` → category lists → verdict cache → unknown.
- Domains in `infrastructure_skip.txt` (CDNs, OS services, analytics) are always allowed and never sent for classification.

### 5.4 Policy engine

- Load the matrix from `assets/policy.json` (format in section 6) so teammates can tune it without code changes.
- Default matrix:

| Category      | Under 13         | 13–15            | 16–17            |
| ------------- | ---------------- | ---------------- | ---------------- |
| ADULT         | BLOCK            | BLOCK            | BLOCK            |
| GAMBLING      | BLOCK            | BLOCK            | BLOCK            |
| SCAM          | BLOCK            | BLOCK            | BLOCK            |
| VIOLENCE_GORE | BLOCK            | RESTRICT         | WARN             |
| DRUGS_ALCOHOL | BLOCK            | RESTRICT         | WARN             |
| SOCIAL_MEDIA  | BLOCK            | ALLOW            | ALLOW            |
| EDUCATIONAL   | ALLOW            | ALLOW            | ALLOW            |
| SAFE          | ALLOW            | ALLOW            | ALLOW            |
| UNKNOWN       | ALLOW + classify | ALLOW + classify | ALLOW + classify |

- Gambling is blocked for every tier because betting is illegal under 25 in Uganda.
- Decisions at DNS level:
  - **ALLOW:** forward.
  - **WARN:** forward and send an explanation notification.
  - **RESTRICT:** block and send a notification with an "Ask a parent" action. A correct PIN allows that domain for 30 minutes.
  - **BLOCK:** block and send an explanation notification.
- Optional strict-unknowns switch for under-13s (off by default): unknown domains wait up to 1.5 s for a verdict, then are allowed and queued.

### 5.5 Classifier client

- `POST {BASE_URL}/v1/classify/domain` with the registrable domain only (contract in section 6). Timeout 2 s.
- Queue requests, de-duplicate in-flight domains, cap at 5 concurrent.
- Cache verdicts for 24 h in memory and DataStore.
- Never classify the API's own host.
- **Mock mode** (toggle in settings, on automatically when the API is unreachable): a clearly labelled `MockClassifier` that guesses from keywords in the domain (`bet`, `odds`, `casino`, `xxx`, `loan`). The diagnostics screen must show whether a verdict came from a list, the model, the cache or the mock.
- The testbed API may run on plain HTTP at an IP address. Add a network security config that allows cleartext **only** to that host.

### 5.6 Notifications and explanations

- One notification channel for explanations, throttled to one per registrable domain per hour.
- Text comes from string resources keyed by category and decision (section 6), in English (`values/`) and Luganda (`values-lg/`). Provide an in-app language picker, since few phones are set to Luganda.
- Tapping a notification opens an explanation screen with the message, "You did nothing wrong", a "Talk to someone you trust" note, and a tap-to-call button for the Sauti 116 child helpline.

### 5.7 UI (Compose)

1. **Setup:** create the parent PIN (hash with PBKDF2 and a random salt; never store it plain), pick the age tier and language, run VPN consent, then the Always-on VPN guidance.
2. **Home:** protection on or off, current tier, and today's counts per category. Starting is free; stopping or changing the tier needs the PIN.
3. **Explanation screen** (above).
4. **Settings (PIN):** API base URL, mock mode, upstream DNS, strict unknowns, language.
5. **Diagnostics (PIN):** type a domain to see its category, source, decision per tier, and lookup latency. Also a "run test list" button that pushes `assets/lists/selftest.txt` through the policy and shows a results table. This screen doubles as a demo and evaluation tool.

## 6. Shared formats (teammates depend on these; do not change without Able)

**Lists** — `android/app/src/main/assets/lists/*.txt`, one lowercase domain per line, no scheme or path, `#` for comments:
`adult.txt`, `gambling.txt`, `scam.txt`, `violence_gore.txt`, `drugs_alcohol.txt`, `social_media.txt`, `educational_allow.txt`, `infrastructure_skip.txt`, `doh_bypass.txt`, `selftest.txt` (format: `domain,expected_category`), and `demo_hosts.txt` (format: `domain ip`; maps demo domains to the testbed's mock sites; used only when demo mode is on).

**Policy** — `assets/policy.json`:

```json
{
  "version": "1",
  "tiers": ["UNDER_13", "AGE_13_15", "AGE_16_17"],
  "matrix": {
    "GAMBLING": {
      "UNDER_13": "BLOCK",
      "AGE_13_15": "BLOCK",
      "AGE_16_17": "BLOCK"
    }
  }
}
```

**Strings** — keys `explain_<category>_<decision>_title` and `explain_<category>_<decision>_body` (lowercase), in `values/strings.xml` and `values-lg/strings.xml`.

**Categories** — `ADULT, GAMBLING, SCAM, VIOLENCE_GORE, DRUGS_ALCOHOL, SOCIAL_MEDIA, EDUCATIONAL, SAFE, UNKNOWN`.

**API contract** (the testbed teammate implements it):

```
GET  /health
     → {"status":"ok","model_version":"..."}

POST /v1/classify/domain
     {"domain":"example.co.ug"}
     → {"domain":"example.co.ug","category":"GAMBLING","confidence":0.93,
        "source":"model","model_version":"..."}
     Any error or timeout → the client treats the domain as UNKNOWN.

POST /v1/check            (Phase 2, share-to-check)
     {"type":"text|url|image","content":"...","age_tier":"AGE_13_15"}
     → {"decision":"BLOCK","category":"GAMBLING","confidence":0.9,
        "explanation_key":"explain_gambling_block"}
```

## 7. Milestones (in order; each needs Able's sign-off before the next)

| #   | Milestone                    | Done when                                                                                                                   | Target                        |
| --- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ----------------------------- |
| M0  | Project skeleton             | Builds, installs and launches on the demo phone; unit tests run                                                             | Day 1, Sprint 1               |
| M1  | DNS pass-through VPN         | With protection on, browsing and apps work normally; debug log shows queries and added latency                              | Day 1, Sprint 1               |
| M2  | List blocking                | A listed domain and its subdomains fail to load; an `educational_allow` domain still loads                                  | Day 1, 14:00                  |
| M3  | Safe modes                   | Google shows SafeSearch locked; YouTube (app and browser) is in Restricted Mode                                             | Day 1, Sprint 2               |
| M4  | Policy, tiers, notifications | The same domain gets different decisions per tier; explanation notifications appear in EN and LG                            | Day 1, Sprint 2               |
| M5  | Classifier client            | An unknown domain triggers one API call; the next lookup enforces the verdict; API down falls back to mock without crashing | Day 1, 17:00                  |
| M6  | Child mode UI                | Setup, PIN-protected stop and settings, home counts, diagnostics screen                                                     | Day 1, 20:00                  |
| M7  | Hardening and docs           | Section 8 checks pass; README, ARCHITECTURE.md and DECLARATIONS.md complete                                                 | Day 2, 11:30 (feature freeze) |

If a milestone runs late, tell Able immediately and propose what to cut. Never leave `main` broken.

## 8. Testing and failure handling

- **Unit tests:** DNS packet parse and build, IPv4 checksum, suffix matching and precedence, policy matrix loading, safe-mode rewrite answers, PIN hashing.
- **Device checklist** (run on the actual demo phone): normal browsing with protection on; a listed betting domain blocked with a notification; SafeSearch locked; YouTube restricted; a new demo domain allowed on first visit, then blocked after classification; an educational health site never blocked; protection survives a Wi-Fi to mobile data switch and screen-off for 10 minutes.
- **Failures to handle:** upstream timeout (next upstream, then SERVFAIL); API down (mock mode plus cache, never a crash); malformed packets (drop and log in debug); VPN revoked (notify, count it); the app killed by the phone's battery saver (always-on VPN restarts it; show a battery-optimisation exemption prompt in setup).
- **Performance targets:** blocked or cached answers add under 50 ms at p95; forwarded answers add under 20 ms beyond upstream time.

## 9. Phase 2 (only after M7, or if Able says so)

1. **Share-to-check:** an `ACTION_SEND` target for text, links and images that calls `/v1/check` and shows the decision and explanation.
2. **Notification shield:** a `NotificationListenerService` that hides harmful message previews in child mode, using a text classifier from the ML teammate.
3. **Media watcher:** watch new images and videos via MediaStore and check them on-device (Marqo nsfw-image-detection-384 and TinyCLIP via ONNX Runtime Android), quarantining flagged files.
4. **Child-mode home screen:** an allowlist launcher that hides apps the parent hasn't approved.

## 10. How to work with Able

- Before each milestone, write a short plan (files to touch, risks). After it, report what changed, test results, and anything Able should check on the phone.
- Commit after each milestone with a message like `M3: safe-mode rewrites`, and push.
- When unsure about Android behaviour, check the current official docs rather than guessing, and say what you checked.
