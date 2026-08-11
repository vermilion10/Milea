package com.github.vermilion10.milea.ui.screens.fillup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.github.vermilion10.milea.data.model.Fillup
import com.github.vermilion10.milea.data.repository.FillupRepository
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import com.github.vermilion10.milea.ui.components.NoActiveVehicleMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class FillupListViewModel @Inject constructor(
    private val fillupRepository: FillupRepository,
    private val vehicleRepository: VehicleRepository,
    private val tripRepository: TripRepository
) : ViewModel() {
    val activeVehicle = vehicleRepository.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)
    val selectedVehicleId = _selectedVehicleId.asStateFlow()

    val fillups = selectedVehicleId
        .filterNotNull()
        .flatMapLatest { vehicleId ->
            fillupRepository.getFillupsByVehicle(vehicleId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog = _showAddDialog.asStateFlow()

    private val _lastPrice = MutableStateFlow<Float?>(null)
    val lastPrice = _lastPrice.asStateFlow()

    // Best known current odometer, offered as a prefill in the Add Fill-up
    // dialog -- the field stays fully editable, this just saves re-typing it.
    private val _suggestedOdometer = MutableStateFlow<Long?>(null)
    val suggestedOdometer = _suggestedOdometer.asStateFlow()

    fun setActiveVehicle(vehicleId: Long) {
        _selectedVehicleId.value = vehicleId
    }

    fun setShowAddDialog(show: Boolean) {
        _showAddDialog.value = show
        if (show) {
            loadLastPrice()
            loadSuggestedOdometer()
        }
    }

    private fun loadLastPrice() {
        viewModelScope.launch {
            _selectedVehicleId.value?.let { vehicleId ->
                fillupRepository.getLatestFillup(vehicleId)?.let { fillup ->
                    _lastPrice.value = fillup.pricePerUnit
                }
            }
        }
    }

    private fun loadSuggestedOdometer() {
        viewModelScope.launch {
            _selectedVehicleId.value?.let { vehicleId ->
                val latestFillupOdometer = fillupRepository.getLatestFillup(vehicleId)?.odometer
                val maxTripOdometer = tripRepository.getMaxOdometerSync(vehicleId)
                val vehicleOffset = vehicleRepository.getVehicleById(vehicleId)?.odometerOffset
                // 0 is a legitimate reading for a brand new vehicle -- not "unknown".
                _suggestedOdometer.value =
                    listOfNotNull(latestFillupOdometer, maxTripOdometer, vehicleOffset)
                        .maxOrNull()
            }
        }
    }

    fun addFillup(fillup: Fillup) {
        viewModelScope.launch {
            fillupRepository.insertFillup(fillup)
        }
    }

    fun deleteFillup(fillup: Fillup) {
        viewModelScope.launch {
            fillupRepository.deleteFillup(fillup)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillupListScreen(
    viewModel: FillupListViewModel = hiltViewModel()
) {
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val fillups by viewModel.fillups.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val lastPrice by viewModel.lastPrice.collectAsState()
    val suggestedOdometer by viewModel.suggestedOdometer.collectAsState()

    LaunchedEffect(activeVehicle) {
        activeVehicle?.let { viewModel.setActiveVehicle(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuel Fill-ups") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (activeVehicle != null) {
                FloatingActionButton(
                    onClick = { viewModel.setShowAddDialog(true) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Fill-up")
                }
            }
        }
    ) { padding ->
        if (activeVehicle == null) {
            NoActiveVehicleMessage(
                padding = padding,
                message = "Add a vehicle first to log fill-ups."
            )
        } else if (fillups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalGasStation,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No fill-ups recorded yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add your first fuel fill-up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fillups) { fillup ->
                    FillupCard(
                        fillup = fillup,
                        unit = activeVehicle?.odometerUnit
                            ?: com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS
                    )
                }
            }
        }

        if (showAddDialog) {
            AddFillupDialog(
                vehicleId = viewModel.selectedVehicleId.value ?: 0,
                lastPrice = lastPrice,
                tankCapacity = activeVehicle?.tankCapacity,
                suggestedOdometer = suggestedOdometer,
                unit = activeVehicle?.odometerUnit
                    ?: com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
                onDismiss = { viewModel.setShowAddDialog(false) },
                onAdd = { fillup ->
                    viewModel.addFillup(fillup)
                    viewModel.setShowAddDialog(false)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillupCard(
    fillup: Fillup,
    unit: com.github.vermilion10.milea.data.model.DistanceUnit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateFormat.format(Date(fillup.date)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (fillup.isFullTank) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Full Tank") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FillupStatItem(
                    icon = Icons.Default.LocalGasStation,
                    label = "Fuel",
                    value = com.github.vermilion10.milea.util.Units.formatFuel(fillup.liters, unit)
                )
                FillupStatItem(
                    icon = Icons.Default.Speed,
                    label = "Odometer",
                    value = "${com.github.vermilion10.milea.util.Units.formatDistanceNumber(fillup.odometer.toFloat(), unit)} ${com.github.vermilion10.milea.util.Units.distanceLabel(unit)}"
                )
                FillupStatItem(
                    icon = Icons.Default.AttachMoney,
                    label = "Cost",
                    value = "$${String.format("%.2f", fillup.totalCost)}"
                )
            }

            if (fillup.pricePerUnit > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Price: ${com.github.vermilion10.milea.util.Units.formatPricePerUnit(fillup.pricePerUnit, unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FillupStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AddFillupDialog(
    vehicleId: Long,
    lastPrice: Float?,
    tankCapacity: Float? = null,
    suggestedOdometer: Long? = null,
    unit: com.github.vermilion10.milea.data.model.DistanceUnit = com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
    onDismiss: () -> Unit,
    onAdd: (Fillup) -> Unit
) {
    // Prefilled from the best-known odometer (latest fill-up/trip/offset) but
    // always editable -- covers "auto-detect OR manual entry" for odometer.
    var odometer by remember {
        mutableStateOf(
            suggestedOdometer?.let {
                Math.round(com.github.vermilion10.milea.util.Units.distance(it.toFloat(), unit)).toString()
            } ?: ""
        )
    }
    var liters by remember { mutableStateOf("") }
    var pricePerUnit by remember { mutableStateOf(lastPrice?.toString() ?: "") }
    var totalCost by remember { mutableStateOf("") }
    var isFullTank by remember { mutableStateOf(true) }
    var fillPercent by remember { mutableStateOf("") }

    // True once the % slider/field has produced a liters value. While true,
    // liters is considered "pinned" by percent -- it's never treated as a
    // calculation target, and the field is disabled to avoid two sources of
    // truth fighting each other.
    var usingPercent by remember { mutableStateOf(false) }

    // Tracks which of the three fuel fields the user has most recently typed into
    // (oldest first). Whichever field is NOT among the two most-recently-edited
    // gets auto-calculated from the other two. This makes the calculator fully
    // bidirectional: e.g. entering total cost + price/unit at the pump now
    // computes liters for you, instead of only liters+price -> total cost.
    val editOrder = remember { mutableStateListOf("liters", "price") }
    fun touch(field: String) {
        editOrder.remove(field)
        editOrder.add(field)
    }

    LaunchedEffect(liters, pricePerUnit, totalCost, editOrder.toList(), usingPercent) {
        val litersValue = liters.toFloatOrNull()
        val priceValue = pricePerUnit.toFloatOrNull()
        val costValue = totalCost.toFloatOrNull()

        if (usingPercent) {
            // Liters is pinned by the % slider/field -- it can never be a
            // calculation target here. Only price and cost interchange, based
            // on whichever of the two was actually edited more recently. This
            // is what prevents price from silently changing just because you
            // typed a total cost while a % fill was active.
            val priceOrCostEdits = editOrder.filter { it == "price" || it == "cost" }
            val target = if (priceOrCostEdits.lastOrNull() == "price") "cost" else "price"
            when (target) {
                "cost" -> if (litersValue != null && litersValue > 0 && priceValue != null && priceValue > 0) {
                    totalCost = String.format("%.2f", litersValue * priceValue)
                }
                "price" -> if (litersValue != null && litersValue > 0 && costValue != null && costValue > 0) {
                    pricePerUnit = String.format("%.3f", costValue / litersValue)
                }
            }
        } else {
            val recentlyEdited = editOrder.takeLast(2)
            if (recentlyEdited.size == 2) {
                when (listOf("liters", "price", "cost").firstOrNull { it !in recentlyEdited }) {
                    "cost" -> if (litersValue != null && litersValue > 0 && priceValue != null && priceValue > 0) {
                        totalCost = String.format("%.2f", litersValue * priceValue)
                    }
                    "liters" -> if (costValue != null && costValue > 0 && priceValue != null && priceValue > 0) {
                        liters = String.format("%.3f", costValue / priceValue)
                    }
                    "price" -> if (costValue != null && costValue > 0 && litersValue != null && litersValue > 0) {
                        pricePerUnit = String.format("%.3f", costValue / litersValue)
                    }
                }
            }
        }
    }

    // When it's not a full tank, let the user say what percentage of the tank
    // they're adding instead of having to know/enter the exact liters. Liters
    // is derived from the vehicle's tank capacity and then pinned (see above)
    // -- price and cost still calculate off it, but percent itself is never
    // overwritten by them.
    LaunchedEffect(fillPercent, tankCapacity, isFullTank) {
        val pct = fillPercent.toFloatOrNull()
        if (!isFullTank && pct != null && pct in 0f..100f && tankCapacity != null && tankCapacity > 0) {
            val litersStored = tankCapacity * pct / 100f
            liters = String.format("%.3f", com.github.vermilion10.milea.util.Units.fuel(litersStored, unit))
            usingPercent = true
        } else if (fillPercent.isBlank()) {
            usingPercent = false
        }
    }

    LaunchedEffect(isFullTank) {
        if (isFullTank) fillPercent = ""
    }

    val distanceLabel = com.github.vermilion10.milea.util.Units.distanceLabel(unit)
    val fuelLabel = com.github.vermilion10.milea.util.Units.fuelLabel(unit)
    val priceLabel = com.github.vermilion10.milea.util.Units.priceUnitLabel(unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Fill-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter { c -> c.isDigit() } },
                    label = { Text("Odometer ($distanceLabel) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = liters,
                    onValueChange = { liters = it; touch("liters") },
                    label = { Text(if (usingPercent) "Fuel ($fuelLabel) \u2014 from % below" else "Fuel ($fuelLabel) *") },
                    singleLine = true,
                    enabled = !usingPercent,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pricePerUnit,
                    onValueChange = { pricePerUnit = it; touch("price") },
                    label = { Text("Price per $priceLabel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = totalCost,
                    onValueChange = { totalCost = it; touch("cost") },
                    label = { Text("Total Cost (enter this + price to auto-fill fuel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isFullTank,
                        onCheckedChange = { isFullTank = it }
                    )
                    Text("Full Tank")
                }
                if (!isFullTank) {
                    OutlinedTextField(
                        value = fillPercent,
                        onValueChange = { fillPercent = it.filter { c -> c.isDigit() || c == '.' } },
                        label = {
                            Text(
                                if (tankCapacity != null) "Fill % of tank added"
                                else "Fill % (set tank capacity on vehicle first)"
                            )
                        },
                        singleLine = true,
                        enabled = tankCapacity != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tankCapacity != null) {
                        Slider(
                            value = (fillPercent.toFloatOrNull() ?: 0f).coerceIn(0f, 100f),
                            onValueChange = { fillPercent = it.toInt().toString() },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val odometerValue = odometer.toLongOrNull() ?: 0
                    val litersValue = liters.toFloatOrNull() ?: 0f
                    val costValue = totalCost.toFloatOrNull() ?: 0f
                    val priceValue = pricePerUnit.toFloatOrNull() ?: 0f

                    val odometerKm = if (unit == com.github.vermilion10.milea.data.model.DistanceUnit.MILES) {
                        (odometerValue / 0.621371).toLong()
                    } else {
                        odometerValue
                    }
                    val litersStored = if (unit == com.github.vermilion10.milea.data.model.DistanceUnit.MILES) {
                        litersValue * 3.78541f
                    } else {
                        litersValue
                    }
                    val pricePerLiter = if (unit == com.github.vermilion10.milea.data.model.DistanceUnit.MILES) {
                        priceValue / 3.78541f
                    } else {
                        priceValue
                    }

                    if (odometerValue > 0 && litersValue > 0 && costValue > 0) {
                        onAdd(
                            Fillup(
                                vehicleId = vehicleId,
                                date = System.currentTimeMillis(),
                                odometer = odometerKm,
                                liters = litersStored,
                                pricePerUnit = pricePerLiter,
                                totalCost = costValue,
                                isFullTank = isFullTank
                            )
                        )
                    }
                },
                enabled = odometer.isNotBlank() && liters.isNotBlank() && totalCost.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
