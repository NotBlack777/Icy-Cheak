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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.icy.devcheckplus.ui.theme.LocalGlassSpec
import com.icy.devcheckplus.ui.theme.solidSurface
import com.icy.devcheckplus.ui.theme.surfaceBrush

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

private val ShadowTint = Color(0xCC000000)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    frosted: Boolean = true,
    overlay: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = LocalGlassSpec.current

    // Only the *binary* decisions live in this scope: while a list is being flung
    // the card requests no elevation shadow at all (another offscreen layer per
    // card) and no frosted layer. The gradient cross-fade is animated inside
    // GlassSurfaceLayer below, so this body — and the card's content lambda, which
    // Compose skips anyway — does not recompose on every frame of that fade.
    val scrolling = LocalScrollActivity.current.value
    val elevation = if (scrolling) 0.dp else spec.cardElevation

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = ShadowTint,
                spotColor = ShadowTint
            )
            .clip(shape)
    ) {
        GlassSurfaceLayer(
            shape = shape,
            surfaceAlpha = spec.cardAlpha,
            blurRadius = spec.cardBlurRadius,
            borderAlpha = spec.borderAlpha,
            sheenAlpha = spec.sheenAlpha,
            frosted = frosted,
            frostPrimary = MaterialTheme.colorScheme.primary,
            frostSecondary = MaterialTheme.colorScheme.tertiary,
            bottomHairline = false,
            modifier = Modifier.matchParentSize()
        )

        // Optional tint *between* the glass and the content: how a row shows it is
        // selected without giving up the gradient underneath (see the onboarding
        // privilege cards and any list row with an active state).
        if (overlay != Color.Transparent) {
            Box(modifier = Modifier.matchParentSize().background(overlay))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Frosted-glass dialog — the app's liquid-glass surface instead of Material's flat
 * `scheme.surface` dialog background.
 *
 * Built on [Dialog] rather than `AlertDialog` deliberately: an AlertDialog paints
 * its own Surface around the slots, and a dialog is a *separate window*, so a
 * translucent container there cannot sample the content behind it. The honest way
 * to get a frosted panel is to draw the layers a [GlassCard] draws — blurred
 * decoration, translucent gradient tint, hairline border, top sheen — and lay the
 * familiar dialog slots out inside them. Slot order, spacing and end-aligned
 * buttons follow Material's dialog metrics, so only the surface changes, not how
 * these read.
 *
 * Blur costs one offscreen layer, and a dialog is a single small surface shown
 * while nothing is scrolling, so this is exactly the case the frosted layer was
 * written for; in OLED mode [com.icy.devcheckplus.ui.theme.GlassSpec] reports a 0dp
 * radius and the panel falls back to a solid tint on its own.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    /** Override for a title that has to carry a warning (Console's risk prompt). */
    titleColor: Color? = null,
    icon: ImageVector? = null,
    text: (@Composable ColumnScope.() -> Unit)? = null,
    confirmButton: @Composable RowScope.() -> Unit,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        GlassCard(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 18.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(26.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor ?: scheme.onSurface
            )
            if (text != null) {
                Spacer(modifier = Modifier.height(12.dp))
                text()
            }
            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dismissButton != null) dismissButton()
                confirmButton()
            }
        }
    }
}

/**
 * Just the glass *layers* — blurred decoration, translucent gradient tint,
 * hairline border, top sheen — with no content and no padding, for painting a
 * frosted surface behind something that supplies its own layout: a bottom sheet's
 * contents, a navigation drawer, an app bar.
 *
 * Use it with `Modifier.matchParentSize()` inside a [Box] whose size is decided by
 * the content it is backing, so the glass never drives layout.
 */
@Composable
fun BoxScope.GlassBackdrop(
    shape: Shape,
    modifier: Modifier = Modifier,
    frosted: Boolean = true,
    surfaceAlpha: Float = LocalGlassSpec.current.cardAlpha
) {
    val spec = LocalGlassSpec.current
    val scheme = MaterialTheme.colorScheme
    GlassSurfaceLayer(
        shape = shape,
        surfaceAlpha = surfaceAlpha,
        blurRadius = spec.cardBlurRadius,
        borderAlpha = spec.borderAlpha,
        sheenAlpha = spec.sheenAlpha,
        frosted = frosted,
        frostPrimary = scheme.primary,
        frostSecondary = scheme.tertiary,
        bottomHairline = false,
        modifier = modifier
    )
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
        GlassSurfaceLayer(
            shape = shape,
            surfaceAlpha = spec.barAlpha,
            blurRadius = spec.barBlurRadius,
            borderAlpha = spec.borderAlpha,
            sheenAlpha = 0f,
            frosted = true,
            frostPrimary = scheme.primary,
            frostSecondary = scheme.secondary,
            bottomHairline = true,
            modifier = Modifier.matchParentSize()
        )
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Everything that *paints* a glass surface: the blurred decoration layer, the
 * gradient-or-flat body, the hairline edge, the sheen and (for the app bar) the
 * bottom hairline.
 *
 * FIXED: Surface gradient is now ALWAYS visible. During scrolling only expensive
 * effects (blur, elevation, sheen) are reduced — never the gradient itself.
 * This fixes the critical bug where gradient disappeared while dragging.
 */
@Composable
private fun BoxScope.GlassSurfaceLayer(
    shape: Shape,
    surfaceAlpha: Float,
    blurRadius: Dp,
    borderAlpha: Float,
    sheenAlpha: Float,
    frosted: Boolean,
    frostPrimary: Color,
    frostSecondary: Color,
    bottomHairline: Boolean,
    modifier: Modifier = Modifier
) {
    val spec = LocalGlassSpec.current
    val scheme = MaterialTheme.colorScheme
    val scrolling = LocalScrollActivity.current.value
    val fidelity = rememberGlassFidelity()

    // Blur is expensive — skip it while scrolling. Gradient stays visible.
    if (frosted && !scrolling && fidelity > 0.05f) {
        FrostedLayer(
            shape = shape,
            radius = blurRadius,
            fidelity = fidelity.coerceAtLeast(0.6f),
            primary = frostPrimary,
            secondary = frostSecondary,
            modifier = modifier
        )
    }

    // FIX: Always paint full gradient — never lerp to flat during scroll.
    // Performance optimization only affects blur/shadow/sheen, not the base surface.
    val surfaceBrush = remember(spec.gradientStyle, scheme, surfaceAlpha, spec.customGradient) {
        spec.gradientStyle.surfaceBrush(scheme, surfaceAlpha, 1f, spec.customGradient)
    }
    Box(
        modifier = modifier
            .then(
                if (surfaceBrush != null) {
                    Modifier.background(surfaceBrush)
                } else {
                    Modifier.background(spec.gradientStyle.solidSurface(scheme, surfaceAlpha))
                }
            )
    )

    // Border always visible, sheen fades during scroll (cheap visual optimization)
    GlassEdges(
        shape = shape,
        borderAlpha = borderAlpha,
        sheenAlpha = sheenAlpha * (0.3f + 0.7f * fidelity),
        modifier = modifier
    )

    if (bottomHairline) {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.86f to Color.Transparent,
                    1f to scheme.onSurface.copy(alpha = borderAlpha * 0.45f * (0.5f + 0.5f * fidelity))
                )
            )
        )
    }
}

/**
 * Blurred decorative layer. Below API 31 [Modifier.blur] cannot run on a
 * hardware canvas, so the whole layer is skipped and the solid tint above acts
 * as the fallback. [fidelity] scales the two tints, which is what makes the frost
 * fade in with the gradient instead of appearing in one frame.
 */
@Composable
private fun FrostedLayer(
    shape: Shape,
    radius: Dp,
    fidelity: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    if (radius <= 0.dp) return
    if (fidelity <= 0.01f) return
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
                            primary.copy(alpha = 0.20f * fidelity),
                            Color.Transparent,
                            secondary.copy(alpha = 0.14f * fidelity)
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
 * A whole *group* of rows in one glass box: header, hairline, content.
 *
 * Grouped-settings structure (the layout a system settings screen uses: a labelled
 * box per category) rendered in this app's own liquid-glass skin. The header sits
 * **inside** the bordered/gradient container rather than floating above it as bare
 * text, so a category reads as one object — the same visual language as the rows
 * inside it, which are no longer cards of their own.
 *
 * Everything expensive is inherited from [GlassCard], so a group box flattens its
 * gradient, drops its blur and its shadow while the list is being flung and fades
 * back once it settles.
 */
@Composable
fun GlassGroupBox(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current

    GlassCard(
        modifier = modifier,
        shape = shape,
        contentPadding = contentPadding,
        frosted = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(scheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
                letterSpacing = 0.9.sp,
                modifier = Modifier.weight(1f)
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

        // Hairline that ties the header to the rows below it without adding a
        // second surface: same tint and thickness the rows already use internally.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(scheme.onSurface.copy(alpha = spec.borderAlpha * 0.5f))
        )

        Spacer(modifier = Modifier.height(8.dp))

        content()
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
