package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.ProductEntity
import com.example.ui.components.CustomSearchBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.CartItem
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreen(
    viewModel: ShopViewModel,
    onNavigateBack: () -> Unit,
    onInvoiceCreated: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val shopProfile by viewModel.shopProfile.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val allProducts by viewModel.allActiveProducts.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val customerName by viewModel.customerNameInput.collectAsStateWithLifecycle()
    val customerMobile by viewModel.customerMobileInput.collectAsStateWithLifecycle()
    val customerAddress by viewModel.customerAddressInput.collectAsStateWithLifecycle()
    val overallDiscount by viewModel.overallDiscount.collectAsStateWithLifecycle()
    val isGstApplied by viewModel.isGstApplied.collectAsStateWithLifecycle()
    val paymentMethod by viewModel.paymentMethod.collectAsStateWithLifecycle()
    val paidAmountInput by viewModel.paidAmountInput.collectAsStateWithLifecycle()
    val saleNotes by viewModel.saleNotes.collectAsStateWithLifecycle()

    var showProductPickerSheet by remember { mutableStateOf(false) }
    var showCustomerPickerSheet by remember { mutableStateOf(false) }
    var showQuickCustomerDialog by remember { mutableStateOf(false) }
    var showNegativeStockWarning by remember { mutableStateOf(false) }
    var negativeStockProductName by remember { mutableStateOf("") }

    val subtotal = cartItems.sumOf { it.itemSubtotal }
    val taxRate = shopProfile?.taxRatePercent ?: 0.0
    val taxAmount = if (isGstApplied && taxRate > 0) (subtotal - overallDiscount).coerceAtLeast(0.0) * (taxRate / 100.0) else 0.0
    val grandTotal = (subtotal - overallDiscount + taxAmount).coerceAtLeast(0.0)
    val totalCost = cartItems.sumOf { it.itemCostTotal }
    val grossProfit = grandTotal - totalCost
    val profitMargin = if (grandTotal > 0) (grossProfit / grandTotal * 100.0) else 0.0

    val isCredit = paymentMethod.contains("Credit", ignoreCase = true) || paymentMethod.contains("Udhaar", ignoreCase = true)
    val parsedPaid = paidAmountInput.toDoubleOrNull() ?: if (isCredit) 0.0 else grandTotal
    val balance = if (isCredit) (grandTotal - parsedPaid).coerceAtLeast(0.0) else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "New Sale / POS Counter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "नया बिल बनाएं • Puja Dry Fruits",
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
                    if (cartItems.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearCart() },
                            modifier = Modifier.testTag("clear_cart_button")
                        ) {
                            Text("Clear", color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Grand Total (${cartItems.size} items)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyUtils.formatInr(grandTotal),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Est. Profit: +${CurrencyUtils.formatInr(grossProfit)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                            Text(
                                text = "Margin: ${CurrencyUtils.formatPercent(profitMargin)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (cartItems.isEmpty()) return@Button
                            // Check stock
                            val outOfStockItem = cartItems.firstOrNull { it.quantity > it.product.currentStock }
                            if (outOfStockItem != null && !(shopProfile?.negativeStockAllowed ?: false)) {
                                negativeStockProductName = "${outOfStockItem.product.name} (In stock: ${outOfStockItem.product.currentStock}, Selling: ${outOfStockItem.quantity})"
                                showNegativeStockWarning = true
                            } else {
                                viewModel.checkoutSale { saleId, _ ->
                                    onInvoiceCreated(saleId)
                                }
                            }
                        },
                        enabled = cartItems.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("checkout_save_bill_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save & Generate Invoice (बिल बनाएं)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Customer Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Customer Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                TextButton(onClick = { showCustomerPickerSheet = true }) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Select Customer")
                                }
                                TextButton(onClick = { showQuickCustomerDialog = true }) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { viewModel.setCustomerName(it) },
                                label = { Text("Customer Name / Walk-in") },
                                placeholder = { Text("Walk-in Customer") },
                                modifier = Modifier.weight(1.2f).testTag("customer_name_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = customerMobile,
                                onValueChange = { viewModel.setCustomerMobile(it) },
                                label = { Text("Mobile Number") },
                                placeholder = { Text("10-digit mobile") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f).testTag("customer_mobile_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        if (selectedCustomer != null && selectedCustomer!!.outstandingBalance > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PurpleUdhaarLight)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = PurpleUdhaar, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Existing Udhaar Balance: ${CurrencyUtils.formatInr(selectedCustomer!!.outstandingBalance)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PurpleUdhaar
                                )
                            }
                        }
                    }
                }
            }

            // 2. Add Dry Fruits Button / Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sale Items (${cartItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = { showProductPickerSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("add_item_to_cart_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Dry Fruit", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. Cart Items List
            if (cartItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProductPickerSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBasket,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Cart is empty",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap '+ Add Dry Fruit' to select almonds, cashews, raisins, etc.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(cartItems) { item ->
                    CartItemCard(
                        cartItem = item,
                        onQuantityChanged = { newQty ->
                            viewModel.updateCartItemQuantity(item.product.id, newQty)
                        },
                        onPriceOrDiscountChanged = { newPrice, discount ->
                            viewModel.updateCartItemPrice(item.product.id, newPrice, discount)
                        },
                        onRemove = {
                            viewModel.removeFromCart(item.product.id)
                        }
                    )
                }
            }

            // 4. Billing Adjustments & Payment Mode
            if (cartItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Payment & Discounts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Overall Discount & GST
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = if (overallDiscount > 0) overallDiscount.toString() else "",
                                    onValueChange = {
                                        viewModel.setOverallDiscount(it.toDoubleOrNull() ?: 0.0)
                                    },
                                    label = { Text("Special Discount (₹)") },
                                    placeholder = { Text("0.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                if (taxRate > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = isGstApplied,
                                            onCheckedChange = { viewModel.setGstApplied(it) }
                                        )
                                        Text("Apply GST (${taxRate}%)", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Payment Method",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Payment Methods Chips
                            val paymentMethods = listOf("Cash", "UPI", "Card", "Credit / Udhaar")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                paymentMethods.forEach { method ->
                                    val isSelected = method == paymentMethod
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setPaymentMethod(method) },
                                        label = { Text(method) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (method.contains("Credit")) PurpleUdhaar else MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // If Udhaar / Credit
                            AnimatedVisibility(visible = isCredit) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PurpleUdhaarLight.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "Udhaar / Credit Settings",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PurpleUdhaar
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = paidAmountInput,
                                            onValueChange = { viewModel.setPaidAmountInput(it) },
                                            label = { Text("Initial Paid (₹)") },
                                            placeholder = { Text("0.00") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(top = 8.dp)
                                        ) {
                                            Text(
                                                text = "Remaining Udhaar:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PurpleUdhaar
                                            )
                                            Text(
                                                text = CurrencyUtils.formatInr(balance),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = PurpleUdhaar
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = saleNotes,
                                onValueChange = { viewModel.setSaleNotes(it) },
                                label = { Text("Bill Notes (Optional)") },
                                placeholder = { Text("e.g. Wedding Gift Pack, Discount given by owner") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Product Selection Bottom Sheet ---
    if (showProductPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProductPickerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ProductPickerContent(
                products = allProducts,
                onProductSelected = { product, qty ->
                    viewModel.addToCart(product, qty)
                    showProductPickerSheet = false
                }
            )
        }
    }

    // --- Customer Selection Bottom Sheet ---
    if (showCustomerPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCustomerPickerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CustomerPickerContent(
                customers = allCustomers,
                onCustomerSelected = { cust ->
                    viewModel.selectCustomer(cust)
                    showCustomerPickerSheet = false
                }
            )
        }
    }

    // --- Quick Add Customer Dialog ---
    if (showQuickCustomerDialog) {
        var newName by remember { mutableStateOf("") }
        var newPhone by remember { mutableStateOf("") }
        var newAddress by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showQuickCustomerDialog = false },
            title = { Text("Add New Customer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Customer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAddress,
                        onValueChange = { newAddress = it },
                        label = { Text("Address (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.addCustomer(newName, newPhone, newAddress)
                            viewModel.setCustomerName(newName)
                            viewModel.setCustomerMobile(newPhone)
                            viewModel.setCustomerAddress(newAddress)
                            showQuickCustomerDialog = false
                        }
                    }
                ) {
                    Text("Save Customer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCustomerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Negative Stock Warning Dialog ---
    if (showNegativeStockWarning) {
        AlertDialog(
            onDismissRequest = { showNegativeStockWarning = false },
            title = { Text("Insufficient Stock Warning") },
            text = {
                Text("Item: $negativeStockProductName.\n\nYou do not have enough stock in inventory to complete this sale. Please adjust quantity or enable negative stock in Shop Settings.")
            },
            confirmButton = {
                Button(onClick = { showNegativeStockWarning = false }) {
                    Text("Understood")
                }
            }
        )
    }
}

@Composable
fun CartItemCard(
    cartItem: CartItem,
    onQuantityChanged: (Double) -> Unit,
    onPriceOrDiscountChanged: (Double, Double) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cartItem.product.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Rate: ${CurrencyUtils.formatInr(cartItem.sellingPrice, false)} / ${cartItem.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove",
                        tint = ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantity Stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val step = if (cartItem.unit.contains("Gram", ignoreCase = true)) 0.25 else 0.5
                            onQuantityChanged((cartItem.quantity - step).coerceAtLeast(0.1))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = CurrencyUtils.formatQuantity(cartItem.quantity, cartItem.unit),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    IconButton(
                        onClick = {
                            val step = if (cartItem.unit.contains("Gram", ignoreCase = true)) 0.25 else 0.5
                            onQuantityChanged(cartItem.quantity + step)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }
                }

                // Total & Profit Preview
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyUtils.formatInr(cartItem.itemSubtotal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "+${CurrencyUtils.formatInr(cartItem.itemProfit, false)} profit",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ProductPickerContent(
    products: List<ProductEntity>,
    onProductSelected: (ProductEntity, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Nuts", "Dry Fruits", "Seeds", "Gift Boxes")
    val filtered = products.filter { prod ->
        val matchesSearch = searchQuery.isBlank() ||
                prod.name.contains(searchQuery, ignoreCase = true) ||
                prod.hindiName.contains(searchQuery, ignoreCase = true) ||
                prod.sku.contains(searchQuery, ignoreCase = true)
        val matchesCat = selectedCategory == "All" || prod.category.equals(selectedCategory, ignoreCase = true)
        matchesSearch && matchesCat
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp)
    ) {
        Text(
            text = "Select Dry Fruit to Add",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        CustomSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholderText = "Search Almonds, Kaju, Pista, Anjeer..."
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = cat == selectedCategory,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProductSelected(product, 1.0) },
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
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (product.hindiName.isNotEmpty()) {
                                Text(
                                    text = product.hindiName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Stock: ${CurrencyUtils.formatQuantity(product.currentStock, product.unit)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (product.currentStock <= product.minStockLevel) WarningOrange else SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyUtils.formatInr(product.sellingPrice) + " / " + product.unit,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = { onProductSelected(product, 1.0) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("+ Add", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerPickerContent(
    customers: List<CustomerEntity>,
    onCustomerSelected: (CustomerEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = customers.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.mobile.contains(query)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(16.dp)
    ) {
        Text(
            text = "Select Customer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        CustomSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholderText = "Search by name or mobile..."
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { cust ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCustomerSelected(cust) },
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
                        Column {
                            Text(text = cust.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = cust.mobile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }

                        if (cust.outstandingBalance > 0) {
                            Text(
                                text = "Udhaar: " + CurrencyUtils.formatInr(cust.outstandingBalance),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PurpleUdhaar
                            )
                        }
                    }
                }
            }
        }
    }
}
