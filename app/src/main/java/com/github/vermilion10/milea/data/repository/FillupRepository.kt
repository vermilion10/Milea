package com.github.vermilion10.milea.data.repository

import com.github.vermilion10.milea.data.local.FillupDao
import com.github.vermilion10.milea.data.model.Fillup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FillupRepository @Inject constructor(
    private val fillupDao: FillupDao
) {
    fun getFillupsByVehicle(vehicleId: Long): Flow<List<Fillup>> = 
        fillupDao.getFillupsByVehicle(vehicleId)

    fun getFillupsByVehicleAndDateRange(vehicleId: Long, startTime: Long, endTime: Long): Flow<List<Fillup>> =
        fillupDao.getFillupsByVehicleAndDateRange(vehicleId, startTime, endTime)

    suspend fun getFillupById(id: Long): Fillup? = fillupDao.getFillupById(id)

    fun getFillupByIdFlow(id: Long): Flow<Fillup?> = fillupDao.getFillupByIdFlow(id)

    fun getFullTankFillupsByVehicle(vehicleId: Long): Flow<List<Fillup>> = 
        fillupDao.getFullTankFillupsByVehicle(vehicleId)

    suspend fun getLastTwoFillups(vehicleId: Long): List<Fillup> = 
        fillupDao.getLastTwoFillups(vehicleId)

    suspend fun getLatestFillup(vehicleId: Long): Fillup? = fillupDao.getLatestFillup(vehicleId)

    fun getLatestFillupFlow(vehicleId: Long): Flow<Fillup?> = 
        fillupDao.getLatestFillupFlow(vehicleId)

    fun getTotalFuelCostForVehicle(vehicleId: Long): Flow<Float?> = 
        fillupDao.getTotalFuelCostForVehicle(vehicleId)

    fun getTotalLitersForVehicle(vehicleId: Long): Flow<Float?> = 
        fillupDao.getTotalLitersForVehicle(vehicleId)

    suspend fun getAllFillupsSync(): List<Fillup> = fillupDao.getAllFillupsSync()

    suspend fun insertAllFillups(fillups: List<Fillup>) = fillupDao.insertAllFillups(fillups)

    suspend fun insertFillup(fillup: Fillup): Long = fillupDao.insertFillup(fillup)

    suspend fun updateFillup(fillup: Fillup) = fillupDao.updateFillup(fillup)

    suspend fun deleteFillup(fillup: Fillup) = fillupDao.deleteFillup(fillup)
}
