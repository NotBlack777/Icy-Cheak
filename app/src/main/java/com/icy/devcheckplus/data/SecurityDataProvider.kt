package com.icy.devcheckplus.data

import android.content.Context
import android.os.Build
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SecurityDataProvider {
    private const val TIMEOUT_MS = 3000L

    suspend fun getSecuritySections(context: Context): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        val selinuxItems = mutableListOf<InfoItem>()
        try {
            val res = PrivilegeManager.executeCommand("getenforce", timeoutMs = TIMEOUT_MS)
            val mode = if (res.isSuccess && res.stdout.isNotEmpty()) res.stdout.first().trim() else "Enforcing (default)"
            selinuxItems.add(InfoItem("SELinux Mode", mode))
        } catch (_: Exception) {
            selinuxItems.add(InfoItem("SELinux Mode", "Enforcing (assumed)"))
        }

        // Verified boot
        try {
            val verified = getProp("ro.boot.verifiedbootstate")
            if (verified.isNotBlank()) {
                selinuxItems.add(InfoItem("Verified Boot State", verified))
            }
            val vbState = getProp("ro.boot.vbmeta.device_state")
            if (vbState.isNotBlank()) {
                selinuxItems.add(InfoItem("VBMeta State", vbState))
            }
            val flashLocked = getProp("ro.boot.flash.locked")
            if (flashLocked.isNotBlank()) {
                selinuxItems.add(InfoItem("Flash Locked", if (flashLocked == "1") "Locked" else "Unlocked"))
            }
            val oemUnlock = getProp("sys.oem_unlock_allowed")
            if (oemUnlock.isNotBlank()) {
                selinuxItems.add(InfoItem("OEM Unlock Allowed", oemUnlock))
            }
        } catch (_: Exception) {}

        sections.add(InfoSection("SELinux & Verified Boot", selinuxItems))

        val bootloaderItems = mutableListOf<InfoItem>()
        bootloaderItems.add(InfoItem("Bootloader", Build.BOOTLOADER))
        bootloaderItems.add(InfoItem("Board", Build.BOARD))
        bootloaderItems.add(InfoItem("Device", Build.DEVICE))
        bootloaderItems.add(InfoItem("Fingerprint", Build.FINGERPRINT))

        try {
            val unlocked = File("/proc/mounts").readText().contains("rw")
            bootloaderItems.add(InfoItem("System Mount", if (unlocked) "RW (possibly unlocked)" else "RO (normal)"))
        } catch (_: Exception) {}

        sections.add(InfoSection("Bootloader & Device", bootloaderItems))

        val privilegeItems = mutableListOf<InfoItem>()
        val status = PrivilegeManager.status.value
        privilegeItems.add(InfoItem("Active Privilege Mode", status.activeMode.name))
        privilegeItems.add(InfoItem("Root Available", if (status.rootAvailable) "Yes" else "No"))
        privilegeItems.add(InfoItem("Shizuku Available", if (status.shizukuAvailable) "Yes" else "No"))
        privilegeItems.add(InfoItem("Shizuku Running", if (status.shizukuRunning) "Yes" else "No"))
        privilegeItems.add(InfoItem("Root Granted", if (status.rootGranted) "Yes" else "No / Unknown"))
        privilegeItems.add(InfoItem("Shizuku Permission", if (status.shizukuPermissionGranted) "Granted" else "Not granted"))

        try {
            val suExists = File("/system/bin/su").exists() || File("/system/xbin/su").exists() || File("/sbin/su").exists()
            privilegeItems.add(InfoItem("SU Binary", if (suExists) "Found" else "Not found"))
        } catch (_: Exception) {}

        try {
            val magisk = File("/sbin/magisk").exists() || File("/system/bin/magisk").exists()
            privilegeItems.add(InfoItem("Magisk", if (magisk) "Detected" else "Not detected"))
        } catch (_: Exception) {}

        sections.add(InfoSection("Root & Privilege Diagnostics", privilegeItems))

        val securityPatchItems = mutableListOf<InfoItem>()
        securityPatchItems.add(InfoItem("Security Patch", Build.VERSION.SECURITY_PATCH ?: "Unknown"))
        securityPatchItems.add(InfoItem("Android Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
        securityPatchItems.add(InfoItem("Build Tags", Build.TAGS))
        securityPatchItems.add(InfoItem("Build Type", Build.TYPE))
        securityPatchItems.add(InfoItem("SELinux Policy", try { getProp("ro.build.selinux") } catch (_: Exception) { "Unknown" }))

        sections.add(InfoSection("Security Patch & Build", securityPatchItems))

        sections
    }

    private suspend fun getProp(key: String): String {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val getMethod = c.getMethod("get", String::class.java)
            val res = getMethod.invoke(c, key) as? String
            if (!res.isNullOrBlank()) return res
            val cmdRes = PrivilegeManager.executeCommand("getprop $key", timeoutMs = TIMEOUT_MS)
            if (cmdRes.isSuccess && cmdRes.stdout.isNotEmpty()) cmdRes.stdout.first().trim() else ""
        } catch (_: Exception) {
            ""
        }
    }
}
