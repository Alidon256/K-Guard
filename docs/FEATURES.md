# K-Guard — Feature Documentation

K-Guard is a two-sided child-safety platform: a **parent app** for supervision and control, and a
**child-device agent** for enforcement, backed by Firebase (Auth, Firestore, Cloud Functions, FCM,
Storage). One install becomes either side based on the role chosen at sign-in.

- **Package:** `com.tele.k_guard` · Android (Jetpack Compose), minSdk 24, targetSdk 35
- **Backend:** Firebase Cloud Functions (`functions/src/index.ts`), Firestore, FCM
- **Build:** `./gradlew :app:assembleDebug` / `:app:assembleRelease` · Tests: `./gradlew :app:testDebugUnitTest` (78 unit tests)

---

## 1. Accounts & Pairing

| Feature | How it works |
|---|---|
| **Parent sign-in (Google-only)** | One-tap Google via Credential Manager → Firebase Auth. No email/password anywhere in the app. |
| **Child device pairing — no account needed** | The parent generates a 6-digit code in the child editor; the child's phone needs **no Google account**: the app creates an anonymous device identity and the code binds it to the child profile (`redeemPairingCode` Cloud Function). |
| **Role separation** | `users/{uid}` carries `role: parent|child` + `familyId`; every Cloud Function validates role server-side. Parent accounts can never redeem child pairing codes and vice-versa. |
| **First-run onboarding** | Welcome screen (features + how-it-works) before sign-in. |

**Setup flows**
- *Parent:* Onboarding → Google sign-in → Home dashboard (family is auto-created server-side on first sign-in).
- *Child:* Onboarding → "Child device" → enter 6-digit code → set uninstall PIN → turn on uninstall protection → child dashboard.

## 2. Child Profiles

- Create/edit child profiles: name, email, photo (on-device picker), age profile.
- **Age profiles (4 tiers):** Kids 3–7 · Pre-Teens 8–12 · Teens 13–17 · Adults 18+ — every protection keys off this.
- **Per-category web rules:** 10 safety categories (cyberbullying, threat, violence, sexual, self-harm, hate, drugs, profanity, scam, personal info) with parent rules Block / Warn / Blur / Allow / Default. Sexual content is **locked against relaxing under 18** server-side and client-side.
- **Reveal control:** parents decide whether blurred content can be revealed on the child's device (disabled for younger profiles).
- **Pairing:** generates the 6-digit code that links a child device (status shown as Paired / Not linked).

## 3. AI Content Moderation (text & images)

- **Text moderation:** the child submits text; the backend (Cloud Function `moderateContent`) analyses it and returns a category, a risk score, and a decision.
- **Image moderation:** images (≤ 8 MB) are uploaded to Storage and analysed by the same pipeline.
- **Engine:** Google Cloud Natural Language `moderateText` + Cloud Vision SafeSearch, with a keyword rule-engine fallback so moderation still works if the paid APIs are unavailable. **Fail-closed:** an unreachable backend returns REVIEW, never ALLOW.
- **Decisions:** ALLOW · WARN · BLUR · BLOCK · REVIEW — mapped through **age-aware thresholds** (e.g. block ≥ 0.50 for kids vs ≥ 0.92 for adults) and the parent's per-category rules.
- **Context softening:** educational/news/fiction context can soften WARN/BLUR (never for kids 3–7); sexual/threat/self-harm are **never softened**.
- **Reveal flow:** blurred content can be revealed in-app when the parent allows it.

## 4. Web Protection (on-device VPN filter)

A foreground `VpnService` routes the device's DNS through K-Guard; every lookup is filtered locally.

- **Category blocklists** per age profile (shipped in assets, one domain per line, wildcard support).
- **Local AI domain classifier** — sites on *no* list are judged **on-device** (offline, milliseconds) by a pattern scorer (`vpn/classifier/`): keyword weights per category, benign-vocabulary damping, punycode detection, per-age risk thresholds mirroring the server's policy. Verdicts are cached (24h TTL, auto-invalidated when the age profile or rules change). A parent toggle turns the AI filter off; a swappable `DomainClassifier` interface is ready for a trained ONNX model later.
- **Parent overrides always win:** custom allow/block lists, per-category rules, and the educational allowlist.
- **Safe-search enforcement:** Google/Bing/YouTube lookups are transparently rewritten to their safe-mode hosts.
- **"Check a site" dry-run:** parents can test any domain and see the exact reason for its verdict.
- **Usage stats:** daily query/blocked counts, flagged-domain breakdown.
- **Encrypted-DNS hardening (partial):** Android Private DNS is disabled via the `use-application-dns.net` NXDOMAIN trick and major public DoH resolvers are hard-blocked (not overridable). Full DoH coverage is a planned SNI-interception phase (see "Known gaps").

## 5. Screen Time & Remote Control

- **Device telemetry:** live card with online/offline state, battery, charging, network, pickups — streamed from `families/{id}/devices/{childId}`.
- **Remote switch off & lock:** the parent sends a command with a reason; the child's phone shows a fullscreen lock screen (reason card, emergency-call button, parent-PIN unlock with 5-attempt/30-s lockout). Delivered via an `onCommandCreated` Cloud Function trigger → high-priority FCM data push → `DeviceLockActivity`.
- **Remote unlock:** closes the lock screen without the PIN (parent-initiated).
- **Ring alarm:** plays the device alarm tone at max volume for ~15 s (find-the-phone).
- **Bonus time:** +15/+30 min grants applied as an atomic server-side increment (no lost updates under double-taps).
- **Daily limit:** slider with age-based recommended defaults; enforced status shown on the child dashboard.
- **Schedules:** bedtime and school-focus windows (start/end/enabled) pushed to the device.
- **Command audit log:** every command and its delivery status (`pushedAt`, `deliveryError`) is visible to the parent — real status, never fake success.

## 6. App Management (install approvals)

- **Child requests an app** (presets or custom name/package): lands in the parent's queue.
- **Parent approves / declines**, optionally with a per-app daily limit; the child gets an approval push.
- **Safety score & reason** per app (AI-assisted), status tags (Approved / Pending / Rejected), revoke support.
- Install detection on the child device via `PACKAGE_ADDED` broadcast.

## 7. Alerts & Review Queue

- Every moderation/web block can create a `SafetyEvent` that lands in the parent's **review queue**.
- **Parent actions:** Allow once · Keep blocked · Dismiss — each with an optional note; audit trail on the event.
- **Open-alert badge** on the dashboard (count from a dedicated listener, includes older open events).
- **Privacy controls:** notify-on level (all / warn+ / block-only), quiet hours with cross-midnight windows, snippet inclusion toggle (flagged content hidden from notifications unless allowed), push-token registration per device.

## 8. System-Level Protection (child device)

- **Uninstall protection (device administrator):** activation is a dedicated setup step; while active, **Android itself blocks uninstalling K-Guard**, and remote lock is enabled. The disable-request dialog carries an explicit deterrent message; deactivation triggers a tamper alert and re-enable nagging.
- **Tamper watchdog:** AlarmManager cycle (every 15 min, re-armed at boot and on app open) that restarts the VPN service if it was killed while protection is on, and rate-limits a "protection is weakened" notification until device admin is re-enabled.
- **Boot persistence:** web protection restarts after reboot when the parent left it on.
- **Uninstall PIN:** PBKDF2-hashed PIN required to unlock the device; raw PIN never stored.
- **Honest limit:** a non-device-owner app can still be deactivated via system settings; the watchdog + nag + tamper alerts are the counterweight (device-owner/MDM provisioning is the future hard path).

## 9. Child Experience

- Calm, friendly child dashboard ("You're protected…") with protection facts and live status.
- Fullscreen lock overlay designed for night use (emergency call always reachable).
- PIN setup with strength feedback; uninstall-protection activation step.

---

## Known gaps (tracked, not shipped)

| Gap | Status |
|---|---|
| Full DoH bypass coverage | Needs TCP/443 routing + TLS SNI sniffing (planned phase 2) |
| Child photo cross-device sync | Photos stay local; needs Storage upload/download |
| DST edge in quiet hours | Frozen `utcOffsetMinutes` gives wrong windows across DST transitions |
| Pairing-code brute-force limiting | 6-digit codes are rate-limitable server-side; attempt counter recommended before rollout |
| Command "Executed" acknowledgement | Child currently does not write back execution status |
| Google sign-in certificates | Requires the release/debug fingerprints to be registered in the Firebase project (pending) |

## Repository map

```
app/src/main/java/com/tele/k_guard/
  ui/            screens (17), navigation, components (KG kit), theme (light-only brand)
  viewmodel/     SessionViewModel (auth/family), ScreenTime, AppManagement, moderation VMs
  data/          model/, repository/ (Family, ParentalControls, Moderation, UserPreferences)
  vpn/           KGuardVpnService, DomainFilter, classifier/ (on-device AI), config/stats
  protection/    DeviceProtection core + tamper watchdog
  receiver/      device admin, app-install detection
  service/       FCM messaging (parent alerts + child command execution)
functions/src/   Cloud Functions: family/pairing, moderation, approvals, web-block reports,
                 command delivery (onCommandCreated), notification fan-out, scheduled purge
```
