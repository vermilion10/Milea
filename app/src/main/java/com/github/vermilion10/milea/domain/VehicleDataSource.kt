package com.github.vermilion10.milea.domain

import com.github.vermilion10.milea.data.model.Expense
import com.github.vermilion10.milea.data.model.Fillup
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.Vehicle
import com.github.vermilion10.milea.data.repository.ExpenseRepository
import com.github.vermilion10.milea.data.repository.FillupRepository
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Everything logged for one vehicle, kept together so derived stats stay consistent. */
data class VehicleData(
    val vehicle: Vehicle,
    val fillups: List<Fillup>,
    val trips: List<Trip>,
    val expenses: List<Expense>
) {
    val currentOdometerKm: Long
        get() = VehicleAnalytics.currentOdometer(vehicle, fillups, trips, expenses)

    val fuelEstimate: FuelEstimate
        get() = VehicleAnalytics.estimateFuel(vehicle, fillups, trips, currentOdometerKm)
}

@Singleton
class VehicleDataSource @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val fillupRepository: FillupRepository,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository
) {
    /** Data for the selected vehicle, or null when there is none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun selectedVehicleData(): Flow<VehicleData?> =
        vehicleRepository.getSelectedVehicle().flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(null)
            else combine(
                fillupRepository.getFillupsByVehicle(vehicle.id),
                tripRepository.getTripsByVehicle(vehicle.id),
                expenseRepository.getExpensesByVehicle(vehicle.id)
            ) { fillups, trips, expenses ->
                VehicleData(vehicle, fillups, trips, expenses)
            }
        }
}
