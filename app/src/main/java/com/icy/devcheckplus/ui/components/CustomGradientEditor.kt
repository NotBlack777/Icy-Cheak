package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.hsv
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toHsv
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.icy.devcheckplus.data.CustomGradient
import com.icy.devcheckplus.ui.theme.contentColorOn
import com.icy.devcheckplus.ui.theme.gradientBrush
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gradient editor: the "Custom" tile in Settings › Colors & Theming.
 *
 * Everything the brief asked for is on one sheet, top to bottom:
 *
 *  1. **live preview** — a card-shaped swatch and three small surfaces painted
 *     with the draft gradient, so the user sees what a *row*, a *tile* and a
 *     *card* will look like, not just a rectangle of colour;
 *  2. **2–4 colour stops** — tap a stop to edit it, `+` to add one (up to four),
 *     the bin to remove one (down to two);
 *  3. **an HSV wheel** — hue on the ring, saturation/value on the square inside
 *     it, both draggable, plus a hex field for an exact value;
 *  4. **direction** — a draggable dial for any angle, preset chips for the usual
 *     eight, and a radial switch;
 *  5. **a name and Save** — the preset goes to DataStore via
 *     [com.icy.devcheckplus.data.UserPreferencesStore.saveCustomGradient] and
 *     becomes the active one, so the whole app repaints on the next frame.
 *
 * Nothing here writes to the store directly: the sheet reports a finished
 * [CustomGradient] through [onSave], which keeps it usable for "edit this preset"
 * and "start from scratch" alike.
 *
 * @param initial the preset being edited, or `null` to start from
 *                [CustomGradient.STARTER].
 */
@Composable
fun CustomGradientSheet(
    initial: CustomGradient?,
    onSave: (CustomGradient) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val start = initial ?: CustomGradient.STARTER

    // The stop list is editor-local state: only a saved gradient is persisted, so
    // dragging the wheel around never touches DataStore.
    val colors = remember(start.id) { start.colors.toMutableStateList() }
    var selectedIndex by remember(start.id) { mutableStateOf(0) }
    var name by rememberSaveable(start.id) { mutableStateOf(if (initial == null) "" else start.name) }
    var angle by rememberSaveable(start.id) { mutableStateOf(start.angleDegrees) }
    var radial by rememberSaveable(start.id) { mutableStateOf(start.radial) }

    val index = selectedIndex.coerceIn(0, (colors.size - 1).coerceAtLeast(0))
    val currentArgb = colors.getOrElse(index) { CustomGradient.STARTER.colors[0] }
    val hsvValues = remember(currentArgb) { Color(currentArgb).toHsv() }

    // The draft is what the previews paint; rebuilt only when an input changes
    // (the name is a key too — the preview card shows it live while typing).
    val draft = remember(colors.toList(), angle, radial, name) {
        CustomGradient(
            id = "draft",
            name = name.ifBlank { "Preview" },
            colors = colors.toList(),
            angleDegrees = angle,
            radial = radial
        )
    }

    PickerSheet(
        title = if (initial == null) "New custom gradient" else "Edit ${start.name}",
        subtitle = "Pick two to four colours, an angle or radial spread, then save it. Saved " +
            "gradients live under the built-in ones as \"My gradients\" and can be switched " +
            "between at any time.",
        onDismiss = onDismiss
    ) {
        GradientPreviewCard(draft)

        Spacer(modifier = Modifier.height(18.dp))
        EditorLabel("Colour stops  •  ${colors.size} of ${CustomGradient.MAX_STOPS}")
        Spacer(modifier = Modifier.height(8.dp))
        StopRow(
            colors = colors,
            selectedIndex = index,
            onSelect = { selectedIndex = it },
            onAdd = {
                // New stop starts as a hue-shifted copy of the last one, so it is
                // visibly different instead of an identical duplicate.
                val last = Color(colors.last())
                val lastHsv = last.toHsv()
                colors.add(hsvToArgb((lastHsv[0] + 60f) % 360f, lastHsv[1], lastHsv[2]))
                selectedIndex = colors.size - 1
            },
            onRemove = {
                if (colors.size > CustomGradient.MIN_STOPS) {
                    colors.removeAt(index)
                    selectedIndex = 0
                }
            }
        )

        Spacer(modifier = Modifier.height(18.dp))
        EditorLabel("Colour")
        Spacer(modifier = Modifier.height(10.dp))
        HsvWheel(
            hue = hsvValues[0],
            saturation = hsvValues[1],
            value = hsvValues[2],
            onChange = { h, s, v -> colors[index] = hsvToArgb(h, s, v) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(10.dp))
        HexField(
            argb = currentArgb,
            onCommit = { colors[index] = it }
        )

        Spacer(modifier = Modifier.height(18.dp))
        EditorLabel("Direction")
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AngleDial(
                angle = angle,
                radial = radial,
                onAngleChange = { angle = it },
                modifier = Modifier.size(88.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (radial) "Radial — from the centre out" else "$angle° — 0° is left to right, 90° top to bottom",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Radial",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    HapticSwitch(checked = radial, onCheckedChange = { radial = it })
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        AnglePresetRow(
            angle = angle,
            radial = radial,
            onAngleChange = { angle = it }
        )

        Spacer(modifier = Modifier.height(18.dp))
        EditorLabel("Name")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(28) },
            singleLine = true,
            placeholder = { Text("Sunset glass", style = MaterialTheme.typography.bodyMedium) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                val saved = CustomGradient(
                    id = initial?.id ?: CustomGradient.newId(),
                    name = CustomGradient.sanitizeName(name).ifEmpty { "Gradient" },
                    colors = colors.toList(),
                    angleDegrees = angle.coerceIn(0, 359),
                    radial = radial
                )
                onSave(saved)
                onDismiss()
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = if (initial == null) "Save & use gradient" else "Save changes",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        PickerNote(
            text = "The gradient tints glass surfaces, so the blur behind them survives — it is a " +
                "tint, not an opaque fill. The ambient background uses the same colours at lower " +
                "strength. OLED mode paints flat surfaces for contrast and battery, so Custom shows " +
                "in System, Light and Dark modes."
        )
    }
}

/** Small caps-style label above each editor block. */
@Composable
private fun ColumnScope.EditorLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Live preview, updated on every edit: one card-sized surface and three smaller
 * ones, all painted with the draft gradient through the same
 * [gradientBrush] the app uses, so what is on screen here is what a card, a tile
 * and a chip will actually look like.
 */
@Composable
private fun ColumnScope.GradientPreviewCard(draft: CustomGradient) {
    val brush = remember(draft) { draft.gradientBrush(draft.colors.map { Color(it) }) }
    val onGradient = Color(draft.colors.first()).contentColorOn()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = draft.name.ifBlank { "Preview" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onGradient
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = draft.directionLabel + "  •  " + draft.colors.size + " colours",
                style = MaterialTheme.typography.labelSmall,
                color = onGradient.copy(alpha = 0.82f)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // A tall tile, a wide row and a small chip: the same brush at three aspect
        // ratios, which is where a naive angle implementation visibly stretches.
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(brush)
        )
    }
}

/** The 2–4 colour stops, with add/remove. */
@Composable
private fun StopRow(
    colors: List<Long>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { index, argb ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(index) }
            )
            if (index != colors.lastIndex) {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (colors.size < CustomGradient.MAX_STOPS) {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add a colour stop",
                    tint = scheme.primary
                )
            }
        }
        if (colors.size > CustomGradient.MIN_STOPS) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove the selected colour stop",
                    tint = scheme.error
                )
            }
        }
    }
}

/**
 * Hue ring + saturation/value square, both draggable.
 *
 * One control instead of three sliders: the ring is 72 arcs of 5° (drawn only when
 * the wheel repaints, i.e. when a colour changes — never per frame), and the square
 * inside it is the classic white → hue horizontal gradient with a transparent →
 * black vertical one on top. Touch inside the square edits saturation and value;
 * touch anywhere else in the circle edits hue.
 */
@Composable
private fun HsvWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (hue: Float, saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // The gesture detector is created once (keying it on the colours would cancel a
    // drag in progress), so it reads the *latest* values through these states rather
    // than capturing the ones from its first composition.
    val currentHue by rememberUpdatedState(hue)
    val currentSaturation by rememberUpdatedState(saturation)
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)
    Canvas(
        modifier = modifier
            .size(208.dp)
            .pointerInput(Unit) {
                fun update(position: Offset) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = position.x - cx
                    val dy = position.y - cy
                    val distance = sqrt(dx * dx + dy * dy)
                    val outer = size.width / 2f
                    val ringWidth = outer * 0.22f
                    // Half-side of the square inscribed in the ring's inner circle.
                    val half = (outer - ringWidth) * 0.70f
                    when {
                        abs(dx) <= half && abs(dy) <= half -> currentOnChange(
                            currentHue,
                            ((dx + half) / (2f * half)).coerceIn(0f, 1f),
                            (1f - (dy + half) / (2f * half)).coerceIn(0f, 1f)
                        )

                        distance <= outer -> currentOnChange(
                            ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f),
                            currentSaturation,
                            currentValue
                        )
                    }
                }
                detectTapGestures { update(it) }
                detectDragGestures { change, _ ->
                    update(change.position)
                    change.consume()
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.width / 2f
        val ringWidth = outer * 0.22f
        val ringStrokeCenter = outer - ringWidth / 2f
        val inset = ringWidth / 2f

        // Hue ring: 72 arcs, each sampled at its own midpoint so the seams match.
        val segments = 72
        val sweep = 360f / segments
        for (segment in 0 until segments) {
            drawArc(
                color = hsv(segment * sweep + sweep / 2f, 1f, 1f),
                startAngle = segment * sweep - 0.6f,
                sweepAngle = sweep + 1.2f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - ringWidth, size.height - ringWidth),
                style = Stroke(width = ringWidth)
            )
        }

        // Saturation/value square inscribed in the ring.
        val half = (outer - ringWidth) * 0.70f
        val squareSize = Size(half * 2f, half * 2f)
        val topLeft = Offset(cx - half, cy - half)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.White, hsv(hue, 1f, 1f))),
            topLeft = topLeft,
            size = squareSize
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            topLeft = topLeft,
            size = squareSize
        )
        drawRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = topLeft,
            size = squareSize,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Hue marker on the ring.
        val hueRadians = Math.toRadians(hue.toDouble())
        val hueMarker = Offset(
            cx + cos(hueRadians).toFloat() * ringStrokeCenter,
            cy + sin(hueRadians).toFloat() * ringStrokeCenter
        )
        drawCircle(
            color = Color.White,
            radius = ringWidth * 0.42f,
            center = hueMarker,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Saturation/value marker inside the square.
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = Offset(topLeft.x + saturation * squareSize.width, topLeft.y + (1f - value) * squareSize.height),
            style = Stroke(width = 2.5.dp.toPx())
        )
        drawCircle(
            color = scheme.onSurface.copy(alpha = 0.35f),
            radius = 10.dp.toPx(),
            center = Offset(topLeft.x + saturation * squareSize.width, topLeft.y + (1f - value) * squareSize.height),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

/** `#RRGGBB` / `#AARRGGBB` entry for an exact colour; ignores anything unparsable. */
@Composable
private fun HexField(argb: Long, onCommit: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val hex = remember(argb) { "#%06X".format(argb and 0xFFFFFFL) }
    var text by remember(argb) { mutableStateOf(hex) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw -> text = raw.take(9) },
            singleLine = true,
            label = { Text("Hex") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            onClick = {
                parseHex(text)?.let { parsed ->
                    onCommit(parsed)
                    text = "#%06X".format(parsed and 0xFFFFFFL)
                }
            },
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Apply", color = scheme.onPrimary)
        }
    }
}

/** Accepts `RGB`, `#RGB`, `RRGGBB`, `#RRGGBB` and `AARRGGBB`; `null` if unusable. */
private fun parseHex(raw: String): Long? {
    val digits = raw.trim().removePrefix("#")
    if (digits.isEmpty() || digits.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return when (digits.length) {
        3 -> {
            val expanded = digits.map { "$it$it" }.joinToString("")
            ("FF$expanded").toLongOrNull(16)
        }

        6 -> ("FF$digits").toLongOrNull(16)
        8 -> digits.toLongOrNull(16)
        else -> null
    }?.let { it and 0xFFFFFFFFL }
}

/** Draggable direction dial: any angle, or concentric rings while radial is on. */
@Composable
private fun AngleDial(
    angle: Int,
    radial: Boolean,
    onAngleChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val handleColor = scheme.primary
    val trackColor = scheme.onSurface.copy(alpha = 0.22f)

    Canvas(
        modifier = modifier
            .pointerInput(radial) {
                // Radial ignores the angle, so the dial stops listening.
                if (radial) return@pointerInput
                fun update(position: Offset) {
                    val dx = position.x - size.width / 2f
                    val dy = position.y - size.height / 2f
                    if (dx == 0f && dy == 0f) return
                    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    onAngleChange(((degrees.roundToInt() % 360) + 360) % 360)
                }
                detectTapGestures { update(it) }
                detectDragGestures { change, _ ->
                    update(change.position)
                    change.consume()
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 8.dp.toPx()
        drawCircle(color = trackColor, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))

        if (radial) {
            // Concentric rings: the shape a radial gradient actually has.
            listOf(0.34f, 0.66f).forEach { factor ->
                drawCircle(
                    color = handleColor.copy(alpha = 0.55f),
                    radius = radius * factor,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            drawCircle(color = handleColor, radius = 5.dp.toPx(), center = center)
            return@Canvas
        }

        val radians = Math.toRadians(angle.toDouble())
        val handle = Offset(
            center.x + cos(radians).toFloat() * radius,
            center.y + sin(radians).toFloat() * radius
        )
        drawLine(
            color = handleColor,
            start = center,
            end = handle,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = handleColor, radius = 7.dp.toPx(), center = handle)
        drawCircle(color = trackColor, radius = 3.dp.toPx(), center = center)
    }
}

/** The eight angles worth a one-tap preset; the dial covers everything between. */
@Composable
private fun AnglePresetRow(angle: Int, radial: Boolean, onAngleChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0, 45, 90, 135, 180, 225, 270, 315).forEach { preset ->
            val selected = !radial && preset == angle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            scheme.primary.copy(alpha = 0.22f)
                        } else {
                            scheme.surfaceVariant.copy(alpha = 0.45f)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = !radial) { onAngleChange(preset) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$preset°",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (radial) scheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else if (selected) scheme.primary else scheme.onSurface
                )
            }
        }
    }
}

/** ARGB `Long` → HSV → back, in one place so the editor and the store agree. */
private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Long =
    hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
        .toArgb()
        .toLong() and 0xFFFFFFFFL
