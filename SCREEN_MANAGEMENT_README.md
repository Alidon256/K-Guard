# K-Guard 🛡️ Screen Management & Remote Device Control Architecture
### Production-Grade Parent-Child Screen Time, Device Lock, and Schedule Enforcement

---

## 1. Executive Summary

In parental control software, **Screen Management** is a safety-critical subsystem. It governs:
1. **Parent-Initiated Remote Control:** Instant screen switch-off, whole-device timed screen locks (hours + minutes), loud ring alert/find phone, and on-demand bonus time grants.
2. **Scheduled Downtime & Focus Regimes:** Overnight bedtime lockouts (locking the whole phone hardware and display, not merely blocking individual apps) and school-hours focus modes.
3. **Daily Screen Limits:** Operating-system-level tracking of active device time against pediatric-recommended thresholds, terminating with strict whole-device lockouts when limits are exhausted.
4. **App Access Interception:** Instant interception and lockout of unapproved, age-inappropriate, or time-restricted applications.

This document identifies all architectural, security, and lifecycle deficiencies that made previous implementations **non-production grade**, establishes a robust zero-trust architecture, and details the production implementation deployed across both parent and child devices—integrating the battle-tested whole-screen locking principles of **KidSafe** (`KidSafe-master`) translated to 100% idiomatic Kotlin, Jetpack Compose, Coroutines, and modern Android 14+ standards.

---

## 2. Comprehensive Audit: What Was NOT Production Grade

### Part A: Child Device Vulnerabilities & Deficiencies

1. **No Real-Time Command Execution (Dead-End Commands):**
   * **Flaw:** When parents triggered "Switch Off Screen & Lock Remotely", "Unlock", or "Play Loud Ring Alert" from the Parent Dashboard, commands were written to Firestore, but the child device had **no listener** attached to `families/$familyId/devices/$childId` or `families/$familyId/commands`.
   * **Consequence:** Remote lockouts, schedule changes, and alarm chimes were complete no-ops in production.

2. **Insecure & Trivial Lock Screen Bypass in `DeviceLockActivity`:**
   * **Flaw:** Line 102 of `DeviceLockActivity.kt` contained a hardcoded backdoor:
     ```kotlin
     if (pin == "1234" || pin.length == 4) { finish(); true }
     ```
     Any 4-digit code (e.g., `0000`) instantly unlocked the device. It ignored the parent's actual encrypted PIN saved in `UserPreferencesRepository`.
   * **Flaw:** No back gesture/button interception (`onBackPressedDispatcher`). A child could simply swipe back or tap the Android system back button to dismiss the lock screen and continue using the phone.
   * **Flaw:** No task/recents protection (`onUserLeaveHint`). A child could hit Home or Recent Apps to switch away from the lock screen.
   * **Flaw:** No auto-dismiss on remote unlock. When a parent unlocked the device from their phone, the child screen remained stuck on the lock screen until manual intervention.

3. **Bedtime Only Blocked Individual Apps Rather than the Whole Phone:**
   * **Flaw:** Bedtime downtime was treated as an application-level filter rather than a full hardware-and-display phone lock.
   * **Consequence:** The child could still access system settings, phone dialer, launchers, notification panels, and quick settings, completely defeating parental sleep schedules.

4. **Missing Hardware Screen-Off via Android Device Administrator:**
   * **Flaw:** `KGuardDeviceAdminReceiver` was declared in `AndroidManifest.xml` with `<force-lock />`, but `DevicePolicyManager.lockNow()` was **never called** in the codebase.
   * **Consequence:** The screen was never physically turned off at the OS keyguard level.

5. **Reboot Bypass (No Boot Complete Receiver):**
   * **Flaw:** The service did not register for `ACTION_BOOT_COMPLETED`.
   * **Consequence:** A child could restart the phone during bedtime or remote lock to boot directly into an unlocked home screen without active enforcement.

6. **Lifecycle Flaw: Screen Enforcement Bound to UI (Compose):**
   * **Flaw:** Telemetry synchronization and app inspection was placed inside a Compose `LaunchedEffect` in `ChildDashboardScreen.kt`.
   * **Consequence:** As soon as the child switched to another app (e.g., TikTok, YouTube, Roblox), Compose paused or was destroyed by the Android OS, halting telemetry and enforcement.

7. **Quota-Exhausting Firestore Write Spam:**
   * **Flaw:** `KGuardCompanionService` wrote to Firestore every 5 seconds (`checkCounter % 5 == 0`), causing **720 writes/hour/device**.
   * **Flaw:** `OsUsageTracker.syncDeviceTelemetryToFirebase` performed batch writes of 30 separate documents to `families/$familyId/apps/` every 30 seconds, generating **3,600 document writes/hour**.
   * **Consequence:** Rapid exhaustion of Firebase free/paid quotas and massive battery drain on the child's device.

8. **Un-Enforced Schedules & Daily Limits:**
   * **Flaw:** Bedtime start/end times (`bedtimeStart`, `bedtimeEnd`), school mode hours (`schoolStart`, `schoolEnd`), and `dailyLimitMinutes` were stored as static text fields without any evaluation engine comparing them to system time or usage.

9. **Missing Audible Alert Engine for "Sound Alarm":**
   * **Flaw:** No audio playback mechanism (`RingtoneManager` or `MediaPlayer` on `STREAM_ALARM`) existed on the child device to play the loud alarm triggered by the parent.

---

### Part B: Parent Device Vulnerabilities & Deficiencies

1. **False Delivery Affirmation ("Ghost Deliveries"):**
   * **Flaw:** When sending a command, the parent app instantly created a log entry with status `"Delivered & Applied"` without awaiting child acknowledgement or device ping.

2. **Hardcoded Initial Child Fallback & Stale State:**
   * **Flaw:** `ScreenTimeViewModel` hardcoded `_selectedChildId = "c1"` ("Ama"), causing UI glitches and stale data when real families loaded with different child IDs.

3. **Lack of Child Permission Observability:**
   * **Flaw:** The parent had no visibility into whether the child had granted Device Administrator, Usage Stats, or Notification permissions, leading to silent enforcement failures.

4. **Missing Granular Timed Lock Logic:**
   * **Flaw:** Parents could not specify a timed lock (e.g., "Lock for 2 hours and 30 minutes") matching KidSafe's `ScreenLock` model.

---

## 3. Production-Grade Architecture & KidSafe Integration

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   PARENT CONTROLLER                                    │
│                                                                                        │
│   ┌────────────────────────────────┐         ┌─────────────────────────────────────┐   │
│   │     ScreenTimeScreen (UI)      │         │     ScreenTimeViewModel             │   │
│   │  - Live Telemetry Badges       │◄───────►│  - Remote Command Dispatcher       │   │
│   │  - Device Admin Health Check   │         │  - Real-Time Device Telemetry Flow  │   │
│   │  - Instant Switch-Off / Unlock │         │  - Timed Lock Dialog (KidSafe model)│   │
│   │  - Bonus Time & Limit Sliders  │         └──────────────────┬──────────────────┘   │
│   └────────────────────────────────┘                            │                      │
└─────────────────────────────────────────────────────────────────┼──────────────────────┘
                                                                  │ Writes / Updates
                                                                  ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                FIRESTORE BACKEND                                       │
│                                                                                        │
│   ┌───────────────────────────────────┐      ┌─────────────────────────────────────┐   │
│   │ families/{familyId}/devices/{cId} │      │ families/{familyId}/commands/{cmd}  │   │
│   │  - lockState: REMOTE_LOCKED / ... │      │  - action: SWITCH_OFF_LOCK / ...    │   │
│   │  - screenLock: {hours, minutes...}│      │  - status: PENDING -> EXECUTED      │   │
│   │  - dailyLimitMinutes / bedtime    │      └─────────────────────────────────────┘   │
│   │  - appUsages (aggregate array)    │                                                │
│   └─────────────────▲─────────────────┘                                                │
└─────────────────────┼──────────────────────────────────────────────────────────────────┘
                      │ Real-Time Snapshot Listener & Throttled Sync
┌─────────────────────┼──────────────────────────────────────────────────────────────────┐
│                     ▼             CHILD ENFORCEMENT ENGINE                             │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                 KGuardCompanionService (Foreground Service)                     │   │
│   │  - Command Listener: Executes SWITCH_OFF_LOCK, UNLOCK, ALARM, BONUS           │   │
│   │  - Locker Loop (1s): Anti-bypass continuous check (KidSafe LockerThread)       │   │
│   │  - Schedule Loop (5s): Bedtime (whole screen) & Daily Limits                   │   │
│   │  - Telemetry Sync (60s): Battery, Active App, Screen Time                      │   │
│   └─────────────────┬───────────────────────────────┬──────────────────────────────┘   │
│                     │                               │                                  │
│                     ▼                               ▼                                  │
│   ┌─────────────────────────────────┐   ┌──────────────────────────────────────────┐   │
│   │     KGuardScreenTimeReceiver    │   │         DeviceLockActivity               │   │
│   │     (KidSafe ScreenTimeReceiver)│   │  - Fullscreen over Keyguard              │   │
│   │  - Traps ACTION_SCREEN_ON       │   │  - System Back / Gesture Lockout         │   │
│   │  - Traps ACTION_SCREEN_OFF      │   │  - Anti-Home / Anti-Recents Guard        │   │
│   │  - Calls lockNow() immediately  │   │  - Parent PIN Verification (Salt/Hash)   │   │
│   │  - Re-launches DeviceLockActivity│  │  - Auto-Dismiss on Remote Unlock Event   │   │
│   └─────────────────┬───────────────┘   └──────────────────────────────────────────┘   │
│                     │                                                                  │
│                     ▼                                                                  │
│   ┌─────────────────────────────────┐                                                  │
│   │    KGuardDeviceAdminReceiver    │                                                  │
│   │    - DevicePolicyManager.lockNow│                                                  │
│   │    - Physical screen shutoff    │                                                  │
│   └─────────────────────────────────┘                                                  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Architectural Pillars

1. **Whole-Screen Bedtime Lockdown (Not App-Only):**
   * When bedtime arrives, `KGuardCompanionService` immediately calls `KGuardDeviceAdminReceiver.lockScreenNow(this)`.
   * It simultaneously launches `DeviceLockActivity` with `EXTRA_LOCK_TYPE = "BEDTIME_LOCKED"`, decorated with `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, and `FLAG_KEEP_SCREEN_ON`.
   * Dynamic receiver `KGuardScreenTimeReceiver` traps `ACTION_SCREEN_ON`. Every time the child attempts to wake the device, the screen is instantly forced shut with `lockNow()` and covered with `DeviceLockActivity`.

2. **KidSafe Reference Integration (`ScreenLock` & `ScreenTimeReceiver`):**
   * **Model:** [`ScreenLock.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/data/model/ScreenLock.kt) faithfully captures the KidSafe duration (`hours`, `minutes`, `timeInSeconds`, `isLocked`, `lockReason`).
   * **Receiver:** [`KGuardScreenTimeReceiver.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/receiver/KGuardScreenTimeReceiver.kt) is dynamically registered in the foreground service to monitor wake/sleep events and compute elapsed screen time.
   * **Continuous Locker:** A 1-second continuous assertion loop in `KGuardCompanionService` ensures that if `DeviceLockActivity` is ever dismissed or minimized while locked, the system immediately re-asserts the hardware keyguard and re-launches the lock screen.

3. **Reboot Protection (`BootCompleteReceiver`):**
   * [`BootCompleteReceiver.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/receiver/BootCompleteReceiver.kt) listens to `ACTION_BOOT_COMPLETED` and `QUICKBOOT_POWERON` to immediately restart the companion engine after any device restart.

4. **Tamper-Resistant Security UI:**
   * Back button / gesture navigation is trapped and swallowed via `OnBackPressedCallback(true)`.
   * `onUserLeaveHint` intercepts attempts to press Home or Recent Apps.
   * Parent PIN verification checks against the true salted/hashed PIN in `UserPreferencesRepository`.
   * Auto-dismissal when parent issues `UNLOCK_DEVICE` or disables `isLocked` in the cloud.

5. **Quota-Optimized Telemetry Protocol:**
   * Telemetry writes are throttled to a single document `families/$familyId/devices/$childId` once every 60 seconds (or immediately upon state changes).
   * App usage lists are stored as a compact embedded array inside the device document, reducing Firestore writes by **98%**.

---

## 4. Implementation Details & File Manifest

| Module / File | Changes & Responsibilities |
|---|---|
| [`ScreenLock.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/data/model/ScreenLock.kt) | Kotlin data model capturing hours, minutes, seconds, lock status, and lock reason matching KidSafe architecture. |
| [`KGuardScreenTimeReceiver.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/receiver/KGuardScreenTimeReceiver.kt) | Dynamic broadcast receiver listening to `ACTION_SCREEN_ON` and `ACTION_SCREEN_OFF` to enforce physical `lockNow()` and display lock overlay upon wake. |
| [`KGuardDeviceAdminReceiver.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/receiver/KGuardDeviceAdminReceiver.kt) | Enhanced with `lockScreenNow()`, `isAdminActive()`, and intent builder for requesting Device Administrator privileges. |
| [`BootCompleteReceiver.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/receiver/BootCompleteReceiver.kt) | Guarantees automatic service restart upon device boot, preventing reboot bypass. |
| [`DeviceLockActivity.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/ui/screens/DeviceLockActivity.kt) | Hardened against back gestures, recents navigation; secure PIN verification; auto-dismiss on parent unlock; localized emergency dialer. |
| [`KGuardCompanionService.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/service/KGuardCompanionService.kt) | Full-fledged background enforcement engine with dynamic `ScreenTimeReceiver` registration, 1-second continuous locker loop, 5-second schedule checks, and throttled telemetry sync. |
| [`UserPreferencesRepository.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/data/repository/UserPreferencesRepository.kt) | Added `familyIdFlow`, `saveFamilyId()`, and secure PIN verification helper methods. |
| [`ParentalControlsRepository.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/data/repository/ParentalControlsRepository.kt) | Added `screenLock` serialization/deserialization and unified remote action dispatch. |
| [`ScreenTimeViewModel.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/viewmodel/ScreenTimeViewModel.kt) | Integrated `remoteTimedLock()`, synchronized `screenLock` maps, and test coroutine scopes. |
| [`ScreenTimeScreen.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/ui/screens/ScreenTimeScreen.kt) | Integrated `PhoneLockDialog`, added Device Admin health badge (`ADMIN` / `NO ADMIN`), and timed lock button. |
| [`ChildDashboardScreen.kt`](file:///C:/Users/ANHONY/AndroidStudioProjects/KGuard/app/src/main/java/com/tele/k_guard/ui/screens/ChildDashboardScreen.kt) | Bound to live device stream; added Device Admin permission request card. |

---

## 5. Verification & Testing Matrix

| Test Case | Expected Behavior | Verification Status |
|---|---|---|
| **Whole-Screen Bedtime Lock** | When clock hits `bedtimeStart` (e.g. 21:00), `DevicePolicyManager.lockNow()` physically turns off screen; waking device re-triggers `lockNow()` and displays `DeviceLockActivity` with `BEDTIME_LOCKED`. Whole phone is locked. | Verified |
| **Timed Phone Lock (KidSafe Logic)** | Parent selects 1 hr 30 mins in `PhoneLockDialog`; `ScreenLock` document updates; child phone locks immediately via `lockNow()` and displays countdown overlay. | Verified |
| **Instant Switch-Off** | Parent taps "Switch Off Screen"; child screen turns off physically (`lockNow`), and `DeviceLockActivity` displays the lock reason upon waking. | Verified |
| **Remote Unlock** | Parent taps "Unlock & Resume"; child's `DeviceLockActivity` auto-dismisses immediately without PIN entry; `ScreenTimeReceiver` resets. | Verified |
| **Anti-Bypass Back Gesture** | Child presses system back button or swipes back gesture; lock screen remains fixed on screen. | Verified |
| **Anti-Bypass Home / Recents** | Child attempts to press Home or open Recent Apps; `onUserLeaveHint` and 1-second service locker loop re-assert the lock screen immediately. | Verified |
| **Reboot Bypass Prevention** | Child turns off and reboots the phone; `BootCompleteReceiver` starts `KGuardCompanionService` immediately, re-locking the screen. | Verified |
| **Secure Parent PIN Unlock** | Child enters random 4-digit code (e.g. `0000`); rejected. Child enters parent's true PIN; unlocked. | Verified |
| **Daily Limit Exhaustion** | When `usedMinutesToday >= dailyLimitMinutes`, child device automatically locks with `LIMIT_REACHED`. | Verified |
| **School Focus Mode** | During school hours on weekdays, social/gaming apps are intercepted while educational apps remain open. | Verified |
| **Loud Alarm Trigger** | Parent taps "Ring Phone"; child phone sounds alarm at high volume via `STREAM_ALARM`. | Verified |
