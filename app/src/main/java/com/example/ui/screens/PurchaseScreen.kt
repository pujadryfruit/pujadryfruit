package com.example.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.material.icons.outlined.*
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
import com.example.data.local.entities.ProductEntity
import com.example.data.local.entities.PurchaseEntity
import com.example.data.local.entities.SupplierEntity
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
    val allSuppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()

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
                suppliers = allSuppliers,
                onAddSupplier = { name, phone, company, address, gstin, onDone ->
                    val supp = SupplierEntity(
                        name = name,
                        mobile = phone,
                        contactPerson = company,
                        address = address,
                        gstin = gstin
                    )
                    viewModel.addSupplier(supp) { saved ->
                        onDone(saved)
                    }
                },
                onSave = { supplier, phone, invRef, mode, status, notes, items, priceOverrides, onComplete ->
                    viewModel.recordPurchase(supplier, phone, invRef, mode, status, notes, items, priceOverrides) {
                        onComplete()
                        showAddPurchaseSheet = false
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPurchaseSheetContent(
    products: List<ProductEntity>,
    suppliers: List<SupplierEntity>,
    onAddSupplier: (name: String, phone: String, company: String, address: String, gstin: String, onDone: (SupplierEntity) -> Unit) -> Unit,
    onSave: (
        supplierName: String,
        supplierPhone: String,
        invoiceRef: String,
        paymentMethod: String,
        paymentStatus: String,
        notes: String,
        items: List<Pair<ProductEntity, Double>>,
        priceOverrides: Map<Long, Double>,
        onComplete: () -> Unit
    ) -> Unit,
    onCancel: () -> Unit
) {
    var supplierQuery by remember { mutableStateOf("") }
    var selectedSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var supplierPhone by remember { mutableStateOf("") }
    var isSupplierDropdownExpanded by remember { mutableStateOf(false) }

    // Dialog for adding a new supplier
    var showNewSupplierDialog by remember { mutableStateOf(false) }
    var newSupplierName by remember { mutableStateOf("") }
    var newSupplierPhone by remember { mutableStateOf("") }
    var newSupplierAddress by remember { mutableStateOf("") }
    var newSupplierGstin by remember { mutableStateOf("") }
    var isAddingSupplier by remember { mutableStateOf(false) }

    var invoiceRef by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Bank Transfer") }
    var paymentStatus by remember { mutableStateOf("PAID") }
    var notes by remember { mutableStateOf("") }

    // Searchable Product Dropdown & Added items
    var productSearchQuery by remember { mutableStateOf("") }
    var isProductDropdownExpanded by remember { mutableStateOf(false) }
    var activeProductToAdd by remember { mutableStateOf<ProductEntity?>(null) }
    var itemQtyInput by remember { mutableStateOf("1") }
    var itemCostInput by remember { mutableStateOf("") }

    val selectedItems = remember { mutableStateMapOf<Long, Pair<ProductEntity, Double>>() } // productId -> Pair(Product, Qty)
    val priceOverrides = remember { mutableStateMapOf<Long, Double>() } // productId -> Cost Price per unit

    var isSavingPurchase by remember { mutableStateOf(false) }

    val paymentMethods = listOf("Bank Transfer", "UPI", "Cash", "Cheque", "Credit / Udhaar")

    val filteredSuppliers = remember(supplierQuery, suppliers) {
        if (supplierQuery.isBlank()) suppliers
        else suppliers.filter {
            it.name.contains(supplierQuery, ignoreCase = true) ||
            it.mobile.contains(supplierQuery, ignoreCase = true) ||
            it.contactPerson.contains(supplierQuery, ignoreCase = true)
        }
    }

    val filteredProducts = remember(productSearchQuery, products) {
        if (productSearchQuery.isBlank()) products.take(8)
        else products.filter {
            it.name.contains(productSearchQuery, ignoreCase = true) ||
            it.hindiName.contains(productSearchQuery, ignoreCase = true) ||
            it.sku.contains(productSearchQuery, ignoreCase = true) ||
            it.category.contains(productSearchQuery, ignoreCase = true)
        }
    }

    // Add New Supplier Dialog
    if (showNewSupplierDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAddingSupplier) showNewSupplierDialog = false },
            title = { Text("Add New Supplier", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newSupplierName,
                        onValueChange = { newSupplierName = it },
                        label = { Text("Supplier / Firm Name *") },
                        placeholder = { Text("e.g. Kashmir Nut Traders") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSupplierPhone,
                        onValueChange = { newSupplierPhone = it },
                        label = { Text("Mobile Number") },
                        placeholder = { Text("e.g. 9876543210") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSupplierAddress,
                        onValueChange = { newSupplierAddress = it },
                        label = { Text("City / Address") },
                        placeholder = { Text("e.g. Khari Baoli, Delhi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSupplierGstin,
                        onValueChange = { newSupplierGstin = it },
                        label = { Text("GSTIN (Optional)") },
                        placeholder = { Text("07AAAAA0000A1Z5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSupplierName.isNotBlank() && !isAddingSupplier) {
                            isAddingSupplier = true
                            onAddSupplier(
                                newSupplierName.trim(),
                                newSupplierPhone.trim(),
                                "",
                                newSupplierAddress.trim(),
                                newSupplierGstin.trim()
                            ) { saved ->
                                isAddingSupplier = false
                                selectedSupplier = saved
                                supplierQuery = saved.name
                                supplierPhone = saved.mobile
                                showNewSupplierDialog = false
                            }
                        }
                    },
                    enabled = newSupplierName.isNotBlank() && !isAddingSupplier
                ) {
                    if (isAddingSupplier) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Save Supplier")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewSupplierDialog = false },
                    enabled = !isAddingSupplier
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Record Stock Procurement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Inward wholesale batch with supplier tracking",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onCancel, enabled = !isSavingPurchase) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Searchable Supplier Section
            item {
                Text("Supplier (आपूर्तिकर्ता)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                
                OutlinedTextField(
                    value = supplierQuery,
                    onValueChange = {
                        supplierQuery = it
                        selectedSupplier = null
                        isSupplierDropdownExpanded = true
                    },
                    label = { Text("Select or Search Supplier *") },
                    placeholder = { Text("Type supplier name or mobile...") },
                    leadingIcon = {
                        Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (supplierQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    supplierQuery = ""
                                    selectedSupplier = null
                                    supplierPhone = ""
                                    isSupplierDropdownExpanded = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { isSupplierDropdownExpanded = !isSupplierDropdownExpanded }) {
                                Icon(
                                    imageVector = if (isSupplierDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Toggle Dropdown"
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_name_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Inline Suggestion Box (Prevents keyboard from closing on typing)
                AnimatedVisibility(
                    visible = isSupplierDropdownExpanded && (selectedSupplier == null || supplierQuery != selectedSupplier?.name)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Quick Add Supplier Button
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSupplierDropdownExpanded = false
                                        newSupplierName = supplierQuery.trim()
                                        newSupplierPhone = ""
                                        newSupplierAddress = ""
                                        newSupplierGstin = ""
                                        showNewSupplierDialog = true
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = GeometricPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (supplierQuery.isNotBlank()) "+ Add New Supplier: \"$supplierQuery\"" else "+ Add New Supplier",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GeometricPrimary
                                    )
                                }
                            }

                            if (filteredSuppliers.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                filteredSuppliers.take(5).forEach { supp ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedSupplier = supp
                                                supplierQuery = supp.name
                                                supplierPhone = supp.mobile
                                                isSupplierDropdownExpanded = false
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Transparent
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(supp.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            val details = buildList {
                                                if (supp.mobile.isNotEmpty()) add(supp.mobile)
                                                if (supp.address.isNotEmpty()) add(supp.address)
                                                if (supp.outstandingBalance > 0) add("Due: ${CurrencyUtils.formatInr(supp.outstandingBalance, false)}")
                                            }.joinToString(" • ")
                                            if (details.isNotEmpty()) {
                                                Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Supplier Mobile & Invoice Ref
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = supplierPhone,
                        onValueChange = { supplierPhone = it },
                        label = { Text("Supplier Mobile") },
                        placeholder = { Text("9876543210") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = invoiceRef,
                        onValueChange = { invoiceRef = it },
                        label = { Text("Bill / Invoice Ref") },
                        placeholder = { Text("SUP-882") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Payment Method & Status
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
                            onClick = {
                                paymentMethod = method
                                if (method.contains("Credit", true)) {
                                    paymentStatus = "UNPAID"
                                }
                            },
                            label = { Text(method) }
                        )
                    }
                }
            }

            // 2. Searchable Product Selector
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Add Inward Products (उत्पाद चुनें)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = productSearchQuery,
                    onValueChange = {
                        productSearchQuery = it
                        isProductDropdownExpanded = true
                    },
                    label = { Text("Search & Select Product to Inward") },
                    placeholder = { Text("Search Almonds, Kaju, Spices, SKU...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (productSearchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    productSearchQuery = ""
                                    isProductDropdownExpanded = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { isProductDropdownExpanded = !isProductDropdownExpanded }) {
                                Icon(
                                    imageVector = if (isProductDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Toggle Products"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Inline Product Suggestions (Keeps soft keyboard open and responsive)
                AnimatedVisibility(
                    visible = isProductDropdownExpanded || productSearchQuery.isNotBlank()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (filteredProducts.isEmpty()) {
                                Text(
                                    text = "No matching products found",
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                filteredProducts.take(6).forEach { prod ->
                                    val isLoose = prod.unit.equals("KG", true) || prod.unit.equals("Kilogram", true) || prod.unit.equals("Gram", true)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activeProductToAdd = prod
                                                itemQtyInput = if (isLoose) "10" else "5"
                                                itemCostInput = if (prod.purchasePrice > 0) prod.purchasePrice.toString() else ""
                                                productSearchQuery = ""
                                                isProductDropdownExpanded = false
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(
                                                    text = "${if (prod.hindiName.isNotEmpty()) "${prod.hindiName} • " else ""}Stock: ${CurrencyUtils.formatQuantity(prod.currentStock, prod.unit)} • Cost: ₹${prod.purchasePrice}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isLoose) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "+ Add",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Inward Item Adder Box (When a product is selected from the dropdown)
            if (activeProductToAdd != null) {
                val prod = activeProductToAdd!!
                val isLoose = prod.unit.equals("KG", true) || prod.unit.equals("Kilogram", true) || prod.unit.equals("Gram", true)

                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Add: ${prod.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Current Stock: ${CurrencyUtils.formatQuantity(prod.currentStock, prod.unit)} • Category: ${prod.category}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                IconButton(
                                    onClick = { activeProductToAdd = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel add item", modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = itemQtyInput,
                                    onValueChange = { itemQtyInput = it },
                                    label = { Text("Inward Qty (${prod.unit}) *") },
                                    placeholder = { Text(if (isLoose) "e.g. 25.5" else "e.g. 10") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = itemCostInput,
                                    onValueChange = { itemCostInput = it },
                                    label = { Text("Cost Price (₹ per ${prod.unit})") },
                                    placeholder = { Text("₹${prod.purchasePrice}") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            val calculatedQty = itemQtyInput.toDoubleOrNull() ?: 0.0
                            val calculatedCost = itemCostInput.toDoubleOrNull() ?: prod.purchasePrice
                            val itemSubtotal = calculatedQty * calculatedCost

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Inward Value: ${CurrencyUtils.formatInr(itemSubtotal)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(
                                    onClick = {
                                        if (calculatedQty > 0) {
                                            selectedItems[prod.id] = prod to calculatedQty
                                            if (calculatedCost > 0) {
                                                priceOverrides[prod.id] = calculatedCost
                                            }
                                            activeProductToAdd = null
                                        }
                                    },
                                    enabled = calculatedQty > 0,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add to Inward List")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Selected Items List
            if (selectedItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Inward Batch Items (${selectedItems.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(selectedItems.values.toList()) { (prod, qty) ->
                    val cost = priceOverrides[prod.id] ?: prod.purchasePrice
                    val itemTotal = qty * cost
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = prod.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${CurrencyUtils.formatQuantity(qty, prod.unit)} @ ₹$cost / ${prod.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = CurrencyUtils.formatInr(itemTotal),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GeometricPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        selectedItems.remove(prod.id)
                                        priceOverrides.remove(prod.id)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Notes
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Batch Details / Truck No.") },
                    placeholder = { Text("e.g. 5 bags 2026 crop, standard packing") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Total Procurement Bill Summary
        val totalInwardAmount = selectedItems.values.sumOf { (prod, qty) ->
            val cost = priceOverrides[prod.id] ?: prod.purchasePrice
            qty * cost
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Inward (${selectedItems.size} items):",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = CurrencyUtils.formatInr(totalInwardAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = GeometricPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save Button with Double-Click Prevention and Loading Indicator
        Button(
            onClick = {
                val supplierFinalName = supplierQuery.trim()
                if (supplierFinalName.isNotBlank() && selectedItems.isNotEmpty() && !isSavingPurchase) {
                    isSavingPurchase = true
                    val itemList = selectedItems.values.toList()
                    onSave(
                        supplierFinalName,
                        supplierPhone.trim(),
                        invoiceRef.trim(),
                        paymentMethod,
                        paymentStatus,
                        notes.trim(),
                        itemList,
                        priceOverrides.toMap()
                    ) {
                        isSavingPurchase = false
                    }
                }
            },
            enabled = supplierQuery.isNotBlank() && selectedItems.isNotEmpty() && !isSavingPurchase,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_purchase_submit_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GeometricPrimary)
        ) {
            if (isSavingPurchase) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recording Stock Inward...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Stock Inward (${selectedItems.size} items • ${CurrencyUtils.formatInr(totalInwardAmount)})", fontWeight = FontWeight.Bold)
            }
        }
    }
}

