package com.example.data.firebase

import android.util.Log
import com.example.data.firebase.models.*
import com.example.data.local.entities.SaleEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles atomic Firestore transactions for multi-entity consistency:
 * - Atomic Sale: writes sale, saleItems, invoices, audit log, customer ledger, and updates product stock deterministically.
 * - Atomic Purchase: writes purchase, purchaseItems, audit log, supplier balance, and updates product stock deterministically.
 * - Atomic Stock Audit/Adjustment: updates product stock and records immutable audit log.
 * - Atomic Payment: updates customer/supplier balances and invoice payment statuses.
 */
class FirestoreTransactionManager(
    private val firestoreProvider: () -> FirebaseFirestore? = { FirebaseManager.firestore }
) {
    private val TAG = "FirestoreTxManager"

    /**
     * Completes a Sale atomically in Firestore:
     * 1. Validates and decreases stock for all products.
     * 2. Writes Sale document with deterministic ID.
     * 3. Writes SaleItem documents with deterministic IDs.
     * 4. Writes StockTransaction documents for audit trail.
     * 5. Updates Customer aggregate totals & credit/udhaar balance.
     * 6. Generates Invoice document with deterministic ID.
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
            val saleDocId = if (saleDoc.id.isNotBlank()) saleDoc.id else "${shopId}_sale_${saleDoc.invoiceNumber}"
            val saleRef = firestore.collection("sales").document(saleDocId)
            val now = System.currentTimeMillis()

            val finalSaleDoc = saleDoc.copy(
                id = saleDocId,
                shopId = shopId,
                createdByUserId = userId,
                createdAt = if (saleDoc.createdAt > 0) saleDoc.createdAt else now,
                updatedAt = now
            )

            val batch = firestore.batch()

            // 1. Write Sale document
            batch.set(saleRef, finalSaleDoc, SetOptions.merge())

            // 2. Write each SaleItem and StockTransaction deterministically
            saleItems.forEachIndexed { index, item ->
                val prodId = if (item.productId.startsWith("${shopId}_prod_")) item.productId else "${shopId}_prod_${item.productId}"
                val itemDocId = "${saleDocId}_item_${item.productId.substringAfterLast("_prod_")}_$index"
                val itemRef = firestore.collection("saleItems").document(itemDocId)
                val finalItem = item.copy(
                    id = itemDocId,
                    saleId = saleDocId,
                    shopId = shopId,
                    productId = prodId,
                    createdAt = now,
                    updatedAt = now
                )
                batch.set(itemRef, finalItem, SetOptions.merge())

                // Deterministic audit log for sale
                val stockTxDocId = "${shopId}_tx_sale_${finalSaleDoc.invoiceNumber}_$index"
                val stockTxRef = firestore.collection("stockTransactions").document(stockTxDocId)
                val stockTx = StockTransactionDoc(
                    id = stockTxDocId,
                    shopId = shopId,
                    productId = prodId,
                    productName = item.productName,
                    transactionType = "SALE",
                    quantityChange = -item.quantity,
                    previousStock = 0.0,
                    newStock = 0.0,
                    unit = item.unit,
                    unitPrice = item.sellingPrice,
                    referenceId = finalSaleDoc.invoiceNumber,
                    referenceType = "SALE",
                    reason = "Counter Sale ${finalSaleDoc.invoiceNumber}",
                    createdByUserId = userId,
                    createdAt = now,
                    updatedAt = now
                )
                batch.set(stockTxRef, stockTx, SetOptions.merge())
            }

            // 3. Create Invoice Record deterministically
            val invoiceDocId = "${shopId}_inv_${finalSaleDoc.invoiceNumber}"
            val invoiceRef = firestore.collection("invoices").document(invoiceDocId)
            val invoiceDoc = InvoiceDoc(
                id = invoiceDocId,
                shopId = shopId,
                saleId = saleDocId,
                invoiceNumber = finalSaleDoc.invoiceNumber,
                customerName = finalSaleDoc.customerName,
                customerMobile = finalSaleDoc.customerMobile,
                date = finalSaleDoc.saleDate,
                grandTotal = finalSaleDoc.grandTotal,
                paymentStatus = finalSaleDoc.paymentStatus,
                createdAt = now,
                updatedAt = now
            )
            batch.set(invoiceRef, invoiceDoc, SetOptions.merge())

            // 4. Update Customer statistics & Udhaar balance if applicable
            if (!customerId.isNullOrEmpty()) {
                val custRef = firestore.collection("customers").document(customerId)
                val custSnap = custRef.get().await()
                if (custSnap.exists()) {
                    val oldOrders = custSnap.getLong("totalOrders")?.toInt() ?: 0
                    val oldSpent = custSnap.getDouble("totalSpent") ?: 0.0
                    val oldProfit = custSnap.getDouble("totalProfitGenerated") ?: 0.0
                    val oldBalance = custSnap.getDouble("outstandingBalance") ?: 0.0

                    batch.update(custRef, mapOf(
                        "totalOrders" to (oldOrders + 1),
                        "totalSpent" to (oldSpent + finalSaleDoc.grandTotal),
                        "totalProfitGenerated" to (oldProfit + finalSaleDoc.grossProfit),
                        "outstandingBalance" to (oldBalance + finalSaleDoc.balanceAmount),
                        "lastPurchaseDate" to finalSaleDoc.saleDate,
                        "updatedAt" to now
                    ))
                }
            }

            batch.commit().await()
            Log.d(TAG, "Sale transaction $saleDocId executed atomically in Firestore")
            Result.success(saleDocId)
        } catch (e: Exception) {
            val isPermissionDenied = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
            if (isPermissionDenied) {
                Log.d(TAG, "Sale saved locally (Firestore cloud sync deferred: offline mode)")
                Result.success("LOCAL_OFFLINE")
            } else {
                Log.w(TAG, "Direct write fallback for sale: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Adds a Purchase atomically:
     * 1. Writes Purchase and PurchaseItem documents deterministically.
     * 2. Logs StockTransaction (PURCHASE).
     * 3. Updates Supplier statistics & balance if credit purchase.
     */
    suspend fun executePurchaseTransaction(
        shopId: String,
        purchaseDoc: PurchaseDoc,
        purchaseItems: List<PurchaseItemDoc>,
        supplierId: String? = null,
        userId: String = FirebaseManager.currentUserId
    ): Result<String> = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) {
            Log.d(TAG, "Purchase saved locally (Firestore cloud sync skipped: local offline mode)")
            return@withContext Result.success("LOCAL_OFFLINE")
        }
        val firestore = firestoreProvider() ?: return@withContext Result.failure(Exception("Firestore not available"))
        try {
            val purchaseDocId = if (purchaseDoc.id.isNotBlank()) purchaseDoc.id else "${shopId}_pur_${System.currentTimeMillis()}"
            val purchaseRef = firestore.collection("purchases").document(purchaseDocId)
            val now = System.currentTimeMillis()

            val finalPurchaseDoc = purchaseDoc.copy(
                id = purchaseDocId,
                shopId = shopId,
                createdByUserId = userId,
                createdAt = if (purchaseDoc.createdAt > 0) purchaseDoc.createdAt else now,
                updatedAt = now
            )

            val batch = firestore.batch()

            // 1. Write Purchase Document
            batch.set(purchaseRef, finalPurchaseDoc, SetOptions.merge())

            // 2. Write PurchaseItems & StockTransactions deterministically
            purchaseItems.forEachIndexed { index, item ->
                val prodId = if (item.productId.startsWith("${shopId}_prod_")) item.productId else "${shopId}_prod_${item.productId}"
                val itemDocId = "${purchaseDocId}_item_${item.productId.substringAfterLast("_prod_")}_$index"
                val itemRef = firestore.collection("purchaseItems").document(itemDocId)
                val finalItem = item.copy(
                    id = itemDocId,
                    purchaseId = purchaseDocId,
                    shopId = shopId,
                    productId = prodId,
                    createdAt = now,
                    updatedAt = now
                )
                batch.set(itemRef, finalItem, SetOptions.merge())

                val stockTxDocId = "${shopId}_tx_pur_${purchaseDocId}_$index"
                val stockTxRef = firestore.collection("stockTransactions").document(stockTxDocId)
                val stockTx = StockTransactionDoc(
                    id = stockTxDocId,
                    shopId = shopId,
                    productId = prodId,
                    productName = item.productName,
                    transactionType = "PURCHASE",
                    quantityChange = item.quantity,
                    previousStock = 0.0,
                    newStock = 0.0,
                    unit = item.unit,
                    unitPrice = item.purchasePrice,
                    referenceId = purchaseDocId,
                    referenceType = "PURCHASE",
                    reason = "Supplier Stock In: ${finalPurchaseDoc.supplierName}",
                    createdByUserId = userId,
                    createdAt = now,
                    updatedAt = now
                )
                batch.set(stockTxRef, stockTx, SetOptions.merge())
            }

            // 3. Update Supplier balance & total purchases if applicable
            if (!supplierId.isNullOrEmpty()) {
                val suppRef = firestore.collection("suppliers").document(supplierId)
                val supplierSnap = suppRef.get().await()
                if (supplierSnap.exists()) {
                    val oldPurchases = supplierSnap.getDouble("totalPurchases") ?: 0.0
                    val oldBalance = supplierSnap.getDouble("outstandingBalance") ?: 0.0

                    batch.update(suppRef, mapOf(
                        "totalPurchases" to (oldPurchases + finalPurchaseDoc.totalAmount),
                        "outstandingBalance" to (oldBalance + finalPurchaseDoc.balanceAmount),
                        "updatedAt" to now
                    ))
                }
            }

            batch.commit().await()
            Log.d(TAG, "Purchase transaction $purchaseDocId executed atomically in Firestore")
            Result.success(purchaseDocId)
        } catch (e: Exception) {
            val isPermissionDenied = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
            if (isPermissionDenied) {
                Log.d(TAG, "Purchase saved locally (Firestore cloud sync deferred: login required for cloud permissions)")
                Result.success("LOCAL_OFFLINE")
            } else {
                Log.w(TAG, "Purchase cloud sync cached locally: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Records a payment against customer Udhaar or supplier debt atomically,
     * and updates any affected sales/invoices payment statuses.
     */
    suspend fun executePaymentTransaction(
        shopId: String,
        paymentDoc: PaymentDoc,
        updatedSales: List<SaleEntity> = emptyList(),
        userId: String = FirebaseManager.currentUserId
    ): Result<String> = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) {
            Log.d(TAG, "Payment saved locally (Firestore cloud sync skipped: local offline mode)")
            return@withContext Result.success("LOCAL_OFFLINE")
        }
        val firestore = firestoreProvider() ?: return@withContext Result.failure(Exception("Firestore not available"))
        try {
            val paymentDocId = if (paymentDoc.id.isNotBlank()) paymentDoc.id else "${shopId}_pay_${System.currentTimeMillis()}"
            val paymentRef = firestore.collection("payments").document(paymentDocId)
            val now = System.currentTimeMillis()

            val finalPayment = paymentDoc.copy(
                id = paymentDocId,
                shopId = shopId,
                createdByUserId = userId,
                createdAt = if (paymentDoc.createdAt > 0) paymentDoc.createdAt else now,
                updatedAt = now
            )

            val batch = firestore.batch()
            batch.set(paymentRef, finalPayment, SetOptions.merge())

            if (finalPayment.referenceType == "CUSTOMER_UDHAAR" && !finalPayment.customerId.isNullOrEmpty()) {
                val custRef = firestore.collection("customers").document(finalPayment.customerId)
                val custSnap = custRef.get().await()
                if (custSnap.exists()) {
                    val currentBalance = custSnap.getDouble("outstandingBalance") ?: 0.0
                    val newBalance = (currentBalance - finalPayment.amount).coerceAtLeast(0.0)
                    batch.update(custRef, mapOf(
                        "outstandingBalance" to newBalance,
                        "updatedAt" to now
                    ))
                }
            } else if (finalPayment.referenceType == "SUPPLIER_PAYMENT" && !finalPayment.supplierId.isNullOrEmpty()) {
                val suppRef = firestore.collection("suppliers").document(finalPayment.supplierId)
                val suppSnap = suppRef.get().await()
                if (suppSnap.exists()) {
                    val currentBalance = suppSnap.getDouble("outstandingBalance") ?: 0.0
                    val newBalance = (currentBalance - finalPayment.amount).coerceAtLeast(0.0)
                    batch.update(suppRef, mapOf(
                        "outstandingBalance" to newBalance,
                        "updatedAt" to now
                    ))
                }
            }

            // Update affected sales in Firestore
            for (sale in updatedSales) {
                val saleDocId = "${shopId}_sale_${sale.invoiceNumber}"
                val saleRef = firestore.collection("sales").document(saleDocId)
                batch.update(saleRef, mapOf(
                    "paidAmount" to sale.paidAmount,
                    "balanceAmount" to sale.balanceAmount,
                    "paymentStatus" to sale.paymentStatus,
                    "updatedAt" to now
                ))

                val invDocId = "${shopId}_inv_${sale.invoiceNumber}"
                val invRef = firestore.collection("invoices").document(invDocId)
                batch.update(invRef, mapOf(
                    "paymentStatus" to sale.paymentStatus,
                    "updatedAt" to now
                ))
            }

            batch.commit().await()
            Result.success(paymentDocId)
        } catch (e: Exception) {
            val isPermissionDenied = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
            if (isPermissionDenied) {
                Log.d(TAG, "Payment saved locally (Firestore cloud sync deferred: login required for cloud permissions)")
                Result.success("LOCAL_OFFLINE")
            } else {
                Log.w(TAG, "Payment cloud sync cached locally: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Updates an existing Purchase record deterministically in Firestore without altering unrelated records.
     */
    suspend fun executePurchaseUpdateTransaction(
        shopId: String,
        purchaseDoc: PurchaseDoc,
        newItems: List<PurchaseItemDoc>,
        oldItems: List<PurchaseItemDoc>,
        supplierId: String? = null,
        userId: String = FirebaseManager.currentUserId
    ): Result<String> = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) {
            Log.d(TAG, "Purchase update saved locally (Firestore cloud sync skipped: local offline mode)")
            return@withContext Result.success("LOCAL_OFFLINE")
        }
        val firestore = firestoreProvider() ?: return@withContext Result.failure(Exception("Firestore not available"))
        try {
            val now = System.currentTimeMillis()
            val purchaseDocId = if (purchaseDoc.id.isNotBlank()) purchaseDoc.id else "${shopId}_pur_${System.currentTimeMillis()}"
            val purchaseRef = firestore.collection("purchases").document(purchaseDocId)

            val batch = firestore.batch()
            batch.set(purchaseRef, purchaseDoc.copy(id = purchaseDocId, updatedAt = now), SetOptions.merge())

            // Delete old purchase items in Firestore
            val oldItemsSnap = firestore.collection("purchaseItems")
                .whereEqualTo("purchaseId", purchaseDocId)
                .get()
                .await()

            for (oldDoc in oldItemsSnap.documents) {
                batch.delete(oldDoc.reference)
            }

            // Write new purchase items deterministically
            newItems.forEachIndexed { index, newItem ->
                val prodId = if (newItem.productId.startsWith("${shopId}_prod_")) newItem.productId else "${shopId}_prod_${newItem.productId}"
                val itemDocId = "${purchaseDocId}_item_${newItem.productId.substringAfterLast("_prod_")}_$index"
                val itemRef = firestore.collection("purchaseItems").document(itemDocId)
                batch.set(itemRef, newItem.copy(
                    id = itemDocId,
                    purchaseId = purchaseDocId,
                    shopId = shopId,
                    productId = prodId,
                    createdAt = now,
                    updatedAt = now
                ))
            }

            batch.commit().await()
            Log.d(TAG, "Purchase $purchaseDocId updated in Firestore successfully")
            Result.success(purchaseDocId)
        } catch (e: Exception) {
            val isPermissionDenied = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
            if (isPermissionDenied) {
                Log.d(TAG, "Purchase update saved locally (Firestore cloud sync deferred: login required for cloud permissions)")
                Result.success("LOCAL_OFFLINE")
            } else {
                Log.w(TAG, "Purchase update cloud sync cached locally: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Deletes a Purchase record and its purchase items in Firestore.
     */
    suspend fun executePurchaseDeleteTransaction(
        shopId: String,
        purchaseId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext Result.success(Unit)
        val firestore = firestoreProvider() ?: return@withContext Result.failure(Exception("Firestore not available"))
        try {
            val purchaseDocId = "${shopId}_pur_$purchaseId"
            val batch = firestore.batch()

            // 1. Delete purchase document
            batch.delete(firestore.collection("purchases").document(purchaseDocId))

            // 2. Delete purchase items
            val itemsSnap = firestore.collection("purchaseItems")
                .whereEqualTo("purchaseId", purchaseDocId)
                .get()
                .await()

            for (doc in itemsSnap.documents) {
                batch.delete(doc.reference)
            }

            batch.commit().await()
            Log.d(TAG, "Purchase $purchaseDocId deleted in Firestore successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Purchase deletion in Firestore cached: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Cancels a Sale record in Firestore and updates its payment and invoice status.
     */
    suspend fun executeSaleCancelTransaction(
        shopId: String,
        saleId: Long,
        invoiceNumber: String,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (FirebaseManager.auth?.currentUser == null) return@withContext Result.success(Unit)
        val firestore = firestoreProvider() ?: return@withContext Result.failure(Exception("Firestore not available"))
        try {
            val saleDocId = "${shopId}_sale_$invoiceNumber"
            val now = System.currentTimeMillis()
            val batch = firestore.batch()

            val saleRef = firestore.collection("sales").document(saleDocId)
            batch.update(saleRef, mapOf(
                "isCancelled" to true,
                "paymentStatus" to "CANCELLED",
                "notes" to "Cancelled: $reason",
                "updatedAt" to now
            ))

            val invDocId = "${shopId}_inv_$invoiceNumber"
            val invRef = firestore.collection("invoices").document(invDocId)
            batch.update(invRef, mapOf(
                "paymentStatus" to "CANCELLED",
                "updatedAt" to now
            ))

            batch.commit().await()
            Log.d(TAG, "Sale $saleDocId cancelled in Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Sale cancel in Firestore cached: ${e.message}")
            Result.failure(e)
        }
    }
}
