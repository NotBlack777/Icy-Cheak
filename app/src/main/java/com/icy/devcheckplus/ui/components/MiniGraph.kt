package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compact sparkline used by the Sensors tab.
 *
 * Pure Canvas drawing with no animation clock of its own: it only redraws when
 * [history] changes, which the (throttled) sensor publisher limits to 2 Hz.
 */
@Composable
fun MiniGraph(
    history: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 48.dp,
    areaFill: Boolean = true
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Normalised points are recomputed only when the data changes.
    val points = remember(history) {
        if (history.size < 2) {
            emptyList()
        } else {
            val min = history.min()
            val max = history.max()
            val range = if (max - min <= 0.0001f) 1f else max - min
            history.mapIndexed { index, value ->
                Offset(
                    x = index.toFloat() / (history.size - 1),
                    y = 1f - ((value - min) / range).coerceIn(0f, 1f)
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (points.isEmpty()) {
            drawLine(
                color = lineColor.copy(alpha = 0.25f),
                start = Offset(0f, size.height * 0.5f),
                end = Offset(size.width, size.height * 0.5f),
                strokeWidth = 1.5.dp.toPx()
            )
            return@Canvas
        }

        val w = size.width
        val h = size.height
        val scaled = points.map { Offset(it.x * w, it.y * h) }

        val line = Path().apply {
            moveTo(scaled[0].x, scaled[0].y)
            for (i in 1 until scaled.size) {
                val prev = scaled[i - 1]
                val cur = scaled[i]
                val midX = (prev.x + cur.x) / 2f
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }

        if (areaFill) {
            val area = Path().apply {
                addPath(line)
                lineTo(scaled.last().x, h)
                lineTo(scaled.first().x, h)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))
                )
            )
        }

        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val last = scaled.last()
        drawCircle(color = lineColor, radius = 2.6.dp.toPx(), center = last)
        drawCircle(color = surfaceColor, radius = 1.1.dp.toPx(), center = last)
    }
}
