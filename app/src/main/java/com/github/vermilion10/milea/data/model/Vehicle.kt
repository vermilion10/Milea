package com.github.vermilion10.milea.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val fuelType: FuelType = FuelType.GASOLINE,
    val tankCapacity: Float? = null,
    val odometerOffset: Long = 0,
    val odometerUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val photoPath: String? = null,
    val isActive: Boolean = true,
    // Whether this is the vehicle currently shown on the Dashboard / used by
    // Quick Actions. Deliberately separate from isActive (archived vs not) --
    // they used to be the same field, which meant switching the selected
    // vehicle silently archived every other vehicle. Exactly one non-archived
    // vehicle should have isSelected = true at a time.
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class FuelType {
    GASOLINE, DIESEL, ELECTRIC, HYBRID, LPG, CNG
}

enum class DistanceUnit {
    KILOMETERS, MILES
}
