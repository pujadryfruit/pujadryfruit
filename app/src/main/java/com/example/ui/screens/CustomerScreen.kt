package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.CustomerEntity
import com.example.ui.components.CustomSearchBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyUtils
import com.example.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    viewModel: ShopViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToInvoice: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var filterCreditOnly by remember { mutableStateOf(false) }

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var selectedCustomerForDetail by remember { mutableStateOf<CustomerEntity?>(null) }
    var showRepayDialog by remember { mutableStateOf(false) }

    val filteredCustomers = customers.filter { cust ->
        val matchesSearch = searchQuery.isBlank() ||
                cust.name.contains(searchQuery, ignoreCase = true) ||
                cust.mobile.contains(searchQuery) ||
                cust.address.contains(searchQuery, ignoreCase = true)
        val matchesCredit = !filterCreditOnly || (cust.outstandingBalance > 0)
        matchesSearch && matchesCredit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Customer Directory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${customers.size} Customers • Udhaar: ${CurrencyUtils.formatInr(metrics.totalOutstandingCredit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddCustomerDialog = true },
                        modifier = Modifier.testTag("add_customer_header_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddCustomerDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CustomSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search by customer name, mobile or city..."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !filterCreditOnly,
                            onClick = { filterCreditOnly = false },
                            label = { Text("All Customers (${customers.size})") }
                        )
                        FilterChip(
                            selected = filterCreditOnly,
                            onClick = { filterCreditOnly = true },
                            label = { Text("Pending Udhaar Only (${customers.count { it.outstandingBalance > 0 }})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PurpleUdhaar,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (filteredCustomers.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.PeopleAlt,
                        title = "No Customers Found",
                        description = "Register regular customers and wholesale buyers to track order history and credit ledgers.",
                        actionButtonText = "+ Add New Customer",
                        onActionClick = { showAddCustomerDialog = true }
                    )
                }
            } else {
                items(filteredCustomers) { cust ->
                    CustomerCard(
                        customer = cust,
                        onSelect = { selectedCustomerForDetail = cust },
                        onRepayClick = {
                            selectedCustomerForDetail = cust
                            showRepayDialog = true
                        },
                        onWhatsAppClick = {
                            val msg = "Namaste ${cust.name} ji, Greetings from Puja Dry Fruits!"
                            ShareUtils.shareViaWhatsApp(context, cust.mobile, msg)
                        }
                    )
                }
            }
        }
    }

    // Add Customer Dialog
    if (showAddCustomerDialog) {
        var name by remember { mutableStateOf("") }
        var mobile by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            title = { Text("Add Customer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Customer Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_cust_name")
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("add_cust_mobile")
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("City / Delivery Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addCustomer(name, mobile, address)
                            showAddCustomerDialog = false
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save Customer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Record Repayment Dialog
    if (showRepayDialog && selectedCustomerForDetail != null) {
        val cust = selectedCustomerForDetail!!
        var repayAmount by remember { mutableStateOf(cust.outstandingBalance.toString()) }
        var repayMode by remember { mutableStateOf("Cash") }
        var repayNote by remember { mutableStateOf("Payment received towards pending bill") }

        AlertDialog(
            onDismissRequest = { showRepayDialog = false },
            title = { Text("Receive Udhaar Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${cust.name} • Balance: ${CurrencyUtils.formatInr(cust.outstandingBalance)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PurpleUdhaar
                    )

                    OutlinedTextField(
                        value = repayAmount,
                        onValueChange = { repayAmount = it },
                        label = { Text("Received Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("repay_amount_input"),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cash", "UPI", "Bank").forEach { mode ->
                            FilterChip(
                                selected = mode == repayMode,
                                onClick = { repayMode = mode },
                                label = { Text(mode) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = repayNote,
                        onValueChange = { repayNote = it },
                        label = { Text("Notes / Reference") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = repayAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.recordUdhaarRepayment(cust.id, amt, repayMode, repayNote)
                            showRepayDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Confirm Receipt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepayDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CustomerCard(
    customer: CustomerEntity,
    onSelect: () -> Unit,
    onRepayClick: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onSelect() }
            .testTag("customer_card_${customer.name.replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (customer.mobile.isNotEmpty()) {
                        Text(
                            text = "📱 ${customer.mobile}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (customer.address.isNotEmpty()) {
                        Text(
                            text = "📍 ${customer.address}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (customer.outstandingBalance > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Udhaar Balance",
                            style = MaterialTheme.typography.labelSmall,
                            color = PurpleUdhaar,
                            fontSize = 10.sp
                        )
                        Text(
                            text = CurrencyUtils.formatInr(customer.outstandingBalance),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PurpleUdhaar
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Spend & Order Stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Spent: ${CurrencyUtils.formatInr(customer.totalSpent)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${customer.totalOrders} Orders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (customer.mobile.isNotEmpty()) {
                    IconButton(onClick = onWhatsAppClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                    }
                }
                if (customer.outstandingBalance > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onRepayClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleUdhaar),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Receive ₹", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
