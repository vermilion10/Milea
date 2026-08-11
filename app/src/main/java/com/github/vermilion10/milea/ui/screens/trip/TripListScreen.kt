package com.github.vermilion10.milea.ui.screens.trip

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.TripCategory
import com.github.vermilion10.milea.data.repository.FillupRepository
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import com.github.vermilion10.milea.service.TripTrackingService
import com.github.vermilion10.milea.ui.components.NoActiveVehicleMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
    private val fillupRepository: FillupRepository
) : ViewModel() {
    val activeVehicle = vehicleRepository.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)
    val selectedVehicleId = _selectedVehicleId.asStateFlow()

    private val _categoryFilter = MutableStateFlow<TripCategory?>(null)
    val categoryFilter = _categoryFilter.asStateFlow()

    val trips = selectedVehicleId
        .filterNotNull()
        .flatMapLatest { vehicleId ->
            categoryFilter.flatMapLatest { category ->
                if (category == null) {
                    tripRepository.getTripsByVehicle(vehicleId)
                } else {
                    tripRepository.getTripsByVehicleAndCategory(vehicleId, category)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    fun setActiveVehicle(vehicleId: Long) {
        _selectedVehicleId.value = vehicleId
    }

    fun setCategoryFilter(category: TripCategory?) {
        _categoryFilter.value = category
    }

    fun setIsTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }

    // Best known odometer reading to carry forward as the new trip's start:
    // prefer the most recent fill-up (most reliable, hand-entered at the pump),
    // fall back to the previous trip's highest recorded reading, then the
    // vehicle's configured offset. 0 is a legitimate value (a brand new
    // vehicle genuinely starts at/near 0) -- it's kept, not treated as unknown.
    suspend fun estimateStartOdometer(vehicleId: Long): Long? {
        val latestFillupOdometer = fillupRepository.getLatestFillup(vehicleId)?.odometer
        val maxTripOdometer = tripRepository.getMaxOdometerSync(vehicleId)
        val vehicleOffset = vehicleRepository.getVehicleById(vehicleId)?.odometerOffset
        return listOfNotNull(latestFillupOdometer, maxTripOdometer, vehicleOffset)
            .maxOrNull()
    }

    fun addTrip(trip: Trip) {
        viewModelScope.launch {
            tripRepository.insertTrip(trip)
        }
    }

    fun updateTrip(trip: Trip) {
        viewModelScope.launch {
            tripRepository.updateTrip(trip.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            tripRepository.deleteTrip(trip)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    onTripClick: (Long) -> Unit = {},
    viewModel: TripListViewModel = hiltViewModel()
) {
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val context = LocalContext.current

    var showEntryDialog by remember { mutableStateOf(false) }
    var tripToEdit by remember { mutableStateOf<Trip?>(null) }
    var suggestedEntryOdometer by remember { mutableStateOf<Long?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            activeVehicle?.let { vehicle ->
                coroutineScope.launch {
                    val startOdometer = viewModel.estimateStartOdometer(vehicle.id)
                    startTracking(context, vehicle.id, startOdometer)
                    viewModel.setIsTracking(true)
                }
            }
        }
    }

    LaunchedEffect(activeVehicle) {
        activeVehicle?.let { viewModel.setActiveVehicle(it.id) }
    }

    // Bind to the running tracking service (if any) so this screen can show
    // live distance/duration/speed while a trip is in progress, instead of
    // only ever showing data once the trip has ended and been saved.
    var trackingService by remember { mutableStateOf<TripTrackingService?>(null) }
    val serviceConnection = remember {
        object : android.content.ServiceConnection {
            override fun onServiceConnected(
                name: android.content.ComponentName?,
                binder: android.os.IBinder?
            ) {
                trackingService = (binder as? TripTrackingService.LocalBinder)?.getService()
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                trackingService = null
            }
        }
    }

    DisposableEffect(isTracking) {
        if (isTracking) {
            val bindIntent = Intent(context, TripTrackingService::class.java)
            context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        onDispose {
            if (trackingService != null) {
                runCatching { context.unbindService(serviceConnection) }
                trackingService = null
            }
        }
    }

    var liveDistanceMeters by remember { mutableStateOf(0f) }
    var liveDurationMs by remember { mutableStateOf(0L) }
    var liveSpeedMps by remember { mutableStateOf(0f) }
    var liveMaxSpeedMps by remember { mutableStateOf(0f) }

    LaunchedEffect(trackingService) {
        trackingService?.let { service ->
            launch { service.distance.collect { liveDistanceMeters = it } }
            launch { service.duration.collect { liveDurationMs = it } }
            launch { service.currentSpeed.collect { liveSpeedMps = it } }
            launch { service.maxSpeed.collect { liveMaxSpeedMps = it } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = {
                        activeVehicle?.let { vehicle ->
                            coroutineScope.launch {
                                suggestedEntryOdometer = viewModel.estimateStartOdometer(vehicle.id)
                                showEntryDialog = true
                            }
                        } ?: run { showEntryDialog = true }
                    }) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = "Add Trip Manually"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isTracking && activeVehicle != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        activeVehicle?.let { vehicle ->
                            if (hasTrackingPermissions(context)) {
                                coroutineScope.launch {
                                    val startOdometer = viewModel.estimateStartOdometer(vehicle.id)
                                    startTracking(context, vehicle.id, startOdometer)
                                    viewModel.setIsTracking(true)
                                }
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    )
                                )
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.PlayArrow, "Start Trip") },
                    text = { Text("Start Trip") }
                )
            } else if (isTracking) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val intent = Intent(context, TripTrackingService::class.java).apply {
                            action = TripTrackingService.ACTION_STOP_TRACKING
                        }
                        ContextCompat.startForegroundService(context, intent)
                        viewModel.setIsTracking(false)
                    },
                    icon = { Icon(Icons.Default.Stop, "Stop Trip") },
                    text = { Text("Stop Trip") },
                    containerColor = MaterialTheme.colorScheme.error
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (activeVehicle == null) {
                NoActiveVehicleMessage(
                    padding = PaddingValues(16.dp),
                    message = "Add a vehicle first to track trips."
                )
            } else {
                TripCategoryFilter(
                    selected = categoryFilter,
                    onSelect = { viewModel.setCategoryFilter(it) }
                )

                if (isTracking && trackingService != null) {
                    LiveTripCard(
                        distanceMeters = liveDistanceMeters,
                        durationMs = liveDurationMs,
                        speedMps = liveSpeedMps,
                        maxSpeedMps = liveMaxSpeedMps,
                        unit = activeVehicle?.odometerUnit
                            ?: com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (trips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Route,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No trips recorded yet",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Start tracking or add a trip manually",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trips) { trip ->
                            TripCard(
                                trip = trip,
                                unit = activeVehicle?.odometerUnit
                                    ?: com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
                                onClick = { onTripClick(trip.id) },
                                onEdit = { tripToEdit = trip }
                            )
                        }
                    }
                }
            }
        }
    }

    val activeVehicleForEntry = activeVehicle
    if (showEntryDialog && activeVehicleForEntry != null) {
        TripEntryDialog(
            vehicleId = activeVehicleForEntry.id,
            unit = activeVehicleForEntry.odometerUnit,
            suggestedOdometer = suggestedEntryOdometer,
            onDismiss = { showEntryDialog = false },
            onSave = { trip ->
                viewModel.addTrip(trip)
                showEntryDialog = false
            }
        )
    }

    tripToEdit?.let { trip ->
        TripEntryDialog(
            trip = trip,
            vehicleId = trip.vehicleId,
            unit = activeVehicle?.odometerUnit
                ?: com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
            onDismiss = { tripToEdit = null },
            onSave = { updated ->
                viewModel.updateTrip(updated)
                tripToEdit = null
            },
            onDelete = {
                viewModel.deleteTrip(trip)
                tripToEdit = null
            }
        )
    }
}

@Composable
fun LiveTripCard(
    distanceMeters: Float,
    durationMs: Long,
    speedMps: Float,
    maxSpeedMps: Float,
    unit: com.github.vermilion10.milea.data.model.DistanceUnit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Recording trip",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Route,
                    label = "Distance",
                    value = com.github.vermilion10.milea.util.Units.formatDistance(distanceMeters / 1000f, unit)
                )
                StatItem(
                    icon = Icons.Default.Schedule,
                    label = "Duration",
                    value = formatDuration(durationMs)
                )
                StatItem(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    value = com.github.vermilion10.milea.util.Units.formatSpeed(speedMps * 3.6f, unit)
                )
                StatItem(
                    icon = Icons.Default.Speed,
                    label = "Max Speed",
                    value = com.github.vermilion10.milea.util.Units.formatSpeed(maxSpeedMps * 3.6f, unit)
                )
            }
        }
    }
}

@Composable
fun TripCategoryFilter(
    selected: TripCategory?,
    onSelect: (TripCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") }
        )
        TripCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category.name.lowercase().replaceFirstChar { it.titlecase() }) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCard(
    trip: Trip,
    unit: com.github.vermilion10.milea.data.model.DistanceUnit,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateFormat.format(Date(trip.startTime)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeFormat.format(Date(trip.startTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                trip.category.name.lowercase()
                                    .replaceFirstChar { it.titlecase() }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                when (trip.category) {
                                    TripCategory.COMMUTE -> Icons.Default.Work
                                    TripCategory.BUSINESS -> Icons.Default.Business
                                    TripCategory.LEISURE -> Icons.Default.BeachAccess
                                    TripCategory.OTHER -> Icons.Default.MoreHoriz
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Trip",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Route,
                    label = "Distance",
                    value = com.github.vermilion10.milea.util.Units.formatDistance(trip.distance, unit)
                )
                StatItem(
                    icon = Icons.Default.Schedule,
                    label = "Duration",
                    value = formatDuration(trip.duration)
                )
                StatItem(
                    icon = Icons.Default.Speed,
                    label = "Avg Speed",
                    value = com.github.vermilion10.milea.util.Units.formatSpeed(trip.averageSpeed * 3.6f, unit)
                )
            }
        }
    }
}

@Composable
fun StatItem(
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

fun formatDuration(millis: Long): String {
    val hours = millis / 3600000
    val minutes = (millis % 3600000) / 60000
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEntryDialog(
    vehicleId: Long,
    trip: Trip? = null,
    unit: com.github.vermilion10.milea.data.model.DistanceUnit = com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
    suggestedOdometer: Long? = null,
    onDismiss: () -> Unit,
    onSave: (Trip) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isEditing = trip != null
    var distance by remember { mutableStateOf(if (isEditing) trip.distance.toString() else "") }
    var durationMin by remember {
        mutableStateOf(if (isEditing) (trip.duration / 60000).toString() else "")
    }
    var category by remember { mutableStateOf(trip?.category ?: TripCategory.COMMUTE) }
    var note by remember { mutableStateOf(trip?.note ?: "") }
    // Prefers the trip's own recorded reading when editing; otherwise falls
    // back to the same best-known-odometer estimate used for tracked trips.
    // Optional -- leave blank if you don't want this trip in the odometer chain.
    var odometer by remember {
        mutableStateOf(
            (trip?.startOdometer ?: suggestedOdometer)?.let {
                Math.round(com.github.vermilion10.milea.util.Units.distance(it.toFloat(), unit)).toString()
            } ?: ""
        )
    }

    val distanceLabel = com.github.vermilion10.milea.util.Units.distanceLabel(unit)

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = trip?.startTime ?: System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember {
        mutableStateOf(trip?.startTime ?: System.currentTimeMillis())
    }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Trip" else "Add Trip") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dateFormat.format(Date(selectedDate)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Pick Date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = distance,
                    onValueChange = { distance = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Distance ($distanceLabel) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter { c -> c.isDigit() } },
                    label = { Text("Odometer at start ($distanceLabel, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = durationMin,
                    onValueChange = { durationMin = it.filter { c -> c.isDigit() } },
                    label = { Text("Duration (minutes) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = category.name.lowercase().replaceFirstChar { it.titlecase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        TripCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name.lowercase().replaceFirstChar { it.titlecase() }) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val durationMillis = (durationMin.toLongOrNull() ?: 0) * 60000
                    val distanceValue = distance.toFloatOrNull() ?: 0f
                    val odometerDisplay = odometer.toFloatOrNull()
                    val startOdometerKm = odometerDisplay?.let {
                        if (unit == com.github.vermilion10.milea.data.model.DistanceUnit.MILES) {
                            Math.round(it / 0.621371f).toLong()
                        } else {
                            it.toLong()
                        }
                    }
                    // distance is entered/stored in the vehicle's own unit here, but
                    // Trip.distance is stored in km elsewhere in the app -- convert
                    // so the odometer chain and other screens stay consistent.
                    val distanceKm = if (unit == com.github.vermilion10.milea.data.model.DistanceUnit.MILES) {
                        distanceValue * 1.609344f
                    } else {
                        distanceValue
                    }
                    val endOdometerKm = startOdometerKm?.let { it + Math.round(distanceKm) }
                    val base = trip ?: Trip(vehicleId = vehicleId, startTime = selectedDate)
                    onSave(
                        base.copy(
                            vehicleId = if (isEditing) trip.vehicleId else vehicleId,
                            startTime = selectedDate,
                            endTime = selectedDate + durationMillis,
                            startOdometer = startOdometerKm,
                            endOdometer = endOdometerKm,
                            distance = distanceKm,
                            duration = durationMillis,
                            movingTime = durationMillis,
                            idleTime = 0,
                            averageSpeed = if (durationMillis > 0) {
                                distanceKm / (durationMillis / 1000f)
                            } else 0f,
                            category = category,
                            note = note.ifBlank { null },
                            isAutoDetected = isEditing && trip.isAutoDetected
                        )
                    )
                },
                enabled = distance.isNotBlank() && durationMin.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = it
                                set(Calendar.HOUR_OF_DAY, 12)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            selectedDate = cal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun hasTrackingPermissions(context: Context): Boolean {
    val fineLocation = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return fineLocation && notifications
}

private fun startTracking(context: Context, vehicleId: Long, startOdometer: Long? = null) {
    val intent = Intent(context, TripTrackingService::class.java).apply {
        action = TripTrackingService.ACTION_START_TRACKING
        putExtra(TripTrackingService.EXTRA_VEHICLE_ID, vehicleId)
        if (startOdometer != null) {
            putExtra(TripTrackingService.EXTRA_START_ODOMETER, startOdometer)
        }
    }
    ContextCompat.startForegroundService(context, intent)
}
