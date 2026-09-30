package com.github.vermilion10.milea.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.Expense
import com.github.vermilion10.milea.data.model.ExpenseCategory
import com.github.vermilion10.milea.data.repository.ExpenseRepository
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

data class ExpenseListState(
    val data: VehicleData? = null,
    val yearTotal: Float = 0f,
    val loaded: Boolean = false
)

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    vehicleDataSource: VehicleDataSource
) : ViewModel() {
    val state = vehicleDataSource.selectedVehicleData()
        .map { data ->
            val yearStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            ExpenseListState(
                data = data,
                yearTotal = data?.expenses?.filter { it.date >= yearStart }
                    ?.sumOf { it.amount.toDouble() }?.toFloat() ?: 0f,
                loaded = true
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseListState())

    fun save(expense: Expense) {
        viewModelScope.launch {
            if (expense.id == 0L) expenseRepository.insertExpense(expense)
            else expenseRepository.updateExpense(expense.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete(expense: Expense) {
        viewModelScope.launch { expenseRepository.deleteExpense(expense) }
    }

    fun restore(expense: Expense) {
        viewModelScope.launch { expenseRepository.insertExpense(expense) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onAddVehicle: () -> Unit = {},
    viewModel: ExpenseListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val data = state.data
    val unit = data?.vehicle?.odometerUnit ?: DistanceUnit.KILOMETERS

    var editorOpen by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<Expense?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("Expenses") }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (data != null) {
                ExtendedFloatingActionButton(
                    onClick = { editorTarget = null; editorOpen = true },
                    expanded = listState.firstVisibleItemIndex == 0,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add expense") }
                )
            }
        }
    ) { padding ->
        when {
            !state.loaded -> Unit
            data == null -> NoActiveVehicleMessage(padding, "Add a vehicle first to log expenses.", onAddVehicle)
            data.expenses.isEmpty() -> EmptyState(
                icon = Icons.Default.Receipt,
                title = "No expenses yet",
                message = "Track servicing, insurance, tolls, parking and more to see what the vehicle really costs to run.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = { editorTarget = null; editorOpen = true }) { Text("Add expense") } }
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    StatTile(
                        label = "Spent this year",
                        value = money.format(state.yearTotal),
                        supporting = "Excludes fuel",
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                }
                var lastMonth: String? = null
                data.expenses.forEach { expense ->
                    val month = formatMonthYear(expense.date)
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
                    item(key = expense.id) {
                        ExpenseItem(expense, unit) { editorTarget = expense; editorOpen = true }
                    }
                }
            }
        }
    }

    if (editorOpen && data != null) {
        ExpenseSheet(
            data = data,
            existing = editorTarget,
            onDismiss = { editorOpen = false },
            onSave = { viewModel.save(it); editorOpen = false },
            onDelete = { expense ->
                viewModel.delete(expense)
                editorOpen = false
                scope.launch {
                    val result = snackbar.showSnackbar("Expense deleted", "Undo", duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(expense)
                }
            }
        )
    }
}

fun ExpenseCategory.icon(): ImageVector = when (this) {
    ExpenseCategory.MAINTENANCE -> Icons.Default.Build
    ExpenseCategory.INSURANCE -> Icons.Default.Security
    ExpenseCategory.TOLLS -> Icons.Default.Toll
    ExpenseCategory.PARKING -> Icons.Default.LocalParking
    ExpenseCategory.REGISTRATION -> Icons.Default.Description
    ExpenseCategory.REPAIRS -> Icons.Default.CarRepair
    ExpenseCategory.OTHER -> Icons.Default.MoreHoriz
}

@Composable
private fun ExpenseItem(expense: Expense, unit: DistanceUnit, onClick: () -> Unit) {
    val money = LocalMoney.current
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        expense.category.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.description ?: expense.category.label(),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    buildString {
                        if (expense.description != null) append(expense.category.label()).append(" · ")
                        append(formatShortDate(expense.date))
                        expense.odometer?.let { append(" · ").append(Units.formatOdometer(it, unit)) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(money.format(expense.amount), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseSheet(
    data: VehicleData,
    existing: Expense?,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit,
    onDelete: (Expense) -> Unit
) {
    val money = LocalMoney.current
    val unit = data.vehicle.odometerUnit
    var category by remember { mutableStateOf(existing?.category ?: ExpenseCategory.MAINTENANCE) }
    var amount by remember { mutableStateOf(existing?.amount?.toInputString(money.settings.decimals) ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var odometer by remember {
        mutableStateOf(existing?.odometer?.let { Math.round(Units.distance(it.toFloat(), unit)).toString() } ?: "")
    }
    var date by remember { mutableLongStateOf(existing?.date ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val amountValue = amount.toFloatOrNull()?.takeIf { it > 0f }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                    if (existing == null) "Add expense" else "Edit expense",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(formatRelativeDay(date)) },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, Modifier.size(18.dp)) }
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ExpenseCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label()) },
                        leadingIcon = { Icon(c.icon(), contentDescription = null, Modifier.size(18.dp)) }
                    )
                }
            }
            NumberField(
                value = amount,
                onValueChange = { amount = it },
                label = "Amount",
                decimals = money.settings.decimals,
                prefix = money.settings.symbol.takeIf { it.isNotBlank() }?.let { "$it " },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            NumberField(
                value = odometer,
                onValueChange = { odometer = it },
                label = "Odometer (optional)",
                decimals = 0,
                suffix = Units.distanceLabel(unit),
                supportingText = if (odometer.isBlank()) "Current: ${Units.formatOdometer(data.currentOdometerKm, unit)}" else null,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) {
                    TextButton(
                        onClick = { onDelete(existing) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = amountValue != null,
                    onClick = {
                        val base = existing ?: Expense(
                            vehicleId = data.vehicle.id, date = date, category = category, amount = 0f
                        )
                        onSave(
                            base.copy(
                                date = date,
                                category = category,
                                amount = amountValue!!,
                                description = description.trim().ifBlank { null },
                                odometer = odometer.toFloatOrNull()?.let { Math.round(Units.distanceToKm(it, unit)).toLong() }
                            )
                        )
                    }
                ) { Text("Save") }
            }
        }
    }

    if (showDatePicker) {
        DateTimeKeepingPicker(initial = date, onDismiss = { showDatePicker = false }, onPicked = { date = it })
    }
}
