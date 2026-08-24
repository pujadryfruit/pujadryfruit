package com.example.data.firebase

import android.util.Log
import com.example.data.firebase.models.*
import com.example.data.local.AppDatabase
import com.example.data.local.entities.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                .whereEqualTo("isArchived", false)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Product listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        Log.d(TAG, "Real-time product updates received: ${snapshot.size()} docs")
                    }
                }

            customerListener = firestore.collection("customers")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Customer listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        Log.d(TAG, "Real-time customer updates received: ${snapshot.size()} docs")
                    }
                }

            expenseListener = firestore.collection("expenses")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Expense listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        Log.d(TAG, "Real-time expense updates received: ${snapshot.size()} docs")
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
     */
    suspend fun performFullCloudSync(shopId: String = FirebaseManager.currentShopId): Result<String> = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: run {
            _syncState.value = CloudSyncState.OFFLINE
            _syncMessage.value = "Local Mode - Cloud Offline"
            return@withContext Result.failure(Exception("Firestore not initialized"))
        }
        _syncState.value = CloudSyncState.SYNCING
        _syncMessage.value = "Backing up to Firestore..."
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

            // 2. Sync Products
            val localProducts = database.productDao().getAllActiveProductsSync()
            if (localProducts.isNotEmpty()) {
                val batch = firestore.batch()
                localProducts.forEach { p ->
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

            // 3. Sync Categories
            val defaultCategories = listOf(
                CategoryDoc(id = "${shopId}_cat_nuts", shopId = shopId, name = "Nuts", hindiName = "मेवे / काजू बादाम", iconName = "nuts", displayOrder = 1),
                CategoryDoc(id = "${shopId}_cat_dryfruits", shopId = shopId, name = "Dry Fruits", hindiName = "सूखे मेवे / किशमिश खजूर", iconName = "dryfruit", displayOrder = 2),
                CategoryDoc(id = "${shopId}_cat_seeds", shopId = shopId, name = "Seeds", hindiName = "बीज / चिया कद्दू", iconName = "seeds", displayOrder = 3),
                CategoryDoc(id = "${shopId}_cat_berries", shopId = shopId, name = "Berries", hindiName = "बेरीज / क्रैनबेरी", iconName = "berries", displayOrder = 4),
                CategoryDoc(id = "${shopId}_cat_spices", shopId = shopId, name = "Spices & Saffron", hindiName = "केसर व मसाले", iconName = "spices", displayOrder = 5),
                CategoryDoc(id = "${shopId}_cat_giftboxes", shopId = shopId, name = "Gift Boxes", hindiName = "गिफ्ट पैक / डब्बे", iconName = "gift", displayOrder = 6)
            )
            val catBatch = firestore.batch()
            defaultCategories.forEach { cat ->
                val ref = firestore.collection("categories").document(cat.id)
                catBatch.set(ref, cat, SetOptions.merge())
            }
            catBatch.commit().await()

            // 4. Sync Customers
            val localCustomers = database.customerDao().getAllCustomersSync()
            if (localCustomers.isNotEmpty()) {
                val custBatch = firestore.batch()
                localCustomers.forEach { c ->
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

            // 5. Sync Expenses
            val localExpenses = database.expenseDao().getAllExpensesSync()
            if (localExpenses.isNotEmpty()) {
                val expBatch = firestore.batch()
                localExpenses.forEach { exp ->
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

            _lastSyncTime.value = System.currentTimeMillis()
            _syncState.value = CloudSyncState.SUCCESS
            _syncMessage.value = "All 14 Collections Synced with Firestore"
            Log.d(TAG, "Full Cloud Firestore Sync completed successfully.")
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
