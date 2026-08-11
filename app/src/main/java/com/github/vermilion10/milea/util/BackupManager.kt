package com.github.vermilion10.milea.util

import com.github.vermilion10.milea.data.repository.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val tripRepository: TripRepository,
    private val fillupRepository: FillupRepository,
    private val expenseRepository: ExpenseRepository,
    private val reminderRepository: ReminderRepository
) {

    private companion object {
        const val BACKUP_VERSION = 1
        const val PBKDF2_ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val GCM_TAG_BITS = 128
        const val SALT_LENGTH = 16
        const val IV_LENGTH = 12
    }

    suspend fun createEncryptedBackup(password: String): File {
        val data = collectAll()
        val json = buildJson(data)
        val encrypted = encrypt(json.toString(), password)

        val file = File.createTempFile("milea_backup", ".mbak")
        file.writeText(encrypted, Charsets.UTF_8)
        return file
    }

    suspend fun restoreFromEncrypted(file: File, password: String): Boolean {
        return try {
            val encrypted = file.readText()
            val json = decrypt(encrypted, password)
            val data = parseJson(JSONObject(json))
            insertAll(data)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun collectAll(): BackupData {
        return BackupData(
            vehicles = vehicleRepository.getAllVehiclesSync(),
            trips = tripRepository.getAllTripsSync(),
            tripPoints = tripRepository.getAllTripPointsSync(),
            fillups = fillupRepository.getAllFillupsSync(),
            expenses = expenseRepository.getAllExpensesSync(),
            reminders = reminderRepository.getAllRemindersSync()
        )
    }

    private suspend fun insertAll(data: BackupData) {
        vehicleRepository.insertAllVehicles(data.vehicles)
        tripRepository.insertAllTrips(data.trips)
        fillupRepository.insertAllFillups(data.fillups)
        expenseRepository.insertAllExpenses(data.expenses)
        reminderRepository.insertAllReminders(data.reminders)
        data.tripPoints.chunked(500).forEach { chunk ->
            tripRepository.insertTripPoints(chunk)
        }
    }

    private fun buildJson(data: BackupData): JSONObject {
        return JSONObject().apply {
            put("version", BACKUP_VERSION)

            put("vehicles", JSONArray().apply {
                data.vehicles.forEach { v ->
                    put(JSONObject().apply {
                        put("id", v.id)
                        put("name", v.name)
                        put("make", v.make ?: JSONObject.NULL)
                        put("model", v.model ?: JSONObject.NULL)
                        put("year", v.year ?: JSONObject.NULL)
                        put("fuelType", v.fuelType.name)
                        put("tankCapacity", v.tankCapacity?.toDouble() ?: JSONObject.NULL)
                        put("odometerOffset", v.odometerOffset)
                        put("odometerUnit", v.odometerUnit.name)
                        put("photoPath", v.photoPath ?: JSONObject.NULL)
                        put("isActive", v.isActive)
                        put("createdAt", v.createdAt)
                        put("updatedAt", v.updatedAt)
                    })
                }
            })

            put("trips", JSONArray().apply {
                data.trips.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("vehicleId", t.vehicleId)
                        put("startTime", t.startTime)
                        put("endTime", t.endTime ?: JSONObject.NULL)
                        put("startOdometer", t.startOdometer ?: JSONObject.NULL)
                        put("endOdometer", t.endOdometer ?: JSONObject.NULL)
                        put("distance", t.distance.toDouble())
                        put("duration", t.duration)
                        put("movingTime", t.movingTime)
                        put("idleTime", t.idleTime)
                        put("averageSpeed", t.averageSpeed.toDouble())
                        put("maxSpeed", t.maxSpeed.toDouble())
                        put("category", t.category.name)
                        put("note", t.note ?: JSONObject.NULL)
                        put("isAutoDetected", t.isAutoDetected)
                        put("createdAt", t.createdAt)
                        put("updatedAt", t.updatedAt)
                    })
                }
            })

            put("tripPoints", JSONArray().apply {
                data.tripPoints.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("tripId", p.tripId)
                        put("latitude", p.latitude)
                        put("longitude", p.longitude)
                        put("timestamp", p.timestamp)
                        put("speed", p.speed?.toDouble() ?: JSONObject.NULL)
                        put("altitude", p.altitude ?: JSONObject.NULL)
                        put("accuracy", p.accuracy?.toDouble() ?: JSONObject.NULL)
                    })
                }
            })

            put("fillups", JSONArray().apply {
                data.fillups.forEach { f ->
                    put(JSONObject().apply {
                        put("id", f.id)
                        put("vehicleId", f.vehicleId)
                        put("date", f.date)
                        put("odometer", f.odometer)
                        put("liters", f.liters.toDouble())
                        put("pricePerUnit", f.pricePerUnit.toDouble())
                        put("totalCost", f.totalCost.toDouble())
                        put("isFullTank", f.isFullTank)
                        put("stationName", f.stationName ?: JSONObject.NULL)
                        put("stationLatitude", f.stationLatitude ?: JSONObject.NULL)
                        put("stationLongitude", f.stationLongitude ?: JSONObject.NULL)
                        put("note", f.note ?: JSONObject.NULL)
                        put("receiptPath", f.receiptPath ?: JSONObject.NULL)
                        put("createdAt", f.createdAt)
                        put("updatedAt", f.updatedAt)
                    })
                }
            })

            put("expenses", JSONArray().apply {
                data.expenses.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id)
                        put("vehicleId", e.vehicleId)
                        put("date", e.date)
                        put("category", e.category.name)
                        put("amount", e.amount.toDouble())
                        put("description", e.description ?: JSONObject.NULL)
                        put("odometer", e.odometer ?: JSONObject.NULL)
                        put("receiptPath", e.receiptPath ?: JSONObject.NULL)
                        put("createdAt", e.createdAt)
                        put("updatedAt", e.updatedAt)
                    })
                }
            })

            put("reminders", JSONArray().apply {
                data.reminders.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("vehicleId", r.vehicleId)
                        put("title", r.title)
                        put("description", r.description ?: JSONObject.NULL)
                        put("dueDate", r.dueDate ?: JSONObject.NULL)
                        put("dueOdometer", r.dueOdometer ?: JSONObject.NULL)
                        put("isRecurring", r.isRecurring)
                        put("recurringIntervalDays", r.recurringIntervalDays ?: JSONObject.NULL)
                        put("recurringIntervalKm", r.recurringIntervalKm ?: JSONObject.NULL)
                        put("isCompleted", r.isCompleted)
                        put("completedAt", r.completedAt ?: JSONObject.NULL)
                        put("createdAt", r.createdAt)
                        put("updatedAt", r.updatedAt)
                    })
                }
            })
        }
    }

    private fun parseJson(json: JSONObject): BackupData {
        val vehicles = json.getJSONArray("vehicles").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.github.vermilion10.milea.data.model.Vehicle(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    make = o.optString("make").ifBlank { null },
                    model = o.optString("model").ifBlank { null },
                    year = if (o.isNull("year")) null else o.getInt("year"),
                    fuelType = com.github.vermilion10.milea.data.model.FuelType.valueOf(o.getString("fuelType")),
                    tankCapacity = if (o.isNull("tankCapacity")) null else o.getDouble("tankCapacity").toFloat(),
                    odometerOffset = o.getLong("odometerOffset"),
                    odometerUnit = com.github.vermilion10.milea.data.model.DistanceUnit.valueOf(o.getString("odometerUnit")),
                    photoPath = o.optString("photoPath").ifBlank { null },
                    isActive = o.getBoolean("isActive"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        }

        val trips = json.getJSONArray("trips").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.github.vermilion10.milea.data.model.Trip(
                    id = o.getLong("id"),
                    vehicleId = o.getLong("vehicleId"),
                    startTime = o.getLong("startTime"),
                    endTime = if (o.isNull("endTime")) null else o.getLong("endTime"),
                    startOdometer = if (o.isNull("startOdometer")) null else o.getLong("startOdometer"),
                    endOdometer = if (o.isNull("endOdometer")) null else o.getLong("endOdometer"),
                    distance = o.getDouble("distance").toFloat(),
                    duration = o.getLong("duration"),
                    movingTime = o.getLong("movingTime"),
                    idleTime = o.getLong("idleTime"),
                    averageSpeed = o.getDouble("averageSpeed").toFloat(),
                    maxSpeed = o.getDouble("maxSpeed").toFloat(),
                    category = com.github.vermilion10.milea.data.model.TripCategory.valueOf(o.getString("category")),
                    note = o.optString("note").ifBlank { null },
                    isAutoDetected = o.getBoolean("isAutoDetected"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        }

        val tripPoints = json.getJSONArray("tripPoints").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.github.vermilion10.milea.data.model.TripPoint(
                    id = o.getLong("id"),
                    tripId = o.getLong("tripId"),
                    latitude = o.getDouble("latitude"),
                    longitude = o.getDouble("longitude"),
                    timestamp = o.getLong("timestamp"),
                    speed = if (o.isNull("speed")) null else o.getDouble("speed").toFloat(),
                    altitude = if (o.isNull("altitude")) null else o.getDouble("altitude"),
                    accuracy = if (o.isNull("accuracy")) null else o.getDouble("accuracy").toFloat()
                )
            }
        }

        val fillups = json.getJSONArray("fillups").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.github.vermilion10.milea.data.model.Fillup(
                    id = o.getLong("id"),
                    vehicleId = o.getLong("vehicleId"),
                    date = o.getLong("date"),
                    odometer = o.getLong("odometer"),
                    liters = o.getDouble("liters").toFloat(),
                    pricePerUnit = o.getDouble("pricePerUnit").toFloat(),
                    totalCost = o.getDouble("totalCost").toFloat(),
                    isFullTank = o.getBoolean("isFullTank"),
                    stationName = o.optString("stationName").ifBlank { null },
                    stationLatitude = if (o.isNull("stationLatitude")) null else o.getDouble("stationLatitude"),
                    stationLongitude = if (o.isNull("stationLongitude")) null else o.getDouble("stationLongitude"),
                    note = o.optString("note").ifBlank { null },
                    receiptPath = o.optString("receiptPath").ifBlank { null },
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        }

        val expenses = json.getJSONArray("expenses").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.github.vermilion10.milea.data.model.Expense(
                    id = o.getLong("id"),
                    vehicleId = o.getLong("vehicleId"),
                    date = o.getLong("date"),
                    category = com.github.vermilion10.milea.data.model.ExpenseCategory.valueOf(o.getString("category")),
                    amount = o.getDouble("amount").toFloat(),
                    description = o.optString("description").ifBlank { null },
                    odometer = if (o.isNull("odometer")) null else o.getLong("odometer"),
                    receiptPath = o.optString("receiptPath").ifBlank { null },
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        }

        val reminders = json.getJSONArray("reminders").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.github.vermilion10.milea.data.model.Reminder(
                    id = o.getLong("id"),
                    vehicleId = o.getLong("vehicleId"),
                    title = o.getString("title"),
                    description = o.optString("description").ifBlank { null },
                    dueDate = if (o.isNull("dueDate")) null else o.getLong("dueDate"),
                    dueOdometer = if (o.isNull("dueOdometer")) null else o.getLong("dueOdometer"),
                    isRecurring = o.getBoolean("isRecurring"),
                    recurringIntervalDays = if (o.isNull("recurringIntervalDays")) null else o.getInt("recurringIntervalDays"),
                    recurringIntervalKm = if (o.isNull("recurringIntervalKm")) null else o.getInt("recurringIntervalKm"),
                    isCompleted = o.getBoolean("isCompleted"),
                    completedAt = if (o.isNull("completedAt")) null else o.getLong("completedAt"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        }

        return BackupData(
            vehicles = vehicles,
            trips = trips,
            tripPoints = tripPoints,
            fillups = fillups,
            expenses = expenses,
            reminders = reminders
        )
    }

    private fun encrypt(plaintext: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val output = ByteArray(salt.size + iv.size + ciphertext.size)
        System.arraycopy(salt, 0, output, 0, salt.size)
        System.arraycopy(iv, 0, output, salt.size, iv.size)
        System.arraycopy(ciphertext, 0, output, salt.size + iv.size, ciphertext.size)
        return Base64.getEncoder().encodeToString(output)
    }

    private fun decrypt(encoded: String, password: String): String {
        val input = Base64.getDecoder().decode(encoded)
        val salt = input.copyOfRange(0, SALT_LENGTH)
        val iv = input.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = input.copyOfRange(SALT_LENGTH + IV_LENGTH, input.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private data class BackupData(
        val vehicles: List<com.github.vermilion10.milea.data.model.Vehicle>,
        val trips: List<com.github.vermilion10.milea.data.model.Trip>,
        val tripPoints: List<com.github.vermilion10.milea.data.model.TripPoint>,
        val fillups: List<com.github.vermilion10.milea.data.model.Fillup>,
        val expenses: List<com.github.vermilion10.milea.data.model.Expense>,
        val reminders: List<com.github.vermilion10.milea.data.model.Reminder>
    )
}
