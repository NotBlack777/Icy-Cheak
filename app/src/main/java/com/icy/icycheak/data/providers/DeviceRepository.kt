package com.icy.icycheak.data.providers

import android.content.Context
import com.icy.icycheak.BuildConfig
import com.icy.icycheak.model.LiveSnapshot
import org.json.JSONArray
import org.json.JSONObject

data class ReportData(val json: String, val text: String)

data class DiffLine(val category: String, val label: String, val oldValue: String, val newValue: String)

/**
 * Aggregates every provider into a single exportable report (text + JSON).
 * Privilege-gated sections simply report what they can; they never throw.
 */
object DeviceRepository {

    suspend fun buildReport(context: Context, publicIpOptIn: Boolean): ReportData {
        val hw = HardwareProvider.getHardwareInfo(context)
        val sw = SoftwareProvider.getSoftwareInfo(context)
        val bat = BatteryProvider.getBatteryInfo(context)
        val storage = StorageProvider.getStorageInfo(context)
        val net = NetworkProvider.getNetworkInfo(context, publicIpOptIn)
        val sensors = SensorsProvider.getSensors(context)
        val apps = InstalledAppsProvider.getInstalledApps(context)
        val dev = DevEnvironmentProvider.getDevTools()
        val (procs, procNote) = ProcessProvider.getProcesses(context)
        val audit = PermissionsAuditProvider.getAuditedApps(context)
        val (crashes, crashNote) = CrashLogProvider.getCrashLog()

        val json = JSONObject().apply {
            put("app", "Icy Cheak")
            put("version", BuildConfig.VERSION_NAME)
            put("generatedAt", System.currentTimeMillis())
            put("hardware", JSONObject().apply {
                put("soc", hw.soc); put("board", hw.board); put("arch", hw.architecture)
                put("abis", JSONArray(hw.abis)); put("coresOnline", hw.cpuCoresOnline)
                put("coresTotal", hw.cpuCoresTotal); put("sensorCount", hw.sensorCount)
                put("ram", JSONArray(hw.ramRows.map { "${it.label}=${it.value}" }))
                put("display", JSONArray(hw.displayRows.map { "${it.label}=${it.value}" }))
            })
            put("software", JSONObject().apply {
                put("android", sw.androidVersion); put("sdk", sw.sdk); put("codename", sw.codename)
                put("securityPatch", sw.securityPatch); put("kernel", sw.kernel)
                put("manufacturer", sw.manufacturer); put("model", sw.model)
                put("selinux", sw.selinux); put("customRom", sw.customRom ?: "None")
                put("uptime", sw.uptime)
            })
            put("battery", JSONObject().apply {
                put("level", bat.level); put("charging", bat.charging); put("plugged", bat.plugged)
                put("health", bat.health); put("tempC", bat.temperatureC); put("voltageMv", bat.voltageMv)
                put("chemistry", bat.chemistry); put("cycles", bat.cycleCount ?: "—")
                put("fullCapacity", bat.fullCapacityMah ?: "—")
            })
            put("storage", JSONObject().apply {
                put("internalTotal", storage.internalTotal); put("internalUsed", storage.internalUsed)
                put("internalFree", storage.internalFree)
                put("partitions", JSONArray(storage.partitions.map {
                    JSONObject().apply { put("mount", it.mount); put("total", it.totalBytes); put("used", it.usedBytes) }
                }))
                put("topConsumers", JSONArray(storage.topConsumers.take(10).map {
                    JSONObject().apply { put("pkg", it.packageName); put("size", it.sizeBytes) }
                }))
            })
            put("network", JSONObject().apply {
                put("type", net.connectionType); put("vpn", net.isVpn); put("ssid", net.wifiSsid ?: "")
                put("carrier", net.carrier ?: ""); put("localIp", net.localIpv4 ?: "")
                put("publicIp", net.publicIp ?: "")
            })
            put("sensors", JSONArray(sensors.map { it.name }))
            put("apps", JSONObject().apply {
                put("count", apps.size)
                put("user", apps.count { !it.isSystem })
                put("system", apps.count { it.isSystem })
            })
            put("devEnvironment", JSONArray(dev.map {
                JSONObject().apply { put("name", it.displayName); put("found", it.found); put("source", it.source.name); put("version", it.version ?: "") }
            }))
            put("processes", JSONObject().apply {
                put("count", procs.size); put("note", procNote ?: "")
            })
            put("permissionsAudit", JSONObject().apply { put("appsWithDangerous", audit.size) })
            put("crashLog", JSONObject().apply { put("count", crashes.size); put("note", crashNote ?: "") })
        }

        val text = buildString {
            appendLine("Icy Cheak Device Report — v${BuildConfig.VERSION_NAME}")
            appendLine("Generated: ${java.util.Date()}")
            appendLine()
            appendSection("HARDWARE") {
                appendLine("SoC: ${hw.soc}"); appendLine("Board: ${hw.board}")
                appendLine("Architecture: ${hw.architecture}  ABIs: ${hw.abis.joinToString()}")
                appendLine("Cores: ${hw.cpuCoresOnline}/${hw.cpuCoresTotal} online  Sensors: ${hw.sensorCount}")
                hw.ramRows.forEach { appendLine("  ${it.label}: ${it.value}") }
                hw.displayRows.forEach { appendLine("  ${it.label}: ${it.value}") }
            }
            appendSection("SOFTWARE") {
                appendLine("Android: ${sw.androidVersion}  SDK ${sw.sdk} (${sw.codename})")
                appendLine("Security patch: ${sw.securityPatch}  Kernel: ${sw.kernel}")
                appendLine("Manufacturer: ${sw.manufacturer}  Model: ${sw.model}")
                appendLine("SELinux: ${sw.selinux}  Custom ROM: ${sw.customRom ?: "None"}  Uptime: ${sw.uptime}")
            }
            appendSection("BATTERY") {
                appendLine("Level: ${bat.level}%  ${if (bat.charging) "Charging (${bat.plugged})" else "Discharging"}")
                appendLine("Health: ${bat.health}  Temp: ${"%.1f".format(bat.temperatureC)}°C  Voltage: ${bat.voltageMv} mV")
                appendLine("Chemistry: ${bat.chemistry}  Cycles: ${bat.cycleCount ?: "—"}  Full capacity: ${bat.fullCapacityMah ?: "—"}")
            }
            appendSection("STORAGE") {
                appendLine("Internal: ${formatBytes(storage.internalUsed)} / ${formatBytes(storage.internalTotal)} used")
                storage.partitions.forEach { appendLine("  ${it.mount}: ${formatBytes(it.usedBytes)} / ${formatBytes(it.totalBytes)}") }
                appendLine("Top consumers:")
                storage.topConsumers.take(10).forEach { appendLine("  ${it.label}: ${formatBytes(it.sizeBytes)}") }
            }
            appendSection("NETWORK") {
                appendLine("Connection: ${net.connectionType}${if (net.isVpn) " (VPN)" else ""}")
                net.rows.forEach { appendLine("  ${it.label}: ${it.value}") }
            }
            appendSection("DEV ENVIRONMENT") {
                dev.forEach {
                    appendLine("  ${it.displayName}: ${if (it.found) "found (${it.source}) ${it.version ?: ""}" else "not found"}")
                }
            }
            appendSection("SUMMARY") {
                appendLine("Installed apps: ${apps.size} (user ${apps.count { !it.isSystem }}, system ${apps.count { it.isSystem }})")
                appendLine("Processes: ${procs.size}${procNote?.let { " — $it" } ?: ""}")
                appendLine("Apps with dangerous permissions: ${audit.size}")
                appendLine("Crash/ANR entries: ${crashes.size}${crashNote?.let { " — $it" } ?: ""}")
                appendLine("Sensors: ${sensors.size}")
            }
        }
        return ReportData(json = json.toString(2), text = text)
    }

    private fun StringBuilder.appendSection(title: String, block: StringBuilder.() -> Unit) {
        appendLine("=== $title ===")
        block()
        appendLine()
    }

    /**
     * Compares two previously-exported reports and returns a list of changed
     * lines. Pure (uses org.json) so it can be unit-tested.
     */
    fun diffReports(oldJson: String, newJson: String): List<DiffLine> {
        val a = runCatching { JSONObject(oldJson) }.getOrNull() ?: return emptyList()
        val b = runCatching { JSONObject(newJson) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<DiffLine>()
        fun cmp(category: String, label: String, ak: String, bk: String) {
            val ov = a.optString(ak, "—")
            val nv = b.optString(bk, "—")
            if (ov != nv) out.add(DiffLine(category, label, ov, nv))
        }
        val hwA = a.optJSONObject("hardware"); val hwB = b.optJSONObject("hardware")
        if (hwA != null && hwB != null) {
            cmp("Hardware", "SoC", "soc", "soc")
        }
        val swA = a.optJSONObject("software"); val swB = b.optJSONObject("software")
        if (swA != null && swB != null) {
            cmp("Software", "Android", "android", "android")
            cmp("Software", "Security patch", "securityPatch", "securityPatch")
        }
        val batA = a.optJSONObject("battery"); val batB = b.optJSONObject("battery")
        if (batA != null && batB != null) {
            cmp("Battery", "Level", "level", "level")
            cmp("Battery", "Health", "health", "health")
        }
        // App count delta
        val appsA = a.optJSONObject("apps"); val appsB = b.optJSONObject("apps")
        if (appsA != null && appsB != null) {
            val c1 = appsA.optInt("count", 0); val c2 = appsB.optInt("count", 0)
            if (c1 != c2) out.add(DiffLine("Apps", "Installed count", c1.toString(), c2.toString()))
        }
        return out
    }

    fun sampleForDiff(batteryLevel: Int, health: String): String = JSONObject().apply {
        put("software", JSONObject().apply { put("android", "14"); put("securityPatch", "2024-01-01") })
        put("battery", JSONObject().apply { put("level", batteryLevel); put("health", health) })
        put("apps", JSONObject().apply { put("count", 0) })
        put("hardware", JSONObject().apply { put("soc", "Test") })
    }.toString()
}
