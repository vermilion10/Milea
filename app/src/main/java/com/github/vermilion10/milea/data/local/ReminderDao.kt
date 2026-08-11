package com.github.vermilion10.milea.data.local

import androidx.room.*
import com.github.vermilion10.milea.data.model.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE vehicleId = :vehicleId AND isCompleted = 0 ORDER BY dueDate ASC, dueOdometer ASC")
    fun getActiveRemindersByVehicle(vehicleId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE vehicleId = :vehicleId ORDER BY createdAt DESC")
    fun getAllRemindersByVehicle(vehicleId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND (dueDate <= :currentTime OR dueOdometer <= :currentOdometer)")
    fun getDueReminders(currentTime: Long, currentOdometer: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY createdAt ASC")
    suspend fun getAllRemindersSync(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReminders(reminders: List<Reminder>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE vehicleId = :vehicleId")
    suspend fun deleteRemindersByVehicle(vehicleId: Long)

    @Query("UPDATE reminders SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markReminderCompleted(id: Long, completedAt: Long = System.currentTimeMillis())
}
