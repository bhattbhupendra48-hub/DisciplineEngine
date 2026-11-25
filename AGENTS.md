# AGENTS.md — SYSTEM CONSTITUTION FOR "DISCIPLINE ENGINE" (ANDROID)

## 1. TARGET HARDWARE & PERFORMANCE BUDGET
- Target Device Profile: Budget 4GB RAM Android (MediaTek Dimensity 6100+ / ARM Mali-G57 MC2, Android 14+, Xiaomi HyperOS / Poco M6 5G).
- Maximum Idle Background RAM: Strictly under 18 MB across all background services combined.
- UI Rendering Target: Constant 60/90 FPS in Jetpack Compose with zero third-party chart or animation bloatware.
- ABI Filter: `arm64-v8a` for lean native library packaging.

## 2. FIVE IRONCLAD ENGINEERING LAWS (NEVER VIOLATE)
1. **NO VPN SERVICE**: Never use `android.net.VpnService`. Block Reels/Shorts via event-driven `AccessibilityService` view-ID inspection, and block 18+ adult sites via OS-Level Private DNS verification (`family.cloudflare-dns.com` / `1.1.1.3`) + a 400KB in-memory URL-bar Bloom Filter.
2. **NO BACKGROUND CAMERA/ML**: Never keep `CameraX` or `MediaPipe` in memory outside `CameraVerifyActivity`. Run `BlazePose-Lite (FP16/INT8)` at `640x480 @ 15 FPS`, and call `cameraProvider.unbindAll()` + `poseLandmarker.close()` in `onStop()`.
3. **ZERO-BAN, ZERO-OPEN PW TRACKING**: Never make unauthorized HTTP/API scraping requests to `pw.live` servers. Track Physics Wallah notices, classes, and tests 100% passively via `NotificationListenerService` (reading status bar notifications from `xyz.penpencil.physicswallah`), an offline Batch Planner Clock, and passive `AccessibilityService` screen reading when the user opens PW.
4. **MANDATORY HARDWARE WATCHDOGS**: Every sensor/camera feature MUST implement the exact state-machine fallbacks defined in `master_blueprint.json` (4-second Camera Hang Watchdog -> Proximity Sensor mode; 3x3 Empty Room Burst Cycle; <5 Lux Dark Room Screen Torch -> 1.5x Gyroscope Squat mode).
5. **DUAL-ORIENTATION & APPLE DOCK PHYSICS**: Every screen must adapt cleanly between Portrait (single column) and Landscape (Left 45% Habits/Schedule, Right 55% Graphs/PW/Stats) with the custom Gaussian-curve Apple Dock (`pointerInput` horizontal slide-to-pop + vertical swipe-up-to-open).

## 3. CODING & DATABASE RULES
- **Database**: All new features build on `DisciplineDatabase.dao()` (`discipline_engine.db`). Any Room schema change MUST increment the version and add a `Migration`.
- **RP Values**: Match `master_blueprint.json` exactly (+25 Pushup Alarm, +40 P1 Block, +30 PW Block, +15 Zero Temptation, -35 Missed/Penalty, -50 Boss).
- **Theme**: Dark Glassmorphism ONLY (`BackgroundCanvas #090D16`). No light theme.
- **Comments**: One-line plain English comment per function.

## 4. PHASE ROADMAP (build only the phase requested)
- Phase 1 ✅ DONE: Clean Gradle structure, Dark Glassmorphism theme, Room DB (`habits_table`, `rp_events`, `streak_state`), test RP button
- Phase 2: Real RP/rank logic (Bronze → Conqueror), streak S2/S4/S5 + miss detection, Illness/Travel mode, Dopamine Debt
- Phase 3: Kill-proof alarm, full-screen intent, reboot resurrection receiver, 3x3 burst cycle
- Phase 4: Schedule creator (7 questions + 8A buffer + 8C priority triage)
- Phase 5: Camera pushup verification (MediaPipe BlazePose-Lite) + watchdogs (proximity/gyro/torch)
- Phase 6: AccessibilityService Reels/Shorts blocker + 18+ Private DNS/Bloom filter + Settings guard
- Phase 7: PW notification catcher + offline batch clock + exam triage engine
- Phase 8: Apple Dock + Gaussian magnification + landscape two-pane layout
- Phase 9: 365-day heatmap + 5-Axis RPG Radar + Urge-vs-Output native Canvas graphs
- Phase 10: Device admin, HyperOS onboarding helper, hardening, polish

## 5. BUILD COMMAND
- Local/CI: `gradle assembleDebug` (or `./gradlew assembleDebug`) -> `app/build/outputs/apk/debug/app-debug.apk`
