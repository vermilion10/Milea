package com.github.vermilion10.milea.data.local

import androidx.room.*
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.TripCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId ORDER BY startTime DESC")
    fun getTripsByVehicle(vehicleId: Long): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId AND startTime >= :startTime AND endTime <= :endTime ORDER BY startTime DESC")
    fun getTripsByVehicleAndDateRange(vehicleId: Long, startTime: Long, endTime: Long): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId AND category = :category ORDER BY startTime DESC")
    fun getTripsByVehicleAndCategory(vehicleId: Long, category: TripCategory): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE id = :id")
    fun getTripByIdFlow(id: Long): Flow<Trip?>

    @Query("SELECT SUM(distance) FROM trips WHERE vehicleId = :vehicleId")
    fun getTotalDistanceForVehicle(vehicleId: Long): Flow<Float?>

    @Query("SELECT SUM(duration) FROM trips WHERE vehicleId = :vehicleId")
    fun getTotalDurationForVehicle(vehicleId: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM trips WHERE vehicleId = :vehicleId")
    fun getTripCountForVehicle(vehicleId: Long): Flow<Int>

    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestTrip(vehicleId: Long): Trip?

    // Highest odometer reading recorded across ALL trips for this vehicle, not
    // just the most recently STARTED one. The most recent trip by time can
    // easily have no odometer at all (still in progress, or left blank on a
    // manual entry) while an earlier trip has a perfectly valid higher one --
    // picking only the latest trip silently threw that away.
    @Query("SELECT MAX(COALESCE(endOdometer, startOdometer)) FROM trips WHERE vehicleId = :vehicleId")
    fun getMaxOdometerFlow(vehicleId: Long): Flow<Long?>

    @Query("SELECT MAX(COALESCE(endOdometer, startOdometer)) FROM trips WHERE vehicleId = :vehicleId")
    suspend fun getMaxOdometerSync(vehicleId: Long): Long?

    @Query("SELECT * FROM trips ORDER BY startTime ASC")
    suspend fun getAllTripsSync(): List<Trip>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTrips(trips: List<Trip>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)

    @Query("DELETE FROM trips WHERE vehicleId = :vehicleId")
    suspend fun deleteTripsByVehicle(vehicleId: Long)
}
