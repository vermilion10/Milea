package com.github.vermilion10.milea

import com.github.vermilion10.milea.data.model.Expense
import com.github.vermilion10.milea.data.model.ExpenseCategory
import com.github.vermilion10.milea.data.model.Fillup
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.Vehicle
import com.github.vermilion10.milea.domain.FuelEstimate
import com.github.vermilion10.milea.domain.StatsPeriod
import com.github.vermilion10.milea.domain.VehicleAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class VehicleAnalyticsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val vehicle = Vehicle(id = 1, name = "Car", tankCapacity = 40f)

    private fun day(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(utc).apply { clear(); set(year, month - 1, day, 12, 0) }.timeInMillis

    private var nextId = 1L
    private fun fill(date: Long, odo: Long, liters: Float, full: Boolean = true, cost: Float = liters * 10f) =
        Fillup(id = nextId++, vehicleId = 1, date = date, odometer = odo, liters = liters,
            pricePerUnit = 10f, totalCost = cost, isFullTank = full)

    @Test
    fun consumptionCountsPartialFillsBetweenFullTanks() {
        val fillups = listOf(
            fill(day(2026, 1, 1), 1000, 40f),
            fill(day(2026, 1, 5), 1200, 10f, full = false),
            fill(day(2026, 1, 10), 1500, 25f)
        )
        val intervals = VehicleAnalytics.consumptionIntervals(fillups)
        assertEquals(1, intervals.size)
        // 35 L over 500 km, not 25 L over 500 km.
        assertEquals(7f, intervals[0].litersPer100Km, 0.001f)
    }

    @Test
    fun averageConsumptionIsDistanceWeighted() {
        val fillups = listOf(
            fill(day(2026, 1, 1), 1000, 40f),
            fill(day(2026, 1, 2), 1100, 10f),   // 100 km, 10 L/100km
            fill(day(2026, 1, 9), 1600, 30f)    // 500 km, 6 L/100km
        )
        val avg = VehicleAnalytics.averageConsumption(VehicleAnalytics.consumptionIntervals(fillups))!!
        assertEquals(40f / 600f * 100f, avg, 0.001f)
    }

    @Test
    fun fuelEstimateBurnsFromLastFullTankAndAddsPartials() {
        val fillups = listOf(
            fill(day(2026, 1, 1), 1000, 40f),
            fill(day(2026, 1, 10), 1500, 40f),               // 8 L/100km
            fill(day(2026, 1, 12), 1600, 5f, full = false)   // 40 - 8 + 5 = 37
        )
        val estimate = VehicleAnalytics.estimateFuel(vehicle, fillups, emptyList(), currentOdometerKm = 1700)
        assertTrue(estimate is FuelEstimate.Available)
        estimate as FuelEstimate.Available
        // 37 - 8 (100 km since the partial fill) = 29 L
        assertEquals(29f, estimate.litersLeft, 0.01f)
        assertEquals(29f / 8f * 100f, estimate.rangeKm, 0.1f)
        assertEquals(100f, estimate.distanceSinceFillKm, 0.01f)
    }

    @Test
    fun fuelEstimateNeverOverflowsTankOrGoesNegative() {
        val fillups = listOf(
            fill(day(2026, 1, 1), 1000, 40f),
            fill(day(2026, 1, 10), 1500, 40f),
            fill(day(2026, 1, 11), 1510, 30f, full = false)
        )
        val full = VehicleAnalytics.estimateFuel(vehicle, fillups, emptyList(), 1510) as FuelEstimate.Available
        assertEquals(40f, full.litersLeft, 0.01f)
        val empty = VehicleAnalytics.estimateFuel(vehicle, fillups, emptyList(), 9000) as FuelEstimate.Available
        assertEquals(0f, empty.litersLeft, 0.01f)
    }

    @Test
    fun fuelEstimateUsesTripsWhenOdometerIsBehind() {
        val fillups = listOf(fill(day(2026, 1, 1), 1000, 40f), fill(day(2026, 1, 10), 1500, 40f))
        val trips = listOf(Trip(id = 1, vehicleId = 1, startTime = day(2026, 1, 11), distance = 50f))
        val estimate = VehicleAnalytics.estimateFuel(vehicle, fillups, trips, 1500) as FuelEstimate.Available
        assertEquals(36f, estimate.litersLeft, 0.01f)
    }

    @Test
    fun fuelEstimateExplainsWhatIsMissing() {
        val one = listOf(fill(day(2026, 1, 1), 1000, 40f))
        assertEquals(
            FuelEstimate.Unavailable(FuelEstimate.Reason.NOT_ENOUGH_FILLUPS),
            VehicleAnalytics.estimateFuel(vehicle, one, emptyList(), 1000)
        )
        assertEquals(
            FuelEstimate.Unavailable(FuelEstimate.Reason.NO_TANK_CAPACITY),
            VehicleAnalytics.estimateFuel(vehicle.copy(tankCapacity = null), one, emptyList(), 1000)
        )
        assertEquals(
            FuelEstimate.Unavailable(FuelEstimate.Reason.NO_FULL_TANK),
            VehicleAnalytics.estimateFuel(vehicle, listOf(fill(day(2026, 1, 1), 1000, 10f, full = false)), emptyList(), 1000)
        )
    }

    @Test
    fun estimateBeforeFillIgnoresLaterFillups() {
        val fillups = listOf(
            fill(day(2026, 1, 1), 1000, 40f),
            fill(day(2026, 1, 10), 1500, 40f),               // 8 L/100km
            fill(day(2026, 1, 20), 1800, 10f, full = false)  // after the target date
        )
        // A fill at 1700 km on Jan 15: 40 L - 200 km * 8 L/100km = 24 L (60%).
        val before = VehicleAnalytics.estimateBeforeFill(vehicle, fillups, emptyList(), 1700, day(2026, 1, 15))
            as FuelEstimate.Available
        assertEquals(24f, before.litersLeft, 0.01f)
        assertEquals(0.6f, before.fraction, 0.001f)
    }

    @Test
    fun monthlyDistanceComesFromOdometerAcrossMonths() {
        val fillups = listOf(
            fill(day(2026, 1, 20), 1000, 40f),
            fill(day(2026, 2, 15), 1800, 40f),
            fill(day(2026, 3, 10), 2300, 40f)
        )
        val months = VehicleAnalytics.monthlyBuckets(
            vehicle, fillups, emptyList(), emptyList(), months = 3, now = day(2026, 3, 20), tz = utc
        )
        assertEquals(listOf(0f, 800f, 500f), months.map { it.distanceKm })
        assertEquals(listOf(1000L, 1800L, 2300L), months.map { it.odometerKm })
    }

    @Test
    fun monthlyCostsSplitFuelAndOther() {
        val fillups = listOf(fill(day(2026, 3, 2), 1000, 20f, cost = 300f))
        val expenses = listOf(
            Expense(id = 1, vehicleId = 1, date = day(2026, 3, 3), category = ExpenseCategory.PARKING, amount = 50f)
        )
        val march = VehicleAnalytics.monthlyBuckets(
            vehicle, fillups, emptyList(), expenses, months = 1, now = day(2026, 3, 20), tz = utc
        ).single()
        assertEquals(300f, march.fuelCost, 0.001f)
        assertEquals(50f, march.otherCost, 0.001f)
        assertEquals(1, march.fillCount)
    }

    @Test
    fun periodStatsBestWorstAndBills() {
        val fillups = listOf(
            fill(day(2026, 1, 1), 1000, 40f, cost = 400f),
            fill(day(2026, 1, 10), 1500, 40f, cost = 420f),  // 8 L/100km
            fill(day(2026, 1, 20), 2000, 30f, cost = 310f)   // 6 L/100km
        )
        val stats = VehicleAnalytics.periodStats(
            vehicle, fillups, emptyList(), emptyList(), StatsPeriod.ALL, now = day(2026, 1, 21)
        )
        assertEquals(3, stats.fillups.count)
        assertEquals(6f, stats.fillups.bestConsumption!!, 0.001f)
        assertEquals(8f, stats.fillups.worstConsumption!!, 0.001f)
        assertEquals(310f, stats.cost.lowestBill!!, 0.001f)
        assertEquals(420f, stats.cost.highestBill!!, 0.001f)
        assertEquals(1000f, stats.distance.totalKm, 0.001f)
        assertEquals(1130f / 1000f, stats.cost.costPerKm!!, 0.0001f)
    }

    @Test
    fun periodStatsEmptyWhenNothingLogged() {
        val stats = VehicleAnalytics.periodStats(vehicle, emptyList(), emptyList(), emptyList(), StatsPeriod.YEAR)
        assertEquals(0, stats.fillups.count)
        assertNull(stats.cost.costPerKm)
    }
}
