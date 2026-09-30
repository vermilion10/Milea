package com.github.vermilion10.milea.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.Expense
import com.github.vermilion10.milea.data.model.Fillup
import com.github.vermilion10.milea.data.model.Vehicle
import com.github.vermilion10.milea.data.repository.ExpenseRepository
import com.github.vermilion10.milea.data.repository.FillupRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import com.github.vermilion10.milea.domain.MonthBucket
import com.github.vermilion10.milea.domain.VehicleAnalytics
import com.github.vermilion10.milea.domain.VehicleData
import com.github.vermilion10.milea.domain.VehicleDataSource
import com.github.vermilion10.milea.service.TripControl
import com.github.vermilion10.milea.service.TripTrackingService
import com.github.vermilion10.milea.ui.components.*
import com.github.vermilion10.milea.ui.screens.expense.ExpenseSheet
import com.github.vermilion10.milea.ui.screens.expense.icon
import com.github.vermilion10.milea.ui.screens.fillup.FillupSheet
import com.github.vermilion10.milea.ui.screens.trip.icon
import com.github.vermilion10.milea.util.LocalMoney
import com.github.vermilion10.milea.util.TrackingPreflight
import com.github.vermilion10.milea.util.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RecentItem {
    val time: Long

    data class TripActivity(val trip: com.github.vermilion10.milea.data.model.Trip) : RecentItem {
        override val time get() = trip.startTime
    }

    data class FillupActivity(val fillup: Fillup) : RecentItem {
        override val time get() = fillup.date
    }

    data class ExpenseActivity(val expense: Expense) : RecentItem {
        override val time get() = expense.date
    }
}

data class DashboardState(
    val data: VehicleData? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val thisMonth: MonthBucket? = null,
    val lastMonth: MonthBucket? = null,
    val recent: List<RecentItem> = emptyList(),
    val loaded: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val fillupRepository: FillupRepository,
    private val expenseRepository: ExpenseRepository,
    vehicleDataSource: VehicleDataSource
) : ViewModel() {
    val state = combine(
        vehicleDataSource.selectedVehicleData(),
        vehicleRepository.getAllActiveVehicles()
    ) { data, vehicles ->
        if (data == null) return@combine DashboardState(vehicles = vehicles, loaded = true)
        val months = VehicleAnalytics.monthlyBuckets(data.vehicle, data.fillups, data.trips, data.expenses, months = 2)
        val recent = buildList<RecentItem> {
            data.trips.filter { it.endTime != null }.take(5).forEach { add(RecentItem.TripActivity(it)) }
            data.fillups.take(5).forEach { add(RecentItem.FillupActivity(it)) }
            data.expenses.take(5).forEach { add(RecentItem.ExpenseActivity(it)) }
        }.sortedByDescending { it.time }.take(5)
        DashboardState(
            data = data,
            vehicles = vehicles,
            thisMonth = months.last(),
            lastMonth = months.first(),
            recent = recent,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    val tracking = TripTrackingService.state

    fun selectVehicle(id: Long) {
        viewModelScope.launch { vehicleRepository.activateVehicle(id) }
    }

    fun saveFillup(fillup: Fillup) {
        viewModelScope.launch {
            if (fillup.id == 0L) fillupRepository.insertFillup(fillup) else fillupRepository.updateFillup(fillup)
        }
    }

    fun saveExpense(expense: Expense) {
        viewModelScope.launch {
            if (expense.id == 0L) expenseRepository.insertExpense(expense) else expenseRepository.updateExpense(expense)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenTrip: (Long) -> Unit = {},
    onOpenTrips: () -> Unit = {},
    onOpenFillups: () -> Unit = {},
    onOpenExpenses: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenVehicles: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracking by viewModel.tracking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val data = state.data
    val unit = data?.vehicle?.odometerUnit ?: DistanceUnit.KILOMETERS

    var fillupSheet by remember { mutableStateOf(false) }
    var expenseSheet by remember { mutableStateOf(false) }
    var switcherOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val startTrip = rememberTripStarter {
        data?.let { TripControl.start(context, it.vehicle.id, it.currentOdometerKm) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    if (data == null) {
                        Text("Milea")
                    } else {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { switcherOpen = true }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(data.vehicle.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch vehicle")
                            }
                            DropdownMenu(expanded = switcherOpen, onDismissRequest = { switcherOpen = false }) {
                                state.vehicles.forEach { v ->
                                    DropdownMenuItem(
                                        text = { Text(v.name) },
                                        leadingIcon = {
                                            if (v.id == data.vehicle.id) Icon(Icons.Default.Check, contentDescription = "Selected")
                                            else Spacer(Modifier.size(24.dp))
                                        },
                                        onClick = {
                                            switcherOpen = false
                                            viewModel.selectVehicle(v.id)
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Manage vehicles") },
                                    leadingIcon = { Icon(Icons.Default.Garage, contentDescription = null) },
                                    onClick = { switcherOpen = false; onOpenVehicles() }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (data == null) {
            EmptyState(
                icon = Icons.Default.DirectionsCar,
                title = "Welcome to Milea",
                message = "Add your vehicle to start logging trips, fuel and expenses.",
                modifier = Modifier.padding(padding),
                action = {
                    Button(onClick = onOpenVehicles) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add vehicle")
                    }
                }
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OdometerHero(data.vehicle, data.currentOdometerKm)

            if (tracking.isTracking) {
                LiveTripCard(
                    state = tracking,
                    unit = unit,
                    onStop = { TripControl.stop(context) },
                    onTurnOnLocation = { context.startActivity(TrackingPreflight.locationSettingsIntent()) }
                )
            }

            QuickActions(
                isTracking = tracking.isTracking,
                onStartTrip = startTrip,
                onRefuel = { fillupSheet = true },
                onExpense = { expenseSheet = true }
            )

            FuelGaugeCard(
                estimate = data.fuelEstimate,
                unit = unit,
                onAction = {
                    if (data.vehicle.tankCapacity == null) onOpenVehicles() else fillupSheet = true
                }
            )

            state.thisMonth?.let { month ->
                MonthSummary(month, state.lastMonth, unit, onOpenStats)
            }

            if (state.recent.isNotEmpty()) {
                RecentActivity(
                    items = state.recent,
                    unit = unit,
                    onTrip = onOpenTrip,
                    onFillups = onOpenFillups,
                    onExpenses = onOpenExpenses,
                    onSeeAll = onOpenTrips
                )
            }
        }
    }

    if (fillupSheet && data != null) {
        FillupSheet(
            data = data,
            existing = null,
            onDismiss = { fillupSheet = false },
            onSave = { viewModel.saveFillup(it); fillupSheet = false },
            onDelete = {}
        )
    }
    if (expenseSheet && data != null) {
        ExpenseSheet(
            data = data,
            existing = null,
            onDismiss = { expenseSheet = false },
            onSave = { viewModel.saveExpense(it); expenseSheet = false },
            onDelete = {}
        )
    }
}

@Composable
private fun OdometerHero(vehicle: Vehicle, odometerKm: Long) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Odometer", style = MaterialTheme.typography.labelLarge)
                Text(
                    String.format(java.util.Locale.getDefault(), "%,d", Math.round(Units.distance(odometerKm.toFloat(), vehicle.odometerUnit))),
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    listOfNotNull(
                        Units.distanceLabel(vehicle.odometerUnit),
                        listOfNotNull(vehicle.year?.toString(), vehicle.make, vehicle.model)
                            .joinToString(" ").ifBlank { null }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (vehicle.fuelType.name == "ELECTRIC") Icons.Default.ElectricCar else Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActions(
    isTracking: Boolean,
    onStartTrip: () -> Unit,
    onRefuel: () -> Unit,
    onExpense: () -> Unit
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (!isTracking) {
        Button(
            onClick = onStartTrip,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start trip", style = MaterialTheme.typography.titleMedium)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = onRefuel,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
        ) {
            Icon(Icons.Default.LocalGasStation, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Refuel")
        }
        FilledTonalButton(
            onClick = onExpense,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
        ) {
            Icon(Icons.Default.Receipt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Expense")
        }
    }
}

@Composable
private fun MonthSummary(month: MonthBucket, previous: MonthBucket?, unit: DistanceUnit, onOpenStats: () -> Unit) {
    val money = LocalMoney.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(formatMonthYear(month.monthStart)) {
            TextButton(onClick = onOpenStats) {
                Text("Statistics")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, Modifier.size(18.dp))
            }
        }
        fun change(now: Float, before: Float?): String? {
            if (before == null || before <= 0f) return null
            val pct = Math.round((now - before) / before * 100)
            return when {
                pct > 0 -> "↑ $pct% vs last month"
                pct < 0 -> "↓ ${-pct}% vs last month"
                else -> "Same as last month"
            }
        }
        StatGrid(
            listOf(
                { m ->
                    StatTile(
                        "Distance", Units.formatWholeDistance(month.distanceKm, unit), m,
                        icon = Icons.Default.Route,
                        supporting = change(month.distanceKm, previous?.distanceKm)
                    )
                },
                { m ->
                    StatTile(
                        "Spent", money.format(month.totalCost), m,
                        icon = Icons.Default.Payments,
                        supporting = change(month.totalCost, previous?.totalCost)
                    )
                },
                { m ->
                    StatTile(
                        "Fuel", Units.formatFuel(month.liters, unit), m,
                        icon = Icons.Default.LocalGasStation,
                        supporting = "${month.fillCount} fill-up${if (month.fillCount == 1) "" else "s"}"
                    )
                },
                { m ->
                    StatTile(
                        "Cost per ${Units.distanceLabel(unit)}",
                        if (month.distanceKm > 0f) money.formatPrecise(Units.costPerDistance(month.totalCost / month.distanceKm, unit)) else "--",
                        m,
                        icon = Icons.Default.Speed
                    )
                }
            )
        )
    }
}

@Composable
private fun RecentActivity(
    items: List<RecentItem>,
    unit: DistanceUnit,
    onTrip: (Long) -> Unit,
    onFillups: () -> Unit,
    onExpenses: () -> Unit,
    onSeeAll: () -> Unit
) {
    val money = LocalMoney.current
    Column {
        SectionHeader("Recent activity") {
            TextButton(onClick = onSeeAll) { Text("All trips") }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                items.forEachIndexed { i, item ->
                    val (icon, title, subtitle, onClick) = when (item) {
                        is RecentItem.TripActivity -> Row4(
                            item.trip.category.icon(),
                            "Trip · ${Units.formatDistance(item.trip.distance, unit)}",
                            "${formatRelativeDay(item.time)}, ${formatTime(item.time)} · ${formatDuration(item.trip.duration)}",
                            { onTrip(item.trip.id) }
                        )
                        is RecentItem.FillupActivity -> Row4(
                            Icons.Default.LocalGasStation,
                            "Refuel · ${money.format(item.fillup.totalCost)}",
                            "${formatRelativeDay(item.time)} · ${Units.formatFuel(item.fillup.liters, unit)}",
                            onFillups
                        )
                        is RecentItem.ExpenseActivity -> Row4(
                            item.expense.category.icon(),
                            "${item.expense.category.label()} · ${money.format(item.expense.amount)}",
                            listOfNotNull(formatRelativeDay(item.time), item.expense.description).joinToString(" · "),
                            onExpenses
                        )
                    }
                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable(onClick = onClick)
                    )
                    if (i < items.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

private data class Row4(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)
