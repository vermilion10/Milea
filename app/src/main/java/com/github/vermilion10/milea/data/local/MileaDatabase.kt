package com.github.vermilion10.milea.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.vermilion10.milea.data.model.*

@Database(
    entities = [
        Vehicle::class,
        Trip::class,
        TripPoint::class,
        Fillup::class,
        Expense::class,
        Reminder::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MileaDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun tripDao(): TripDao
    abstract fun tripPointDao(): TripPointDao
    abstract fun fillupDao(): FillupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao
}
