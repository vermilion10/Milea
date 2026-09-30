package com.github.vermilion10.milea.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One x-axis slot. [values] are stacked bottom to top in series order. */
data class ChartEntry(val label: String, val values: List<Float>) {
    val total: Float get() = values.sum()
}

data class ChartSeries(val name: String, val color: Color)

private data class AxisScale(val min: Float, val max: Float, val step: Float)

/** Rounds the axis to 1/2/5 x 10^n steps so gridlines land on readable values. */
private fun niceScale(minValue: Float, maxValue: Float, ticks: Int = 4): AxisScale {
    val lo = minOf(minValue, maxValue)
    val hi = maxOf(minValue, maxValue)
    val span = (hi - lo).takeIf { it > 0f } ?: (if (hi > 0f) hi else 1f)
    val rawStep = span / ticks
    val magnitude = 10f.pow(floor(log10(rawStep)))
    val residual = rawStep / magnitude
    val step = when {
        residual > 5f -> 10f
        residual > 2f -> 5f
        residual > 1f -> 2f
        else -> 1f
    } * magnitude
    val niceMin = floor(lo / step) * step
    val niceMax = ceil(hi / step) * step
    return AxisScale(niceMin, if (niceMax <= niceMin) niceMin + step else niceMax, step)
}

@Composable
private fun ChartReadout(
    title: String,
    lines: List<Pair<ChartSeries?, String>>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            lines.forEach { (series, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (series != null) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(series.color)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(text, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun DrawScope.drawYAxis(
    scale: AxisScale,
    top: Float,
    bottom: Float,
    gutter: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
    formatter: (Float) -> String
) {
    var value = scale.min
    while (value <= scale.max + scale.step / 2) {
        val y = bottom - (value - scale.min) / (scale.max - scale.min) * (bottom - top)
        drawLine(
            gridColor,
            Offset(gutter, y),
            Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (value == scale.min) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
        val layout = measurer.measure(formatter(value), style)
        drawText(
            layout,
            topLeft = Offset(
                gutter - layout.size.width - 6.dp.toPx(),
                (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
            )
        )
        value += scale.step
    }
}

private fun DrawScope.drawXLabels(
    labels: List<String>,
    slotStart: Float,
    slotWidth: Float,
    centered: Boolean,
    y: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    selected: Int
) {
    if (labels.isEmpty()) return
    val widest = labels.maxOf { measurer.measure(it, style).size.width }
    // Skip labels so they never overlap; the readout above names the selection.
    val every = ceil((widest + 8.dp.toPx()) / slotWidth).toInt().coerceAtLeast(1)
    labels.forEachIndexed { i, label ->
        val fromEnd = labels.lastIndex - i
        if (fromEnd % every != 0) return@forEachIndexed
        val layout = measurer.measure(label, style)
        val cx = slotStart + slotWidth * i + if (centered) slotWidth / 2 else 0f
        drawText(
            layout,
            topLeft = Offset(
                (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                y
            )
        )
    }
}

/**
 * Bar chart with optional stacking. Tap or drag across bars to read a value.
 */
@Composable
fun BarChart(
    entries: List<ChartEntry>,
    series: List<ChartSeries>,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    axisFormatter: (Float) -> String = valueFormatter,
    readoutTitle: (ChartEntry) -> String = { it.label },
    height: Dp = 200.dp
) {
    if (entries.isEmpty()) return
    var selected by remember(entries.size) {
        mutableIntStateOf(entries.indexOfLast { it.total > 0f }.takeIf { it >= 0 } ?: entries.lastIndex)
    }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(entries) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(600))
    }

    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val scale = niceScale(0f, entries.maxOf { it.total })

    val entry = entries[selected.coerceIn(0, entries.lastIndex)]
    val readout = if (series.size > 1) {
        listOf(null to valueFormatter(entry.total)) +
            series.mapIndexed { i, s -> s to valueFormatter(entry.values.getOrElse(i) { 0f }) }
    } else listOf(null to valueFormatter(entry.total))

    Column(modifier = modifier) {
        ChartReadout(readoutTitle(entry), readout)
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription = entries.joinToString { "${it.label}: ${valueFormatter(it.total)}" }
                }
                .pointerInput(entries) {
                    detectTapGestures { offset ->
                        selected = slotAt(offset.x, size.width.toFloat(), entries.size, gutterPx(measurer, axisStyle, scale, axisFormatter))
                    }
                }
                .pointerInput(entries) {
                    detectHorizontalDragGestures { change, _ ->
                        selected = slotAt(change.position.x, size.width.toFloat(), entries.size, gutterPx(measurer, axisStyle, scale, axisFormatter))
                    }
                }
        ) {
            val gutter = gutterPx(measurer, axisStyle, scale, axisFormatter)
            val labelHeight = measurer.measure("M", axisStyle).size.height
            val top = labelHeight / 2f
            val bottom = size.height - labelHeight - 8.dp.toPx()
            drawYAxis(scale, top, bottom, gutter, measurer, axisStyle, gridColor, axisFormatter)

            val slotWidth = (size.width - gutter) / entries.size
            val barWidth = (slotWidth * 0.62f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(minOf(barWidth / 2, 6.dp.toPx()))
            entries.forEachIndexed { i, e ->
                val left = gutter + slotWidth * i + (slotWidth - barWidth) / 2
                if (i == selected) {
                    drawRoundRect(
                        highlight,
                        topLeft = Offset(gutter + slotWidth * i + 2.dp.toPx(), top),
                        size = Size(slotWidth - 4.dp.toPx(), bottom - top),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                }
                var base = bottom
                val nonZero = e.values.indexOfLast { it > 0f }
                e.values.forEachIndexed { si, v ->
                    if (v <= 0f) return@forEachIndexed
                    val h = v / (scale.max - scale.min) * (bottom - top) * grow.value
                    val color = series.getOrNull(si)?.color ?: series.last().color
                    val rect = RoundRect(
                        left = left, top = base - h, right = left + barWidth, bottom = base,
                        topLeftCornerRadius = if (si == nonZero) radius else CornerRadius.Zero,
                        topRightCornerRadius = if (si == nonZero) radius else CornerRadius.Zero
                    )
                    drawPath(Path().apply { addRoundRect(rect) }, color)
                    base -= h
                }
            }
            drawXLabels(
                entries.map { it.label }, gutter, slotWidth, centered = true,
                y = bottom + 6.dp.toPx(), measurer = measurer,
                style = axisStyle, selected = selected
            )
        }
        if (series.size > 1) {
            Spacer(Modifier.height(8.dp))
            ChartLegend(series)
        }
    }
}

/** Line chart with a soft area fill. Tap or drag to read a point. */
@Composable
fun LineChart(
    labels: List<String>,
    values: List<Float>,
    color: Color,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    axisFormatter: (Float) -> String = valueFormatter,
    readoutTitle: (Int) -> String = { labels[it] },
    includeZero: Boolean = false,
    height: Dp = 200.dp,
    onSelect: ((Int) -> Unit)? = null
) {
    if (values.isEmpty()) return
    var selected by remember(values.size) { mutableIntStateOf(values.lastIndex) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(700))
    }

    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val scale = niceScale(if (includeZero) 0f else values.min(), values.max())

    Column(modifier = modifier) {
        ChartReadout(readoutTitle(selected.coerceIn(0, values.lastIndex)), listOf(null to valueFormatter(values[selected.coerceIn(0, values.lastIndex)])))
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription = labels.zip(values).joinToString { (l, v) -> "$l: ${valueFormatter(v)}" }
                }
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        selected = pointAt(offset.x, size.width.toFloat(), values.size, gutterPx(measurer, axisStyle, scale, axisFormatter)).also { onSelect?.invoke(it) }
                    }
                }
                .pointerInput(values) {
                    detectHorizontalDragGestures { change, _ ->
                        selected = pointAt(change.position.x, size.width.toFloat(), values.size, gutterPx(measurer, axisStyle, scale, axisFormatter)).also { onSelect?.invoke(it) }
                    }
                }
        ) {
            val gutter = gutterPx(measurer, axisStyle, scale, axisFormatter)
            val labelHeight = measurer.measure("M", axisStyle).size.height
            val top = labelHeight / 2f + 6.dp.toPx()
            val bottom = size.height - labelHeight - 8.dp.toPx()
            drawYAxis(scale, top, bottom, gutter, measurer, axisStyle, gridColor, axisFormatter)

            val inset = 8.dp.toPx()
            val plotWidth = size.width - gutter - inset * 2
            val stepX = if (values.size > 1) plotWidth / (values.size - 1) else 0f
            fun x(i: Int) = gutter + inset + if (values.size > 1) stepX * i else plotWidth / 2
            fun y(v: Float) = bottom - (v - scale.min) / (scale.max - scale.min) * (bottom - top)

            val line = Path()
            values.forEachIndexed { i, v ->
                if (i == 0) line.moveTo(x(i), y(v)) else {
                    // Gentle horizontal-tangent curve between points.
                    val px = x(i - 1)
                    val cx = (px + x(i)) / 2
                    line.cubicTo(cx, y(values[i - 1]), cx, y(v), x(i), y(v))
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(values.lastIndex), bottom)
                lineTo(x(0), bottom)
                close()
            }
            val clipRight = gutter + (size.width - gutter) * reveal.value
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, clipRight, size.height)
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)), top, bottom))
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawContext.canvas.restore()

            val sx = x(selected)
            val sy = y(values[selected])
            drawLine(color.copy(alpha = 0.4f), Offset(sx, top), Offset(sx, bottom), 1.dp.toPx())
            drawCircle(surface, 7.dp.toPx(), Offset(sx, sy))
            drawCircle(color, 5.dp.toPx(), Offset(sx, sy))

            drawXLabels(
                labels, gutter + inset - stepX / 2, stepX.coerceAtLeast(1f), centered = true,
                y = bottom + 6.dp.toPx(), measurer = measurer, style = axisStyle, selected = selected
            )
        }
    }
}

@Composable
fun ChartLegend(series: List<ChartSeries>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(s.color)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    s.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun gutterPx(
    measurer: TextMeasurer,
    style: TextStyle,
    scale: AxisScale,
    formatter: (Float) -> String
): Float {
    val widest = listOf(scale.min, scale.max, (scale.min + scale.max) / 2)
        .maxOf { measurer.measure(formatter(it), style).size.width }
    return widest + 12f * 2.5f
}

private fun slotAt(x: Float, width: Float, count: Int, gutter: Float): Int =
    (((x - gutter) / ((width - gutter) / count)).toInt()).coerceIn(0, count - 1)

private fun pointAt(x: Float, width: Float, count: Int, gutter: Float): Int {
    if (count <= 1) return 0
    val step = (width - gutter) / (count - 1)
    return Math.round((x - gutter) / step).coerceIn(0, count - 1)
}
