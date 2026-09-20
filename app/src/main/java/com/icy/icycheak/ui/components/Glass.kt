package com.icy.icycheak.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.icy.icycheak.ui.theme.LocalScrolling
import com.icy.icycheak.ui.theme.LocalTheme

/**
 * Wraps a scroll container so the ambient background can pause its animation
 * while the user is actively scrolling (avoids jank). Works identically for a
 * LazyColumn (pass `listState.isScrollInProgress`) or a plain scroll
 * (`scrollState.isScrollInProgress`).
 */
@Composable
fun ProvideScrolling(scrolling: State<Boolean>, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScrolling provides scrolling) {
        content()
    }
}

/**
 * Frosted-glass / gradient surface. In Normal render mode (or OLED) it degrades
 * to a plain solid Material3 surface with a thin border — the blur/gradient
 * composables are genuinely skipped, not just hidden.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val theme = LocalTheme.current
    val liquid = theme.liquidGlass
    val bg = if (liquid) MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
    else MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(18.dp)

    Surface(
        color = bg,
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, theme.accent.copy(alpha = if (liquid) 0.28f else 0.18f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (liquid) 0.dp else 1.dp,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box {
            if (liquid) {
                Box(
                    Modifier
                        .matchParentSize()
                        .alpha(0.10f)
                        .background(
                            Brush.linearGradient(
                                0f to theme.gradientStart,
                                1f to theme.gradientEnd
                            )
                        )
                )
            }
            Box(Modifier.padding(14.dp)) { content() }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun InfoRowView(label: String, value: String, emphasized: Boolean = false, warning: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            color = when {
                warning -> Color(0xFFF85149)
                emphasized -> LocalTheme.current.accent
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun LoadingSkeleton() {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) {
            Box(
                Modifier.fillMaxWidth().height(46.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Inline explanation for privilege-gated data that couldn't be read. */
@Composable
fun WarningNote(message: String) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD29922))
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF85149))
        onRetry?.let {
            Surface(
                color = LocalTheme.current.accent, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable(onClick = it)
            ) {
                Text("Retry", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White)
            }
        }
    }
}

/**
 * Ambient animated background. In Liquid Glass mode it draws blurred, drifting
 * gradient blobs. In Normal mode / OLED it is a single solid Material3 color —
 * no blur, no gradient, no animation. The animated variant is ONLY composed
 * when not scrolling, so a scroll freezes it instantly (and restores on stop).
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier.fillMaxSize()) {
    val theme = LocalTheme.current
    val scrolling by LocalScrolling.current
    val liquid = theme.liquidGlass && !theme.oled && theme.ambient != com.icy.icycheak.data.settings.AmbientStyle.NONE
    if (liquid && !scrolling) {
        AnimatedAmbient(modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.background))
    }
}

@Composable
private fun AnimatedAmbient(modifier: Modifier) {
    val theme = LocalTheme.current
    val transition = rememberInfiniteTransition(label = "ambient")
    val x1 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse))
    val y1 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Reverse))
    val x2 by transition.animateFloat(1f, 0f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse))
    val y2 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse))

    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.fillMaxSize().alpha(0.9f).blur(60.dp)
                .background(
                    Brush.radialGradient(
                        radius = 600f,
                        center = androidx.compose.ui.geometry.Offset(x1 * 1000f, y1 * 1200f),
                        colors = listOf(theme.gradientStart.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier.fillMaxSize().alpha(0.8f).blur(60.dp)
                .background(
                    Brush.radialGradient(
                        radius = 550f,
                        center = androidx.compose.ui.geometry.Offset(x2 * 1000f, y2 * 1200f),
                        colors = listOf(theme.gradientEnd.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
    }
}

/** Small selectable chip used by segmented pickers and filters. */
@Composable
fun SurfaceChip(
    selected: Boolean,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val theme = LocalTheme.current
    val bg = if (selected) theme.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val border = if (selected) theme.accent else MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) theme.accent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
