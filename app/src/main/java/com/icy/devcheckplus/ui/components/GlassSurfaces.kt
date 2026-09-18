package com.icy.devcheckplus.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.ui.theme.LocalGlassSpec

/**
 * "Liquid glass" building blocks.
 *
 * The frosted look is produced by a *small*, card-sized decoration layer that is
 * blurred with [Modifier.blur] (RenderEffect backed on API 31+) and then covered
 * by a semi-transparent surface tint + hairline border + sheen. Blur is never
 * applied to full-screen content or to every row of a long list — only to the few
 * grouped panels on the Settings screen and to the top app bar, and it is
 * switched off completely in OLED mode where [com.icy.devcheckplus.ui.theme.GlassSpec]
 * reports a 0dp radius (solid colour fallback).
 */

/**
 * Whether frosted surfaces may build their blur layer right now.
 *
 * `Modifier.blur` renders the layer into an offscreen buffer and applies a
 * RenderEffect; that is cheap to *translate* but expensive to *create*, and a
 * LazyColumn creates items continuously while flinging. Scrolling containers
 * therefore provide `false` during a scroll and `true` again once the list
 * settles, which trades a momentary flat tint for a smooth fling. Two
 * recompositions per fling, instead of a blur setup per card per frame.
 */
val LocalFrostEffect = compositionLocalOf { true }

private val ShadowTint = Color(0xCC000000)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    frosted: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = LocalGlassSpec.current
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .shadow(
                elevation = spec.cardElevation,
                shape = shape,
                clip = false,
                ambientColor = ShadowTint,
                spotColor = ShadowTint
            )
            .clip(shape)
    ) {
        if (frosted && LocalFrostEffect.current) {
            FrostedLayer(
                shape = shape,
                radius = spec.cardBlurRadius,
                primary = scheme.primary,
                tertiary = scheme.tertiary,
                modifier = Modifier.matchParentSize()
            )
        }
        // Semi-transparent surface tint (the "glass" body).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.surface.copy(alpha = (spec.cardAlpha + 0.10f).coerceAtMost(1f)),
                            scheme.surface.copy(alpha = spec.cardAlpha)
                        )
                    )
                )
        )
        GlassEdges(shape = shape, borderAlpha = spec.borderAlpha, sheenAlpha = spec.sheenAlpha, modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Frosted variant of the app bar: one blurred layer for a single, small surface.
 */
@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = LocalGlassSpec.current
    val scheme = MaterialTheme.colorScheme

    Box(modifier = modifier.clip(shape)) {
        FrostedLayer(
            shape = shape,
            radius = spec.barBlurRadius,
            primary = scheme.primary,
            tertiary = scheme.secondary,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(scheme.surface.copy(alpha = spec.barAlpha))
        )
        // Hairline at the bottom edge only.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.86f to Color.Transparent,
                        1f to scheme.onSurface.copy(alpha = spec.borderAlpha * 0.45f)
                    )
                )
        )
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Blurred decorative layer. Below API 31 [Modifier.blur] cannot run on a
 * hardware canvas, so the whole layer is skipped and the solid tint above acts
 * as the fallback.
 */
@Composable
private fun FrostedLayer(
    shape: Shape,
    radius: Dp,
    primary: Color,
    tertiary: Color,
    modifier: Modifier = Modifier
) {
    if (radius <= 0.dp) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    Box(
        modifier = modifier.blur(radius, BlurredEdgeTreatment(shape))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.20f),
                            Color.Transparent,
                            tertiary.copy(alpha = 0.14f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )
    }
}

/** Border + top sheen that sell the glass edge. */
@Composable
private fun GlassEdges(
    shape: Shape,
    borderAlpha: Float,
    sheenAlpha: Float,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        scheme.onSurface.copy(alpha = borderAlpha),
                        scheme.onSurface.copy(alpha = borderAlpha * 0.25f)
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                shape = shape
            )
    )
    if (sheenAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = sheenAlpha * 0.5f),
                            Color.White.copy(alpha = 0f)
                        )
                    )
                )
        )
    }
}

/** Morphe-style section label floating above a card. */
@Composable
fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
            letterSpacing = 0.9.sp
        )
        if (supporting != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A tappable row inside a [GlassCard]: icon well + title/subtitle + trailing slot.
 * Rounded so the ripple stays inside the card.
 */
@Composable
fun GlassRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val rowTick = rememberHapticTick()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = { rowTick(); onClick() })
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background((iconTint ?: scheme.primary).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}
