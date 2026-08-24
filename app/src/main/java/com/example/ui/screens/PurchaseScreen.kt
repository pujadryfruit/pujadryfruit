package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ProductEntity
import com.example.data.local.entities.PurchaseEntity
import com.example.ui.components.CustomSearchBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: ShopViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val purchases by viewModel.allPurchases.collectAsStateWithLifecycle()
    val allProducts by viewModel.allActiveProducts.collectAsStateWithLifecycle()

    var showAddPurchaseSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPurchases = purchases.filter { p ->
        searchQuery.isBlank() ||
                p.supplierName.contains(searchQuery, ignoreCase = true) ||
                p.invoiceReference.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Purchases / Stock Inward",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "खरीद और माल आवक • Supplier Invoices",
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
                        onClick = { showAddPurchaseSheet = true },
                        modifier = Modifier.testTag("add_purchase_btn")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Record Stock In", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPurchaseSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = "Record Stock In")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = "Search by supplier or invoice no..."
                )
            }

            if (filteredPurchases.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.LocalShipping,
                        title = "No Purchases Recorded",
                        description = "Add wholesale dry fruit procurement to automatically increase your stock inventory.",
                        actionButtonText = "+ Record Inward Stock",
                        onActionClick = { showAddPurchaseSheet = true }
                    )
                }
            } else {
                items(filteredPurchases) { purchase ->
                    PurchaseCard(purchase = purchase)
                }
            }
        }
    }

    // Add Purchase Bottom Sheet
    if (showAddPurchaseSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddPurchaseSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddPurchaseSheetContent(
                products = allProducts,
                onSave = { supplier, phone, invRef, mode, status, notes, items, priceOverrides ->
                    viewModel.recordPurchase(supplier, phone, invRef, mode, status, notes, items, priceOverrides)
                    showAddPurchaseSheet = false
                },
                onCancel = { showAddPurchaseSheet = false }
            )
        }
    }
}

@Composable
fun PurchaseCard(purchase: PurchaseEntity) {
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
                Column {
                    Text(
                        text = purchase.supplierName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ref: ${if (purchase.invoiceReference.isNotEmpty()) purchase.invoiceReference else "PUR-${purchase.id}"} • ${DateUtils.formatDateTime(purchase.purchaseDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = purchase.paymentStatus)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mode: ${purchase.paymentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = CurrencyUtils.formatInr(purchase.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (purchase.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Note: ${purchase.notes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddPurchaseSheetContent(
    products: List<ProductEntity>,
    onSave: (
        supplierName: String,
        supplierPhone: String,
        invoiceRef: String,
        paymentMethod: String,
        paymentStatus: String,
        notes: String,
        items: List<Pair<ProductEntity, Double>>,
        priceOverrides: Map<Long, Double>
    ) -> Unit,
    onCancel: () -> Unit
) {
    var supplierName by remember { mutableStateOf("") }
    var supplierPhone by remember { mutableStateOf("") }
    var invoiceRef by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Bank Transfer") }
    var paymentStatus by remember { mutableStateOf("PAID") }
    var notes by remember { mutableStateOf("") }

    val selectedItems = remember { mutableStateMapOf<Long, Double>() } // productId -> quantity
    val priceOverrides = remember { mutableStateMapOf<Long, Double>() } // productId -> cost price

    val paymentMethods = listOf("Bank Transfer", "UPI", "Cash", "Cheque", "Credit")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Record Stock Procurement",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = { Text("Supplier Name / Firm *") },
                    placeholder = { Text("e.g. Kashmir Nut Exporters") },
                    modifier = Modifier.fillMaxWidth().testTag("supplier_name_input"),
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = supplierPhone,
                        onValueChange = { supplierPhone = it },
                        label = { Text("Supplier Mobile") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    OutlinedTextField(
                        value = invoiceRef,
                        onValueChange = { invoiceRef = it },
                        label = { Text("Bill / Invoice Ref") },
                        placeholder = { Text("SUP-882") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            item {
                Text("Payment Method", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    paymentMethods.forEach { method ->
                        FilterChip(
                            selected = method == paymentMethod,
                            onClick = { paymentMethod = method },
                            label = { Text(method) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Select Products & Inward Quantities",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(products) { prod ->
                val qty = selectedItems[prod.id] ?: 0.0
                val cost = priceOverrides[prod.id] ?: prod.purchasePrice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (qty > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(text = prod.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(text = "Current: ${CurrencyUtils.formatQuantity(prod.currentStock, prod.unit)} • Cost: ₹$cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }

                        OutlinedTextField(
                            value = if (qty > 0) qty.toString() else "",
                            onValueChange = {
                                val parsed = it.toDoubleOrNull() ?: 0.0
                                if (parsed > 0) {
                                    selectedItems[prod.id] = parsed
                                } else {
                                    selectedItems.remove(prod.id)
                                }
                            },
                            label = { Text("+ Qty (${prod.unit})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(130.dp),
                            singleLine = true
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Batch Details") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val totalProcuredProducts = selectedItems.size
        Button(
            onClick = {
                if (supplierName.isNotBlank() && selectedItems.isNotEmpty()) {
                    val itemList = selectedItems.mapNotNull { (prodId, qty) ->
                        val product = products.find { it.id == prodId }
                        if (product != null) product to qty else null
                    }
                    onSave(
                        supplierName.trim(),
                        supplierPhone.trim(),
                        invoiceRef.trim(),
                        paymentMethod,
                        paymentStatus,
                        notes.trim(),
                        itemList,
                        priceOverrides.toMap()
                    )
                }
            },
            enabled = supplierName.isNotBlank() && selectedItems.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_purchase_submit_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save Stock Inward ($totalProcuredProducts items)", fontWeight = FontWeight.Bold)
        }
    }
}
