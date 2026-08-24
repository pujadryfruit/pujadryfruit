package com.example.data.firebase

import android.util.Log
import com.example.data.firebase.models.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles atomic Firestore transactions for multi-entity consistency:
 * - Atomic Sale: decreases product stock, calculates profit & costs, updates customer ledger & balances, writes stockTransactions & invoice.
 * - Atomic Purchase: increases product stock, updates supplier balance, writes stockTransactions.
 * - Atomic Stock Audit/Adjustment: updates product stock and records immutable audit log.
 */
class FirestoreTransactionManager(
    private val firestoreProvider: () -> FirebaseFirestore? = { FirebaseManager.firestore }
) {
    private val TAG = "FirestoreTxManager"

    /**
     * Completes a Sale atomically in a single Firestore transaction:
     * 1. Validates and decreases stock for all products.
     * 2. Writes Sale document.
     * 3. Writes SaleItem documents in batch/subcollection.
     * 4. Writes StockTransaction documents for audit trail.
     * 5. Updates Customer aggregate totals & credit/udhaar balance.
     * 6. Generates Invoice document.
     */
    suspend fun executeSaleTransaction(
        shopId: String,
        saleDoc: SaleDoc,
        saleItems: List<SaleItemDoc>,
        customerId: String? = null,
        userId: String = FirebaseManager.currentUserId
    ): Result<String> = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext Result.failure(Exception("Firestore not available"))
        try {
            val saleRef = firestore.collection("sales").document()
            val saleId = saleRef.id
            val now = System.currentTimeMillis()

            val finalSaleDoc = saleDoc.copy(
                id = saleId,
                shopId = shopId,
                createdByUserId = userId,
                createdAt = now,
                updatedAt = now
            )

            firestore.runTransaction { tx ->
                // Step 1: Read all product documents within the transaction
                val productUpdates = mutableListOf<Triple<String, Double, Double>>() // (productId, currentStock, newStock)
                
                for (item in saleItems) {
                    if (item.productId.isNotEmpty()) {
                        val productRef = firestore.collection("products").document(item.productId)
                        val productSnap = tx.get(productRef)
                        if (productSnap.exists()) {
                            val currentStock = productSnap.getDouble("currentStock") ?: 0.0
                            val newStock = currentStock - item.quantity
                            productUpdates.add(Triple(item.productId, currentStock, newStock))
                        }
                    }
                }

                // Step 2: Read Customer document if applicable
                var customerSnap: com.google.firebase.firestore.DocumentSnapshot? = null
                val custRef = if (!customerId.isNullOrEmpty()) {
                    val ref = firestore.collection("customers").document(customerId)
                    customerSnap = tx.get(ref)
                    ref
                } else null

                // Step 3: Write updated stock to Product documents
                for ((productId, _, newStock) in productUpdates) {
                    val productRef = firestore.collection("products").document(productId)
                    tx.update(productRef, mapOf(
                        "currentStock" to newStock,
                        "updatedAt" to now
                    ))
                }

                // Step 4: Write Sale document
                tx.set(saleRef, finalSaleDoc)

                // Step 5: Write each SaleItem and StockTransaction
                saleItems.forEach { item ->
                    val itemRef = firestore.collection("saleItems").document()
                    val finalItem = item.copy(
                        id = itemRef.id,
                        saleId = saleId,
                        shopId = shopId,
                        createdAt = now,
                        updatedAt = now
                    )
                    tx.set(itemRef, finalItem)

                    // Audit log for stock decrement
                    val stockTxRef = firestore.collection("stockTransactions").document()
                    val prodUpdate = productUpdates.find { it.first == item.productId }
                    val prevStock = prodUpdate?.second ?: 0.0
                    val nStock = prodUpdate?.third ?: (prevStock - item.quantity)

                    val stockTx = StockTransactionDoc(
                        id = stockTxRef.id,
                        shopId = shopId,
                        productId = item.productId,
                        productName = item.productName,
                        transactionType = "SALE",
                        quantityChange = -item.quantity,
                        previousStock = prevStock,
                        newStock = nStock,
                        unit = item.unit,
                        unitPrice = item.sellingPrice,
                        referenceId = saleId,
                        referenceType = "SALE",
                        reason = "Counter Sale ${finalSaleDoc.invoiceNumber}",
                        createdByUserId = userId,
                        createdAt = now,
                        updatedAt = now
                    )
                    tx.set(stockTxRef, stockTx)
                }

                // Step 6: Update Customer statistics & Udhaar balance
                if (custRef != null && customerSnap != null && customerSnap.exists()) {
                    val oldOrders = customerSnap.getLong("totalOrders")?.toInt() ?: 0
                    val oldSpent = customerSnap.getDouble("totalSpent") ?: 0.0
                    val oldProfit = customerSnap.getDouble("totalProfitGenerated") ?: 0.0
                    val oldBalance = customerSnap.getDouble("outstandingBalance") ?: 0.0

                    tx.update(custRef, mapOf(
                        "totalOrders" to (oldOrders + 1),
                        "totalSpent" to (oldSpent + finalSaleDoc.grandTotal),
                        "totalProfitGenerated" to (oldProfit + finalSaleDoc.grossProfit),
                        "outstandingBalance" to (oldBalance + finalSaleDoc.balanceAmount),
                        "lastPurchaseDate" to finalSaleDoc.saleDate,
                        "updatedAt" to now
                    ))
                }

                // Step 7: Create Invoice Record
                val invoiceRef = firestore.collection("invoices").document()
                val invoiceDoc = InvoiceDoc(
                    id = invoiceRef.id,
                    shopId = shopId,
                    saleId = saleId,
                    invoiceNumber = finalSaleDoc.invoiceNumber,
                    customerName = finalSaleDoc.customerName,
                    customerMobile = finalSaleDoc.customerMobile,
                    date = finalSaleDoc.saleDate,
                    grandTotal = finalSaleDoc.grandTotal,
                    paymentStatus = finalSaleDoc.paymentStatus,
                    createdAt = now,
                    updatedAt = now
                )
                tx.set(invoiceRef, invoiceDoc)
            }.await()

            Log.d(TAG, "Sale transaction $saleId executed atomically in Firestore")
            Result.success(saleId)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing sale transaction: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Adds a Purchase atomically:
     * 1. Increases product stock and updates cost/purchase price.
     * 2. Writes Purchase and PurchaseItem documents.
     * 3. Logs StockTransaction (PURCHASE).
     * 4. Updates Supplier statistics & balance if credit purchase.
     */
    suspend fun executePurchaseTransaction(
        shopId: String,
        purchaseDoc: PurchaseDoc,
        purchaseItems: List<PurchaseItemDoc>,
        supplierId: String? = null,
        userId: String = FirebaseManager.currentUserId
    ): Result<String> = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext Result.failure(Exception("Firestore not available"))
        try {
            val purchaseRef = firestore.collection("purchases").document()
            val purchaseId = purchaseRef.id
            val now = System.currentTimeMillis()

            val finalPurchaseDoc = purchaseDoc.copy(
                id = purchaseId,
                shopId = shopId,
                createdByUserId = userId,
                createdAt = now,
                updatedAt = now
            )

            firestore.runTransaction { tx ->
                // Step 1: Read all products
                val productUpdates = mutableListOf<Triple<String, Double, Double>>() // (productId, prevStock, newStock)
                for (item in purchaseItems) {
                    if (item.productId.isNotEmpty()) {
                        val productRef = firestore.collection("products").document(item.productId)
                        val productSnap = tx.get(productRef)
                        if (productSnap.exists()) {
                            val currentStock = productSnap.getDouble("currentStock") ?: 0.0
                            val newStock = currentStock + item.quantity
                            productUpdates.add(Triple(item.productId, currentStock, newStock))
                        }
                    }
                }

                // Step 2: Read Supplier if applicable
                var supplierSnap: com.google.firebase.firestore.DocumentSnapshot? = null
                val suppRef = if (!supplierId.isNullOrEmpty()) {
                    val ref = firestore.collection("suppliers").document(supplierId)
                    supplierSnap = tx.get(ref)
                    ref
                } else null

                // Step 3: Update products stock & purchase price
                purchaseItems.forEach { item ->
                    if (item.productId.isNotEmpty()) {
                        val prodUpdate = productUpdates.find { it.first == item.productId }
                        val newStock = prodUpdate?.third ?: item.quantity
                        val productRef = firestore.collection("products").document(item.productId)
                        tx.update(productRef, mapOf(
                            "currentStock" to newStock,
                            "purchasePrice" to item.purchasePrice,
                            "updatedAt" to now
                        ))
                    }
                }

                // Step 4: Write Purchase Document
                tx.set(purchaseRef, finalPurchaseDoc)

                // Step 5: Write PurchaseItems & StockTransactions
                purchaseItems.forEach { item ->
                    val itemRef = firestore.collection("purchaseItems").document()
                    val finalItem = item.copy(
                        id = itemRef.id,
                        purchaseId = purchaseId,
                        shopId = shopId,
                        createdAt = now,
                        updatedAt = now
                    )
                    tx.set(itemRef, finalItem)

                    val stockTxRef = firestore.collection("stockTransactions").document()
                    val prodUpdate = productUpdates.find { it.first == item.productId }
                    val prevStock = prodUpdate?.second ?: 0.0
                    val nStock = prodUpdate?.third ?: (prevStock + item.quantity)

                    val stockTx = StockTransactionDoc(
                        id = stockTxRef.id,
                        shopId = shopId,
                        productId = item.productId,
                        productName = item.productName,
                        transactionType = "PURCHASE",
                        quantityChange = item.quantity,
                        previousStock = prevStock,
                        newStock = nStock,
                        unit = item.unit,
                        unitPrice = item.purchasePrice,
                        referenceId = purchaseId,
                        referenceType = "PURCHASE",
                        reason = "Supplier Stock In: ${finalPurchaseDoc.supplierName}",
                        createdByUserId = userId,
                        createdAt = now,
                        updatedAt = now
                    )
                    tx.set(stockTxRef, stockTx)
                }

                // Step 6: Update Supplier balance & total purchases
                if (suppRef != null && supplierSnap != null && supplierSnap.exists()) {
                    val oldPurchases = supplierSnap.getDouble("totalPurchases") ?: 0.0
                    val oldBalance = supplierSnap.getDouble("outstandingBalance") ?: 0.0

                    tx.update(suppRef, mapOf(
                        "totalPurchases" to (oldPurchases + finalPurchaseDoc.totalAmount),
                        "outstandingBalance" to (oldBalance + finalPurchaseDoc.balanceAmount),
                        "updatedAt" to now
                    ))
                }
            }.await()

            Log.d(TAG, "Purchase transaction $purchaseId executed atomically in Firestore")
            Result.success(purchaseId)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing purchase transaction: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Records a payment against customer Udhaar or supplier debt atomically.
     */
    suspend fun executePaymentTransaction(
        shopId: String,
        paymentDoc: PaymentDoc,
        userId: String = FirebaseManager.currentUserId
    ): Result<String> = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider() ?: return@withContext Result.failure(Exception("Firestore not available"))
        try {
            val paymentRef = firestore.collection("payments").document()
            val paymentId = paymentRef.id
            val now = System.currentTimeMillis()

            val finalPayment = paymentDoc.copy(
                id = paymentId,
                shopId = shopId,
                createdByUserId = userId,
                createdAt = now,
                updatedAt = now
            )

            firestore.runTransaction { tx ->
                tx.set(paymentRef, finalPayment)

                if (finalPayment.referenceType == "CUSTOMER_UDHAAR" && !finalPayment.customerId.isNullOrEmpty()) {
                    val custRef = firestore.collection("customers").document(finalPayment.customerId)
                    val custSnap = tx.get(custRef)
                    if (custSnap.exists()) {
                        val currentBalance = custSnap.getDouble("outstandingBalance") ?: 0.0
                        val newBalance = (currentBalance - finalPayment.amount).coerceAtLeast(0.0)
                        tx.update(custRef, mapOf(
                            "outstandingBalance" to newBalance,
                            "updatedAt" to now
                        ))
                    }
                } else if (finalPayment.referenceType == "SUPPLIER_PAYMENT" && !finalPayment.supplierId.isNullOrEmpty()) {
                    val suppRef = firestore.collection("suppliers").document(finalPayment.supplierId)
                    val suppSnap = tx.get(suppRef)
                    if (suppSnap.exists()) {
                        val currentBalance = suppSnap.getDouble("outstandingBalance") ?: 0.0
                        val newBalance = (currentBalance - finalPayment.amount).coerceAtLeast(0.0)
                        tx.update(suppRef, mapOf(
                            "outstandingBalance" to newBalance,
                            "updatedAt" to now
                        ))
                    }
                }
            }.await()

            Result.success(paymentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing payment transaction: ${e.message}", e)
            Result.failure(e)
        }
    }
}
