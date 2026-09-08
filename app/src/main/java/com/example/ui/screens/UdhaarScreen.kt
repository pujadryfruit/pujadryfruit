package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.ShopProfile
import com.example.ui.components.CustomSearchBar
import com.example.ui.components.DateFilterChips
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyUtils
import com.example.util.DateFilterRange
import com.example.util.DateUtils
import com.example.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdhaarScreen(
    viewModel: ShopViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shopProfile by viewModel.shopProfile.collectAsStateWithLifecycle()
    val creditCustomers by viewModel.customersWithUdhaar.collectAsStateWithLifecycle()
    val creditPayments by viewModel.creditPaymentsInDateRange.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dashboardDateFilter.collectAsStateWithLifecycle()
    val dateRange by viewModel.currentDashboardDateRange.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCustomerForRepay by remember { mutableStateOf<CustomerEntity?>(null) }
    var showRepayDialog by remember { mutableStateOf(false) }

    val filtered = creditCustomers.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.mobile.contains(searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Customer Khata / Udhaar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "उधार बही-खाता • Credit Ledger",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
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
            // Tab Row
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Pending Khata (${creditCustomers.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Collections (${creditPayments.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            if (selectedTab == 0) {
            // Header Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PurpleUdhaarLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Total Pending Udhaar Balance",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PurpleUdhaar
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyUtils.formatInr(metrics.totalOutstandingCredit),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurpleUdhaar
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${creditCustomers.size} Customers with pending credit balance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = "Search customer in khata..."
                )
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.CheckCircle,
                        title = "No Pending Udhaar!",
                        description = "Great job! All customer accounts are settled and fully paid up."
                    )
                }
            } else {
                items(filtered) { cust ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cust.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (cust.mobile.isNotEmpty()) {
                                        Text(
                                            text = "📱 ${cust.mobile}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Due Amount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PurpleUdhaar,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(cust.outstandingBalance),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PurpleUdhaar
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // WhatsApp Payment Reminder
                                if (cust.mobile.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            val reminder = "Namaste ${cust.name} ji,\nThis is a gentle payment reminder from Puja Dry Fruits for your pending balance of ${CurrencyUtils.formatInr(cust.outstandingBalance)}.\nKindly pay via UPI: ${shopProfile?.upiId ?: "pujadryfruits@upi"}.\nThank you!"
                                            ShareUtils.shareViaWhatsApp(context, cust.mobile, reminder)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Remind", fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        selectedCustomerForRepay = cust
                                        showRepayDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("receive_payment_btn")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Receive ₹", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
                // Period Collections & Recovery Tab
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DateFilterChips(
                            selectedRange = dateFilter,
                            activeDateRange = dateRange,
                            onRangeSelected = { viewModel.setDashboardDateFilter(it) },
                            onSelectMonthYear = { y, m -> viewModel.selectMonthYearFilter(y, m) },
                            onSelectWeek = { s, e -> viewModel.selectWeekFilter(s, e) },
                            onCustomRange = { s, e -> viewModel.setDashboardDateFilter(DateFilterRange.CUSTOM, Pair(s, e)) }
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Period: ${dateRange.displayLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (dateFilter != DateFilterRange.TODAY) {
                                    Text(
                                        text = "Reset to Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { viewModel.setDashboardDateFilter(DateFilterRange.TODAY) }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Summary of collections in period
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Udhaar Recovery in Period (${dateRange.displayLabel})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Credit Collected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = CurrencyUtils.formatInr(metrics.totalCreditCollected),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SuccessGreen
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("New Credit Given", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = CurrencyUtils.formatInr(metrics.totalCreditGiven),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ErrorRed
                                    )
                                }
                            }
                        }
                    }
                }

                if (creditPayments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.ReceiptLong,
                            title = "No Collections in Period",
                            description = "No customer credit repayments were recorded for ${dateRange.displayLabel}."
                        )
                    }
                } else {
                    items(creditPayments) { payment ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = payment.customerName.ifBlank { "Customer #${payment.customerId}" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${payment.paymentMethod} • ${DateUtils.formatDateTime(payment.paymentDate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (payment.notes.isNotBlank()) {
                                        Text(
                                            text = payment.notes,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Text(
                                    text = "+${CurrencyUtils.formatInr(payment.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Repay Dialog
    if (showRepayDialog && selectedCustomerForRepay != null) {
        val cust = selectedCustomerForRepay!!
        var repayAmount by remember { mutableStateOf(cust.outstandingBalance.toString()) }
        var repayMode by remember { mutableStateOf("Cash") }
        var repayNote by remember { mutableStateOf("Udhaar balance clearance") }

        AlertDialog(
            onDismissRequest = { showRepayDialog = false },
            title = { Text("Receive Payment: ${cust.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total Due: ${CurrencyUtils.formatInr(cust.outstandingBalance)}", color = PurpleUdhaar, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = repayAmount,
                        onValueChange = { repayAmount = it },
                        label = { Text("Amount Received (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("udhaar_repay_input"),
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
                        label = { Text("Notes / Receipt Reference") },
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
                    Text("Confirm Payment Received")
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
