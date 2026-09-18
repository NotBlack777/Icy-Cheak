package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.AccentPalette
import com.icy.devcheckplus.data.BackgroundAnimation
import com.icy.devcheckplus.data.ExportFormatPreference
import com.icy.devcheckplus.data.GradientStyle
import com.icy.devcheckplus.data.RefreshRate
import com.icy.devcheckplus.data.ReportSection
import com.icy.devcheckplus.data.WatchdogTimeout
import com.icy.devcheckplus.data.UserPreferencesStore
import com.icy.devcheckplus.ui.theme.contentColorOn
import kotlin.math.sin

/**
 * Tappable value chip.
 *
 * This is the interactive counterpart of the informational pill: the *visual* is
 * a small rounded badge, but the tap target is padded to the 48 dp platform
 * minimum, so a chip that shows a value the user can change is actually
 * reachable (and reported correctly to accessibility services).
 */
@Composable
fun PillAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberHapticTick()
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            // 48 dp touch target even though the pill itself is ~24 dp tall: the
            // padding around it is part of the clickable area.
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription
            ) { tick(); onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary.copy(alpha = 0.14f * alpha))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.primary.copy(alpha = alpha)
            )
        }
    }
}

/**
 * Bottom sheet used by every Settings picker: title, optional explanation and a
 * scrolling body, themed with the active glass spec.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    // The sheet's own scroll counts as scroll activity: the glass inside a sheet
    // flattens while it is being flicked exactly like a screen's cards do.
    val scrollState = rememberScrollState()
    TrackScrollActivity(scrollState)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

/** One selectable line inside a picker: label, optional caption, check mark. */
@Composable
fun PickerOptionRow(
    label: String,
    caption: String?,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable BoxScope.() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberHapticTick()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton) { tick(); onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
                content = leading
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.primary else scheme.onSurface
            )
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Global refresh-rate picker.
 *
 * This replaced the old "live telemetry polling" picker: the telemetry interval
 * is still what the user picks here, but the value now also drives the sensor
 * publisher, the log viewer's auto-refresh and the dashboard's pinned re-reads,
 * so there is exactly one cadence to reason about (and one to lower when the
 * device feels slow). The battery/CPU trade-off is spelled out per option.
 */
@Composable
fun RefreshRateSheet(current: RefreshRate, onSelect: (RefreshRate) -> Unit, onDismiss: () -> Unit) {
    val rates = remember { RefreshRate.values().toList() }
    PickerSheet(
        title = "Refresh rate",
        subtitle = "One cadence for everything that updates live: telemetry charts, the Sensors tab, " +
            "the log viewer's auto-refresh and the dashboard's pinned values.",
        onDismiss = onDismiss
    ) {
        rates.forEach { rate ->
            PickerOptionRow(
                label = "${rate.label}  •  ${UserPreferencesStore.formatPollInterval(rate.intervalMs)}",
                caption = rate.cost,
                selected = rate == current,
                onClick = { onSelect(rate); onDismiss() }
            )
        }
        PickerNote(
            text = "Real-time may increase battery and CPU usage — every telemetry sample can cost one " +
                "privileged shell read. Deep re-reads (pinned values, logcat) stay at least 5 s apart " +
                "whatever you pick here, and all sampling stops in the background."
        )
    }
}

/**
 * Watchdog duration picker (Settings › Advanced).
 *
 * Bounds one *deep* read — a category while exporting a report, or a category
 * while resolving a pinned dashboard value. Reads run in parallel behind their own
 * watchdog, so this also bounds the whole export.
 */
@Composable
fun WatchdogSheet(
    current: WatchdogTimeout,
    onSelect: (WatchdogTimeout) -> Unit,
    onDismiss: () -> Unit
) {
    val options = remember { WatchdogTimeout.values().toList() }
    PickerSheet(
        title = "Watchdog timeout",
        subtitle = "How long one deep read may take while a report is exported or a pinned value is " +
            "resolved. A root or Shizuku call that overruns it reports \"Unavailable — request " +
            "timed out\" instead of hanging the UI.",
        onDismiss = onDismiss
    ) {
        options.forEach { option ->
            PickerOptionRow(
                label = option.label,
                caption = option.tagline,
                selected = option == current,
                onClick = { onSelect(option); onDismiss() }
            )
        }
        PickerNote(
            text = "Longer gives a slow device more chance to answer and a hung shell more time before " +
                "it is given up on. Categories are collected in parallel, so the whole export is " +
                "bounded by roughly this value too."
        )
    }
}

/** Default export format picker (Settings › Advanced). */
@Composable
fun ExportFormatSheet(
    current: ExportFormatPreference,
    onSelect: (ExportFormatPreference) -> Unit,
    onDismiss: () -> Unit
) {
    val options = remember { ExportFormatPreference.values().toList() }
    PickerSheet(
        title = "Default export format",
        subtitle = "What the export action does first. The other format always stays one tap away in " +
            "the dialog, so nothing becomes unreachable.",
        onDismiss = onDismiss
    ) {
        options.forEach { option ->
            PickerOptionRow(
                label = option.label,
                caption = option.tagline,
                selected = option == current,
                onClick = { onSelect(option); onDismiss() }
            )
        }
    }
}

/** Report-content picker for "What is included". */
@Composable
fun ReportSectionsSheet(
    selected: Set<ReportSection>,
    onToggle: (ReportSection, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    PickerSheet(
        title = "Report sections",
        subtitle = "Choose which of the ${ReportSection.ALL.size} sections an exported device report contains. " +
            "Everything is included by default.",
        onDismiss = onDismiss
    ) {
        ReportSection.values().forEach { section ->
            val checked = section in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggle(section, !checked) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(section, it) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selected.size} of ${ReportSection.ALL.size} selected",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onSelectAll,
                enabled = selected.size != ReportSection.ALL.size
            ) {
                Text("Include all")
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Appearance pickers (inline tile grids)                             */
/* ------------------------------------------------------------------ */

/** Accent colour grid — one swatch per [AccentPalette]. */
@Composable
fun AccentGrid(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit
) {
    val palettes = remember { AccentPalette.values().toList() }
    TileGrid(items = palettes, columns = 3, spacing = 10.dp, aspectRatio = 0.92f) { tileModifier, palette ->
        SelectableTile(
            selected = palette == selected,
            onClick = { onSelect(palette) },
            modifier = tileModifier,
            label = palette.label,
            supporting = palette.tagline,
            preview = { AccentSwatchPreview(palette) }
        )
    }
}

@Composable
private fun BoxScope.AccentSwatchPreview(palette: AccentPalette) {
    val seed = Color(palette.seed)
    val companion = Color(palette.companion)
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(48.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(seed, companion)))
            .border(2.dp, Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(seed.contentColorOn().copy(alpha = 0.85f))
        )
    }
}

/** Gradient style grid — each tile previews the direction/colour pair it applies. */
@Composable
fun GradientGrid(
    selected: GradientStyle,
    onSelect: (GradientStyle) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val styles = remember { GradientStyle.values().toList() }
    TileGrid(items = styles, columns = 2, spacing = 10.dp, aspectRatio = 1f) { tileModifier, style ->
        SelectableTile(
            selected = style == selected,
            onClick = { onSelect(style) },
            modifier = tileModifier,
            label = style.label,
            supporting = style.tagline,
            preview = { GradientStylePreview(style = style, scheme = scheme) }
        )
    }
}

@Composable
private fun BoxScope.GradientStylePreview(
    style: GradientStyle,
    scheme: androidx.compose.material3.ColorScheme
) {
    val surface = scheme.surface
    val brush: Brush = when (style) {
        GradientStyle.DEFAULT -> Brush.verticalGradient(
            listOf(scheme.primary.copy(alpha = 0.28f), surface)
        )
        GradientStyle.SOLID -> Brush.verticalGradient(listOf(surface, surface))
        GradientStyle.OCEAN -> Brush.linearGradient(
            listOf(scheme.primary.copy(alpha = 0.55f), Color(0xFF10304F))
        )
        GradientStyle.SUNSET -> Brush.linearGradient(
            listOf(Color(0xFFFFB020), Color(0xFFFF6FA5))
        )
        GradientStyle.VOID -> Brush.verticalGradient(
            listOf(scheme.tertiary.copy(alpha = 0.65f), Color(0xFF07060E))
        )
    }
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.9f)
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
    ) {
        if (style == GradientStyle.SOLID) {
            // Solid needs an explicit cue that it is intentionally flat.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.55f))
            )
        }
    }
}

/**
 * Ambient background style grid — one full-tile still preview per style.
 *
 * Each preview is a *static* frame of the style it selects (the aurora and the
 * starfield draw theirs on a small Canvas with a fixed phase), so a grid of seven
 * tiles costs nothing per second while still showing what the background will
 * actually look like.
 */
@Composable
fun BackgroundAnimationGrid(
    selected: BackgroundAnimation,
    onSelect: (BackgroundAnimation) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val styles = remember { BackgroundAnimation.values().toList() }
    TileGrid(items = styles, columns = 2, spacing = 10.dp, aspectRatio = 1f) { tileModifier, style ->
        SelectableTile(
            selected = style == selected,
            onClick = { onSelect(style) },
            modifier = tileModifier,
            label = style.label,
            supporting = style.tagline,
            preview = { BackgroundAnimationPreview(style = style, scheme = scheme) }
        )
    }
}

@Composable
private fun BoxScope.BackgroundAnimationPreview(
    style: BackgroundAnimation,
    scheme: androidx.compose.material3.ColorScheme
) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.background)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
    ) {
        when (style) {
            BackgroundAnimation.NONE -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(scheme.background, scheme.surfaceVariant.copy(alpha = 0.22f))))
            )

            BackgroundAnimation.GRADIENT_DRIFT -> {
                // Two offset "blobs", drawn as soft radial gradients: a still frame
                // of the drift, so the preview costs nothing per second.
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .align(Alignment.TopStart)
                        .background(
                            Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.55f), Color.Transparent))
                        )
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.BottomEnd)
                        .background(
                            Brush.radialGradient(listOf(scheme.tertiary.copy(alpha = 0.5f), Color.Transparent))
                        )
                )
            }

            BackgroundAnimation.AURORA_WAVES -> {
                val curtains = remember(scheme) {
                    listOf(
                        scheme.primary to 0.30f,
                        scheme.tertiary to 0.52f,
                        scheme.secondary to 0.74f
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    val w = size.width
                    val h = size.height
                    curtains.forEachIndexed { index, (color, anchor) ->
                        val baseY = h * anchor
                        val path = Path()
                        path.moveTo(0f, baseY)
                        for (i in 1..16) {
                            val x = w * i / 16f
                            path.lineTo(x, baseY + h * 0.10f * sin((x / w) * 6f + index * 2f))
                        }
                        path.lineTo(w, h)
                        path.lineTo(0f, h)
                        path.close()
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                                startY = baseY,
                                endY = h
                            )
                        )
                    }
                }
            }

            BackgroundAnimation.FLOATING_ORBS -> {
                listOf(
                    Triple(22.dp, Alignment.CenterStart, scheme.primary),
                    Triple(30.dp, Alignment.Center, scheme.tertiary),
                    Triple(16.dp, Alignment.CenterEnd, scheme.secondary)
                ).forEach { (diameter, alignment, color) ->
                    Box(
                        modifier = Modifier
                            .align(alignment)
                            .size(diameter)
                            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent)))
                    )
                }
                // The lit edge that makes an orb read as a sphere.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 6.dp, end = 6.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.30f))
                )
            }

            BackgroundAnimation.MESH_GRADIENT -> {
                val points = remember(scheme) {
                    listOf(
                        Triple(0.22f, 0.28f, scheme.primary),
                        Triple(0.72f, 0.20f, scheme.tertiary),
                        Triple(0.34f, 0.82f, scheme.secondary),
                        Triple(0.84f, 0.74f, scheme.primary)
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    points.forEach { (fx, fy, color) ->
                        val center = Offset(size.width * fx, size.height * fy)
                        val radius = size.minDimension * 0.75f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color.copy(alpha = 0.42f), Color.Transparent),
                                center = center,
                                radius = radius
                            ),
                            radius = radius,
                            center = center
                        )
                    }
                }
            }

            BackgroundAnimation.PARTICLES -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                        .background(
                            Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.35f), Color.Transparent))
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.9f, 0.6f, 1f, 0.5f, 0.75f).forEachIndexed { index, alpha ->
                        Box(
                            modifier = Modifier
                                .size(if (index % 2 == 0) 5.dp else 3.dp)
                                .clip(CircleShape)
                                .background(scheme.primary.copy(alpha = alpha))
                        )
                    }
                }
            }

            BackgroundAnimation.STARFIELD -> Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.16f), Color.Transparent)))
            ) {
                // Fixed pseudo-random field: same stars every recomposition.
                var seed = 7_919
                fun next(): Float {
                    seed = (seed * 1_103_515_245 + 12_345) and 0x7FFFFFFF
                    return (seed % 1_000) / 1_000f
                }
                repeat(26) { index ->
                    val x = size.width * next()
                    val y = size.height * next()
                    val radius = (0.6f + next() * 1.3f) * density
                    drawCircle(
                        color = Color.White,
                        radius = radius,
                        center = Offset(x, y),
                        alpha = if (index % 4 == 0) 0.95f else 0.55f
                    )
                }
            }
        }
    }
}

/** Two-line explanation under the background tiles. */
@Composable
fun PickerNote(text: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    )
}
