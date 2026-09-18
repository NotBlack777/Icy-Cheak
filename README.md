# Icy Cheak 🔍⚡

**Icy Cheak** is an advanced, modern Android hardware, OS, and device inspector, enhanced with elevated privileges via **Root (libsu)** and **Shizuku API**.

- 💸 **100% Free and Open Source**
- 🛡️ **Zero Ads, Zero Tracking, Zero Analytics**
- ⚡ **Deep hardware inspection** with root and Shizuku elevation
- 🎨 **Material 3 Design** with smooth transitions and system dark/light theme support
- 🔎 **Real-time persistent filtering & search** across all categories

---

## 📱 Features & Categories

1. **Hardware**
   - SoC name, CPU architecture, core counts, and supported ABIs.
   - **Live per-core frequency** via `/sys/devices/system/cpu/` (elevated through Root/Shizuku).
   - Detailed RAM statistics: total, used, free, threshold, and ZRAM/Swap totals.
   - Display telemetry: resolution, density DPI, refresh rate, physical dimensions.
   - Sensors overview and capabilities.

2. **Software**
   - Android OS version, SDK API level, codename, and security patch level.
   - Linux kernel version, architecture, and system uptime.
   - Device build fingerprint, manufacturer, model, and board.
   - Bootloader unlock status and SELinux enforcement (`getenforce`).
   - Custom ROM detection (LineageOS, HyperOS/MIUI, One UI, crDroid, PixelExperience).

3. **Battery**
   - Real-time battery percentage, charging state, and plugged power source (AC/USB/Wireless).
   - Battery health, temperature (°C and °F), voltage (mV), and battery chemistry.
   - **Charge cycle count** and **charge full capacity** from kernel drivers via Root/Shizuku.
   - Instant current draw (µA) and remaining charge counter.

4. **Storage**
   - Internal storage breakdown (used, free, percentage).
   - Full partition mounting breakdown (`/`, `/system`, `/vendor`, `/data`) via `df`.
   - Directory size analyzer for app and system directories.

5. **Network**
   - Connection status (Wi-Fi, Cellular, Ethernet, VPN detection).
   - Wi-Fi telemetry: SSID, BSSID (MAC), link speed, signal strength (RSSI), frequency band (2.4 GHz, 5 GHz, 6 GHz), and Wi-Fi generation standard (Wi-Fi 4/5/6/7).
   - Cellular carrier names, SIM country ISO, and SIM state.
   - Local IPv4 network address and configured DNS servers.
   - **Optional / Opt-in Public IPv4 lookup** (can be enabled in Settings; no network requests are made by default).

6. **Processes & Services**
   - Running system and user processes (requires Root or Shizuku on modern Android).
   - Detailed per-process PID, user, memory RSS, CPU usage percentage, and execution state.

7. **Installed Apps**
   - Complete package list filterable by **All**, **User Apps**, and **System Apps**.
   - Package name, version name, version code, and APK file size.
   - Install time, last update time, and requested Android permissions.

8. **System Logs (Logcat)**
   - Live system logcat viewer (requires Root or Shizuku on Android 6+).
   - Filterable by log priority level: Verbose (`V`), Debug (`D`), Info (`I`), Warning (`W`), Error (`E`).
   - Toggleable auto-refresh loop and real-time query filtering.

9. **Sensors**
   - Real-time readout of all available hardware sensors (accelerometer, gyroscope, light, proximity, barometer, etc.).
   - Live canvas graphing of real-time sensor measurements.

10. **Settings & Privilege Engine**
    - Dynamic privilege switcher: **Auto**, **Root Superuser**, **Shizuku**, or **Standard Mode**.
    - **Colors & Theming**: six accent palettes applied app-wide through the Material 3 colour
      roles, plus surface gradients (*Default*, *Solid*, *Ocean*, *Sunset*, *Void*).
    - **Background animation**: *Gradient Drift*, *Particles* or *None* — forced to *None* in
      OLED mode unless you confirm the battery warning.
    - **Live telemetry polling**: pick 0.5 s / 1 s / 2 s / 5 s; CPU, RAM and battery share one
      sampling loop that stops when the app is not in the foreground.
    - **Report sections**: choose which of the nine categories an exported device report contains.
    - **Section organizer**: drag Settings sections into any order, or hide the ones you never use.
    - **Updates**: check GitHub Releases automatically (throttled) or on demand, and install a
      newer build from inside the app.
    - Relaunch onboarding setup at any time.
    - Toggle public IP lookup.

---

## 🔒 Root & Shizuku Privileges

On modern Android (Android 6.0+ through Android 14+), SELinux restrictions and Android OS sandboxing prevent normal apps from:
- Reading live per-core CPU clock frequencies from `/sys/devices/system/cpu/`
- Inspecting other applications' running processes and memory/CPU usage
- Reading system-wide `logcat` buffers
- Reading kernel battery cycle counters from `/sys/class/power_supply/battery/`

### How Icy Cheak Handles Access Gracefully
- **Zero Crashes**: All elevated calls are wrapped safely with graceful fallbacks. If neither Root nor Shizuku is granted, individual items clearly show `"Unavailable — requires root or Shizuku"`.
- **Root (libsu)**: Uses `com.github.topjohnwu.libsu` to run commands with standard superuser authorization prompts.
- **Shizuku**: Uses the official `dev.rikka.shizuku` API to execute privileged ADB commands without needing root.

### How to Install and Start Shizuku (Non-Root Users)
If your device is not rooted, you can easily use Shizuku to grant Icy Cheak elevated permissions:
1. Install **Shizuku** from Google Play or the [Shizuku GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).
2. Start Shizuku:
   - **Wireless Debugging (Android 11+)**: Enable Developer Options -> Wireless Debugging -> Pair with Shizuku using the pairing code -> Tap **Start** in Shizuku.
   - **Via PC (ADB)**: Connect your device via USB with USB Debugging enabled, then execute the command provided in the Shizuku app:
     ```bash
     adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
     ```
3. Open **Icy Cheak** and grant Shizuku permission when prompted on the onboarding screen or in Settings.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 1.9.24
- **UI Framework**: Jetpack Compose (BOM 2024.06.00) with Material 3
- **SDK Targets**: Min SDK 26 (Android 8.0 Oreo), Target & Compile SDK 34 (Android 14)
- **Root Execution**: `com.github.topjohnwu.libsu:core:5.3.0`
- **Privileged Access**: `dev.rikka.shizuku:api:13.1.5` & `dev.rikka.shizuku:provider:13.1.5`
- **Gradle**: Gradle 8.7 with Android Gradle Plugin 8.4.2

---

## 🚀 Automated Builds, Releases & In-App Updates

Everything is driven by `.github/workflows/build.yml`.

### Every push
The workflow builds `assembleDebug`, then **verifies that an APK was actually produced** and
publishes its path and size as a check-run annotation. A build that fails to package anything
fails the run — the pipeline is deliberately not allowed to report success without an APK.

### Every push to `main`
A GitHub Release is created (or updated) tagged `v<versionName>`, with the APK attached as
`IcyCheak-<versionName>.apk`. The workflow and `app/build.gradle.kts` derive the version from
the same source (`GITHUB_RUN_NUMBER`), so the tag can never drift from `BuildConfig`, and
`versionCode` increases with every release.

Releases — not workflow artifacts — are the download channel, because artifact downloads
require authentication and expire after the retention window. When you *do* want the raw
artifact of a run, it is still attached as `IcyCheak-Debug-APK`.

### Updating the app
**Settings › Updates › Check now** (or the automatic check on launch, throttled to once every
six hours) reads
`https://api.github.com/repos/NotBlack777/Icy-Cheak/releases/latest`, compares the release
version with the installed build, and offers *Update now* / *Later*. *Update now* downloads the
APK into a private cache directory, verifies it is really an APK, and hands it to the system
package installer.

Android requires a confirmation tap for every install, and this app deliberately does not use
root or device-owner privileges for updates — so the final dialog is expected behaviour, not a
bug. On first use Android also asks you to allow "install unknown apps" for the app; the app
offers a shortcut to that exact settings screen.

Releases are signed with the Android debug keystore that CI caches across runs, so consecutive
releases share one signing identity and install over each other. (Builds published before that
cache existed were signed with a throwaway key: uninstall once, then updates work in place.)

### Building locally
1. Clone the repository:
   ```bash
   git clone https://github.com/NotBlack777/Icy-Cheak.git
   ```
2. Open the project in Android Studio (Jellyfish / Koala or newer recommended).
3. Let Gradle sync and run `./gradlew assembleDebug` or click **Run ▶** to deploy to an emulator or physical device.

### Building Locally with Android Studio
1. Clone the repository:
   ```bash
   git clone https://github.com/NotBlack777/Icy-Cheak.git
   ```
2. Open the project in Android Studio (Jellyfish / Koala or newer recommended).
3. Let Gradle sync and run `./gradlew assembleDebug` or click **Run ▶** to deploy to an emulator or physical device.

---

## 📄 License

Icy Cheak is open-source under the Apache 2.0 License.
