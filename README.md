# K-Guard

**AI-powered protection for safer digital experiences.**

| DETECT | PROTECT | INTERVENE |
|---|---|---|
| Identify harmful content using AI. | Apply age-appropriate safety policies. | Warn, restrict and notify when necessary. |

**Event:** UCC Testbed Hackathon 2026 · **Track:** AI for Intelligent Online Content Safety and Age-Appropriate Access
**Platform:** Android (Kotlin, Jetpack Compose) · **Backend:** Firebase (Auth, Firestore, Storage, Cloud Functions, FCM)
**Firebase project:** `k-guarducc` · **Application ID:** `com.tele.k_guard`

> This document is the build plan. It describes what exists today, what is missing, and exactly what
> will be implemented. The earlier prototype notes are kept in `README_K-Guard.md`.

---

## 1. Product scope (from the track brief)

### Feature 1 — AI-powered content detection and filtering
- Analyse text and images for harmful or age-inappropriate content.
- Detect cyberbullying, threats, violence, sexual content, self-harm, hate, drugs and other categories.
- Blur or restrict inappropriate content.
- Allow safe and educational content.
- Explain every moderation decision.

### Feature 2 — Age-aware access and personalised protection
- Child profiles, each with an age group.
- Restrictions that differ by age.
- Parents choose which categories are acceptable, per child.
- Educational discussion is told apart from genuinely harmful content.
- Warnings and restrictions adapt to the profile.

### Feature 3 — Real-time safety alerts and intervention
- Harmful content is identified during supported checking workflows.
- A warning or blurred preview appears immediately.
- Important moderation events are recorded.
- Parents are notified of significant incidents, subject to their privacy settings.
- Parents can review flagged content and act on it.

### Honest scope limits
- K-Guard moderates content that is **submitted to K-Guard** (text checker, image checker, shared-in content).
  It does not claim to read inside WhatsApp, TikTok or a browser. Android does not allow that without
  Accessibility/VPN integrations, which need their own review and consent flow.
- K-Guard is **not** built by decompiling Google Family Link. The features above are fully specified by the
  brief, so they are implemented from that specification. See section 9 for how the `rea` tool is set up and
  what it is (and is not) used for.
- Moderation is probabilistic. K-Guard never claims content is "guaranteed safe". When the AI service cannot
  answer, the content is held for review. It is never silently approved.

---

## 2. Current state vs. gaps

| Area | Exists today | Gap to close |
|---|---|---|
| Onboarding, theme, navigation | Yes | Add auth gate and role routing |
| Age profile | One device-level profile in DataStore | **Multiple child profiles**, stored per family in Firestore |
| Text checker | UI + Retrofit call | **Fails open**: on any error it returns a mock result. Must fail closed (REVIEW) |
| Image checker | UI + Retrofit call | Same fail-open problem. No Storage upload, no safe blurred preview |
| Moderation API | Retrofit to `10.0.2.2:8080` | No deployed backend. Replace with Cloud Function proxy |
| Category settings | None | **Per-child parent-configurable category policy** |
| Educational vs harmful | None | Context-aware decision in the backend |
| Alerts | In-memory fake list | **Persisted events + FCM push to parents** |
| Parent review | Mark-as-reviewed only | Review queue with Allow / Keep blocked / Dismiss and a note |
| Privacy settings | None | Notification threshold, snippet retention, quiet hours |
| Auth | Dependencies only | Google sign-in, parent and child roles |
| Parent protection | None | Parent PIN gate for settings |
| Screen time, app limits | In-memory demo data | Persist to Firestore (kept, secondary to the three core features) |
| Firebase config | `google-services.json` only | `firebase.json`, rules, indexes, Functions, deployed |

---

## 3. Architecture

```
 Android (Compose + ViewModel + Repository)
    │  Firebase Auth (Google)          │  Firestore (live listeners)     │  FCM (push)
    ▼                                  ▼                                  ▲
 Callable Cloud Function  ──►  moderate(text | image, childId)  ──►  events + notify
    │                               │
    │                               ├─ 1. External AI moderation API (MODERATION_API_URL secret), if configured
    │                               ├─ 2. Built-in rule engine (offline fallback, clearly labelled source: "rules")
    │                               └─ 3. Policy engine: category scores + child policy + age profile
    ▼                                       → ALLOW | WARN | BLUR | BLOCK | REVIEW + explanation
 Cloud Storage: users/{uid}/uploads/…  (private, short retention)
```

Why a Cloud Function in front of the AI service:
- The API key and URL stay on the server, not in the APK.
- Age policy and per-child categories are enforced in one place, so the app cannot be tricked into a looser decision.
- Events and parent notifications are written server-side, so a child's device cannot suppress them.

### Layers (Android)
`ui` (Compose) → `viewmodel` → `data/repository` → `data/remote` (Functions, Firestore, Storage) and `data/local` (DataStore).

---

## 4. Moderation model

### Categories
`cyberbullying`, `threat`, `violence`, `sexual`, `self_harm`, `hate`, `drugs`, `profanity`, `scam`, `personal_info`, `other`.

### Decisions
| Decision | Meaning | UI |
|---|---|---|
| `ALLOW` | Suitable under this child's policy | Shown normally |
| `WARN` | Borderline | Warning banner, child can continue |
| `BLUR` | Sensitive | Blurred preview, tap to reveal only if age profile allows |
| `BLOCK` | Not suitable | Content withheld, explanation shown |
| `REVIEW` | Uncertain or service unavailable | Held until a parent decides |

### Policy resolution (server side)
1. The AI (or fallback rules) returns a score per category plus a `context` hint: `harmful`, `educational`, `news`, `fiction`, `unclear`.
2. The age profile sets default thresholds. The child's parent-set category rules can only make a category **stricter or looser within the age profile's ceiling**:
   - `KIDS_3_7`: lowest thresholds, no tap-to-reveal.
   - `PRETEENS_8_12`: balanced.
   - `TEENS_13_17`: severe risks and explicit content; educational and news context allowed.
   - `ADULTS_18_PLUS`: standard moderation, illegal and hazardous content flagged.
3. If `context` is `educational`/`news` and the category is not `sexual` with a minor, `threat` directed at a person, or `self_harm` instructions, the decision is lowered one step and the explanation says why.
4. Any timeout, error or missing score produces `REVIEW`, never `ALLOW`.

### Response contract (callable `moderateContent`)
```json
{
  "decision": "BLUR",
  "category": "violence",
  "riskScore": 0.81,
  "context": "news",
  "ageAppropriate": false,
  "explanation": "Graphic violence in a news setting. Blurred for ages 8–12.",
  "categories": { "violence": 0.81, "threat": 0.04 },
  "source": "ai|rules",
  "eventId": "…"
}
```

---

## 5. Firestore data model

```
users/{uid}                      role: "parent"|"child", displayName, familyId, fcmTokens[]
families/{familyId}              ownerUid, parentUids[], privacy{…}, createdAt
  children/{childId}             name, ageProfile, linkedUid?, categoryRules{category: "block"|"blur"|"warn"|"allow"},
                                 allowReveal, createdAt
  events/{eventId}               childId, contentType, category, riskScore, decision, context, explanation,
                                 snippet?, storagePath?, status: "open"|"reviewed", parentAction?, parentNote?,
                                 notified, createdAt, reviewedAt?
  apps/{appId}                   childId, packageName, status, dailyLimitMinutes
  screenTime/{childId}           dailyLimitMinutes, bedtime, school hours
  pinHash (in families doc)      salted hash of the parent PIN, never the PIN
```

`privacy` (per family): `notifyOn: "all"|"warn_and_above"|"block_only"`, `includeSnippet: bool`, `retainDays: 7|30|90`,
`quietHours{start,end}`. If `includeSnippet` is false, text and image references are not stored on the event or sent in the push.

### Security rules (summary)
- Only signed-in users; every document is reached through `familyId`.
- Parents (`parentUids`) can read and write their family. Children can read only their own profile and write **nothing** to `events`
  (events are written by Functions with the Admin SDK).
- `events`: parents may update only `status`, `parentAction`, `parentNote`, `reviewedAt`.
- Storage: users may write only under their own `users/{uid}/uploads/`, images only, ≤ 8 MB; reads are limited to the owner and family parents.

### Indexes
- `events`: `(status ASC, createdAt DESC)`, `(childId ASC, createdAt DESC)`, `(decision ASC, createdAt DESC)`.
- `children`: `(createdAt ASC)`.

---

## 6. Cloud Functions (TypeScript, Node 22, 2nd gen)

| Function | Type | Purpose |
|---|---|---|
| `moderateContent` | callable | Text/image moderation, policy resolution, event write, parent notification |
| `createFamily` | callable | Creates family on first parent sign-in, sets custom claim role |
| `linkChild` | callable | Generates a short-lived pairing code; child device redeems it |
| `onEventCreated` | Firestore trigger | Sends FCM push to parents per privacy settings and quiet hours |
| `purgeOldEvents` | scheduled | Deletes events and uploads past each family's `retainDays` |

Config: `MODERATION_API_URL`, `MODERATION_API_KEY` read from `functions/.env.k-guarducc` (git-ignored). Not committed.
Note: deploying Functions needs the project on the **Blaze** plan. Rules and indexes deploy on any plan.

---

## 7. Android implementation plan

### New and changed screens
1. **Sign-in** (Google via Credential Manager) and role choice (parent / child device).
2. **Family dashboard** (parent): children list, open alerts count, quick actions.
3. **Child profile editor**: name, age group, **category rules** (Block / Blur / Warn / Allow per category), reveal permission.
4. **Safety checker** (text and image): fixed to call `moderateContent`, with loading, empty and error states, and a **fail-closed** result.
5. **Result view**: decision badge, explanation, blurred preview with age-gated reveal, "Ask a parent to review".
6. **Alerts / Review queue** (parent): filters by child and decision; Allow / Keep blocked / Dismiss with note.
7. **Privacy and notifications**: threshold, snippet storage, retention, quiet hours.
8. **Parent PIN**: set and verify before opening parent-only settings.
9. Existing screen-time and app screens read and write Firestore instead of in-memory demo data.

### Code structure (additions)
```
data/model/        ChildProfile, FamilySettings, SafetyEvent, CategoryRule, ModerationResult
data/remote/       FunctionsModerationService (replaces Retrofit mock fallback)
data/repository/   AuthRepository, FamilyRepository, EventRepository, ModerationRepository (rewritten)
service/           KGuardMessagingService (FCM)
viewmodel/         AuthViewModel, FamilyViewModel, ChildEditorViewModel, ReviewQueueViewModel, PrivacyViewModel
ui/screens/        SignInScreen, FamilyDashboardScreen, ChildEditorScreen, ReviewQueueScreen, PrivacySettingsScreen, PinScreen
```

### Dependencies to add
`firebase-messaging`; `kotlinx-coroutines-play-services` (for `await()`); Retrofit remains only if needed for a direct API. `androidx.biometric` is optional for PIN.

### Permissions
`INTERNET`, `POST_NOTIFICATIONS` (Android 13+, requested at the moment of first use). No other permissions.

---

## 8. Privacy and safety commitments
- Collect the minimum. Snippets are stored only if the parent enables it, and are purged on schedule.
- Children are told when parents can see an alert (transparency screen at pairing).
- Never log submitted text or image contents.
- Fail closed: no answer means `REVIEW`.
- No absolute claims in the UI. Wording is "likely", "flagged", "held for review".

---

## 9. About `rea` (morluto/rea)

`rea` is an MCP server for reverse-engineering binaries and apps. It was cloned for inspection and the
setup is documented here. Points to be clear about:
- The three features in section 1 are specified in the brief, so no decompilation is needed to build them.
- Decompiling Google's proprietary Family Link app to copy its implementation would likely breach its terms and copyright, so it is not part of this plan.
- `rea` may be used for legitimate analysis: inspecting **K-Guard's own APK** (verify no secrets or logging leak, check exported components), and reviewing open-source or properly licensed references.
- Its requirement is Node.js `22.x >= 22.23.4` or `24.x >= 24.22.1`. This machine has Node 22.19.0, so upgrade Node before running `npx rea-agents setup`.
  Setup is interactive and edits the global agent configuration, so it is left for you to run: `npx rea-agents setup`.

---

## 10. Build order and acceptance

| # | Milestone | Done when |
|---|---|---|
| 1 | Firebase project files | `firebase.json`, `.firebaserc`, rules, indexes in repo |
| 2 | Rules and indexes deployed | `firebase deploy --only firestore,storage` succeeds |
| 3 | Cloud Functions written and type-checked | `npm run build` passes in `functions/` |
| 4 | Functions deployed | Needs Blaze plan; otherwise documented as pending |
| 5 | Android data layer | Models, repositories compile |
| 6 | Auth, family, child profiles, category rules | Parent can add a child and set rules |
| 7 | Moderation flow rewired and fail-closed | Network off → result is `REVIEW`, not `ALLOW` |
| 8 | Alerts, review queue, FCM | Flagged event reaches parent device and can be actioned |
| 9 | Privacy settings and PIN | Settings change notification behaviour |
| 10 | Debug build and unit tests | `./gradlew assembleDebug test` pass |

### Build and deploy commands
```powershell
# Android
.\gradlew.bat assembleDebug
.\gradlew.bat test

# Firebase
firebase use k-guarducc
firebase deploy --only firestore:rules,firestore:indexes,storage
cd functions; npm install; npm run build; cd ..
firebase functions:secrets:set MODERATION_API_KEY
firebase deploy --only functions
```

---

## 11. Implementation status (updated 2026-11-05)

| # | Milestone | Status |
|---|---|---|
| 1 | Firebase project files | Done |
| 2 | Firestore rules, indexes, Storage rules | **Deployed** to `k-guarducc` |
| 3 | Cloud Functions written, type-checked, 9 policy tests pass | Done |
| 4 | Functions deployed | **Blocked**: the CLI account is not a project owner and cannot grant the IAM roles Cloud Functions needs (see below) |
| 5 | Android data layer | Done |
| 6 | Auth (email/password), family, child profiles, category rules | Done |
| 7 | Moderation rewired, fail-closed, blur works on Android < 12 | Done |
| 8 | Alerts, review queue, FCM push | Done in app and functions; needs milestone 4 to run end to end |
| 9 | Privacy settings | Done. **Parent PIN not built yet** |
| 10 | `assembleDebug` and `test` | Pass |

Not built: parent PIN, child-device pairing screen (the `createPairingCode` / `redeemPairingCode` functions exist, the app UI does not),
and persisting screen time / app limits to Firestore (they remain in-memory demo data).

### To finish deployment
A project **Owner** runs these once, then `firebase deploy --only functions`:
```
gcloud projects add-iam-policy-binding k-guarducc --member=serviceAccount:service-806029279640@gcp-sa-pubsub.iam.gserviceaccount.com --role=roles/iam.serviceAccountTokenCreator
gcloud projects add-iam-policy-binding k-guarducc --member=serviceAccount:806029279640-compute@developer.gserviceaccount.com --role=roles/run.invoker
gcloud projects add-iam-policy-binding k-guarducc --member=serviceAccount:806029279640-compute@developer.gserviceaccount.com --role=roles/eventarc.eventReceiver
```
Also enable **Authentication > Email/Password** in the Firebase console. To use the team's AI service, create
`functions/.env.k-guarducc` containing `MODERATION_API_URL=` and `MODERATION_API_KEY=` (git-ignored). Without it, text uses the
built-in rule engine and images return `REVIEW`.
Config note: functions run on Node 22 (Node 20 was decommissioned on 2026-10-30), and the project Kotlin version is now 2.4.21,
which the current Firebase Auth library requires.

---

## 12. Web Protection (on-device VPN)

**What it is.** A local `VpnService` that captures only DNS lookups (it routes a single virtual DNS address, not all traffic).
Each lookup is checked against the child's age profile and category rules. Harmful domains get an NXDOMAIN answer, so the site never
loads in any browser or app. Allowed lookups are forwarded unchanged to a public resolver. No traffic is proxied through a K-Guard server.

**Policy.** Domain lists per category (`assets/blocklists/<category>.txt`, one domain per line, subdomains match).
Defaults: ages 3–12 block every listed category; teens block sexual, drugs, hate, violence, scam; adults block scam.
Parent rules apply: `Block` always blocks. `Allow` is honoured except for sexual content under 18. `Warn`/`Blur` cannot be shown at DNS level, so they behave as allow.
Parents may also add custom blocked domains.

**Safe modes.** For profiles under 18, lookups for Google (including country domains such as `google.co.ug`), YouTube, Bing and DuckDuckGo are answered with a CNAME to the vendor's own enforcement host, so SafeSearch and YouTube Restricted Mode are locked on in every browser and app: `forcesafesearch.google.com`, `restrict.youtube.com`, `strict.bing.com` and `safe.duckduckgo.com`. The CNAME is followed by the target's real addresses, resolved upstream. HTTPS/SVCB lookups for those names get an empty answer so a browser cannot skip the rewrite, and if the safe host cannot be resolved the lookup goes unanswered instead of falling back to the normal site. Adult profiles are not rewritten. YouTube always uses strict Restricted Mode, because the teen profile spans 13 to 17. Code: `vpn/SafeSearch.kt` and the CNAME helpers in `vpn/DnsPacket.kt`. Browsers with their own encrypted DNS can still bypass it, as noted below.

**Intervene.** A blocked lookup is reported through the `reportWebBlock` function, which records an event and notifies parents per their
privacy settings. The domain is stored only if "store excerpts" is on. Reports are throttled per domain.

**Limits (shown to the parent in the app).**
- Domain-level only. It cannot see page text, images or in-app content. Text and image checking stays in the checkers.
- Apps or browsers using their own encrypted DNS (DoH) or strict Private DNS can bypass it. Parents should turn off "Secure DNS" in the browser.
- The bundled lists are a **small starter set**. A maintained, licensed blocklist feed should be dropped into `assets/blocklists/` before real use.
- A child who is not locked out of Settings can switch the VPN off. Device-owner/lock features and the parent PIN are future work.
- Android requires a persistent notification and the user's one-time VPN consent.

**Code.** `vpn/DnsPacket.kt` (packet parsing/building), `vpn/DomainFilter.kt` (policy), `vpn/KGuardVpnService.kt`,
`vpn/VpnConfigStore.kt`, `vpn/WebBlockReporter.kt`, `ui/screens/WebProtectionScreen.kt`, function `reportWebBlock`. Unit tests cover the filter and packet code.

---

**K-Guard** — Detect. Protect. Intervene.
