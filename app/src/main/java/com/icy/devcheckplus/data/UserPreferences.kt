package com.icy.devcheckplus.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/* ------------------------------------------------------------------ */
/*  Option types                                                       */
/* ------------------------------------------------------------------ */

/**
 * One global cadence for *everything* that updates live.
 *
 * Before this existed the app had a "polling interval" that only the telemetry
 * ticker read: the sensor publisher was hardwired to 500 ms, the log viewer to
 * 3 s and the dashboard pins to manual refresh. The user therefore had no single
 * knob for "how hard should this app work", and turning the telemetry poll down
 * did not reduce the rest of the live cost.
 *
 * Every live surface now derives its cadence from this one value:
 *  - [com.icy.devcheckplus.data.LiveMetricsPoller] — CPU / RAM / battery ticker;
 *  - [com.icy.devcheckplus.data.SensorLiveMonitor] — publish rate + sensor delay;
 *  - the log viewer's auto-refresh and the dashboard's pinned-value re-read.
 *
 * The trade-off is stated in the UI through [tagline] and [cost]: the fastest
 * setting is explicitly labelled as costing battery and CPU, because each
 * telemetry sample can involve a privileged shell read.
 */
enum class RefreshRate(
    val label: String,
    val tagline: String,
    val intervalMs: Long,
    val cost: String
) {
    BATTERY_SAVER(
        label = "Battery Saver",
        tagline = "2 s • lowest power",
        intervalMs = 2_000L,
        cost = "Half an update per second. Fewest wake-ups and fewest privileged reads — " +
            "the best choice for battery, and the coarsest charts."
    ),
    BALANCED(
        label = "Balanced",
        tagline = "1 s • default",
        intervalMs = 1_000L,
        cost = "One update per second. The default trade-off between smoothness and battery use."
    ),
    SMOOTH(
        label = "Smooth",
        tagline = "0.5 s • fluid",
        intervalMs = 500L,
        cost = "Two updates per second. Charts and sensors look fluid; modest extra CPU and battery use."
    ),
    REAL_TIME(
        label = "Real-time",
        tagline = "0.25 s • fastest",
        intervalMs = 250L,
        cost = "Four updates per second. Real-time may increase battery and CPU usage, and every " +
            "sample can cost one privileged shell read."
    );

    /** Cadence used for *deep* re-reads (dashboard pins, logcat), which are far
     *  more expensive than one telemetry sample and therefore deliberately run
     *  several refresh ticks apart. */
    val deepReadIntervalMs: Long
        get() = (intervalMs * DEEP_READ_MULTIPLIER).coerceAtLeast(DEEP_READ_FLOOR_MS)

    companion object {
        val DEFAULT = BALANCED

        /** Deep reads never happen faster than every 5 s, even at Real-time. */
        private const val DEEP_READ_FLOOR_MS = 5_000L

        /** Deep read cadence = 10 refresh ticks. */
        private const val DEEP_READ_MULTIPLIER = 10L

        fun fromKey(raw: String?): RefreshRate =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT

        /** Closest match for a legacy stored interval in milliseconds. */
        fun nearest(ms: Long): RefreshRate =
            values().minByOrNull { abs(it.intervalMs - ms) } ?: DEFAULT
    }
}

/**
 * App-wide accent. [seed] is an ARGB value rather than a `Color` so this stays a
 * plain data-layer type; `ui/theme/Accent.kt` turns it into a Material 3 scheme.
 */
enum class AccentPalette(val label: String, val tagline: String, val seed: Long, val companion: Long) {
    DEFAULT("Default", "Theme cyan", 0xFF00D2FF, 0xFF9D7BFF),
    OCEAN("Ocean", "Deep blue", 0xFF4C8DFF, 0xFF00D2FF),
    VIOLET("Violet", "Neon purple", 0xFF9D7BFF, 0xFFFF6FA5),
    EMERALD("Emerald", "Mint green", 0xFF2EE6C5, 0xFF3FB950),
    AMBER("Amber", "Warm gold", 0xFFFFB020, 0xFFFF8A3D),
    ROSE("Rose", "Soft pink", 0xFFFF6FA5, 0xFF9D7BFF);

    companion object {
        fun fromKey(raw: String?): AccentPalette =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/** Gradient treatment for glass/card surfaces. */
enum class GradientStyle(val label: String, val tagline: String) {
    DEFAULT("Default", "Theme glass tint"),
    SOLID("Solid", "Flat, no gradient"),
    OCEAN("Ocean", "Cyan → deep blue"),
    SUNSET("Sunset", "Amber → magenta"),
    VOID("Void", "Violet → black"),
    /** Painted from [CustomGradient]: the user's own colours, angle or radius. */
    CUSTOM("Custom", "Your colours, your angle");

    companion object {
        fun fromKey(raw: String?): GradientStyle =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * A gradient the user built themselves (Settings › Colors & Theming › Custom).
 *
 * Colours are ARGB `Long`s so this stays a plain data-layer type — `ui/theme`
 * turns them into `Color`s the same way it does for [AccentPalette.seed].
 *
 * [angleDegrees] is a CSS-style direction for the linear case: 0° runs left →
 * right, 90° top → bottom, 135° is the classic corner-to-corner diagonal.
 * [radial] ignores the angle and radiates from the centre of whatever it paints.
 *
 * There is deliberately no `require` in here: a corrupt or truncated record in
 * DataStore must degrade to a fallback gradient, never crash the theme at start.
 */
data class CustomGradient(
    val id: String,
    val name: String,
    val colors: List<Long>,
    val angleDegrees: Int = 135,
    val radial: Boolean = false
) {
    /** Human-readable direction, used by the picker and the preset list. */
    val directionLabel: String
        get() = if (radial) "Radial" else "$angleDegrees°"

    /**
     * One record of the stored list: `id~name~angle~radial~#AARRGGBB,#AARRGGBB`.
     * Names are sanitised first so a stray separator can never split a record.
     */
    fun serialize(): String = listOf(
        id,
        sanitizeName(name),
        angleDegrees.coerceIn(0, 359).toString(),
        if (radial) "1" else "0",
        colors.take(MAX_STOPS).joinToString(COLOR_SEPARATOR.toString()) { "%08X".format(it and 0xFFFFFFFFL) }
    ).joinToString(FIELD_SEPARATOR.toString())

    companion object {
        const val MIN_STOPS = 2
        const val MAX_STOPS = 4

        /** A sensible starting point: the theme's own cyan → violet diagonal. */
        val STARTER = CustomGradient(
            id = "starter",
            name = "My gradient",
            colors = listOf(0xFF00D2FF, 0xFF9D7BFF),
            angleDegrees = 135,
            radial = false
        )

        private const val FIELD_SEPARATOR = '~'
        private const val RECORD_SEPARATOR = ';'
        private const val COLOR_SEPARATOR = ','

        /** Characters that would corrupt a record, replaced with a space. */
        fun sanitizeName(raw: String): String = raw
            .map { c -> if (c == FIELD_SEPARATOR || c == RECORD_SEPARATOR || c == COLOR_SEPARATOR || c == '\n' || c == '\r') ' ' else c }
            .joinToString("")
            .trim()
            .take(28)

        fun parse(record: String?): CustomGradient? {
            val parts = record?.split(FIELD_SEPARATOR) ?: return null
            if (parts.size < 5) return null
            val id = parts[0].trim()
            if (id.isEmpty()) return null
            val colors = parts[4].split(COLOR_SEPARATOR).mapNotNull { token ->
                token.trim().removePrefix("#").toLongOrNull(16)?.let { it and 0xFFFFFFFFL }
            }
            if (colors.size < MIN_STOPS) return null
            return CustomGradient(
                id = id,
                name = sanitizeName(parts[1]).ifEmpty { "Gradient" },
                colors = colors.take(MAX_STOPS),
                angleDegrees = (parts[2].toIntOrNull() ?: STARTER.angleDegrees).coerceIn(0, 359),
                radial = parts[3] == "1"
            )
        }

        fun parseList(raw: String?): List<CustomGradient> =
            raw.orEmpty().split(RECORD_SEPARATOR).mapNotNull { parse(it) }

        fun serializeList(gradients: List<CustomGradient>): String =
            gradients.joinToString(RECORD_SEPARATOR.toString()) { it.serialize() }

        /** Unique enough for a hand-saved preset, and stable across a rename. */
        fun newId(): String = "g" + System.currentTimeMillis().toString(36) + (100..999).random()
    }
}

/**
 * Ambient background behind every screen.
 *
 * Every style is drawn by the *same* single ~30 Hz phase clock in
 * [com.icy.devcheckplus.ui.components.AmbientBackground], stops completely while a
 * list is being flung and while the app is backgrounded, and is replaced by one
 * static gradient when [NONE] is chosen. [motes] marks the styles that also draw
 * small drifting elements on top of the colour fields; [moteDensity] scales the
 * theme's mote budget for them, and [detail] is the sentence the picker shows
 * under the tiles so the cost of each style is not a mystery.
 */
enum class BackgroundAnimation(
    val label: String,
    val tagline: String,
    val motes: Boolean = false,
    val moteDensity: Float = 1f,
    val detail: String = ""
) {
    GRADIENT_DRIFT(
        label = "Gradient Drift",
        tagline = "Slow drifting colour fields",
        detail = "Three soft colour fields drifting on one 26 s cycle — the lightest animated style."
    ),
    AURORA_WAVES(
        label = "Aurora Waves",
        tagline = "Curtains of light",
        detail = "Three sine-edged curtains swept sideways. Slightly more path work per frame than Drift."
    ),
    FLOATING_ORBS(
        label = "Floating Orbs",
        tagline = "Soft orbs, slow bob",
        motes = true,
        moteDensity = 0.45f,
        detail = "A few large soft orbs bobbing and rising, each with a lit edge. Fewer elements than Particles."
    ),
    MESH_GRADIENT(
        label = "Mesh Gradient",
        tagline = "Interpolated lattice",
        detail = "A drifting lattice of colour points that blend into each other. The richest look, and the " +
            "most gradient fills per frame."
    ),
    PARTICLES(
        label = "Particles",
        tagline = "Drift plus floating motes",
        motes = true,
        detail = "Gradient Drift with twinkling motes rising through it."
    ),
    STARFIELD(
        label = "Starfield",
        tagline = "Twinkling stars",
        motes = true,
        // Stars are tiny single-colour circles, so the count can be ~3× the mote
        // budget and still cost less per frame than one large orb.
        moteDensity = 3f,
        detail = "A dense field of small stars that twinkle and drift over one faint nebula. Most " +
            "elements of any style, but each one is a cheap dot — pick Drift or None if you want " +
            "the fewest fills per frame."
    ),
    NONE(
        label = "None",
        tagline = "Static gradient only",
        detail = "Nothing is animated: a single static gradient is drawn once, with no animation clock."
    );

    companion object {
        fun fromKey(raw: String?): BackgroundAnimation =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GRADIENT_DRIFT
    }
}

/**
 * Watchdog for one *deep* read while building an exported report or resolving a
 * pinned dashboard value.
 *
 * These reads can need a root or Shizuku shell, so they are bounded — but the
 * right bound depends on the device: a slow shell answering in 25 s is a success
 * on one phone and a hang on another, which is why the duration is a setting
 * (Settings › Advanced) instead of a constant baked into the collectors.
 */
enum class WatchdogTimeout(val label: String, val tagline: String, val seconds: Int) {
    STRICT("10 s", "Fail fast", 10),
    BALANCED("20 s", "Default — tolerates one prompt", 20),
    PATIENT("45 s", "Slow shells, busy devices", 45),
    VERY_PATIENT("90 s", "Almost never times out", 90);

    val millis: Long get() = seconds * 1_000L

    companion object {
        val DEFAULT = BALANCED

        fun fromKey(raw: String?): WatchdogTimeout =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/** What the export action does without asking. */
enum class ExportFormatPreference(
    val label: String,
    /** Pill-sized version of [label], for the settings row. */
    val shortLabel: String,
    val tagline: String,
    val format: ReportFormat?
) {
    ASK("Ask every time", "Ask", "Two buttons: plain text or JSON", null),
    TEXT("Plain text", "Text", "One tap exports readable text", ReportFormat.TEXT),
    JSON("JSON", "JSON", "One tap exports structured JSON", ReportFormat.JSON);

    companion object {
        val DEFAULT = ASK

        fun fromKey(raw: String?): ExportFormatPreference =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/** The ten sections an exported device report can contain. */
enum class ReportSection(val id: String, val label: String) {
    TELEMETRY("telemetry", "Live telemetry"),
    HARDWARE("hardware", "Hardware"),
    SOFTWARE("software", "Software"),
    BATTERY("battery", "Battery"),
    STORAGE("storage", "Storage"),
    NETWORK("network", "Network"),
    PROCESSES("processes", "Processes"),
    INSTALLED_APPS("apps", "Installed apps"),
    SENSORS("sensors", "Sensors"),
    DEV_ENVIRONMENT("dev_environment", "Dev Environment");

    companion object {
        val ALL: Set<ReportSection> = values().toSet()

        fun fromIds(raw: Set<String>?): Set<ReportSection> {
            if (raw == null) return ALL
            val parsed = raw.mapNotNull { id -> values().firstOrNull { it.id == id } }.toSet()
            return parsed
        }
    }
}

/**
 * Settings sections, in their default order.
 *
 * The list is data, not code: Settings renders whatever order is stored (see
 * [UserPreferencesStore.settingsSectionOrder]) and hides whatever the organizer has
 * switched off, so reordering or hiding a section never touches the screen's own
 * layout code. [fromKeys] keeps a stored order usable when a new section appears
 * in a later release.
 */
enum class SettingsSectionId(val title: String) {
    APPEARANCE("APPEARANCE"),
    THEMING("COLORS & THEMING"),
    BACKGROUND("BACKGROUND ANIMATION"),
    PRIVILEGE("PRIVILEGE ENGINE"),
    PRIVACY("PRIVACY & NETWORK"),
    GENERAL("GENERAL"),
    UPDATES("UPDATES"),
    EXPORT("EXPORT & SHARE"),
    /** Power-user switches: performance, watchdogs, export defaults, console. */
    ADVANCED("ADVANCED"),
    ABOUT("ABOUT");

    companion object {
        val DEFAULT_ORDER: List<SettingsSectionId> = values().toList()

        /**
         * Restores a stored order, inserting anything it predates at its *default
         * slot* rather than dangling it at the bottom — so a section added in a
         * later release (ADVANCED) shows up where it belongs for existing installs
         * whose order was saved before it existed.
         */
        fun fromKeys(order: List<String>?): List<SettingsSectionId> {
            val parsed = order.orEmpty().mapNotNull { key -> values().firstOrNull { it.name == key } }
            if (parsed.size == DEFAULT_ORDER.size) return parsed

            val result = parsed.toMutableList()
            DEFAULT_ORDER.forEachIndexed { index, section ->
                if (section in result) return@forEachIndexed
                // Anchor on the next section in the default order that the stored
                // list does contain, and insert just before it.
                val anchor = DEFAULT_ORDER.drop(index + 1).firstOrNull { it in result }
                val at = anchor?.let { result.indexOf(it) }?.takeIf { it >= 0 } ?: result.size
                result.add(at, section)
            }
            return result
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Snapshot + store                                                   */
/* ------------------------------------------------------------------ */

data class UserPreferences(
    /** Global cadence for every live-updating surface (telemetry, sensors, logs, pins). */
    val refreshRate: RefreshRate = RefreshRate.DEFAULT,
    /**
     * Master switch for live line/area charts. When off, no chart canvas is
     * composed at all: the card shows a static last-known readout instead, and
     * the chart cards stop subscribing to the telemetry ticker (which, being
     * ref-counted, then stops sampling unless something else still needs it).
     */
    val liveGraphsEnabled: Boolean = true,
    /**
     * Logs a rolling frame-timing summary (total / janky / average / worst frame)
     * to logcat under the `DevCheckPerf` tag, together with the settings that
     * produced it. This is how the effect of "Live graphs off" or a slower refresh
     * rate is *measured* instead of guessed at.
     */
    val frameMetricsLogging: Boolean = false,
    /** Sections included in an exported device report. */
    val reportSections: Set<ReportSection> = ReportSection.ALL,
    val accent: AccentPalette = AccentPalette.DEFAULT,
    val gradient: GradientStyle = GradientStyle.DEFAULT,
    val backgroundAnimation: BackgroundAnimation = BackgroundAnimation.GRADIENT_DRIFT,
    /** Explicit opt-in to keep the animation running in OLED mode. */
    val backgroundAnimationOverride: Boolean = false,
    val autoUpdateCheck: Boolean = true,
    /** Watchdog for one deep read while exporting or resolving pinned values. */
    val watchdogTimeout: WatchdogTimeout = WatchdogTimeout.DEFAULT,
    /** Whether the export action needs a format choice first. */
    val exportFormatPreference: ExportFormatPreference = ExportFormatPreference.DEFAULT,
    /** Adds a Console shortcut to the top bar (Settings › Advanced). */
    val consoleQuickAccess: Boolean = false,
    /** Gradients the user has saved, most recently saved first. */
    val customGradients: List<CustomGradient> = emptyList(),
    /** Which of [customGradients] [GradientStyle.CUSTOM] paints. */
    val activeCustomGradientId: String? = null,
    val hiddenSettingsSections: Set<SettingsSectionId> = emptySet(),
    val settingsSectionOrder: List<SettingsSectionId> = SettingsSectionId.DEFAULT_ORDER
) {
    /**
     * The gradient [GradientStyle.CUSTOM] paints right now: the selected one, or
     * the newest saved one if the selection points at a preset that was deleted.
     * `null` only while the user has never saved one — the theme then falls back
     * to [GradientStyle.DEFAULT] instead of painting nothing.
     */
    val activeCustomGradient: CustomGradient?
        get() = customGradients.firstOrNull { it.id == activeCustomGradientId } ?: customGradients.firstOrNull()

    /** Live telemetry cadence in milliseconds — the refresh rate, kept as a
     *  property so existing callers (labels, the ticker) stay unchanged. */
    val pollIntervalMs: Long get() = refreshRate.intervalMs
}

private val Context.userPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "devcheck_prefs")

/**
 * User-configurable options added alongside the existing preferences.
 *
 * The original app preferences (theme mode, dynamic colour, haptics, history,
 * public-IP opt-in) stay in the original SharedPreferences file *and keep their
 * keys*, because they are read synchronously before the first frame on cold
 * start. Everything introduced from here on is persisted with Preferences
 * **DataStore**, which is what the pickers in Settings write to:
 *
 *  - polling interval, report sections, accent, gradient, background animation,
 *    settings-section order/visibility and the auto-update toggle;
 *  - one write helper per option, each of which updates the in-memory mirror
 *    immediately (so the UI reacts on the next frame) and persists in the
 *    background;
 *  - derived `StateFlow`s per option so a consumer of, say, the poll interval is
 *    not invalidated when the user picks a different gradient.
 *
 * Cold start — FIXED, no more main-thread stall:
 * [init] used to `runBlocking` on the *main* thread (Application.onCreate) to
 * read DataStore before the first frame, which could stall startup for up to
 * the full 750 ms cap on a cold device. The initial read is now performed
 * asynchronously on Dispatchers.IO, and [ready] flips to `true` the moment the
 * stored values are applied (or the hard cap expires and defaults proceed).
 * MainActivity holds the window's splash/background — not a wrongly themed
 * frame — until [ready] is true, so the very first composed frame already uses
 * the saved accent/gradient/animation: no default→saved theme flash, and no
 * startup stall.
 */
object UserPreferencesStore {

    private const val KEY_POLL_INTERVAL = "poll_interval_ms"
    private const val KEY_REFRESH_RATE = "refresh_rate"
    private const val KEY_LIVE_GRAPHS = "live_graphs_enabled"
    private const val KEY_FRAME_METRICS = "frame_metrics_logging"
    private const val KEY_REPORT_SECTIONS = "report_sections"
    private const val KEY_ACCENT = "accent_palette"
    private const val KEY_GRADIENT = "gradient_style"
    private const val KEY_BACKGROUND_ANIMATION = "background_animation"
    private const val KEY_BACKGROUND_OVERRIDE = "background_animation_override"
    private const val KEY_AUTO_UPDATE = "auto_update_check"
    private const val KEY_WATCHDOG = "watchdog_timeout"
    private const val KEY_EXPORT_FORMAT = "export_format_preference"
    private const val KEY_CONSOLE_SHORTCUT = "console_quick_access"
    private const val KEY_CUSTOM_GRADIENTS = "custom_gradients"
    private const val KEY_ACTIVE_CUSTOM_GRADIENT = "active_custom_gradient"
    private const val KEY_HIDDEN_SECTIONS = "settings_hidden_sections"
    private const val KEY_SECTION_ORDER = "settings_section_order"

    /** Upper bound for the cold-start read; beyond that the defaults are used. */
    private const val COLD_START_READ_TIMEOUT_MS = 750L

    @Volatile
    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _preferences = MutableStateFlow(UserPreferences())

    /** Full snapshot — use the per-option flows below unless you need everything. */
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    /**
     * False until the initial (asynchronous) DataStore read has been applied —
     * or has hit [COLD_START_READ_TIMEOUT_MS], after which defaults proceed.
     * Consumers that must not render with default theming (the root composable)
     * wait for this; everything else simply reads the option flows, which start
     * emitting stored values the moment this flips.
     */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val refreshRate: StateFlow<RefreshRate> = derive { it.refreshRate }
    val liveGraphsEnabled: StateFlow<Boolean> = derive { it.liveGraphsEnabled }
    val frameMetricsLogging: StateFlow<Boolean> = derive { it.frameMetricsLogging }
    /** Telemetry cadence in ms — always [RefreshRate.intervalMs]. */
    val pollIntervalMs: StateFlow<Long> = derive { it.refreshRate.intervalMs }
    val reportSections: StateFlow<Set<ReportSection>> = derive { it.reportSections }
    val accent: StateFlow<AccentPalette> = derive { it.accent }
    val gradient: StateFlow<GradientStyle> = derive { it.gradient }
    val backgroundAnimation: StateFlow<BackgroundAnimation> = derive { it.backgroundAnimation }
    val backgroundAnimationOverride: StateFlow<Boolean> = derive { it.backgroundAnimationOverride }
    val autoUpdateCheck: StateFlow<Boolean> = derive { it.autoUpdateCheck }
    val watchdogTimeout: StateFlow<WatchdogTimeout> = derive { it.watchdogTimeout }
    val exportFormatPreference: StateFlow<ExportFormatPreference> = derive { it.exportFormatPreference }
    val consoleQuickAccess: StateFlow<Boolean> = derive { it.consoleQuickAccess }
    /** Saved "My Gradients" presets, newest first. */
    val customGradients: StateFlow<List<CustomGradient>> = derive { it.customGradients }
    /** The preset [gradient] == CUSTOM paints; `null` until one is saved. */
    val activeCustomGradient: StateFlow<CustomGradient?> = derive { it.activeCustomGradient }
    val hiddenSettingsSections: StateFlow<Set<SettingsSectionId>> = derive { it.hiddenSettingsSections }
    val settingsSectionOrder: StateFlow<List<SettingsSectionId>> = derive { it.settingsSectionOrder }

    private fun <T> derive(selector: (UserPreferences) -> T): StateFlow<T> =
        _preferences
            .map(selector)
            .stateIn(scope, SharingStarted.Eagerly, selector(_preferences.value))

    /** Called once from `Application.onCreate()`. Does not block the caller. */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        // FIXED — cold start: the one initial DataStore read now happens on the
        // IO dispatcher instead of `runBlocking` on the main thread. The hard cap
        // still applies, and [ready] flips either way, so a pathologically slow
        // file can delay theming by at most COLD_START_READ_TIMEOUT_MS and can
        // never freeze application startup.
        scope.launch {
            val loaded = runCatching {
                withTimeoutOrNull(COLD_START_READ_TIMEOUT_MS) { read(app) }
            }.getOrNull()
            if (loaded != null) _preferences.value = loaded
            _ready.value = true
        }
        // Keep the mirror current for writes made by anything else (another
        // process, a restored backup, the platform's auto-backup restore).
        scope.launch {
            app.userPreferencesDataStore.data.collect { prefs ->
                _preferences.value = decode(prefs)
            }
        }
    }

    private suspend fun read(context: Context): UserPreferences = decode(context.userPreferencesDataStore.data.first())

    private fun decode(prefs: Preferences): UserPreferences {
        // Migration: the first release stored a raw poll interval in ms. The
        // refresh-rate picker supersedes it, so a stored interval is mapped onto
        // the nearest rate (500 ms → Smooth, 1 s → Balanced, 2 s/5 s → Battery
        // Saver) and the user keeps roughly the cadence they chose.
        val legacyInterval = prefs[longPreferencesKey(KEY_POLL_INTERVAL)]
        val refreshRate = prefs[stringPreferencesKey(KEY_REFRESH_RATE)]
            ?.let { RefreshRate.fromKey(it) }
            ?: legacyInterval?.let { RefreshRate.nearest(it) }
            ?: RefreshRate.DEFAULT

        return UserPreferences(
            refreshRate = refreshRate,
            liveGraphsEnabled = prefs[booleanPreferencesKey(KEY_LIVE_GRAPHS)] ?: true,
            frameMetricsLogging = prefs[booleanPreferencesKey(KEY_FRAME_METRICS)] ?: false,
            reportSections = ReportSection.fromIds(prefs[stringSetPreferencesKey(KEY_REPORT_SECTIONS)]),
            accent = AccentPalette.fromKey(prefs[stringPreferencesKey(KEY_ACCENT)]),
            gradient = GradientStyle.fromKey(prefs[stringPreferencesKey(KEY_GRADIENT)]),
            backgroundAnimation = BackgroundAnimation.fromKey(prefs[stringPreferencesKey(KEY_BACKGROUND_ANIMATION)]),
            backgroundAnimationOverride = prefs[booleanPreferencesKey(KEY_BACKGROUND_OVERRIDE)] ?: false,
            autoUpdateCheck = prefs[booleanPreferencesKey(KEY_AUTO_UPDATE)] ?: true,
            watchdogTimeout = WatchdogTimeout.fromKey(prefs[stringPreferencesKey(KEY_WATCHDOG)]),
            exportFormatPreference = ExportFormatPreference.fromKey(prefs[stringPreferencesKey(KEY_EXPORT_FORMAT)]),
            consoleQuickAccess = prefs[booleanPreferencesKey(KEY_CONSOLE_SHORTCUT)] ?: false,
            customGradients = CustomGradient.parseList(prefs[stringPreferencesKey(KEY_CUSTOM_GRADIENTS)]),
            activeCustomGradientId = prefs[stringPreferencesKey(KEY_ACTIVE_CUSTOM_GRADIENT)]?.takeIf { it.isNotBlank() },
            hiddenSettingsSections = prefs[stringSetPreferencesKey(KEY_HIDDEN_SECTIONS)]
                .orEmpty()
                .mapNotNull { key -> SettingsSectionId.values().firstOrNull { it.name == key } }
                .toSet(),
            settingsSectionOrder = SettingsSectionId.fromKeys(
                prefs[stringPreferencesKey(KEY_SECTION_ORDER)]?.split('\u001F')
            )
        )
    }

    private fun update(transform: (UserPreferences) -> UserPreferences) {
        _preferences.value = transform(_preferences.value)
    }

    private fun persist(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val context = appContext ?: return
        scope.launch { runCatching { context.userPreferencesDataStore.edit(block) } }
    }

    /* -------------------------------------------------------------- */

    /** Applies the global refresh rate to every live surface (see [RefreshRate]). */
    fun setRefreshRate(rate: RefreshRate) {
        update { it.copy(refreshRate = rate) }
        persist { prefs ->
            prefs[stringPreferencesKey(KEY_REFRESH_RATE)] = rate.name
            // The legacy key is kept in step so a downgrade, or any component
            // still reading the raw interval, sees the same cadence.
            prefs[longPreferencesKey(KEY_POLL_INTERVAL)] = rate.intervalMs
        }
    }

    /** Master switch for live charts (Settings › Advanced › Live graphs). */
    fun setLiveGraphsEnabled(enabled: Boolean) {
        update { it.copy(liveGraphsEnabled = enabled) }
        persist { prefs -> prefs[booleanPreferencesKey(KEY_LIVE_GRAPHS)] = enabled }
    }

    /** Rolling frame-timing summary to logcat (see [com.icy.devcheckplus.data.FrameMetricsMonitor]). */
    fun setFrameMetricsLogging(enabled: Boolean) {
        update { it.copy(frameMetricsLogging = enabled) }
        persist { prefs -> prefs[booleanPreferencesKey(KEY_FRAME_METRICS)] = enabled }
    }

    fun setReportSections(sections: Set<ReportSection>) {
        val encoded = sections.map { it.id }.toSet()
        update { it.copy(reportSections = sections) }
        persist { prefs -> prefs[stringSetPreferencesKey(KEY_REPORT_SECTIONS)] = encoded }
    }

    fun toggleReportSection(section: ReportSection, included: Boolean) {
        val current = _preferences.value.reportSections.toMutableSet()
        if (included) current.add(section) else current.remove(section)
        setReportSections(current)
    }

    fun setAccent(accent: AccentPalette) {
        update { it.copy(accent = accent) }
        persist { prefs -> prefs[stringPreferencesKey(KEY_ACCENT)] = accent.name }
    }

    fun setGradient(gradient: GradientStyle) {
        update { it.copy(gradient = gradient) }
        persist { prefs -> prefs[stringPreferencesKey(KEY_GRADIENT)] = gradient.name }
    }

    fun setBackgroundAnimation(animation: BackgroundAnimation, explicitOverride: Boolean = false) {
        update {
            it.copy(
                backgroundAnimation = animation,
                backgroundAnimationOverride = if (animation == BackgroundAnimation.NONE) false else explicitOverride
            )
        }
        persist { prefs ->
            prefs[stringPreferencesKey(KEY_BACKGROUND_ANIMATION)] = animation.name
            prefs[booleanPreferencesKey(KEY_BACKGROUND_OVERRIDE)] = animation != BackgroundAnimation.NONE && explicitOverride
        }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        update { it.copy(autoUpdateCheck = enabled) }
        persist { prefs -> prefs[booleanPreferencesKey(KEY_AUTO_UPDATE)] = enabled }
    }

    fun setWatchdogTimeout(timeout: WatchdogTimeout) {
        update { it.copy(watchdogTimeout = timeout) }
        persist { prefs -> prefs[stringPreferencesKey(KEY_WATCHDOG)] = timeout.name }
    }

    fun setExportFormatPreference(preference: ExportFormatPreference) {
        update { it.copy(exportFormatPreference = preference) }
        persist { prefs -> prefs[stringPreferencesKey(KEY_EXPORT_FORMAT)] = preference.name }
    }

    fun setConsoleQuickAccess(enabled: Boolean) {
        update { it.copy(consoleQuickAccess = enabled) }
        persist { prefs -> prefs[booleanPreferencesKey(KEY_CONSOLE_SHORTCUT)] = enabled }
    }

    /**
     * Saves a gradient into "My Gradients": an existing id is replaced in place (a
     * rename or an edit of the active preset), a new one is prepended so the newest
     * is first, and saving also makes it the active custom gradient.
     */
    fun saveCustomGradient(gradient: CustomGradient) {
        update { current ->
            val others = current.customGradients.filterNot { it.id == gradient.id }
            current.copy(
                customGradients = listOf(gradient) + others,
                activeCustomGradientId = gradient.id
            )
        }
        persistCustomGradients()
    }

    /**
     * Switches which saved preset the Custom style paints without editing it.
     * `null` falls back to the newest one.
     */
    fun setActiveCustomGradient(id: String?) {
        update { it.copy(activeCustomGradientId = id) }
        persist { prefs ->
            if (id == null) {
                prefs.remove(stringPreferencesKey(KEY_ACTIVE_CUSTOM_GRADIENT))
            } else {
                prefs[stringPreferencesKey(KEY_ACTIVE_CUSTOM_GRADIENT)] = id
            }
        }
    }

    /**
     * Deletes a saved preset. If it was the active one the selection falls back to
     * the newest survivor (or to `null`, which makes Custom paint the default
     * glass tint until another preset is saved).
     */
    fun deleteCustomGradient(id: String) {
        update { current ->
            val remaining = current.customGradients.filterNot { it.id == id }
            current.copy(
                customGradients = remaining,
                activeCustomGradientId = if (current.activeCustomGradientId == id) {
                    remaining.firstOrNull()?.id
                } else {
                    current.activeCustomGradientId
                }
            )
        }
        persistCustomGradients()
    }

    /** Writes both custom-gradient keys from the in-memory mirror. */
    private fun persistCustomGradients() {
        val snapshot = _preferences.value
        val gradients = snapshot.customGradients
        val active = snapshot.activeCustomGradientId
        persist { prefs ->
            prefs[stringPreferencesKey(KEY_CUSTOM_GRADIENTS)] = CustomGradient.serializeList(gradients)
            if (active == null) {
                prefs.remove(stringPreferencesKey(KEY_ACTIVE_CUSTOM_GRADIENT))
            } else {
                prefs[stringPreferencesKey(KEY_ACTIVE_CUSTOM_GRADIENT)] = active
            }
        }
    }

    /**
     * Puts every power-user switch back to its default in one tap. Kept to the
     * Advanced group on purpose: it never touches theming, pins or report content.
     */
    fun resetAdvancedDefaults() {
        update {
            it.copy(
                liveGraphsEnabled = true,
                refreshRate = RefreshRate.DEFAULT,
                frameMetricsLogging = false,
                watchdogTimeout = WatchdogTimeout.DEFAULT,
                exportFormatPreference = ExportFormatPreference.DEFAULT,
                consoleQuickAccess = false
            )
        }
        persist { prefs ->
            prefs[booleanPreferencesKey(KEY_LIVE_GRAPHS)] = true
            prefs[stringPreferencesKey(KEY_REFRESH_RATE)] = RefreshRate.DEFAULT.name
            prefs[longPreferencesKey(KEY_POLL_INTERVAL)] = RefreshRate.DEFAULT.intervalMs
            prefs[booleanPreferencesKey(KEY_FRAME_METRICS)] = false
            prefs[stringPreferencesKey(KEY_WATCHDOG)] = WatchdogTimeout.DEFAULT.name
            prefs[stringPreferencesKey(KEY_EXPORT_FORMAT)] = ExportFormatPreference.DEFAULT.name
            prefs[booleanPreferencesKey(KEY_CONSOLE_SHORTCUT)] = false
        }
    }

    fun setSettingsSectionOrder(order: List<SettingsSectionId>) {
        update { it.copy(settingsSectionOrder = order) }
        persist { prefs ->
            prefs[stringPreferencesKey(KEY_SECTION_ORDER)] = order.joinToString("\u001F") { it.name }
        }
    }

    fun setSettingsSectionHidden(section: SettingsSectionId, hidden: Boolean) {
        val current = _preferences.value.hiddenSettingsSections.toMutableSet()
        if (hidden) current.add(section) else current.remove(section)
        update { it.copy(hiddenSettingsSections = current) }
        persist { prefs ->
            prefs[stringSetPreferencesKey(KEY_HIDDEN_SECTIONS)] = current.map { it.name }.toSet()
        }
    }

    /** "0.5 s" / "1 s" / "2 s" / "5 s" — used by badges and subtitles. */
    fun formatPollInterval(ms: Long): String =
        if (ms % 1000L == 0L) "${ms / 1000L} s" else String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)
}
