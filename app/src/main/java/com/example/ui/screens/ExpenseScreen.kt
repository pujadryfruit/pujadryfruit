package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ExpenseEntity
import com.example.ui.components.CustomSearchBar
import com.example.ui.components.DateFilterChips
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyUtils
import com.example.util.DateFilterRange
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    viewModel: ShopViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val categoryExpenses by viewModel.categoryExpenses.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dashboardDateFilter.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val categories = listOf("All", "Shop Rent", "Staff Salary", "Packaging", "Electricity", "Transport", "Tea & Snacks", "Maintenance", "Other")

    val filteredExpenses = expenses.filter { exp ->
        val matchesSearch = searchQuery.isBlank() ||
                exp.description.contains(searchQuery, ignoreCase = true) ||
                exp.category.contains(searchQuery, ignoreCase = true)
        val matchesCat = selectedCategoryFilter == "All" || exp.category.equals(selectedCategoryFilter, ignoreCase = true)
        matchesSearch && matchesCat
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Shop Expenses Tracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "दुकान खर्चे • ${dateFilter.label}: ${CurrencyUtils.formatInr(metrics.totalExpenses)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddExpenseDialog = true },
                        modifier = Modifier.testTag("add_expense_header_btn")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Expense", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddExpenseDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .testTag("add_expense_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date Filter
            item {
                DateFilterChips(
                    selectedRange = dateFilter,
                    onRangeSelected = { viewModel.setDashboardDateFilter(it) }
                )
            }

            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total ${dateFilter.label} Expenses",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                            Text(
                                text = CurrencyUtils.formatInr(metrics.totalExpenses),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = ErrorRed
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ErrorRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ErrorRed)
                        }
                    }
                }
            }

            // Category Distribution Card (if any)
            if (categoryExpenses.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Expense Breakdown by Category",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            categoryExpenses.forEach { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${cat.category} (${cat.count})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(cat.totalAmount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ErrorRed
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = "Search expenses..."
                )
            }

            // Category Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = cat == selectedCategoryFilter,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.AddCard,
                        title = "No Expenses Logged",
                        description = "Record shop utilities, packaging material, transport or staff payments to accurately compute net business profit.",
                        actionButtonText = "+ Add Expense",
                        onActionClick = { showAddExpenseDialog = true }
                    )
                }
            } else {
                items(filteredExpenses) { expense ->
                    ExpenseItemCard(
                        expense = expense,
                        onDelete = { viewModel.deleteExpense(expense.id) }
                    )
                }
            }
        }
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        var category by remember { mutableStateOf("Shop Rent") }
        var description by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var paymentMethod by remember { mutableStateOf("UPI") }
        var notes by remember { mutableStateOf("") }

        val expenseCategories = listOf("Shop Rent", "Staff Salary", "Packaging Boxes", "Electricity Bill", "Transport / Freight", "Tea & Refreshments", "Shop Maintenance", "Other")

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text("Record Expense") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        expenseCategories.forEach { cat ->
                            FilterChip(
                                selected = cat == category,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description *") },
                        placeholder = { Text("e.g. November Shop Rent / 500 Gift Boxes") },
                        modifier = Modifier.fillMaxWidth().testTag("expense_desc_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("expense_amount_input"),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("UPI", "Cash", "Bank").forEach { mode ->
                            FilterChip(
                                selected = mode == paymentMethod,
                                onClick = { paymentMethod = mode },
                                label = { Text(mode) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                        if (parsedAmount > 0 && description.isNotBlank()) {
                            viewModel.addExpense(category, description, parsedAmount, paymentMethod, notes)
                            showAddExpenseDialog = false
                        }
                    },
                    enabled = amount.isNotBlank() && description.isNotBlank()
                ) {
                    Text("Save Expense")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ExpenseItemCard(
    expense: ExpenseEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${expense.category} • ${DateUtils.formatDateTime(expense.expenseDate)} • ${expense.paymentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CurrencyUtils.formatInr(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
