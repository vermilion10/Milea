package com.github.vermilion10.milea.ui.screens.tripdetail

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.TripPoint
import com.github.vermilion10.milea.ui.components.formatDuration
import com.github.vermilion10.milea.util.Units
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/**
 * Speed bands used for coloring the route, the legend and the zones bar.
 * Colors are fixed (green = slow, red = fast) so they read the same on the
 * light map tiles in any app theme.
 */
data class SpeedBand(val label: String, val maxKmh: Float, val color: Color)

fun speedBands(unit: DistanceUnit): List<SpeedBand> {
    val colors = listOf(
        Color(0xFF2E7D32), Color(0xFF7CB342), Color(0xFFFDD835),
        Color(0xFFFB8C00), Color(0xFFE64A19), Color(0xFFC62828)
    )
    // Thresholds in the vehicle's own unit, so the legend shows round numbers.
    val limits = if (unit == DistanceUnit.MILES) listOf(10f, 25f, 40f, 50f, 60f) else listOf(20f, 40f, 60f, 80f, 100f)
    val toKmh = if (unit == DistanceUnit.MILES) 1.609344f else 1f
    val label = Units.speedLabel(unit)
    return colors.mapIndexed { i, color ->
        val lower = limits.getOrNull(i - 1)
        val upper = limits.getOrNull(i)
        SpeedBand(
            label = when {
                lower == null -> "<${upper!!.toInt()}"
                upper == null -> "${lower.toInt()}+"
                else -> "${lower.toInt()}–${upper.toInt()}"
            } + if (i == colors.lastIndex) " $label" else "",
            maxKmh = upper?.times(toKmh) ?: Float.MAX_VALUE,
            color = color
        )
    }
}

fun bandIndex(kmh: Float, bands: List<SpeedBand>): Int = bands.indexOfFirst { kmh < it.maxKmh }.coerceAtLeast(0)

/** Speed of the segment ending at [i], in km/h; falls back to the speed implied by position change. */
fun segmentSpeedKmh(points: List<TripPoint>, i: Int): Float {
    val p = points[i]
    p.speed?.let { return it * 3.6f }
    val prev = points.getOrNull(i - 1) ?: return 0f
    val seconds = (p.timestamp - prev.timestamp) / 1000f
    if (seconds <= 0f) return 0f
    val results = FloatArray(1)
    android.location.Location.distanceBetween(prev.latitude, prev.longitude, p.latitude, p.longitude, results)
    return results[0] / seconds * 3.6f
}

/** One polyline per run of consecutive segments in the same band, so long trips stay light to draw. */
fun speedPolylines(points: List<TripPoint>, bands: List<SpeedBand>, width: Float): List<Polyline> {
    if (points.size < 2) return emptyList()
    val lines = mutableListOf<Polyline>()
    var runBand = bandIndex(segmentSpeedKmh(points, 1), bands)
    var run = mutableListOf(GeoPoint(points[0].latitude, points[0].longitude))
    for (i in 1 until points.size) {
        val band = bandIndex(segmentSpeedKmh(points, i), bands)
        val geo = GeoPoint(points[i].latitude, points[i].longitude)
        if (band != runBand) {
            lines += polyline(run, bands[runBand].color.toArgb(), width)
            run = mutableListOf(run.last())
            runBand = band
        }
        run += geo
    }
    lines += polyline(run, bands[runBand].color.toArgb(), width)
    return lines
}

fun polyline(points: List<GeoPoint>, color: Int, width: Float) = Polyline().apply {
    setPoints(points)
    outlinePaint.apply {
        this.color = color
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
}

/** A plain dot on the map (start, finish, chart selection) without marker icons. */
class DotOverlay(
    private val position: GeoPoint,
    private val fill: Int,
    private val ring: Int,
    private val radius: Float
) : Overlay() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ring; style = Paint.Style.STROKE; strokeWidth = radius * 0.45f
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val p = mapView.projection.toPixels(position, null)
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), radius, fillPaint)
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), radius, ringPaint)
    }
}

@Composable
fun SpeedLegend(bands: List<SpeedBand>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        bands.forEach { band ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1.dp)
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(band.color)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    band.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Time spent in each speed band, as one stacked bar plus a per-band list. */
@Composable
fun SpeedZones(points: List<TripPoint>, bands: List<SpeedBand>, unit: DistanceUnit) {
    val time = LongArray(bands.size)
    for (i in 1 until points.size) {
        val dt = (points[i].timestamp - points[i - 1].timestamp).coerceIn(0, 60_000)
        time[bandIndex(segmentSpeedKmh(points, i), bands)] += dt
    }
    val total = time.sum().takeIf { it > 0 } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(MaterialTheme.shapes.small)
        ) {
            bands.forEachIndexed { i, band ->
                if (time[i] > 0) {
                    Box(
                        Modifier
                            .weight(time[i].toFloat())
                            .fillMaxHeight()
                            .background(band.color)
                    )
                }
            }
        }
        bands.forEachIndexed { i, band ->
            if (time[i] == 0L) return@forEachIndexed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(band.color)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (i == bands.lastIndex) band.label else "${band.label} ${Units.speedLabel(unit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${formatDuration(time[i])} · ${Math.round(time[i] * 100f / total)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Chart samples bucketed to ~60 points, remembering which GPS point each came from. */
data class TrackSample(val time: Long, val value: Float, val pointIndex: Int)

fun bucketed(points: List<TripPoint>, value: (Int) -> Float?): List<TrackSample> {
    val indexed = points.indices.mapNotNull { i -> value(i)?.let { i to it } }
    if (indexed.size < 3) return emptyList()
    val size = (indexed.size / 60).coerceAtLeast(1)
    return indexed.chunked(size).map { chunk ->
        val mid = chunk[chunk.size / 2].first
        TrackSample(points[mid].timestamp, chunk.map { it.second }.average().toFloat(), mid)
    }
}

/** Climb over the smoothed altitude, so GPS jitter doesn't add phantom meters. */
fun elevationGain(samples: List<TrackSample>): Float =
    samples.zipWithNext { a, b -> (b.value - a.value).coerceAtLeast(0f) }.sum()
