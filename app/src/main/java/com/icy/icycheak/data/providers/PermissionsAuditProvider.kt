package com.icy.icycheak.data.providers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.icy.icycheak.model.AppPermissionEntry

object PermissionsAuditProvider {

    /** Permission base names considered "dangerous" for the audit. */
    val DANGEROUS = setOf(
        "CAMERA", "RECORD_AUDIO",
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
        "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS",
        "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "RECEIVE_MMS", "READ_CELL_BROADCASTS",
        "READ_PHONE_STATE", "READ_CALL_LOG", "WRITE_CALL_LOG", "READ_PHONE_NUMBERS",
        "ANSWER_PHONE_CALLS", "PROCESS_OUTGOING_CALLS", "ACCEPT_HANDOVER",
        "READ_CALENDAR", "WRITE_CALENDAR",
        "BODY_SENSORS", "ACTIVITY_RECOGNITION",
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
        "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO",
        "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "NEARBY_WIFI_DEVICES"
    )

    /** Maps a permission to a human category used for filtering. */
    fun categoryOf(permission: String): String {
        val base = permission.substringAfterLast('.')
        return when {
            base == "CAMERA" -> "Camera"
            base == "RECORD_AUDIO" -> "Microphone"
            base in setOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "BLUETOOTH_SCAN", "NEARBY_WIFI_DEVICES") -> "Location"
            base.contains("CONTACTS") || base == "GET_ACCOUNTS" -> "Contacts"
            base.contains("SMS") || base.contains("MMS") || base.contains("CELL_BROADCAST") -> "SMS"
            base.contains("PHONE") || base.contains("CALL_LOG") || base.contains("HANDOVER") -> "Phone"
            base.contains("CALENDAR") -> "Calendar"
            base.contains("SENSOR") || base == "ACTIVITY_RECOGNITION" -> "Body & Fitness"
            base.contains("STORAGE") || base.contains("MEDIA") -> "Storage"
            else -> "Other"
        }
    }

    fun getAuditedApps(context: Context): List<AppPermissionEntry> = runCatching {
        val pm = context.packageManager
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            val dangerous = (pi.requestedPermissions ?: emptyArray()).filter { perm ->
                DANGEROUS.any { perm.endsWith(".$it") || perm.endsWith(it) }
            }
            if (dangerous.isEmpty()) return@mapNotNull null
            AppPermissionEntry(
                packageName = pi.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                permissions = dangerous.sorted(),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedWith(compareBy({ !it.isSystem }, { it.label.lowercase() }))
    }.getOrDefault(emptyList())
}
