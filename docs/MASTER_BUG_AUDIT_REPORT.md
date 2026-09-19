# Master Bug Audit + Fix Report — 2026-09-19

Audited against commit `23f9938` (current `main`). This round both **fixed confirmed
bugs** and **re-verified areas already correct**. The two categories are kept strictly
separate below.

## Build Verification
- **Green run: [35475267834](https://github.com/NotBlack777/Icy-Cheak/actions/runs/35475267834)**
  on commit `0977c20` — `assembleDebug` ✓ and `testDebugUnitTest` ✓ (all suites pass:
  PureLogicTest, DevToolDetectionTest, WidgetConsistencyTest, UpdaterLogicTest,
  PrivilegeModeTest).
- APK: `app/build/outputs/apk/debug/app-debug.apk` (17,293,042 bytes), verified
  installable-shaped by the CI "Verify APK" step.
- Signing identity unchanged (same cached debug keystore, SHA256
  `E9:51:6F:9A:98:23:3E:D2:7C:68:B4:2A:D7:F4:24:86:33:0A:51:27:3E:F4:E6:13:76:54:27:86:BF:F5:AB:F9`),
  so the in-app updater can still install over previous builds.
- Note: this sandbox has no JDK and no route to Maven/Gradle/Google hosts, so the
  Gradle commands could not run locally — GitHub Actions is the real verification
  gate (same situation as prior PRs). Two compile/test issues were caught and fixed
  through that gate before green.

---

## A. CONFIRMED FIXED (changed + how verified)

### 1. Dev Environment tool detection rebuilt (audit §2, §3, §4)
**Gap (confirmed in code):** PATH was only extended with Termux `bin`, `go/bin`,
`.cargo/bin`; no `~/.local/bin`, no venv, no proot, no "installed but in a different
environment" labeling; pip probed only via bare `pip3`/`pip`.

**Fix:** `DevEnvironmentDataProvider` was rewritten around a declarative `DevToolSpec`
(command candidates, version args, `python -m` module probes). Each tool runs ONE
POSIX-sh probe script that emits tagged lines (`DIRECT|`, `ENV|`, `ENV_VER|`,
`PROOT_DISTRO|`, `MOD|`, `VENV|`, `DENY|`); parsing + classification are pure Kotlin
(`parseProbeOutput`, `classify`) and unit-tested.

Distinct states now produced (never collapsed into "Not installed"):
- `DIRECT` — on the answering shell's PATH (directly runnable)
- `TERMUX` — inside the Termux prefix (also when found via the root PATH)
- `VENV` — pyvenv.cfg detected around the resolved executable
- `PROOT` — inside a proot-distro rootfs (`Installed in Ubuntu proot`, *not directly
  runnable from Android shell*)
- `OTHER_PATH_DIR` — executable in `~/.local/bin` / `~/.cargo/bin` / `~/go/bin`
  (Termux home included): "Installed but not on current PATH"
- `PYTHON_MODULE` — no standalone launcher but `python3 -m pip` / `python -m pip`
  works (module path parsed from pip's own output)
- `PERMISSION_DENIED` — file exists at a known location but is not executable
- `timedOut` — per-probe watchdog state
- binary found but version command failed → "Installed — version check failed"

pip is tested as `pip3`, `pip`, `python3 -m pip`, `python --version` forms; resolution
uses `command -v` plus known absolute directories per environment; `$HOME`-based dirs
are included so a root shell with a different HOME/PATH is handled (matrix #12).

Tools added on the extensible spec architecture: **uv, aider, Yarn, Poetry** (aider and
poetry also get `python -m` fallbacks). Total 16 tools, all probed in parallel with the
existing 6 s per-probe watchdog through `PrivilegeManager.executeCommand`.

**Verified:** script logic executed against real `sh`/`dash` in a POSIX sandbox
(direct, `ENV|localbin`, `ENV|proot`+distro, `DENY`, module fallback branches all
produced the expected tagged output); full classification matrix covered by
`DevToolDetectionTest` (12 scenarios).

### 2. Orphaned network widget — implemented fully (audit §5, Option A)
**Gap (confirmed):** `layout/widget_network.xml` + `widget_network_name/description`
strings existed with no provider class, no metadata, no manifest receiver.

**Fix:** `NetworkWidgetProvider` added to the family file; `res/xml/widget_network_info.xml`
metadata added; manifest `<receiver>` registered; provider added to
`WidgetRefreshScheduler.ALL_PROVIDERS` and the refresh renderer map; renders
`network_type` / `network_status` with offline/warn colouring and the standard click
intent. **Verified:** `WidgetConsistencyTest` asserts the manifest ↔ metadata ↔ layout ↔
scheduler chain in both directions, so an orphan in either direction fails CI.

### 3. "Can't load widget" gradient root cause (audit §6)
**Gap (confirmed):** `ic_launcher_foreground.xml` (108dp adaptive vector with an
`<aapt:attr>` linear gradient) was used as a plain `ImageView src` inside
`widget_metrics.xml` and `widget_compact.xml` — RemoteViews-inflated layouts.

**Fix:** new flat, solid-fill `drawable/ic_widget_logo.xml` (no `<gradient>`, no
`aapt:attr`) used by every widget layout; `ic_launcher_foreground` is now referenced
only by the launcher mipmaps. **Verified:** `WidgetConsistencyTest` fails if any
`widget_*` layout references `ic_launcher_foreground` or any widget-referenced drawable
contains a `<gradient>`/`aapt:attr`.

### 4. Widget fallback + diagnostics system (audit §7, §23)
**Fix:** new `WidgetRender.renderSafely` pipeline for all 7 providers:
1. RemoteViews object built first (minimal valid widget);
2. optional fields populated afterwards, each inside a section guard — a failing field
   keeps its layout placeholder (`--`) while the rest survives;
3. any full-render failure returns the new trivial `widget_fallback.xml` RemoteViews;
4. snapshot reads go through `WidgetMetrics.readSafely` (failure → logged, empty
   snapshot whose fields render as placeholders).

All silent `catch (_: Throwable) {}` blocks in the widget layer were replaced with
`WidgetLog` (single tag `IcyCheakWidget`, logs provider name, widget id, layout,
exception type+message; no stack traces in UI). Widget size math clamped: battery
percent clamped to 0..100 at read time; renderers reject values outside 0..100.

### 5. Permission cleanup (audit §13, §15)
`READ_PHONE_STATE` removed from the manifest: the cellular section only reads public
carrier/SIM fields that need no runtime permission, and the app never reads device
identifiers — the declaration was an unnecessary dangerous permission.

### 6. `am force-stop` honest copy (audit §16 caveat)
Success message now says "Force-stop sent … (Android reports success even if it was
already idle)" instead of claiming the app definitely stopped.

### 7. Dead code removal
Unused `WidgetRefreshScheduler.SystemClockElapsed()` removed.

### 8. New tests (audit §25)
- `DevToolDetectionTest` — probe-script forms + the full 12-case classification matrix.
- `WidgetConsistencyTest` — manifest ↔ provider ↔ metadata ↔ layout ↔ drawable ↔ string
  ↔ scheduler chain for every widget, both directions.
- `UpdaterLogicTest` — numeric version comparison (`1.0.10 > 1.0.9`), normalization,
  release JSON parsing incl. APK asset selection and corrupt payloads.
- `PrivilegeModeTest` — AUTO order Root→Shizuku→NONE; explicit ROOT/SHIZUKU never
  silently downgrade; NONE stays NONE.

---

## B. AUDITED AND FOUND ALREADY CORRECT (no change, evidence noted)

- **Drawer scrolling (§11):** `MainActivity` uses `LazyColumn` with `weight(1f)` between
  pinned header/footer, plus a `LaunchedEffect` keeping the selected item visible. The
  eba0e80 fix is genuinely in place — left untouched.
- **Privilege engine (§12):** AUTO/ROOT/SHIZUKU/NONE semantics correct (now also unit
  tested); per-command hard timeout + circuit breaker apply at **every** call site
  (Dev Environment 6 s, app management 15 s, logcat 8 s, console, battery/hardware
  sysfs reads — all pass explicit `timeoutMs`); cancellation destroys only the owning
  job's process; stdout/stderr read on separate threads (no deadlock); process handles
  destroyed in `finally`. Root path re-checks `shell.isRoot` before crediting results.
- **Camera (§14):** metadata-only via `getCameraCharacteristics`; no `openCamera`, no
  `PreviewView`, no `ProcessCameraProvider` anywhere; API 29+ redaction is explained in
  UI states. Correct as-is.
- **Network screen (§15):** distinguishes "permission not granted" vs "location services
  off" vs generic unavailable, per-row explanations, explicit request button only.
- **Installed apps / uninstall lifecycle (§16):** result launcher + PackageManager
  re-check after standard uninstall; privileged uninstall refreshes the list; cancel vs
  failure distinguished; no fake success.
- **DataStore / startup (§17):** no `runBlocking` anywhere; DataStore cold-start read is
  async with a 750 ms cap and splash-coloured holding frame; theme prefs mirrored
  synchronously from SharedPreferences.
- **Updater (§21):** version comparison is numeric per-component (now unit-tested);
  download validates size floor + ZIP magic; FileProvider shares only `cacheDir/updates`;
  install requires the per-app unknown-sources grant with a deep link; failures surface
  reasons and a Retry; UI never claims success before the platform installer runs.
- **Security (§24):** all widget PendingIntents `FLAG_IMMUTABLE`; all widget receivers
  `exported="false"`; FileProvider scoped to one cache subdir; shell commands built only
  from constants or PackageManager-sourced names (console runs user input by design).
- **Widget refresh/lifecycle (§9):** single shared scheduler, one inexact non-wake alarm
  with a fixed PendingIntent, rate-limited re-arming, cancelled when the last widget of
  the family is removed (`onDeleted`/`onDisabled`), telemetry read off the receiver
  thread via `goAsync` + single-thread executor, 1 s snapshot cache deduplicates bursts.
- **Search/navigation (§20):** all 18 `NavCategory` entries mapped in the main `when`,
  drawer + search + console shortcut all route through the same state; empty query shows
  full content; commit-locate pulses matches without fighting scroll.
- **Performance (§18):** prior passes (ring-buffer console output, counted-once chips,
  ref-counted telemetry poller, scroll-paused ambient animation, chart master switch,
  per-option preference flows) are all present and consistent; no new regressions
  introduced by this round's changes (widget render stays one snapshot per cycle).

## C. ENVIRONMENT LIMITATIONS (stated plainly)

- No local JDK and no network route to Maven Central / Gradle / Google hosts in this
  sandbox, so `assembleDebug`/`testDebugUnitTest` could **not** be run locally — GitHub
  Actions is the verification gate for this commit (same situation as prior PRs).
- No emulator/launcher available, so widgets could not be placed on a real home screen;
  `WidgetConsistencyTest` is the strongest automated substitute and validates every link
  of the provider chain against the actual resource files.
- The probe scripts themselves WERE exercised in a real POSIX shell (sh/dash) covering
  the direct/localbin/proot/deny/module branches.
