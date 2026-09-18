package com.icy.devcheckplus.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.BuildConfig
import com.icy.devcheckplus.data.AccentPalette
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.data.BackgroundAnimation
import com.icy.devcheckplus.data.LiveMetricsRepository
import com.icy.devcheckplus.data.ReportSection
import com.icy.devcheckplus.data.SettingsSectionId
import com.icy.devcheckplus.data.UpdateCheckState
import com.icy.devcheckplus.data.UpdateChecker
import com.icy.devcheckplus.data.UpdateRepository
import com.icy.devcheckplus.data.UserPreferencesStore
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.privilege.PrivilegeStatus
import com.icy.devcheckplus.ui.components.AccentGrid
import com.icy.devcheckplus.ui.components.AmbientBackground
import com.icy.devcheckplus.ui.components.BackgroundAnimationGrid
import com.icy.devcheckplus.ui.components.ExportReportDialog
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassRow
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.GradientGrid
import com.icy.devcheckplus.ui.components.HapticSwitch
import com.icy.devcheckplus.ui.components.PillAction
import com.icy.devcheckplus.ui.components.PollIntervalSheet
import com.icy.devcheckplus.ui.components.ReportSectionsSheet
import com.icy.devcheckplus.ui.components.SelectableTile
import com.icy.devcheckplus.ui.components.SettingsOrganizerSheet
import com.icy.devcheckplus.ui.components.ThemeModePreview
import com.icy.devcheckplus.ui.components.TileGrid
import com.icy.devcheckplus.ui.components.TileIconPreview
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.displayIcon
import com.icy.devcheckplus.ui.components.UpdateStatusLine
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import com.icy.devcheckplus.ui.theme.LocalGlassSpec
import com.icy.devcheckplus.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Settings — grouped frosted-glass cards over an ambient animated background.
 *
 * Structure: the list is generated from [SettingsSectionId] so the section order
 * and visibility can be driven by stored preferences (see the organizer in the
 * header) without touching this composable.
 *
 * Recomposition note: this screen root subscribes only to the *layout* preference
 * (section order/visibility). Every value shown in a row is collected by the card
 * that displays it, so flipping a switch, picking an accent or changing the poll
 * interval invalidates that card alone — previously all of these flows were read
 * at the top of the screen, which meant the whole LazyColumn re-ran on any change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onResetOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showPollIntervalSheet by remember { mutableStateOf(false) }
    var showReportSectionsSheet by remember { mutableStateOf(false) }
    var showAnimationWarning by remember { mutableStateOf(false) }
    var showOrganizer by remember { mutableStateOf(false) }
    var pendingAnimation by remember { mutableStateOf(BackgroundAnimation.GRADIENT_DRIFT) }

    val sectionOrder by UserPreferencesStore.settingsSectionOrder
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.settingsSectionOrder.value)
    val hiddenSections by UserPreferencesStore.hiddenSettingsSections
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.hiddenSettingsSections.value)

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground(modifier = Modifier.matchParentSize())

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 36.dp)
        ) {
            item(key = "settings_header") {
                SettingsHeader(onOrganizeSections = { showOrganizer = true })
            }

            val visibleSections = sectionOrder.filterNot { it in hiddenSections }
            visibleSections.forEach { section ->
                // Sticky: the section label pins under the app bar while its card
                // scrolls, so the grouping stays readable at any scroll offset.
                stickyHeader(key = "header_${section.name}") {
                    StickySectionHeader(
                        title = section.title,
                        icon = section.displayIcon()
                    )
                }
                item(key = "card_${section.name}") {
                    when (section) {
                        SettingsSectionId.APPEARANCE -> ThemeCard()
                        SettingsSectionId.THEMING -> ThemingCard()
                        SettingsSectionId.BACKGROUND -> BackgroundAnimationCard(
                            onRequestAnimation = { requested ->
                                // OLED forces "None" unless the user explicitly opts
                                // back in, which is what the warning confirms.
                                pendingAnimation = requested
                                showAnimationWarning = true
                            }
                        )
                        SettingsSectionId.PRIVILEGE -> PrivilegeCard()
                        SettingsSectionId.PRIVACY -> PrivacyCard(
                            onPollIntervalClick = { showPollIntervalSheet = true }
                        )
                        SettingsSectionId.GENERAL -> GeneralCard(onResetOnboarding = onResetOnboarding)
                        SettingsSectionId.EXPORT -> ExportCard(
                            onExport = { showExportDialog = true },
                            onSectionsClick = { showReportSectionsSheet = true }
                        )
                        SettingsSectionId.UPDATES -> UpdatesCard()
                        SettingsSectionId.ABOUT -> AboutCard()
                    }
                }
            }

            item(key = "settings_footer") {
                Text(
                    text = "Root & Shizuku powered • 100% open source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showExportDialog) {
            ExportReportDialog(onDismiss = { showExportDialog = false })
        }

        if (showPollIntervalSheet) {
            val interval by UserPreferencesStore.pollIntervalMs
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.pollIntervalMs.value)
            PollIntervalSheet(
                currentMs = interval,
                onSelect = { UserPreferencesStore.setPollInterval(it) },
                onDismiss = { showPollIntervalSheet = false }
            )
        }

        if (showReportSectionsSheet) {
            val sections by UserPreferencesStore.reportSections
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.reportSections.value)
            ReportSectionsSheet(
                selected = sections,
                onToggle = { section, included -> UserPreferencesStore.toggleReportSection(section, included) },
                onSelectAll = { UserPreferencesStore.setReportSections(ReportSection.ALL) },
                onDismiss = { showReportSectionsSheet = false }
            )
        }

        if (showOrganizer) {
            SettingsOrganizerSheet(onDismiss = { showOrganizer = false })
        }

        if (showAnimationWarning) {
            val oled = AppSettingsStore.themeMode
                .collectAsStateWithLifecycle(initialValue = AppSettingsStore.themeMode.value)
                .value == ThemeMode.OLED
            AlertDialog(
                onDismissRequest = { showAnimationWarning = false },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Keep the animation on?", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Text(
                        text = if (oled) {
                            "May increase battery usage on OLED displays. Animated backgrounds light up " +
                                "pixels that true-black surfaces would otherwise leave off, and the " +
                                "animation is redrawn continuously. It stays available, at reduced " +
                                "intensity, if you want it."
                        } else {
                            "May increase battery usage on OLED displays — the animation is redrawn " +
                                "continuously. It is used at reduced intensity in OLED mode."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            UserPreferencesStore.setBackgroundAnimation(pendingAnimation, explicitOverride = true)
                            showAnimationWarning = false
                        }
                    ) {
                        Text("Keep animation")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            UserPreferencesStore.setBackgroundAnimation(BackgroundAnimation.NONE)
                            showAnimationWarning = false
                        }
                    ) {
                        Text("Use None")
                    }
                }
            )
        }
    }
}

/**
 * Section label that pins while its card scrolls. The translucent background is
 * what makes it readable over the card sliding underneath: without it the pinned
 * label would overlap the glass and both would be unreadable.
 */
@Composable
private fun StickySectionHeader(title: String, icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background.copy(alpha = 0.92f))
    ) {
        GlassSectionHeader(title = title, icon = icon)
    }
}

/** Status source — collected where it is displayed, not at screen level. */
@Composable
private fun rememberPrivilegeStatus(): PrivilegeStatus =
    PrivilegeManager.status.collectAsStateWithLifecycle(initialValue = PrivilegeManager.status.value).value

@Composable
private fun SettingsHeader(onOrganizeSections: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val status = rememberPrivilegeStatus()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(scheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground
            )
            Text(
                text = "Appearance, elevation & privacy",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        StatusPill(status = status)
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onOrganizeSections) {
            Icon(
                imageVector = Icons.Default.Reorder,
                contentDescription = "Reorder or hide Settings sections",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Privilege status *label*, not a control: it reports the active elevation level
 * and therefore carries no click indication (the rows below it do the selecting),
 * so it cannot be mistaken for a tappable chip.
 */
@Composable
private fun StatusPill(status: PrivilegeStatus) {
    val scheme = MaterialTheme.colorScheme
    val granted = status.activeMode == PrivilegeMode.ROOT || status.activeMode == PrivilegeMode.SHIZUKU
    val color = when {
        granted -> AccentGreen
        status.rootAvailable || status.shizukuRunning -> AccentOrange
        else -> scheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (granted) status.activeMode.name else "STANDARD",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.6.sp
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Appearance                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun ThemeCard() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current

    val themeMode by AppSettingsStore.themeMode.collectAsStateWithLifecycle(initialValue = AppSettingsStore.themeMode.value)
    val dynamicColor by AppSettingsStore.dynamicColor.collectAsStateWithLifecycle(initialValue = AppSettingsStore.dynamicColor.value)
    val hapticFeedback by AppSettingsStore.hapticFeedback.collectAsStateWithLifecycle(initialValue = AppSettingsStore.hapticFeedback.value)

    GlassCard(frosted = true) {
        Text(
            text = "Theme mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Dark and OLED are separate themes — OLED is the lightweight one.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        val themeModes = remember { ThemeMode.values().toList() }
        TileGrid(items = themeModes, columns = 2, spacing = 10.dp, aspectRatio = 1f) { tileModifier, mode ->
            SelectableTile(
                selected = mode == themeMode,
                onClick = { AppSettingsStore.setThemeMode(context, mode) },
                modifier = tileModifier,
                label = mode.label,
                supporting = mode.tagline,
                preview = { ThemeModePreview(mode) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        DetailNote(text = themeMode.detailText)

        if (themeMode == ThemeMode.OLED) {
            Spacer(modifier = Modifier.height(8.dp))
            DetailNote(
                text = "OLED sets surfaces to pure black, disables blur, elevation, gradients and " +
                    "the background animation, and drops the poll-driven repaint cost — the " +
                    "smoothest, lowest-power rendering path."
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        GlassRow(
            title = "Dynamic colour",
            icon = Icons.Default.Palette,
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "Pull accents from your wallpaper (Material You). An accent picked below overrides it."
            } else {
                "Wallpaper colours need Android 12+ — the built-in cyan palette is used."
            },
            trailing = {
                HapticSwitch(
                    checked = dynamicColor,
                    onCheckedChange = { AppSettingsStore.setDynamicColor(context, it) }
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        GlassRow(
            title = "Haptic feedback",
            icon = Icons.Default.Vibration,
            subtitle = "A light tick on toggles, tile selections and pin stars.",
            trailing = {
                HapticSwitch(
                    checked = hapticFeedback,
                    onCheckedChange = { AppSettingsStore.setHapticFeedback(context, it) }
                )
            }
        )
    }
}

private val ThemeMode.detailText: String
    get() = when (this) {
        ThemeMode.SYSTEM ->
            "Follows the device setting. A dark system theme renders the elevated Dark glass."
        ThemeMode.LIGHT ->
            "Bright surfaces with a light frosted glass treatment and a soft ambient wash."
        ThemeMode.DARK ->
            "#121212 elevated surfaces, translucent frosted cards, blurred app bar and the full ambient animation."
        ThemeMode.OLED ->
            "Pure black #000000 surfaces, opaque cards, no blur, no elevation and no background animation — minimal overdraw, best for battery life and scroll smoothness."
    }

/* ------------------------------------------------------------------ */
/*  Colors & theming                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun ThemingCard() {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val accent by UserPreferencesStore.accent.collectAsStateWithLifecycle(initialValue = UserPreferencesStore.accent.value)
    val gradient by UserPreferencesStore.gradient.collectAsStateWithLifecycle(initialValue = UserPreferencesStore.gradient.value)

    GlassCard(frosted = true) {
        Text(
            text = "Accent colour",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Applied app-wide to buttons, switches, selection rings, highlights, ripple and " +
                "chart strokes — nothing is hardcoded per screen.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        AccentGrid(
            selected = accent,
            onSelect = { UserPreferencesStore.setAccent(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        DetailNote(
            text = if (accent == AccentPalette.DEFAULT) {
                "Currently using the theme's own accent. Pick a swatch to override it everywhere."
            } else {
                "${accent.label} is active. \"Default\" restores the theme/dynamic accent."
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        Text(
            text = "Surface gradient",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Sets the gradient direction and colour pair used by cards, tiles and the app bar. " +
                "\"Solid\" disables gradients entirely.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        GradientGrid(
            selected = gradient,
            onSelect = { UserPreferencesStore.setGradient(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        DetailNote(
            text = if (spec.isOled) {
                "OLED mode forces solid surfaces: gradients band on true-black panels and add overdraw. " +
                    "Your choice applies again in System, Light and Dark."
            } else {
                "Gradients are painted by the shared glass container, so every card and surface follows this."
            }
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Background animation                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun BackgroundAnimationCard(onRequestAnimation: (BackgroundAnimation) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val animation by UserPreferencesStore.backgroundAnimation
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.backgroundAnimation.value)
    val override by UserPreferencesStore.backgroundAnimationOverride
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.backgroundAnimationOverride.value)
    val themeMode by AppSettingsStore.themeMode
        .collectAsStateWithLifecycle(initialValue = AppSettingsStore.themeMode.value)

    val oled = themeMode == ThemeMode.OLED
    val effective = if (oled && !override) BackgroundAnimation.NONE else animation

    GlassCard(frosted = true) {
        Text(
            text = "Ambient background",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "The animated wash behind every screen. It pauses while a list scrolls and whenever " +
                "the app is in the background.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        BackgroundAnimationGrid(
            selected = effective,
            onSelect = { picked ->
                // Picking an animation while OLED is active raises the battery
                // warning before anything is stored.
                if (oled && picked != BackgroundAnimation.NONE) {
                    onRequestAnimation(picked)
                } else {
                    UserPreferencesStore.setBackgroundAnimation(picked)
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        DetailNote(
            text = when {
                effective == BackgroundAnimation.NONE && oled && !override ->
                    "Forced to None because OLED mode is active — animated backgrounds light up pixels " +
                        "a true-black panel would leave off. Pick a style and confirm the warning to override."
                effective == BackgroundAnimation.NONE ->
                    "Nothing is animated: a single static gradient is drawn once, with no animation clock."
                oled && override ->
                    "OLED override active. The animation runs at reduced intensity (fewer, dimmer " +
                        "particles) — May increase battery usage on OLED displays."
                else ->
                    "Runs on a single ~30 Hz clock, is skipped while scrolling, and stops completely " +
                        "when the app is not in the foreground."
            }
        )

        if (oled && override) {
            Spacer(modifier = Modifier.height(10.dp))
            GlassRow(
                title = "Reset to None",
                icon = Icons.Default.Wallpaper,
                subtitle = "Return to the power-saving default for OLED displays.",
                onClick = { UserPreferencesStore.setBackgroundAnimation(BackgroundAnimation.NONE) },
                trailing = {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Privilege engine                                                   */
/* ------------------------------------------------------------------ */

private data class ModeEntry(
    val mode: PrivilegeMode,
    val label: String,
    val short: String,
    val icon: ImageVector,
    val detail: String
)

private val modeEntries = listOf(
    ModeEntry(
        mode = PrivilegeMode.AUTO,
        label = "Auto",
        short = "Best available",
        icon = Icons.Default.AutoAwesome,
        detail = "Uses Root when it is granted, otherwise Shizuku, otherwise falls back to standard access."
    ),
    ModeEntry(
        mode = PrivilegeMode.ROOT,
        label = "Root",
        short = "libsu superuser",
        icon = Icons.Default.Terminal,
        detail = "Persistent libsu root shell: live per-core frequencies, full logcat, charge cycles, deep process inspection."
    ),
    ModeEntry(
        mode = PrivilegeMode.SHIZUKU,
        label = "Shizuku",
        short = "Privileged ADB",
        icon = Icons.Default.Adb,
        detail = "ADB-level privileges through Shizuku — most elevated readings without rooting the device."
    ),
    ModeEntry(
        mode = PrivilegeMode.NONE,
        label = "Standard",
        short = "No elevation",
        icon = Icons.Default.Lock,
        detail = "Public Android APIs only. Per-core frequency, system logcat and cycle count stay unavailable."
    )
)

@Composable
private fun PrivilegeCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val status = rememberPrivilegeStatus()

    val selectMode: (PrivilegeMode) -> Unit = { mode ->
        PrivilegeManager.setPreferredMode(context, mode)
        // Telemetry availability depends on the privilege level — drop stale points.
        LiveMetricsRepository.reset()
        if (mode == PrivilegeMode.ROOT && !status.rootGranted) {
            scope.launch { PrivilegeManager.requestRootAccess() }
        } else if (mode == PrivilegeMode.SHIZUKU && !status.shizukuGranted) {
            PrivilegeManager.requestShizukuPermission()
        }
    }

    GlassCard(frosted = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Preferred execution mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatusPill(status = status)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Active: ${status.activeMode.name}   •   Root: ${if (status.rootGranted) "Granted" else "None"}   •   Shizuku: ${if (status.shizukuGranted) "Granted" else if (status.shizukuRunning) "Pending" else "None"}",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        TileGrid(items = modeEntries, columns = 2, spacing = 10.dp, aspectRatio = 1f) { tileModifier, entry ->
            val selected = status.preferredMode == entry.mode
            SelectableTile(
                selected = selected,
                onClick = { selectMode(entry.mode) },
                modifier = tileModifier,
                label = entry.label,
                supporting = entry.short,
                preview = {
                    TileIconPreview(
                        icon = entry.icon,
                        tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        halo = selected
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        DetailNote(
            text = modeEntries.firstOrNull { it.mode == status.preferredMode }?.detail
                ?: modeEntries[0].detail
        )
    }
}


/* ------------------------------------------------------------------ */
/*  Updates                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun UpdatesCard() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val checkState by UpdateRepository.checkState.collectAsStateWithLifecycle()
    val autoCheck by UserPreferencesStore.autoUpdateCheck
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.autoUpdateCheck.value)

    GlassCard(frosted = true) {
        GlassRow(
            title = "Check for updates",
            icon = Icons.Default.SystemUpdate,
            subtitle = "Reads the latest release from GitHub Releases — no account, no token. " +
                "Currently on v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}).",
            onClick = { UpdateRepository.check(context) },
            trailing = {
                PillAction(
                    text = if (checkState is UpdateCheckState.Checking) "Checking…" else "Check now",
                    onClick = { UpdateRepository.check(context) },
                    contentDescription = "Check GitHub Releases for a newer build",
                    enabled = checkState !is UpdateCheckState.Checking
                )
            }
        )

        UpdateStatusLine(
            checkState = checkState,
            modifier = Modifier.padding(top = 10.dp)
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        GlassRow(
            title = "Check automatically",
            icon = Icons.Default.RestartAlt,
            subtitle = "One check per app launch at most (throttled to every 6 hours). A manual " +
                "check above always runs.",
            trailing = {
                HapticSwitch(
                    checked = autoCheck,
                    onCheckedChange = { UserPreferencesStore.setAutoUpdateCheck(it) }
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        GlassRow(
            title = "Release page",
            icon = Icons.Default.Public,
            subtitle = "Open the releases in a browser to download an APK manually.",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_PAGE_URL))
                    )
                }
            },
            trailing = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        DetailNote(
            text = "Android requires a confirmation tap for every install — no app can update " +
                "itself silently without root or device-owner privileges, and this app does not " +
                "request them for updates. \"Update now\" downloads the APK and opens the system " +
                "installer; if it is your first time, Android asks you to allow \"install unknown " +
                "apps\" for DevCheck+ first."
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Privacy, general, export, about                                    */
/* ------------------------------------------------------------------ */

@Composable
private fun PrivacyCard(onPollIntervalClick: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val publicIpLookup by AppSettingsStore.publicIpLookup
        .collectAsStateWithLifecycle(initialValue = AppSettingsStore.publicIpLookup.value)
    val pollInterval by UserPreferencesStore.pollIntervalMs
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.pollIntervalMs.value)

    GlassCard(frosted = true) {
        GlassRow(
            title = "Public IP address lookup",
            icon = Icons.Default.Public,
            subtitle = "Sends a lightweight request to api.ipify.org to show your external IPv4 in the Network tab.",
            trailing = {
                HapticSwitch(
                    checked = publicIpLookup,
                    onCheckedChange = { AppSettingsStore.setPublicIpLookup(context, it) }
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        GlassRow(
            title = "Live telemetry polling",
            icon = Icons.Default.Speed,
            subtitle = "CPU, RAM and battery share one sampling loop while a live screen is visible, " +
                "and it pauses in the background. Currently sampling every " +
                "${UserPreferencesStore.formatPollInterval(pollInterval)}.",
            onClick = onPollIntervalClick,
            trailing = {
                PillAction(
                    text = UserPreferencesStore.formatPollInterval(pollInterval),
                    onClick = onPollIntervalClick,
                    contentDescription = "Change live telemetry polling interval"
                )
            }
        )
    }
}

@Composable
private fun ExportCard(onExport: () -> Unit, onSectionsClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val sections by UserPreferencesStore.reportSections
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.reportSections.value)
    val allIncluded = sections.size == ReportSection.ALL.size

    GlassCard(frosted = true) {
        GlassRow(
            title = "Export device report",
            icon = Icons.Default.Share,
            subtitle = "Full dump of the selected categories as readable text or structured JSON, sent " +
                "through the Android share sheet.",
            onClick = onExport,
            trailing = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        GlassRow(
            title = "What is included",
            icon = Icons.Default.Info,
            subtitle = if (allIncluded) {
                "Hardware • Software • Battery • Storage • Network • Processes • Installed apps • " +
                    "Sensors • live telemetry. Tap to choose."
            } else {
                sections.sortedBy { it.ordinal }.joinToString(" • ") { it.label } +
                    ". Tap to choose."
            },
            onClick = onSectionsClick,
            trailing = {
                PillAction(
                    text = if (allIncluded) {
                        "${ReportSection.ALL.size} sections"
                    } else {
                        "${sections.size} of ${ReportSection.ALL.size}"
                    },
                    onClick = onSectionsClick,
                    contentDescription = "Choose which sections the exported report contains"
                )
            }
        )
    }
}

@Composable
private fun GeneralCard(onResetOnboarding: () -> Unit) {
    GlassCard(frosted = true) {
        GlassRow(
            title = "Relaunch onboarding setup",
            icon = Icons.Default.RestartAlt,
            subtitle = "Run the elevated-access configuration wizard again.",
            onClick = onResetOnboarding,
            trailing = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

@Composable
private fun AboutCard() {
    val scheme = MaterialTheme.colorScheme
    val status = rememberPrivilegeStatus()
    val themeMode by AppSettingsStore.themeMode.collectAsStateWithLifecycle(initialValue = AppSettingsStore.themeMode.value)
    val dynamicColor by AppSettingsStore.dynamicColor.collectAsStateWithLifecycle(initialValue = AppSettingsStore.dynamicColor.value)

    GlassCard(frosted = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DevCheck+ v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                Text(
                    text = "Deep hardware & system inspector",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "100% Free, Ad-Free & Open Source system inspector with Libsu Root & Shizuku elevation.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoPill(label = "THEME", value = themeMode.label, modifier = Modifier.weight(1f))
            InfoPill(
                label = "ACCESS",
                value = if (status.activeMode == PrivilegeMode.NONE) "Std" else status.activeMode.name,
                modifier = Modifier.weight(1f)
            )
            InfoPill(
                label = "MATERIAL YOU",
                value = if (dynamicColor) "On" else "Off",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Read-only summary stat. Deliberately has no click indication so the three values
 * that merely *report* state cannot be confused with the tappable badges above.
 */
@Composable
private fun InfoPill(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 2
        )
    }
}

/** Tinted explanation strip shown under a tile grid. */
@Composable
private fun DetailNote(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            lineHeight = 17.sp
        )
    }
}
