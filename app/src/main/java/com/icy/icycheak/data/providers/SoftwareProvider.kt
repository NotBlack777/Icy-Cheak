package com.icy.icycheak.data.providers

import android.content.Context
import android.os.Build
import com.icy.icycheak.model.InfoRow
import com.icy.icycheak.model.SoftwareInfo
import com.icy.icycheak.privilege.PrivilegeEngine
import java.io.BufferedReader
import java.io.File

object SoftwareProvider {

    data class RomProps(
        val selinux: String,
        val bootloaderUnlocked: Boolean?,
        val customRom: String?
    )

    suspend fun getSoftwareInfo(context: Context): SoftwareInfo {
        val sdkInt = Build.VERSION.SDK_INT
        val securityPatch = if (sdkInt >= 23) Build.VERSION.SECURITY_PATCH else "—"
        val kernel = readKernelVersion()
        val arch = System.getProperty("os.arch") ?: "—"
        val uptime = readUptime()
        val fingerprint = Build.FINGERPRINT
        val props = readRomProps()

        val releaseName = Build.VERSION.RELEASE.orEmpty().ifBlank { "—" }
        val rows = listOf(
            row("Android version", "$releaseName (API $sdkInt)"),
            row("Codename", codenameFor(sdkInt)),
            row("Security patch", securityPatch),
            row("Kernel", kernel),
            row("Architecture", arch),
            row("Uptime", uptime),
            row("Manufacturer", Build.MANUFACTURER),
            row("Model", Build.MODEL),
            row("Board", Build.BOARD),
            row("Bootloader", Build.BOOTLOADER),
            row("Build ID", Build.ID),
            row("SELinux", props.selinux, emphasized = true),
            row("Bootloader unlock", when (props.bootloaderUnlocked) {
                true -> "Unlocked"
                false -> "Locked"
                null -> "Unknown"
            })
        )
        return SoftwareInfo(
            androidVersion = "${Build.VERSION.RELEASE.orEmpty()} (API $sdkInt)",
            sdk = sdkInt,
            codename = codenameFor(sdkInt),
            securityPatch = securityPatch,
            kernel = kernel,
            arch = arch,
            uptime = uptime,
            fingerprint = fingerprint,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            bootloaderUnlocked = props.bootloaderUnlocked,
            selinux = props.selinux,
            customRom = props.customRom,
            rows = rows
        )
    }

    private fun codenameFor(sdk: Int): String = when (sdk) {
        34 -> "UpsideDownCake (14)"
        33 -> "Tiramisu (13)"
        32 -> "Tiramisu (12.1)"
        31 -> "S/V (12)"
        30 -> "R (11)"
        29 -> "Q (10)"
        28 -> "Pie (9)"
        else -> "SDK $sdk"
    }

    private fun readKernelVersion(): String = runCatching {
        File("/proc/version").readText().trim().take(120)
    }.getOrDefault("—")

    private fun readUptime(): String {
        val secs = runCatching {
            File("/proc/uptime").readText().trim().split(" ")[0].toDouble().toLong()
        }.getOrNull() ?: (android.os.SystemClock.elapsedRealtime() / 1000)
        val d = secs / 86400
        val h = (secs % 86400) / 3600
        val m = (secs % 3600) / 60
        return buildString {
            if (d > 0) append("${d}d ")
            if (h > 0 || d > 0) append("${h}h ")
            append("${m}m")
        }
    }

    private suspend fun readRomProps(): RomProps {
        // One shell call for SELinux; fall back to the sysfs enforce file.
        val enforce = PrivilegeEngine.execute("getenforce 2>/dev/null || cat /sys/fs/selinux/enforce 2>/dev/null", 4000)
        val selinux = when {
            enforce.stdout.firstOrNull()?.contains("Permissive", true) == true -> "Permissive"
            enforce.stdout.firstOrNull()?.contains("Enforcing", true) == true -> "Enforcing"
            enforce.stdout.firstOrNull()?.trim() == "0" -> "Permissive"
            enforce.stdout.firstOrNull()?.trim() == "1" -> "Enforcing"
            else -> "Unknown"
        }

        // Detect common custom ROMs from build identifiers / props.
        val display = Build.DISPLAY.orEmpty()
        val incremental = Build.VERSION.INCREMENTAL.orEmpty()
        val id = Build.ID.orEmpty()
        val hay = "$display $incremental $id".lowercase()
        val customRom = when {
            hay.contains("lineage") -> "LineageOS"
            hay.contains("crDroid".lowercase()) || hay.contains("crdroid") -> "crDroid"
            hay.contains("pixel experience") || hay.contains("pxl") -> "Pixel Experience"
            hay.contains("evolution") -> "Evolution X"
            hay.contains("hyperos") -> "Xiaomi HyperOS"
            hay.contains("miui") -> "Xiaomi MIUI"
            hay.contains("oneui") || id.contains("OneUI", true) -> "Samsung One UI"
            else -> null
        }

        val locked = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.flash.locked"))
            BufferedReader(java.io.InputStreamReader(p.inputStream)).readText().trim()
        }.getOrNull()
        val bootloaderUnlocked = when (locked) {
            "0" -> true
            "1" -> false
            else -> null
        }
        return RomProps(selinux, bootloaderUnlocked, customRom)
    }

}
