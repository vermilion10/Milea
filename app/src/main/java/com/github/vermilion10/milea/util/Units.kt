package com.github.vermilion10.milea.util

import com.github.vermilion10.milea.data.model.DistanceUnit
import java.util.Locale

object Units {
    private const val KM_PER_MILE = 1.609344f
    private const val LITERS_PER_GALLON = 3.78541f
    private const val L100KM_TO_MPG = 235.214583f

    fun distance(km: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) km / KM_PER_MILE else km

    fun distanceLabel(unit: DistanceUnit): String =
        if (unit == DistanceUnit.MILES) "mi" else "km"

    fun fuel(liters: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) liters / LITERS_PER_GALLON else liters

    fun fuelLabel(unit: DistanceUnit): String =
        if (unit == DistanceUnit.MILES) "gal" else "L"

    fun consumption(l100km: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) L100KM_TO_MPG / l100km else l100km

    fun consumptionLabel(unit: DistanceUnit): String =
        if (unit == DistanceUnit.MILES) "mpg" else "L/100km"

    fun pricePerUnit(pricePerLiter: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) pricePerLiter * LITERS_PER_GALLON else pricePerLiter

    fun priceUnitLabel(unit: DistanceUnit): String =
        if (unit == DistanceUnit.MILES) "gal" else "L"

    fun speed(kmh: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) kmh / KM_PER_MILE else kmh

    fun speedLabel(unit: DistanceUnit): String =
        if (unit == DistanceUnit.MILES) "mph" else "km/h"

    fun formatDistance(km: Float, unit: DistanceUnit): String =
        String.format(Locale.getDefault(), "%.1f %s", distance(km, unit), distanceLabel(unit))

    fun formatDistanceNumber(km: Float, unit: DistanceUnit): String =
        String.format(Locale.getDefault(), "%.1f", distance(km, unit))

    fun formatFuel(liters: Float, unit: DistanceUnit): String =
        String.format(Locale.getDefault(), "%.1f %s", fuel(liters, unit), fuelLabel(unit))

    fun formatConsumption(l100km: Float, unit: DistanceUnit): String =
        String.format(Locale.getDefault(), "%.1f %s", consumption(l100km, unit), consumptionLabel(unit))

    fun formatPricePerUnit(pricePerLiter: Float, unit: DistanceUnit, money: MoneyFormat): String =
        "${money.formatPrecise(pricePerUnit(pricePerLiter, unit))}/${priceUnitLabel(unit)}"

    /** Cost per km (as stored) converted to cost per display distance unit. */
    fun costPerDistance(costPerKm: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) costPerKm * KM_PER_MILE else costPerKm

    // Inverse conversions: values typed by the user in their display unit,
    // converted to the km / liter / price-per-liter values the database stores.
    fun distanceToKm(value: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) value * KM_PER_MILE else value

    fun fuelToLiters(value: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) value * LITERS_PER_GALLON else value

    fun priceToPerLiter(value: Float, unit: DistanceUnit): Float =
        if (unit == DistanceUnit.MILES) value / LITERS_PER_GALLON else value

    fun formatWholeDistance(km: Float, unit: DistanceUnit): String =
        String.format(Locale.getDefault(), "%,.0f %s", distance(km, unit), distanceLabel(unit))

    fun formatOdometer(km: Long, unit: DistanceUnit): String =
        String.format(Locale.getDefault(), "%,d %s", Math.round(distance(km.toFloat(), unit)), distanceLabel(unit))

    fun formatSpeed(kmh: Float, unit: DistanceUnit): String =
        String.format(Locale.getDefault(), "%.0f %s", speed(kmh, unit), speedLabel(unit))
}
