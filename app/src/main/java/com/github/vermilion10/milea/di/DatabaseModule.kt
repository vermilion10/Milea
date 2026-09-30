package com.github.vermilion10.milea.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.vermilion10.milea.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MileaDatabase {
        return Room.databaseBuilder(
            context,
            MileaDatabase::class.java,
            "milea_database"
        )
            // v1 -> v2 added Vehicle.isSelected (see VehicleRepository/VehicleDao for
            // why selection was split out from the archived flag). No user-facing
            // release has shipped yet, so a real migration isn't needed -- this
            // just avoids a crash on devices that already have the v1 schema
            // installed from testing. Replace with a proper Migration before
            // shipping a version people have real data in.
            .fallbackToDestructiveMigration()
            .addCallback(NullTextCleanup)
            .build()
    }

    @Provides
    fun provideVehicleDao(database: MileaDatabase): VehicleDao = database.vehicleDao()

    @Provides
    fun provideTripDao(database: MileaDatabase): TripDao = database.tripDao()

    @Provides
    fun provideTripPointDao(database: MileaDatabase): TripPointDao = database.tripPointDao()

    @Provides
    fun provideFillupDao(database: MileaDatabase): FillupDao = database.fillupDao()

    @Provides
    fun provideExpenseDao(database: MileaDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun provideReminderDao(database: MileaDatabase): ReminderDao = database.reminderDao()
}

/**
 * Older backups restored optional text fields as the literal string "null".
 * Clear those on open so they show as empty instead of "null".
 */
private object NullTextCleanup : RoomDatabase.Callback() {
    private val columns = mapOf(
        "vehicles" to listOf("make", "model", "photoPath"),
        "trips" to listOf("note"),
        "fillups" to listOf("stationName", "note", "receiptPath"),
        "expenses" to listOf("description", "receiptPath"),
        "reminders" to listOf("description")
    )

    override fun onOpen(db: SupportSQLiteDatabase) {
        columns.forEach { (table, cols) ->
            cols.forEach { col -> db.execSQL("UPDATE $table SET $col = NULL WHERE $col = 'null'") }
        }
    }
}
