package com.github.vermilion10.milea.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.Fillup
import com.github.vermilion10.milea.data.repository.ExpenseRepository
import com.github.vermilion10.milea.data.repository.FillupRepository
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import com.github.vermilion10.milea.util.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ConsumptionPoint(val date: Long, val value: Float)
data class MonthlySpend(val label: String, val value: Float)

data class StatsUiState(
    val vehicleName: String? = null,
    val unit: DistanceUnit = DistanceUnit.KILOMETERS,
    val totalDistance: Float = 0f,
    val totalFuelCost: Float = 0f,
    val totalLiters: Float = 0f,
    val tripCount: Int = 0,
    val avgConsumption: Float? = null,
    val costPerKm: Float? = null,
    val totalExpense: Float = 0f,
    val consumptionSeries: List<ConsumptionPoint> = emptyList(),
    val monthlySpend: List<MonthlySpend> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val tripRepository: TripRepository,
    private val fillupRepository: FillupRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {
    val activeVehicle = vehicleRepository.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val uiState = activeVehicle
        .filterNotNull()
        .flatMapLatest { vehicle ->
            combine(
                combine(
                    tripRepository.getTotalDistanceForVehicle(vehicle.id),
                    tripRepository.getTripCountForVehicle(vehicle.id)
                ) { distance, count -> distance to count },
                combine(
                    fillupRepository.getTotalFuelCostForVehicle(vehicle.id),
                    fillupRepository.getTotalLitersForVehicle(vehicle.id)
                ) { cost, liters -> cost to liters },
                fillupRepository.getFillupsByVehicle(vehicle.id),
                expenseRepository.getTotalExpenseForVehicle(vehicle.id)
            ) { distanceAndCount, costAndLiters, fillups, expense ->
                val distance = distanceAndCount.first ?: 0f
                val count = distanceAndCount.second
                val cost = costAndLiters.first ?: 0f
                val liters = costAndLiters.second ?: 0f
                val consumptionSeries = computeConsumptionSeries(fillups)
                val monthlySpend = computeMonthlySpend(fillups)
                StatsUiState(
                    vehicleName = vehicle.name,
                    unit = vehicle.odometerUnit,
                    totalDistance = distance,
                    totalFuelCost = cost,
                    totalLiters = liters,
                    tripCount = count,
                    avgConsumption = consumptionSeries.lastOrNull()?.value,
                    costPerKm = if (distance > 0) cost / distance else null,
                    totalExpense = expense ?: 0f,
                    consumptionSeries = consumptionSeries,
                    monthlySpend = monthlySpend
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, StatsUiState())

    private fun computeConsumptionSeries(fillups: List<Fillup>): List<ConsumptionPoint> {
        val sorted = fillups.sortedBy { it.odometer }
        val result = mutableListOf<ConsumptionPoint>()
        var previousFullTank: Fillup? = null
        for (fillup in sorted) {
            if (fillup.isFullTank) {
                previousFullTank?.let { previous ->
                    val distance = fillup.odometer - previous.odometer
                    if (distance > 0) {
                        result.add(
                            ConsumptionPoint(
                                date = fillup.date,
                                value = (fillup.liters / distance) * 100f
                            )
                        )
                    }
                }
                previousFullTank = fillup
            }
        }
        return result
    }

    private fun computeMonthlySpend(fillups: List<Fillup>): List<MonthlySpend> {
        val fmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return fillups
            .groupBy { fmt.format(Date(it.date)) }
            .map { (label, list) ->
                MonthlySpend(label, list.sumOf { it.totalCost.toDouble() }.toFloat())
            }
            .sortedBy { it.label }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val activeVehicle by viewModel.activeVehicle.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (activeVehicle == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No active vehicle",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Add a vehicle to see statistics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatsSummaryCard(state)

            ConsumptionChartCard(state)

            MonthlySpendCard(state)

            ExpenseSummaryCard(state)
        }
    }
}

@Composable
fun StatsSummaryCard(state: StatsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                state.vehicleName ?: "Vehicle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Total Distance",
                    value = Units.formatDistance(state.totalDistance, state.unit),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Trips",
                    value = state.tripCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Fuel Cost",
                    value = "$${String.format("%.2f", state.totalFuelCost)}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Fuel Used",
                    value = Units.formatFuel(state.totalLiters, state.unit),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = if (state.unit == DistanceUnit.MILES) "Avg Consumption" else "Avg Consumption",
                    value = state.avgConsumption?.let {
                        Units.formatConsumption(it, state.unit)
                    } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Cost per ${Units.distanceLabel(state.unit)}",
                    value = state.costPerKm?.let {
                        val costPerUnit = if (state.unit == DistanceUnit.MILES) it * 1.609344f else it
                        "$${String.format("%.3f", costPerUnit)}"
                    } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ConsumptionChartCard(state: StatsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Consumption Trend (${Units.consumptionLabel(state.unit)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (state.consumptionSeries.isEmpty()) {
                EmptyChartMessage("Add at least two full-tank fill-ups to see the trend")
            } else {
                LineChart(
                    points = state.consumptionSeries.map {
                        Units.consumption(it.value, state.unit)
                    },
                    labels = state.consumptionSeries.map { point ->
                        SimpleDateFormat("MMM yy", Locale.getDefault()).format(Date(point.date))
                    }
                )
            }
        }
    }
}

@Composable
fun MonthlySpendCard(state: StatsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Monthly Fuel Spend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (state.monthlySpend.isEmpty()) {
                EmptyChartMessage("No fuel spend data yet")
            } else {
                BarChart(
                    values = state.monthlySpend.map { it.value },
                    labels = state.monthlySpend.map {
                        SimpleDateFormat("MMM yy", Locale.getDefault())
                            .format(Date(monthToMillis(it.label)))
                    }
                )
            }
        }
    }
}

@Composable
fun ExpenseSummaryCard(state: StatsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Non-Fuel Expenses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Total: $${String.format("%.2f", state.totalExpense)}",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (state.totalDistance > 0) {
                val combinedPerKm = (state.totalFuelCost + state.totalExpense) / state.totalDistance
                val combinedPerUnit = if (state.unit == DistanceUnit.MILES) combinedPerKm * 1.609344f else combinedPerKm
                Text(
                    "True cost per ${Units.distanceLabel(state.unit)} (fuel + expenses): $${String.format("%.3f", combinedPerUnit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyChartMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LineChart(
    points: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        EmptyChartMessage("Not enough data points")
        return
    }

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = points.maxOrNull() ?: 1f
    val minValue = points.minOrNull() ?: 0f
    val range = (maxValue - minValue).coerceAtLeast(0.1f)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val stepX = size.width / (points.size - 1)
            val topPad = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()

            for (i in 0..4) {
                val y = topPad + (bottom - topPad) * i / 4
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }

            val path = Path()
            points.forEachIndexed { index, value ->
                val x = index * stepX
                val y = bottom - ((value - minValue) / range) * (bottom - topPad)
                val point = Offset(x, y)
                if (index == 0) {
                    path.moveTo(point.x, point.y)
                } else {
                    path.lineTo(point.x, point.y)
                }
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))

            points.forEachIndexed { index, value ->
                val x = index * stepX
                val y = bottom - ((value - minValue) / range) * (bottom - topPad)
                drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                labels.firstOrNull() ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
            Text(
                labels.lastOrNull() ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
        }
    }
}

@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) {
        EmptyChartMessage("No data points")
        return
    }

    val barColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = values.maxOrNull() ?: 1f

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val slotWidth = size.width / values.size
            val barWidth = slotWidth * 0.6f
            val bottom = size.height - 8.dp.toPx()

            values.forEachIndexed { index, value ->
                val x = slotWidth * index + (slotWidth - barWidth) / 2
                val barHeight = (value / maxValue) * (size.height - 24.dp.toPx())
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, bottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                labels.firstOrNull() ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
            Text(
                labels.lastOrNull() ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
        }
    }
}

private fun monthToMillis(monthLabel: String): Long {
    return try {
        SimpleDateFormat("yyyy-MM", Locale.getDefault())
            .parse(monthLabel)?.time ?: System.currentTimeMillis()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}
