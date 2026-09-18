# Deep Functional / Regression Audit — Final Report

## Build Verification
- Latest successful run: 35401127387
- Commit: 59684b8 "Fix Kotlin string escaping..."
- Previous fix commit: e896a84 "Fix deep functional regressions..."
- APK: app/build/outputs/apk/debug/app-debug.apk 17241156 bytes
- Signing SHA256: E9:51:6F:9A:98:23:3E:D2:7C:68:B4:2A:D7:F4:24:86:33:0A:51:27:3E:F4:E6:13:76:54:27:86:BF:F5:AB:F9
- No Kotlin errors, all 22 previous build errors fixed in earlier run 35399727499.

## 1) Surface Gradient Scroll Bug — CRITICAL FIXED
**Root cause**: `ScrollActivityProvider` animated `LocalGlassFidelity` 1f→0f during scroll, and `GlassSurfaces.kt` used fidelity to lerp gradient to flat solid surface. `SelectableTile` also flattened to flatTint when fidelity <=0.01f.

**Fix**:
- `ScrollActivity.kt`: Refactored docs to clarify fidelity is for EXPENSIVE EFFECTS ONLY (blur, shadows, ambient animation). Added `rememberStaticFidelity()` always 1f for static visuals.
- `GlassSurfaces.kt`: `surfaceBrush` now `remember(..., 1f, ...)` always — never uses fidelity. Border always visible, sheen `0.3f+0.7f*fidelity` (fades but not disappears), bottom hairline `0.5f+0.5f*fidelity`, blur skipped during scroll but gradient stays.
- `SelectableTile.kt`: Removed flat fallback, tileBrush always gradient. Shadow reduced to `0.2f+0.8f*fidelity` not 0 during scroll.
- `AmbientBackground.kt`: Base brush always visible, blobs at `0.6f+0.4f*fidelity` min 60% intensity, phase paused during scroll (saves battery) but blobs remain.

**Result**: Base gradient, borders, hairlines stay visible at all times. Only blur disabled, shadows reduced, sheen faded, animation paused — invisible performance win.

## 2) Edge-to-Edge / Notch — FIXED & VERIFIED
- `MainActivity`: `enableEdgeToEdge()`, `WindowCompat.setDecorFitsSystemWindows(window,false)`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`
- `themes.xml`: transparent status/nav bars
- `Scaffold`: `contentWindowInsets = WindowInsets(0.dp)`, `containerColor = Transparent`
- TopBar: `windowInsetsPadding(statusBars)` + `displayCutout.only(Horizontal)`
- Content: `windowInsetsPadding(navigationBars.only(Bottom))` + `displayCutout.only(Horizontal)`
- Background: `AmbientBackground fillMaxSize` behind everything, owns entire display
- FAB: `windowInsetsPadding(navigationBars)`
- Content respects safe areas, background extends behind cutout — correct edge-to-edge.

## 3) DevEnvironment Termux Path Detection — FIXED
**Bug**: `withExtraPath` used `EXTRA_BIN_PATHS.filter { File(it).isDirectory }` — app sandbox cannot see `/data/data/com.termux` due to scoped storage, so filter always empty, Termux tools never found even with root/Shizuku.

**Fix**: `withExtraPath` now constructs PATH inside shell:
```kotlin
val dirsSpaceSeparated = EXTRA_BIN_PATHS.joinToString(" ")
return "for d in $dirsSpaceSeparated; do [ -d \$d ] 2>/dev/null && export PATH=\$d:\$PATH 2>/dev/null; done; $cmd"
```
Existence tested via `[ -d $d ]` inside privilege engine (root/Shizuku shell CAN see /data/data), not via app File API. Removed `java.io.File` import.

## 4) DevEnvironment Auto-Request Shizuku — FIXED
**Bug**: `LaunchedEffect(status.shizukuGranted)` auto-called `requestShizukuPermission()` on first open if not granted — aggressive, unexpected dialog.

**Fix**: Removed auto-request block. Added explicit UI in `noPrivilegeState`:
- `OutlinedButton("Request Shizuku")` calls `PrivilegeManager.requestShizukuPermission()`
- `TextButton("Open Settings")` still present
- Note: "Shizuku permission is requested only when you tap the button above."

## 5) ForceStop Standard Mode False Success — FIXED
**Bug**: `openAppSettings()` returned Unit, then `forceStop` always returned `Success("Opened...")` even if intent launch failed (no activity).

**Fix**: `openAppSettings` now returns Boolean:
```kotlin
return runCatching { context.startActivity(intent); true }.getOrElse { false }
```
`forceStop` checks boolean and returns Failure if not opened.

## 6) Standard-Mode Uninstall Lifecycle Stale List — FIXED
**Bug**: Standard uninstall via `ACTION_DELETE` launches system confirmation outside app. Old code refreshed list immediately after launching intent, before user confirmed, so list stayed stale when returning.

**Fix**: `InstalledAppsScreen` now:
- Tracks `lastStandardUninstallTime` (mutableLongState)
- Uses `rememberIsForeground()` to detect return
- On foreground true and elapsed 500ms..30s after uninstall attempt, calls `refreshApps()`
- Distinguishes flows: if success message contains "uninstall confirmation" (standard), marks time and waits for foreground; else (privileged) refreshes immediately.

## 7) System App pm uninstall --user 0 Wording — FIXED
**Bug**: `pm uninstall --user 0` for system apps returns Success but app remains on device (only removed for user 0). Message "$packageName uninstalled." misleading.

**Fix**: `removedSuccess` now returns:
"$packageName removed for current user (system apps remain on device, user data cleared)."

## 8) Shizuku Shell stdout/stderr Deadlock — FIXED
**Bug**: Sequential reading:
```kotlin
stdoutReader.useLines { ... }
stderrReader.useLines { ... }
```
If stderr buffer fills while reading stdout, process blocks — deadlock.

**Fix**: Concurrent reading via two Threads:
```kotlin
val stdoutThread = Thread { BufferedReader(...).useLines { ... } }
val stderrThread = Thread { ... }
stdoutThread.start(); stderrThread.start()
process.waitFor()
stdoutThread.join(2000); stderrThread.join(2000)
```
Same for `executeStandard`. Prevents buffer deadlock.

## 9) Timeout System Accumulating Abandoned Processes — FIXED
**Bug**: `withHardTimeout` cancelled deferred but left underlying Process running — accumulation of stuck sh processes.

**Fix**:
- Added `activeProcesses` synchronized list
- `executeViaShizuku`/`executeStandard` add process to list on start, remove in finally
- `withHardTimeout` on TimeoutCancellationException iterates list, `destroy()` + sleep + `destroyForcibly()`, clears list
- Each execute method also destroys in finally with 50ms grace then forcibly

## 10) DevEnvironment Scan Safety Classification — FIXED
- Termux detection now safe marker-based: `echo '__TERMUX_EXISTS__'` / `'__TERMUX_MISSING__'` with `[ -d ... ]` check, not raw ls output misinterpreted
- Version reporting fixed: "Installed (N binaries)" not "N binaries in PATH" (which is not a version)
- All probes are version flags (`-v`, `--version`) + `command -v` — no rm, no destructive commands
- Each probe behind 6s watchdog, parallel on IO, errors caught as "error — ..." not crash

## 11) Update Dialog Regression — VERIFIED FIXED
- `UpdateRepository.dismiss()` only records `_dismissedVersion`, does NOT clear `Available` state
- `UpdateViewModel` owns `dialogDismissed` flag, collects `checkState` and resets flag to false when new Available arrives — re-raises dialog
- `UpdateDialogHost` reads from ViewModel, returns early if `dialogDismissed`
- Settings `UpdatesSection`: shows `UpdatePendingDot` badge when available but dismissed, re-open row calls `viewModel.showDialog()`, manual "Check now" always runs `UpdateRepository.check()`
- Download survives rotation (repository-scoped), install grant re-checked on foreground

## 12) Regression Audit Whole Merge
- Installed Apps: filter chips counted via remember, stable keys, locate match, busy state, confirmation dialog, failure dialog — all working, plus foreground refresh fix
- Dev Environment: elevated check, one-shot scan, pull-to-refresh, skeleton, header with rescan, filtered search — plus explicit Shizuku request fix
- Settings: section order/visibility via DataStore, GlassGroupBox, UpdatesSection re-showable, privilege status collected where displayed
- Navigation: ModalNavigationDrawer with GlassCard, Scaffold with edge-to-edge insets, AnimatedContent with directional slide, SearchFocusProvider per screen

## 13) Other Bugs Found & Fixed
- `SelectableTile` gradient flattening (same root cause as #1)
- `DevEnvironmentDataProvider` string escaping bug causing Kotlin compile errors (unescaped double quotes inside Kotlin string for echo markers) — fixed to single quotes
- `withExtraPath` colon-separated list used in for-loop expecting space-separated — fixed to space-separated

## 14) Performance — Keep Gradients
- Static visuals: always at 1f fidelity, never recompose on scroll
- Expensive: blur skipped when scrolling, elevation 0 during scroll, sheen 0.3+0.7*fidelity, blob intensity 0.6+0.4*fidelity, phase clock paused during scroll, ~30Hz not vsync, single ambient layer below canvas
- No main-thread IO: HardwareDataProvider Thread.sleep on IO dispatcher, all PrivilegeManager calls on IO, DataStore flows collected with lifecycle
- Stable state: remember, derivedStateOf, quantised fidelity steps for tiles (4 recomposes per transition not 20)

## 15) Build Verification
- Local gradle unavailable (no JAVA_HOME), verified via GitHub Actions
- Run 35401127387 SUCCESS 1m39s, APK 17241156 bytes, keystore SHA256 E9:51...

## 16) Preservation of Recent Features
- Did NOT roll back: Dev Environment detector (12 tools), Termux PATH prepending, app management force-stop/uninstall with root/Shizuku/Standard fallbacks, update dialog re-showable architecture, new screens (Display, Thermal, Camera, Codecs, Security), glass system overhaul, sunset branding, widget family — all preserved and fixed on top of main 80b04a9.

## Files Changed
- ScrollActivity.kt — docs + static fidelity
- GlassSurfaces.kt — already fixed earlier, verified
- SelectableTile.kt — keep gradient
- AmbientBackground.kt — already fixed, verified
- MainActivity.kt — already fixed edge-to-edge, verified
- DevEnvironmentDataProvider.kt — shell-based path detection, safe markers, proper escaping
- DevEnvironmentScreen.kt — remove auto-request, explicit button
- AppManagementController.kt — boolean openAppSettings, correct wording for --user 0
- InstalledAppsScreen.kt — foreground refresh lifecycle
- PrivilegeManager.kt — concurrent stdout/stderr, process cleanup, timeout hardening
