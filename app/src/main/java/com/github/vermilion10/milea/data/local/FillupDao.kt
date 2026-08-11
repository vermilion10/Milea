package com.github.vermilion10.milea.data.local

import androidx.room.*
import com.github.vermilion10.milea.data.model.Fillup
import kotlinx.coroutines.flow.Flow

@Dao
interface FillupDao {
    @Query("SELECT * FROM fillups WHERE vehicleId = :vehicleId ORDER BY date DESC, odometer DESC")
    fun getFillupsByVehicle(vehicleId: Long): Flow<List<Fillup>>

    @Query("SELECT * FROM fillups WHERE vehicleId = :vehicleId AND date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getFillupsByVehicleAndDateRange(vehicleId: Long, startTime: Long, endTime: Long): Flow<List<Fillup>>

    @Query("SELECT * FROM fillups WHERE id = :id")
    suspend fun getFillupById(id: Long): Fillup?

    @Query("SELECT * FROM fillups WHERE id = :id")
    fun getFillupByIdFlow(id: Long): Flow<Fillup?>

    @Query("SELECT * FROM fillups WHERE vehicleId = :vehicleId AND isFullTank = 1 ORDER BY odometer DESC")
    fun getFullTankFillupsByVehicle(vehicleId: Long): Flow<List<Fillup>>

    @Query("SELECT * FROM fillups WHERE vehicleId = :vehicleId AND isFullTank = 1 ORDER BY odometer ASC")
    suspend fun getFullTankFillupsByVehicleSync(vehicleId: Long): List<Fillup>

    @Query("SELECT * FROM fillups WHERE vehicleId = :vehicleId ORDER BY odometer DESC LIMIT 2")
    suspend fun getLastTwoFillups(vehicleId: Long): List<Fillup>

    @Query("SELECT * FROM fillups WHERE vehicleId = :vehicleId ORDER BY odometer DESC LIMIT 1")
    suspend fun getLatestFillup(vehicleId: Long): Fillup?

    @Query("SELECT * FROM fillups WHERE vehicleId = :vehicleId ORDER BY odometer DESC LIMIT 1")
    fun getLatestFillupFlow(vehicleId: Long): Flow<Fillup?>

    @Query("SELECT SUM(totalCost) FROM fillups WHERE vehicleId = :vehicleId")
    fun getTotalFuelCostForVehicle(vehicleId: Long): Flow<Float?>

    @Query("SELECT SUM(liters) FROM fillups WHERE vehicleId = :vehicleId")
    fun getTotalLitersForVehicle(vehicleId: Long): Flow<Float?>

    @Query("SELECT * FROM fillups ORDER BY date ASC")
    suspend fun getAllFillupsSync(): List<Fillup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFillups(fillups: List<Fillup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFillup(fillup: Fillup): Long

    @Update
    suspend fun updateFillup(fillup: Fillup)

    @Delete
    suspend fun deleteFillup(fillup: Fillup)

    @Query("DELETE FROM fillups WHERE vehicleId = :vehicleId")
    suspend fun deleteFillupsByVehicle(vehicleId: Long)
}
