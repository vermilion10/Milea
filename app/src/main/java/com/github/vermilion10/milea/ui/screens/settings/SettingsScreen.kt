package com.github.vermilion10.milea.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.repository.ExpenseRepository
import com.github.vermilion10.milea.data.repository.FillupRepository
import com.github.vermilion10.milea.data.repository.SettingsRepository
import com.github.vermilion10.milea.data.repository.TripRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import com.github.vermilion10.milea.service.TripTrackingService
import com.github.vermilion10.milea.util.BackupManager
import com.github.vermilion10.milea.util.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val tripRepository: TripRepository,
    private val fillupRepository: FillupRepository,
    private val expenseRepository: ExpenseRepository,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager
) : ViewModel() {
    val activeVehicle = vehicleRepository.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val autoDetectEnabled = settingsRepository.autoDetectEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog = _showExportDialog.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage = _exportMessage.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage = _backupMessage.asStateFlow()

    private var pendingRestoreFile: File? = null

    fun setAutoDetectEnabled(context: Context, enabled: Boolean, vehicleId: Long?) {
        viewModelScope.launch {
            settingsRepository.setAutoDetectEnabled(enabled)
            val vid = vehicleId ?: vehicleRepository.getSelectedVehicleOnce()?.id ?: return@launch
            val intent = Intent(context, TripTrackingService::class.java).apply {
                action = if (enabled) {
                    TripTrackingService.ACTION_START_MONITORING
                } else {
                    TripTrackingService.ACTION_STOP_MONITORING
                }
                putExtra(TripTrackingService.EXTRA_VEHICLE_ID, vid)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun setShowExportDialog(show: Boolean) {
        _showExportDialog.value = show
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun exportData(context: Context, exportType: String) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportMessage.value = null
            runCatching {
                val vehicle = vehicleRepository.getSelectedVehicleOnce()
                    ?: error("No vehicle to export")
                val vehicleName = vehicle.name

                when (exportType) {
                    "trips" -> {
                        val trips = tripRepository.getTripsByVehicle(vehicle.id).first()
                        CsvExporter.exportTrips(context, trips, vehicleName)
                    }
                    "fillups" -> {
                        val fillups = fillupRepository.getFillupsByVehicle(vehicle.id).first()
                        CsvExporter.exportFillups(context, fillups, vehicleName)
                    }
                    "expenses" -> {
                        val expenses = expenseRepository.getExpensesByVehicle(vehicle.id).first()
                        CsvExporter.exportExpenses(context, expenses, vehicleName)
                    }
                    else -> {
                        val trips = tripRepository.getTripsByVehicle(vehicle.id).first()
                        val fillups = fillupRepository.getFillupsByVehicle(vehicle.id).first()
                        val expenses = expenseRepository.getExpensesByVehicle(vehicle.id).first()
                        CsvExporter.exportAll(context, trips, fillups, expenses, vehicleName)
                    }
                }
            }.onSuccess { file ->
                CsvExporter.shareFile(context, file)
                _exportMessage.value = "Exported ${file.name}"
            }.onFailure { error ->
                _exportMessage.value = "Export failed: ${error.message ?: "Unknown error"}"
            }
            _isExporting.value = false
        }
    }

    fun createBackup(context: Context, password: String) {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = null
            runCatching {
                backupManager.createEncryptedBackup(password)
            }.onSuccess { file ->
                CsvExporter.shareFile(context, file)
                _backupMessage.value = "Backup created"
            }.onFailure { error ->
                _backupMessage.value = "Backup failed: ${error.message ?: "Unknown error"}"
            }
            _isBackingUp.value = false
        }
    }

    fun setPendingRestoreFile(file: File?) {
        pendingRestoreFile = file
    }

    fun restoreBackup(password: String) {
        val file = pendingRestoreFile ?: return
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = null
            val success = backupManager.restoreFromEncrypted(file, password)
            _backupMessage.value = if (success) {
                "Restore completed"
            } else {
                "Restore failed: wrong password or corrupt file"
            }
            pendingRestoreFile = null
            _isBackingUp.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val autoDetectEnabled by viewModel.autoDetectEnabled.collectAsState()
    val showExportDialog by viewModel.showExportDialog.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val context = LocalContext.current

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordAction by remember { mutableStateOf("backup") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.setAutoDetectEnabled(
                context = context,
                enabled = true,
                vehicleId = activeVehicle?.id
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val temp = File(context.cacheDir, "milea_restore_temp.mbak")
                    temp.outputStream().use { output -> input.copyTo(output) }
                    viewModel.setPendingRestoreFile(temp)
                    passwordAction = "restore"
                    showPasswordDialog = true
                }
            } catch (_: Exception) {
                viewModel.setPendingRestoreFile(null)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Data Export",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Export ${activeVehicle?.name ?: "your vehicle"} data as CSV",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.setShowExportDialog(true) },
                        enabled = activeVehicle != null && !isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Data")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Automatic Trip Detection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Automatically start recording when your vehicle moves and stop after it has been idle for 3 minutes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = autoDetectEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasLocationPermission(context)) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    )
                                    return@Switch
                                }
                                viewModel.setAutoDetectEnabled(
                                    context = context,
                                    enabled = enabled,
                                    vehicleId = activeVehicle?.id
                                )
                            },
                            enabled = activeVehicle != null
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Backup & Restore",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Create an encrypted backup of all vehicle data or restore from a previous backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                passwordAction = "backup"
                                showPasswordDialog = true
                            },
                            enabled = !isBackingUp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Backup")
                        }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            enabled = !isBackingUp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore")
                        }
                    }

                    if (isBackingUp) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    backupMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (message.startsWith("Restore failed") || message.startsWith("Backup failed")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Milea - Vehicle Trip & Fuel Tracker",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Version ${com.github.vermilion10.milea.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Track your vehicle trips, fuel consumption, and expenses with detailed analytics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            exportMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(message)
                    }
                }
            }
        }

        if (showExportDialog) {
            ExportDialog(
                onDismiss = { viewModel.setShowExportDialog(false) },
                onExport = { exportType ->
                    viewModel.setShowExportDialog(false)
                    viewModel.exportData(context, exportType)
                }
            )
        }

        if (showPasswordDialog) {
            PasswordDialog(
                isRestore = passwordAction == "restore",
                onDismiss = { showPasswordDialog = false },
                onConfirm = { password ->
                    if (passwordAction == "backup") {
                        viewModel.createBackup(context, password)
                    } else {
                        viewModel.restoreBackup(password)
                    }
                    showPasswordDialog = false
                }
            )
        }
    }
}

@Composable
fun PasswordDialog(
    isRestore: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRestore) "Restore Backup" else "Create Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isRestore) {
                        "Enter the password used to encrypt this backup."
                    } else {
                        "Choose a password to encrypt your backup data."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) {
                Text(if (isRestore) "Restore" else "Backup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    var selectedType by remember { mutableStateOf("all") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select data to export:",
                    style = MaterialTheme.typography.bodyMedium
                )

                ExportOptionRow("all", selectedType, "All Data (Trips, Fill-ups, Expenses)", { selectedType = it })
                ExportOptionRow("trips", selectedType, "Trips Only", { selectedType = it })
                ExportOptionRow("fillups", selectedType, "Fill-ups Only", { selectedType = it })
                ExportOptionRow("expenses", selectedType, "Expenses Only", { selectedType = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(selectedType) }
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportOptionRow(
    value: String,
    selectedValue: String,
    label: String,
    onSelect: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = value == selectedValue,
            onClick = { onSelect(value) }
        )
        Text(label)
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
