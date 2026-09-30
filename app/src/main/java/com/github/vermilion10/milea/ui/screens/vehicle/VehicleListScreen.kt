package com.github.vermilion10.milea.ui.screens.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.github.vermilion10.milea.ui.components.EmptyState
import com.github.vermilion10.milea.ui.components.NumberField
import com.github.vermilion10.milea.ui.components.toInputString
import com.github.vermilion10.milea.util.Units
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.FuelType
import com.github.vermilion10.milea.data.model.Vehicle
import com.github.vermilion10.milea.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleListViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {
    val vehicles = vehicleRepository.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeVehicle = vehicleRepository.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog = _showAddDialog.asStateFlow()

    private val _vehicleToEdit = MutableStateFlow<Vehicle?>(null)
    val vehicleToEdit = _vehicleToEdit.asStateFlow()

    fun setShowAddDialog(show: Boolean) {
        _showAddDialog.value = show
    }

    fun setVehicleToEdit(vehicle: Vehicle?) {
        _vehicleToEdit.value = vehicle
    }

    fun addVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepository.insertVehicle(vehicle)
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepository.updateVehicle(vehicle.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun archiveVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepository.archiveVehicle(vehicle.id)
        }
    }

    fun activateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepository.activateVehicle(vehicle.id)
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepository.deleteVehicle(vehicle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    onBack: (() -> Unit)? = null,
    viewModel: VehicleListViewModel = hiltViewModel()
) {
    val vehicles by viewModel.vehicles.collectAsState()
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val vehicleToEdit by viewModel.vehicleToEdit.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicles") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.setShowAddDialog(true) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add vehicle") }
            )
        }
    ) { padding ->
        if (vehicles.isEmpty()) {
            EmptyState(
                icon = Icons.Default.DirectionsCar,
                title = "No vehicles yet",
                message = "Add the car or motorcycle you want to track. You can add more later and switch between them.",
                modifier = Modifier.padding(padding)
            )
        } else {
            val (current, archived) = vehicles.partition { it.isActive }
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(current, key = { it.id }) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        isSelected = vehicle.id == activeVehicle?.id,
                        onEdit = { viewModel.setVehicleToEdit(vehicle) },
                        onSelect = { viewModel.activateVehicle(vehicle) },
                        onArchive = { viewModel.archiveVehicle(vehicle) }
                    )
                }
                if (archived.isNotEmpty()) {
                    item {
                        Text(
                            "Archived",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(archived, key = { it.id }) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            isSelected = false,
                            onEdit = { viewModel.setVehicleToEdit(vehicle) },
                            onSelect = { viewModel.activateVehicle(vehicle) },
                            onArchive = null
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            VehicleSheet(
                onDismiss = { viewModel.setShowAddDialog(false) },
                onSave = { vehicle ->
                    viewModel.addVehicle(vehicle)
                    viewModel.setShowAddDialog(false)
                }
            )
        }

        vehicleToEdit?.let { vehicle ->
            VehicleSheet(
                vehicle = vehicle,
                onDismiss = { viewModel.setVehicleToEdit(null) },
                onSave = { updated ->
                    viewModel.updateVehicle(updated)
                    viewModel.setVehicleToEdit(null)
                }
            )
        }
    }
}

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    isSelected: Boolean,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onArchive: (() -> Unit)?
) {
    val unit = vehicle.odometerUnit
    ElevatedCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (vehicle.fuelType == FuelType.ELECTRIC) Icons.Default.ElectricCar else Icons.Default.DirectionsCar,
                            contentDescription = null
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(vehicle.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(
                            listOfNotNull(vehicle.year?.toString(), vehicle.make, vehicle.model).joinToString(" ").ifBlank { null },
                            vehicle.fuelType.name.lowercase().replaceFirstChar { it.titlecase() },
                            vehicle.tankCapacity?.let { "${Units.formatFuel(it, unit)} tank" }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onArchive != null && isSelected) {
                    TextButton(onClick = onArchive) { Text("Archive") }
                }
                if (!isSelected) {
                    TextButton(onClick = onSelect) {
                        Text(if (vehicle.isActive) "Select" else "Restore and select")
                    }
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleSheet(
    vehicle: Vehicle? = null,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit
) {
    var unit by remember { mutableStateOf(vehicle?.odometerUnit ?: DistanceUnit.KILOMETERS) }
    var name by remember { mutableStateOf(vehicle?.name ?: "") }
    var make by remember { mutableStateOf(vehicle?.make ?: "") }
    var model by remember { mutableStateOf(vehicle?.model ?: "") }
    var year by remember { mutableStateOf(vehicle?.year?.toString() ?: "") }
    var fuelType by remember { mutableStateOf(vehicle?.fuelType ?: FuelType.GASOLINE) }
    // Capacity and offset are stored in liters / km; shown in the vehicle's own units.
    var tankCapacity by remember {
        mutableStateOf(vehicle?.tankCapacity?.let { Units.fuel(it, vehicle.odometerUnit).toInputString(1) } ?: "")
    }
    var odometerOffset by remember {
        mutableStateOf(
            vehicle?.odometerOffset?.takeIf { it > 0 }
                ?.let { Math.round(Units.distance(it.toFloat(), vehicle.odometerUnit)).toString() } ?: ""
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.navigationBars) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (vehicle == null) "Add vehicle" else "Edit vehicle",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Daily rider") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = make,
                    onValueChange = { make = it },
                    label = { Text("Make") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = year,
                onValueChange = { year = it.filter { ch -> ch.isDigit() }.take(4) },
                label = { Text("Year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fuel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FuelType.entries.forEach { type ->
                        FilterChip(
                            selected = fuelType == type,
                            onClick = { fuelType = type },
                            label = { Text(type.name.lowercase().replaceFirstChar { it.titlecase() }) }
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Units", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = unit == DistanceUnit.KILOMETERS,
                        onClick = { unit = DistanceUnit.KILOMETERS },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("km · L") }
                    SegmentedButton(
                        selected = unit == DistanceUnit.MILES,
                        onClick = { unit = DistanceUnit.MILES },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("mi · gal") }
                }
            }
            NumberField(
                value = tankCapacity,
                onValueChange = { tankCapacity = it },
                label = "Tank capacity",
                decimals = 1,
                suffix = Units.fuelLabel(unit),
                supportingText = "Needed to estimate fuel left and range",
                modifier = Modifier.fillMaxWidth()
            )
            NumberField(
                value = odometerOffset,
                onValueChange = { odometerOffset = it },
                label = "Odometer when you started tracking",
                decimals = 0,
                suffix = Units.distanceLabel(unit),
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(
                            (vehicle ?: Vehicle(name = name.trim())).copy(
                                name = name.trim(),
                                make = make.trim().ifBlank { null },
                                model = model.trim().ifBlank { null },
                                year = year.toIntOrNull(),
                                fuelType = fuelType,
                                tankCapacity = tankCapacity.toFloatOrNull()?.takeIf { it > 0f }
                                    ?.let { Units.fuelToLiters(it, unit) },
                                odometerOffset = odometerOffset.toFloatOrNull()
                                    ?.let { Math.round(Units.distanceToKm(it, unit)).toLong() } ?: 0,
                                odometerUnit = unit,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                ) { Text("Save") }
            }
        }
    }
}
