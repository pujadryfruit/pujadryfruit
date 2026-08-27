package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.*
import com.example.data.firebase.models.UserDoc
import com.example.data.local.AppDatabase
import com.example.data.local.dao.CategoryExpenseSummary
import com.example.data.local.dao.PaymentMethodSummary
import com.example.data.local.dao.TopProductSummary
import com.example.data.local.entities.*
import com.example.data.repository.*
import com.example.util.DateFilterRange
import com.example.util.DateRange
import com.example.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FinancialStats(
    val sales: Double = 0.0,
    val grossProfit: Double = 0.0,
    val orders: Int = 0,
    val expenses: Double = 0.0
)

data class InventoryCustomerStats(
    val totalProducts: Int = 0,
    val lowStock: Int = 0,
    val stockValue: Double = 0.0,
    val totalCustomers: Int = 0,
    val totalCredit: Double = 0.0
)

data class DashboardMetrics(
    val totalSales: Double = 0.0,
    val totalCost: Double = 0.0,
    val grossProfit: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val profitMarginPercent: Double = 0.0,
    val totalOrders: Int = 0,
    val totalCustomers: Int = 0,
    val totalProducts: Int = 0,
    val lowStockCount: Int = 0,
    val totalStockValue: Double = 0.0,
    val totalOutstandingCredit: Double = 0.0
)

data class CartItem(
    val product: ProductEntity,
    val quantity: Double = 1.0,
    val unit: String = product.unit,
    val sellingPrice: Double = product.sellingPrice,
    val costPrice: Double = product.purchasePrice,
    val discount: Double = 0.0 // item discount amount
) {
    val itemSubtotal: Double get() = (sellingPrice * quantity) - discount
    val itemCostTotal: Double get() = costPrice * quantity
    val itemProfit: Double get() = itemSubtotal - itemCostTotal
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val shopRepo = ShopRepository(db.shopDao())
    val productRepo = ProductRepository(db.productDao(), db.stockAuditDao())
    val salesRepo = SalesRepository(db, db.saleDao(), db.productDao(), db.customerDao(), db.stockAuditDao(), db.shopDao())
    val supplierRepo = SupplierRepository(db.supplierDao())
    val purchaseRepo = PurchaseRepository(db, db.purchaseDao(), db.productDao(), db.supplierDao(), db.stockAuditDao())
    val customerRepo = CustomerRepository(db.customerDao(), db.creditPaymentDao(), db.saleDao())
    val expenseRepo = ExpenseRepository(db.expenseDao())
    val stockAuditDao = db.stockAuditDao()

    // --- Firebase Services & Cloud State ---
    val authService = FirebaseAuthService()
    val syncRepo = FirebaseSyncRepository(db)
    val storageService = FirebaseStorageService()
    val functionsClient = FirebaseCloudFunctionsClient()

    val currentUserDoc: StateFlow<UserDoc?> = authService.currentUserDoc
    val authState = authService.authState
    val cloudSyncState: StateFlow<CloudSyncState> = syncRepo.syncState
    val lastSyncTime: StateFlow<Long> = syncRepo.lastSyncTime
    val syncMessage: StateFlow<String> = syncRepo.syncMessage

    init {
        FirebaseManager.initialize(application)
        viewModelScope.launch {
            authState.collect { state ->
                when (state) {
                    is FirebaseAuthService.AuthState.Authenticated -> {
                        val uid = state.user?.uid ?: state.userDoc.id
                        FirebaseManager.currentUserId = uid
                        syncRepo.startRealtimeListeners()
                        syncRepo.performFullCloudSync()
                    }
                    else -> {
                        syncRepo.stopRealtimeListeners()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncRepo.stopRealtimeListeners()
    }

    // 1. Shop Profile & Security
    val shopProfile: StateFlow<ShopProfile?> = shopRepo.shopProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // 2. Date Filter State for Dashboard & Reports
    private val _dashboardDateFilter = MutableStateFlow(DateFilterRange.TODAY)
    val dashboardDateFilter: StateFlow<DateFilterRange> = _dashboardDateFilter.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)

    val currentDashboardDateRange: StateFlow<DateRange> = combine(
        _dashboardDateFilter,
        _customDateRange
    ) { filter, custom ->
        DateUtils.getDateRange(filter, custom?.first, custom?.second)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DateUtils.getDateRange(DateFilterRange.TODAY)
    )

    // 3. Reactive Dashboard Metrics
    val dashboardMetrics: StateFlow<DashboardMetrics> = currentDashboardDateRange
        .flatMapLatest { dateRange ->
            val financialFlow = combine(
                salesRepo.getTotalSalesByDateRange(dateRange.startTimestamp, dateRange.endTimestamp),
                salesRepo.getTotalGrossProfitByDateRange(dateRange.startTimestamp, dateRange.endTimestamp),
                salesRepo.getTotalOrdersCountByDateRange(dateRange.startTimestamp, dateRange.endTimestamp),
                expenseRepo.getTotalExpensesByDateRange(dateRange.startTimestamp, dateRange.endTimestamp)
            ) { sales, grossProfit, orders, expenses ->
                FinancialStats(
                    sales = sales ?: 0.0,
                    grossProfit = grossProfit ?: 0.0,
                    orders = orders,
                    expenses = expenses ?: 0.0
                )
            }

            val inventoryCustFlow = combine(
                productRepo.totalProductsCount,
                productRepo.lowStockCount,
                productRepo.totalStockValue,
                customerRepo.totalCustomersCount,
                customerRepo.totalOutstandingCredit
            ) { totalProd, lowProd, stockVal, totalCust, credit ->
                InventoryCustomerStats(
                    totalProducts = totalProd,
                    lowStock = lowProd,
                    stockValue = stockVal ?: 0.0,
                    totalCustomers = totalCust,
                    totalCredit = credit ?: 0.0
                )
            }

            combine(financialFlow, inventoryCustFlow) { fin, inv ->
                val netProfit = fin.grossProfit - fin.expenses
                val margin = if (fin.sales > 0) (fin.grossProfit / fin.sales * 100.0) else 0.0
                val cost = fin.sales - fin.grossProfit

                DashboardMetrics(
                    totalSales = fin.sales,
                    totalCost = cost,
                    grossProfit = fin.grossProfit,
                    totalExpenses = fin.expenses,
                    netProfit = netProfit,
                    profitMarginPercent = margin,
                    totalOrders = fin.orders,
                    totalCustomers = inv.totalCustomers,
                    totalProducts = inv.totalProducts,
                    lowStockCount = inv.lowStock,
                    totalStockValue = inv.stockValue,
                    totalOutstandingCredit = inv.totalCredit
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    val topSellingProducts: StateFlow<List<TopProductSummary>> = currentDashboardDateRange
        .flatMapLatest { dateRange ->
            salesRepo.getTopSellingProducts(dateRange.startTimestamp, dateRange.endTimestamp, limit = 5)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentMethodBreakdown: StateFlow<List<PaymentMethodSummary>> = currentDashboardDateRange
        .flatMapLatest { dateRange ->
            salesRepo.getPaymentMethodBreakdown(dateRange.startTimestamp, dateRange.endTimestamp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSalesList: StateFlow<List<SaleEntity>> = currentDashboardDateRange
        .flatMapLatest { dateRange ->
            salesRepo.getSalesByDateRange(dateRange.startTimestamp, dateRange.endTimestamp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Products & Inventory
    val allActiveProducts: StateFlow<List<ProductEntity>> = productRepo.allActiveProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<ProductEntity>> = productRepo.lowStockProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Customers & Udhaar
    val allCustomers: StateFlow<List<CustomerEntity>> = customerRepo.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customersWithUdhaar: StateFlow<List<CustomerEntity>> = customerRepo.customersWithCredit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Expenses
    val allExpenses: StateFlow<List<ExpenseEntity>> = expenseRepo.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryExpenses: StateFlow<List<CategoryExpenseSummary>> = currentDashboardDateRange
        .flatMapLatest { dateRange ->
            expenseRepo.getExpensesByCategory(dateRange.startTimestamp, dateRange.endTimestamp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 7. Purchases & Suppliers
    val allPurchases: StateFlow<List<PurchaseEntity>> = purchaseRepo.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<SupplierEntity>> = supplierRepo.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 8. New Sale / POS Cart State
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    private val _customerNameInput = MutableStateFlow("")
    val customerNameInput: StateFlow<String> = _customerNameInput.asStateFlow()

    private val _customerMobileInput = MutableStateFlow("")
    val customerMobileInput: StateFlow<String> = _customerMobileInput.asStateFlow()

    private val _customerAddressInput = MutableStateFlow("")
    val customerAddressInput: StateFlow<String> = _customerAddressInput.asStateFlow()

    private val _overallDiscount = MutableStateFlow(0.0)
    val overallDiscount: StateFlow<Double> = _overallDiscount.asStateFlow()

    private val _isGstApplied = MutableStateFlow(false)
    val isGstApplied: StateFlow<Boolean> = _isGstApplied.asStateFlow()

    private val _paymentMethod = MutableStateFlow("Cash") // Cash, UPI, Card, Bank Transfer, Credit / Udhaar
    val paymentMethod: StateFlow<String> = _paymentMethod.asStateFlow()

    private val _paidAmountInput = MutableStateFlow("")
    val paidAmountInput: StateFlow<String> = _paidAmountInput.asStateFlow()

    private val _dueDateMillis = MutableStateFlow<Long?>(null)
    val dueDateMillis: StateFlow<Long?> = _dueDateMillis.asStateFlow()

    private val _saleNotes = MutableStateFlow("")
    val saleNotes: StateFlow<String> = _saleNotes.asStateFlow()

    // Active Invoice For Viewing
    private val _activeInvoiceSale = MutableStateFlow<SaleEntity?>(null)
    val activeInvoiceSale: StateFlow<SaleEntity?> = _activeInvoiceSale.asStateFlow()

    private val _activeInvoiceItems = MutableStateFlow<List<SaleItemEntity>>(emptyList())
    val activeInvoiceItems: StateFlow<List<SaleItemEntity>> = _activeInvoiceItems.asStateFlow()

    // Feedback Toast / Snackbar message
    private val _uiEventMessage = MutableSharedFlow<String>()
    val uiEventMessage = _uiEventMessage.asSharedFlow()

    init {
        // Check if shop has PIN enabled
        viewModelScope.launch {
            shopRepo.shopProfile.collect { profile ->
                if (profile != null && profile.isPinSecurityEnabled) {
                    _isUnlocked.value = false
                } else {
                    _isUnlocked.value = true
                }
            }
        }
    }

    fun unlockApp(pin: String): Boolean {
        val currentPin = shopProfile.value?.securityPin ?: "1234"
        return if (pin == currentPin) {
            _isUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun lockApp() {
        _isUnlocked.value = false
    }

    fun setDashboardDateFilter(filter: DateFilterRange, customRange: Pair<Long, Long>? = null) {
        _dashboardDateFilter.value = filter
        if (customRange != null) {
            _customDateRange.value = customRange
        }
    }

    // --- POS Cart Operations ---
    fun addToCart(product: ProductEntity, quantity: Double = 1.0) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            current.add(CartItem(product = product, quantity = quantity))
        }
        _cartItems.value = current
    }

    fun updateCartItemQuantity(productId: Long, newQuantity: Double) {
        if (newQuantity <= 0) {
            removeFromCart(productId)
            return
        }
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = newQuantity)
            _cartItems.value = current
        }
    }

    fun updateCartItemPrice(productId: Long, newPrice: Double, discount: Double = 0.0) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            current[index] = current[index].copy(sellingPrice = newPrice, discount = discount)
            _cartItems.value = current
        }
    }

    fun removeFromCart(productId: Long) {
        _cartItems.value = _cartItems.value.filter { it.product.id != productId }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _selectedCustomer.value = null
        _customerNameInput.value = ""
        _customerMobileInput.value = ""
        _customerAddressInput.value = ""
        _overallDiscount.value = 0.0
        _isGstApplied.value = false
        _paymentMethod.value = "Cash"
        _paidAmountInput.value = ""
        _dueDateMillis.value = null
        _saleNotes.value = ""
    }

    fun selectCustomer(customer: CustomerEntity?) {
        _selectedCustomer.value = customer
        if (customer != null) {
            _customerNameInput.value = customer.name
            _customerMobileInput.value = customer.mobile
            _customerAddressInput.value = customer.address
        }
    }

    fun setCustomerName(name: String) { _customerNameInput.value = name }
    fun setCustomerMobile(mobile: String) { _customerMobileInput.value = mobile }
    fun setCustomerAddress(address: String) { _customerAddressInput.value = address }
    fun setOverallDiscount(discount: Double) { _overallDiscount.value = discount }
    fun setGstApplied(applied: Boolean) { _isGstApplied.value = applied }
    fun setPaymentMethod(method: String) { _paymentMethod.value = method }
    fun setPaidAmountInput(amount: String) { _paidAmountInput.value = amount }
    fun setDueDate(date: Long?) { _dueDateMillis.value = date }
    fun setSaleNotes(notes: String) { _saleNotes.value = notes }

    /**
     * Completes and saves a Sale
     */
    fun checkoutSale(onSuccess: (Long, String) -> Unit) {
        val items = _cartItems.value
        if (items.isEmpty()) return

        val profile = shopProfile.value ?: ShopProfile()
        val nextSeq = profile.nextInvoiceNumber
        val prefix = if (profile.invoicePrefix.isNotBlank()) profile.invoicePrefix else "PDF"
        val invoiceNo = "$prefix-$nextSeq"

        val subtotal = items.sumOf { it.itemSubtotal }
        val discount = _overallDiscount.value
        val discountedSubtotal = (subtotal - discount).coerceAtLeast(0.0)
        val taxAmount = if (_isGstApplied.value && profile.taxRatePercent > 0) {
            discountedSubtotal * (profile.taxRatePercent / 100.0)
        } else 0.0
        val grandTotal = discountedSubtotal + taxAmount

        val totalCost = items.sumOf { it.itemCostTotal }
        val grossProfit = grandTotal - totalCost
        val margin = if (grandTotal > 0) (grossProfit / grandTotal * 100.0) else 0.0

        val method = _paymentMethod.value
        val isCredit = method.contains("Credit", ignoreCase = true) || method.contains("Udhaar", ignoreCase = true)
        val parsedPaid = _paidAmountInput.value.toDoubleOrNull() ?: if (isCredit) 0.0 else grandTotal
        val finalPaid = if (isCredit) parsedPaid.coerceAtMost(grandTotal) else grandTotal
        val balance = (grandTotal - finalPaid).coerceAtLeast(0.0)

        val paymentStatus = when {
            balance <= 0.0 -> "PAID"
            finalPaid > 0.0 -> "PARTIAL"
            else -> "UNPAID"
        }

        val custName = when {
            _selectedCustomer.value != null -> _selectedCustomer.value!!.name
            _customerNameInput.value.isNotBlank() -> _customerNameInput.value.trim()
            else -> "Walk-in Customer"
        }

        val saleEntity = SaleEntity(
            invoiceNumber = invoiceNo,
            customerId = _selectedCustomer.value?.id,
            customerName = custName,
            customerMobile = _customerMobileInput.value.trim(),
            saleDate = System.currentTimeMillis(),
            subtotal = subtotal,
            discount = discount,
            taxAmount = taxAmount,
            grandTotal = grandTotal,
            totalCost = totalCost,
            grossProfit = grossProfit,
            profitMarginPercent = margin,
            paymentMethod = method,
            paymentStatus = paymentStatus,
            paidAmount = finalPaid,
            balanceAmount = balance,
            dueDate = _dueDateMillis.value,
            notes = _saleNotes.value.trim()
        )

        val saleItems = items.map {
            SaleItemEntity(
                saleId = 0, // Assigned in repo
                productId = it.product.id,
                productName = it.product.name,
                unit = it.unit,
                quantity = it.quantity,
                sellingPrice = it.sellingPrice,
                costPrice = it.costPrice,
                discount = it.discount,
                totalAmount = it.itemSubtotal,
                itemProfit = it.itemProfit
            )
        }

        viewModelScope.launch {
            val saleId = salesRepo.createSale(saleEntity, saleItems, _customerAddressInput.value)
            loadActiveInvoice(saleId)
            clearCart()
            _uiEventMessage.emit("Invoice $invoiceNo generated successfully!")
            
            // Sync atomically to Firebase Firestore in background
            launch {
                syncRepo.syncSaleToCloud(saleEntity.copy(id = saleId), saleItems)
                // Sync updated products to ensure exact stock consistency
                for (item in saleItems) {
                    val updatedProd = productRepo.getProductById(item.productId)
                    if (updatedProd != null) {
                        syncRepo.syncProductToCloud(updatedProd)
                    }
                }
            }

            onSuccess(saleId, invoiceNo)
        }
    }

    fun loadActiveInvoice(saleId: Long) {
        viewModelScope.launch {
            val sale = salesRepo.getSaleById(saleId)
            if (sale != null) {
                _activeInvoiceSale.value = sale
                _activeInvoiceItems.value = salesRepo.getSaleItemsSync(saleId)
            }
        }
    }

    fun cancelSale(saleId: Long, reason: String) {
        viewModelScope.launch {
            val sale = salesRepo.getSaleById(saleId)
            val saleItems = salesRepo.getSaleItemsSync(saleId)
            salesRepo.cancelSale(saleId, reason)
            loadActiveInvoice(saleId)
            _uiEventMessage.emit("Sale cancelled and inventory restored!")
            launch {
                if (sale != null) {
                    syncRepo.syncSaleCancelToCloud(saleId, sale.invoiceNumber, reason)
                }
                for (item in saleItems) {
                    val updatedProd = productRepo.getProductById(item.productId)
                    if (updatedProd != null) {
                        syncRepo.syncProductToCloud(updatedProd)
                    }
                }
            }
        }
    }

    // --- Product & Inventory Operations ---
    fun addProduct(product: ProductEntity, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val trimmedName = product.name.trim()
            val trimmedSku = product.sku.trim()

            // Check if product already exists (by SKU or Name) to prevent duplicate creation
            val existing = if (trimmedSku.isNotBlank()) {
                productRepo.getProductBySku(trimmedSku)
            } else {
                productRepo.getProductByName(trimmedName)
            }

            if (existing != null && !existing.isArchived) {
                val updated = existing.copy(
                    name = trimmedName,
                    hindiName = product.hindiName.trim(),
                    category = product.category,
                    unit = product.unit,
                    purchasePrice = product.purchasePrice,
                    sellingPrice = product.sellingPrice,
                    currentStock = product.currentStock,
                    minStockLevel = product.minStockLevel,
                    supplierName = product.supplierName.trim(),
                    description = product.description.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                productRepo.updateProduct(updated)
                _uiEventMessage.emit("Updated product ${product.name}")
                launch {
                    syncRepo.syncProductToCloud(updated)
                }
                onComplete?.invoke(true)
                return@launch
            }

            val id = productRepo.addProduct(product.copy(name = trimmedName, sku = trimmedSku))
            val productWithId = product.copy(id = id, name = trimmedName, sku = trimmedSku)
            _uiEventMessage.emit("Added ${product.name} to inventory")
            launch {
                syncRepo.syncProductToCloud(productWithId)
            }
            onComplete?.invoke(true)
        }
    }

    fun updateProduct(product: ProductEntity, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            productRepo.updateProduct(product)
            _uiEventMessage.emit("Updated ${product.name}")
            launch {
                syncRepo.syncProductToCloud(product)
            }
            onComplete?.invoke(true)
        }
    }

    fun deleteProduct(productId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val product = productRepo.getProductById(productId)
            productRepo.deleteProduct(productId)
            _uiEventMessage.emit("Product deleted from inventory and cloud")
            if (product != null) {
                launch {
                    syncRepo.deleteProductFromCloud(product)
                }
            }
            onComplete?.invoke()
        }
    }

    fun adjustStock(productId: Long, diff: Double, reason: String) {
        viewModelScope.launch {
            productRepo.adjustStock(productId, diff, reason)
            val updatedProduct = productRepo.getProductById(productId)
            _uiEventMessage.emit("Stock adjusted successfully")
            if (updatedProduct != null) {
                launch {
                    syncRepo.syncStockAdjustmentToCloud(
                        productId = productId,
                        productName = updatedProduct.name,
                        diff = diff,
                        newStock = updatedProduct.currentStock,
                        reason = reason
                    )
                    syncRepo.syncProductToCloud(updatedProduct)
                }
            }
        }
    }

    // --- Supplier Operations ---
    fun addSupplier(supplier: SupplierEntity, onComplete: ((SupplierEntity) -> Unit)? = null) {
        viewModelScope.launch {
            val trimmedName = supplier.name.trim()
            val trimmedMobile = supplier.mobile.trim()
            val existing = if (trimmedName.isNotBlank()) supplierRepo.getSupplierByName(trimmedName) else null

            if (existing != null) {
                val updated = existing.copy(
                    mobile = if (trimmedMobile.isNotBlank()) trimmedMobile else existing.mobile,
                    address = if (supplier.address.isNotBlank()) supplier.address.trim() else existing.address,
                    gstin = if (supplier.gstin.isNotBlank()) supplier.gstin.trim() else existing.gstin,
                    contactPerson = if (supplier.contactPerson.isNotBlank()) supplier.contactPerson.trim() else existing.contactPerson,
                    updatedAt = System.currentTimeMillis()
                )
                supplierRepo.updateSupplier(updated)
                launch { syncRepo.syncSupplierToCloud(updated) }
                _uiEventMessage.emit("Supplier $trimmedName selected")
                onComplete?.invoke(updated)
                return@launch
            }

            val newSupp = supplier.copy(name = trimmedName, mobile = trimmedMobile)
            val id = supplierRepo.addSupplier(newSupp)
            val savedSupp = newSupp.copy(id = id)
            _uiEventMessage.emit("New supplier $trimmedName added")
            launch {
                syncRepo.syncSupplierToCloud(savedSupp)
            }
            onComplete?.invoke(savedSupp)
        }
    }

    // --- Purchase Operations ---
    fun recordPurchase(
        supplierName: String,
        supplierPhone: String,
        invoiceRef: String,
        paymentMethod: String,
        paymentStatus: String,
        notes: String,
        items: List<Pair<ProductEntity, Double>>, // Product and quantity
        priceOverrides: Map<Long, Double> = emptyMap(), // Product ID to purchase price per unit
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val purchaseItems = items.map { (prod, qty) ->
                val pPrice = priceOverrides[prod.id] ?: prod.purchasePrice
                PurchaseItemEntity(
                    purchaseId = 0,
                    productId = prod.id,
                    productName = prod.name,
                    unit = prod.unit,
                    quantity = qty,
                    purchasePrice = pPrice,
                    totalAmount = pPrice * qty
                )
            }
            val totalAmount = purchaseItems.sumOf { it.totalAmount }

            val purchase = PurchaseEntity(
                supplierName = supplierName.trim(),
                supplierPhone = supplierPhone.trim(),
                invoiceReference = invoiceRef.trim(),
                totalAmount = totalAmount,
                paymentStatus = paymentStatus,
                paymentMethod = paymentMethod,
                notes = notes.trim()
            )
            val purchaseId = purchaseRepo.createPurchase(purchase, purchaseItems)
            _uiEventMessage.emit("Stock inward of ${items.size} products recorded!")

            // Sync purchase to Firebase Firestore atomically in background
            launch {
                syncRepo.syncPurchaseToCloud(purchase.copy(id = purchaseId), purchaseItems)
                for (item in purchaseItems) {
                    val updatedProd = productRepo.getProductById(item.productId)
                    if (updatedProd != null) {
                        syncRepo.syncProductToCloud(updatedProd)
                    }
                }
            }
            onComplete?.invoke(true)
        }
    }

    fun updatePurchase(
        purchaseId: Long,
        supplierName: String,
        supplierPhone: String,
        invoiceRef: String,
        paymentMethod: String,
        paymentStatus: String,
        notes: String,
        items: List<Pair<ProductEntity, Double>>,
        priceOverrides: Map<Long, Double> = emptyMap(),
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val oldItems = purchaseRepo.getPurchaseItemsSync(purchaseId)
            val purchaseItems = items.map { (prod, qty) ->
                val pPrice = priceOverrides[prod.id] ?: prod.purchasePrice
                PurchaseItemEntity(
                    purchaseId = purchaseId,
                    productId = prod.id,
                    productName = prod.name,
                    unit = prod.unit,
                    quantity = qty,
                    purchasePrice = pPrice,
                    totalAmount = pPrice * qty
                )
            }
            val totalAmount = purchaseItems.sumOf { it.totalAmount }
            val updatedPurchase = PurchaseEntity(
                id = purchaseId,
                supplierName = supplierName.trim(),
                supplierPhone = supplierPhone.trim(),
                invoiceReference = invoiceRef.trim(),
                totalAmount = totalAmount,
                paymentStatus = paymentStatus,
                paymentMethod = paymentMethod,
                notes = notes.trim(),
                purchaseDate = System.currentTimeMillis()
            )
            purchaseRepo.updatePurchase(purchaseId, updatedPurchase, purchaseItems)
            _uiEventMessage.emit("Stock inward #${purchaseId} updated successfully!")
            launch {
                syncRepo.syncPurchaseUpdateToCloud(updatedPurchase, purchaseItems, oldItems)
                for (item in purchaseItems) {
                    val updatedProd = productRepo.getProductById(item.productId)
                    if (updatedProd != null) {
                        syncRepo.syncProductToCloud(updatedProd)
                    }
                }
            }
            onComplete?.invoke(true)
        }
    }

    suspend fun getPurchaseItemsForEdit(purchaseId: Long): List<PurchaseItemEntity> {
        return purchaseRepo.getPurchaseItemsSync(purchaseId)
    }

    fun deletePurchase(purchaseId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val items = purchaseRepo.getPurchaseItemsSync(purchaseId)
            purchaseRepo.deletePurchase(purchaseId)
            _uiEventMessage.emit("Purchase #$purchaseId deleted and stock restored")
            launch {
                syncRepo.syncPurchaseDeleteToCloud(purchaseId)
                for (item in items) {
                    val updatedProd = productRepo.getProductById(item.productId)
                    if (updatedProd != null) {
                        syncRepo.syncProductToCloud(updatedProd)
                    }
                }
            }
            onComplete?.invoke()
        }
    }

    // --- Customer & Udhaar Operations ---
    fun addCustomer(name: String, mobile: String, address: String, onComplete: ((CustomerEntity) -> Unit)? = null) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val trimmedMobile = mobile.trim()
            val trimmedAddress = address.trim()

            // Check if customer already exists by mobile or name to prevent duplicate creation
            val existing = if (trimmedMobile.isNotBlank()) {
                customerRepo.getCustomerByMobile(trimmedMobile)
            } else {
                customerRepo.getCustomerByName(trimmedName)
            }

            if (existing != null) {
                val updated = existing.copy(
                    name = trimmedName,
                    mobile = trimmedMobile,
                    address = if (trimmedAddress.isNotBlank()) trimmedAddress else existing.address,
                    updatedAt = System.currentTimeMillis()
                )
                customerRepo.updateCustomer(updated)
                _uiEventMessage.emit("Customer $trimmedName updated")
                launch {
                    syncRepo.syncCustomerToCloud(updated)
                }
                onComplete?.invoke(updated)
                return@launch
            }

            val customer = CustomerEntity(
                name = trimmedName,
                mobile = trimmedMobile,
                address = trimmedAddress
            )
            val id = customerRepo.addCustomer(customer)
            val custWithId = customer.copy(id = id)
            _uiEventMessage.emit("Customer $trimmedName registered")
            launch {
                syncRepo.syncCustomerToCloud(custWithId)
            }
            onComplete?.invoke(custWithId)
        }
    }

    fun updateCustomer(customer: CustomerEntity, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val updated = customer.copy(
                name = customer.name.trim(),
                mobile = customer.mobile.trim(),
                address = customer.address.trim(),
                updatedAt = System.currentTimeMillis()
            )
            customerRepo.updateCustomer(updated)
            _uiEventMessage.emit("Customer ${updated.name} updated")
            launch {
                syncRepo.syncCustomerToCloud(updated)
            }
            onComplete?.invoke(true)
        }
    }

    fun deleteCustomer(customerId: Long) {
        viewModelScope.launch {
            customerRepo.deleteCustomer(customerId)
            _uiEventMessage.emit("Customer removed")
            launch {
                syncRepo.deleteCustomerFromCloud(customerId)
            }
        }
    }

    fun recordUdhaarRepayment(
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String
    ) {
        viewModelScope.launch {
            val (paymentId, updatedSales) = customerRepo.recordCreditRepayment(customerId, amount, paymentMethod, notes)
            _uiEventMessage.emit("Received payment of ₹$amount from customer")
            launch {
                syncRepo.syncPaymentToCloud(
                    customerId = customerId,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    paymentId = paymentId,
                    updatedSales = updatedSales
                )
            }
        }
    }

    // --- Expense Operations ---
    fun addExpense(
        category: String,
        description: String,
        amount: Double,
        paymentMethod: String,
        notes: String,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                expenseDate = date,
                category = category,
                description = description.trim(),
                amount = amount,
                paymentMethod = paymentMethod,
                notes = notes.trim()
            )
            val id = expenseRepo.addExpense(expense)
            val expWithId = if (expense.id == 0L) expense.copy(id = id) else expense
            _uiEventMessage.emit("Expense of ₹$amount recorded")
            launch {
                syncRepo.syncExpenseToCloud(expWithId)
            }
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            expenseRepo.deleteExpense(id)
            _uiEventMessage.emit("Expense deleted")
            launch {
                syncRepo.deleteExpenseFromCloud(id)
            }
        }
    }

    // --- Settings / Shop Profile ---
    fun updateShopProfile(profile: ShopProfile) {
        viewModelScope.launch {
            shopRepo.saveShopProfile(profile)
            _uiEventMessage.emit("Shop Profile updated successfully")
            // Also sync profile update to Firestore
            launch {
                syncRepo.performFullCloudSync()
            }
        }
    }

    // --- Firebase Cloud Sync & Authentication Operations ---
    fun signInWithGoogle(context: android.content.Context) {
        viewModelScope.launch {
            _uiEventMessage.emit("Signing in with Google...")
            val result = authService.signInWithGoogle(context)
            if (result.isSuccess) {
                val user = result.getOrNull()
                _uiEventMessage.emit("Welcome, ${user?.displayName ?: "User"}!")
                syncRepo.performFullCloudSync()
            } else {
                _uiEventMessage.emit("Google Sign-In: ${result.exceptionOrNull()?.localizedMessage ?: "Failed"}")
            }
        }
    }

    fun clearAuthError() {
        authService.clearError()
    }

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _uiEventMessage.emit("Signing in...")
            val result = authService.signInWithEmailPassword(email, pass)
            if (result.isSuccess) {
                val user = result.getOrNull()
                _uiEventMessage.emit("Welcome, ${user?.displayName ?: user?.email}!")
                syncRepo.performFullCloudSync()
            } else {
                _uiEventMessage.emit("Login failed: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun signInWithPin(enteredPin: String) {
        viewModelScope.launch {
            val profile = shopProfile.value
            if (profile == null || !profile.isPinSecurityEnabled || profile.securityPin.isBlank()) {
                _uiEventMessage.emit("PIN access is not configured. Please log in with Email & Password first and enable PIN in Shop Settings.")
                return@launch
            }
            _uiEventMessage.emit("Verifying Security PIN...")
            val currentPin = profile.securityPin
            val result = authService.signInWithPin(enteredPin, currentPin)
            if (result.isSuccess) {
                _isUnlocked.value = true
                _uiEventMessage.emit("Security PIN verified! Welcome.")
            } else {
                _uiEventMessage.emit(result.exceptionOrNull()?.message ?: "Incorrect PIN")
            }
        }
    }

    fun loginWithDefaultOwner(pass: String = "Rohit@") {
        viewModelScope.launch {
            _uiEventMessage.emit("Signing in as Owner...")
            val result = authService.signInWithEmailPassword("rohit21oc@gmail.com", pass)
            if (result.isSuccess) {
                _isUnlocked.value = true
                _uiEventMessage.emit("Welcome Rohit Kumar (Owner)!")
                syncRepo.performFullCloudSync()
            } else {
                _uiEventMessage.emit("Owner login failed: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun adminRegisterStaffUser(
        email: String,
        pass: String,
        name: String,
        role: String = "STAFF / CASHIER",
        phone: String = "",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _uiEventMessage.emit("Registering staff user: $name...")
            val result = authService.adminRegisterNewUser(email, pass, name, role, phone)
            if (result.isSuccess) {
                val msg = "Staff user $name registered and authorized successfully!"
                _uiEventMessage.emit(msg)
                onResult(true, msg)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to register user"
                _uiEventMessage.emit("Registration failed: $err")
                onResult(false, err)
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String, name: String) {
        viewModelScope.launch {
            _uiEventMessage.emit("Creating account...")
            val result = authService.signUpWithEmailPassword(email, pass, name)
            if (result.isSuccess) {
                _uiEventMessage.emit("Account created for $name!")
                syncRepo.performFullCloudSync()
            } else {
                _uiEventMessage.emit("Sign-up failed: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = authService.sendPasswordResetEmail(email)
            if (result.isSuccess) {
                val msg = result.getOrNull() ?: "Password reset email sent!"
                _uiEventMessage.emit(msg)
                onResult(true, msg)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email"
                _uiEventMessage.emit(err)
                onResult(false, err)
            }
        }
    }

    fun changePassword(newPass: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = authService.changePassword(newPass)
            if (result.isSuccess) {
                val msg = result.getOrNull() ?: "Password updated successfully!"
                _uiEventMessage.emit(msg)
                onResult(true, msg)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to update password"
                _uiEventMessage.emit(err)
                onResult(false, err)
            }
        }
    }

    fun updateUserProfile(displayName: String, phone: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = authService.updateUserProfile(displayName, phone)
            if (result.isSuccess) {
                _uiEventMessage.emit("Profile updated successfully!")
                onResult(true, "Profile updated successfully!")
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to update profile"
                _uiEventMessage.emit(err)
                onResult(false, err)
            }
        }
    }

    fun signOut(context: android.content.Context) {
        viewModelScope.launch {
            authService.signOut(context)
            _uiEventMessage.emit("Signed out successfully")
        }
    }

    fun syncWithCloud() {
        triggerFullCloudSync()
    }

    fun triggerFullCloudSync() {
        viewModelScope.launch {
            _uiEventMessage.emit("Syncing store data...")
            val result = syncRepo.performFullCloudSync()
            if (result.isSuccess) {
                _uiEventMessage.emit("Data backup & sync complete!")
            } else {
                _uiEventMessage.emit("Saved locally. Will sync when online.")
            }
        }
    }

    fun uploadProductImage(productId: Long, bitmap: Bitmap, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _uiEventMessage.emit("Uploading image...")
            val result = storageService.uploadProductImage(
                shopId = FirebaseManager.currentShopId,
                productId = "${FirebaseManager.currentShopId}_prod_$productId",
                bitmap = bitmap
            )
            if (result.isSuccess) {
                val url = result.getOrNull() ?: ""
                _uiEventMessage.emit("Image saved successfully")
                onComplete(url)
            } else {
                _uiEventMessage.emit("Image upload failed: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }
}
