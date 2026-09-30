package com.github.vermilion10.milea.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.vermilion10.milea.util.PreflightResult
import com.github.vermilion10.milea.util.TrackingPreflight
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/**
 * Runs everything that has to happen before GPS recording starts: runtime
 * permissions, then device checks (location on, battery saver, battery
 * optimization, precise location). Blocking problems stop the start; the rest
 * are shown as warnings the user can fix in place or skip.
 *
 * Returns a function to call from a "Start trip" button.
 */
@Composable
fun rememberTripStarter(onStart: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnStart by rememberUpdatedState(onStart)
    var preflight by remember { mutableStateOf<PreflightResult?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    fun evaluate() {
        val result = TrackingPreflight.check(context)
        if (result.isClear) {
            preflight = null
            currentOnStart()
        } else {
            preflight = result
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val anyLocation = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasLocationPermission(context)
        if (anyLocation) evaluate() else permissionDenied = true
    }

    // Google Play services shows the in-place "Turn on location?" sheet, so the
    // user doesn't have to hunt through system settings.
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { if (preflight != null) evaluate() }

    // Returning from a settings screen: re-check, and start automatically if
    // the user fixed everything.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (preflight != null) evaluate()
    }

    preflight?.let { result ->
        TrackingPreflightDialog(
            result = result,
            onTurnOnLocation = {
                requestLocationOn(
                    context = context,
                    onResolution = { request -> resolutionLauncher.launch(request) },
                    onAlreadyOn = { evaluate() }
                )
            },
            onFixBatterySaver = { context.startActivity(TrackingPreflight.batterySaverIntent()) },
            onFixBatteryOptimization = {
                runCatching {
                    context.startActivity(TrackingPreflight.batteryOptimizationIntent(context))
                }
            },
            onFixPrecise = { context.startActivity(TrackingPreflight.appSettingsIntent(context)) },
            onStartAnyway = {
                preflight = null
                currentOnStart()
            },
            onDismiss = { preflight = null }
        )
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            icon = { Icon(Icons.Default.LocationOff, contentDescription = null) },
            title = { Text("Location permission needed") },
            text = {
                Text("Milea records your route and distance with GPS. Allow location access in app settings to start trips.")
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionDenied = false
                    context.startActivity(TrackingPreflight.appSettingsIntent(context))
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { permissionDenied = false }) { Text("Not now") }
            }
        )
    }

    return remember {
        {
            if (hasLocationPermission(context) && hasNotificationPermission(context)) {
                evaluate()
            } else {
                permissionLauncher.launch(
                    buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                )
            }
        }
    }
}

@Composable
private fun TrackingPreflightDialog(
    result: PreflightResult,
    onTurnOnLocation: () -> Unit,
    onFixBatterySaver: () -> Unit,
    onFixBatteryOptimization: () -> Unit,
    onFixPrecise: () -> Unit,
    onStartAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    if (result.isBlocked) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.LocationOff, contentDescription = null) },
            title = { Text("Location is off") },
            text = {
                Text(
                    "Trips are recorded with GPS, so nothing can be tracked while location is turned off. Turn it on to start this trip."
                )
            },
            confirmButton = {
                Button(onClick = onTurnOnLocation) { Text("Turn on location") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.BatteryAlert, contentDescription = null) },
        title = { Text("Recording may be unreliable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your phone may pause GPS in the background, which leaves gaps in the route and undercounts distance.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (result.powerSaver) {
                    PreflightIssue(
                        icon = Icons.Default.BatterySaver,
                        title = "Power saving is on",
                        detail = "Limits GPS updates while the screen is off.",
                        onFix = onFixBatterySaver
                    )
                }
                if (result.batteryOptimized) {
                    PreflightIssue(
                        icon = Icons.Default.BatteryAlert,
                        title = "Battery optimization",
                        detail = "The system may stop Milea during long trips.",
                        onFix = onFixBatteryOptimization
                    )
                }
                if (result.approximateOnly) {
                    PreflightIssue(
                        icon = Icons.Default.LocationSearching,
                        title = "Approximate location only",
                        detail = "Allow precise location for accurate distance.",
                        onFix = onFixPrecise
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onStartAnyway) {
                Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PreflightIssue(
    icon: ImageVector,
    title: String,
    detail: String,
    onFix: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onFix) { Text("Fix") }
        }
    }
}

private fun requestLocationOn(
    context: Context,
    onResolution: (IntentSenderRequest) -> Unit,
    onAlreadyOn: () -> Unit
) {
    val activity = context.findActivity()
    if (activity == null) {
        context.startActivity(TrackingPreflight.locationSettingsIntent())
        return
    }
    val request = LocationSettingsRequest.Builder()
        .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build())
        .setAlwaysShow(true)
        .build()
    LocationServices.getSettingsClient(activity)
        .checkLocationSettings(request)
        .addOnSuccessListener { onAlreadyOn() }
        .addOnFailureListener { error ->
            if (error is ResolvableApiException) {
                onResolution(IntentSenderRequest.Builder(error.resolution).build())
            } else {
                context.startActivity(TrackingPreflight.locationSettingsIntent())
            }
        }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
