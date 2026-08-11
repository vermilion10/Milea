package com.github.vermilion10.milea.data.local

import androidx.room.*
import com.github.vermilion10.milea.data.model.TripPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface TripPointDao {
    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getTripPointsForTrip(tripId: Long): Flow<List<TripPoint>>

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getTripPointsForTripSync(tripId: Long): List<TripPoint>

    @Query("SELECT * FROM trip_points ORDER BY timestamp ASC")
    suspend fun getAllTripPointsSync(): List<TripPoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripPoint(tripPoint: TripPoint): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripPoints(tripPoints: List<TripPoint>)

    @Delete
    suspend fun deleteTripPoint(tripPoint: TripPoint)

    @Query("DELETE FROM trip_points WHERE tripId = :tripId")
    suspend fun deleteTripPointsForTrip(tripId: Long)

    @Query("SELECT COUNT(*) FROM trip_points WHERE tripId = :tripId")
    suspend fun getTripPointCount(tripId: Long): Int
}
