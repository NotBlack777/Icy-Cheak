package com.icy.devcheckplus.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/* ------------------------------------------------------------------ */
/*  Loading skeletons                                                  */
/* ------------------------------------------------------------------ */

/**
 * One animated brush, reused by every skeleton shape on screen.
 *
 * The sweep runs on a single infinite transition that exists only while a
 * skeleton is composed, so a populated screen costs nothing. Skeleton shapes are
 * fixed-size boxes, so they never trigger layout thrash while the real content is
 * being measured off-thread.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val sweep by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1_400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_300, easing = LinearEasing)
        ),
        label = "skeletonSweep"
    )
    val base = scheme.onSurface.copy(alpha = 0.05f)
    val highlight = scheme.onSurface.copy(alpha = 0.13f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(sweep - 300f, 0f),
        end = Offset(sweep, 220f)
    )
}

/**
 * Placeholder row in the shape of a glass card with an icon well and two text
 * lines — used while a list's first load is still running, instead of a blank
 * screen that pops into content.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    showIconWell: Boolean = true
) {
    val brush = rememberShimmerBrush()
    GlassCard(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        frosted = false
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIconWell) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(brush)
                )
            }
        }
    }
}

/** A stack of [count] placeholder rows. */
@Composable
fun SkeletonList(
    count: Int = 6,
    modifier: Modifier = Modifier,
    showIconWell: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(count) {
            SkeletonRow(showIconWell = showIconWell)
        }
    }
}

/**
 * Chart-shaped placeholder for live graphs before the first samples arrive, so
 * the card keeps its final size and the content does not jump.
 */
@Composable
fun SkeletonChart(
    modifier: Modifier = Modifier,
    height: Int = 132
) {
    val brush = rememberShimmerBrush()
    GlassCard(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        frosted = false
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(11.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Empty states                                                       */
/* ------------------------------------------------------------------ */

/**
 * Friendly empty state: rounded-square icon well (the same shape every row uses),
 * a short title, one sentence of explanation and an optional action.
 */
@Composable
fun GlassEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Search targeting                                                   */
/* ------------------------------------------------------------------ */

/**
 * One-shot highlight for a row that a search just located.
 *
 * [trigger] is incremented each time the user picks a search (suggestion chip or
 * the keyboard's Search action — not on every keystroke), and only the row whose
 * [active] flag is set pulses. The animation is a single [Animatable] that runs
 * once and then stops reading, so a pulsing row costs nothing after ~1.4 s.
 */
@Composable
fun rememberMatchHighlight(
    active: Boolean,
    trigger: Int,
    peakAlpha: Float = 0.22f
): Float {
    val highlight = remember { Animatable(0f) }
    // Each row remembers the last request it animated, so continuing to type does
    // not restart the pulse on rows that keep matching — only a new commit does.
    val lastHandled = remember { mutableStateOf(-1) }

    LaunchedEffect(active, trigger) {
        if (!active || trigger <= 0 || trigger == lastHandled.value) return@LaunchedEffect
        lastHandled.value = trigger
        highlight.snapTo(peakAlpha)
        highlight.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 1_400, easing = LinearEasing)
        )
    }
    return highlight.value
}

/**
 * Scrolls [listState] to the located row when a new search selection arrives.
 *
 * Index-based on purpose: the caller passes the *absolute* item index inside its
 * own LazyColumn (headers included), which is cheaper and more predictable than
 * key lookup through the lazy layout info.
 */
@Composable
fun LocateMatchEffect(
    listState: LazyListState,
    token: Int,
    targetIndex: Int
) {
    LaunchedEffect(token) {
        if (token > 0 && targetIndex >= 0) {
            runCatching { listState.animateScrollToItem(targetIndex) }
        }
    }
}

/** Shared corner radius for row highlights, so pulses match the card shape. */
val MatchHighlightShape = RoundedCornerShape(14.dp)
