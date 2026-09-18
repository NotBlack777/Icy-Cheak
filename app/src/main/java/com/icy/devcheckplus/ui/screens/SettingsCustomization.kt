package com.icy.devcheckplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.ReportSection
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassRow
import com.icy.devcheckplus.ui.components.HapticSwitch
import com.icy.devcheckplus.ui.components.SelectableTile
import com.icy.devcheckplus.ui.components.TileGrid
import com.icy.devcheckplus.ui.components.rememberHapticTick
import com.icy.devcheckplus.ui.theme.AccentPreset
import com.icy.devcheckplus.ui.theme.AmbientStyle
import com.icy.devcheckplus.ui.theme.LocalGlassSpec
import com.icy.devcheckplus.ui.theme.SurfaceGradient
import com.icy.devcheckplus.ui.theme.ThemeMode
import com.icy.devcheckplus.ui.theme.ramp

/* ------------------------------------------------------------------ */
/*  Tappable value badges                                              */
/* ------------------------------------------------------------------ */

/**
 * A badge that is actually a control.
 *
 * The old `PillLabel` looked exactly like this — rounded, accent-tinted, sitting
 * in the trailing slot of a row — but had no click handler, which is why "1 s"
 * and "9 sections" read as tappable and did nothing. This version adds a
 * chevron affordance, ripple, a haptic tick and a 48dp minimum touch target
 * (the pill itself stays small; the *hit area* is padded out).
 */
@Composable
internal fun SettingsPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberHapticTick()

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable {
                tick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.primary
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = scheme.primary
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Sheets                                                             */
/* ------------------------------------------------------------------ */

private data class IntervalOption(val millis: Long, val label: String, val note: String)

private val INTERVAL_OPTIONS = listOf(
    IntervalOption(500L, "0.5 s", "Fastest — doubles the sampling cost"),
    IntervalOption(1_000L, "1 s", "Default — smooth charts, modest cost"),
    IntervalOption(2_000L, "2 s", "Balanced — noticeably cheaper"),
    IntervalOption(5_000L, "5 s", "Battery saver — charts update slowly")
)

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun OptionRow(
    label: String,
    note: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberHapticTick()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                tick()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) scheme.primary else scheme.onSurface
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = scheme.primary
            )
        }
    }
}

/** Sampling cadence picker for the "Live telemetry polling" row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PollingIntervalSheet(
    currentMillis: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        SheetHeader(
            title = "Live telemetry interval",
            subtitle = "How often the CPU, RAM and battery charts take a sample. Polling only runs while a chart is on screen and the app is in the foreground."
        )
        Spacer(modifier = Modifier.height(6.dp))
        INTERVAL_OPTIONS.forEach { option ->
            OptionRow(
                label = option.label,
                note = option.note,
                selected = option.millis == currentMillis,
                onClick = {
                    onSelect(option.millis)
                    onDismiss()
                }
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

/** Section picker for the "What is included" export row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSectionsSheet(
    selected: Set<ReportSection>,
    onDismiss: () -> Unit,
    onToggle: (ReportSection) -> Unit,
    onSelectAll: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberHapticTick()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surface
    ) {
        SheetHeader(
            title = "Report sections",
            subtitle = "Unticked sections are not collected at all — no privileged shell, no sensor window, no package scan — so the export is both smaller and faster. At least one section stays selected."
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { tick(); onSelectAll() }) { Text("Select all") }
        }

        ReportSection.values().forEach { section ->
            val checked = section in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = checked || selected.size > 1) {
                        tick()
                        onToggle(section)
                    }
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // The row owns the tap target; the box is a state indicator.
                Checkbox(checked = checked, onCheckedChange = null)
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

/* ------------------------------------------------------------------ */
/*  Colours & gradients                                                */
/* ------------------------------------------------------------------ */

@Composable
internal fun ColorsCard(
    accentArgb: Long,
    dynamicColor: Boolean,
    gradient: SurfaceGradient,
    onAccentChange: (AccentPreset?) -> Unit,
    onGradientChange: (SurfaceGradient) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val selectedAccent = remember(accentArgb) { AccentPreset.fromArgb(accentArgb) }
    val accentColor = selectedAccent?.color ?: scheme.primary

    // "Auto" (null) hands control back to Material You / the shipped palette.
    val swatchRows = remember {
        (listOf<AccentPreset?>(null) + AccentPreset.values().toList()).chunked(5)
    }

    GlassCard(frosted = true) {
        Text(
            text = "Accent colour",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = if (dynamicColor) {
                "Applied to buttons, switches, highlights and selection states. A chosen preset overrides Material You; Auto hands control back to your wallpaper."
            } else {
                "Applied to buttons, switches, highlights and selection states everywhere in the app."
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        swatchRows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { preset ->
                    AccentSwatch(
                        preset = preset,
                        selected = preset == selectedAccent,
                        onClick = { onAccentChange(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f),
            thickness = 0.8.dp
        )

        Text(
            text = "Card gradient",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Surface treatment for every glass card. Solid removes gradients entirely, and OLED always renders flat for contrast and to avoid extra overdraw.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        val gradients = remember { SurfaceGradient.values().toList() }
        TileGrid(
            items = gradients,
            columns = 2,
            spacing = 10.dp,
            aspectRatio = 1.35f
        ) { tileModifier, style ->
            SelectableTile(
                selected = style == gradient,
                onClick = { onGradientChange(style) },
                modifier = tileModifier,
                label = style.label,
                supporting = style.tagline,
                preview = { GradientPreview(style = style, accent = accentColor) }
            )
        }
    }
}

@Composable
private fun AccentSwatch(
    preset: AccentPreset?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberHapticTick()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The whole 48dp cell is the touch target, not just the 28dp dot.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable {
                    tick()
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, scheme.onSurface, CircleShape)
                        } else {
                            Modifier.border(1.dp, scheme.onSurface.copy(alpha = 0.18f), CircleShape)
                        }
                    )
                    .background(
                        if (preset == null) {
                            Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))
                        } else {
                            Brush.verticalGradient(listOf(preset.color, preset.color))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(scheme.surface)
                    )
                }
            }
        }
        Text(
            text = preset?.label ?: "Auto",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun BoxScope.GradientPreview(style: SurfaceGradient, accent: Color) {
    val base = Color(0xFF12141A)
    val ramp = remember(style, accent) { style.ramp(accent) }

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.88f)
            .fillMaxHeight(0.78f)
            .clip(RoundedCornerShape(12.dp))
            .background(base)
    ) {
        if (ramp.strength > 0.001f) {
            // Small tile, so the blend is pushed harder than on a real card.
            val boost = (ramp.strength * 2.4f).coerceAtMost(0.9f)
            val top = lerp(base, ramp.top, boost)
            val bottom = lerp(base, ramp.bottom, boost)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (ramp.diagonal) {
                            Brush.linearGradient(listOf(top, bottom))
                        } else {
                            Brush.verticalGradient(listOf(top, bottom))
                        }
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Background animation                                               */
/* ------------------------------------------------------------------ */

@Composable
internal fun AmbientCard(
    style: AmbientStyle,
    themeMode: ThemeMode,
    oledOverride: Boolean,
    onStyleChange: (AmbientStyle) -> Unit,
    onOledOverrideChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isOled = themeMode == ThemeMode.OLED
    var pendingStyle by remember { mutableStateOf<AmbientStyle?>(null) }

    // What is actually on screen right now (OLED clamps to None without the
    // explicit override), so the tiles never claim a state that is not true.
    val effective = if (isOled && !oledOverride) AmbientStyle.NONE else style

    GlassCard(frosted = true) {
        Text(
            text = "Background animation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "The ambient layer painted behind Settings. Particles skips the three screen-sized gradients, so it is the cheap animated option; None is a static gradient with no per-frame work at all.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        val styles = remember { AmbientStyle.values().toList() }
        TileGrid(items = styles, columns = 3, spacing = 10.dp, aspectRatio = 0.95f) { tileModifier, option ->
            SelectableTile(
                selected = option == effective,
                onClick = {
                    if (isOled && !oledOverride && option != AmbientStyle.NONE) {
                        // Ask before spending battery on a true-black theme.
                        pendingStyle = option
                    } else {
                        onStyleChange(option)
                    }
                },
                modifier = tileModifier,
                label = option.label,
                supporting = option.tagline,
                preview = { AmbientPreview(style = option, accent = scheme.primary) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isOled) {
            DetailNote(
                text = if (oledOverride) {
                    "OLED override is active: the ambient layer is drawing on a true-black theme."
                } else {
                    "OLED forces the background to None — flat black, zero per-frame draw work, best contrast and battery. Pick a style anyway and you will be warned first."
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            GlassRow(
                title = "Animate on OLED anyway",
                subtitle = "May increase battery usage on OLED displays.",
                trailing = {
                    HapticSwitch(checked = oledOverride, onCheckedChange = onOledOverrideChange)
                }
            )
        } else {
            DetailNote(text = "$effective is active. The layer pauses automatically when the app goes to the background.")
        }
    }

    pendingStyle?.let { requested ->
        AlertDialog(
            onDismissRequest = { pendingStyle = null },
            shape = MaterialTheme.shapes.large,
            containerColor = scheme.surface,
            title = {
                Text(
                    text = "Animation on an OLED theme",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "May increase battery usage on OLED displays.\n\nOLED mode normally renders a flat black background with no per-frame drawing. " +
                        "Enabling \"${requested.label}\" keeps an animated layer alive on a true-black theme, which costs GPU time and battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onOledOverrideChange(true)
                        onStyleChange(requested)
                        pendingStyle = null
                    }
                ) { Text("Enable anyway") }
            },
            dismissButton = {
                TextButton(onClick = { pendingStyle = null }) { Text("Keep it off") }
            }
        )
    }
}

@Composable
private fun BoxScope.AmbientPreview(style: AmbientStyle, accent: Color) {
    val base = Color(0xFF0B0D12)

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.88f)
            .fillMaxHeight(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .background(base)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (style) {
                AmbientStyle.GRADIENT_DRIFT -> {
                    val first = Offset(size.width * 0.30f, size.height * 0.32f)
                    val second = Offset(size.width * 0.74f, size.height * 0.68f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.55f), Color.Transparent),
                            center = first,
                            radius = size.minDimension * 0.52f
                        ),
                        radius = size.minDimension * 0.52f,
                        center = first
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.32f), Color.Transparent),
                            center = second,
                            radius = size.minDimension * 0.44f
                        ),
                        radius = size.minDimension * 0.44f,
                        center = second
                    )
                }

                AmbientStyle.PARTICLES -> {
                    // Deterministic scatter so the tile preview is stable.
                    for (index in 0 until 16) {
                        val x = size.width * ((index * 37 % 100) / 100f)
                        val y = size.height * ((index * 61 % 100) / 100f)
                        drawCircle(
                            color = accent.copy(alpha = 0.25f + (index % 4) * 0.16f),
                            radius = 1.4f + (index % 3) * 0.9f,
                            center = Offset(x, y)
                        )
                    }
                }

                AmbientStyle.NONE -> Unit
            }
        }
    }
}
