package com.github.vermilion10.milea.data.repository

import com.github.vermilion10.milea.data.local.ReminderDao
import com.github.vermilion10.milea.data.model.Reminder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao
) {
    fun getActiveRemindersByVehicle(vehicleId: Long): Flow<List<Reminder>> =
        reminderDao.getActiveRemindersByVehicle(vehicleId)

    fun getAllRemindersByVehicle(vehicleId: Long): Flow<List<Reminder>> =
        reminderDao.getAllRemindersByVehicle(vehicleId)

    suspend fun getReminderById(id: Long): Reminder? = reminderDao.getReminderById(id)

    suspend fun insertReminder(reminder: Reminder): Long = reminderDao.insertReminder(reminder)

    suspend fun updateReminder(reminder: Reminder) = reminderDao.updateReminder(reminder)

    suspend fun deleteReminder(reminder: Reminder) = reminderDao.deleteReminder(reminder)

    suspend fun markReminderCompleted(id: Long) = reminderDao.markReminderCompleted(id)

    suspend fun getAllRemindersSync(): List<Reminder> = reminderDao.getAllRemindersSync()

    suspend fun insertAllReminders(reminders: List<Reminder>) = reminderDao.insertAllReminders(reminders)
}
