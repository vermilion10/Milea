package com.github.vermilion10.milea.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.vermilion10.milea.data.model.Expense
import com.github.vermilion10.milea.data.model.ExpenseCategory
import com.github.vermilion10.milea.data.repository.ExpenseRepository
import com.github.vermilion10.milea.data.repository.VehicleRepository
import com.github.vermilion10.milea.ui.components.NoActiveVehicleMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {
    val activeVehicle = vehicleRepository.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)
    val selectedVehicleId = _selectedVehicleId.asStateFlow()

    val expenses = selectedVehicleId
        .filterNotNull()
        .flatMapLatest { vehicleId ->
            expenseRepository.getExpensesByVehicle(vehicleId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog = _showAddDialog.asStateFlow()

    private val _expenseToEdit = MutableStateFlow<Expense?>(null)
    val expenseToEdit = _expenseToEdit.asStateFlow()

    fun setActiveVehicle(vehicleId: Long) {
        _selectedVehicleId.value = vehicleId
    }

    fun setShowAddDialog(show: Boolean) {
        _showAddDialog.value = show
    }

    fun setExpenseToEdit(expense: Expense?) {
        _expenseToEdit.value = expense
    }

    fun addExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.insertExpense(expense)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.updateExpense(expense.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    viewModel: ExpenseListViewModel = hiltViewModel()
) {
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val expenseToEdit by viewModel.expenseToEdit.collectAsState()

    LaunchedEffect(activeVehicle) {
        activeVehicle?.let { viewModel.setActiveVehicle(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (activeVehicle != null) {
                FloatingActionButton(
                    onClick = { viewModel.setShowAddDialog(true) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            }
        }
    ) { padding ->
        if (activeVehicle == null) {
            NoActiveVehicleMessage(
                padding = padding,
                message = "Add a vehicle first to log expenses."
            )
        } else if (expenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No expenses recorded yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Track maintenance, insurance, and more",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses) { expense ->
                    ExpenseCard(
                        expense = expense,
                        unit = activeVehicle?.odometerUnit
                            ?: com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
                        onEdit = { viewModel.setExpenseToEdit(expense) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddExpenseDialog(
                vehicleId = viewModel.selectedVehicleId.value ?: 0,
                unit = activeVehicle?.odometerUnit
                    ?: com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
                onDismiss = { viewModel.setShowAddDialog(false) },
                onSave = { expense ->
                    viewModel.addExpense(expense)
                    viewModel.setShowAddDialog(false)
                }
            )
        }

        expenseToEdit?.let { expense ->
            AddExpenseDialog(
                vehicleId = expense.vehicleId,
                expense = expense,
                unit = activeVehicle?.odometerUnit
                    ?: com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
                onDismiss = { viewModel.setExpenseToEdit(null) },
                onSave = { updated ->
                    viewModel.updateExpense(updated)
                    viewModel.setExpenseToEdit(null)
                },
                onDelete = {
                    viewModel.deleteExpense(expense)
                    viewModel.setExpenseToEdit(null)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCard(
    expense: Expense,
    unit: com.github.vermilion10.milea.data.model.DistanceUnit,
    onEdit: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (expense.category) {
                    ExpenseCategory.MAINTENANCE -> Icons.Default.Build
                    ExpenseCategory.INSURANCE -> Icons.Default.Security
                    ExpenseCategory.TOLLS -> Icons.Default.Toll
                    ExpenseCategory.PARKING -> Icons.Default.LocalParking
                    ExpenseCategory.REGISTRATION -> Icons.Default.Description
                    ExpenseCategory.REPAIRS -> Icons.Default.BuildCircle
                    ExpenseCategory.OTHER -> Icons.Default.MoreHoriz
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.category.name.lowercase()
                        .replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormat.format(Date(expense.date)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    expense.odometer?.let { odometer ->
                        Text(
                            text = " \u2022 ${com.github.vermilion10.milea.util.Units.formatDistanceNumber(odometer.toFloat(), unit)} ${com.github.vermilion10.milea.util.Units.distanceLabel(unit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                expense.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                text = "$${String.format("%.2f", expense.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit Expense",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    vehicleId: Long,
    expense: Expense? = null,
    unit: com.github.vermilion10.milea.data.model.DistanceUnit = com.github.vermilion10.milea.data.model.DistanceUnit.KILOMETERS,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isEditing = expense != null
    var category by remember { mutableStateOf(expense?.category ?: ExpenseCategory.OTHER) }
    var amount by remember { mutableStateOf(expense?.amount?.toString() ?: "") }
    var description by remember { mutableStateOf(expense?.description ?: "") }
    var odometer by remember {
        mutableStateOf(
            expense?.odometer?.let {
                com.github.vermilion10.milea.util.Units.formatDistanceNumber(it.toFloat(), unit)
            } ?: ""
        )
    }

    val distanceLabel = com.github.vermilion10.milea.util.Units.distanceLabel(unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Expense" else "Add Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var expanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = category.name.lowercase().replaceFirstChar { it.titlecase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ExpenseCategory.values().forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name.lowercase().replaceFirstChar { it.titlecase() }) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Odometer ($distanceLabel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountValue = amount.toFloatOrNull() ?: 0f
                    val odometerDisplay = odometer.toFloatOrNull()
                    val odometerKm = odometerDisplay?.let {
                        if (unit == com.github.vermilion10.milea.data.model.DistanceUnit.MILES) {
                            (it / 0.621371f).toLong()
                        } else {
                            it.toLong()
                        }
                    }

                    if (amountValue > 0) {
                        val base = expense ?: Expense(
                            vehicleId = vehicleId,
                            date = System.currentTimeMillis(),
                            category = category,
                            amount = amountValue
                        )
                        onSave(
                            base.copy(
                                vehicleId = vehicleId,
                                category = category,
                                amount = amountValue,
                                description = description.ifBlank { null },
                                odometer = odometerKm
                            )
                        )
                    }
                },
                enabled = amount.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
