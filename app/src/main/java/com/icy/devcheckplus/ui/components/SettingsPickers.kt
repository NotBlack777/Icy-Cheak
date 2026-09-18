package com.icy.devcheckplus.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.AccentPalette
import com.icy.devcheckplus.data.BackgroundAnimation
import com.icy.devcheckplus.data.GradientStyle
import com.icy.devcheckplus.data.LiveMetricsPoller
import com.icy.devcheckplus.data.ReportSection
import com.icy.devcheckplus.data.UserPreferencesStore
import com.icy.devcheckplus.ui.theme.contentColorOn

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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

/** Polling cadence picker for "Live telemetry polling". */
@Composable
fun PollIntervalSheet(currentMs: Long, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    PickerSheet(
        title = "Live telemetry polling",
        subtitle = "How often CPU frequency, RAM and battery are sampled while a live screen is " +
            "visible. Faster is smoother but costs more battery; sampling always stops in the background.",
        onDismiss = onDismiss
    ) {
        LiveMetricsPoller.INTERVAL_OPTIONS_MS.forEach { interval ->
            PickerOptionRow(
                label = UserPreferencesStore.formatPollInterval(interval),
                caption = when (interval) {
                    500L -> "Smoothest charts • highest battery use"
                    1_000L -> "Default — one sample per second"
                    2_000L -> "Half the wake-ups"
                    5_000L -> "Lowest battery use • coarse charts"
                    else -> null
                },
                selected = interval == currentMs,
                onClick = { onSelect(interval); onDismiss() }
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

/** Ambient background style grid — Gradient Drift / Particles / None. */
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
                    .fillMaxWidth()
                    .height(56.dp)
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
