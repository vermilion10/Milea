package com.github.vermilion10.milea.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object TripControl {
    fun start(context: Context, vehicleId: Long, startOdometerKm: Long?) {
        val intent = Intent(context, TripTrackingService::class.java).apply {
            action = TripTrackingService.ACTION_START_TRACKING
            putExtra(TripTrackingService.EXTRA_VEHICLE_ID, vehicleId)
            if (startOdometerKm != null) {
                putExtra(TripTrackingService.EXTRA_START_ODOMETER, startOdometerKm)
            }
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, TripTrackingService::class.java).apply {
            action = TripTrackingService.ACTION_STOP_TRACKING
        }
        // Plain startService: the service is already in the foreground, and
        // startForegroundService would demand a new startForeground() call.
        context.startService(intent)
    }
}
