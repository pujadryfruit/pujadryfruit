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
import kotlinx.coroutines.sync.Mutex
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
    private val fullSyncMutex = Mutex()

    private val _syncState = MutableStateFlow(CloudSyncState.IDLE)
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _syncMessage = MutableStateFlow("Local Storage Active")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private var productListener: ListenerRegistration? = null
    private var customerListener: ListenerRegistration? = null
    private var supplierListener: ListenerRegistration? = null
    private var expenseListener: ListenerRegistration? = null
    private var paymentListener: ListenerRegistration? = null

    /**
     * Initializes real-time listener for products, customers, suppliers, expenses & payments from Firestore to local Room.
     * Uses efficient snapshot listeners restricted by shopId and prevents duplicate row creation.
     */
    fun startRealtimeListeners(shopId: String = FirebaseManager.currentShopId) {
        stopRealtimeListeners()
        try {
            val firestore = firestoreProvider() ?: run {
                Log.d(TAG, "Firestore not available for real-time listeners; running offline mode.")
                return
            }
            if (FirebaseManager.auth?.currentUser == null) {
                Log.d(TAG, "Cloud sync listeners deferred: Local offline mode.")
                return
            }
            productListener = firestore.collection("products")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.d(TAG, "Product cloud listener: Auth required, running in local mode")
                            stopRealtimeListeners()
                        } else {
                            Log.w(TAG, "Product listener error: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        syncScope.launch {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document.toObject(ProductDoc::class.java)
                                val docId = change.document.id
                                val parsedId = docId.substringAfterLast("_prod_", "").toLongOrNull()
                                
                                val existingProduct = (if (parsedId != null && parsedId > 0) database.productDao().getProductById(parsedId) else null)
                                    ?: (if (doc.sku.isNotBlank()) database.productDao().getProductBySku(doc.sku) else null)
                                    ?: database.productDao().getProductByName(doc.name)

                                when (change.type) {
                                    DocumentChange.Type.REMOVED -> {
                                        val targetId = existingProduct?.id ?: parsedId
                                        if (targetId != null && targetId > 0) {
                                            database.productDao().deleteProductById(targetId)
                                        }
                                    }
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val targetId = existingProduct?.id ?: (parsedId ?: 0L)
                                        if (doc.isArchived) {
                                            if (targetId > 0) {
                                                database.productDao().archiveProduct(targetId)
                                            }
                                        } else {
                                            // Check if local product has newer updates than the incoming Firestore doc
                                            if (existingProduct != null && existingProduct.updatedAt > doc.updatedAt) {
                                                // Local product has newer changes (e.g. recent sales/adjustments).
                                                // Prevent local stock from being overridden by stale remote stock.
                                                // Sync the latest local product to Firestore.
                                                syncScope.launch {
                                                    syncProductToCloud(existingProduct, shopId)
                                                }
                                            } else {
                                                val entity = ProductEntity(
                                                    id = targetId,
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
                }

            customerListener = firestore.collection("customers")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.d(TAG, "Customer cloud listener: Auth required")
                        } else {
                            Log.w(TAG, "Customer listener error: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        syncScope.launch {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document.toObject(CustomerDoc::class.java)
                                val docId = change.document.id
                                val parsedId = docId.substringAfterLast("_cust_", "").toLongOrNull()

                                val existingCustomer = (if (parsedId != null && parsedId > 0) database.customerDao().getCustomerById(parsedId) else null)
                                    ?: (if (doc.mobile.isNotBlank()) database.customerDao().getCustomerByMobile(doc.mobile) else null)
                                    ?: database.customerDao().getCustomerByName(doc.name)

                                when (change.type) {
                                    DocumentChange.Type.REMOVED -> {
                                        val targetId = existingCustomer?.id ?: parsedId
                                        if (targetId != null && targetId > 0) {
                                            database.customerDao().deleteCustomerById(targetId)
                                        } else if (doc.mobile.isNotBlank()) {
                                            database.customerDao().deleteCustomerByMobile(doc.mobile)
                                        }
                                    }
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val targetId = existingCustomer?.id ?: (parsedId ?: 0L)
                                        val entity = CustomerEntity(
                                            id = targetId,
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

            supplierListener = firestore.collection("suppliers")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.d(TAG, "Supplier cloud listener: Auth required")
                        } else {
                            Log.w(TAG, "Supplier listener error: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        syncScope.launch {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document.toObject(SupplierDoc::class.java)
                                val docId = change.document.id
                                val parsedId = docId.substringAfterLast("_supp_", "").toLongOrNull()

                                val existingSupplier = (if (parsedId != null && parsedId > 0) database.supplierDao().getSupplierById(parsedId) else null)
                                    ?: (if (doc.name.isNotBlank()) database.supplierDao().getSupplierByName(doc.name) else null)
                                    ?: (if (doc.mobile.isNotBlank()) database.supplierDao().getSupplierByMobile(doc.mobile) else null)

                                when (change.type) {
                                    DocumentChange.Type.REMOVED -> {
                                        val targetId = existingSupplier?.id ?: parsedId
                                        if (targetId != null && targetId > 0) {
                                            database.supplierDao().deleteSupplierById(targetId)
                                        }
                                    }
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val targetId = existingSupplier?.id ?: (parsedId ?: 0L)
                                        val entity = SupplierEntity(
                                            id = targetId,
                                            name = doc.name,
                                            contactPerson = doc.contactPerson,
                                            mobile = doc.mobile,
                                            email = doc.email,
                                            address = doc.address,
                                            gstin = doc.gstin,
                                            totalPurchases = doc.totalPurchases,
                                            outstandingBalance = doc.outstandingBalance,
                                            notes = doc.notes,
                                            createdAt = doc.createdAt,
                                            updatedAt = doc.updatedAt
                                        )
                                        database.supplierDao().insertSupplier(entity)
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
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.d(TAG, "Expense cloud listener: Auth required")
                        } else {
                            Log.w(TAG, "Expense listener error: ${error.message}")
                        }
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

            paymentListener = firestore.collection("payments")
                .whereEqualTo("shopId", shopId)
                .whereEqualTo("referenceType", "CUSTOMER_UDHAAR")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.d(TAG, "Payment cloud listener: Auth required")
                        } else {
                            Log.w(TAG, "Payment listener error: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        syncScope.launch {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document.toObject(PaymentDoc::class.java)
                                val docId = change.document.id
                                val parsedId = docId.substringAfterLast("_pay_", "").toLongOrNull()
                                val parsedCustId = doc.customerId?.substringAfterLast("_cust_", "")?.toLongOrNull() ?: 0L

                                when (change.type) {
                                    DocumentChange.Type.REMOVED -> {
                                        if (parsedId != null && parsedId > 0) {
                                            database.creditPaymentDao().deletePaymentById(parsedId)
                                        }
                                    }
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val custName = if (parsedCustId > 0) {
                                            database.customerDao().getCustomerById(parsedCustId)?.name ?: "Customer"
                                        } else "Customer"
                                        val entity = CreditPaymentEntity(
                                            id = parsedId ?: 0L,
                                            customerId = parsedCustId,
                                            customerName = custName,
                                            amount = doc.amount,
                                            paymentDate = doc.paymentDate,
                                            paymentMethod = doc.paymentMethod,
                                            notes = doc.notes
                                        )
                                        database.creditPaymentDao().insertPayment(entity)
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
        supplierListener?.remove()
        supplierListener = null
        expenseListener?.remove()
        expenseListener = null
        paymentListener?.remove()
        paymentListener = null
    }

    /**
     * One-Tap Cloud Backup & Full Bidirectional Sync.
     * Pushes all local Room records to Firestore and pulls remote updates.
     * If documents were deleted in Firestore, they are deleted locally as well.
     */
    suspend fun performFullCloudSync(shopId: String = FirebaseManager.currentShopId): Result<String> = withContext(Dispatchers.IO) {
        if (!fullSyncMutex.tryLock()) {
            Log.d(TAG, "Full cloud sync already in progress, skipping concurrent trigger.")
            return@withContext Result.success("Sync already in progress")
        }
        val firestore = firestoreProvider() ?: run {
            fullSyncMutex.unlock()
            _syncState.value = CloudSyncState.OFFLINE
            _syncMessage.value = "Local Mode - Offline Active"
            return@withContext Result.success("Local Mode Active")
        }
        val currentUser = FirebaseManager.auth?.currentUser
        if (currentUser == null) {
            fullSyncMutex.unlock()
            _syncState.value = CloudSyncState.OFFLINE
            _syncMessage.value = "Local Storage Active (Sign in for Cloud Backup)"
            Log.d(TAG, "Cloud sync deferred: User not signed in to Firebase Auth. Using local Room database.")
            return@withContext Result.success("Offline storage active")
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

            // 2. Bidirectional Intelligent Sync for Products with Timestamp Conflict Resolution
            val remoteProductsSnap = firestore.collection("products")
                .whereEqualTo("shopId", shopId)
                .get()
                .await()

            val remoteProducts = remoteProductsSnap.documents.mapNotNull { it.toObject(ProductDoc::class.java) }
            val activeRemoteProducts = remoteProducts.filter { !it.isArchived }
            val localProducts = database.productDao().getAllActiveProductsSync()

            val localProductMap = localProducts.associateBy { it.id }
            val remoteDocMap = activeRemoteProducts.associateBy { it.id.substringAfterLast("_prod_", "").toLongOrNull() ?: 0L }

            val productsToPushToCloud = mutableListOf<ProductEntity>()
            val productsToUpdateLocally = mutableListOf<ProductEntity>()

            // Evaluate local products against remote
            for (local in localProducts) {
                val remote = remoteDocMap[local.id]
                if (remote == null) {
                    // Not yet in cloud, push local
                    productsToPushToCloud.add(local)
                } else {
                    if (local.updatedAt > remote.updatedAt) {
                        // Local has newer changes (sales, inward, adjustments) - push to cloud
                        productsToPushToCloud.add(local)
                    } else if (remote.updatedAt > local.updatedAt) {
                        // Cloud has newer changes - update local
                        productsToUpdateLocally.add(
                            ProductEntity(
                                id = local.id,
                                sku = remote.sku,
                                name = remote.name,
                                hindiName = remote.hindiName,
                                category = remote.category,
                                unit = remote.unit,
                                purchasePrice = remote.purchasePrice,
                                sellingPrice = remote.sellingPrice,
                                currentStock = remote.currentStock,
                                minStockLevel = remote.minStockLevel,
                                supplierName = remote.supplierName,
                                description = remote.description,
                                colorHex = remote.colorHex,
                                isArchived = false,
                                createdAt = remote.createdAt,
                                updatedAt = remote.updatedAt
                            )
                        )
                    }
                }
            }

            // Evaluate remote products that don't exist locally
            for (remote in activeRemoteProducts) {
                val parsedId = remote.id.substringAfterLast("_prod_", "").toLongOrNull() ?: 0L
                if (parsedId > 0 && !localProductMap.containsKey(parsedId)) {
                    productsToUpdateLocally.add(
                        ProductEntity(
                            id = parsedId,
                            sku = remote.sku,
                            name = remote.name,
                            hindiName = remote.hindiName,
                            category = remote.category,
                            unit = remote.unit,
                            purchasePrice = remote.purchasePrice,
                            sellingPrice = remote.sellingPrice,
                            currentStock = remote.currentStock,
                            minStockLevel = remote.minStockLevel,
                            supplierName = remote.supplierName,
                            description = remote.description,
                            colorHex = remote.colorHex,
                            isArchived = false,
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt
                        )
                    )
                }
            }

            // Push newer / unsaved local products to cloud
            if (productsToPushToCloud.isNotEmpty()) {
                val batch = firestore.batch()
                productsToPushToCloud.forEach { p ->
                    val docId = "${shopId}_prod_${p.id}"
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

            // Update only genuinely newer remote products into local database
            if (productsToUpdateLocally.isNotEmpty()) {
                database.productDao().insertAllProducts(productsToUpdateLocally)
            }

            // 3. Push & Pull Customers
            val remoteCustSnap = firestore.collection("customers")
                .whereEqualTo("shopId", shopId)
                .get()
                .await()

            val remoteCustomers = remoteCustSnap.documents.mapNotNull { it.toObject(CustomerDoc::class.java) }
            val localCustomers = database.customerDao().getAllCustomersSync()

            val remoteCustDocIds = remoteCustSnap.documents.map { it.id }.toSet()
            val unsavedLocalCustomers = localCustomers.filter { c ->
                val docId = "${shopId}_cust_${c.id}"
                docId !in remoteCustDocIds
            }

            if (unsavedLocalCustomers.isNotEmpty()) {
                val custBatch = firestore.batch()
                unsavedLocalCustomers.forEach { c ->
                    val docId = "${shopId}_cust_${c.id}"
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

            // 4. Push & Pull Suppliers
            val remoteSuppSnap = firestore.collection("suppliers")
                .whereEqualTo("shopId", shopId)
                .get()
                .await()

            val remoteSuppliers = remoteSuppSnap.documents.mapNotNull { it.toObject(SupplierDoc::class.java) }
            val localSuppliers = database.supplierDao().getAllSuppliersSync()
            val remoteSuppDocIds = remoteSuppSnap.documents.map { it.id }.toSet()

            val unsavedLocalSuppliers = localSuppliers.filter { s ->
                val docId = "${shopId}_supp_${s.id}"
                docId !in remoteSuppDocIds
            }

            if (unsavedLocalSuppliers.isNotEmpty()) {
                val suppBatch = firestore.batch()
                unsavedLocalSuppliers.forEach { s ->
                    val docId = "${shopId}_supp_${s.id}"
                    val ref = firestore.collection("suppliers").document(docId)
                    val suppDoc = SupplierDoc(
                        id = docId,
                        shopId = shopId,
                        name = s.name,
                        contactPerson = s.contactPerson,
                        mobile = s.mobile,
                        email = s.email,
                        address = s.address,
                        gstin = s.gstin,
                        totalPurchases = s.totalPurchases,
                        outstandingBalance = s.outstandingBalance,
                        notes = s.notes,
                        createdAt = s.createdAt,
                        updatedAt = s.updatedAt
                    )
                    suppBatch.set(ref, suppDoc, SetOptions.merge())
                }
                suppBatch.commit().await()
            }

            val remoteSupplierEntities = remoteSuppliers.map { doc ->
                val parsedId = doc.id.substringAfterLast("_supp_", "").toLongOrNull() ?: 0L
                SupplierEntity(
                    id = parsedId,
                    name = doc.name,
                    contactPerson = doc.contactPerson,
                    mobile = doc.mobile,
                    email = doc.email,
                    address = doc.address,
                    gstin = doc.gstin,
                    totalPurchases = doc.totalPurchases,
                    outstandingBalance = doc.outstandingBalance,
                    notes = doc.notes,
                    createdAt = doc.createdAt,
                    updatedAt = doc.updatedAt
                )
            }
            if (remoteSupplierEntities.isNotEmpty()) {
                database.supplierDao().insertAllSuppliers(remoteSupplierEntities)
            }

            // 5. Push & Pull Expenses
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

            // 6. Push Local Sales and Pull Remote Sales
            try {
                // A. Cleanup any existing local duplicates (e.g. phantom PDF-... rows created before login)
                cleanupLocalSalesDuplicates()

                val localSales = database.saleDao().getAllSalesSync()
                val remoteSalesSnap = firestore.collection("sales")
                    .whereEqualTo("shopId", shopId)
                    .get()
                    .await()

                // B. Prune remote phantom duplicates from Firestore
                pruneRemoteSalesDuplicates(shopId, firestore, remoteSalesSnap)

                // Re-evaluate remote sales documents after pruning
                val remoteSalesDocs = remoteSalesSnap.documents.mapNotNull { doc ->
                    doc.toObject(SaleDoc::class.java)?.copy(id = doc.id)
                }

                val datePattern = Regex("^\\d{8}\\d{3,}$")
                val dateBasedRemote = remoteSalesDocs.filter { datePattern.matches(it.invoiceNumber) }

                // Find phantom duplicate remote invoices that should be ignored
                val phantomRemoteInvoices = mutableSetOf<String>()
                for (doc in remoteSalesDocs) {
                    if (!datePattern.matches(doc.invoiceNumber)) {
                        val hasEquivalent = dateBasedRemote.any { dateDoc ->
                            Math.abs(dateDoc.saleDate - doc.saleDate) <= 180_000L &&
                            Math.abs(dateDoc.grandTotal - doc.grandTotal) < 0.01 &&
                            (dateDoc.customerName.equals(doc.customerName, ignoreCase = true) ||
                             (dateDoc.customerMobile.isNotBlank() && dateDoc.customerMobile == doc.customerMobile))
                        }
                        if (hasEquivalent) {
                            phantomRemoteInvoices.add(doc.invoiceNumber)
                        }
                    }
                }

                val validRemoteDocs = remoteSalesDocs.filter { it.invoiceNumber !in phantomRemoteInvoices }
                val remoteInvoiceNumbers = validRemoteDocs.map { it.invoiceNumber }.toSet()
                
                // Push any unsynced canonical local sales to Firestore immediately
                val unsyncedLocalSales = localSales.filter { it.invoiceNumber !in remoteInvoiceNumbers }
                for (unsynced in unsyncedLocalSales) {
                    val saleItems = database.saleDao().getSaleItemsSync(unsynced.id)
                    val saleDocId = "${shopId}_sale_${unsynced.invoiceNumber}"
                    val saleItemDocs = saleItems.mapIndexed { idx, item ->
                        val itemDocId = "${saleDocId}_item_${item.productId}_$idx"
                        SaleItemDoc(
                            id = itemDocId,
                            saleId = saleDocId,
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
                    val saleDoc = SaleDoc(
                        id = saleDocId,
                        shopId = shopId,
                        invoiceNumber = unsynced.invoiceNumber,
                        customerId = unsynced.customerId?.let { "${shopId}_cust_$it" },
                        customerName = unsynced.customerName,
                        customerMobile = unsynced.customerMobile,
                        saleDate = unsynced.saleDate,
                        subtotal = unsynced.subtotal,
                        discount = unsynced.discount,
                        taxAmount = unsynced.taxAmount,
                        grandTotal = unsynced.grandTotal,
                        totalCost = unsynced.totalCost,
                        grossProfit = unsynced.grossProfit,
                        profitMarginPercent = unsynced.profitMarginPercent,
                        paymentMethod = unsynced.paymentMethod,
                        paymentStatus = unsynced.paymentStatus,
                        paidAmount = unsynced.paidAmount,
                        balanceAmount = unsynced.balanceAmount,
                        dueDate = unsynced.dueDate,
                        notes = unsynced.notes,
                        isCancelled = unsynced.isCancelled,
                        cancelledAt = unsynced.cancelledAt,
                        cancellationReason = unsynced.cancellationReason,
                        createdByUserId = FirebaseManager.currentUserId,
                        createdAt = unsynced.createdAt,
                        updatedAt = System.currentTimeMillis(),
                        items = saleItemDocs
                    )
                    firestore.collection("sales").document(saleDocId).set(saleDoc, SetOptions.merge()).await()

                    saleItemDocs.forEach { itemDoc ->
                        firestore.collection("saleItems").document(itemDoc.id).set(itemDoc, SetOptions.merge()).await()
                    }
                }

                // Pull any valid remote sales not present locally
                for (saleDoc in validRemoteDocs) {
                    if (saleDoc.invoiceNumber.isNotBlank()) {
                        val existingSale = database.saleDao().getSaleByInvoiceNumber(saleDoc.invoiceNumber)
                        val hasEquivalentLocal = localSales.any { local ->
                            local.invoiceNumber == saleDoc.invoiceNumber || (
                                Math.abs(local.saleDate - saleDoc.saleDate) <= 180_000L &&
                                Math.abs(local.grandTotal - saleDoc.grandTotal) < 0.01 &&
                                (local.customerName.equals(saleDoc.customerName, ignoreCase = true) ||
                                 (local.customerMobile.isNotBlank() && local.customerMobile == saleDoc.customerMobile))
                            )
                        }
                        if (existingSale == null && !hasEquivalentLocal) {
                            val parsedCustId = saleDoc.customerId?.substringAfterLast("_cust_", "")?.toLongOrNull()
                            val saleEntity = SaleEntity(
                                invoiceNumber = saleDoc.invoiceNumber,
                                customerId = parsedCustId,
                                customerName = saleDoc.customerName,
                                customerMobile = saleDoc.customerMobile,
                                saleDate = saleDoc.saleDate,
                                subtotal = saleDoc.subtotal,
                                discount = saleDoc.discount,
                                taxAmount = saleDoc.taxAmount,
                                grandTotal = saleDoc.grandTotal,
                                totalCost = saleDoc.totalCost,
                                grossProfit = saleDoc.grossProfit,
                                profitMarginPercent = saleDoc.profitMarginPercent,
                                paymentMethod = saleDoc.paymentMethod,
                                paymentStatus = saleDoc.paymentStatus,
                                paidAmount = saleDoc.paidAmount,
                                balanceAmount = saleDoc.balanceAmount,
                                dueDate = saleDoc.dueDate,
                                notes = saleDoc.notes,
                                isCancelled = saleDoc.isCancelled,
                                cancelledAt = saleDoc.cancelledAt,
                                cancellationReason = saleDoc.cancellationReason,
                                createdAt = saleDoc.createdAt
                            )
                            val insertedSaleId = database.saleDao().insertSale(saleEntity)

                            // Load items: prefer embedded saleDoc.items, fallback to collection query
                            val itemEntities = if (saleDoc.items.isNotEmpty()) {
                                saleDoc.items.map { itemDoc ->
                                    val parsedProdId = itemDoc.productId.substringAfterLast("_prod_", "").toLongOrNull() ?: 0L
                                    SaleItemEntity(
                                        saleId = insertedSaleId,
                                        productId = parsedProdId,
                                        productName = itemDoc.productName,
                                        unit = itemDoc.unit,
                                        quantity = itemDoc.quantity,
                                        sellingPrice = itemDoc.sellingPrice,
                                        costPrice = itemDoc.costPrice,
                                        discount = itemDoc.discount,
                                        totalAmount = itemDoc.totalAmount,
                                        itemProfit = itemDoc.itemProfit
                                    )
                                }
                            } else {
                                val itemsSnap = firestore.collection("saleItems")
                                    .whereEqualTo("saleId", saleDoc.id)
                                    .get()
                                    .await()

                                itemsSnap.documents.mapNotNull { it.toObject(SaleItemDoc::class.java) }.map { itemDoc ->
                                    val parsedProdId = itemDoc.productId.substringAfterLast("_prod_", "").toLongOrNull() ?: 0L
                                    SaleItemEntity(
                                        saleId = insertedSaleId,
                                        productId = parsedProdId,
                                        productName = itemDoc.productName,
                                        unit = itemDoc.unit,
                                        quantity = itemDoc.quantity,
                                        sellingPrice = itemDoc.sellingPrice,
                                        costPrice = itemDoc.costPrice,
                                        discount = itemDoc.discount,
                                        totalAmount = itemDoc.totalAmount,
                                        itemProfit = itemDoc.itemProfit
                                    )
                                }
                            }
                            if (itemEntities.isNotEmpty()) {
                                database.saleDao().insertSaleItems(itemEntities)
                            }
                        }
                    }
                }
            } catch (se: Exception) {
                Log.w(TAG, "Sales cloud sync warning: ${se.message}")
            }

            // 7. Push & Pull Purchases
            try {
                val localPurchases = database.purchaseDao().getAllPurchasesSync()
                val remotePurchasesSnap = firestore.collection("purchases")
                    .whereEqualTo("shopId", shopId)
                    .get()
                    .await()

                val remotePurchaseIds = remotePurchasesSnap.documents.map { it.id }.toSet()
                val unsavedLocalPurchases = localPurchases.filter { "${shopId}_pur_${it.id}" !in remotePurchaseIds }

                for (pur in unsavedLocalPurchases) {
                    val purDocId = "${shopId}_pur_${pur.id}"
                    val purDoc = PurchaseDoc(
                        id = purDocId,
                        shopId = shopId,
                        purchaseDate = pur.purchaseDate,
                        supplierId = "${shopId}_supp_${pur.supplierName.hashCode()}",
                        supplierName = pur.supplierName,
                        supplierPhone = pur.supplierPhone,
                        invoiceReference = pur.invoiceReference,
                        totalAmount = pur.totalAmount,
                        paymentStatus = pur.paymentStatus,
                        paymentMethod = pur.paymentMethod,
                        paidAmount = pur.totalAmount,
                        notes = pur.notes,
                        createdByUserId = FirebaseManager.currentUserId,
                        createdAt = pur.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                    firestore.collection("purchases").document(purDocId).set(purDoc, SetOptions.merge()).await()

                    val purItems = database.purchaseDao().getPurchaseItemsSync(pur.id)
                    purItems.forEachIndexed { idx, item ->
                        val itemDocId = "${purDocId}_item_${item.productId}_$idx"
                        val itemDoc = PurchaseItemDoc(
                            id = itemDocId,
                            purchaseId = purDocId,
                            shopId = shopId,
                            productId = "${shopId}_prod_${item.productId}",
                            productName = item.productName,
                            unit = item.unit,
                            quantity = item.quantity,
                            purchasePrice = item.purchasePrice,
                            totalAmount = item.totalAmount
                        )
                        firestore.collection("purchaseItems").document(itemDocId).set(itemDoc, SetOptions.merge()).await()
                    }
                }

                for (docSnap in remotePurchasesSnap.documents) {
                    val purDoc = docSnap.toObject(PurchaseDoc::class.java)
                    if (purDoc != null) {
                        val parsedId = docSnap.id.substringAfterLast("_pur_", "").toLongOrNull() ?: 0L
                        val existingPur = if (parsedId > 0) database.purchaseDao().getPurchaseById(parsedId) else null
                        if (existingPur == null) {
                            val purchaseEntity = PurchaseEntity(
                                id = parsedId,
                                purchaseDate = purDoc.purchaseDate,
                                supplierName = purDoc.supplierName,
                                supplierPhone = purDoc.supplierPhone,
                                invoiceReference = purDoc.invoiceReference,
                                totalAmount = purDoc.totalAmount,
                                paymentStatus = purDoc.paymentStatus,
                                paymentMethod = purDoc.paymentMethod,
                                notes = purDoc.notes,
                                createdAt = purDoc.createdAt
                            )
                            val insertedPurId = database.purchaseDao().insertPurchase(purchaseEntity)

                            val itemsSnap = firestore.collection("purchaseItems")
                                .whereEqualTo("purchaseId", purDoc.id)
                                .get()
                                .await()

                            val itemEntities = itemsSnap.documents.mapNotNull { it.toObject(PurchaseItemDoc::class.java) }.map { itemDoc ->
                                val parsedProdId = itemDoc.productId.substringAfterLast("_prod_", "").toLongOrNull() ?: 0L
                                PurchaseItemEntity(
                                    purchaseId = if (parsedId > 0) parsedId else insertedPurId,
                                    productId = parsedProdId,
                                    productName = itemDoc.productName,
                                    unit = itemDoc.unit,
                                    quantity = itemDoc.quantity,
                                    purchasePrice = itemDoc.purchasePrice,
                                    totalAmount = itemDoc.totalAmount
                                )
                            }
                            if (itemEntities.isNotEmpty()) {
                                database.purchaseDao().insertPurchaseItems(itemEntities)
                            }
                        }
                    }
                }
            } catch (pe: Exception) {
                Log.w(TAG, "Purchases cloud sync warning: ${pe.message}")
            }

            // 8. Push & Pull Credit Payments
            try {
                val localPayments = database.creditPaymentDao().getAllCreditPaymentsSync()
                val remotePaymentsSnap = firestore.collection("payments")
                    .whereEqualTo("shopId", shopId)
                    .whereEqualTo("referenceType", "CUSTOMER_UDHAAR")
                    .get()
                    .await()

                val remotePaymentIds = remotePaymentsSnap.documents.map { it.id }.toSet()
                val unsavedPayments = localPayments.filter { "${shopId}_pay_${it.id}" !in remotePaymentIds }

                for (pay in unsavedPayments) {
                    val payDoc = PaymentDoc(
                        id = "${shopId}_pay_${pay.id}",
                        shopId = shopId,
                        referenceType = "CUSTOMER_UDHAAR",
                        customerId = "${shopId}_cust_${pay.customerId}",
                        amount = pay.amount,
                        paymentMethod = pay.paymentMethod,
                        notes = pay.notes,
                        createdByUserId = FirebaseManager.currentUserId,
                        paymentDate = pay.paymentDate
                    )
                    firestore.collection("payments").document(payDoc.id).set(payDoc, SetOptions.merge()).await()
                }

                for (docSnap in remotePaymentsSnap.documents) {
                    val payDoc = docSnap.toObject(PaymentDoc::class.java)
                    if (payDoc != null) {
                        val parsedId = docSnap.id.substringAfterLast("_pay_", "").toLongOrNull() ?: 0L
                        val parsedCustId = payDoc.customerId?.substringAfterLast("_cust_", "")?.toLongOrNull() ?: 0L
                        val existingPay = if (parsedId > 0) database.creditPaymentDao().getPaymentById(parsedId) else null
                        if (existingPay == null) {
                            val custName = if (parsedCustId > 0) {
                                database.customerDao().getCustomerById(parsedCustId)?.name ?: "Customer"
                            } else "Customer"
                            val payEntity = CreditPaymentEntity(
                                id = parsedId,
                                customerId = parsedCustId,
                                customerName = custName,
                                amount = payDoc.amount,
                                paymentDate = payDoc.paymentDate,
                                paymentMethod = payDoc.paymentMethod,
                                notes = payDoc.notes
                            )
                            database.creditPaymentDao().insertPayment(payEntity)
                        }
                    }
                }
            } catch (payEx: Exception) {
                Log.w(TAG, "Credit payments cloud sync warning: ${payEx.message}")
            }

            // Start / refresh real-time listeners
            startRealtimeListeners(shopId)

            _lastSyncTime.value = System.currentTimeMillis()
            _syncState.value = CloudSyncState.SUCCESS
            _syncMessage.value = "Synced with Firestore (Deletions & Updates applied)"
            Log.d(TAG, "Full Cloud Firestore Sync completed successfully.")
            Result.success("Sync completed successfully")
        } catch (e: Exception) {
            val isPermissionDenied = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
            if (isPermissionDenied) {
                Log.d(TAG, "Cloud sync deferred: Firestore authentication required (running offline mode).")
                _syncState.value = CloudSyncState.OFFLINE
                _syncMessage.value = "Local Mode Active (Sign in for Cloud Sync)"
                Result.success("Local mode active")
            } else {
                Log.e(TAG, "Cloud sync error: ${e.message}", e)
                _syncState.value = CloudSyncState.ERROR
                _syncMessage.value = "Sync Error: ${e.localizedMessage}"
                Result.failure(e)
            }
        } finally {
            if (fullSyncMutex.isLocked) {
                fullSyncMutex.unlock()
            }
        }
    }

    /**
     * Saves or updates a single Product in Firestore directly
     */
    suspend fun syncProductToCloud(
        product: ProductEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = "${shopId}_prod_${product.id}"
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
     * Permanently deletes a Product in Firestore directly
     */
    suspend fun deleteProductFromCloud(
        product: ProductEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val batch = firestore.batch()
            val docId = "${shopId}_prod_${product.id}"
            val directRef = firestore.collection("products").document(docId)
            batch.delete(directRef)

            // Also search by SKU or name to ensure any duplicate doc is deleted
            if (product.sku.isNotBlank()) {
                val skuSnap = firestore.collection("products")
                    .whereEqualTo("shopId", shopId)
                    .whereEqualTo("sku", product.sku)
                    .get()
                    .await()
                for (doc in skuSnap.documents) {
                    batch.delete(doc.reference)
                }
            }

            if (product.name.isNotBlank()) {
                val nameSnap = firestore.collection("products")
                    .whereEqualTo("shopId", shopId)
                    .whereEqualTo("name", product.name)
                    .get()
                    .await()
                for (doc in nameSnap.documents) {
                    batch.delete(doc.reference)
                }
            }

            batch.commit().await()
            Log.d(TAG, "Product $docId (${product.name}) permanently deleted from Firestore")
        } catch (e: Exception) {
            val isPermDenied = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
            if (isPermDenied) {
                Log.d(TAG, "Product delete local offline mode")
            } else {
                Log.w(TAG, "Product delete in Firestore queued/cached: ${e.message}")
            }
        }
    }

    /**
     * Saves or updates a single Customer in Firestore directly
     */
    suspend fun syncCustomerToCloud(
        customer: CustomerEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = "${shopId}_cust_${customer.id}"
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
     * Saves or updates a single Supplier in Firestore directly
     */
    suspend fun syncSupplierToCloud(
        supplier: SupplierEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = "${shopId}_supp_${supplier.id}"
            val ref = firestore.collection("suppliers").document(docId)
            val suppDoc = SupplierDoc(
                id = docId,
                shopId = shopId,
                name = supplier.name,
                contactPerson = supplier.contactPerson,
                mobile = supplier.mobile,
                email = supplier.email,
                address = supplier.address,
                gstin = supplier.gstin,
                totalPurchases = supplier.totalPurchases,
                outstandingBalance = supplier.outstandingBalance,
                notes = supplier.notes,
                createdAt = supplier.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            ref.set(suppDoc, SetOptions.merge()).await()
            Log.d(TAG, "Supplier $docId saved directly to Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Supplier save to Firestore queued/cached: ${e.message}")
        }
    }

    /**
     * Saves or updates a single Expense in Firestore directly
     */
    suspend fun syncExpenseToCloud(
        expense: ExpenseEntity,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
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
        if (FirebaseManager.auth?.currentUser == null) return@withContext
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
     * Deletes a Customer in Firestore directly
     */
    suspend fun deleteCustomerFromCloud(
        customerId: Long,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val docId = "${shopId}_cust_${customerId}"
            firestore.collection("customers").document(docId).delete().await()
            Log.d(TAG, "Customer $docId deleted from Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Customer delete from Firestore queued/cached: ${e.message}")
        }
    }

    /**
     * Records a payment against customer Udhaar in Firestore atomically,
     * and updates affected sales/invoices payment statuses.
     */
    suspend fun syncPaymentToCloud(
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String,
        paymentId: Long = 0L,
        updatedSales: List<SaleEntity> = emptyList(),
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        val paymentDoc = PaymentDoc(
            id = if (paymentId > 0) "${shopId}_pay_$paymentId" else "",
            shopId = shopId,
            referenceType = "CUSTOMER_UDHAAR",
            customerId = "${shopId}_cust_$customerId",
            amount = amount,
            paymentMethod = paymentMethod,
            notes = notes,
            createdByUserId = FirebaseManager.currentUserId,
            paymentDate = System.currentTimeMillis()
        )
        transactionManager.executePaymentTransaction(shopId, paymentDoc, updatedSales)
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
        if (FirebaseManager.auth?.currentUser == null) return@withContext
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
        val firestore = firestoreProvider() ?: return@withContext
        try {
            val saleItemDocs = items.mapIndexed { idx, item ->
                val itemDocId = "${shopId}_sale_${sale.invoiceNumber}_item_${item.productId}_$idx"
                SaleItemDoc(
                    id = itemDocId,
                    saleId = "${shopId}_sale_${sale.invoiceNumber}",
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

            val saleDoc = SaleDoc(
                id = "${shopId}_sale_${sale.invoiceNumber}",
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
                updatedAt = System.currentTimeMillis(),
                items = saleItemDocs
            )

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
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        try {
            val isPaid = purchase.paymentStatus.equals("PAID", true)
            val purchaseDoc = PurchaseDoc(
                id = "${shopId}_pur_${purchase.id}",
                shopId = shopId,
                purchaseDate = purchase.purchaseDate,
                supplierId = "${shopId}_supp_${purchase.supplierName.hashCode()}",
                supplierName = purchase.supplierName,
                supplierPhone = purchase.supplierPhone,
                invoiceReference = purchase.invoiceReference,
                totalAmount = purchase.totalAmount,
                paymentStatus = purchase.paymentStatus,
                paymentMethod = purchase.paymentMethod,
                paidAmount = if (isPaid) purchase.totalAmount else 0.0,
                balanceAmount = if (isPaid) 0.0 else purchase.totalAmount,
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

    /**
     * Updates an existing Purchase record and adjusts product stock in Firestore
     */
    suspend fun syncPurchaseUpdateToCloud(
        purchase: PurchaseEntity,
        newItems: List<PurchaseItemEntity>,
        oldItems: List<PurchaseItemEntity>,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        try {
            val isPaid = purchase.paymentStatus.equals("PAID", true)
            val purchaseDoc = PurchaseDoc(
                id = "${shopId}_pur_${purchase.id}",
                shopId = shopId,
                purchaseDate = purchase.purchaseDate,
                supplierId = "${shopId}_supp_${purchase.supplierName.hashCode()}",
                supplierName = purchase.supplierName,
                supplierPhone = purchase.supplierPhone,
                invoiceReference = purchase.invoiceReference,
                totalAmount = purchase.totalAmount,
                paymentStatus = purchase.paymentStatus,
                paymentMethod = purchase.paymentMethod,
                paidAmount = if (isPaid) purchase.totalAmount else 0.0,
                balanceAmount = if (isPaid) 0.0 else purchase.totalAmount,
                notes = purchase.notes,
                createdByUserId = FirebaseManager.currentUserId
            )

            val newDocs = newItems.map { item ->
                PurchaseItemDoc(
                    shopId = shopId,
                    purchaseId = purchaseDoc.id,
                    productId = "${shopId}_prod_${item.productId}",
                    productName = item.productName,
                    unit = item.unit,
                    quantity = item.quantity,
                    purchasePrice = item.purchasePrice,
                    totalAmount = item.totalAmount
                )
            }

            val oldDocs = oldItems.map { item ->
                PurchaseItemDoc(
                    shopId = shopId,
                    purchaseId = purchaseDoc.id,
                    productId = "${shopId}_prod_${item.productId}",
                    productName = item.productName,
                    unit = item.unit,
                    quantity = item.quantity,
                    purchasePrice = item.purchasePrice,
                    totalAmount = item.totalAmount
                )
            }

            transactionManager.executePurchaseUpdateTransaction(
                shopId = shopId,
                purchaseDoc = purchaseDoc,
                newItems = newDocs,
                oldItems = oldDocs,
                supplierId = purchaseDoc.supplierId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Background Firestore purchase update sync error: ${e.message}")
        }
    }

    /**
     * Deletes a Purchase record in Firestore
     */
    suspend fun syncPurchaseDeleteToCloud(
        purchaseId: Long,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        try {
            transactionManager.executePurchaseDeleteTransaction(shopId, purchaseId)
        } catch (e: Exception) {
            Log.w(TAG, "Background Firestore purchase delete sync error: ${e.message}")
        }
    }

    /**
     * Cancels a Sale in Firestore
     */
    suspend fun syncSaleCancelToCloud(
        saleId: Long,
        invoiceNumber: String,
        reason: String,
        shopId: String = FirebaseManager.currentShopId
    ) = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext
        try {
            transactionManager.executeSaleCancelTransaction(shopId, saleId, invoiceNumber, reason)
        } catch (e: Exception) {
            Log.w(TAG, "Background Firestore sale cancel sync error: ${e.message}")
        }
    }

    /**
     * Cleans up local Room database to eliminate any phantom duplicate sales created by previous bugs.
     * Preserves valid sales and their items.
     */
    private suspend fun cleanupLocalSalesDuplicates() {
        val localSales = database.saleDao().getAllSalesSync()
        if (localSales.isEmpty()) return

        val datePattern = Regex("^\\d{8}\\d{3,}$")
        val dateBasedSales = localSales.filter { datePattern.matches(it.invoiceNumber) }
        val nonDateSales = localSales.filter { !datePattern.matches(it.invoiceNumber) }

        val idsToDelete = mutableSetOf<Long>()

        for (nonDateSale in nonDateSales) {
            // Check if there is an equivalent date-based sale
            val duplicate = dateBasedSales.firstOrNull { dateSale ->
                Math.abs(dateSale.saleDate - nonDateSale.saleDate) <= 180_000L &&
                Math.abs(dateSale.grandTotal - nonDateSale.grandTotal) < 0.01 &&
                (dateSale.customerName.equals(nonDateSale.customerName, ignoreCase = true) ||
                 (dateSale.customerMobile.isNotBlank() && dateSale.customerMobile == nonDateSale.customerMobile))
            }
            if (duplicate != null) {
                idsToDelete.add(nonDateSale.id)
                Log.i(TAG, "Cleaning up local phantom duplicate sale: id=${nonDateSale.id}, inv=${nonDateSale.invoiceNumber}")
            }
        }

        // Also check if any duplicate sales share identical invoice numbers
        val groupedByInvoice = localSales.filter { it.id !in idsToDelete }.groupBy { it.invoiceNumber }
        for ((_, salesWithSameInvoice) in groupedByInvoice) {
            if (salesWithSameInvoice.size > 1) {
                // Keep the lowest ID (first inserted), delete subsequent duplicates
                salesWithSameInvoice.drop(1).forEach { idsToDelete.add(it.id) }
            }
        }

        for (id in idsToDelete) {
            database.saleDao().deleteSaleItemsBySaleId(id)
            database.saleDao().deleteSaleById(id)
        }
    }

    /**
     * Prunes remote duplicate sales from Firestore if phantom "PDF-..." copies were previously created.
     */
    private suspend fun pruneRemoteSalesDuplicates(
        shopId: String,
        firestore: FirebaseFirestore,
        remoteSalesSnap: com.google.firebase.firestore.QuerySnapshot
    ) {
        try {
            val datePattern = Regex("^\\d{8}\\d{3,}$")
            val remoteDocs = remoteSalesSnap.documents.mapNotNull { doc ->
                doc.toObject(SaleDoc::class.java)?.copy(id = doc.id)
            }
            val dateBasedRemote = remoteDocs.filter { datePattern.matches(it.invoiceNumber) }
            val nonDateRemote = remoteDocs.filter { !datePattern.matches(it.invoiceNumber) }

            for (nonDateSale in nonDateRemote) {
                val duplicate = dateBasedRemote.firstOrNull { dateSale ->
                    Math.abs(dateSale.saleDate - nonDateSale.saleDate) <= 180_000L &&
                    Math.abs(dateSale.grandTotal - nonDateSale.grandTotal) < 0.01 &&
                    (dateSale.customerName.equals(nonDateSale.customerName, ignoreCase = true) ||
                     (dateSale.customerMobile.isNotBlank() && dateSale.customerMobile == nonDateSale.customerMobile))
                }
                if (duplicate != null) {
                    firestore.collection("sales").document(nonDateSale.id).delete().await()
                    firestore.collection("invoices").document("${shopId}_inv_${nonDateSale.invoiceNumber}").delete().await()
                    Log.i(TAG, "Pruned remote phantom duplicate sale: docId=${nonDateSale.id}, inv=${nonDateSale.invoiceNumber}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warning while pruning remote duplicates: ${e.message}")
        }
    }
}
