package com.github.vermilion10.milea.data.repository

import com.github.vermilion10.milea.data.local.TripDao
import com.github.vermilion10.milea.data.local.TripPointDao
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.TripCategory
import com.github.vermilion10.milea.data.model.TripPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val tripPointDao: TripPointDao
) {
    fun getTripsByVehicle(vehicleId: Long): Flow<List<Trip>> = 
        tripDao.getTripsByVehicle(vehicleId)

    fun getTripsByVehicleAndDateRange(vehicleId: Long, startTime: Long, endTime: Long): Flow<List<Trip>> =
        tripDao.getTripsByVehicleAndDateRange(vehicleId, startTime, endTime)

    fun getTripsByVehicleAndCategory(vehicleId: Long, category: TripCategory): Flow<List<Trip>> =
        tripDao.getTripsByVehicleAndCategory(vehicleId, category)

    suspend fun getTripById(id: Long): Trip? = tripDao.getTripById(id)

    fun getTripByIdFlow(id: Long): Flow<Trip?> = tripDao.getTripByIdFlow(id)

    fun getTotalDistanceForVehicle(vehicleId: Long): Flow<Float?> = 
        tripDao.getTotalDistanceForVehicle(vehicleId)

    fun getTotalDurationForVehicle(vehicleId: Long): Flow<Long?> = 
        tripDao.getTotalDurationForVehicle(vehicleId)

    fun getTripCountForVehicle(vehicleId: Long): Flow<Int> = 
        tripDao.getTripCountForVehicle(vehicleId)

    suspend fun getLatestTrip(vehicleId: Long): Trip? = tripDao.getLatestTrip(vehicleId)

    fun getMaxOdometerFlow(vehicleId: Long): Flow<Long?> = tripDao.getMaxOdometerFlow(vehicleId)

    suspend fun getMaxOdometerSync(vehicleId: Long): Long? = tripDao.getMaxOdometerSync(vehicleId)

    suspend fun getAllTripsSync(): List<Trip> = tripDao.getAllTripsSync()

    suspend fun insertAllTrips(trips: List<Trip>) = tripDao.insertAllTrips(trips)

    suspend fun insertTrip(trip: Trip): Long = tripDao.insertTrip(trip)

    suspend fun updateTrip(trip: Trip) = tripDao.updateTrip(trip)

    suspend fun deleteTrip(trip: Trip) {
        tripPointDao.deleteTripPointsForTrip(trip.id)
        tripDao.deleteTrip(trip)
    }

    fun getTripPointsForTrip(tripId: Long): Flow<List<TripPoint>> = 
        tripPointDao.getTripPointsForTrip(tripId)

    suspend fun getTripPointsForTripSync(tripId: Long): List<TripPoint> = 
        tripPointDao.getTripPointsForTripSync(tripId)

    suspend fun getAllTripPointsSync(): List<TripPoint> = tripPointDao.getAllTripPointsSync()

    suspend fun insertTripPoint(tripPoint: TripPoint): Long = 
        tripPointDao.insertTripPoint(tripPoint)

    suspend fun insertTripPoints(tripPoints: List<TripPoint>) = 
        tripPointDao.insertTripPoints(tripPoints)
}
