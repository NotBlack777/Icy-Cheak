package com.icy.devcheckplus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.ui.theme.DeepDarkBackground
import com.icy.devcheckplus.ui.theme.DeepDarkSurface
import com.icy.devcheckplus.ui.theme.LightBackground
import com.icy.devcheckplus.ui.theme.LightSurface
import com.icy.devcheckplus.ui.theme.LocalGlassSpec
import com.icy.devcheckplus.ui.theme.OledBackground
import com.icy.devcheckplus.ui.theme.OledSurfaceVariant
import com.icy.devcheckplus.ui.theme.ThemeMode

/**
 * Square / rounded selectable tile in the style of Morphe's "App icon" and
 * "Background animation" grids: the preview content owns the whole tile body
 * (no small icon floating in an empty box), and the selected state is a coloured
 * border + glow with a checkmark badge in the corner.
 */
@Composable
fun SelectableTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    supporting: String? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    preview: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current
    val tileTick = rememberHapticTick()
    // Tiles are numerous and cheap, so they observe the *quantised* glass fidelity:
    // a tile grid recomposes four times while a list settles instead of once per
    // animation frame, and still visibly fades between the flat scroll fallback and
    // the full gradient treatment.
    val fidelity = rememberGlassFidelityStep()

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.onSurface.copy(alpha = 0.12f)
            selected -> scheme.primary
            else -> scheme.onSurface.copy(alpha = spec.borderAlpha)
        },
        animationSpec = tween(200),
        label = "tileBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(200),
        label = "tileBorderWidth"
    )
    val containerTint by animateColorAsState(
        targetValue = if (selected) {
            scheme.primary.copy(alpha = 0.16f)
        } else {
            scheme.surface.copy(alpha = (spec.cardAlpha * 0.75f).coerceIn(0f, 1f))
        },
        animationSpec = tween(220),
        label = "tileTint"
    )
    val glow by animateFloatAsState(
        targetValue = if (selected && spec.glow && enabled) 1f else 0f,
        animationSpec = tween(280),
        label = "tileGlow"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurface,
        animationSpec = tween(200),
        label = "tileLabelColor"
    )

    // FIXED: Base gradient always visible, even during scroll.
    // Only expensive glow/shadow is reduced via fidelity, not the gradient itself.
    val flatTint = scheme.surface.copy(alpha = (spec.cardAlpha * 0.75f).coerceIn(0f, 1f))
    val tileBrush = remember(containerTint, scheme, spec.cardAlpha) {
        Brush.verticalGradient(
            listOf(
                containerTint,
                scheme.surface.copy(alpha = (spec.cardAlpha * 0.5f).coerceIn(0f, 1f))
            )
        )
    }

    val description = "$label${supporting?.let { ", $it" } ?: ""}${if (selected) ", selected" else ""}"
    // Captured under a distinct name so the semantics assignment below can never
    // resolve back onto the receiver's own `selected` property.
    val selectedValue = selected

    Box(
        modifier = modifier
            // Glow shadow reduced during scroll (expensive offscreen layer) but base gradient stays.
            .shadow(
                elevation = 10.dp * glow * (0.2f + 0.8f * fidelity),
                shape = shape,
                clip = false,
                ambientColor = scheme.primary,
                spotColor = scheme.primary
            )
            .clip(shape)
            .background(tileBrush)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clickable(enabled = enabled, onClick = { tileTick(); onClick() })
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selectedValue
                contentDescription = description
            }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview owns every remaining pixel of the tile.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
                content = preview
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (enabled) labelColor else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (supporting != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supporting,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(
            visible = selected,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = scaleIn(animationSpec = tween(200), initialScale = 0.4f) + fadeIn(tween(160)),
            exit = scaleOut(animationSpec = tween(160)) + fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .shadow(
                        elevation = if (spec.glow) 6.dp else 0.dp,
                        shape = CircleShape,
                        spotColor = scheme.primary,
                        ambientColor = scheme.primary
                    )
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** Large icon that scales to fill the tile's preview area. */
@Composable
fun BoxScope.TileIconPreview(
    icon: ImageVector,
    tint: Color,
    fill: Float = 0.82f,
    halo: Boolean = true
) {
    if (halo) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.92f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxSize(fill)
    )
}

/**
 * Mini "device screen" mock used by the theme tiles — the preview fills the tile
 * instead of showing a generic palette icon.
 */
@Composable
fun BoxScope.ThemeModePreview(mode: ThemeMode) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary

    val baseBrush = when (mode) {
        ThemeMode.LIGHT -> Brush.verticalGradient(listOf(LightBackground, Color(0xFFE9EDF2)))
        ThemeMode.DARK -> Brush.verticalGradient(listOf(DeepDarkBackground, Color(0xFF0D0E11)))
        ThemeMode.OLED -> Brush.verticalGradient(listOf(OledBackground, OledBackground))
        ThemeMode.SYSTEM -> Brush.linearGradient(
            0f to LightBackground,
            0.499f to LightBackground,
            0.5f to DeepDarkBackground,
            1f to DeepDarkBackground,
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }
    val cardColor = when (mode) {
        ThemeMode.LIGHT -> LightSurface
        ThemeMode.DARK -> DeepDarkSurface
        ThemeMode.OLED -> OledSurfaceVariant
        ThemeMode.SYSTEM -> Color.White.copy(alpha = 0.14f)
    }
    val textColor = if (mode == ThemeMode.LIGHT) Color(0xFF3A4048) else Color.White.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxHeight()
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(12.dp))
            .background(baseBrush)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(7.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(cardColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(cardColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.35f))
            )
        }
    }
}

/**
 * Fixed-column grid of tiles. Leftover slots in the final row are padded with
 * invisible spacers so tile widths never change between rows.
 */
@Composable
fun <T> TileGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    spacing: Dp = 10.dp,
    aspectRatio: Float = 1f,
    itemContent: @Composable (tileModifier: Modifier, item: T) -> Unit
) {
    val rows = remember(items, columns) { items.chunked(columns) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                rowItems.forEach { item ->
                    itemContent(
                        Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio),
                        item
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio)
                    )
                }
            }
        }
    }
}
