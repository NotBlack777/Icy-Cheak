package com.icy.icycheak.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.icy.icycheak.ui.theme.LocalTheme

/**
 * Lightweight line/area chart. Pure Compose Canvas — no external chart library.
 * Used by the battery-history drain graph and the live metrics sparkline.
 */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier.fillMaxWidth().height(120.dp),
    color: Color = LocalTheme.current.accent,
    fill: Boolean = true
) {
    val lineColor = color
    val fillColor = color.copy(alpha = 0.18f)
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val max = points.maxOrNull() ?: 1f
        val min = points.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(0.0001f)
        val stepX = w / (points.size - 1)
        val toOffset: (Int) -> Offset = { i ->
            Offset(i * stepX, h - ((points[i] - min) / range) * h)
        }
        val path = Path().apply {
            moveTo(0f, h)
            points.forEachIndexed { i, _ -> lineTo(toOffset(i).x, toOffset(i).y) }
            lineTo(w, h)
            close()
        }
        if (fill) drawPath(path, fillColor)
        val line = Path().apply {
            moveTo(toOffset(0).x, toOffset(0).y)
            for (i in 1 until points.size) lineTo(toOffset(i).x, toOffset(i).y)
        }
        drawPath(line, lineColor, style = Stroke(width = 2.5f))
    }
}
