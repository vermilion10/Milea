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
import com.github.vermilion10.milea.data.model.TripPoint
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import com.github.vermilion10.milea.util.Units
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Details") },
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
        state.trip?.let { trip ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TripMap(
                    points = state.tripPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                .format(Date(trip.startTime)),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        AssistChip(
                            onClick = { },
                            label = { Text(trip.category.name.lowercase().replaceFirstChar { it.titlecase() }) }
                        )
                    }
                    state.vehicleName?.let { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripDetailStat(
                            label = "Distance",
                            value = Units.formatDistance(trip.distance, state.unit),
                            modifier = Modifier.weight(1f)
                        )
                        TripDetailStat(
                            label = "Duration",
                            value = formatDuration(trip.duration),
                            modifier = Modifier.weight(1f)
                        )
                        TripDetailStat(
                            label = "Avg Speed",
                            value = Units.formatSpeed(trip.averageSpeed * 3.6f, state.unit),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripDetailStat(
                            label = "Max Speed",
                            value = Units.formatSpeed(trip.maxSpeed * 3.6f, state.unit),
                            modifier = Modifier.weight(1f)
                        )
                        TripDetailStat(
                            label = "Moving",
                            value = formatDuration(trip.movingTime),
                            modifier = Modifier.weight(1f)
                        )
                        TripDetailStat(
                            label = "Idle",
                            value = formatDuration(trip.idleTime),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    trip.note?.let { note ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripMap(
    points: List<TripPoint>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                    val route = Polyline().apply {
                        setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.apply {
                            color = 0xFF1565C0.toInt()
                            strokeWidth = 6f
                            isAntiAlias = true
                        }
                    }
                    view.overlays.add(route)

                    val north = points.maxOf { it.latitude }
                    val south = points.minOf { it.latitude }
                    val east = points.maxOf { it.longitude }
                    val west = points.minOf { it.longitude }
                    val boundingBox = org.osmdroid.util.BoundingBox(north, east, south, west)
                    // Same layout-timing issue as above -- defer until the view has a
                    // real size, otherwise this is a no-op and the map stays zoomed out.
                    view.post {
                        view.zoomToBoundingBox(boundingBox, true, 64)
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

@Composable
fun TripDetailStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
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

private fun formatDuration(millis: Long): String {
    val hours = millis / 3600000
    val minutes = (millis % 3600000) / 60000
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
