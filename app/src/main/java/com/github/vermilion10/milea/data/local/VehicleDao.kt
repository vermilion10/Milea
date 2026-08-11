package com.github.vermilion10.milea.data.local

import androidx.room.*
import com.github.vermilion10.milea.data.model.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicleById(id: Long): Vehicle?

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun getVehicleByIdFlow(id: Long): Flow<Vehicle?>

    @Query("SELECT * FROM vehicles WHERE isSelected = 1 LIMIT 1")
    fun getSelectedVehicleFlow(): Flow<Vehicle?>

    @Query("SELECT * FROM vehicles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedVehicleOnce(): Vehicle?

    @Query("SELECT id FROM vehicles WHERE isActive = 1 ORDER BY name ASC LIMIT 1")
    suspend fun getFirstActiveVehicleId(): Long?

    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    suspend fun getAllVehiclesSync(): List<Vehicle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllVehicles(vehicles: List<Vehicle>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    // Archiving no longer touches selection state for every OTHER vehicle --
    // it only ever affects the one vehicle being archived.
    @Query("UPDATE vehicles SET isActive = 0, isSelected = 0 WHERE id = :id")
    suspend fun archiveVehicle(id: Long)

    @Query("UPDATE vehicles SET isActive = 1 WHERE id = :id")
    suspend fun restoreVehicle(id: Long)

    @Query("UPDATE vehicles SET isSelected = 0")
    suspend fun deselectAllVehicles()

    @Query("UPDATE vehicles SET isSelected = 1 WHERE id = :id")
    suspend fun selectVehicle(id: Long)
}
