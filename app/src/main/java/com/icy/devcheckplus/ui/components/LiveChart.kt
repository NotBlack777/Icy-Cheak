package com.icy.devcheckplus.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.ui.theme.LocalGlassSpec

/** One line on a live chart. Marked [Immutable] so unchanged series skip recomposition. */
@Immutable
data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Float>,
    val strokeWidthDp: Float = 2.2f,
    val alpha: Float = 1f
)

/** Data-derived part of the chart's scaling — recomputed only when data changes. */
private class ChartDomain(
    val maxPoints: Int,
    val min: Float,
    val max: Float,
    val range: Float
)

/**
 * Canvas-drawn line/area chart with smooth animated updates — no charting
 * library and no per-frame recomposition.
 *
 * The slide animation is a single `animateFloatAsState` that chases the sample
 * counter; its value is read *inside* the draw scope, so a new sample only
 * invalidates drawing for this node (and only for the ~420 ms it takes to
 * settle). Between updates the chart is completely static: no infinite
 * transition, no frame callbacks, no recomposition, zero CPU.
 */
@Composable
fun LiveLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    yMin: Float = 0f,
    yMax: Float = 100f,
    version: Int = 0,
    areaSeriesIndex: Int = -1,
    gridRows: Int = 3,
    chartHeight: Dp = 128.dp,
    topLabel: String? = null,
    bottomLabel: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalGlassSpec.current

    val gridColor = scheme.onSurface.copy(alpha = if (spec.isOled) 0.10f else 0.07f)
    val dotCoreColor = scheme.surface
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(7f, 9f), 0f) }

    // Chases the sample counter: right after a new sample lands, the difference
    // is 1f and eases back down to 0f, which is exactly the slide offset.
    val animatedVersion by animateFloatAsState(
        targetValue = version.toFloat(),
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "chartSlide"
    )

    val hasData = series.any { it.points.size >= 2 }

    // Scanning every buffer for min/max is O(samples x series); doing it inside the
    // draw block would repeat it on every frame of the slide animation. The
    // derivedStateOf is recreated only when the data or the axis bounds change, so
    // the draw scope just reads an already-computed value.
    val domain = remember(series, yMin, yMax) {
        derivedStateOf {
            val maxPoints = series.maxOfOrNull { it.points.size } ?: 0
            val dataMax = if (yMax > yMin) 0f else (series.flatMap { it.points }.maxOrNull() ?: 0f)
            val lo = yMin
            val hi = if (yMax > yMin) yMax else (dataMax * 1.18f).coerceAtLeast(1f)
            ChartDomain(maxPoints, lo, hi, (hi - lo).coerceAtLeast(0.0001f))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val gridStroke = 1.dp.toPx()
            for (i in 0..gridRows) {
                val y = h * i / gridRows
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = gridStroke,
                    pathEffect = dashEffect
                )
            }

            val scaled = domain.value
            val maxPoints = scaled.maxPoints
            if (maxPoints < 2) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = dashEffect
                )
                return@Canvas
            }

            val domainMin = scaled.min
            val range = scaled.range
            val stepX = w / (maxPoints - 1)
            val slide = (version - animatedVersion).coerceIn(0f, 1f)
            val dotRadius = 3.dp.toPx()

            series.forEachIndexed { index, line ->
                val points = line.points
                val n = points.size
                if (n < 2) return@forEachIndexed
                val startOffset = maxPoints - n

                val offsets = ArrayList<Offset>(n)
                for (i in 0 until n) {
                    val x = ((startOffset + i) - slide) * stepX
                    val normalized = ((points[i] - domainMin) / range).coerceIn(0f, 1f)
                    offsets.add(Offset(x, h - normalized * h))
                }

                val linePath = Path().apply { smoothThrough(offsets) }

                if (index == areaSeriesIndex) {
                    val areaPath = Path().apply {
                        addPath(linePath)
                        lineTo(offsets[n - 1].x, h)
                        lineTo(offsets[0].x, h)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            listOf(
                                line.color.copy(alpha = 0.30f * line.alpha),
                                line.color.copy(alpha = 0.02f)
                            )
                        )
                    )
                }

                drawPath(
                    path = linePath,
                    color = line.color.copy(alpha = line.alpha),
                    style = Stroke(
                        width = line.strokeWidthDp.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                if (index == areaSeriesIndex || series.size == 1) {
                    val last = offsets[n - 1]
                    if (spec.glow) {
                        drawCircle(
                            color = line.color.copy(alpha = 0.28f * slide),
                            radius = dotRadius + 6.dp.toPx() * slide,
                            center = last
                        )
                    }
                    drawCircle(color = line.color, radius = dotRadius, center = last)
                    drawCircle(color = dotCoreColor, radius = dotRadius * 0.45f, center = last)
                }
            }
        }

        if (!hasData) {
            Text(
                text = "Sampling…",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (topLabel != null) {
            Text(
                text = topLabel,
                fontSize = 9.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
            )
        }
        if (bottomLabel != null) {
            Text(
                text = bottomLabel,
                fontSize = 9.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 2.dp, end = 2.dp)
            )
        }
    }
}

private fun Path.smoothThrough(points: List<Offset>) {
    if (points.isEmpty()) return
    moveTo(points[0].x, points[0].y)
    if (points.size == 1) return
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val midX = (prev.x + cur.x) / 2f
        cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
    }
}

/**
 * Chart wrapped in a glass card with a big live readout. Used by the Hardware
 * (CPU / RAM) and Battery (temperature / drain) tabs.
 */
@Composable
fun LiveChartCard(
    title: String,
    value: String,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    version: Int = 0,
    icon: ImageVector? = null,
    subtitle: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.primary,
    yMin: Float = 0f,
    yMax: Float = 100f,
    areaSeriesIndex: Int = 0,
    topLabel: String? = null,
    bottomLabel: String? = null,
    chartHeight: Dp = 128.dp,
    legend: List<Pair<String, Color>> = emptyList()
) {
    val scheme = MaterialTheme.colorScheme

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        frosted = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(valueColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = valueColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LiveLineChart(
            series = series,
            version = version,
            yMin = yMin,
            yMax = yMax,
            areaSeriesIndex = areaSeriesIndex,
            chartHeight = chartHeight,
            topLabel = topLabel,
            bottomLabel = bottomLabel
        )

        if (legend.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                legend.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(entry.second)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = entry.first,
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
