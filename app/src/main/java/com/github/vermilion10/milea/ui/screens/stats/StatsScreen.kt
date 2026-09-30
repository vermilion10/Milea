package com.github.vermilion10.milea.ui.screens.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.domain.ConsumptionInterval
import com.github.vermilion10.milea.domain.MonthBucket
import com.github.vermilion10.milea.domain.PeriodStats
import com.github.vermilion10.milea.domain.StatsPeriod
import com.github.vermilion10.milea.domain.VehicleAnalytics
import com.github.vermilion10.milea.domain.VehicleDataSource
import com.github.vermilion10.milea.ui.components.*
import com.github.vermilion10.milea.util.LocalMoney
import com.github.vermilion10.milea.util.LocalConsumptionUnit
import com.github.vermilion10.milea.util.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.util.Locale
import javax.inject.Inject

data class StatsUiState(
    val hasVehicle: Boolean = false,
    val loaded: Boolean = false,
    val unit: DistanceUnit = DistanceUnit.KILOMETERS,
    val period: StatsPeriod = StatsPeriod.YEAR,
    val stats: PeriodStats = PeriodStats(),
    val months: List<MonthBucket> = emptyList(),
    val intervals: List<ConsumptionInterval> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    vehicleDataSource: VehicleDataSource
) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.YEAR)

    val uiState = combine(vehicleDataSource.selectedVehicleData(), period) { data, p ->
        if (data == null) return@combine StatsUiState(loaded = true, period = p)
        StatsUiState(
            hasVehicle = true,
            loaded = true,
            unit = data.vehicle.odometerUnit,
            period = p,
            stats = VehicleAnalytics.periodStats(data.vehicle, data.fillups, data.trips, data.expenses, p),
            months = VehicleAnalytics.monthlyBuckets(data.vehicle, data.fillups, data.trips, data.expenses, months = 12),
            intervals = VehicleAnalytics.consumptionIntervals(data.fillups).takeLast(16)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(p: StatsPeriod) {
        period.value = p
    }
}

private enum class Trend(val label: String) {
    DISTANCE("Distance"), ODOMETER("Odometer"), COST("Costs"), CONSUMPTION("Consumption")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onAddVehicle: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("Statistics") }, scrollBehavior = scrollBehavior) }
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (!state.hasVehicle) {
            NoActiveVehicleMessage(padding, "Add a vehicle to see statistics.", onAddVehicle)
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TrendsCard(state)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    StatsPeriod.MONTH to "30 days",
                    StatsPeriod.QUARTER to "3 months",
                    StatsPeriod.YEAR to "Year",
                    StatsPeriod.ALL to "All"
                )
                options.forEachIndexed { i, (p, label) ->
                    SegmentedButton(
                        selected = state.period == p,
                        onClick = { viewModel.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        icon = {}
                    ) { Text(label, maxLines = 1) }
                }
            }

            FillupSection(state)
            CostSection(state)
            DistanceSection(state)
        }
    }
}

@Composable
private fun TrendsCard(state: StatsUiState) {
    val money = LocalMoney.current
    val unit = state.unit
    var trend by rememberSaveable { mutableStateOf(Trend.DISTANCE) }
    val monthLabels = state.months.map { formatMonth(it.monthStart) }
    val consumptionUnit = LocalConsumptionUnit.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Trend.entries.forEach { t ->
                    FilterChip(
                        selected = trend == t,
                        onClick = { trend = t },
                        label = { Text(t.label) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                val primary = MaterialTheme.colorScheme.primary
                val tertiary = MaterialTheme.colorScheme.tertiary
                val hasMonthData = state.months.any { it.distanceKm > 0f || it.totalCost > 0f || it.odometerKm != null }
                when (trend) {
                    Trend.DISTANCE -> if (!hasMonthData) ChartEmpty("Log trips or fill-ups to see distance per month.") else BarChart(
                        entries = state.months.map { ChartEntry(formatMonth(it.monthStart), listOf(Units.distance(it.distanceKm, unit))) },
                        series = listOf(ChartSeries("Distance", primary)),
                        valueFormatter = { String.format(Locale.getDefault(), "%,.0f %s", it, Units.distanceLabel(unit)) },
                        axisFormatter = { compactNumber(it) },
                        readoutTitle = { e -> "${fullMonth(state, e)} · distance" }
                    )
                    Trend.ODOMETER -> {
                        val points = state.months.filter { it.odometerKm != null }
                        if (points.size < 2) ChartEmpty("Odometer readings from at least two months are needed.")
                        else LineChart(
                            labels = points.map { formatMonth(it.monthStart) },
                            values = points.map { Units.distance(it.odometerKm!!.toFloat(), unit) },
                            color = primary,
                            valueFormatter = { String.format(Locale.getDefault(), "%,.0f %s", it, Units.distanceLabel(unit)) },
                            axisFormatter = { compactNumber(it) },
                            readoutTitle = { i -> "${formatMonthYear(points[i].monthStart)} · odometer" }
                        )
                    }
                    Trend.COST -> if (state.months.all { it.totalCost == 0f }) ChartEmpty("Log fill-ups or expenses to see monthly costs.") else BarChart(
                        entries = state.months.map { ChartEntry(formatMonth(it.monthStart), listOf(it.fuelCost, it.otherCost)) },
                        series = listOf(ChartSeries("Fuel", primary), ChartSeries("Other", tertiary)),
                        valueFormatter = { money.format(it) },
                        axisFormatter = { money.formatCompact(it) },
                        readoutTitle = { e -> "${fullMonth(state, e)} · total" }
                    )
                    Trend.CONSUMPTION -> if (state.intervals.size < 2) ChartEmpty(
                        "Consumption is measured between full-tank fill-ups. Log a few more to see the trend."
                    ) else LineChart(
                        labels = state.intervals.map { formatShortDate(it.endDate) },
                        values = state.intervals.map { Units.consumption(it.litersPer100Km, unit, consumptionUnit) },
                        color = tertiary,
                        valueFormatter = { String.format(Locale.getDefault(), "%.1f %s", it, Units.consumptionLabel(unit, consumptionUnit)) },
                        axisFormatter = { String.format(Locale.getDefault(), "%.0f", it) },
                        readoutTitle = { i ->
                            val interval = state.intervals[i]
                            "Tank ending ${formatShortDate(interval.endDate)} · ${Units.formatWholeDistance(interval.distanceKm, unit)}"
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (trend == Trend.CONSUMPTION) "Per full tank, most recent ${state.intervals.size}"
                    else "Last 12 months",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun fullMonth(state: StatsUiState, entry: ChartEntry): String =
    state.months.firstOrNull { formatMonth(it.monthStart) == entry.label }?.let { formatMonthYear(it.monthStart) } ?: entry.label

private fun compactNumber(value: Float): String = when {
    value >= 1_000_000f -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000f)
    value >= 10_000f -> String.format(Locale.getDefault(), "%.0fk", value / 1_000f)
    value >= 1_000f -> String.format(Locale.getDefault(), "%.1fk", value / 1_000f)
    else -> String.format(Locale.getDefault(), "%.0f", value)
}

@Composable
private fun ChartEmpty(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun FillupSection(state: StatsUiState) {
    val s = state.stats.fillups
    val unit = state.unit
    val money = LocalMoney.current
    val consumptionUnit = LocalConsumptionUnit.current
    fun cons(v: Float?) = v?.let { Units.formatConsumption(it, unit, consumptionUnit) } ?: "--"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Fill-ups")
        StatGrid(
            listOf(
                { m -> StatTile("Fill-ups", s.count.toString(), m, icon = Icons.Default.LocalGasStation) },
                { m -> StatTile("Total fuel", Units.formatFuel(s.totalLiters, unit), m, icon = Icons.Default.WaterDrop) },
                { m ->
                    StatTile(
                        "Avg consumption", cons(s.avgConsumption), m,
                        icon = Icons.Default.Speed,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                { m -> StatTile("Avg per fill-up", s.avgLitersPerFill?.let { Units.formatFuel(it, unit) } ?: "--", m) },
                { m -> StatTile("Best", cons(s.bestConsumption), m, icon = Icons.AutoMirrored.Filled.TrendingDown) },
                { m -> StatTile("Worst", cons(s.worstConsumption), m, icon = Icons.AutoMirrored.Filled.TrendingUp) },
                { m ->
                    StatTile(
                        "Avg price", s.avgPricePerLiter?.let { Units.formatPricePerUnit(it, unit, money) } ?: "--", m
                    )
                }
            )
        )
    }
}

@Composable
private fun CostSection(state: StatsUiState) {
    val c = state.stats.cost
    val unit = state.unit
    val money = LocalMoney.current
    val perUnit = "per ${Units.distanceLabel(unit)}"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Costs")
        StatTile(
            label = "Total cost",
            value = money.format(c.totalCost),
            supporting = "Fuel ${money.format(c.fuelCost)} · Other ${money.format(c.otherCost)}",
            icon = Icons.Default.Payments,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        StatGrid(
            listOf(
                { m -> StatTile("Lowest bill", c.lowestBill?.let { money.format(it) } ?: "--", m, icon = Icons.Default.ArrowDownward) },
                { m -> StatTile("Highest bill", c.highestBill?.let { money.format(it) } ?: "--", m, icon = Icons.Default.ArrowUpward) },
                { m ->
                    StatTile(
                        "Cost $perUnit",
                        c.costPerKm?.let { money.formatPrecise(Units.costPerDistance(it, unit)) } ?: "--", m,
                        supporting = "Fuel + expenses"
                    )
                },
                { m ->
                    StatTile(
                        "Fuel $perUnit",
                        c.fuelCostPerKm?.let { money.formatPrecise(Units.costPerDistance(it, unit)) } ?: "--", m,
                        supporting = c.avgBill?.let { "Avg bill ${money.format(it)}" }
                    )
                }
            )
        )
    }
}

@Composable
private fun DistanceSection(state: StatsUiState) {
    val d = state.stats.distance
    val unit = state.unit
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Distance")
        StatGrid(
            listOf(
                { m ->
                    StatTile(
                        "Driven", Units.formatWholeDistance(d.totalKm, unit), m,
                        icon = Icons.Default.Route,
                        supporting = "From odometer and trips",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                },
                { m ->
                    StatTile(
                        "Tracked trips", d.tripCount.toString(), m,
                        icon = Icons.Default.GpsFixed,
                        supporting = Units.formatWholeDistance(d.trackedKm, unit)
                    )
                },
                { m -> StatTile("Longest trip", d.longestTripKm?.let { Units.formatDistance(it, unit) } ?: "--", m) },
                { m -> StatTile("Per day", d.avgPerDayKm?.let { Units.formatDistance(it, unit) } ?: "--", m) },
                { m -> StatTile("Per month", d.avgPerMonthKm?.let { Units.formatWholeDistance(it, unit) } ?: "--", m) }
            )
        )
    }
}
