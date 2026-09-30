package com.github.vermilion10.milea.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
import com.github.vermilion10.milea.data.repository.AppearanceSettings
import com.github.vermilion10.milea.data.repository.CurrencySettings
import com.github.vermilion10.milea.data.repository.SettingsRepository
import com.github.vermilion10.milea.data.repository.ThemeMode
import com.github.vermilion10.milea.ui.components.rememberTripStarter
import com.github.vermilion10.milea.util.MoneyFormat
import com.github.vermilion10.milea.util.ConsumptionUnit
import com.github.vermilion10.milea.util.Units
import com.github.vermilion10.milea.data.model.DistanceUnit
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import com.github.vermilion10.milea.util.TrackingPreflight
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

    val appearance = settingsRepository.appearance
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    val currency = settingsRepository.currency
        .stateIn(viewModelScope, SharingStarted.Eagerly, CurrencySettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    val consumptionUnit = settingsRepository.consumptionUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConsumptionUnit.AUTO)

    fun setConsumptionUnit(unit: ConsumptionUnit) {
        viewModelScope.launch { settingsRepository.setConsumptionUnit(unit) }
    }

    fun setCurrency(symbol: String, decimals: Int) {
        viewModelScope.launch { settingsRepository.setCurrency(symbol, decimals) }
    }

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
    onBack: () -> Unit = {},
    onOpenVehicles: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val autoDetectEnabled by viewModel.autoDetectEnabled.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val consumptionUnit by viewModel.consumptionUnit.collectAsState()
    val showExportDialog by viewModel.showExportDialog.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordAction by remember { mutableStateOf("backup") }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showConsumptionDialog by remember { mutableStateOf(false) }

    // Turning auto-detect on goes through the same permission and
    // location/battery checks as starting a trip by hand.
    val enableAutoDetect = rememberTripStarter {
        viewModel.setAutoDetectEnabled(context = context, enabled = true, vehicleId = activeVehicle?.id)
    }

    LaunchedEffect(exportMessage) {
        exportMessage?.let { snackbar.showSnackbar(it); viewModel.clearExportMessage() }
    }
    LaunchedEffect(backupMessage) {
        backupMessage?.let { snackbar.showSnackbar(it); viewModel.clearBackupMessage() }
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SettingsGroup("Vehicles") {
                SettingsRow(
                    icon = Icons.Default.Garage,
                    title = "Manage vehicles",
                    subtitle = activeVehicle?.let { "Selected: ${it.name}" } ?: "No vehicle yet",
                    onClick = onOpenVehicles
                )
            }

            SettingsGroup("Appearance") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = appearance.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size)
                            ) { Text(label) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsSwitchRow(
                        icon = Icons.Default.Palette,
                        title = "Dynamic color",
                        subtitle = "Use colors from your wallpaper",
                        checked = appearance.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor
                    )
                }
                SettingsRow(
                    icon = Icons.Default.Payments,
                    title = "Currency",
                    subtitle = "${currency.symbol.ifBlank { "No symbol" }} · ${currency.decimals} decimals · e.g. ${MoneyFormat(currency).format(125000f)}",
                    onClick = { showCurrencyDialog = true }
                )
                SettingsRow(
                    icon = Icons.Default.Speed,
                    title = "Fuel consumption",
                    subtitle = consumptionUnit.title,
                    onClick = { showConsumptionDialog = true }
                )
            }

            SettingsGroup("Trip recording") {
                SettingsSwitchRow(
                    icon = Icons.Default.Sensors,
                    title = "Automatic trip detection",
                    subtitle = "Start recording when the vehicle moves, stop after 3 minutes idle",
                    checked = autoDetectEnabled,
                    enabled = activeVehicle != null,
                    onCheckedChange = { enabled ->
                        if (enabled) enableAutoDetect()
                        else viewModel.setAutoDetectEnabled(context, false, activeVehicle?.id)
                    }
                )
                val preflight = remember(autoDetectEnabled) { TrackingPreflight.check(context) }
                if (preflight.batteryOptimized) {
                    SettingsRow(
                        icon = Icons.Default.BatteryAlert,
                        title = "Allow background activity",
                        subtitle = "Battery optimization can stop recording on long trips",
                        onClick = {
                            runCatching { context.startActivity(TrackingPreflight.batteryOptimizationIntent(context)) }
                        }
                    )
                }
            }

            SettingsGroup("Data") {
                SettingsRow(
                    icon = Icons.Default.Download,
                    title = "Export CSV",
                    subtitle = "Share ${activeVehicle?.name ?: "vehicle"} data as spreadsheet files",
                    enabled = activeVehicle != null && !isExporting,
                    onClick = { viewModel.setShowExportDialog(true) },
                    trailing = if (isExporting) {
                        { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    } else null
                )
                SettingsRow(
                    icon = Icons.Default.Backup,
                    title = "Back up",
                    subtitle = "Password-protected copy of all vehicles and logs",
                    enabled = !isBackingUp,
                    onClick = {
                        passwordAction = "backup"
                        showPasswordDialog = true
                    },
                    trailing = if (isBackingUp) {
                        { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    } else null
                )
                SettingsRow(
                    icon = Icons.Default.Restore,
                    title = "Restore",
                    subtitle = "Replace current data with a backup file",
                    enabled = !isBackingUp,
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) }
                )
            }

            SettingsGroup("About") {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "Milea",
                    subtitle = "Version ${com.github.vermilion10.milea.BuildConfig.VERSION_NAME}",
                    onClick = null
                )
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

        if (showConsumptionDialog) {
            AlertDialog(
                onDismissRequest = { showConsumptionDialog = false },
                title = { Text("Fuel consumption") },
                text = {
                    Column {
                        ConsumptionUnit.entries.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .selectable(
                                        selected = option == consumptionUnit,
                                        role = Role.RadioButton,
                                        onClick = {
                                            viewModel.setConsumptionUnit(option)
                                            showConsumptionDialog = false
                                        }
                                    )
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(selected = option == consumptionUnit, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(option.title, style = MaterialTheme.typography.bodyLarge)
                                    if (option.label.isNotEmpty()) {
                                        Text(
                                            "e.g. ${Units.formatConsumption(4f, DistanceUnit.KILOMETERS, option)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showConsumptionDialog = false }) { Text("Close") } }
            )
        }

        if (showCurrencyDialog) {
            CurrencyDialog(
                current = currency,
                onDismiss = { showCurrencyDialog = false },
                onSave = { symbol, decimals ->
                    viewModel.setCurrency(symbol, decimals)
                    showCurrencyDialog = false
                }
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyDialog(
    current: CurrencySettings,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var symbol by remember { mutableStateOf(current.symbol) }
    var decimals by remember { mutableIntStateOf(current.decimals) }
    val presets = listOf("Rp" to 0, "$" to 2, "€" to 2, "£" to 2, "RM" to 2, "¥" to 0, "₹" to 2)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (s, d) ->
                        FilterChip(
                            selected = symbol == s && decimals == d,
                            onClick = { symbol = s; decimals = d },
                            label = { Text(s) }
                        )
                    }
                }
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.take(4) },
                    label = { Text("Symbol") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text("Decimal places", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        (0..2).forEach { d ->
                            SegmentedButton(
                                selected = decimals == d,
                                onClick = { decimals = d },
                                shape = SegmentedButtonDefaults.itemShape(d, 3)
                            ) { Text(d.toString()) }
                        }
                    }
                }
                Text(
                    "Preview: ${MoneyFormat(CurrencySettings(symbol, decimals)).format(125000.5f)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(symbol.trim(), decimals) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
