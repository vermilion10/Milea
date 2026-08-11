package com.github.vermilion10.milea.ui.screens.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.Vehicle
import com.github.vermilion10.milea.data.repository.FillupRepository
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardStats(
    val totalDistance: Float = 0f,
    val totalFuelCost: Float = 0f,
    val totalLiters: Float = 0f,
    val tripCount: Int = 0,
    val avgConsumption: Float? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val tripRepository: TripRepository,
    private val fillupRepository: FillupRepository
) : ViewModel() {
    val activeVehicle = vehicleRepository.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val vehicles = vehicleRepository.getAllActiveVehicles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val stats = activeVehicle
        .filterNotNull()
        .flatMapLatest { vehicle ->
            combine(
                tripRepository.getTotalDistanceForVehicle(vehicle.id),
                fillupRepository.getTotalFuelCostForVehicle(vehicle.id),
                fillupRepository.getTotalLitersForVehicle(vehicle.id),
                tripRepository.getTripCountForVehicle(vehicle.id)
            ) { distance, cost, liters, count ->
                DashboardStats(
                    totalDistance = distance ?: 0f,
                    totalFuelCost = cost ?: 0f,
                    totalLiters = liters ?: 0f,
                    tripCount = count,
                    avgConsumption = fillupRepository.calculateConsumption(vehicle.id)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, DashboardStats())

    // Vehicle's real-world total odometer -- NOT the app's "Total Distance"
    // stat (which only sums trips logged in-app). Best estimate from the
    // latest fill-up or trip end reading, falling back to the vehicle's
    // configured offset. Reactive to new fillups/trips for the active vehicle.
    val currentOdometer = activeVehicle
        .filterNotNull()
        .flatMapLatest { vehicle ->
            combine(
                fillupRepository.getFillupsByVehicle(vehicle.id),
                tripRepository.getMaxOdometerFlow(vehicle.id)
            ) { fillups, maxTripOdometer ->
                val latestFillupOdometer = fillups.maxByOrNull { it.odometer }?.odometer
                listOfNotNull(latestFillupOdometer, maxTripOdometer, vehicle.odometerOffset)
                    .maxOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun hasVehicles(): Flow<Boolean> = vehicleRepository.getAllActiveVehicles()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun setActiveVehicle(vehicleId: Long) {
        viewModelScope.launch {
            vehicleRepository.activateVehicle(vehicleId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenTrips: () -> Unit = {},
    onOpenFillups: () -> Unit = {},
    onOpenExpenses: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenVehicles: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val currentOdometer by viewModel.currentOdometer.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Milea") 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            activeVehicle?.let { vehicle ->
                VehicleInfoCard(vehicle, currentOdometer)
                VehicleSwitcher(
                    vehicles = vehicles,
                    activeVehicleId = vehicle.id,
                    onSelect = { viewModel.setActiveVehicle(it) },
                    onAddVehicle = onOpenVehicles
                )
                StatsGrid(stats, vehicle.odometerUnit)
            } ?: run {
                EmptyStateCard(onAddVehicle = onOpenVehicles)
            }

            QuickActions(
                hasVehicle = activeVehicle != null,
                onOpenTrips = onOpenTrips,
                onOpenFillups = onOpenFillups,
                onOpenExpenses = onOpenExpenses,
                onOpenStats = onOpenStats,
                onOpenVehicles = onOpenVehicles
            )
        }
    }
}

@Composable
fun EmptyStateCard(onAddVehicle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No vehicles yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Add a vehicle to start tracking trips, fuel, and expenses.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddVehicle) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Vehicle")
            }
        }
    }
}

@Composable
fun VehicleInfoCard(vehicle: Vehicle, currentOdometer: Long? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    vehicle.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (vehicle.make != null || vehicle.model != null) {
                    Text(
                        listOfNotNull(vehicle.make, vehicle.model, vehicle.year?.toString())
                            .joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                // Vehicle's real total odometer, not the app's tracked distance.
                currentOdometer?.let { odometer ->
                    Text(
                        "${com.github.vermilion10.milea.util.Units.formatDistanceNumber(odometer.toFloat(), vehicle.odometerUnit)} ${com.github.vermilion10.milea.util.Units.distanceLabel(vehicle.odometerUnit)} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun VehicleSwitcher(
    vehicles: List<Vehicle>,
    activeVehicleId: Long,
    onSelect: (Long) -> Unit,
    onAddVehicle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        vehicles.forEach { vehicle ->
            FilterChip(
                selected = vehicle.id == activeVehicleId,
                onClick = { onSelect(vehicle.id) },
                label = { Text(vehicle.name) }
            )
        }
        // Quick access to add/manage vehicles without going through Quick Actions.
        AssistChip(
            onClick = onAddVehicle,
            label = { Text("Manage") },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add or manage vehicles",
                    modifier = Modifier.size(16.dp)
                )
            }
        )
    }
}

@Composable
fun QuickActions(
    hasVehicle: Boolean,
    onOpenTrips: () -> Unit,
    onOpenFillups: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenVehicles: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    title = "Trips",
                    icon = Icons.Default.Route,
                    onClick = onOpenTrips,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    title = "Fuel",
                    icon = Icons.Default.LocalGasStation,
                    onClick = onOpenFillups,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    title = "Expenses",
                    icon = Icons.Default.Receipt,
                    onClick = onOpenExpenses,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    title = "Statistics",
                    icon = Icons.Default.BarChart,
                    onClick = onOpenStats,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    title = "Vehicles",
                    icon = Icons.Default.DirectionsCar,
                    onClick = onOpenVehicles,
                    modifier = Modifier.weight(1f)
                )
                if (!hasVehicle) {
                    QuickActionButton(
                        title = "Add Vehicle",
                        icon = Icons.Default.Add,
                        onClick = onOpenVehicles,
                        highlighted = true,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    if (highlighted) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(title)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(title)
        }
    }
}

@Composable
fun StatsGrid(
    stats: DashboardStats,
    unit: com.github.vermilion10.milea.data.model.DistanceUnit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Statistics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Total Distance",
                value = com.github.vermilion10.milea.util.Units.formatDistance(stats.totalDistance, unit),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Total Trips",
                value = stats.tripCount.toString(),
                icon = Icons.Default.Timeline,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Fuel Cost",
                value = "$${String.format("%.2f", stats.totalFuelCost)}",
                icon = Icons.Default.AttachMoney,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Fuel Used",
                value = com.github.vermilion10.milea.util.Units.formatFuel(stats.totalLiters, unit),
                icon = Icons.Default.LocalGasStation,
                modifier = Modifier.weight(1f)
            )
        }

        stats.avgConsumption?.let { consumption ->
            StatCard(
                title = "Avg Consumption",
                value = com.github.vermilion10.milea.util.Units.formatConsumption(consumption, unit),
                icon = Icons.Default.Speed,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
