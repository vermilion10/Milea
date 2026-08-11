package com.github.vermilion10.milea.data.repository

import com.github.vermilion10.milea.data.local.VehicleDao
import com.github.vermilion10.milea.data.model.Vehicle
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao
) {
    fun getAllActiveVehicles(): Flow<List<Vehicle>> = vehicleDao.getAllActiveVehicles()

    fun getAllVehicles(): Flow<List<Vehicle>> = vehicleDao.getAllVehicles()

    suspend fun getVehicleById(id: Long): Vehicle? = vehicleDao.getVehicleById(id)

    fun getVehicleByIdFlow(id: Long): Flow<Vehicle?> = vehicleDao.getVehicleByIdFlow(id)

    // The single vehicle currently shown on the Dashboard / used by Quick
    // Actions -- independent of how many OTHER vehicles exist or are archived.
    fun getSelectedVehicle(): Flow<Vehicle?> = vehicleDao.getSelectedVehicleFlow()

    suspend fun getSelectedVehicleOnce(): Vehicle? = vehicleDao.getSelectedVehicleOnce()

    suspend fun getAllVehiclesSync(): List<Vehicle> = vehicleDao.getAllVehiclesSync()

    suspend fun insertAllVehicles(vehicles: List<Vehicle>) = vehicleDao.insertAllVehicles(vehicles)

    suspend fun insertVehicle(vehicle: Vehicle): Long {
        val id = vehicleDao.insertVehicle(vehicle)
        // First vehicle ever added (or the only non-archived one left) should
        // become the selected one automatically, so it shows up on the
        // Dashboard without an extra manual step.
        if (vehicleDao.getSelectedVehicleOnce() == null) {
            vehicleDao.selectVehicle(id)
        }
        return id
    }

    suspend fun updateVehicle(vehicle: Vehicle) = vehicleDao.updateVehicle(vehicle)

    suspend fun deleteVehicle(vehicle: Vehicle) = vehicleDao.deleteVehicle(vehicle)

    suspend fun archiveVehicle(id: Long) {
        vehicleDao.archiveVehicle(id)
        // If the archived vehicle was the selected one, hand selection to
        // another non-archived vehicle so the app doesn't end up with none
        // selected at all.
        if (vehicleDao.getSelectedVehicleOnce() == null) {
            vehicleDao.getFirstActiveVehicleId()?.let { vehicleDao.selectVehicle(it) }
        }
    }

    // Selecting a vehicle only ever changes THAT vehicle's selection state --
    // it used to also flip every other vehicle's archived flag off, which is
    // what caused newly-switched-away-from vehicles to vanish from lists that
    // filter on "not archived". Selecting an archived vehicle still restores
    // it first, matching the previous "Set Active" behavior for that case.
    suspend fun activateVehicle(id: Long) {
        vehicleDao.getVehicleById(id)?.let { vehicle ->
            if (!vehicle.isActive) {
                vehicleDao.restoreVehicle(id)
            }
        }
        vehicleDao.deselectAllVehicles()
        vehicleDao.selectVehicle(id)
    }
}
