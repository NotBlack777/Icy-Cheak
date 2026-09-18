package com.icy.devcheckplus.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.BuildConfig
import com.icy.devcheckplus.data.AccentPalette
import com.icy.devcheckplus.data.AppManagementController
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.data.BackgroundAnimation
import com.icy.devcheckplus.data.CustomGradient
import com.icy.devcheckplus.data.GradientStyle
import com.icy.devcheckplus.data.LiveMetricsRepository
import com.icy.devcheckplus.data.ReportSection
import com.icy.devcheckplus.data.SettingsSectionId
import com.icy.devcheckplus.data.UpdateCheckState
import com.icy.devcheckplus.data.UpdateChecker
import com.icy.devcheckplus.data.UpdateRepository
import com.icy.devcheckplus.data.UpdateViewModel
import com.icy.devcheckplus.data.UserPreferencesStore
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.privilege.PrivilegeStatus
import com.icy.devcheckplus.ui.components.AccentGrid
import com.icy.devcheckplus.ui.components.AppActionConfirmationDialog
import com.icy.devcheckplus.ui.components.AppActionFailureDialog
import com.icy.devcheckplus.ui.components.AppManagementAction
import com.icy.devcheckplus.ui.components.CustomGradientSheet
import com.icy.devcheckplus.ui.components.BackgroundAnimationGrid
import com.icy.devcheckplus.ui.components.ExportFormatSheet
import com.icy.devcheckplus.ui.components.ExportReportDialog
import com.icy.devcheckplus.ui.components.GlassRow
import com.icy.devcheckplus.ui.components.GlassDialog
import com.icy.devcheckplus.ui.components.GlassGroupBox
import com.icy.devcheckplus.ui.components.GradientGrid
import com.icy.devcheckplus.ui.components.HapticSwitch
import com.icy.devcheckplus.ui.components.PillAction
import com.icy.devcheckplus.ui.components.RefreshRateSheet
import com.icy.devcheckplus.ui.components.rememberFrameReport
import com.icy.devcheckplus.ui.components.ReportSectionsSheet
import com.icy.devcheckplus.ui.components.SelectableTile
import com.icy.devcheckplus.ui.components.SettingsOrganizerSheet
import com.icy.devcheckplus.ui.components.ThemeModePreview
import com.icy.devcheckplus.ui.components.TileGrid
import com.icy.devcheckplus.ui.components.TileIconPreview
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.displayIcon
import com.icy.devcheckplus.ui.components.UpdatePendingDot
import com.icy.devcheckplus.ui.components.UpdateStatusLine
import com.icy.devcheckplus.ui.components.WatchdogSheet
import com.icy.devcheckplus.ui.components.rememberHapticTick
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.gradientBrush
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
@Composable
fun SettingsScreen(
    onResetOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showRefreshRateSheet by remember { mutableStateOf(false) }
    var showWatchdogSheet by remember { mutableStateOf(false) }
    var showExportFormatSheet by remember { mutableStateOf(false) }
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

    // Hoisted out of the list content so a cross-link row can compute the target
    // item index: item 0 is the header, then one item per visible category, in the
    // user's own order.
    val visibleSections = remember(sectionOrder, hiddenSections) {
        sectionOrder.filterNot { it in hiddenSections }
    }
    val scope = rememberCoroutineScope()

    // No AmbientBackground here: since the app-wide polish pass the ambient layer
    // is painted once at the root of MainActivity, behind every category screen,
    // so Settings would otherwise be drawing a second full-screen canvas.
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 36.dp)
        ) {
            item(key = "settings_header") {
                SettingsHeader(onOrganizeSections = { showOrganizer = true })
            }

            visibleSections.forEach { section ->
                // One item per category, and the header is *inside* the box: a
                // category is a single bordered gradient/glass object rather than a
                // bare label floating above a separate card. The sticky header the
                // previous layout used is gone with it — a pinned label cannot live
                // inside the container it labels, and the grouped box keeps the
                // category readable on its own.
                item(key = "section_${section.name}") {
                    GlassGroupBox(
                        title = section.title,
                        icon = section.displayIcon(),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        when (section) {
                            SettingsSectionId.APPEARANCE -> AppearanceSection()
                            SettingsSectionId.THEMING -> ThemingSection()
                            SettingsSectionId.BACKGROUND -> BackgroundSection(
                                onRequestAnimation = { requested ->
                                    // OLED forces "None" unless the user explicitly
                                    // opts back in, which is what the warning confirms.
                                    pendingAnimation = requested
                                    showAnimationWarning = true
                                }
                            )
                            SettingsSectionId.PRIVILEGE -> PrivilegeSection()
                            SettingsSectionId.PRIVACY -> PrivacySection(
                                onOpenAdvanced = {
                                    // Scroll the list to the Advanced box (+1 for the
                                    // header item). No-op when the user has hidden it.
                                    val index = visibleSections.indexOf(SettingsSectionId.ADVANCED)
                                    if (index >= 0) {
                                        scope.launch { listState.animateScrollToItem(index + 1) }
                                    }
                                }
                            )
                            SettingsSectionId.ADVANCED -> AdvancedSection(
                                onRefreshRateClick = { showRefreshRateSheet = true },
                                onWatchdogClick = { showWatchdogSheet = true },
                                onExportFormatClick = { showExportFormatSheet = true }
                            )
                            SettingsSectionId.GENERAL -> GeneralSection(onResetOnboarding = onResetOnboarding)
                            SettingsSectionId.EXPORT -> ExportSection(
                                onExport = { showExportDialog = true },
                                onSectionsClick = { showReportSectionsSheet = true }
                            )
                            SettingsSectionId.UPDATES -> UpdatesSection()
                            SettingsSectionId.ABOUT -> AboutSection()
                        }
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

        if (showRefreshRateSheet) {
            val rate by UserPreferencesStore.refreshRate
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.refreshRate.value)
            RefreshRateSheet(
                current = rate,
                onSelect = { UserPreferencesStore.setRefreshRate(it) },
                onDismiss = { showRefreshRateSheet = false }
            )
        }

        if (showWatchdogSheet) {
            val watchdog by UserPreferencesStore.watchdogTimeout
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.watchdogTimeout.value)
            WatchdogSheet(
                current = watchdog,
                onSelect = { UserPreferencesStore.setWatchdogTimeout(it) },
                onDismiss = { showWatchdogSheet = false }
            )
        }

        if (showExportFormatSheet) {
            val exportFormat by UserPreferencesStore.exportFormatPreference
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.exportFormatPreference.value)
            ExportFormatSheet(
                current = exportFormat,
                onSelect = { UserPreferencesStore.setExportFormatPreference(it) },
                onDismiss = { showExportFormatSheet = false }
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
            GlassDialog(
                onDismissRequest = { showAnimationWarning = false },
                title = "Keep the animation on?",
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
private fun ColumnScope.AppearanceSection() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current

    val themeMode by AppSettingsStore.themeMode.collectAsStateWithLifecycle(initialValue = AppSettingsStore.themeMode.value)
    val dynamicColor by AppSettingsStore.dynamicColor.collectAsStateWithLifecycle(initialValue = AppSettingsStore.dynamicColor.value)
    val hapticFeedback by AppSettingsStore.hapticFeedback.collectAsStateWithLifecycle(initialValue = AppSettingsStore.hapticFeedback.value)

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
private fun ColumnScope.ThemingSection() {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val accent by UserPreferencesStore.accent.collectAsStateWithLifecycle(initialValue = UserPreferencesStore.accent.value)
    val gradient by UserPreferencesStore.gradient.collectAsStateWithLifecycle(initialValue = UserPreferencesStore.gradient.value)
    val customGradients by UserPreferencesStore.customGradients
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.customGradients.value)
    val activeCustom by UserPreferencesStore.activeCustomGradient
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.activeCustomGradient.value)
    // Editor state lives with the section: the sheet is its own window, so it can
    // be hosted here without touching the screen above.
    var showGradientEditor by remember { mutableStateOf(false) }
    var editingGradient by remember { mutableStateOf<CustomGradient?>(null) }

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
        onSelect = { UserPreferencesStore.setGradient(it) },
        custom = activeCustom,
        onCustomize = {
            // Editing starts from the active preset when there is one, so "Custom"
            // reopens what the user last built instead of a blank sheet.
            editingGradient = activeCustom
            showGradientEditor = true
        }
    )

    Spacer(modifier = Modifier.height(14.dp))

    DetailNote(
        text = when {
            spec.isOled ->
                "OLED mode forces solid surfaces: gradients band on true-black panels and add overdraw. " +
                    "Your choice applies again in System, Light and Dark."
            gradient == GradientStyle.CUSTOM && activeCustom != null ->
                "\"${activeCustom!!.name}\" is painting every glass surface: ${activeCustom!!.colors.size} " +
                    "colours, ${activeCustom!!.directionLabel}. Tap Custom to edit it."
            gradient == GradientStyle.CUSTOM ->
                "No custom gradient is saved yet — tap Custom to build one (2–4 colours, an angle or " +
                    "radial spread) and it becomes the surface treatment app-wide."
            else ->
                "Gradients are painted by the shared glass container, so every card and surface follows this."
        }
    )

    // "My gradients": every saved preset, switchable without reopening the editor.
    if (customGradients.isNotEmpty()) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        Text(
            text = "My gradients",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "${customGradients.size} saved • tap one to paint the app with it, edit to change " +
                "it, or delete to remove it.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        customGradients.forEach { saved ->
            SavedGradientRow(
                gradient = saved,
                active = gradient == GradientStyle.CUSTOM && saved.id == activeCustom?.id,
                onSelect = {
                    UserPreferencesStore.setActiveCustomGradient(saved.id)
                    UserPreferencesStore.setGradient(GradientStyle.CUSTOM)
                },
                onEdit = {
                    editingGradient = saved
                    showGradientEditor = true
                },
                onDelete = { UserPreferencesStore.deleteCustomGradient(saved.id) }
            )
        }
    }

    if (showGradientEditor) {
        CustomGradientSheet(
            initial = editingGradient,
            onSave = { built ->
                // Save, make it active and switch the style to Custom in one tap:
                // building a gradient you then have to go and select would be two
                // steps for what is obviously one intention.
                UserPreferencesStore.saveCustomGradient(built)
                UserPreferencesStore.setGradient(GradientStyle.CUSTOM)
            },
            onDismiss = { showGradientEditor = false }
        )
    }
}

/** One saved preset in the "My gradients" list: swatch, name, select/edit/delete. */
@Composable
private fun SavedGradientRow(
    gradient: CustomGradient,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val brush = remember(gradient) { gradient.gradientBrush(gradient.colors.map { Color(it) }) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(brush)
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) scheme.primary else scheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(13.dp)
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gradient.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) scheme.primary else scheme.onSurface
            )
            Text(
                text = "${gradient.colors.size} colours • ${gradient.directionLabel}" +
                    if (active) " • Active" else "",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit ${gradient.name}",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete ${gradient.name}",
                tint = scheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Background animation                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun ColumnScope.BackgroundSection(onRequestAnimation: (BackgroundAnimation) -> Unit) {
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
            effective == BackgroundAnimation.NONE -> effective.detail
            oled && override ->
                "OLED override active. The animation runs at reduced intensity (fewer, dimmer " +
                    "elements) — May increase battery usage on OLED displays. " + effective.detail
            // Each style describes its own cost, then the shared scheduling rules.
            else ->
                effective.detail + " Runs on a single ~30 Hz clock, is skipped while scrolling, and " +
                    "stops completely when the app is not in the foreground."
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
private fun ColumnScope.PrivilegeSection() {
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


/* ------------------------------------------------------------------ */
/*  Updates                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun ColumnScope.UpdatesSection() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val updateViewModel: UpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = UpdateViewModel.Factory)
    val checkState by updateViewModel.checkState.collectAsStateWithLifecycle()
    val dialogDismissed by updateViewModel.dialogDismissed.collectAsStateWithLifecycle()
    val autoCheck by UserPreferencesStore.autoUpdateCheck
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.autoUpdateCheck.value)

    // Update available but dismissed this session: show a re-open row + badge.
    val available = checkState as? UpdateCheckState.Available
    val updatePending = available != null && dialogDismissed

    GlassRow(
        title = "Check for updates",
        icon = Icons.Default.SystemUpdate,
        subtitle = "Reads the latest release from GitHub Releases — no account, no token. " +
            "Currently on v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}).",
        onClick = { UpdateRepository.check(context) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillAction(
                    text = if (checkState is UpdateCheckState.Checking) "Checking…" else "Check now",
                    onClick = { UpdateRepository.check(context) },
                    contentDescription = "Check GitHub Releases for a newer build",
                    enabled = checkState !is UpdateCheckState.Checking
                )
                Spacer(modifier = Modifier.width(6.dp))
                UpdatePendingDot(
                    viewModel = updateViewModel,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    )

    // The re-open affordance: dismissing the dialog with "Later" must not be a
    // dead end. This row appears while an update is available-but-dismissed and,
    // unlike "Check now", does not hit the network again — it just re-shows the
    // dialog the ViewModel is already holding.
    if (updatePending) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )
        GlassRow(
            title = "Update available — v${available?.info?.versionName}",
            icon = Icons.Default.SystemUpdate,
            subtitle = "You dismissed this earlier. Open the update prompt again without checking the network.",
            onClick = { updateViewModel.showDialog() },
            trailing = {
                UpdatePendingDot(viewModel = updateViewModel, modifier = Modifier.size(9.dp))
            }
        )
    }

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
            "apps\" for Icy Cheak first."
    )
}

/* ------------------------------------------------------------------ */
/*  Privacy, general, export, about                                    */
/* ------------------------------------------------------------------ */

@Composable
private fun ColumnScope.PrivacySection(onOpenAdvanced: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val publicIpLookup by AppSettingsStore.publicIpLookup
        .collectAsStateWithLifecycle(initialValue = AppSettingsStore.publicIpLookup.value)

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

    // Cross-link, not a copy: the rendering/polling costs used to live here and are
    // now one tap away in Advanced, so this section stays about what leaves the device.
    GlassRow(
        title = "Rendering & polling costs",
        icon = Icons.Default.Speed,
        subtitle = "Live graphs, the global refresh rate, frame-metric logging, the export watchdog " +
            "and the default export format.",
        onClick = onOpenAdvanced,
        trailing = {
            PillAction(
                text = "Advanced",
                onClick = onOpenAdvanced,
                contentDescription = "Open Advanced settings"
            )
        },
        iconTint = scheme.primary
    )
}

/**
 * Advanced / developer section (new top-level category): every knob that trades
 * fidelity for responsiveness in one place, instead of being scattered through
 * Privacy and Export.
 *
 * Live graphs, the global refresh rate and frame-metric logging are the measured
 * cost controls; the console shortcut, watchdog timeout and default export format
 * are the workflow controls. A single reset returns all six to their safe defaults.
 */
@Composable
private fun ColumnScope.AdvancedSection(
    onRefreshRateClick: () -> Unit,
    onWatchdogClick: () -> Unit,
    onExportFormatClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val watchdog by UserPreferencesStore.watchdogTimeout
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.watchdogTimeout.value)
    val exportFormat by UserPreferencesStore.exportFormatPreference
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.exportFormatPreference.value)
    val consoleShortcut by UserPreferencesStore.consoleQuickAccess
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.consoleQuickAccess.value)
    val refreshRate by UserPreferencesStore.refreshRate
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.refreshRate.value)
    val liveGraphs by UserPreferencesStore.liveGraphsEnabled
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.liveGraphsEnabled.value)
    val frameMetrics by UserPreferencesStore.frameMetricsLogging
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.frameMetricsLogging.value)
    // Only non-null while logging is on: the row then reports the measured jank of
    // the last window, which is how the switches here are verified on device.
    val frameReport = rememberFrameReport()
    val spec = LocalGlassSpec.current
    val hapticTick = rememberHapticTick()

    GlassRow(
        title = "Live graphs",
        icon = Icons.Default.ShowChart,
        subtitle = if (liveGraphs) {
            "CPU, RAM, battery and sensor charts redraw continuously while their screen is visible."
        } else {
            "Charts are not composed at all: every card shows a flat last-known value instead, and " +
                "the telemetry ticker stops if nothing else needs it. The cheapest rendering path."
        },
        trailing = {
            HapticSwitch(
                checked = liveGraphs,
                onCheckedChange = { UserPreferencesStore.setLiveGraphsEnabled(it) }
            )
        }
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
        thickness = 0.8.dp
    )

    GlassRow(
        title = "Refresh rate",
        icon = Icons.Default.Speed,
        subtitle = "One cadence for every live surface — telemetry, sensors, log auto-refresh and " +
            "pinned dashboard values. Currently ${refreshRate.label} " +
            "(${UserPreferencesStore.formatPollInterval(refreshRate.intervalMs)}).",
        onClick = onRefreshRateClick,
        trailing = {
            PillAction(
                text = refreshRate.label,
                onClick = onRefreshRateClick,
                contentDescription = "Change the global refresh rate"
            )
        }
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
        thickness = 0.8.dp
    )

    GlassRow(
        title = "Frame metrics logging",
        icon = Icons.Default.Timeline,
        subtitle = frameReport?.let {
            "Measured • last ${it.windowMs / 1000} s: ${it.summary()} • ${it.config}"
        } ?: "Writes a rolling jank summary to logcat (tag DevCheckPerf) together with the " +
            "settings that produced it, so the two switches above can be measured instead of " +
            "guessed at. Off costs nothing.",
        trailing = {
            HapticSwitch(
                checked = frameMetrics,
                onCheckedChange = { UserPreferencesStore.setFrameMetricsLogging(it) }
            )
        }
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
        thickness = 0.8.dp
    )

    GlassRow(
        title = "Console shortcut in header",
        icon = Icons.Default.Terminal,
        subtitle = if (consoleShortcut) {
            "A terminal icon sits in the top bar of every category screen and opens Console directly."
        } else {
            "Console is reached from the navigation drawer only, keeping the header to search and pinning."
        },
        trailing = {
            HapticSwitch(
                checked = consoleShortcut,
                onCheckedChange = { UserPreferencesStore.setConsoleQuickAccess(it) }
            )
        }
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
        thickness = 0.8.dp
    )

    GlassRow(
        title = "Watchdog timeout",
        icon = Icons.Default.Timer,
        subtitle = "One deep read may run for " + watchdog.label + " while a report is exported or a " +
            "pinned value is resolved; past that it reports \"timed out\" instead of hanging.",
        onClick = onWatchdogClick,
        trailing = {
            PillAction(
                text = watchdog.seconds.toString() + "s",
                onClick = onWatchdogClick,
                contentDescription = "Change the watchdog timeout"
            )
        },
        iconTint = scheme.primary
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
        thickness = 0.8.dp
    )

    GlassRow(
        title = "Default export format",
        icon = Icons.Default.Description,
        subtitle = "The export dialog's primary button uses this format, and the other one stays a " +
            "tap away. " + exportFormat.tagline + ".",
        onClick = onExportFormatClick,
        trailing = {
            PillAction(
                text = exportFormat.shortLabel,
                onClick = onExportFormatClick,
                contentDescription = "Change the default export format"
            )
        },
        iconTint = scheme.primary
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
        thickness = 0.8.dp
    )

    // One tap back to a known-good performance configuration: graphs on, balanced
    // polling, watchdog and export format at their defaults. Useful after
    // experimenting — and the fastest way to A/B a jank report.
    GlassRow(
        title = "Reset advanced defaults",
        icon = Icons.Default.SettingsBackupRestore,
        subtitle = "Live graphs on, refresh rate balanced, frame-metric logging off, console " +
            "shortcut off, watchdog 20s, export format asks. Appearance and pinned data are untouched.",
        onClick = {
            hapticTick()
            UserPreferencesStore.resetAdvancedDefaults()
        },
        iconTint = scheme.tertiary
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
        thickness = 0.8.dp
    )

    SelfManagementSection()
}

/**
 * Quick access to DevCheck+'s own management actions (Settings › Advanced).
 *
 * Convenience copies of the per-app actions in Installed Apps, so a user can
 * restart or remove the app without hunting through the package list. They share
 * the exact same confirmation/warning behaviour: force-stop narrates that the
 * app closes immediately, and self-uninstall demands an extra-explicit step.
 */
@Composable
private fun SelfManagementSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val appName = remember(context) {
        runCatching { context.applicationInfo.loadLabel(context.packageManager).toString() }
            .getOrDefault(context.packageName)
    }

    var pendingAction by remember { mutableStateOf<AppManagementAction?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf<String?>(null) }

    fun perform(action: AppManagementAction) {
        if (busy) return
        busy = true
        scope.launch {
            val result = when (action) {
                AppManagementAction.FORCE_STOP -> AppManagementController.forceStop(context, context.packageName)
                AppManagementAction.UNINSTALL -> AppManagementController.uninstall(context, context.packageName)
            }
            busy = false
            if (result is AppManagementController.AppActionResult.Failure) {
                failureMessage = result.message
            }
        }
    }

    GlassRow(
        title = "Force stop $appName",
        icon = Icons.Default.StopCircle,
        iconTint = scheme.tertiary,
        subtitle = if (busy) "Working…" else "Closes the app immediately — handy after changing a theme or engine.",
        enabled = !busy,
        onClick = { pendingAction = AppManagementAction.FORCE_STOP },
        trailing = {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.primary)
            }
        }
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = scheme.onSurface.copy(alpha = LocalGlassSpec.current.borderAlpha * 0.5f),
        thickness = 0.8.dp
    )

    GlassRow(
        title = "Uninstall $appName",
        icon = Icons.Outlined.DeleteOutline,
        iconTint = scheme.error,
        subtitle = "Removes the app from this device. This cannot be undone.",
        enabled = !busy,
        onClick = { pendingAction = AppManagementAction.UNINSTALL }
    )

    val action = pendingAction
    if (action != null) {
        AppActionConfirmationDialog(
            action = action,
            appName = appName,
            packageName = context.packageName,
            isSystemApp = false,
            isSelf = true,
            selfName = appName,
            onConfirm = {
                pendingAction = null
                perform(action)
            },
            onDismiss = { pendingAction = null }
        )
    }

    val failure = failureMessage
    if (failure != null) {
        AppActionFailureDialog(message = failure, onDismiss = { failureMessage = null })
    }
}

@Composable
private fun ColumnScope.ExportSection(onExport: () -> Unit, onSectionsClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val sections by UserPreferencesStore.reportSections
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.reportSections.value)
    val allIncluded = sections.size == ReportSection.ALL.size

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
                "Sensors • Dev Environment • live telemetry. Tap to choose."
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

@Composable
private fun ColumnScope.GeneralSection(onResetOnboarding: () -> Unit) {
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

@Composable
private fun ColumnScope.AboutSection() {
    val scheme = MaterialTheme.colorScheme
    val status = rememberPrivilegeStatus()
    val themeMode by AppSettingsStore.themeMode.collectAsStateWithLifecycle(initialValue = AppSettingsStore.themeMode.value)
    val dynamicColor by AppSettingsStore.dynamicColor.collectAsStateWithLifecycle(initialValue = AppSettingsStore.dynamicColor.value)

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
                text = "Icy Cheak v${BuildConfig.VERSION_NAME}",
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
