package com.icy.devcheckplus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.icy.devcheckplus.MainActivity
import com.icy.devcheckplus.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Premium redesigned widget family — Quick Stats (default) + additional types.
 * All widgets share the same refresh scheduler and telemetry source.
 *
 * FIXED — lifecycle + performance:
 *  - telemetry is read ONCE per refresh cycle (see [WidgetMetrics.read]'s
 *    short-lived snapshot cache) and shared by every placed widget, instead of
 *    six providers each performing identical battery/RAM/CPU/storage reads;
 *  - the read now happens on [WidgetWorkExecutor]'s worker thread, never on the
 *    receiver (main) thread — [goAsync] keeps the broadcast alive until the
 *    render finishes and the PendingResult is released;
 *  - the refresh alarm exists only while at least one widget of the family is
 *    placed: [onUpdate] re-checks placement, [onDeleted] cancels when the last
 *    widget goes, [onDisabled] cancels unconditionally.
 *
 * FIXED — render robustness ("Can't load widget"):
 *  - widget layouts use the flat [R.drawable.ic_widget_logo] brand mark instead
 *    of ic_launcher_foreground, whose aapt:attr gradient broke RemoteViews
 *    inflation on several OEM widget hosts;
 *  - every render goes through [WidgetRender.renderSafely]: a minimal valid
 *    RemoteViews is created first, optional fields are populated afterwards
 *    (a failing field keeps its layout placeholder instead of killing the
 *    widget), and if the whole render still throws the provider falls back to
 *    [R.layout.widget_fallback] rather than leaving a dead widget;
 *  - no silent `catch (_: Throwable) {}` remains: every failure is logged with
 *    provider / widget id / layout / exception detail under [WidgetLog.TAG].
 */
abstract class BaseSnapshotWidgetProvider : AppWidgetProvider() {

    /** Provider label used in diagnostics. */
    abstract val providerName: String

    /** Renders one widget instance for the (shared) [snapshot]. Must never throw. */
    abstract fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        WidgetRefreshScheduler.schedule(context)
        val pending = goAsync()
        WidgetWorkExecutor.post(pending) {
            val manager = AppWidgetManager.getInstance(context) ?: return@post
            // ONE snapshot for every id of this provider (and, via the 1 s cache,
            // for every other provider firing in the same burst). A snapshot
            // failure must not kill the update: fall back to an empty snapshot
            // whose fields all render as placeholders.
            val snapshot = WidgetMetrics.readSafely(providerName, context)
            appWidgetIds.forEach { id ->
                val views = runCatching { buildViews(context, snapshot) }.getOrElse { t ->
                    WidgetLog.renderFailure(providerName, id, "onUpdate", t)
                    WidgetRender.fallbackViews(context)
                }
                try {
                    manager.updateAppWidget(id, views)
                } catch (t: Throwable) {
                    WidgetLog.updateFailure(providerName, id, t)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val isRefresh = intent.action == WidgetRefreshScheduler.ACTION_REFRESH
        val pending: BroadcastReceiver.PendingResult? = if (isRefresh) goAsync() else null
        try {
            super.onReceive(context, intent)
            if (isRefresh) {
                WidgetWorkExecutor.post(pending) { MetricsWidgetProvider.refreshAllBlocking(context) }
            }
        } catch (t: Throwable) {
            // Never leave a goAsync() result dangling; and never stay silent.
            WidgetLog.schedulerNote("onReceive(${intent.action}) failed: ${t.javaClass.simpleName}: ${t.message}")
            try {
                pending?.finish()
            } catch (_: Throwable) {}
            if (t is RuntimeException) throw t
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Cancel the shared alarm the moment the family's last widget is gone.
        if (!WidgetRefreshScheduler.hasPlacedWidgets(context)) {
            WidgetRefreshScheduler.cancel(context)
        }
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.cancel(context)
    }
}

/**
 * Shared render helpers: the "minimal first, populate after, fall back last"
 * pipeline every provider uses (see class docs).
 */
internal object WidgetRender {

    /** Section-level guard: one failing metric keeps its "--" placeholder and
     *  the rest of the widget survives. */
    inline fun section(provider: String, layout: String, name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            WidgetLog.partialRenderFailure(provider, layout, name, t)
        }
    }

    /**
     * Builds [layoutRes] defensively:
     *  1. the RemoteViews object is created first (the minimal valid widget);
     *  2. [populate] fills optional content — section-guarded by the caller;
     *  3. the click intent is attached;
     *  4. if ANYTHING throws, returns [fallbackViews] so the widget is never
     *     left dead.
     */
    fun renderSafely(
        context: Context,
        provider: String,
        layoutRes: Int,
        layoutName: String,
        requestCode: Int,
        populate: (RemoteViews) -> Unit
    ): RemoteViews {
        return try {
            val views = RemoteViews(context.packageName, layoutRes)
            try {
                populate(views)
            } catch (t: Throwable) {
                WidgetLog.partialRenderFailure(provider, layoutName, "populate", t)
            }
            try {
                views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context, requestCode))
            } catch (t: Throwable) {
                WidgetLog.partialRenderFailure(provider, layoutName, "click-intent", t)
            }
            views
        } catch (t: Throwable) {
            WidgetLog.renderFailure(provider, 0, layoutName, t)
            fallbackViews(context)
        }
    }

    /** The last line of defence: the trivial fallback layout. */
    fun fallbackViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_fallback)
        views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context, FALLBACK_REQUEST_CODE))
        return views
    }

    fun launchIntent(context: Context, requestCode: Int): PendingIntent {
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val FALLBACK_REQUEST_CODE = 99
}

class MetricsWidgetProvider : BaseSnapshotWidgetProvider() {

    override val providerName: String = "MetricsWidget"

    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

    companion object {

        /**
         * Fire-and-forget refresh of every placed widget of the family. Safe to
         * call from any thread (including Application.onCreate): the telemetry
         * pass runs on the widget worker thread.
         */
        fun refreshAll(context: Context) {
            WidgetWorkExecutor.post(pendingResult = null) { refreshAllBlocking(context.applicationContext) }
        }

        /**
         * The actual refresh cycle. Reads [WidgetMetrics] ONCE and paints every
         * provider from that snapshot, collapsing duplicate REFRESH broadcasts
         * so a burst of widget events costs a single cycle. One provider's
         * render failure is logged and skipped — it can never abort the rest.
         */
        fun refreshAllBlocking(context: Context) {
            try {
                if (WidgetWorkExecutor.shouldSkipDuplicateRefresh()) return
                WidgetWorkExecutor.markRefreshStarted()

                val manager = AppWidgetManager.getInstance(context) ?: return
                if (!WidgetRefreshScheduler.hasPlacedWidgets(context)) {
                    WidgetRefreshScheduler.cancel(context)
                    return
                }

                // Single telemetry read for the whole cycle.
                val snapshot = WidgetMetrics.readSafely("refresh-cycle", context)

                var anyPlaced = false
                WidgetRefreshScheduler.ALL_PROVIDERS.forEach { providerClass ->
                    val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
                    if (ids.isEmpty()) return@forEach
                    anyPlaced = true
                    val renderer = RENDERERS[providerClass] ?: return@forEach
                    ids.forEach { id ->
                        val views = runCatching { renderer(context, snapshot) }.getOrElse { t ->
                            WidgetLog.renderFailure(providerClass.simpleName ?: "widget", id, "refresh", t)
                            WidgetRender.fallbackViews(context)
                        }
                        try {
                            manager.updateAppWidget(id, views)
                        } catch (t: Throwable) {
                            WidgetLog.updateFailure(providerClass.simpleName ?: "widget", id, t)
                        }
                    }
                }
                if (!anyPlaced) {
                    WidgetRefreshScheduler.cancel(context)
                }
            } catch (t: Throwable) {
                WidgetLog.schedulerNote("refresh cycle aborted: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        /** One renderer per provider class — keeps the refresh loop flat. */
        private val RENDERERS: Map<Class<*>, (Context, WidgetSnapshot) -> RemoteViews> = mapOf(
            MetricsWidgetProvider::class.java to { c, s -> buildViews(c, s) },
            CompactWidgetProvider::class.java to { c, s -> CompactWidgetProvider.buildViews(c, s) },
            BatteryWidgetProvider::class.java to { c, s -> BatteryWidgetProvider.buildViews(c, s) },
            DeviceOverviewWidgetProvider::class.java to { c, s -> DeviceOverviewWidgetProvider.buildViews(c, s) },
            PerformanceWidgetProvider::class.java to { c, s -> PerformanceWidgetProvider.buildViews(c, s) },
            MinimalWidgetProvider::class.java to { c, s -> MinimalWidgetProvider.buildViews(c, s) },
            NetworkWidgetProvider::class.java to { c, s -> NetworkWidgetProvider.buildViews(c, s) }
        )

        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
            WidgetRender.renderSafely(context, "MetricsWidget", R.layout.widget_metrics, "widget_metrics", 0) { views ->
                WidgetRender.section("MetricsWidget", "widget_metrics", "timestamp") {
                    views.setTextViewText(
                        R.id.widget_updated,
                        SimpleDateFormat("HH:mm", Locale.US).format(Date())
                    )
                }

                // Battery — "--"/placeholder stays if this section alone fails.
                WidgetRender.section("MetricsWidget", "widget_metrics", "battery") {
                    if (snapshot.batteryPercent in 0..100) {
                        views.setTextViewText(R.id.widget_battery_value, "${snapshot.batteryPercent}%")
                        val sub = buildString {
                            if (snapshot.batteryCharging) append("charging")
                            else append("on battery")
                            snapshot.batteryTempC?.let { append(" • ${it.toInt()}°C") }
                        }
                        views.setTextViewText(R.id.widget_battery_sub, sub)
                        views.setTextColor(R.id.widget_battery_value, batteryColor(context, snapshot.batteryPercent))
                    } else {
                        views.setTextViewText(R.id.widget_battery_value, context.getString(R.string.widget_placeholder))
                        views.setTextViewText(R.id.widget_battery_sub, "no data")
                    }
                }

                // RAM
                WidgetRender.section("MetricsWidget", "widget_metrics", "ram") {
                    if (snapshot.ramPercent in 0..100) {
                        views.setTextViewText(R.id.widget_ram_value, "${snapshot.ramPercent}%")
                        views.setTextViewText(
                            R.id.widget_ram_sub,
                            String.format(Locale.US, "%.1f/%.1f GB", snapshot.ramUsedMb / 1024f, snapshot.ramTotalMb / 1024f)
                        )
                    } else {
                        views.setTextViewText(R.id.widget_ram_value, context.getString(R.string.widget_placeholder))
                        views.setTextViewText(R.id.widget_ram_sub, "no data")
                    }
                }

                // CPU
                WidgetRender.section("MetricsWidget", "widget_metrics", "cpu") {
                    if (snapshot.cpuReadable && snapshot.cpuFreqMhz > 0f) {
                        views.setTextViewText(R.id.widget_cpu_value, formatFrequency(snapshot.cpuFreqMhz))
                        views.setTextViewText(R.id.widget_cpu_sub, "${snapshot.cpuCoreCount} cores")
                    } else {
                        views.setTextViewText(R.id.widget_cpu_value, context.getString(R.string.widget_placeholder))
                        views.setTextViewText(R.id.widget_cpu_sub, "not readable")
                        views.setTextColor(R.id.widget_cpu_value, context.getColor(R.color.widget_warn))
                    }
                }

                // Footer
                WidgetRender.section("MetricsWidget", "widget_metrics", "footer") {
                    views.setTextViewText(R.id.widget_device_model, snapshot.deviceModel)
                    views.setTextViewText(R.id.widget_network, snapshot.networkType)
                }
            }

        private fun batteryColor(context: Context, percent: Int): Int = when {
            percent <= 10 -> context.getColor(R.color.widget_critical)
            percent <= 25 -> context.getColor(R.color.widget_warn)
            else -> context.getColor(R.color.widget_accent)
        }

        fun formatFrequency(mhz: Float): String =
            if (mhz >= 1000f) String.format(Locale.US, "%.2f GHz", mhz / 1000f)
            else String.format(Locale.US, "%.0f MHz", mhz)
    }
}

// Additional widget providers for family.
// Lifecycle (scheduling/cancel/off-thread refresh) is inherited from
// [BaseSnapshotWidgetProvider]; each subclass only renders its own layout.

class CompactWidgetProvider : BaseSnapshotWidgetProvider() {
    override val providerName: String = "CompactWidget"

    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
            WidgetRender.renderSafely(context, "CompactWidget", R.layout.widget_compact, "widget_compact", 1) { views ->
                WidgetRender.section("CompactWidget", "widget_compact", "stats") {
                    views.setTextViewText(R.id.compact_battery, if (snapshot.batteryPercent in 0..100) "${snapshot.batteryPercent}%" else "--")
                    views.setTextViewText(R.id.compact_ram, if (snapshot.ramPercent in 0..100) "${snapshot.ramPercent}%" else "--")
                    views.setTextViewText(
                        R.id.compact_cpu,
                        if (snapshot.cpuReadable && snapshot.cpuFreqMhz > 0f) MetricsWidgetProvider.formatFrequency(snapshot.cpuFreqMhz) else "--"
                    )
                }
            }
    }
}

class BatteryWidgetProvider : BaseSnapshotWidgetProvider() {
    override val providerName: String = "BatteryWidget"

    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
            WidgetRender.renderSafely(context, "BatteryWidget", R.layout.widget_battery, "widget_battery", 2) { views ->
                WidgetRender.section("BatteryWidget", "widget_battery", "stats") {
                    views.setTextViewText(R.id.battery_percent, if (snapshot.batteryPercent in 0..100) "${snapshot.batteryPercent}%" else "--")
                    views.setTextViewText(R.id.battery_status, if (snapshot.batteryCharging) "Charging" else "On battery")
                    views.setTextViewText(R.id.battery_temp, snapshot.batteryTempC?.let { "${it.toInt()}°C" } ?: "--")
                    views.setTextViewText(R.id.battery_voltage, snapshot.batteryVoltageMv?.let { "$it mV" } ?: "--")
                    views.setTextViewText(R.id.battery_health, snapshot.batteryHealth ?: "Unknown")
                }
            }
    }
}

class DeviceOverviewWidgetProvider : BaseSnapshotWidgetProvider() {
    override val providerName: String = "DeviceOverviewWidget"

    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
            WidgetRender.renderSafely(context, "DeviceOverviewWidget", R.layout.widget_device_overview, "widget_device_overview", 3) { views ->
                WidgetRender.section("DeviceOverviewWidget", "widget_device_overview", "device") {
                    views.setTextViewText(R.id.device_model, snapshot.deviceModel)
                    views.setTextViewText(R.id.device_android, snapshot.androidVersion)
                }
                WidgetRender.section("DeviceOverviewWidget", "widget_device_overview", "ram-storage") {
                    views.setTextViewText(
                        R.id.device_ram,
                        if (snapshot.ramPercent in 0..100) "${snapshot.ramPercent}% • ${snapshot.ramTotalMb / 1024} GB" else "--"
                    )
                    views.setTextViewText(
                        R.id.device_storage,
                        if (snapshot.storagePercent in 0..100) {
                            "${snapshot.storagePercent}% • " +
                                String.format(Locale.US, "%.1f/%.1f GB", snapshot.storageUsedGb, snapshot.storageTotalGb)
                        } else "--"
                    )
                }
            }
    }
}

class PerformanceWidgetProvider : BaseSnapshotWidgetProvider() {
    override val providerName: String = "PerformanceWidget"

    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
            WidgetRender.renderSafely(context, "PerformanceWidget", R.layout.widget_performance, "widget_performance", 4) { views ->
                WidgetRender.section("PerformanceWidget", "widget_performance", "stats") {
                    views.setTextViewText(
                        R.id.perf_cpu,
                        if (snapshot.cpuReadable && snapshot.cpuFreqMhz > 0f) MetricsWidgetProvider.formatFrequency(snapshot.cpuFreqMhz) else "N/A"
                    )
                    views.setTextViewText(R.id.perf_ram, if (snapshot.ramPercent in 0..100) "${snapshot.ramPercent}%" else "--")
                    views.setTextViewText(R.id.perf_temp, snapshot.batteryTempC?.let { "${it.toInt()}°C" } ?: "--")
                    views.setTextViewText(R.id.perf_cores, "${snapshot.cpuCoreCount} cores")
                }
            }
    }
}

class MinimalWidgetProvider : BaseSnapshotWidgetProvider() {
    override val providerName: String = "MinimalWidget"

    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
            WidgetRender.renderSafely(context, "MinimalWidget", R.layout.widget_minimal, "widget_minimal", 5) { views ->
                WidgetRender.section("MinimalWidget", "widget_minimal", "stats") {
                    views.setTextViewText(R.id.minimal_value, if (snapshot.batteryPercent in 0..100) "${snapshot.batteryPercent}%" else "--")
                    views.setTextViewText(R.id.minimal_label, "Battery • ${snapshot.deviceModel}")
                }
            }
    }
}

/**
 * Network widget — previously an orphaned layout + strings with no provider,
 * metadata or manifest entry (a half-built feature). Now fully wired: provider
 * class here, metadata in res/xml/widget_network_info.xml, receiver registered
 * in AndroidManifest.xml, and part of the shared refresh scheduler.
 */
class NetworkWidgetProvider : BaseSnapshotWidgetProvider() {
    override val providerName: String = "NetworkWidget"

    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
            WidgetRender.renderSafely(context, "NetworkWidget", R.layout.widget_network, "widget_network", 6) { views ->
                WidgetRender.section("NetworkWidget", "widget_network", "state") {
                    val type = snapshot.networkType.ifBlank { "Unknown" }
                    val offline = type.equals("Offline", ignoreCase = true) ||
                        type.equals("Unknown", ignoreCase = true)
                    views.setTextViewText(R.id.network_type, type)
                    views.setTextViewText(
                        R.id.network_status,
                        snapshot.networkExtra ?: if (offline) "No connection" else "—"
                    )
                    views.setTextColor(
                        R.id.network_type,
                        context.getColor(if (offline) R.color.widget_warn else R.color.widget_accent)
                    )
                }
            }
    }
}
