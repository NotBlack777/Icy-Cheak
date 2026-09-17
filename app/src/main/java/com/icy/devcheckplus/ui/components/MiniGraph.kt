package com.icy.devcheckplus.ui.components

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

@Composable
fun MiniGraph(
    history: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (history.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val min = history.minOrNull() ?: 0f
        val max = history.maxOrNull() ?: 1f
        val range = if (max - min == 0f) 1f else max - min

        val stepX = size.width / (history.size - 1).coerceAtLeast(1)
        val path = Path()

        history.forEachIndexed { index, value ->
            val normalizedY = (value - min) / range
            val x = index * stepX
            val y = size.height - (normalizedY * size.height)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
