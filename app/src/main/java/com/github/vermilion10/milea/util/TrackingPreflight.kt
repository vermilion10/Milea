package com.github.vermilion10.milea.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Device conditions that stop or degrade GPS trip recording. */
data class PreflightResult(
    val locationOff: Boolean,
    val powerSaver: Boolean,
    val batteryOptimized: Boolean,
    val approximateOnly: Boolean
) {
    /** Recording can't work at all until this is fixed. */
    val isBlocked: Boolean get() = locationOff
    val hasWarnings: Boolean get() = powerSaver || batteryOptimized || approximateOnly
    val isClear: Boolean get() = !isBlocked && !hasWarnings
}

object TrackingPreflight {
    fun check(context: Context): PreflightResult {
        val location = context.getSystemService(LocationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return PreflightResult(
            locationOff = location?.isLocationEnabled == false,
            powerSaver = power?.isPowerSaveMode == true,
            batteryOptimized = power?.isIgnoringBatteryOptimizations(context.packageName) == false,
            approximateOnly = coarse && !fine
        )
    }

    fun locationSettingsIntent() = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    fun batterySaverIntent() = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)

    @SuppressLint("BatteryLife")
    fun batteryOptimizationIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    fun appSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
}
