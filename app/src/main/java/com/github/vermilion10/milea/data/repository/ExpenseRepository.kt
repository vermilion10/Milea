package com.github.vermilion10.milea.data.repository

import com.github.vermilion10.milea.data.local.ExpenseDao
import com.github.vermilion10.milea.data.model.Expense
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    fun getExpensesByVehicle(vehicleId: Long): Flow<List<Expense>> = 
        expenseDao.getExpensesByVehicle(vehicleId)

    fun getExpensesByVehicleAndDateRange(vehicleId: Long, startTime: Long, endTime: Long): Flow<List<Expense>> =
        expenseDao.getExpensesByVehicleAndDateRange(vehicleId, startTime, endTime)

    suspend fun getExpenseById(id: Long): Expense? = expenseDao.getExpenseById(id)

    fun getExpenseByIdFlow(id: Long): Flow<Expense?> = expenseDao.getExpenseByIdFlow(id)

    fun getTotalExpenseForVehicle(vehicleId: Long): Flow<Float?> = 
        expenseDao.getTotalExpenseForVehicle(vehicleId)

    fun getTotalExpenseByCategory(vehicleId: Long, category: String): Flow<Float?> =
        expenseDao.getTotalExpenseByCategory(vehicleId, category)

    suspend fun getAllExpensesSync(): List<Expense> = expenseDao.getAllExpensesSync()

    suspend fun insertAllExpenses(expenses: List<Expense>) = expenseDao.insertAllExpenses(expenses)

    suspend fun insertExpense(expense: Expense): Long = expenseDao.insertExpense(expense)

    suspend fun updateExpense(expense: Expense) = expenseDao.updateExpense(expense)

    suspend fun deleteExpense(expense: Expense) = expenseDao.deleteExpense(expense)
}
