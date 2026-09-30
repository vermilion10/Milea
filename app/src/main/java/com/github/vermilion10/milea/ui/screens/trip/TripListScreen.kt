package com.github.vermilion10.milea.ui.screens.trip

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.averageSpeedMps
import com.github.vermilion10.milea.data.model.TripCategory
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.domain.VehicleData
import com.github.vermilion10.milea.domain.VehicleDataSource
import com.github.vermilion10.milea.service.TripControl
import com.github.vermilion10.milea.service.TripTrackingService
import com.github.vermilion10.milea.ui.components.*
import com.github.vermilion10.milea.util.TrackingPreflight
import com.github.vermilion10.milea.util.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripListState(
    val data: VehicleData? = null,
    val trips: List<Trip> = emptyList(),
    val filter: TripCategory? = null,
    val loaded: Boolean = false
)

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    vehicleDataSource: VehicleDataSource
) : ViewModel() {
    private val filter = MutableStateFlow<TripCategory?>(null)

    val state = combine(vehicleDataSource.selectedVehicleData(), filter) { data, category ->
        TripListState(
            data = data,
            // The in-progress trip lives in the live card, not the list.
            trips = data?.trips.orEmpty()
                .filter { it.endTime != null }
                .filter { category == null || it.category == category },
            filter = category,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListState())

    val tracking = TripTrackingService.state

    fun setFilter(category: TripCategory?) {
        filter.value = category
    }

    fun save(trip: Trip) {
        viewModelScope.launch {
            if (trip.id == 0L) tripRepository.insertTrip(trip)
            else tripRepository.updateTrip(trip.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete(trip: Trip) {
        viewModelScope.launch { tripRepository.deleteTrip(trip) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    onTripClick: (Long) -> Unit = {},
    onAddVehicle: () -> Unit = {},
    viewModel: TripListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracking by viewModel.tracking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val data = state.data
    val unit = data?.vehicle?.odometerUnit ?: DistanceUnit.KILOMETERS

    var sheetOpen by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<Trip?>(null) }
    var confirmDelete by remember { mutableStateOf<Trip?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val startTrip = rememberTripStarter {
        data?.let { TripControl.start(context, it.vehicle.id, it.currentOdometerKm) }
    }

    // Offer to open the trip that just finished (and from there, share it).
    LaunchedEffect(tracking.lastCompletedTripId) {
        val tripId = tracking.lastCompletedTripId ?: return@LaunchedEffect
        TripTrackingService.consumeCompletedTrip()
        val result = snackbar.showSnackbar("Trip saved", actionLabel = "View", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onTripClick(tripId)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (data != null) {
                        IconButton(onClick = { sheetTarget = null; sheetOpen = true }) {
                            Icon(Icons.Default.EditNote, contentDescription = "Add trip manually")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (data != null && !tracking.isTracking) {
                ExtendedFloatingActionButton(
                    onClick = startTrip,
                    expanded = listState.firstVisibleItemIndex == 0,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text("Start trip") }
                )
            }
        }
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (data == null) {
            NoActiveVehicleMessage(padding, "Add a vehicle first to track trips.", onAddVehicle)
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tracking.isTracking) {
                item(key = "live") {
                    LiveTripCard(
                        state = tracking,
                        unit = unit,
                        onStop = { TripControl.stop(context) },
                        onTurnOnLocation = { context.startActivity(TrackingPreflight.locationSettingsIntent()) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            item(key = "filter") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.filter == null,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text("All") }
                    )
                    TripCategory.entries.forEach { c ->
                        FilterChip(
                            selected = state.filter == c,
                            onClick = { viewModel.setFilter(if (state.filter == c) null else c) },
                            label = { Text(c.label()) },
                            leadingIcon = { Icon(c.icon(), contentDescription = null, Modifier.size(18.dp)) }
                        )
                    }
                }
            }
            if (state.trips.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.Route,
                        title = if (state.filter == null) "No trips yet" else "No ${state.filter!!.label().lowercase()} trips",
                        message = "Tap Start trip when you set off. Milea records the route, distance and time with GPS.",
                        modifier = Modifier.heightIn(min = 360.dp)
                    )
                }
            }
            items(state.trips, key = { it.id }) { trip ->
                TripItem(
                    trip = trip,
                    unit = unit,
                    onClick = { onTripClick(trip.id) },
                    onEdit = { sheetTarget = trip; sheetOpen = true }
                )
            }
        }
    }

    if (sheetOpen && data != null) {
        TripSheet(
            vehicleId = data.vehicle.id,
            existing = sheetTarget,
            unit = unit,
            suggestedOdometerKm = data.currentOdometerKm,
            onDismiss = { sheetOpen = false },
            onSave = { viewModel.save(it); sheetOpen = false },
            onDelete = { confirmDelete = it; sheetOpen = false }
        )
    }

    confirmDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete trip?") },
            text = { Text("The trip and its recorded route will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(trip); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

fun TripCategory.icon(): ImageVector = when (this) {
    TripCategory.COMMUTE -> Icons.Default.Work
    TripCategory.BUSINESS -> Icons.Default.BusinessCenter
    TripCategory.LEISURE -> Icons.Default.Landscape
    TripCategory.OTHER -> Icons.Default.Route
}

@Composable
fun TripItem(
    trip: Trip,
    unit: DistanceUnit,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        trip.category.icon(),
                        contentDescription = trip.category.label(),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    Units.formatDistance(trip.distance, unit),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    buildString {
                        append(formatRelativeDay(trip.startTime))
                        append(", ")
                        append(formatTime(trip.startTime))
                        append(" · ")
                        append(formatDuration(trip.duration))
                        if (trip.averageSpeedMps > 0f) {
                            append(" · ")
                            append(Units.formatSpeed(trip.averageSpeedMps * 3.6f, unit))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (trip.isAutoDetected || trip.note != null) {
                    Text(
                        listOfNotNull(if (trip.isAutoDetected) "Auto-detected" else null, trip.note).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit trip")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripSheet(
    vehicleId: Long,
    existing: Trip?,
    unit: DistanceUnit,
    suggestedOdometerKm: Long?,
    onDismiss: () -> Unit,
    onSave: (Trip) -> Unit,
    onDelete: (Trip) -> Unit
) {
    // The text first shown for each field. Recorded values are rounded for
    // display, so a field only counts as edited if its text changed; otherwise
    // the precise recorded value is kept.
    val initialDistance = remember { existing?.let { Units.distance(it.distance, unit).toInputString(1) } ?: "" }
    val initialMinutes = remember { existing?.let { (it.duration / 60_000).toString() } ?: "" }
    var distance by remember { mutableStateOf(initialDistance) }
    var minutes by remember { mutableStateOf(initialMinutes) }
    var category by remember { mutableStateOf(existing?.category ?: TripCategory.COMMUTE) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val initialOdometer = remember {
        (existing?.startOdometer ?: if (existing == null) suggestedOdometerKm else null)
            ?.let { Math.round(Units.distance(it.toFloat(), unit)).toString() } ?: ""
    }
    var odometer by remember { mutableStateOf(initialOdometer) }
    var date by remember { mutableLongStateOf(existing?.startTime ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val distanceValue = distance.toFloatOrNull()?.takeIf { it > 0f }
    val minutesValue = minutes.toLongOrNull()?.takeIf { it > 0 }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (existing == null) "Add trip" else "Edit trip",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(formatRelativeDay(date)) },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, Modifier.size(18.dp)) }
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label()) },
                        leadingIcon = { Icon(c.icon(), contentDescription = null, Modifier.size(18.dp)) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = distance,
                    onValueChange = { distance = it },
                    label = "Distance",
                    decimals = 1,
                    suffix = Units.distanceLabel(unit),
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    label = "Duration",
                    decimals = 0,
                    suffix = "min",
                    modifier = Modifier.weight(1f)
                )
            }
            NumberField(
                value = odometer,
                onValueChange = { odometer = it },
                label = "Odometer at start (optional)",
                decimals = 0,
                suffix = Units.distanceLabel(unit),
                supportingText = "Keeps the odometer continuous across trips and fill-ups",
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) {
                    TextButton(
                        onClick = { onDelete(existing) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = distanceValue != null && minutesValue != null,
                    onClick = {
                        val base = existing ?: Trip(vehicleId = vehicleId, startTime = date)
                        val distanceEdited = existing == null || distance != initialDistance
                        val durationEdited = existing == null || minutes != initialMinutes
                        val odometerEdited = existing == null || odometer != initialOdometer
                        val distanceKm = if (distanceEdited) Units.distanceToKm(distanceValue!!, unit) else base.distance
                        val durationMs = if (durationEdited) minutesValue!! * 60_000 else base.duration
                        val startOdo = if (odometerEdited) {
                            odometer.toFloatOrNull()?.let { Math.round(Units.distanceToKm(it, unit)).toLong() }
                        } else base.startOdometer
                        // Editing only the category, note or date must never touch
                        // what GPS recorded (moving/idle split, speeds, odometers).
                        val recordedUnchanged = !distanceEdited && !durationEdited
                        onSave(
                            base.copy(
                                startTime = date,
                                endTime = date + durationMs,
                                startOdometer = startOdo,
                                endOdometer = if (!distanceEdited && !odometerEdited) base.endOdometer
                                else startOdo?.let { it + Math.round(distanceKm) },
                                distance = distanceKm,
                                duration = durationMs,
                                movingTime = if (recordedUnchanged) base.movingTime else durationMs,
                                idleTime = if (recordedUnchanged) base.idleTime else 0,
                                averageSpeed = if (recordedUnchanged) base.averageSpeed
                                else distanceKm * 1000f / (durationMs / 1000f),
                                category = category,
                                note = note.trim().ifBlank { null }
                            )
                        )
                    }
                ) { Text("Save") }
            }
        }
    }

    if (showDatePicker) {
        DateTimeKeepingPicker(initial = date, onDismiss = { showDatePicker = false }, onPicked = { date = it })
    }
}
