package com.example.data.firebase

import android.util.Log
import com.example.data.firebase.models.*
import com.example.data.local.AppDatabase
import com.example.data.local.entities.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class CloudSyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    OFFLINE,
    ERROR
}

class FirebaseSyncRepository(
    private val database: AppDatabase,
    private val firestoreProvider: () -> FirebaseFirestore? = { FirebaseManager.firestore },
    private val transactionManager: FirestoreTransactionManager = FirestoreTransactionManager(firestoreProvider)
) {
    private val TAG = "FirebaseSyncRepo"
    private val syncScope = CoroutineScope(Dispatchers.IO)

    private val _syncState = MutableStateFlow(CloudSyncState.IDLE)
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _syncMessage = MutableStateFlow("Local Storage Active")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private var productListener: ListenerRegistration? = null
    private var customerListener: ListenerRegistration? = null
    private var expenseListener: ListenerRegistration? = null

    /**
     * Initializes real-time listener for products, customers & expenses from Firestore to local Room.
     * Uses efficient snapshot listeners restricted by shopId.
     */
    fun startRealtimeListeners(shopId: String = FirebaseManager.currentShopId) {
        stopRealtimeListeners()
        try {
            val firestore = firestoreProvider() ?: run {
                Log.d(TAG, "Firestore not available for real-time listeners; running offline mode.")
                return
            }
            productListener = firestore.collection("products")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Product listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        syncScope.launch {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document.toObject(ProductDoc::class.java)
                                val docId = change.document.id
                                val parsedId = docId.substringAfterLast("_prod_", "").toLongOrNull()
                                when (change.type) {
                                    DocumentChange.Type.REMOVED -> {
                                        if (parsedId != null && parsedId > 0) {
                                            database.productDao().deleteProductById(parsedId)
                                        }
                                    }
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        if (doc.isArchived) {
                                            if (parsedId != null && parsedId > 0) {
                                                database.productDao().archiveProduct(parsedId)
                                            }
                                        } else {
                                            val entity = ProductEntity(
                                                id = parsedId ?: 0L,
                                                sku = doc.sku,
                                                name = doc.name,
                                                hindiName = doc.hindiName,
                                                category = doc.category,
                                                unit = doc.unit,
                                                purchasePrice = doc.purchasePrice,
                                                sellingPrice = doc.sellingPrice,
                                                currentStock = doc.currentStock,
                                                minStockLevel = doc.minStockLevel,
                                                supplierName = doc.supplierName,
                                                description = doc.description,
                                                colorHex = doc.colorHex,
                                                isArchived = doc.isArchived,
                                                createdAt = doc.createdAt,
                                                updatedAt = doc.updatedAt
                                            )
                                            database.productDao().insertProduct(entity)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            customerListener = firestore.collection("customers")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Customer listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        syncScope.launch {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document.toObject(CustomerDoc::class.java)
                                val docId = change.document.id
                                val parsedId = docId.substringAfterLast("_cust_", "").toLongOrNull()
                                when (change.type) {
                                    DocumentChange.Type.REMOVED -> {
                                        if (parsedId != null && parsedId > 0) {
                                            database.customerDao().deleteCustomerById(parsedId)
                                        } else if (doc.mobile.isNotBlank()) {
                                            database.customerDao().deleteCustomerByMobile(doc.mobile)
                                        }
                                    }
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val entity = CustomerEntity(
                                            id = parsedId ?: 0L,
                                            name = doc.name,
                                            mobile = doc.mobile,
                                            address = doc.address,
                                            totalOrders = doc.totalOrders,
                                            totalSpent = doc.totalSpent,
                                            totalProfitGenerated = doc.totalProfitGenerated,
                                            outstandingBalance = doc.outstandingBalance,
                                            lastPurchaseDate = doc.lastPurchaseDate,
                                            notes = doc.notes,
                                            createdAt = doc.createdAt,
                                            updatedAt = doc.updatedAt
                                        )
                                        database.customerDao().insertCustomer(entity)
                                    }
                                }
                            }
                        }
                    }
                }

            expenseListener = firestore.collection("expenses")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Expense listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        syncScope.launch {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document.toObject(ExpenseDoc::class.java)
                                val docId = change.document.id
                                val parsedId = docId.substringAfterLast("_exp_", "").toLongOrNull()
                                when (change.type) {
                                    DocumentChange.Type.REMOVED -> {
                                        if (parsedId != null && parsedId > 0) {
                                            database.expenseDao().deleteExpenseById(parsedId)
                                        }
                                    }
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val entity = ExpenseEntity(
                                            id = parsedId ?: 0L,
                                            expenseDate = doc.expenseDate,
                                            category = doc.category,
                                            description = doc.description,
                                            amount = doc.amount,
                                            paymentMethod = doc.paymentMethod,
                                            notes = doc.notes,
                                            receiptRef = doc.receiptRef,
                                            createdAt = doc.createdAt
                                        )
                                        database.expenseDao().insertExpense(entity)
                                    }
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting real-time listeners: ${e.message}", e)
        }
    }

    fun stopRealtimeListeners() {
        productListener?.remove()
        productListener = null
        customerListener?.remove()
        customerListener = null
        expenseListener?.remove()
        expenseListener = null
    }

    /**
     * One-Tap Cloud Backup & Full Bidirectional Sync.
     * Pushes all local Room records to Firestore and pulls remote updates.
     * If documents were deleted in Firestore, they are deleted locally as well.
     */
    suspend fun performFullCloudSync(shopId: String = FirebaseManager.currentShopId): Result<String> = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: run {
            _syncState.value = CloudSyncState.OFFLINE
            _syncMessage.value = "Local Mode - Cloud Offline"
            return@withContext Result.failure(Exception("Firestore not initialized"))
        }
        _syncState.value = CloudSyncState.SYNCING
        _syncMessage.value = "Synchronizing with Cloud..."
        try {
            val now = System.currentTimeMillis()

            // 1. Sync Shop Profile
            val shopProfile = database.shopDao().getShopProfileSync() ?: ShopProfile()
            val shopDoc = ShopDoc(
                id = shopId,
                shopName = shopProfile.shopName,
                shopHindiName = shopProfile.shopHindiName,
                tagline = shopProfile.tagline,
                ownerName = shopProfile.ownerName,
                phone = shopProfile.phone,
                email = shopProfile.email,
                address = shopProfile.address,
                gstin = shopProfile.gstin,
                upiId = shopProfile.upiId,
                invoicePrefix = shopProfile.invoicePrefix,
                nextInvoiceNumber = shopProfile.nextInvoiceNumber,
                taxRatePercent = shopProfile.taxRatePercent,
                isTaxEnabled = shopProfile.isTaxEnabled,
                thankYouMessage = shopProfile.thankYouMessage,
                ownerUserId = FirebaseManager.currentUserId,
                createdAt = now,
                updatedAt = now
            )
            firestore.collection("shops").document(shopId).set(shopDoc, SetOptions.merge()).await()

            // 2. Push & Pull Products (Mirrors deletion from Firebase)
            val remoteProductsSnap = firestore.collection("products")
                .whereEqualTo("shopId", shopId)
                .get()
                .await()

            val remoteProducts = remoteProductsSnap.documents.mapNotNull { it.toObject(ProductDoc::class.java) }
            val activeRemoteProducts = remoteProducts.filter { !it.isArchived }

            val localProducts = database.productDao().getAllActiveProductsSync()

            // Push any local products that don't exist remotely
            val remoteDocIds = remoteProductsSnap.documents.map { it.id }.toSet()
            val unsavedLocalProducts = localProducts.filter { p ->
                val docId = if (p.sku.isNotBlank()) "${shopId}_${p.sku}" else "${shopId}_prod_${p.id}"
                docId !in remoteDocIds
            }

            if (unsavedLocalProducts.isNotEmpty()) {
                val batch = firestore.batch()
                unsavedLocalProducts.forEach { p ->
                    val docId = if (p.sku.isNotBlank()) "${shopId}_${p.sku}" else "${shopId}_prod_${p.id}"
                    val ref = firestore.collection("products").document(docId)
                    val prodDoc = ProductDoc(
                        id = docId,
                        shopId = shopId,
                        sku = p.sku,
                        name = p.name,
                        hindiName = p.hindiName,
                        category = p.category,
                        unit = p.unit,
                        purchasePrice = p.purchasePrice,
                        sellingPrice = p.sellingPrice,
                        currentStock = p.currentStock,
                        minStockLevel = p.minStockLevel,
                        supplierName = p.supplierName,
                        description = p.description,
                        colorHex = p.colorHex,
                        isArchived = p.isArchived,
                        createdAt = p.createdAt,
                        updatedAt = p.updatedAt
                    )
                    batch.set(ref, prodDoc, SetOptions.merge())
                }
                batch.commit().await()
            }

            // Sync from Remote to Local: Insert/update active remote products
            val remoteProductEntities = activeRemoteProducts.map { doc ->
                val parsedId = doc.id.substringAfterLast("_prod_", "").toLongOrNull() ?: 0L
                ProductEntity(
                    id = parsedId,
                    sku = doc.sku,
                    name = doc.name,
                    hindiName = doc.hindiName,
                    category = doc.category,
                    unit = doc.unit,
                    purchasePrice = doc.purchasePrice,
                    sellingPrice = doc.sellingPrice,
                    currentStock = doc.currentStock,
                    minStockLevel = doc.minStockLevel,
                    supplierName = doc.supplierName,
                    description = doc.description,
                    colorHex = doc.colorHex,
                    isArchived = false,
                    createdAt = doc.createdAt,
                    updatedAt = doc.updatedAt
                )
            }
            if (remoteProductEntities.isNotEmpty()) {
                database.productDao().insertAllProducts(remoteProductEntities)
                val validProductIds = remoteProductEntities.map { it.id }.filter { it > 0 }
                if (validProductIds.isNotEmpty()) {
                    database.productDao().deleteProductsNotIn(validProductIds)
                }
            } else {
                // If cloud has 0 active products, clear local products
                database.productDao().clearAllProducts()
            }

            // 3. Push & Pull Customers (Mirrors deletion from Firebase)
            val remoteCustSnap = firestore.collection("customers")
                .whereEqualTo("shopId", shopId)
                .get()
                .await()

            val remoteCustomers = remoteCustSnap.documents.mapNotNull { it.toObject(CustomerDoc::class.java) }
            val localCustomers = database.customerDao().getAllCustomersSync()

            val remoteCustDocIds = remoteCustSnap.documents.map { it.id }.toSet()
            val unsavedLocalCustomers = localCustomers.filter { c ->
                val docId = if (c.mobile.isNotBlank()) "${shopId}_cust_${c.mobile}" else "${shopId}_cust_${c.id}"
                docId !in remoteCustDocIds
            }

            if (unsavedLocalCustomers.isNotEmpty()) {
                val custBatch = firestore.batch()
                unsavedLocalCustomers.forEach { c ->
                    val docId = if (c.mobile.isNotBlank()) "${shopId}_cust_${c.mobile}" else "${shopId}_cust_${c.id}"
                    val ref = firestore.collection("customers").document(docId)
                    val custDoc = CustomerDoc(
                        id = docId,
                        shopId = shopId,
                        name = c.name,
                        mobile = c.mobile,
                        address = c.address,
                        totalOrders = c.totalOrders,
                        totalSpent = c.totalSpent,
                        totalProfitGenerated = c.totalProfitGenerated,
                        outstandingBalance = c.outstandingBalance,
                        lastPurchaseDate = c.lastPurchaseDate,
                        notes = c.notes,
                        createdAt = c.createdAt,
                        updatedAt = c.updatedAt
                    )
                    custBatch.set(ref, custDoc, SetOptions.merge())
                }
                custBatch.commit().await()
            }

            // Sync from Remote to Local: Insert/update remote customers, prune deleted ones
            val remoteCustomerEntities = remoteCustomers.map { doc ->
                val parsedId = doc.id.substringAfterLast("_cust_", "").toLongOrNull() ?: 0L
                CustomerEntity(
                    id = parsedId,
                    name = doc.name,
                    mobile = doc.mobile,
                    address = doc.address,
                    totalOrders = doc.totalOrders,
                    totalSpent = doc.totalSpent,
                    totalProfitGenerated = doc.totalProfitGenerated,
                    outstandingBalance = doc.outstandingBalance,
                    lastPurchaseDate = doc.lastPurchaseDate,
                    notes = doc.notes,
                    createdAt = doc.createdAt,
                    updatedAt = doc.updatedAt
                )
            }
            if (remoteCustomerEntities.isNotEmpty()) {
                database.customerDao().insertAllCustomers(remoteCustomerEntities)
                val validCustIds = remoteCustomerEntities.map { it.id }.filter { it > 0 }
                if (validCustIds.isNotEmpty()) {
                    database.customerDao().deleteCustomersNotIn(validCustIds)
                }
            } else {
                database.customerDao().clearAllCustomers()
            }

            // 4. Push & Pull Expenses (Mirrors deletion from Firebase)
            val remoteExpSnap = firestore.collection("expenses")
                .whereEqualTo("shopId", shopId)
                .get()
                .await()

            val remoteExpenses = remoteExpSnap.documents.mapNotNull { it.toObject(ExpenseDoc::class.java) }
            val localExpenses = database.expenseDao().getAllExpensesSync()

            val remoteExpDocIds = remoteExpSnap.documents.map { it.id }.toSet()
            val unsavedLocalExpenses = localExpenses.filter { exp ->
                val docId = "${shopId}_exp_${exp.id}"
                docId !in remoteExpDocIds
            }

            if (unsavedLocalExpenses.isNotEmpty()) {
                val expBatch = firestore.batch()
                unsavedLocalExpenses.forEach { exp ->
                    val docId = "${shopId}_exp_${exp.id}"
                    val ref = firestore.collection("expenses").document(docId)
                    val expDoc = ExpenseDoc(
                        id = docId,
                        shopId = shopId,
                        expenseDate = exp.expenseDate,
                        category = exp.category,
                        description = exp.description,
                        amount = exp.amount,
                        paymentMethod = exp.paymentMethod,
                        notes = exp.notes,
                        receiptRef = exp.receiptRef,
                        createdByUserId = FirebaseManager.currentUserId,
                        createdAt = exp.createdAt,
                        updatedAt = exp.createdAt
                    )
                    expBatch.set(ref, expDoc, SetOptions.merge())
                }
                expBatch.commit().await()
            }

            val remoteExpenseEntities = remoteExpenses.map { doc ->
                val parsedId = doc.id.substringAfterLast("_exp_", "").toLongOrNull() ?: 0L
                ExpenseEntity(
                    id = parsedId,
                    expenseDate = doc.expenseDate,
                    category = doc.category,
                    description = doc.description,
                    amount = doc.amount,
                    paymentMethod = doc.paymentMethod,
                    notes = doc.notes,
                    receiptRef = doc.receiptRef,
                    createdAt = doc.createdAt
                )
            }
            if (remoteExpenseEntities.isNotEmpty()) {
                database.expenseDao().insertAllExpenses(remoteExpenseEntities)
                val validExpIds = remoteExpenseEntities.map { it.id }.filter { it > 0 }
                if (validExpIds.isNotEmpty()) {
                    database.expenseDao().deleteExpensesNotIn(validExpIds)
                }
            } else {
                database.expenseDao().clearAllExpenses()
            }

            // Start / refresh real-time listeners
            startRealtimeListeners(shopId)

            _lastSyncTime.value = System.currentTimeMillis()
            _syncState.value = CloudSyncState.SUCCESS
            _syncMessage.value = "Synced with Firestore (Deletions & Updates applied)"
            Log.d(TAG, "Full Cloud Firestore Sync and Deletion Mirror completed successfully.")
            Result.success("Sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync error: ${e.message}", e)
            _syncState.value = CloudSyncState.ERROR
            _syncMessage.value = "Sync Error: ${e.localizedMessage}"
            Result.failure(e)
        }
    }

    /**
     * Saves or updates a single Product in Firestore directly
     */
    suspend fun syncProductToCloud(
        product: ProductEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = if (product.sku.isNotBlank()) "${shopId}_${product.sku}" else "${shopId}_prod_${product.id}"
            val ref = firestore.collection("products").document(docId)
            val prodDoc = ProductDoc(
                id = docId,
                shopId = shopId,
                sku = product.sku,
                name = product.name,
                hindiName = product.hindiName,
                category = product.category,
                unit = product.unit,
                purchasePrice = product.purchasePrice,
                sellingPrice = product.sellingPrice,
                currentStock = product.currentStock,
                minStockLevel = product.minStockLevel,
                supplierName = product.supplierName,
                description = product.description,
                colorHex = product.colorHex,
                isArchived = product.isArchived,
                createdAt = product.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            ref.set(prodDoc, SetOptions.merge()).await()
            Log.d(TAG, "Product $docId saved directly to Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Product save to Firestore queued/cached: ${e.message}")
        }
    }

    /**
     * Archives / deletes a Product in Firestore directly
     */
    suspend fun deleteProductFromCloud(
        product: ProductEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = if (product.sku.isNotBlank()) "${shopId}_${product.sku}" else "${shopId}_prod_${product.id}"
            val ref = firestore.collection("products").document(docId)
            // Mark archived in Firestore or delete
            ref.update(mapOf("isArchived" to true, "updatedAt" to System.currentTimeMillis())).await()
            Log.d(TAG, "Product $docId archived in Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Product archive in Firestore queued/cached: ${e.message}")
        }
    }

    /**
     * Saves or updates a single Customer in Firestore directly
     */
    suspend fun syncCustomerToCloud(
        customer: CustomerEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = if (customer.mobile.isNotBlank()) "${shopId}_cust_${customer.mobile}" else "${shopId}_cust_${customer.id}"
            val ref = firestore.collection("customers").document(docId)
            val custDoc = CustomerDoc(
                id = docId,
                shopId = shopId,
                name = customer.name,
                mobile = customer.mobile,
                address = customer.address,
                totalOrders = customer.totalOrders,
                totalSpent = customer.totalSpent,
                totalProfitGenerated = customer.totalProfitGenerated,
                outstandingBalance = customer.outstandingBalance,
                lastPurchaseDate = customer.lastPurchaseDate,
                notes = customer.notes,
                createdAt = customer.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            ref.set(custDoc, SetOptions.merge()).await()
            Log.d(TAG, "Customer $docId saved directly to Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Customer save to Firestore queued/cached: ${e.message}")
        }
    }

    /**
     * Saves or updates a single Expense in Firestore directly
     */
    suspend fun syncExpenseToCloud(
        expense: ExpenseEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = "${shopId}_exp_${expense.id}"
            val ref = firestore.collection("expenses").document(docId)
            val expDoc = ExpenseDoc(
                id = docId,
                shopId = shopId,
                expenseDate = expense.expenseDate,
                category = expense.category,
                description = expense.description,
                amount = expense.amount,
                paymentMethod = expense.paymentMethod,
                notes = expense.notes,
                receiptRef = expense.receiptRef,
                createdByUserId = FirebaseManager.currentUserId,
                createdAt = expense.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            ref.set(expDoc, SetOptions.merge()).await()
            Log.d(TAG, "Expense $docId saved directly to Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Expense save to Firestore queued/cached: ${e.message}")
        }
    }

    /**
     * Deletes an Expense in Firestore directly
     */
    suspend fun deleteExpenseFromCloud(
        expenseId: Long,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = "${shopId}_exp_${expenseId}"
            firestore.collection("expenses").document(docId).delete().await()
            Log.d(TAG, "Expense $docId deleted from Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Expense delete from Firestore queued/cached: ${e.message}")
        }
    }

    /**
     * Records a payment against customer Udhaar in Firestore
     */
    suspend fun syncPaymentToCloud(
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        val paymentDoc = PaymentDoc(
            shopId = shopId,
            referenceType = "CUSTOMER_UDHAAR",
            customerId = "${shopId}_cust_$customerId",
            amount = amount,
            paymentMethod = paymentMethod,
            notes = notes,
            createdByUserId = FirebaseManager.currentUserId,
            paymentDate = System.currentTimeMillis()
        )
        transactionManager.executePaymentTransaction(shopId, paymentDoc)
    }

    /**
     * Records a stock audit adjustment in Firestore directly
     */
    suspend fun syncStockAdjustmentToCloud(
        productId: Long,
        productName: String,
        diff: Double,
        newStock: Double,
        reason: String,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val prodDocId = "${shopId}_prod_$productId"
            val now = System.currentTimeMillis()
            
            // 1. Update product stock
            firestore.collection("products").document(prodDocId)
                .update(mapOf("currentStock" to newStock, "updatedAt" to now)).await()

            // 2. Add stock transaction audit entry
            val stockTxRef = firestore.collection("stockTransactions").document()
            val stockTx = StockTransactionDoc(
                id = stockTxRef.id,
                shopId = shopId,
                productId = prodDocId,
                productName = productName,
                transactionType = "ADJUSTMENT",
                quantityChange = diff,
                previousStock = (newStock - diff),
                newStock = newStock,
                referenceId = "AUDIT_${now}",
                referenceType = "MANUAL_AUDIT",
                reason = reason,
                createdByUserId = FirebaseManager.currentUserId,
                createdAt = now,
                updatedAt = now
            )
            stockTxRef.set(stockTx).await()
            Log.d(TAG, "Stock adjustment for $prodDocId saved to Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Stock adjustment write to Firestore cached: ${e.message}")
        }
    }

    /**
     * Uploads single sale to Firestore atomically in background
     */
    suspend fun syncSaleToCloud(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        try {
            val saleDoc = SaleDoc(
                shopId = shopId,
                invoiceNumber = sale.invoiceNumber,
                customerId = sale.customerId?.let { "${shopId}_cust_$it" },
                customerName = sale.customerName,
                customerMobile = sale.customerMobile,
                saleDate = sale.saleDate,
                subtotal = sale.subtotal,
                discount = sale.discount,
                taxAmount = sale.taxAmount,
                grandTotal = sale.grandTotal,
                totalCost = sale.totalCost,
                grossProfit = sale.grossProfit,
                profitMarginPercent = sale.profitMarginPercent,
                paymentMethod = sale.paymentMethod,
                paymentStatus = sale.paymentStatus,
                paidAmount = sale.paidAmount,
                balanceAmount = sale.balanceAmount,
                dueDate = sale.dueDate,
                notes = sale.notes,
                createdByUserId = FirebaseManager.currentUserId,
                createdAt = sale.createdAt,
                updatedAt = System.currentTimeMillis()
            )

            val saleItemDocs = items.map { item ->
                SaleItemDoc(
                    shopId = shopId,
                    productId = "${shopId}_prod_${item.productId}",
                    productName = item.productName,
                    unit = item.unit,
                    quantity = item.quantity,
                    sellingPrice = item.sellingPrice,
                    costPrice = item.costPrice,
                    discount = item.discount,
                    totalAmount = item.totalAmount,
                    itemProfit = item.itemProfit
                )
            }

            transactionManager.executeSaleTransaction(
                shopId = shopId,
                saleDoc = saleDoc,
                saleItems = saleItemDocs,
                customerId = saleDoc.customerId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Background Firestore sale sync cached locally: ${e.message}")
        }
    }

    /**
     * Uploads single purchase to Firestore atomically in background
     */
    suspend fun syncPurchaseToCloud(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        try {
            val purchaseDoc = PurchaseDoc(
                shopId = shopId,
                purchaseDate = purchase.purchaseDate,
                supplierId = "${shopId}_supp_${purchase.supplierName.hashCode()}",
                supplierName = purchase.supplierName,
                supplierPhone = purchase.supplierPhone,
                invoiceReference = purchase.invoiceReference,
                totalAmount = purchase.totalAmount,
                paymentStatus = purchase.paymentStatus,
                paymentMethod = purchase.paymentMethod,
                paidAmount = purchase.totalAmount,
                notes = purchase.notes,
                createdByUserId = FirebaseManager.currentUserId
            )

            val purchaseItemDocs = items.map { item ->
                PurchaseItemDoc(
                    shopId = shopId,
                    productId = "${shopId}_prod_${item.productId}",
                    productName = item.productName,
                    unit = item.unit,
                    quantity = item.quantity,
                    purchasePrice = item.purchasePrice,
                    totalAmount = item.totalAmount
                )
            }

            transactionManager.executePurchaseTransaction(
                shopId = shopId,
                purchaseDoc = purchaseDoc,
                purchaseItems = purchaseItemDocs,
                supplierId = purchaseDoc.supplierId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Background Firestore purchase sync cached locally: ${e.message}")
        }
    }
}
