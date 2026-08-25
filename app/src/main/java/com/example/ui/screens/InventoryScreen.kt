package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ProductEntity
import com.example.ui.components.CustomSearchBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: ShopViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val products by viewModel.allActiveProducts.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var filterLowStockOnly by remember { mutableStateOf(false) }

    var showAddEditSheet by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    var showAdjustStockDialog by remember { mutableStateOf(false) }
    var adjustingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }

    val categories = listOf("All", "Nuts", "Dry Fruits", "Seeds", "Gift Boxes")

    val filteredProducts = products.filter { prod ->
        val matchesSearch = searchQuery.isBlank() ||
                prod.name.contains(searchQuery, ignoreCase = true) ||
                prod.hindiName.contains(searchQuery, ignoreCase = true) ||
                prod.sku.contains(searchQuery, ignoreCase = true) ||
                prod.supplierName.contains(searchQuery, ignoreCase = true)
        val matchesCat = selectedCategory == "All" || prod.category.equals(selectedCategory, ignoreCase = true)
        val matchesLowStock = !filterLowStockOnly || (prod.currentStock <= prod.minStockLevel)
        matchesSearch && matchesCat && !prod.isArchived
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Dry Fruit Inventory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${products.size} Products • Total Stock: ${CurrencyUtils.formatInr(metrics.totalStockValue)}",
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
                        onClick = { viewModel.syncWithCloud() },
                        modifier = Modifier.testTag("sync_inventory_btn")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Cloud", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = {
                            editingProduct = null
                            showAddEditSheet = true
                        },
                        modifier = Modifier.testTag("add_product_action_btn")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Product", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProduct = null
                    showAddEditSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Dry Fruit")
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
            // Search and Filters
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CustomSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search Badam, Kaju, Pista, Dates, SKU..."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category & Low Stock Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { filterLowStockOnly = !filterLowStockOnly },
                            shape = RoundedCornerShape(100.dp),
                            color = if (filterLowStockOnly) ErrorRed else MaterialTheme.colorScheme.surface,
                            contentColor = if (filterLowStockOnly) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = if (filterLowStockOnly) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Low Stock Alert (${metrics.lowStockCount})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        categories.forEach { cat ->
                            val isSelected = cat == selectedCategory && !filterLowStockOnly
                            Surface(
                                onClick = {
                                    selectedCategory = cat
                                    filterLowStockOnly = false
                                },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) GeometricPrimary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Products List
            if (filteredProducts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Inventory2,
                        title = "No Products Found",
                        description = "Try changing your search query or category filter, or add a new dry fruit product.",
                        actionButtonText = "+ Add New Dry Fruit",
                        onActionClick = {
                            editingProduct = null
                            showAddEditSheet = true
                        }
                    )
                }
            } else {
                items(filteredProducts) { product ->
                    ProductItemCard(
                        product = product,
                        onEdit = {
                            editingProduct = product
                            showAddEditSheet = true
                        },
                        onAdjustStock = {
                            adjustingProduct = product
                            showAdjustStockDialog = true
                        },
                        onDelete = {
                            productToDelete = product
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }
        }
    }

    // Add / Edit Product Bottom Sheet
    if (showAddEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddEditSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddEditProductSheetContent(
                product = editingProduct,
                onSave = { savedProduct, onFinished ->
                    if (editingProduct != null) {
                        viewModel.updateProduct(savedProduct) {
                            onFinished()
                            showAddEditSheet = false
                        }
                    } else {
                        viewModel.addProduct(savedProduct) {
                            onFinished()
                            showAddEditSheet = false
                        }
                    }
                },
                onCancel = { showAddEditSheet = false }
            )
        }
    }

    // Stock Adjustment Dialog
    if (showAdjustStockDialog && adjustingProduct != null) {
        val prod = adjustingProduct!!
        var adjustAmount by remember { mutableStateOf("") }
        var isAddition by remember { mutableStateOf(true) }
        var reason by remember { mutableStateOf("Stock inward / count verification") }

        AlertDialog(
            onDismissRequest = { showAdjustStockDialog = false },
            title = { Text("Adjust Stock: ${prod.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Current Stock: ${CurrencyUtils.formatQuantity(prod.currentStock, prod.unit)}")
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isAddition,
                            onClick = { isAddition = true },
                            label = { Text("+ Add Stock") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isAddition,
                            onClick = { isAddition = false },
                            label = { Text("- Deduct Stock") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = adjustAmount,
                        onValueChange = { adjustAmount = it },
                        label = { Text("Quantity (${prod.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason for Adjustment") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = adjustAmount.toDoubleOrNull() ?: 0.0
                        if (qty > 0) {
                            val diff = if (isAddition) qty else -qty
                            viewModel.adjustStock(prod.id, diff, reason)
                            showAdjustStockDialog = false
                        }
                    }
                ) {
                    Text("Save Adjustment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustStockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && productToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Archive Product?") },
            text = {
                Text("Are you sure you want to remove '${productToDelete!!.name}' from active catalog? Past invoices referencing this product will be preserved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(productToDelete!!.id)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Archive / Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProductItemCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = product.currentStock <= product.minStockLevel
    val isOutOfStock = product.currentStock <= 0.0
    val margin = if (product.sellingPrice > 0) ((product.sellingPrice - product.purchasePrice) / product.sellingPrice * 100.0) else 0.0
    val stockValue = product.currentStock * product.purchasePrice

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("product_card_${product.sku}"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (product.hindiName.isNotEmpty()) {
                        Text(
                            text = product.hindiName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (product.sku.isNotEmpty()) {
                        Text(
                            text = "SKU: ${product.sku} • Category: ${product.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // Stock Badge
                val (badgeBg, badgeText, badgeLabel) = when {
                    isOutOfStock -> Triple(ErrorRedLight, ErrorRed, "Out of Stock")
                    isLowStock -> Triple(WarningOrangeLight, WarningOrange, "Low Stock")
                    else -> Triple(SuccessGreenLight, SuccessGreen, "In Stock")
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pricing & Stock Grid (Cost Price hidden from general catalog list)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Selling Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = CurrencyUtils.formatInr(product.sellingPrice) + " / " + product.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GeometricPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = CurrencyUtils.formatQuantity(product.currentStock, product.unit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isLowStock) WarningOrange else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stock: ${CurrencyUtils.formatQuantity(product.currentStock, product.unit)} • ${product.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )

                Row {
                    IconButton(onClick = onAdjustStock, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = "Adjust Stock", tint = GeometricPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditProductSheetContent(
    product: ProductEntity?,
    onSave: (ProductEntity, onFinished: () -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    // Determine initial product type: LOOSE (KG/Gram) or PACKET (PCS/Packet/Box)
    val initialIsLoose = product?.let {
        it.unit.equals("Kilogram", ignoreCase = true) ||
        it.unit.equals("KG", ignoreCase = true) ||
        it.unit.equals("Gram", ignoreCase = true) ||
        it.unit.equals("Gm", ignoreCase = true)
    } ?: true

    var productType by remember { mutableStateOf(if (initialIsLoose) "LOOSE" else "PACKET") } // "LOOSE" or "PACKET"
    var name by remember { mutableStateOf(product?.name ?: "") }
    var hindiName by remember { mutableStateOf(product?.hindiName ?: "") }
    var sku by remember { mutableStateOf(product?.sku ?: "") }
    var category by remember { mutableStateOf(product?.category ?: "Dry Fruits") }

    val looseUnits = listOf("KG", "Gram")
    val packetUnits = listOf("PCS", "Packet", "Box")

    var selectedUnit by remember {
        mutableStateOf(
            product?.unit?.let { u ->
                when {
                    u.equals("Kilogram", true) -> "KG"
                    u.equals("KG", true) -> "KG"
                    u.equals("Gram", true) || u.equals("Gm", true) -> "Gram"
                    u.equals("Piece", true) || u.equals("PCS", true) -> "PCS"
                    u.equals("Packet", true) -> "Packet"
                    u.equals("Box", true) -> "Box"
                    else -> u
                }
            } ?: (if (initialIsLoose) "KG" else "PCS")
        )
    }

    var purchasePrice by remember { mutableStateOf(if (product != null && product.purchasePrice > 0) product.purchasePrice.toString() else "") }
    var sellingPrice by remember { mutableStateOf(if (product != null && product.sellingPrice > 0) product.sellingPrice.toString() else "") }
    var currentStock by remember { mutableStateOf(if (product != null) product.currentStock.toString() else "10") }
    var minStock by remember { mutableStateOf(if (product != null) product.minStockLevel.toString() else "5") }
    var supplier by remember { mutableStateOf(product?.supplierName ?: "") }
    var description by remember { mutableStateOf(product?.description ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    val categories = listOf("Dry Fruits", "Nuts", "Spices / Masala", "Seeds", "Berries", "Gift Boxes", "Combos")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (product == null) "Add New Product" else "Edit Product",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Loose (KG/Gram) or Packaged (PCS/Packet)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onCancel, enabled = !isSaving) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Product Type Selector (Loose vs Packaged)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "PRODUCT TYPE (प्रकार)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    productType = "LOOSE"
                                    if (selectedUnit !in looseUnits) selectedUnit = "KG"
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (productType == "LOOSE") GeometricPrimary else MaterialTheme.colorScheme.surface,
                                contentColor = if (productType == "LOOSE") Color.White else MaterialTheme.colorScheme.onSurface,
                                border = if (productType == "LOOSE") null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Scale, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LOOSE (खुला माल)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    productType = "PACKET"
                                    if (selectedUnit !in packetUnits) selectedUnit = "PCS"
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (productType == "PACKET") GeometricPrimary else MaterialTheme.colorScheme.surface,
                                contentColor = if (productType == "PACKET") Color.White else MaterialTheme.colorScheme.onSurface,
                                border = if (productType == "PACKET") null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PACKET (पैकेट/पीस)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Product Name & Hindi Name
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    placeholder = {
                        Text(if (productType == "LOOSE") "e.g. California Almonds (Badam) / Jeera" else "e.g. Haldiram Bhujia 500g Pack")
                    },
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input"),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = hindiName,
                    onValueChange = { hindiName = it },
                    label = { Text("Hindi Name (Optional)") },
                    placeholder = { Text("e.g. बादाम / काजू / पिस्ता / जीरा / मसाला") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // 3. Category & Unit Selection
            item {
                Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = cat == category,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = if (productType == "LOOSE") "Unit (LOOSE → KG / GRAM)" else "Unit (PACKET → PCS / PACKET / BOX)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val availableUnits = if (productType == "LOOSE") looseUnits else packetUnits
                    availableUnits.forEach { u ->
                        FilterChip(
                            selected = u == selectedUnit,
                            onClick = { selectedUnit = u },
                            label = { Text(u, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. SKU & Supplier
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU / Code") },
                        placeholder = { Text(if (productType == "LOOSE") "DF-ALM-KG" else "PK-ALM-500G") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text("Supplier Name") },
                        placeholder = { Text("e.g. Sharma Traders") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true
                    )
                }
            }

            // 5. Cost & Selling Price
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text("Cost Price (₹ per $selectedUnit)") },
                        placeholder = { Text("750.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("product_cost_price_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sellingPrice,
                        onValueChange = { sellingPrice = it },
                        label = { Text("Selling Price (₹ per $selectedUnit)") },
                        placeholder = { Text("950.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("product_selling_price_input"),
                        singleLine = true
                    )
                }
            }

            // 6. Stock Quantity & Min Stock Alert
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentStock,
                        onValueChange = { currentStock = it },
                        label = {
                            Text(
                                if (productType == "LOOSE") "Initial Stock (in $selectedUnit, e.g. 10.5)"
                                else "Initial Stock (Qty in $selectedUnit)"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minStock,
                        onValueChange = { minStock = it },
                        label = { Text("Min Stock Alert ($selectedUnit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            // 7. Description
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description & Grade Notes") },
                    placeholder = { Text("e.g. Jumbo sweet grade, vacuum packed, 100% pure") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Save Button with Double-Click Prevention and Progress Indicator
        Button(
            onClick = {
                if (name.isNotBlank() && !isSaving) {
                    isSaving = true
                    val pPrice = purchasePrice.toDoubleOrNull() ?: 0.0
                    val sPrice = sellingPrice.toDoubleOrNull() ?: 0.0
                    val cStock = currentStock.toDoubleOrNull() ?: 0.0
                    val mStock = minStock.toDoubleOrNull() ?: 5.0

                    val newProduct = (product ?: ProductEntity(name = name.trim())).copy(
                        name = name.trim(),
                        hindiName = hindiName.trim(),
                        sku = sku.trim(),
                        category = category,
                        unit = selectedUnit,
                        purchasePrice = pPrice,
                        sellingPrice = sPrice,
                        currentStock = cStock,
                        minStockLevel = mStock,
                        supplierName = supplier.trim(),
                        description = description.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(newProduct) {
                        isSaving = false
                    }
                }
            },
            enabled = name.isNotBlank() && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_product_submit_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GeometricPrimary)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving to Catalog...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (product == null) "Save Product to Catalog" else "Update Product Details",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
