package com.github.vermilion10.milea.ui.screens.tripdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.averageSpeedMps
import com.github.vermilion10.milea.data.model.TripPoint
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import com.github.vermilion10.milea.util.Units
import com.github.vermilion10.milea.ui.components.*
import com.github.vermilion10.milea.ui.screens.poster.PosterContent
import com.github.vermilion10.milea.ui.screens.poster.TripPosterDialog
import com.github.vermilion10.milea.ui.screens.poster.posterTitle
import com.github.vermilion10.milea.ui.screens.trip.icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class TripDetailState(
    val trip: Trip? = null,
    val tripPoints: List<TripPoint> = emptyList(),
    val unit: DistanceUnit = DistanceUnit.KILOMETERS,
    val vehicleName: String? = null
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {
    private val _tripId = MutableStateFlow<Long?>(null)
    val tripId = _tripId.asStateFlow()

    fun setTripId(id: Long) {
        _tripId.value = id
    }

    val state = _tripId
        .filterNotNull()
        .flatMapLatest { tripId ->
            val tripFlow = tripRepository.getTripByIdFlow(tripId).filterNotNull()
            val pointsFlow = tripRepository.getTripPointsForTrip(tripId)
            combine(tripFlow, pointsFlow) { trip, points ->
                val vehicle = vehicleRepository.getVehicleById(trip.vehicleId)
                TripDetailState(
                    trip = trip,
                    tripPoints = points,
                    unit = vehicle?.odometerUnit ?: DistanceUnit.KILOMETERS,
                    vehicleName = vehicle?.name
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, TripDetailState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    onBack: (() -> Unit)? = null,
    viewModel: TripDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(tripId) {
        viewModel.setTripId(tripId)
    }

    val state by viewModel.state.collectAsState()
    var showPoster by remember { mutableStateOf(false) }
    var colorBySpeed by remember { mutableStateOf(true) }
    var highlight by remember { mutableStateOf<Int?>(null) }
    var trackChart by remember { mutableStateOf("speed") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.trip?.let { posterTitle(it) } ?: "Trip") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (state.trip != null) {
                        IconButton(onClick = { showPoster = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Share trip")
                        }
                    }
                }
            )
        }
    ) { padding ->
        state.trip?.let { trip ->
            val unit = state.unit
            val points = state.tripPoints
            val speeds = remember(points) { bucketed(points) { i -> if (i == 0) null else segmentSpeedKmh(points, i) } }
            val altitudes = remember(points) { bucketed(points) { i -> points[i].altitude?.toFloat() } }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.tripPoints.isNotEmpty()) {
                    val bands = remember(unit) { speedBands(unit) }
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TripMap(
                            points = state.tripPoints,
                            unit = unit,
                            colorBySpeed = colorBySpeed,
                            highlightIndex = highlight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = colorBySpeed,
                                onClick = { colorBySpeed = true },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("Color by speed") }
                            SegmentedButton(
                                selected = !colorBySpeed,
                                onClick = { colorBySpeed = false },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("Plain route") }
                        }
                        if (colorBySpeed) SpeedLegend(bands)
                        if (speeds.isNotEmpty()) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = trackChart == "speed",
                                        onClick = { trackChart = "speed" },
                                        label = { Text("Speed") }
                                    )
                                    if (altitudes.isNotEmpty()) {
                                        FilterChip(
                                            selected = trackChart == "elevation",
                                            onClick = { trackChart = "elevation" },
                                            label = { Text("Elevation") }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                if (trackChart == "elevation" && altitudes.isNotEmpty()) {
                                    LineChart(
                                        labels = altitudes.map { formatTime(it.time) },
                                        values = altitudes.map { it.value },
                                        color = MaterialTheme.colorScheme.tertiary,
                                        valueFormatter = { String.format(Locale.getDefault(), "%.0f m", it) },
                                        axisFormatter = { String.format(Locale.getDefault(), "%.0f", it) },
                                        readoutTitle = { i -> "${formatTime(altitudes[i].time)} \u00B7 altitude" },
                                        height = 160.dp,
                                        onSelect = { highlight = altitudes[it].pointIndex }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Climb ${String.format(Locale.getDefault(), "%.0f m", elevationGain(altitudes))} \u00B7 " +
                                            "range ${String.format(Locale.getDefault(), "%.0f\u2013%.0f m", altitudes.minOf { it.value }, altitudes.maxOf { it.value })}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LineChart(
                                        labels = speeds.map { formatTime(it.time) },
                                        values = speeds.map { Units.speed(it.value, unit) },
                                        color = MaterialTheme.colorScheme.primary,
                                        valueFormatter = { String.format(Locale.getDefault(), "%.0f %s", it, Units.speedLabel(unit)) },
                                        axisFormatter = { String.format(Locale.getDefault(), "%.0f", it) },
                                        readoutTitle = { i -> "${formatTime(speeds[i].time)} \u00B7 speed" },
                                        includeZero = true,
                                        height = 160.dp,
                                        onSelect = { highlight = speeds[it].pointIndex }
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tap or drag the chart to pin that moment on the map",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${formatDate(trip.startTime)}, ${formatTime(trip.startTime)}" +
                            (trip.endTime?.let { " – ${formatTime(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { },
                            label = { Text(trip.category.label()) },
                            leadingIcon = { Icon(trip.category.icon(), contentDescription = null, Modifier.size(18.dp)) }
                        )
                        state.vehicleName?.let { name ->
                            AssistChip(
                                onClick = { },
                                label = { Text(name) },
                                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, Modifier.size(18.dp)) }
                            )
                        }
                    }

                    StatTile(
                        label = "Distance",
                        value = Units.formatDistance(trip.distance, unit),
                        icon = Icons.Default.Route,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    StatGrid(
                        listOf(
                            { m -> StatTile("Duration", formatDuration(trip.duration), m, icon = Icons.Default.Schedule) },
                            { m -> StatTile("Avg speed", Units.formatSpeed(trip.averageSpeedMps * 3.6f, unit), m, icon = Icons.Default.Speed) },
                            { m -> StatTile("Moving", formatDuration(trip.movingTime), m) },
                            { m -> StatTile("Idle", formatDuration(trip.idleTime), m) },
                            { m ->
                                StatTile(
                                    "Max speed",
                                    if (trip.maxSpeed > 0f) Units.formatSpeed(trip.maxSpeed * 3.6f, unit) else "--",
                                    m
                                )
                            },
                            { m ->
                                StatTile(
                                    "Odometer",
                                    trip.endOdometer?.let { Units.formatOdometer(it, unit) } ?: "--",
                                    m
                                )
                            }
                        )
                    )

                    if (speeds.isNotEmpty()) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Time in speed zones", style = MaterialTheme.typography.titleMedium)
                                SpeedZones(points, speedBands(unit), unit)
                            }
                        }
                    }

                    trip.note?.let { note ->
                        StatTile(label = "Note", value = note, modifier = Modifier.fillMaxWidth())
                    }

                    FilledTonalButton(
                        onClick = { showPoster = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create trip poster")
                    }
                }
            }

            if (showPoster) {
                TripPosterDialog(
                    content = PosterContent(trip, state.tripPoints, unit, state.vehicleName),
                    onDismiss = { showPoster = false }
                )
            }
        }
    }
}

@Composable
fun TripMap(
    points: List<TripPoint>,
    unit: DistanceUnit,
    colorBySpeed: Boolean,
    highlightIndex: Int?,
    modifier: Modifier = Modifier
) {
    val bands = remember(unit) { speedBands(unit) }
    val speedLines = remember(points, bands) { speedPolylines(points, bands, 10f) }
    var zoomed by remember(points) { mutableStateOf(false) }
    val context = LocalContext.current
    // Map tiles are light in both themes, so pick the darker of primary /
    // inversePrimary to keep the route readable on them.
    val routeColor = with(MaterialTheme.colorScheme) {
        if (primary.luminance() > 0.5f) inversePrimary else primary
    }.toArgb()
    var isOffline by remember { mutableStateOf(!isNetworkAvailable(context)) }

    // Route recording (GPS points) and map tiles are independent -- the route
    // itself is always drawn from locally stored points regardless of
    // connectivity. This banner only tells you WHY the tile background might
    // be blank, so it's not silently ambiguous when offline.
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                isOffline = false
            }
            override fun onLost(network: android.net.Network) {
                isOffline = !isNetworkAvailable(context)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.overlays.clear()
                if (points.size < 2) {
                    if (points.isNotEmpty()) {
                        val point = GeoPoint(points.first().latitude, points.first().longitude)
                        // Deferred with post() -- calling this immediately (during the
                        // very first update pass) runs before the view has been through
                        // a layout pass, so it has zero width/height and silently falls
                        // back to the default world view instead of actually centering.
                        view.post {
                            view.controller.setCenter(point)
                            view.controller.setZoom(15.0)
                        }
                    }
                } else {
                    val geo = points.map { GeoPoint(it.latitude, it.longitude) }
                    view.overlays.add(polyline(geo, android.graphics.Color.WHITE, 16f))
                    if (colorBySpeed) view.overlays.addAll(speedLines)
                    else view.overlays.add(polyline(geo, routeColor, 10f))
                    view.overlays.add(DotOverlay(geo.first(), android.graphics.Color.WHITE, 0xFF2E7D32.toInt(), 12f))
                    view.overlays.add(DotOverlay(geo.last(), 0xFF212121.toInt(), android.graphics.Color.WHITE, 12f))
                    highlightIndex?.let { idx ->
                        view.overlays.add(DotOverlay(geo[idx.coerceIn(0, geo.lastIndex)], routeColor, android.graphics.Color.WHITE, 16f))
                    }

                    val north = points.maxOf { it.latitude }
                    val south = points.minOf { it.latitude }
                    val east = points.maxOf { it.longitude }
                    val west = points.minOf { it.longitude }
                    val boundingBox = org.osmdroid.util.BoundingBox(north, east, south, west)
                    // Same layout-timing issue as above -- defer until the view has a
                    // real size, otherwise this is a no-op and the map stays zoomed out.
                    // Only on first show; later updates (e.g. a chart selection)
                    // must not reset the user's pan and zoom.
                    if (!zoomed) {
                        zoomed = true
                        view.post {
                            view.zoomToBoundingBox(boundingBox, true, 64)
                        }
                    }
                }
                view.invalidate()
            }
        )

        if (isOffline) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.small,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Offline \u2014 showing route without map",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun isNetworkAvailable(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
