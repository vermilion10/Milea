package com.github.vermilion10.milea.domain

import com.github.vermilion10.milea.data.model.Expense
import com.github.vermilion10.milea.data.model.Fillup
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.Vehicle
import java.util.Calendar
import java.util.TimeZone

/** One full-tank-to-full-tank interval. Liters include any partial fills in between. */
data class ConsumptionInterval(
    val startDate: Long,
    val endDate: Long,
    val distanceKm: Float,
    val liters: Float,
    val endFillupId: Long = 0
) {
    val litersPer100Km: Float get() = liters / distanceKm * 100f
}

sealed interface FuelEstimate {
    data class Available(
        val litersLeft: Float,
        val tankCapacity: Float,
        val rangeKm: Float,
        val litersPer100Km: Float,
        val distanceSinceFillKm: Float,
        val lastFillDate: Long
    ) : FuelEstimate {
        val fraction: Float get() = (litersLeft / tankCapacity).coerceIn(0f, 1f)
    }

    /** Estimate can't be made yet; [reason] says what the user needs to add. */
    data class Unavailable(val reason: Reason) : FuelEstimate

    enum class Reason { NO_TANK_CAPACITY, NO_FULL_TANK, NOT_ENOUGH_FILLUPS }
}

data class OdometerPoint(val date: Long, val odometerKm: Long)

data class MonthBucket(
    /** Start of the month, local time. */
    val monthStart: Long,
    val distanceKm: Float,
    val odometerKm: Long?,
    val fuelCost: Float,
    val otherCost: Float,
    val liters: Float,
    val fillCount: Int
) {
    val totalCost: Float get() = fuelCost + otherCost
}

enum class StatsPeriod(val days: Int?) {
    MONTH(30), QUARTER(90), YEAR(365), ALL(null)
}

data class FillupStats(
    val count: Int = 0,
    val totalLiters: Float = 0f,
    val avgLitersPerFill: Float? = null,
    val avgConsumption: Float? = null,
    /** Lowest L/100km, i.e. the most efficient interval. */
    val bestConsumption: Float? = null,
    val worstConsumption: Float? = null,
    val avgPricePerLiter: Float? = null
)

data class CostStats(
    val totalCost: Float = 0f,
    val fuelCost: Float = 0f,
    val otherCost: Float = 0f,
    val lowestBill: Float? = null,
    val highestBill: Float? = null,
    val avgBill: Float? = null,
    val costPerKm: Float? = null,
    val fuelCostPerKm: Float? = null
)

data class DistanceStats(
    val totalKm: Float = 0f,
    val trackedKm: Float = 0f,
    val tripCount: Int = 0,
    val longestTripKm: Float? = null,
    val avgPerDayKm: Float? = null,
    val avgPerMonthKm: Float? = null
)

data class PeriodStats(
    val fillups: FillupStats = FillupStats(),
    val cost: CostStats = CostStats(),
    val distance: DistanceStats = DistanceStats()
)

object VehicleAnalytics {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun consumptionIntervals(fillups: List<Fillup>): List<ConsumptionInterval> {
        val sorted = fillups.sortedWith(compareBy({ it.odometer }, { it.date }))
        val result = mutableListOf<ConsumptionInterval>()
        var anchor: Fillup? = null
        var litersSinceAnchor = 0f
        for (fillup in sorted) {
            if (anchor == null) {
                if (fillup.isFullTank) anchor = fillup
                continue
            }
            litersSinceAnchor += fillup.liters
            if (fillup.isFullTank) {
                val distance = (fillup.odometer - anchor.odometer).toFloat()
                if (distance > 0f && litersSinceAnchor > 0f) {
                    result += ConsumptionInterval(anchor.date, fillup.date, distance, litersSinceAnchor, fillup.id)
                }
                anchor = fillup
                litersSinceAnchor = 0f
            }
        }
        return result
    }

    /** Distance-weighted average, so one short hop can't skew the figure. */
    fun averageConsumption(intervals: List<ConsumptionInterval>): Float? {
        val distance = intervals.sumOf { it.distanceKm.toDouble() }
        if (distance <= 0.0) return null
        return (intervals.sumOf { it.liters.toDouble() } / distance * 100.0).toFloat()
    }

    fun odometerTimeline(
        vehicle: Vehicle?,
        fillups: List<Fillup>,
        trips: List<Trip>,
        expenses: List<Expense>
    ): List<OdometerPoint> {
        val raw = buildList {
            fillups.forEach { add(OdometerPoint(it.date, it.odometer)) }
            trips.forEach { trip ->
                (trip.endOdometer ?: trip.startOdometer)?.let {
                    add(OdometerPoint(trip.endTime ?: trip.startTime, it))
                }
            }
            expenses.forEach { e -> e.odometer?.let { add(OdometerPoint(e.date, it)) } }
        }.sortedBy { it.date }
        // An odometer never goes backwards; smooth out typos and mixed sources
        // with a running maximum.
        var runningMax = vehicle?.odometerOffset ?: 0L
        return raw.map { point ->
            runningMax = maxOf(runningMax, point.odometerKm)
            point.copy(odometerKm = runningMax)
        }
    }

    fun currentOdometer(
        vehicle: Vehicle,
        fillups: List<Fillup>,
        trips: List<Trip>,
        expenses: List<Expense>
    ): Long = listOfNotNull(
        vehicle.odometerOffset,
        fillups.maxOfOrNull { it.odometer },
        trips.mapNotNull { it.endOdometer ?: it.startOdometer }.maxOrNull(),
        expenses.mapNotNull { it.odometer }.maxOrNull()
    ).max()

    /**
     * Estimates fuel in the tank now: start from the last full tank, then walk
     * forward through later partial fills, burning fuel at the vehicle's
     * average consumption for every km driven.
     */
    fun estimateFuel(
        vehicle: Vehicle,
        fillups: List<Fillup>,
        trips: List<Trip>,
        currentOdometerKm: Long
    ): FuelEstimate {
        val capacity = vehicle.tankCapacity?.takeIf { it > 0f }
            ?: return FuelEstimate.Unavailable(FuelEstimate.Reason.NO_TANK_CAPACITY)
        val sorted = fillups.sortedWith(compareBy({ it.odometer }, { it.date }))
        val lastFullIndex = sorted.indexOfLast { it.isFullTank }
        if (lastFullIndex < 0) return FuelEstimate.Unavailable(FuelEstimate.Reason.NO_FULL_TANK)
        val consumption = averageConsumption(consumptionIntervals(fillups))
            ?: return FuelEstimate.Unavailable(FuelEstimate.Reason.NOT_ENOUGH_FILLUPS)
        val perKm = consumption / 100f

        var level = capacity
        var previous = sorted[lastFullIndex]
        for (fillup in sorted.drop(lastFullIndex + 1)) {
            level -= (fillup.odometer - previous.odometer).coerceAtLeast(0) * perKm
            level = (level.coerceAtLeast(0f) + fillup.liters).coerceAtMost(capacity)
            previous = fillup
        }

        // Driving since the last fill-up. The odometer chain normally covers it,
        // but trips recorded without a known odometer still burn fuel.
        val byOdometer = (currentOdometerKm - previous.odometer).coerceAtLeast(0).toFloat()
        val byTrips = trips
            .filter { it.startTime >= previous.date }
            .sumOf { it.distance.toDouble() }
            .toFloat()
        val sinceFill = maxOf(byOdometer, byTrips)
        level = (level - sinceFill * perKm).coerceIn(0f, capacity)

        return FuelEstimate.Available(
            litersLeft = level,
            tankCapacity = capacity,
            rangeKm = level / perKm,
            litersPer100Km = consumption,
            distanceSinceFillKm = sinceFill,
            lastFillDate = previous.date
        )
    }

    /**
     * Fuel in the tank just before a fill-up at [odometerKm] / [date], using
     * only what was logged before it. Pass [fillups] without the fill-up being
     * entered or edited.
     */
    fun estimateBeforeFill(
        vehicle: Vehicle,
        fillups: List<Fillup>,
        trips: List<Trip>,
        odometerKm: Long,
        date: Long
    ): FuelEstimate = estimateFuel(
        vehicle,
        fillups.filter { it.odometer <= odometerKm && it.date <= date },
        trips.filter { it.startTime < date },
        odometerKm
    )

    private fun monthStart(time: Long, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply {
            timeInMillis = time
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun addMonths(time: Long, months: Int, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply {
            timeInMillis = time
            add(Calendar.MONTH, months)
        }.timeInMillis

    /** Highest odometer reading at or before [time], or null if none yet. */
    private fun odometerAt(timeline: List<OdometerPoint>, time: Long): Long? =
        timeline.lastOrNull { it.date <= time }?.odometerKm

    /**
     * Distance driven in [start, end). Uses the odometer where readings exist,
     * which also catches driving that was never recorded as a trip, and falls
     * back to the sum of recorded trips when that is larger.
     */
    private fun distanceBetween(
        timeline: List<OdometerPoint>,
        trips: List<Trip>,
        start: Long,
        end: Long
    ): Float {
        val inRange = timeline.filter { it.date in start until end }
        val odoDistance = if (inRange.isEmpty()) 0f else {
            val baseline = odometerAt(timeline, start - 1) ?: inRange.first().odometerKm
            (inRange.last().odometerKm - baseline).coerceAtLeast(0).toFloat()
        }
        val tripDistance = trips
            .filter { it.startTime in start until end }
            .sumOf { it.distance.toDouble() }
            .toFloat()
        return maxOf(odoDistance, tripDistance)
    }

    fun monthlyBuckets(
        vehicle: Vehicle?,
        fillups: List<Fillup>,
        trips: List<Trip>,
        expenses: List<Expense>,
        months: Int = 12,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): List<MonthBucket> {
        val timeline = odometerTimeline(vehicle, fillups, trips, expenses)
        val currentMonth = monthStart(now, tz)
        return (months - 1 downTo 0).map { back ->
            val start = addMonths(currentMonth, -back, tz)
            val end = addMonths(start, 1, tz)
            val monthFillups = fillups.filter { it.date in start until end }
            MonthBucket(
                monthStart = start,
                distanceKm = distanceBetween(timeline, trips, start, end),
                odometerKm = odometerAt(timeline, end - 1),
                fuelCost = monthFillups.sumOf { it.totalCost.toDouble() }.toFloat(),
                otherCost = expenses.filter { it.date in start until end }
                    .sumOf { it.amount.toDouble() }.toFloat(),
                liters = monthFillups.sumOf { it.liters.toDouble() }.toFloat(),
                fillCount = monthFillups.size
            )
        }
    }

    fun periodStats(
        vehicle: Vehicle?,
        fillups: List<Fillup>,
        trips: List<Trip>,
        expenses: List<Expense>,
        period: StatsPeriod,
        now: Long = System.currentTimeMillis()
    ): PeriodStats {
        val earliest = listOfNotNull(
            fillups.minOfOrNull { it.date },
            trips.minOfOrNull { it.startTime },
            expenses.minOfOrNull { it.date }
        ).minOrNull() ?: return PeriodStats()
        val start = period.days?.let { now - it * DAY_MS } ?: earliest
        val end = now + 1

        val periodFillups = fillups.filter { it.date in start until end }
        val periodTrips = trips.filter { it.startTime in start until end }
        val periodExpenses = expenses.filter { it.date in start until end }
        val intervals = consumptionIntervals(fillups).filter { it.endDate in start until end }
        val timeline = odometerTimeline(vehicle, fillups, trips, expenses)

        val totalLiters = periodFillups.sumOf { it.liters.toDouble() }.toFloat()
        val fuelCost = periodFillups.sumOf { it.totalCost.toDouble() }.toFloat()
        val otherCost = periodExpenses.sumOf { it.amount.toDouble() }.toFloat()
        val distance = distanceBetween(timeline, trips, start, end)
        val trackedKm = periodTrips.sumOf { it.distance.toDouble() }.toFloat()

        // Averages per day/month are over the span actually covered by data,
        // so a vehicle added last week doesn't look like it barely moves.
        val spanStart = maxOf(start, earliest)
        val spanDays = ((now - spanStart).toFloat() / DAY_MS).coerceAtLeast(1f)

        val perInterval = intervals.map { it.litersPer100Km }
        return PeriodStats(
            fillups = FillupStats(
                count = periodFillups.size,
                totalLiters = totalLiters,
                avgLitersPerFill = if (periodFillups.isNotEmpty()) totalLiters / periodFillups.size else null,
                avgConsumption = averageConsumption(intervals),
                bestConsumption = perInterval.minOrNull(),
                worstConsumption = perInterval.maxOrNull(),
                avgPricePerLiter = if (totalLiters > 0f) fuelCost / totalLiters else null
            ),
            cost = CostStats(
                totalCost = fuelCost + otherCost,
                fuelCost = fuelCost,
                otherCost = otherCost,
                lowestBill = periodFillups.minOfOrNull { it.totalCost },
                highestBill = periodFillups.maxOfOrNull { it.totalCost },
                avgBill = if (periodFillups.isNotEmpty()) fuelCost / periodFillups.size else null,
                costPerKm = if (distance > 0f) (fuelCost + otherCost) / distance else null,
                fuelCostPerKm = if (distance > 0f) fuelCost / distance else null
            ),
            distance = DistanceStats(
                totalKm = distance,
                trackedKm = trackedKm,
                tripCount = periodTrips.size,
                longestTripKm = periodTrips.maxOfOrNull { it.distance },
                avgPerDayKm = if (distance > 0f) distance / spanDays else null,
                avgPerMonthKm = if (distance > 0f) distance / spanDays * 30.44f else null
            )
        )
    }
}
