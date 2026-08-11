package com.github.vermilion10.milea.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
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
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val startOdometer: Long? = null,
    val endOdometer: Long? = null,
    val distance: Float = 0f,
    val duration: Long = 0,
    val movingTime: Long = 0,
    val idleTime: Long = 0,
    val averageSpeed: Float = 0f,
    val maxSpeed: Float = 0f,
    val category: TripCategory = TripCategory.OTHER,
    val note: String? = null,
    val isAutoDetected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TripCategory {
    COMMUTE, BUSINESS, LEISURE, OTHER
}
