# DevCheck+ 🔍⚡

**DevCheck+** is an advanced, modern Android hardware, OS, and device inspector inspired by Dev Check, enhanced with elevated privileges via **Root (libsu)** and **Shizuku API**.

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
    - Relaunch onboarding setup at any time.
    - Toggle public IP lookup.

---

## 🔒 Root & Shizuku Privileges

On modern Android (Android 6.0+ through Android 14+), SELinux restrictions and Android OS sandboxing prevent normal apps from:
- Reading live per-core CPU clock frequencies from `/sys/devices/system/cpu/`
- Inspecting other applications' running processes and memory/CPU usage
- Reading system-wide `logcat` buffers
- Reading kernel battery cycle counters from `/sys/class/power_supply/battery/`

### How DevCheck+ Handles Access Gracefully
- **Zero Crashes**: All elevated calls are wrapped safely with graceful fallbacks. If neither Root nor Shizuku is granted, individual items clearly show `"Unavailable — requires root or Shizuku"`.
- **Root (libsu)**: Uses `com.github.topjohnwu.libsu` to run commands with standard superuser authorization prompts.
- **Shizuku**: Uses the official `dev.rikka.shizuku` API to execute privileged ADB commands without needing root.

### How to Install and Start Shizuku (Non-Root Users)
If your device is not rooted, you can easily use Shizuku to grant DevCheck+ elevated permissions:
1. Install **Shizuku** from Google Play or the [Shizuku GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).
2. Start Shizuku:
   - **Wireless Debugging (Android 11+)**: Enable Developer Options -> Wireless Debugging -> Pair with Shizuku using the pairing code -> Tap **Start** in Shizuku.
   - **Via PC (ADB)**: Connect your device via USB with USB Debugging enabled, then execute the command provided in the Shizuku app:
     ```bash
     adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
     ```
3. Open **DevCheck+** and grant Shizuku permission when prompted on the onboarding screen or in Settings.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 1.9.24
- **UI Framework**: Jetpack Compose (BOM 2024.06.00) with Material 3
- **SDK Targets**: Min SDK 26 (Android 8.0 Oreo), Target & Compile SDK 34 (Android 14)
- **Root Execution**: `com.github.topjohnwu.libsu:core:5.3.0`
- **Privileged Access**: `dev.rikka.shizuku:api:13.1.5` & `dev.rikka.shizuku:provider:13.1.5`
- **Gradle**: Gradle 8.7 with Android Gradle Plugin 8.4.2

---

## 🚀 Automated CI / CD & Downloading the APK

This repository is set up with GitHub Actions in `.github/workflows/build.yml`.

### Download Prebuilt Debug APK
1. Go to the **Actions** tab of this repository on GitHub.
2. Click on the latest workflow run for the `main` or working branch.
3. Scroll down to the **Artifacts** section at the bottom of the summary page.
4. Download the `DevCheckPlus-Debug-APK` zip file, extract it, and install `app-debug.apk` directly on your Android device!

### Building Locally with Android Studio
1. Clone the repository:
   ```bash
   git clone https://github.com/NotBlack777/Icy-Cheak.git
   ```
2. Open the project in Android Studio (Jellyfish / Koala or newer recommended).
3. Let Gradle sync and run `./gradlew assembleDebug` or click **Run ▶** to deploy to an emulator or physical device.

---

## 📄 License

DevCheck+ is open-source under the Apache 2.0 License.
