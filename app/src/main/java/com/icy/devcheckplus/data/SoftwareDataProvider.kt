package com.icy.devcheckplus.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.UNAVAILABLE_TIMED_OUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SoftwareDataProvider {

    /** Watchdog for a single getprop / getenforce read. */
    private const val PROP_TIMEOUT_MS = 3_000L


    suspend fun getSoftwareSections(context: Context): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        // 1. Android OS & System
        val osItems = mutableListOf<InfoItem>()
        osItems.add(InfoItem("Android Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
        osItems.add(InfoItem("Security Patch Level", Build.VERSION.SECURITY_PATCH ?: "Unknown"))
        osItems.add(InfoItem("Codename", Build.VERSION.CODENAME))
        osItems.add(InfoItem("Build ID / Display", Build.DISPLAY))
        osItems.add(InfoItem("Build Fingerprint", Build.FINGERPRINT))
        val buildDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(Build.TIME))
        osItems.add(InfoItem("Build Date", buildDate))
        osItems.add(InfoItem("Build Type & Tags", "${Build.TYPE} / ${Build.TAGS}"))
        sections.add(InfoSection("Android Operating System", osItems))

        // 2. Kernel & Low-Level
        val kernelItems = mutableListOf<InfoItem>()
        val kernelVersion = getKernelVersion()
        kernelItems.add(InfoItem("Linux Kernel Version", kernelVersion))
        val kernelArch = System.getProperty("os.arch") ?: "Unknown"
        kernelItems.add(InfoItem("Kernel Architecture", kernelArch))

        // Uptime
        val uptimeMillis = SystemClock.elapsedRealtime()
        kernelItems.add(InfoItem("System Uptime", formatUptime(uptimeMillis)))

        // Bootloader status (via getprop if possible)
        val bootloaderStatus = getBootloaderStatus()
        kernelItems.add(
            InfoItem(
                title = "Bootloader Status",
                value = bootloaderStatus,
                requiresPrivilege = true,
                privilegeSource = "getprop sys.oem_unlock_allowed / ro.boot.flash.locked"
            )
        )

        // SELinux Status
        val selinuxStatus = getSELinuxStatus()
        kernelItems.add(
            InfoItem(
                title = "SELinux Status",
                value = selinuxStatus,
                requiresPrivilege = true
            )
        )
        sections.add(InfoSection("Kernel & Security", kernelItems))

        // 3. Device & ROM Details
        val romItems = mutableListOf<InfoItem>()
        romItems.add(InfoItem("Manufacturer", Build.MANUFACTURER))
        romItems.add(InfoItem("Brand", Build.BRAND))
        romItems.add(InfoItem("Model", Build.MODEL))
        romItems.add(InfoItem("Device Code", Build.DEVICE))
        romItems.add(InfoItem("Product Code", Build.PRODUCT))
        romItems.add(InfoItem("Hardware Board", Build.BOARD))

        // Custom ROM Detection (LineageOS, PixelExperience, MIUI, OneUI, etc.)
        val romInfo = detectCustomRom()
        if (romInfo.isNotBlank()) {
            romItems.add(InfoItem("Custom ROM / Skin", romInfo))
        }
        sections.add(InfoSection("Device & Firmware Details", romItems))

        sections
    }

    private fun getKernelVersion(): String {
        return try {
            BufferedReader(FileReader("/proc/version")).use { it.readLine() ?: "Unknown" }
        } catch (_: Exception) {
            System.getProperty("os.version") ?: "Unknown"
        }
    }

    private fun formatUptime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        val days = millis / (1000 * 60 * 60 * 24)
        return if (days > 0) {
            "${days}d ${hours}h ${minutes}m ${seconds}s"
        } else {
            "${hours}h ${minutes}m ${seconds}s"
        }
    }

    private suspend fun getBootloaderStatus(): String {
        // Try reading fastboot/bootloader state properties
        val lockedProp = getSystemProperty("ro.boot.flash.locked")
        val verifiedBoot = getSystemProperty("ro.boot.verifiedbootstate")
        if (lockedProp.isNotBlank() || verifiedBoot.isNotBlank()) {
            val locked = if (lockedProp == "1") "Locked" else if (lockedProp == "0") "Unlocked" else "Unknown"
            val state = if (verifiedBoot.isNotBlank()) "State: $verifiedBoot" else ""
            return "$locked $state".trim()
        }
        return "Locked / Protected (Normal)"
    }

    private suspend fun getSELinuxStatus(): String {
        val result = PrivilegeManager.executeCommand("getenforce", timeoutMs = PROP_TIMEOUT_MS)
        if (result.isSuccess && result.stdout.isNotEmpty()) {
            return result.stdout.first().trim()
        }
        if (result.timedOut) return UNAVAILABLE_TIMED_OUT
        return "Enforcing"
    }

    private suspend fun detectCustomRom(): String {
        val lineage = getSystemProperty("ro.lineage.version")
        if (lineage.isNotBlank()) return "LineageOS $lineage"

        val miui = getSystemProperty("ro.miui.ui.version.name")
        if (miui.isNotBlank()) return "Xiaomi HyperOS / MIUI $miui"

        val oneui = getSystemProperty("ro.build.version.oneui")
        if (oneui.isNotBlank()) return "Samsung One UI ($oneui)"

        val crdroid = getSystemProperty("ro.crdroid.version")
        if (crdroid.isNotBlank()) return "crDroid $crdroid"

        val pixelProps = getSystemProperty("ro.pixel.version")
        if (pixelProps.isNotBlank()) return "PixelOS / PixelExperience"

        return "Stock / AOSP based"
    }

    private suspend fun getSystemProperty(key: String): String {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val getMethod = c.getMethod("get", String::class.java)
            val res = getMethod.invoke(c, key) as? String
            if (!res.isNullOrBlank()) return res

            // Many properties are probed in a row, so each gets a short watchdog
            // and the PrivilegeManager circuit breaker stops a hung shell from
            // stacking timeouts.
            val cmdRes = PrivilegeManager.executeCommand("getprop $key", timeoutMs = PROP_TIMEOUT_MS)
            if (cmdRes.isSuccess && cmdRes.stdout.isNotEmpty()) {
                cmdRes.stdout.first().trim()
            } else ""
        } catch (_: Exception) {
            ""
        }
    }
}
