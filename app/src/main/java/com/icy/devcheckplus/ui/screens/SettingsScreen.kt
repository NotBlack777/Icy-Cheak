package com.icy.devcheckplus.ui.screens

import android.os.Build
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.data.LiveMetricsRepository
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.privilege.PrivilegeStatus
import com.icy.devcheckplus.ui.components.AmbientBackground
import com.icy.devcheckplus.ui.components.ExportReportDialog
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassRow
import com.icy.devcheckplus.ui.components.HapticSwitch
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.SelectableTile
import com.icy.devcheckplus.ui.components.ThemeModePreview
import com.icy.devcheckplus.ui.components.TileGrid
import com.icy.devcheckplus.ui.components.TileIconPreview
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import com.icy.devcheckplus.ui.theme.LocalGlassSpec
import com.icy.devcheckplus.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Settings — grouped frosted-glass cards over an ambient animated background.
 *
 * All the original capabilities are still here (privilege/execution selector,
 * public IP lookup opt-in, onboarding relaunch, about) and the persisted keys
 * are untouched; only the presentation changed, plus the new Appearance section
 * with Dark / OLED themes and dynamic colour.
 *
 * Recomposition note: this screen root intentionally subscribes to *nothing*.
 * Each card collects exactly the preference it renders (and the privilege banner
 * its own status), so flipping a switch or picking a theme tile invalidates that
 * card alone — previously every one of these flows was read at the top of
 * [SettingsScreen], which meant the whole LazyColumn re-ran on any change.
 */
@Composable
fun SettingsScreen(
    onResetOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showExportDialog by remember { mutableStateOf(false) }

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
                SettingsHeader()
            }

            item(key = "header_appearance") {
                GlassSectionHeader(title = "APPEARANCE", icon = Icons.Default.Palette)
            }
            item(key = "card_theme") {
                ThemeCard()
            }

            item(key = "header_privilege") {
                GlassSectionHeader(title = "PRIVILEGE ENGINE", icon = Icons.Default.Lock)
            }
            item(key = "card_privilege") {
                PrivilegeCard()
            }

            item(key = "header_privacy") {
                GlassSectionHeader(title = "PRIVACY & NETWORK", icon = Icons.Default.Public)
            }
            item(key = "card_privacy") {
                PrivacyCard()
            }

            item(key = "header_general") {
                GlassSectionHeader(title = "GENERAL", icon = Icons.Default.Tune)
            }
            item(key = "card_general") {
                GeneralCard(onResetOnboarding = onResetOnboarding)
            }

            item(key = "header_export") {
                GlassSectionHeader(title = "EXPORT & SHARE", icon = Icons.Default.Share)
            }
            item(key = "card_export") {
                ExportCard(onExport = { showExportDialog = true })
            }

            item(key = "header_about") {
                GlassSectionHeader(title = "ABOUT", icon = Icons.Default.Info)
            }
            item(key = "card_about") {
                AboutCard()
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
    }
}

/** Status pill source — collected where it is displayed, not at screen level. */
@Composable
private fun rememberPrivilegeStatus(): PrivilegeStatus =
    PrivilegeManager.status.collectAsStateWithLifecycle(initialValue = PrivilegeManager.status.value).value

@Composable
private fun SettingsHeader() {
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
    }
}

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

    // Collected here: a theme pick or a toggle only invalidates this card.
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

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        GlassRow(
            title = "Dynamic colour",
            icon = Icons.Default.Palette,
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "Pull accents from your wallpaper (Material You)."
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
    val spec = LocalGlassSpec.current
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
/*  Privacy, general, about                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun PrivacyCard() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val publicIpLookup by AppSettingsStore.publicIpLookup
        .collectAsStateWithLifecycle(initialValue = AppSettingsStore.publicIpLookup.value)

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
            subtitle = "CPU, RAM and battery charts share one sampling loop, only while a screen is visible, and pause in the background.",
            trailing = { PillLabel(text = "1 s") }
        )
    }
}

@Composable
private fun ExportCard(onExport: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current

    GlassCard(frosted = true) {
        GlassRow(
            title = "Export device report",
            icon = Icons.Default.Share,
            subtitle = "Full dump of every detected category as readable text or structured JSON, sent through the Android share sheet.",
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
            subtitle = "Hardware • Software • Battery • Storage • Network • Processes • Installed apps • Sensors • live telemetry, plus app version, device fingerprint and export timestamp.",
            trailing = { PillLabel(text = "9 sections") }
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

/**
 * Static value chip. It carries **no** click indication on purpose: an
 * interactive value uses `PillAction` instead, so a chip that cannot be tapped
 * can never look tappable.
 */
@Composable
private fun PillLabel(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.primary
        )
    }
}
