package com.github.vermilion10.milea.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.github.vermilion10.milea.MainActivity
import com.github.vermilion10.milea.R
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.TripPoint
import com.github.vermilion10.milea.data.repository.TripRepository
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class TripTrackingService : Service() {

    @Inject
    lateinit var tripRepository: TripRepository

    private val binder = LocalBinder()
    private var currentTripId: Long? = null
    private var vehicleId: Long? = null
    private var isTracking = false
    private var isMonitoring = false
    private var isAutoStarted = false
    private var tripStartTime: Long = 0
    private var tripStartOdometer: Long? = null
    private val tripPoints = mutableListOf<TripPoint>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var prefs: SharedPreferences

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _distance = MutableStateFlow(0f)
    val distance = _distance.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed = _currentSpeed.asStateFlow()

    private val _maxSpeed = MutableStateFlow(0f)
    val maxSpeed = _maxSpeed.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoringFlow = _isMonitoring.asStateFlow()

    private var lastLocation: Location? = null
    private var movingTime: Long = 0
    private var lastMovingTime: Long = 0
    private var lastMovementTimestamp: Long = 0
    private var lastAutoStopTime: Long = 0

    companion object {
        const val CHANNEL_ID = "trip_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val MONITOR_NOTIFICATION_ID = 1002

        const val ACTION_START_TRACKING = "com.github.vermilion10.milea.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.github.vermilion10.milea.STOP_TRACKING"
        const val ACTION_START_MONITORING = "com.github.vermilion10.milea.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.github.vermilion10.milea.STOP_MONITORING"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
        const val EXTRA_START_ODOMETER = "start_odometer"

        const val PREF_NAME = "trip_tracking_state"
        const val PREF_IS_TRACKING = "is_tracking"
        const val PREF_IS_MONITORING = "is_monitoring"
        const val PREF_VEHICLE_ID = "vehicle_id"
        const val PREF_TRIP_ID = "trip_id"
        const val PREF_START_TIME = "start_time"
        const val PREF_DISTANCE = "distance"
        const val PREF_MOVING_TIME = "moving_time"

        // Auto-detection thresholds
        private const val AUTO_START_SPEED_MPS = 4.0f          // ~14 km/h
        private const val AUTO_START_SAMPLES = 3                // consecutive samples above speed
        private const val IDLE_STOP_TIMEOUT_MS = 3 * 60 * 1000L // 3 minutes of idle (auto-detected trips only)
        private const val MANUAL_IDLE_SAFETY_TIMEOUT_MS = 45 * 60 * 1000L // manual trips: safety net only
        private const val MONITOR_INTERVAL_MS = 10_000L         // 10s sampling while monitoring
    }

    inner class LocalBinder : Binder() {
        fun getService(): TripTrackingService = this@TripTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Restarted by the system after being killed (START_STICKY)
            restoreState()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START_TRACKING -> {
                val vid = intent.getLongExtra(EXTRA_VEHICLE_ID, -1)
                val odometer = intent.getLongExtra(EXTRA_START_ODOMETER, -1)
                if (vid > 0) {
                    if (isMonitoring) {
                        stopMonitoring()
                    }
                    startTracking(vid, if (odometer > 0) odometer else null)
                }
            }
            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
            ACTION_START_MONITORING -> {
                val vid = intent.getLongExtra(EXTRA_VEHICLE_ID, -1)
                if (vid > 0) {
                    startMonitoring(vid)
                }
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                if (!isTracking) {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun restoreState() {
        if (prefs.getBoolean(PREF_IS_TRACKING, false)) {
            val vid = prefs.getLong(PREF_VEHICLE_ID, -1)
            if (vid > 0) {
                vehicleId = vid
                currentTripId = prefs.getLong(PREF_TRIP_ID, -1).takeIf { it > 0 }
                tripStartTime = prefs.getLong(PREF_START_TIME, System.currentTimeMillis())
                _distance.value = prefs.getFloat(PREF_DISTANCE, 0f)
                movingTime = prefs.getLong(PREF_MOVING_TIME, 0)
                isTracking = true
                lastMovingTime = System.currentTimeMillis()
                startLocationUpdates(highAccuracy = true)
                startForeground(NOTIFICATION_ID, createNotification())
                startDurationLoop()
            }
        } else if (prefs.getBoolean(PREF_IS_MONITORING, false)) {
            val vid = prefs.getLong(PREF_VEHICLE_ID, -1)
            if (vid > 0) {
                startMonitoring(vid)
            }
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val speed = location.speed

        if (isMonitoring && !isTracking) {
            // Candidate detection: speed above threshold for N consecutive samples
            if (speed >= AUTO_START_SPEED_MPS) {
                autoStartSamples++
                if (autoStartSamples >= AUTO_START_SAMPLES) {
                    autoStartSamples = 0
                    vehicleId?.let { startTracking(it, isAutoStarted = true) }
                }
            } else {
                autoStartSamples = 0
            }
            return
        }

        if (!isTracking) return

        _currentSpeed.value = speed

        if (speed > _maxSpeed.value) {
            _maxSpeed.value = speed
        }

        val now = System.currentTimeMillis()
        lastLocation?.let { last ->
            val distanceDelta = last.distanceTo(location)
            val elapsedSeconds = ((now - lastMovingTime).coerceAtLeast(200L)) / 1000f
            // The GPS-reported "speed" field is noisy at low speed and in weak-signal
            // areas (slow traffic, urban canyons, cloud cover) and can under-report
            // even while the position is genuinely changing. Falling back to the
            // speed implied by the actual position change stops real movement --
            // e.g. slow city traffic -- from being silently dropped as "idle",
            // which was previously under-recording both distance and moving time.
            val impliedSpeedMps = distanceDelta / elapsedSeconds
            val isMoving = speed > 0.5f || impliedSpeedMps > 1.0f // ~3.6 km/h

            if (distanceDelta < 150f) { // ignore GPS jumps/outlier fixes
                if (isMoving) {
                    _distance.value += distanceDelta
                    movingTime += now - lastMovingTime
                    lastMovementTimestamp = now
                }
            }
        }
        // Always advance the sample clock, moving or not -- otherwise the next
        // "moving" sample after a genuinely idle stretch would wrongly add the
        // whole idle stretch into movingTime.
        lastMovingTime = now

        lastLocation = location

        currentTripId?.let { tripId ->
            val tripPoint = TripPoint(
                tripId = tripId,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = now,
                speed = location.speed,
                altitude = location.altitude,
                accuracy = location.accuracy
            )
            tripPoints.add(tripPoint)

            if (tripPoints.size >= 50) {
                serviceScope.launch {
                    tripRepository.insertTripPoints(tripPoints.toList())
                    tripPoints.clear()
                }
            }
        }
    }

    private var autoStartSamples = 0

    fun startTracking(vehicleId: Long, startOdometer: Long? = null, isAutoStarted: Boolean = false) {
        if (isTracking) return

        this.vehicleId = vehicleId
        this.tripStartOdometer = startOdometer
        this.isTracking = true
        this.isAutoStarted = isAutoStarted
        this.tripStartTime = System.currentTimeMillis()
        this.lastMovingTime = tripStartTime
        this.lastMovementTimestamp = tripStartTime
        this.movingTime = 0
        this._distance.value = 0f
        this._maxSpeed.value = 0f
        this.tripPoints.clear()

        serviceScope.launch {
            val tripId = tripRepository.insertTrip(
                Trip(
                    vehicleId = vehicleId,
                    startTime = tripStartTime,
                    startOdometer = startOdometer,
                    isAutoDetected = isAutoStarted
                )
            )
            currentTripId = tripId
            persistState()
        }

        startLocationUpdates(highAccuracy = true)
        startForeground(NOTIFICATION_ID, createNotification())
        startDurationLoop()
    }

    fun stopTracking(resumeMonitoring: Boolean = false): Long? {
        if (!isTracking) return null

        isTracking = false
        stopLocationUpdates()
        val tripId = currentTripId

        serviceScope.launch {
            runCatching {
                if (tripPoints.isNotEmpty()) {
                    tripRepository.insertTripPoints(tripPoints.toList())
                    tripPoints.clear()
                }

                currentTripId?.let { id ->
                    tripRepository.getTripById(id)?.let { trip ->
                        val distanceKm = _distance.value / 1000f
                        // Carry the odometer forward: end reading = start reading (known
                        // when tracking began) + distance driven this trip, rounded to
                        // whole km/mi. This keeps the odometer continuous trip-to-trip so
                        // the next fill-up entry can be pre-filled/checked against it.
                        val computedEndOdometer = trip.startOdometer?.let { start ->
                            start + Math.round(distanceKm)
                        }
                        tripRepository.updateTrip(
                            trip.copy(
                                endTime = System.currentTimeMillis(),
                                distance = distanceKm,
                                duration = _duration.value,
                                movingTime = movingTime,
                                idleTime = (_duration.value - movingTime).coerceAtLeast(0),
                                averageSpeed = if (_duration.value > 0) _distance.value / (_duration.value / 1000f) else 0f,
                                maxSpeed = _maxSpeed.value,
                                endOdometer = computedEndOdometer
                            )
                        )
                    }
                }
            }

            currentTripId = null

            if (resumeMonitoring && vehicleId != null) {
                // stay running and go back to monitoring
                clearPersistedState()
                isMonitoring = true
                autoStartSamples = 0
                _isMonitoring.value = true
                persistState()
                startLocationUpdates(highAccuracy = false)
                startForeground(MONITOR_NOTIFICATION_ID, createMonitoringNotification())
            } else {
                clearPersistedState()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return tripId
    }

    fun startMonitoring(vehicleId: Long) {
        if (isTracking) return
        this.vehicleId = vehicleId
        this.isMonitoring = true
        autoStartSamples = 0
        _isMonitoring.value = true

        persistState()
        startLocationUpdates(highAccuracy = false)
        startForeground(MONITOR_NOTIFICATION_ID, createMonitoringNotification())
    }

    fun stopMonitoring() {
        isMonitoring = false
        _isMonitoring.value = false
        autoStartSamples = 0
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        clearPersistedState()
    }

    private fun startDurationLoop() {
        serviceScope.launch {
            while (isTracking) {
                _duration.value = System.currentTimeMillis() - tripStartTime

                // Idle auto-stop only ends a trip that was started automatically by
                // the movement monitor -- that flow needs to hand back off to
                // monitoring fairly quickly so it can catch the next trip. A trip
                // YOU started with the "Start Trip" button keeps recording through
                // ordinary stops (traffic, red lights, a quick errand) since those
                // are a normal part of a real trip -- it only auto-stops after a
                // much longer safety-net timeout, in case Stop was genuinely
                // forgotten. This is what previously cut manually-started trips
                // short (and silently stopped recording the rest of the drive)
                // whenever GPS speed misread as "idle" for a few minutes.
                val idleMillis = System.currentTimeMillis() - lastMovementTimestamp
                val idleTimeout = if (isAutoStarted) IDLE_STOP_TIMEOUT_MS else MANUAL_IDLE_SAFETY_TIMEOUT_MS
                if (idleMillis > idleTimeout) {
                    stopTracking(resumeMonitoring = isMonitoring)
                    break
                }
                delay(1000)
            }
        }
    }

    private fun persistState() {
        prefs.edit()
            .putBoolean(PREF_IS_TRACKING, isTracking)
            .putBoolean(PREF_IS_MONITORING, isMonitoring)
            .putLong(PREF_VEHICLE_ID, vehicleId ?: -1)
            .putLong(PREF_TRIP_ID, currentTripId ?: -1)
            .putLong(PREF_START_TIME, tripStartTime)
            .putFloat(PREF_DISTANCE, _distance.value)
            .putLong(PREF_MOVING_TIME, movingTime)
            .apply()
    }

    private fun clearPersistedState() {
        prefs.edit()
            .putBoolean(PREF_IS_TRACKING, false)
            .putBoolean(PREF_IS_MONITORING, false)
            .remove(PREF_VEHICLE_ID)
            .remove(PREF_TRIP_ID)
            .remove(PREF_START_TIME)
            .remove(PREF_DISTANCE)
            .remove(PREF_MOVING_TIME)
            .apply()
    }

    private fun startLocationUpdates(highAccuracy: Boolean) {
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val interval = if (highAccuracy) 2000L else MONITOR_INTERVAL_MS

        val locationRequest = LocationRequest.Builder(priority, interval).apply {
            setMinUpdateDistanceMeters(if (highAccuracy) 5f else 10f)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing trip tracking notification"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Trip")
            .setContentText("Distance: ${String.format("%.2f", _distance.value / 1000)} km")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createMonitoringNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitoring for Trips")
            .setContentText("Auto-detect is watching for movement")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
