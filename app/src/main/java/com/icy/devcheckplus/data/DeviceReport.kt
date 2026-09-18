package com.icy.devcheckplus.data

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import com.icy.devcheckplus.BuildConfig
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Output flavours offered by the share sheet. */
enum class ReportFormat(val label: String) {
    TEXT("Plain text"),
    JSON("JSON")
}

/**
 * The nine blocks the report can contain.
 *
 * Persisted as a bitmask (one bit per section) in `devcheck_settings`, edited
 * from Settings → Export & share → "What is included". [reportTitle] is the
 * heading used in the rendered output.
 */
enum class ReportSection(val label: String, val reportTitle: String, val bit: Long) {
    HARDWARE("Hardware", "HARDWARE", 1L shl 0),
    SOFTWARE("Software", "SOFTWARE", 1L shl 1),
    BATTERY("Battery", "BATTERY", 1L shl 2),
    STORAGE("Storage", "STORAGE", 1L shl 3),
    NETWORK("Network", "NETWORK", 1L shl 4),
    PROCESSES("Processes", "PROCESSES", 1L shl 5),
    APPS("Installed apps", "INSTALLED APPS", 1L shl 6),
    SENSORS("Sensors", "SENSORS", 1L shl 7),
    TELEMETRY("Live telemetry", "LIVE TELEMETRY", 1L shl 8);

    companion object {
        val ALL_MASK: Long = values().fold(0L) { acc, section -> acc or section.bit }

        fun fromMask(mask: Long): Set<ReportSection> =
            values().filter { mask and it.bit != 0L }.toSet()

        fun toMask(sections: Set<ReportSection>): Long =
            sections.fold(0L) { acc, section -> acc or section.bit }
    }
}

private data class ReportCategory(
    val name: String,
    val sections: List<InfoSection>
)

private data class ReportModel(
    val appVersion: String,
    val generatedAt: String,
    val generatedAtIso: String,
    val timeZone: String,
    val device: List<InfoItem>,
    val privilege: List<InfoItem>,
    val categories: List<ReportCategory>
)

/**
 * Builds a full device report out of every category the app can inspect and
 * hands it to Android's native share sheet.
 *
 * Two renderings of the *same* collected data are supported: a readable
 * plain-text dump and a structured JSON document.
 *
 * Robustness rules:
 *  - every category is collected on [Dispatchers.IO] behind its own
 *    [withTimeoutOrNull] watchdog, so one slow privileged call (a root prompt
 *    the user never answers, a hung shell) can never freeze the export or the
 *    UI — that category simply reports "Unavailable — request timed out";
 *  - categories are collected in parallel, so the whole export costs roughly
 *    the slowest single category instead of the sum;
 *  - the payload is capped before it goes into the Intent, because Binder
 *    transactions fail above ~1 MB.
 */
object DeviceReport {

    /** Per-category watchdog. */
    private const val CATEGORY_TIMEOUT_MS = 20_000L

    /** Live sensor sampling window (SENSOR_DELAY_NORMAL ≈ 200 ms per sensor). */
    private const val SENSOR_WINDOW_MS = 900L

    private const val MAX_APPS = 400
    private const val MAX_PROCESSES = 250
    private const val MAX_SHARE_CHARS = 700_000
    private const val LABEL_WIDTH = 26

    /** Collects every category and renders it in the requested [format]. */
    suspend fun build(context: Context, format: ReportFormat): String = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val model = collect(appContext)
        val body = if (format == ReportFormat.JSON) renderJson(model) else renderText(model)
        if (body.length > MAX_SHARE_CHARS) {
            body.take(MAX_SHARE_CHARS) +
                "\n\n[... report truncated at ${MAX_SHARE_CHARS / 1000}k characters for sharing ...]"
        } else {
            body
        }
    }

    /** Opens the system share sheet with [body] as shareable text. */
    fun share(context: Context, format: ReportFormat, body: String) {
        val subject = "DevCheck+ device report (${format.label})"
        val send = Intent(Intent.ACTION_SEND).apply {
            // text/plain keeps every target app (Messages, mail, Discord,
            // clipboard tools) able to receive the payload, JSON included.
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TITLE, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(send, "Share device report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /* ------------------------------------------------------------------ */
    /*  Collection                                                         */
    /* ------------------------------------------------------------------ */

    private suspend fun collect(context: Context): ReportModel = coroutineScope {
        // Sections deselected in Settings are never collected: no privileged
        // shell, no sensor sampling window, no package scan for data that would
        // only be thrown away. That also makes the export visibly faster.
        val selected = AppSettingsStore.reportSectionsNow()

        val telemetry = if (ReportSection.TELEMETRY in selected) {
            async { guarded(context, "Live telemetry") { telemetrySections(it) } }
        } else null
        val hardware = if (ReportSection.HARDWARE in selected) {
            async { guarded(context, "Hardware") { HardwareDataProvider.getHardwareSections(it) } }
        } else null
        val software = if (ReportSection.SOFTWARE in selected) {
            async { guarded(context, "Software") { SoftwareDataProvider.getSoftwareSections(it) } }
        } else null
        val battery = if (ReportSection.BATTERY in selected) {
            async { guarded(context, "Battery") { BatteryDataProvider.getBatterySections(it) } }
        } else null
        val storage = if (ReportSection.STORAGE in selected) {
            async { guarded(context, "Storage") { storageSections(it) } }
        } else null
        val network = if (ReportSection.NETWORK in selected) {
            async {
                guarded(context, "Network") {
                    NetworkDataProvider.getNetworkSections(it, AppSettingsStore.publicIpLookupEnabled(it))
                }
            }
        } else null
        val processes = if (ReportSection.PROCESSES in selected) {
            async { guarded(context, "Processes") { processSections(it) } }
        } else null
        val apps = if (ReportSection.APPS in selected) {
            async { guarded(context, "Installed apps") { appSections(it) } }
        } else null
        val sensors = if (ReportSection.SENSORS in selected) {
            async { guarded(context, "Sensors") { sensorSections(it) } }
        } else null

        val now = Date()
        val zone = TimeZone.getDefault()
        val human = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply { timeZone = zone }
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { timeZone = zone }

        ReportModel(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            generatedAt = human.format(now),
            generatedAtIso = iso.format(now),
            timeZone = zone.id ?: "unknown",
            device = deviceItems(),
            privilege = privilegeItems(),
            categories = listOfNotNull(
                telemetry?.let { ReportCategory(ReportSection.TELEMETRY.reportTitle, it.await()) },
                hardware?.let { ReportCategory(ReportSection.HARDWARE.reportTitle, it.await()) },
                software?.let { ReportCategory(ReportSection.SOFTWARE.reportTitle, it.await()) },
                battery?.let { ReportCategory(ReportSection.BATTERY.reportTitle, it.await()) },
                storage?.let { ReportCategory(ReportSection.STORAGE.reportTitle, it.await()) },
                network?.let { ReportCategory(ReportSection.NETWORK.reportTitle, it.await()) },
                processes?.let { ReportCategory(ReportSection.PROCESSES.reportTitle, it.await()) },
                apps?.let { ReportCategory(ReportSection.APPS.reportTitle, it.await()) },
                sensors?.let { ReportCategory(ReportSection.SENSORS.reportTitle, it.await()) }
            )
        )
    }

    /**
     * Runs one category behind a watchdog. A timeout, a cancelled job or any
     * throwable degrades to a single explanatory section instead of failing the
     * whole report.
     */
    private suspend fun guarded(
        context: Context,
        label: String,
        block: suspend (Context) -> List<InfoSection>
    ): List<InfoSection> {
        return try {
            val result = withTimeoutOrNull(CATEGORY_TIMEOUT_MS) { block(context) }
            result ?: unavailable(label, "Unavailable — request timed out")
        } catch (interrupted: kotlinx.coroutines.CancellationException) {
            throw interrupted
        } catch (t: Throwable) {
            unavailable(label, "Unavailable — ${t.javaClass.simpleName}")
        }
    }

    private fun unavailable(label: String, message: String): List<InfoSection> =
        listOf(InfoSection(label, listOf(InfoItem("Status", message, requiresPrivilege = true))))

    private suspend fun storageSections(context: Context): List<InfoSection> {
        val (sections, partitions) = StorageDataProvider.getStorageSections(context)
        if (partitions.isEmpty()) return sections
        return sections + InfoSection(
            title = "Partitions",
            items = partitions.map {
                InfoItem(
                    title = it.mountPoint,
                    value = "${it.usedFormatted} / ${it.totalFormatted} (${it.usedPercent}%)",
                    subtitle = "${it.filesystem} • ${it.freeFormatted} free"
                )
            }
        )
    }

    private suspend fun processSections(context: Context): List<InfoSection> {
        val (processes, error) = ProcessDataProvider.getProcesses()
        val sections = mutableListOf<InfoSection>()
        sections.add(
            InfoSection(
                title = "Running processes (${processes.size})",
                items = processes.take(MAX_PROCESSES).map {
                    InfoItem(
                        title = it.name,
                        value = "PID ${it.pid} • CPU ${it.cpuPercent} • RSS ${it.memRss}",
                        subtitle = "${it.user} • ${it.status}"
                    )
                }
            )
        )
        if (processes.size > MAX_PROCESSES) {
            sections.add(
                InfoSection(
                    title = "Note",
                    items = listOf(
                        InfoItem("Truncated", "${processes.size - MAX_PROCESSES} further processes omitted from this report")
                    )
                )
            )
        }
        if (!error.isNullOrBlank()) {
            sections.add(InfoSection("Processes status", listOf(InfoItem("Detail", error, requiresPrivilege = true))))
        }
        return sections
    }

    private suspend fun appSections(context: Context): List<InfoSection> {
        val apps = AppsDataProvider.getInstalledApps(context)
        val system = apps.count { it.isSystemApp }
        val summary = InfoSection(
            title = "Installed apps",
            items = listOf(
                InfoItem("Total packages", apps.size.toString()),
                InfoItem("System apps", system.toString()),
                InfoItem("User apps", (apps.size - system).toString())
            )
        )
        val list = InfoSection(
            title = "Package list",
            items = apps.take(MAX_APPS).map {
                InfoItem(
                    title = it.appName,
                    value = it.versionName,
                    subtitle = "${it.packageName}${if (it.isSystemApp) " • system" else " • user"}"
                )
            }
        )
        val truncated = if (apps.size > MAX_APPS) {
            listOf(
                InfoSection(
                    title = "Note",
                    items = listOf(InfoItem("Truncated", "${apps.size - MAX_APPS} further packages omitted from this report"))
                )
            )
        } else {
            emptyList()
        }
        return listOf(summary, list) + truncated
    }

    private suspend fun sensorSections(context: Context): List<InfoSection> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return unavailable("Sensors", "Unavailable — no sensor service")
        val all = runCatching { manager.getSensorList(Sensor.TYPE_ALL) }.getOrNull().orEmpty()
        if (all.isEmpty()) return unavailable("Sensors", "Unavailable — no sensors reported")

        val live = captureSensorValues(context)
        val items = all.map { sensor ->
            val reading = live[sensor.type]
            InfoItem(
                title = sensor.name,
                value = reading?.let { formatSensorValues(it) } ?: "No reading in sample window",
                subtitle = "type ${sensor.type} • ${sensor.vendor} • max ${sensor.maximumRange}" +
                    " • res ${sensor.resolution} • ${sensor.power} mA"
            )
        }
        return listOf(
            InfoSection("Sensors (${all.size})", items),
            InfoSection(
                "Sensor capture",
                items = listOf(
                    InfoItem("Live values", "${live.size} of ${all.size} sensors reported within ${SENSOR_WINDOW_MS.toInt()} ms"),
                    InfoItem("Sampling rate", "SENSOR_DELAY_NORMAL (~200 ms)")
                )
            )
        )
    }

    /**
     * Short, bounded live capture: registers the common sensor types, waits one
     * fixed window, then unregisters. Never blocks longer than the window and
     * always returns whatever arrived.
     */
    private suspend fun captureSensorValues(context: Context): Map<Int, FloatArray> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyMap()
        val types = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_STEP_COUNTER,
            Sensor.TYPE_AMBIENT_TEMPERATURE,
            Sensor.TYPE_RELATIVE_HUMIDITY
        )
        val sensors = types.mapNotNull { runCatching { manager.getDefaultSensor(it) }.getOrNull() }
        if (sensors.isEmpty()) return emptyMap()

        return withContext(Dispatchers.Main) {
            val collected = mutableMapOf<Int, FloatArray>()
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val e = event ?: return
                    val sensor = e.sensor ?: return
                    if (!collected.containsKey(sensor.type)) {
                        collected[sensor.type] = e.values.copyOf()
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensors.forEach { runCatching { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) } }
            delay(SENSOR_WINDOW_MS)
            runCatching { manager.unregisterListener(listener) }
            collected.toMap()
        }
    }

    /** One extra sample so CPU/RAM/battery numbers are present even if those tabs were never opened. */
    private suspend fun telemetrySections(context: Context): List<InfoSection> {
        val metrics = try {
            withTimeoutOrNull(CATEGORY_TIMEOUT_MS) { LiveMetricsRepository.sample(context) }
        } catch (t: Throwable) {
            null
        } ?: LiveMetricsRepository.snapshot()

        val cpuValue = if (metrics.cpuReadable) {
            formatFrequency(metrics.latestAverageFreqMhz)
        } else {
            "Unavailable — requires root or Shizuku"
        }
        val perCore = if (metrics.cpuReadable && metrics.coreFreqMhz.isNotEmpty()) {
            metrics.coreFreqMhz.joinToString("  ") { core ->
                formatFrequency(core.lastOrNull() ?: 0f)
            }
        } else {
            "Unavailable"
        }
        val ramPercent = if (metrics.totalRamMb > 0) {
            metrics.usedRamMb * 100f / metrics.totalRamMb
        } else {
            0f
        }

        return listOf(
            InfoSection(
                title = "Live telemetry (${formatSamplingInterval(LiveMetricsRepository.intervalMs)} sampling)",
                items = listOf(
                    InfoItem("Samples collected", metrics.sampleCount.toString()),
                    InfoItem("CPU cores", metrics.coreCount.toString()),
                    InfoItem("Average CPU frequency", cpuValue, requiresPrivilege = !metrics.cpuReadable),
                    InfoItem("Per-core frequency", perCore, requiresPrivilege = !metrics.cpuReadable),
                    InfoItem(
                        "RAM used",
                        if (metrics.totalRamMb > 0) "${metrics.usedRamMb} MB of ${metrics.totalRamMb} MB" else "Unavailable",
                        subtitle = if (metrics.totalRamMb > 0) String.format(Locale.US, "%.1f%% used • %d MB available", ramPercent, metrics.availableRamMb) else null
                    ),
                    InfoItem("Battery level", if (metrics.batteryLevel >= 0) "${metrics.batteryLevel}%" else "Unavailable"),
                    InfoItem("Charging", if (metrics.batteryLevel >= 0) (if (metrics.batteryCharging) "Yes" else "No") else "Unavailable"),
                    InfoItem(
                        "Battery temperature",
                        if (metrics.temperatureReadable) String.format(Locale.US, "%.1f °C", metrics.latestTempC) else "Unavailable"
                    ),
                    InfoItem(
                        "Battery current",
                        if (metrics.currentReadable) String.format(Locale.US, "%+.0f mA", metrics.latestCurrentMa) else "Unavailable"
                    )
                )
            )
        )
    }

    private fun deviceItems(): List<InfoItem> = listOf(
        InfoItem("Manufacturer", Build.MANUFACTURER),
        InfoItem("Model", Build.MODEL),
        InfoItem("Device", Build.DEVICE),
        InfoItem("Board", Build.BOARD),
        InfoItem("Hardware", Build.HARDWARE),
        InfoItem("Android version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        InfoItem("Security patch", Build.VERSION.SECURITY_PATCH),
        InfoItem("Build ID", Build.ID),
        InfoItem("Build type", Build.TYPE),
        InfoItem("Bootloader", Build.BOOTLOADER),
        InfoItem("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", "))
    )

    private fun deviceFingerprint(): String = runCatching { Build.FINGERPRINT }.getOrDefault("unknown")

    private fun privilegeItems(): List<InfoItem> {
        val status = PrivilegeManager.status.value
        return listOf(
            InfoItem("Preferred mode", status.preferredMode.name),
            InfoItem("Active mode", status.activeMode.name),
            InfoItem("Root granted", if (status.rootGranted) "Yes" else "No"),
            InfoItem("Root binary present", if (status.rootAvailable) "Yes" else "No"),
            InfoItem("Shizuku running", if (status.shizukuRunning) "Yes" else "No"),
            InfoItem("Shizuku granted", if (status.shizukuGranted) "Yes" else "No")
        )
    }

    private fun formatFrequency(mhz: Float): String =
        if (mhz >= 1000f) String.format(Locale.US, "%.2f GHz", mhz / 1000f) else String.format(Locale.US, "%.0f MHz", mhz)

    private fun formatSensorValues(values: FloatArray): String =
        values.take(3).joinToString(", ") { String.format(Locale.US, "%.3f", it) }

    /* ------------------------------------------------------------------ */
    /*  Plain-text rendering                                               */
    /* ------------------------------------------------------------------ */

    private fun renderText(model: ReportModel): String {
        val sb = StringBuilder(64 * 1024)
        val rule = "=".repeat(64)
        val thin = "-".repeat(64)

        sb.append(rule).append('\n')
        sb.append(" DevCheck+ — Device Report\n")
        sb.append(rule).append('\n')
        sb.append(row("App version", model.appVersion))
        sb.append(row("Generated", model.generatedAt))
        sb.append(row("Time zone", model.timeZone))
        sb.append(row("Fingerprint", deviceFingerprint()))
        sb.append('\n')

        sb.append(thin).append('\n')
        sb.append("DEVICE\n")
        sb.append(thin).append('\n')
        model.device.forEach { sb.append(row(it.title, it.value)) }
        sb.append('\n')

        sb.append(thin).append('\n')
        sb.append("PRIVILEGE\n")
        sb.append(thin).append('\n')
        model.privilege.forEach { sb.append(row(it.title, it.value)) }
        sb.append('\n')

        model.categories.forEach { category ->
            sb.append(rule).append('\n')
            sb.append(category.name).append('\n')
            sb.append(rule).append('\n')
            if (category.sections.isEmpty()) {
                sb.append("  (no data)\n")
            }
            category.sections.forEach { section ->
                sb.append("\n--- ").append(section.title).append(" ---\n")
                section.items.forEach { item ->
                    sb.append(row(item.title, item.value))
                    if (!item.subtitle.isNullOrBlank()) {
                        sb.append("      ").append(item.subtitle).append('\n')
                    }
                }
            }
            sb.append('\n')
        }

        sb.append(thin).append('\n')
        sb.append("Generated by DevCheck+ v").append(model.appVersion).append(" on ").append(model.generatedAt).append('\n')
        return sb.toString()
    }

    private fun row(label: String, value: String): String {
        val safeLabel = if (label.length > LABEL_WIDTH) label.take(LABEL_WIDTH - 1) + "…" else label
        return "  " + safeLabel.padEnd(LABEL_WIDTH) + " : " + value + "\n"
    }

    /* ------------------------------------------------------------------ */
    /*  JSON rendering (hand-rolled: no extra dependency, valid escaping)   */
    /* ------------------------------------------------------------------ */

    private fun renderJson(model: ReportModel): String = jsonObject(
        "app" to jsonObject(
            "name" to jsonString("DevCheck+"),
            "version" to jsonString(BuildConfig.VERSION_NAME),
            "versionCode" to BuildConfig.VERSION_CODE.toString()
        ),
        "generatedAt" to jsonString(model.generatedAtIso),
        "generatedAtLocal" to jsonString(model.generatedAt),
        "timeZone" to jsonString(model.timeZone),
        "device" to jsonArray(model.device.map { itemJson(it) }),
        "deviceFingerprint" to jsonString(deviceFingerprint()),
        "privilege" to jsonArray(model.privilege.map { itemJson(it) }),
        "categories" to jsonArray(
            model.categories.map { category ->
                jsonObject(
                    "name" to jsonString(category.name),
                    "sections" to jsonArray(
                        category.sections.map { section ->
                            jsonObject(
                                "title" to jsonString(section.title),
                                "items" to jsonArray(section.items.map { itemJson(it) })
                            )
                        }
                    )
                )
            }
        )
    )

    private fun itemJson(item: InfoItem): String = jsonObject(
        "title" to jsonString(item.title),
        "value" to jsonString(item.value),
        "subtitle" to jsonString(item.subtitle),
        "requiresPrivilege" to item.requiresPrivilege.toString(),
        "privilegeSource" to jsonString(item.privilegeSource)
    )

    private fun jsonObject(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(separator = ",", prefix = "{", postfix = "}") {
            jsonString(it.first) + ":" + it.second
        }

    private fun jsonArray(items: List<String>): String =
        items.joinToString(separator = ",", prefix = "[", postfix = "]")

    private fun jsonString(value: String?): String {
        if (value == null) return "null"
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (ch < ' ') {
                    sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
