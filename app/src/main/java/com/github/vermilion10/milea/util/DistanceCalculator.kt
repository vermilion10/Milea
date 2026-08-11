package com.github.vermilion10.milea.util

import android.location.Location

object DistanceCalculator {
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun calculateTotalDistance(locations: List<Pair<Double, Double>>): Float {
        if (locations.size < 2) return 0f
        
        var totalDistance = 0f
        for (i in 1 until locations.size) {
            val (lat1, lon1) = locations[i - 1]
            val (lat2, lon2) = locations[i]
            totalDistance += calculateDistance(lat1, lon1, lat2, lon2)
        }
        return totalDistance
    }
}
