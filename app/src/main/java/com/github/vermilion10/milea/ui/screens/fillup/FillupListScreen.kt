package com.github.vermilion10.milea.ui.screens.fillup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.Fillup
import com.github.vermilion10.milea.data.repository.FillupRepository
import com.github.vermilion10.milea.domain.FuelEstimate
import com.github.vermilion10.milea.domain.VehicleAnalytics
import com.github.vermilion10.milea.domain.VehicleData
import com.github.vermilion10.milea.domain.VehicleDataSource
import com.github.vermilion10.milea.ui.components.*
import com.github.vermilion10.milea.util.LocalMoney
import com.github.vermilion10.milea.util.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** A fill-up plus what it tells us relative to the previous one. */
data class FillupRow(
    val fillup: Fillup,
    val distanceSincePreviousKm: Long?,
    val litersPer100Km: Float?
)

data class FillupListState(
    val data: VehicleData? = null,
    val rows: List<FillupRow> = emptyList(),
    val avgConsumption: Float? = null,
    val monthSpend: Float = 0f,
    val loaded: Boolean = false
)

@HiltViewModel
class FillupListViewModel @Inject constructor(
    private val fillupRepository: FillupRepository,
    vehicleDataSource: VehicleDataSource
) : ViewModel() {
    val state = vehicleDataSource.selectedVehicleData()
        .map { data ->
            if (data == null) return@map FillupListState(loaded = true)
            val intervals = VehicleAnalytics.consumptionIntervals(data.fillups)
            val byEnd = intervals.associateBy { it.endFillupId }
            val byOdometer = data.fillups.sortedWith(compareBy({ it.odometer }, { it.date }))
            val rows = byOdometer.mapIndexed { i, f ->
                FillupRow(
                    fillup = f,
                    distanceSincePreviousKm = byOdometer.getOrNull(i - 1)?.let { f.odometer - it.odometer },
                    litersPer100Km = byEnd[f.id]?.litersPer100Km
                )
            }.sortedWith(compareByDescending<FillupRow> { it.fillup.date }.thenByDescending { it.fillup.odometer })
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            FillupListState(
                data = data,
                rows = rows,
                avgConsumption = VehicleAnalytics.averageConsumption(intervals),
                monthSpend = data.fillups.filter { it.date >= monthStart }.sumOf { it.totalCost.toDouble() }.toFloat(),
                loaded = true
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FillupListState())

    fun save(fillup: Fillup) {
        viewModelScope.launch {
            if (fillup.id == 0L) fillupRepository.insertFillup(fillup)
            else fillupRepository.updateFillup(fillup.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete(fillup: Fillup) {
        viewModelScope.launch { fillupRepository.deleteFillup(fillup) }
    }

    fun restore(fillup: Fillup) {
        viewModelScope.launch { fillupRepository.insertFillup(fillup) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillupListScreen(
    onAddVehicle: () -> Unit = {},
    viewModel: FillupListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val data = state.data
    val unit = data?.vehicle?.odometerUnit ?: DistanceUnit.KILOMETERS

    var editorTarget by remember { mutableStateOf<Fillup?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(title = { Text("Fuel") }, scrollBehavior = scrollBehavior)
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (data != null) {
                ExtendedFloatingActionButton(
                    onClick = { editorTarget = null; editorOpen = true },
                    expanded = listState.firstVisibleItemIndex == 0,
                    icon = { Icon(Icons.Default.LocalGasStation, contentDescription = null) },
                    text = { Text("Refuel") }
                )
            }
        }
    ) { padding ->
        when {
            !state.loaded -> Unit
            data == null -> NoActiveVehicleMessage(padding, "Add a vehicle first to log fill-ups.", onAddVehicle)
            state.rows.isEmpty() -> EmptyState(
                icon = Icons.Default.LocalGasStation,
                title = "No fill-ups yet",
                message = "Log each time you refuel. After two full-tank fill-ups Milea can show your consumption and estimate fuel left.",
                modifier = Modifier.padding(padding),
                action = {
                    Button(onClick = { editorTarget = null; editorOpen = true }) { Text("Log first fill-up") }
                }
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = "Avg consumption",
                            value = state.avgConsumption?.let { Units.formatConsumption(it, unit) } ?: "--",
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        StatTile(
                            label = "This month",
                            value = money.format(state.monthSpend),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                var lastMonth: String? = null
                state.rows.forEach { row ->
                    val month = formatMonthYear(row.fillup.date)
                    if (month != lastMonth) {
                        lastMonth = month
                        item(key = "h-$month") {
                            Text(
                                month,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }
                    }
                    item(key = row.fillup.id) {
                        FillupItem(row, unit, onClick = { editorTarget = row.fillup; editorOpen = true })
                    }
                }
            }
        }
    }

    if (editorOpen && data != null) {
        FillupSheet(
            data = data,
            existing = editorTarget,
            onDismiss = { editorOpen = false },
            onSave = { fillup ->
                viewModel.save(fillup)
                editorOpen = false
            },
            onDelete = { fillup ->
                viewModel.delete(fillup)
                editorOpen = false
                scope.launch {
                    val result = snackbar.showSnackbar(
                        "Fill-up deleted", actionLabel = "Undo", duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(fillup)
                }
            }
        )
    }
}

@Composable
private fun FillupItem(row: FillupRow, unit: DistanceUnit, onClick: () -> Unit) {
    val money = LocalMoney.current
    val f = row.fillup
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (f.isFullTank) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (f.isFullTank) Icons.Default.LocalGasStation else Icons.Default.WaterDrop,
                        contentDescription = if (f.isFullTank) "Full tank" else "Partial fill",
                        tint = if (f.isFullTank) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${Units.formatFuel(f.liters, unit)} · ${money.format(f.totalCost)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    buildString {
                        append(formatShortDate(f.date))
                        append(" · ")
                        append(Units.formatOdometer(f.odometer, unit))
                        row.distanceSincePreviousKm?.takeIf { it > 0 }?.let {
                            append(" · +")
                            append(Units.formatWholeDistance(it.toFloat(), unit))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                f.stationName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            row.litersPer100Km?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        String.format(java.util.Locale.getDefault(), "%.1f", Units.consumption(it, unit)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        Units.consumptionLabel(unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private enum class AmountMode(val label: String) { COST("Amount paid"), VOLUME("Fuel amount"), GAUGE("Fuel gauge") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillupSheet(
    data: VehicleData,
    existing: Fillup?,
    onDismiss: () -> Unit,
    onSave: (Fillup) -> Unit,
    onDelete: (Fillup) -> Unit
) {
    val money = LocalMoney.current
    val vehicle = data.vehicle
    val unit = vehicle.odometerUnit
    val capacity = vehicle.tankCapacity?.takeIf { it > 0f }
    val lastFillup = remember(data.fillups) { data.fillups.maxByOrNull { it.date } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var date by remember { mutableLongStateOf(existing?.date ?: System.currentTimeMillis()) }
    var isFull by remember { mutableStateOf(existing?.isFullTank ?: true) }
    var odometer by remember {
        mutableStateOf(
            Math.round(Units.distance((existing?.odometer ?: data.currentOdometerKm).toFloat(), unit)).toString()
        )
    }
    val pricePrefill = existing?.pricePerUnit ?: lastFillup?.pricePerUnit
    var price by remember {
        mutableStateOf(pricePrefill?.let { Units.pricePerUnit(it, unit).toInputString(3) } ?: "")
    }
    var mode by remember { mutableStateOf(AmountMode.COST) }
    var cost by remember { mutableStateOf(existing?.totalCost?.toInputString(money.settings.decimals) ?: "") }
    var volume by remember { mutableStateOf(existing?.let { Units.fuel(it.liters, unit).toInputString(2) } ?: "") }
    // Fuel gauge mode: where the needle ended up, and where it was before.
    // "Before" follows Milea's estimate until the user moves it.
    var gaugeAfter by remember { mutableFloatStateOf(100f) }
    var gaugeBeforeOverride by remember { mutableStateOf<Float?>(null) }
    var adjustBefore by remember { mutableStateOf(false) }
    var station by remember { mutableStateOf(existing?.stationName ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showDetails by remember { mutableStateOf(existing?.stationName != null || existing?.note != null) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (isFull && mode == AmountMode.GAUGE) mode = AmountMode.COST

    val odometerKm = odometer.toFloatOrNull()?.let { Math.round(Units.distanceToKm(it, unit)).toLong() }
    val estimatedBefore: Float? = remember(odometerKm, date, data) {
        if (odometerKm == null || capacity == null) null
        else (VehicleAnalytics.estimateBeforeFill(
            vehicle, data.fillups.filter { it.id != existing?.id }, data.trips, odometerKm, date
        ) as? FuelEstimate.Available)?.let { it.fraction * 100f }
    }
    val gaugeBefore = gaugeBeforeOverride ?: estimatedBefore?.let { Math.round(it / 5f) * 5f } ?: 25f

    // Derive the missing value from price and whichever amount the user knows.
    val priceValue = price.toFloatOrNull()?.takeIf { it > 0f }
    val displayVolume: Float? = when (mode) {
        AmountMode.COST -> cost.toFloatOrNull()?.let { c -> priceValue?.let { c / it } }
        AmountMode.VOLUME -> volume.toFloatOrNull()
        AmountMode.GAUGE -> capacity?.let { Units.fuel(it * (gaugeAfter - gaugeBefore) / 100f, unit) }
    }?.takeIf { it > 0f }
    val totalCost: Float? = when (mode) {
        AmountMode.COST -> cost.toFloatOrNull()
        else -> displayVolume?.let { v -> priceValue?.let { v * it } }
    }?.takeIf { it > 0f }

    // Odometer must fit between the fill-ups logged before and after this date.
    val others = data.fillups.filter { it.id != existing?.id }
    val previous = others.filter { it.date <= date }.maxByOrNull { it.odometer }
    val next = others.filter { it.date > date }.minByOrNull { it.odometer }
    val odometerError = when {
        odometerKm == null -> null
        previous != null && odometerKm < previous.odometer ->
            "Lower than the previous fill-up (${Units.formatOdometer(previous.odometer, unit)})"
        next != null && odometerKm > next.odometer ->
            "Higher than a later fill-up (${Units.formatOdometer(next.odometer, unit)})"
        else -> null
    }
    val odometerHint = if (odometerError == null && odometerKm != null && previous != null) {
        "+${Units.formatWholeDistance((odometerKm - previous.odometer).toFloat(), unit)} since last fill-up"
    } else null

    val canSave = odometerKm != null && odometerError == null && displayVolume != null && totalCost != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.navigationBars) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (existing == null) "Log fill-up" else "Edit fill-up",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(formatRelativeDay(date)) },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, Modifier.size(18.dp)) }
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isFull,
                    onClick = { isFull = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Full tank") }
                SegmentedButton(
                    selected = !isFull,
                    onClick = { isFull = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Partial") }
            }

            NumberField(
                value = odometer,
                onValueChange = { odometer = it },
                label = "Odometer",
                decimals = 0,
                suffix = Units.distanceLabel(unit),
                supportingText = odometerError ?: odometerHint,
                isError = odometerError != null,
                modifier = Modifier.fillMaxWidth()
            )

            NumberField(
                value = price,
                onValueChange = { price = it },
                label = "Price per ${Units.priceUnitLabel(unit)}",
                decimals = 3,
                prefix = money.settings.symbol.takeIf { it.isNotBlank() }?.let { "$it " },
                supportingText = if (existing == null && pricePrefill != null) "Same as last time" else null,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "I know the",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val modes = if (!isFull && capacity != null) AmountMode.entries else listOf(AmountMode.COST, AmountMode.VOLUME)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = {
                                // Carry the current figure over so switching modes never loses input.
                                when (m) {
                                    AmountMode.COST -> totalCost?.let { cost = it.toInputString(money.settings.decimals) }
                                    AmountMode.VOLUME -> displayVolume?.let { volume = it.toInputString(2) }
                                    AmountMode.GAUGE -> Unit
                                }
                                mode = m
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            icon = {}
                        ) { Text(m.label, maxLines = 1) }
                    }
                }
            }

            when (mode) {
                AmountMode.COST -> NumberField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = "Amount paid",
                    decimals = money.settings.decimals,
                    prefix = money.settings.symbol.takeIf { it.isNotBlank() }?.let { "$it " },
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth()
                )
                AmountMode.VOLUME -> NumberField(
                    value = volume,
                    onValueChange = { volume = it },
                    label = "Fuel added",
                    decimals = 2,
                    suffix = Units.fuelLabel(unit),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth()
                )
                AmountMode.GAUGE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GaugeSlider(
                        label = "Gauge after refuel",
                        value = gaugeAfter,
                        caption = null,
                        onValueChange = { gaugeAfter = it }
                    )
                    // Most people don't look at the needle before filling up, so
                    // the estimated fuel left is used unless they choose to adjust it.
                    if (estimatedBefore != null && !adjustBefore) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Before refuel ≈ ${gaugeBefore.toInt()}%", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Estimated fuel left",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { adjustBefore = true }) { Text("Adjust") }
                            }
                        }
                    } else {
                        GaugeSlider(
                            label = "Gauge before refuel",
                            value = gaugeBefore,
                            caption = null,
                            onValueChange = { gaugeBeforeOverride = it }
                        )
                        if (estimatedBefore != null) {
                            TextButton(onClick = { adjustBefore = false; gaugeBeforeOverride = null }) {
                                Text("Use estimate (${Math.round(estimatedBefore / 5f) * 5}%)")
                            }
                        } else {
                            Text(
                                "No estimate yet: log two full-tank fill-ups to have this filled in for you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        if (gaugeAfter > gaugeBefore) {
                            "Adds ${(gaugeAfter - gaugeBefore).toInt()}% of the tank"
                        } else "After must be higher than before",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (gaugeAfter > gaugeBefore) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            FillupSummary(
                volume = displayVolume,
                cost = totalCost,
                unit = unit,
                showVolume = mode != AmountMode.VOLUME,
                showCost = mode != AmountMode.COST
            )

            TextButton(onClick = { showDetails = !showDetails }) {
                Icon(
                    if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("Station and note")
            }
            AnimatedVisibility(showDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = station,
                        onValueChange = { station = it },
                        label = { Text("Station") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existing != null) {
                    TextButton(
                        onClick = { onDelete(existing) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = canSave,
                    onClick = {
                        val base = existing ?: Fillup(
                            vehicleId = vehicle.id, date = date, odometer = 0,
                            liters = 0f, pricePerUnit = 0f, totalCost = 0f
                        )
                        val liters = Units.fuelToLiters(displayVolume!!, unit)
                        onSave(
                            base.copy(
                                date = date,
                                odometer = odometerKm!!,
                                liters = liters,
                                pricePerUnit = priceValue?.let { Units.priceToPerLiter(it, unit) } ?: (totalCost!! / liters),
                                totalCost = totalCost!!,
                                isFullTank = isFull,
                                stationName = station.trim().ifBlank { null },
                                note = note.trim().ifBlank { null }
                            )
                        )
                    }
                ) { Text("Save") }
            }
        }
    }

    if (showDatePicker) {
        DateTimeKeepingPicker(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPicked = { date = it }
        )
    }
}

@Composable
private fun GaugeSlider(
    label: String,
    value: Float,
    caption: String?,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text("${value.toInt()}%", style = MaterialTheme.typography.titleMedium)
        }
        // 5% steps, like reading a needle between gauge marks.
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..100f, steps = 19)
    }
}

@Composable
private fun FillupSummary(
    volume: Float?,
    cost: Float?,
    unit: DistanceUnit,
    showVolume: Boolean,
    showCost: Boolean
) {
    val money = LocalMoney.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Fuel", style = MaterialTheme.typography.labelMedium)
                Text(
                    volume?.let { Units.formatFuel(Units.fuelToLiters(it, unit), unit) } ?: "--",
                    style = if (showVolume) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Total", style = MaterialTheme.typography.labelMedium)
                Text(
                    cost?.let { money.format(it) } ?: "--",
                    style = if (showCost) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
