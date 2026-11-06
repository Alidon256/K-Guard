# Plan: K-Guard UI Overhaul (Framer design system)

Source spec: `DESIGN.md` (installed by `npx getdesign@latest add framer`).
Survey basis: full UI/auth/demo-mode exploration, 6 Nov 2026.

## Design translation (marketing spec → Android app)

- **Light-only brand (product decision, overriding DESIGN.md's dark canvas).** Canvas white,
  surface-1 `#F5F5F5`, surface-2 `#EBEBEB`, hairline `#E5E5E5`. `dynamicColor` OFF — the brand is
  the brand on every device. Primary CTA inverts: black pill with white text; secondary is the
  light-gray pill. Same structure, daylight pairing.
- **Two-tier text:** white ink / `#999999` ink-muted. No other grays.
- **One accent:** `#0099FF` for links, focus rings, selection only — never fills.
- **CTAs are pills:** white-on-black primary; charcoal `#141414` secondary; ≥44dp touch height.
- **Inputs:** surface-1 fill, 10dp radius, blue focus ring, no borders.
- **Cards:** surface-1, 20dp; gradient spotlight cards (violet/magenta/orange) used at most 1–2 per screen (hero moments only).
- **Type:** Inter (bundled) or system SansSerif with tightened tracking; display sizes get negative letter-spacing; weight band 400–700.
- **Semantic colors** (needed by a safety app, outside the marketing spec): success `#22C55E`, warn `#F59E0B`, danger `#EF4444` — used as signals only, never as surfaces, and mapped once in the theme.

## Workstreams

### W1 — Foundation (main agent, before any screen work)
1. `ui/theme/Color.kt` — framer palette + semantic signals.
2. `ui/theme/Theme.kt` — fixed dark scheme, `dynamicColor = false`.
3. `ui/theme/Type.kt` — scale with negative tracking; `ui/theme/Shape.kt` (new) — 10/20/30/pill/full.
4. `ui/components/` — new shared kit: `KGButton` (primary/secondary/ghost/danger pills), `KGTextField`, `KGCard`, `KGSectionHeader`, `KGStatusTag`, `KGToggleRow`; migrate `DecisionBadge`, `KGuardTopAppBar`, `ModerationResultCard` colors to theme tokens.
5. Bundled Inter fonts if obtainable, else tuned system fonts.

### W2 — Google-only auth (main agent, compile-coupled core)
- `SignInScreen`: delete email/password fields + "Sign in/Create account"; Google = white pill with Google mark; parent/child chips stay; child OTP pairing stays.
- `SessionViewModel` + `FamilyRepository`: remove `signIn(email,…)`/`signUp`.
- Flow: Onboarding → Sign in with Google → (parent: Home / child: OTP → PIN → Child dashboard).

### W3 — Demo-mode removal (main agent core + per-screen remnants)
- Delete `DebugPreview.kt`; strip: SessionViewModel demo data/flows/branches, ModerationRepository demo branch, AppNavigation startRoute, SignIn demo button, WebProtection webTab, QueryMonitor debug gate → `BuildConfig`-style check.
- ParentalControlsRepository: delete `_demo*` + `resetDemoData()` + `"demo"` familyId branches.
- ViewModels: `familyId ?: "demo"` → explicit null + empty/needs-setup states.
- ScreenTimeScreen: remove hardcoded Ama/Kofi fallback children → real empty state.
- ProfileScreen/PrivacyScreen: "Exit demo" → plain sign-out.

### W4 — Per-screen subagents (one per screen/group, disjoint files)
| Agent | Files | Notes |
|---|---|---|
| A1 auth | SignInScreen, OnboardingScreen | after W2 |
| A2 home | HomeScreen | add Children management entry (flow bug: ChildEditor unreachable) |
| A3 children | ChildrenScreen (incl. ChildEditor) | |
| A4 web | WebProtectionScreen, QueryStatsCard | remove orphaned legacy composable |
| A5 screentime | ScreenTimeScreen, ScreenTimeViewModel | biggest; demo fallback |
| A6 apps | AppManagementScreen, AppDetailScreen, AppManagementViewModel | reword "simulate" copy |
| A7 small | Profile, Privacy, AgeProfile, ChildPinSetup, TextModeration, ImageModeration, ReviewQueue | |
| A8 child | ChildDashboardScreen, DeviceLockActivity | child-facing; gradient hero allowed |

Rules for all agents: theme tokens only (zero `Color(0x…)` literals), use the shared KG kit, pill CTAs, ≥44dp targets, no business-logic changes, no gradle runs (main agent compiles/fixes), keep function signatures unless the plan says otherwise.

### W5 — Integration & verification (main agent)
Compile → fix → full unit tests → assembleDebug → Mac testbed build → emulator install → visual check of 3 key screens → commit/push.

## Success criteria
1. Zero hardcoded `Color(0x…)` outside theme/gradient definitions.
2. Email/password auth gone; Google-only works for parent and child pairing.
3. No demo path anywhere; null familyId shows honest empty states, never fabricated data.
4. Every screen uses the shared kit; consistent pills/cards/inputs per DESIGN.md.
5. Full test suite green; app installs and navigates Onboarding → Home (parent) and pairing → Child dashboard (child).
