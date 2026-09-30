package com.github.vermilion10.milea.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.ExpenseCategory
import com.github.vermilion10.milea.data.model.TripCategory
import com.github.vermilion10.milea.domain.FuelEstimate
import com.github.vermilion10.milea.util.Units
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun NoActiveVehicleMessage(
    padding: PaddingValues,
    message: String,
    onAddVehicle: (() -> Unit)? = null
) {
    EmptyState(
        icon = Icons.Default.DirectionsCar,
        title = "No vehicle selected",
        message = message,
        modifier = Modifier.padding(padding),
        action = onAddVehicle?.let { { Button(onClick = it) { Text("Add vehicle") } } }
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(24.dp))
                action()
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        action?.invoke()
    }
}

/** A compact number-with-label tile on a tonal container. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor.copy(alpha = 0.72f)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Two tiles per row; an odd last tile spans the full width. */
@Composable
fun StatGrid(tiles: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> tile(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Estimated fuel left, as a segmented gauge with liters and range.
 * When there isn't enough data yet it explains what to log next.
 */
@Composable
fun FuelGaugeCard(
    estimate: FuelEstimate,
    unit: DistanceUnit,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Fuel left", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Estimate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            when (estimate) {
                is FuelEstimate.Available -> FuelGaugeContent(estimate, unit)
                is FuelEstimate.Unavailable -> {
                    Text(
                        when (estimate.reason) {
                            FuelEstimate.Reason.NO_TANK_CAPACITY ->
                                "Set your tank capacity on the vehicle to estimate fuel left and range."
                            FuelEstimate.Reason.NO_FULL_TANK ->
                                "Log a full-tank fill-up so Milea knows when the tank was full."
                            FuelEstimate.Reason.NOT_ENOUGH_FILLUPS ->
                                "Log one more full-tank fill-up to learn your consumption. The estimate starts after that."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onAction != null) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onAction) {
                            Text(
                                if (estimate.reason == FuelEstimate.Reason.NO_TANK_CAPACITY) "Edit vehicle"
                                else "Log fill-up"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelGaugeContent(estimate: FuelEstimate.Available, unit: DistanceUnit) {
    val fraction by animateFloatAsState(estimate.fraction, tween(800), label = "fuel")
    val low = estimate.fraction < 0.15f
    val barColor = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${Math.round(estimate.fraction * 100)}%",
            style = MaterialTheme.typography.displaySmall,
            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = 6.dp)) {
            Text(
                "${Units.formatFuel(estimate.litersLeft, unit)} of ${Units.formatFuel(estimate.tankCapacity, unit)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "about ${Units.formatWholeDistance(estimate.rangeKm, unit)} range",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    // Eight segments, like a car's fuel gauge.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        val segments = 8
        repeat(segments) { i ->
            val segFill = ((fraction * segments) - i).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { segFill },
                    modifier = Modifier.fillMaxSize(),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "${Units.formatDistance(estimate.distanceSinceFillKm, unit)} since last fill-up · " +
            Units.formatConsumption(estimate.litersPer100Km, unit) + " avg",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

fun formatDuration(millis: Long): String {
    val hours = millis / 3_600_000
    val minutes = (millis % 3_600_000) / 60_000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** h:mm:ss clock for live timers. */
fun formatClock(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format(
        Locale.getDefault(), "%d:%02d:%02d",
        totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60
    )
}

fun formatDate(time: Long): String =
    SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(time))

fun formatShortDate(time: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))

fun formatTime(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

fun formatMonth(time: Long): String =
    SimpleDateFormat("MMM", Locale.getDefault()).format(Date(time))

fun formatMonthYear(time: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(time))

/** "Today", "Yesterday", "3 days ago", else a date. */
fun formatRelativeDay(time: Long, now: Long = System.currentTimeMillis()): String {
    fun dayIndex(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let {
        it.get(Calendar.YEAR) * 400 + it.get(Calendar.DAY_OF_YEAR)
    }
    val days = dayIndex(now) - dayIndex(time)
    return when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days in 2..6 -> "$days days ago"
        TimeUnit.MILLISECONDS.toDays(now - time) < 300 -> formatShortDate(time)
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time))
    }
}

fun TripCategory.label(): String = name.lowercase().replaceFirstChar { it.titlecase() }

fun ExpenseCategory.label(): String = name.lowercase().replaceFirstChar { it.titlecase() }
