package com.github.vermilion10.milea.data.local

import androidx.room.*
import com.github.vermilion10.milea.data.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getExpensesByVehicle(vehicleId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId AND date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getExpensesByVehicleAndDateRange(vehicleId: Long, startTime: Long, endTime: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun getExpenseByIdFlow(id: Long): Flow<Expense?>

    @Query("SELECT SUM(amount) FROM expenses WHERE vehicleId = :vehicleId")
    fun getTotalExpenseForVehicle(vehicleId: Long): Flow<Float?>

    @Query("SELECT SUM(amount) FROM expenses WHERE vehicleId = :vehicleId AND category = :category")
    fun getTotalExpenseByCategory(vehicleId: Long, category: String): Flow<Float?>

    @Query("SELECT * FROM expenses ORDER BY date ASC")
    suspend fun getAllExpensesSync(): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllExpenses(expenses: List<Expense>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE vehicleId = :vehicleId")
    suspend fun deleteExpensesByVehicle(vehicleId: Long)
}
