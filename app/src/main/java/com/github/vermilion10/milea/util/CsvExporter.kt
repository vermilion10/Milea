package com.github.vermilion10.milea.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.github.vermilion10.milea.data.model.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    fun exportTrips(context: Context, trips: List<Trip>, vehicleName: String): File {
        val fileName = "milea_trips_${vehicleName.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        FileWriter(file).use { writer ->
            writer.append("ID,Vehicle ID,Start Time,End Time,Distance (km),Duration (min),Moving Time (min),Idle Time (min),Avg Speed (km/h),Max Speed (km/h),Category,Note,Auto Detected\n")
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            trips.forEach { trip ->
                writer.append("${trip.id},")
                writer.append("${trip.vehicleId},")
                writer.append("${dateFormat.format(Date(trip.startTime))},")
                writer.append("${if (trip.endTime != null) dateFormat.format(Date(trip.endTime)) else ""},")
                writer.append("${trip.distance},")
                writer.append("${trip.duration / 60000},")
                writer.append("${trip.movingTime / 60000},")
                writer.append("${trip.idleTime / 60000},")
                writer.append("${trip.averageSpeed * 3.6f},")
                writer.append("${trip.maxSpeed * 3.6f},")
                writer.append("${trip.category.name},")
                writer.append("${trip.note ?: ""},")
                writer.append("${trip.isAutoDetected}\n")
            }
        }
        
        return file
    }

    fun exportFillups(context: Context, fillups: List<Fillup>, vehicleName: String): File {
        val fileName = "milea_fillups_${vehicleName.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        FileWriter(file).use { writer ->
            writer.append("ID,Vehicle ID,Date,Odometer (km),Liters,Price per Liter,Total Cost,Full Tank,Station,Note\n")
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            fillups.forEach { fillup ->
                writer.append("${fillup.id},")
                writer.append("${fillup.vehicleId},")
                writer.append("${dateFormat.format(Date(fillup.date))},")
                writer.append("${fillup.odometer},")
                writer.append("${fillup.liters},")
                writer.append("${fillup.pricePerUnit},")
                writer.append("${fillup.totalCost},")
                writer.append("${fillup.isFullTank},")
                writer.append("${fillup.stationName ?: ""},")
                writer.append("${fillup.note ?: ""}\n")
            }
        }
        
        return file
    }

    fun exportExpenses(context: Context, expenses: List<Expense>, vehicleName: String): File {
        val fileName = "milea_expenses_${vehicleName.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        FileWriter(file).use { writer ->
            writer.append("ID,Vehicle ID,Date,Category,Amount,Description,Odometer (km)\n")
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            expenses.forEach { expense ->
                writer.append("${expense.id},")
                writer.append("${expense.vehicleId},")
                writer.append("${dateFormat.format(Date(expense.date))},")
                writer.append("${expense.category.name},")
                writer.append("${expense.amount},")
                writer.append("${expense.description ?: ""},")
                writer.append("${expense.odometer ?: ""}\n")
            }
        }
        
        return file
    }

    fun exportAll(context: Context, trips: List<Trip>, fillups: List<Fillup>, expenses: List<Expense>, vehicleName: String): File {
        val fileName = "milea_complete_${vehicleName.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        FileWriter(file).use { writer ->
            writer.append("TYPE,")
            writer.append("ID,Vehicle ID,Date/Start Time,")
            writer.append("Distance (km),Duration (min),Category,")
            writer.append("Liters,Price per Liter,Total Cost,Full Tank,")
            writer.append("Amount,Expense Category,")
            writer.append("Note/Description\n")
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            trips.forEach { trip ->
                writer.append("TRIP,")
                writer.append("${trip.id},${trip.vehicleId},")
                writer.append("${dateFormat.format(Date(trip.startTime))},")
                writer.append("${trip.distance},${trip.duration / 60000},")
                writer.append("${trip.category.name},")
                writer.append(",,,,,")
                writer.append("${trip.note ?: ""}\n")
            }
            
            fillups.forEach { fillup ->
                writer.append("FILLUP,")
                writer.append("${fillup.id},${fillup.vehicleId},")
                writer.append("${dateFormat.format(Date(fillup.date))},")
                writer.append(",,")
                writer.append(",")
                writer.append("${fillup.liters},${fillup.pricePerUnit},${fillup.totalCost},${fillup.isFullTank},")
                writer.append(",")
                writer.append("${fillup.note ?: ""}\n")
            }
            
            expenses.forEach { expense ->
                writer.append("EXPENSE,")
                writer.append("${expense.id},${expense.vehicleId},")
                writer.append("${dateFormat.format(Date(expense.date))},")
                writer.append(",,")
                writer.append(",")
                writer.append(",,,,")
                writer.append("${expense.amount},${expense.category.name},")
                writer.append("${expense.description ?: ""}\n")
            }
        }
        
        return file
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooserIntent = Intent.createChooser(shareIntent, "Export Data")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }
}
