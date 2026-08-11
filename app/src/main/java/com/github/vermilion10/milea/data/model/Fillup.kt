package com.github.vermilion10.milea.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fillups",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId")]
)
data class Fillup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val odometer: Long,
    val liters: Float,
    val pricePerUnit: Float,
    val totalCost: Float,
    val isFullTank: Boolean = true,
    val stationName: String? = null,
    val stationLatitude: Double? = null,
    val stationLongitude: Double? = null,
    val note: String? = null,
    val receiptPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
