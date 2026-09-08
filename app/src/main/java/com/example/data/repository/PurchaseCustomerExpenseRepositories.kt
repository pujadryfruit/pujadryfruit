package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.dao.*
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class PurchaseRepository(
    private val database: AppDatabase,
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao,
    private val supplierDao: SupplierDao,
    private val stockAuditDao: StockAuditDao
) {
    val allPurchases: Flow<List<PurchaseEntity>> = purchaseDao.getAllPurchases()

    fun getPurchasesByDateRange(startDate: Long, endDate: Long): Flow<List<PurchaseEntity>> =
        purchaseDao.getPurchasesByDateRange(startDate, endDate)

    fun getTotalPurchasesByDateRange(startDate: Long, endDate: Long): Flow<Double?> =
        purchaseDao.getTotalPurchaseAmountByDateRange(startDate, endDate)

    fun getPurchaseItems(purchaseId: Long): Flow<List<PurchaseItemEntity>> =
        purchaseDao.getPurchaseItems(purchaseId)

    suspend fun getPurchaseItemsSync(purchaseId: Long): List<PurchaseItemEntity> =
        purchaseDao.getPurchaseItemsSync(purchaseId)

    suspend fun getPurchaseById(purchaseId: Long): PurchaseEntity? =
        purchaseDao.getPurchaseById(purchaseId)

    suspend fun createPurchase(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>
    ): Long {
        var createdPurchaseId: Long = 0
        database.withTransaction {
            createdPurchaseId = purchaseDao.insertPurchase(purchase)
            val mappedItems = items.map { it.copy(purchaseId = createdPurchaseId) }
            purchaseDao.insertPurchaseItems(mappedItems)

            // Update Supplier record and outstanding balance for Udhaar
            if (purchase.supplierName.isNotBlank()) {
                val isUnpaid = purchase.paymentStatus.equals("UNPAID", true) || purchase.paymentMethod.contains("Credit", true)
                val balanceDelta = if (isUnpaid) purchase.totalAmount else 0.0
                val existingSupp = supplierDao.getSupplierByName(purchase.supplierName.trim())
                if (existingSupp != null) {
                    supplierDao.recordPurchaseWithBalance(existingSupp.id, purchase.totalAmount, balanceDelta)
                } else {
                    val newSupp = SupplierEntity(
                        name = purchase.supplierName.trim(),
                        mobile = purchase.supplierPhone.trim(),
                        totalPurchases = purchase.totalAmount,
                        outstandingBalance = balanceDelta,
                        notes = "Auto-created from stock inward"
                    )
                    supplierDao.insertSupplier(newSupp)
                }
            }

            for (item in mappedItems) {
                val product = productDao.getProductById(item.productId)
                val currentStock = product?.currentStock ?: 0.0
                val newStock = currentStock + item.quantity

                productDao.updateStock(item.productId, item.quantity)
                stockAuditDao.insertAudit(
                    StockAuditEntity(
                        productId = item.productId,
                        productName = item.productName,
                        changeType = "PURCHASE",
                        quantityChanged = item.quantity,
                        previousStock = currentStock,
                        newStock = newStock,
                        referenceId = if (purchase.invoiceReference.isNotBlank()) purchase.invoiceReference else "PUR-$createdPurchaseId",
                        reason = "Procured from ${purchase.supplierName}"
                    )
                )
            }
        }
        return createdPurchaseId
    }

    /**
     * Updates an existing Purchase record and adjusts product stock accurately without double-counting.
     * Also updates supplier ledger / outstanding balance if paymentStatus changed between UNPAID/PAID.
     */
    suspend fun updatePurchase(
        purchaseId: Long,
        updatedPurchase: PurchaseEntity,
        newItems: List<PurchaseItemEntity>
    ) {
        database.withTransaction {
            val oldPurchase = purchaseDao.getPurchaseById(purchaseId)
            val oldItems = purchaseDao.getPurchaseItemsSync(purchaseId)

            // Update supplier balance if udhaar changed
            val oldWasUnpaid = oldPurchase?.paymentStatus?.equals("UNPAID", true) == true || oldPurchase?.paymentMethod?.contains("Credit", true) == true
            val oldDue = if (oldWasUnpaid) (oldPurchase?.totalAmount ?: 0.0) else 0.0

            val newIsUnpaid = updatedPurchase.paymentStatus.equals("UNPAID", true) || updatedPurchase.paymentMethod.contains("Credit", true)
            val newDue = if (newIsUnpaid) updatedPurchase.totalAmount else 0.0

            val balanceDelta = newDue - oldDue
            val purchaseTotalDelta = updatedPurchase.totalAmount - (oldPurchase?.totalAmount ?: 0.0)

            if (updatedPurchase.supplierName.isNotBlank()) {
                val existingSupp = supplierDao.getSupplierByName(updatedPurchase.supplierName.trim())
                if (existingSupp != null) {
                    supplierDao.recordPurchaseWithBalance(existingSupp.id, purchaseTotalDelta, balanceDelta)
                }
            }

            // 1. Revert previous stock increments
            for (oldItem in oldItems) {
                val product = productDao.getProductById(oldItem.productId)
                val currentStock = product?.currentStock ?: 0.0
                val revertedStock = currentStock - oldItem.quantity
                productDao.updateStock(oldItem.productId, -oldItem.quantity)
                stockAuditDao.insertAudit(
                    StockAuditEntity(
                        productId = oldItem.productId,
                        productName = oldItem.productName,
                        changeType = "PURCHASE_EDIT_REVERT",
                        quantityChanged = -oldItem.quantity,
                        previousStock = currentStock,
                        newStock = revertedStock,
                        referenceId = if (updatedPurchase.invoiceReference.isNotBlank()) updatedPurchase.invoiceReference else "PUR-$purchaseId",
                        reason = "Stock Inward Edited: Reverted old stock"
                    )
                )
            }

            // 2. Delete old items and insert updated items
            purchaseDao.deletePurchaseItemsByPurchaseId(purchaseId)
            val mappedNewItems = newItems.map { it.copy(purchaseId = purchaseId) }
            purchaseDao.insertPurchaseItems(mappedNewItems)

            // 3. Apply new stock increments
            for (newItem in mappedNewItems) {
                val product = productDao.getProductById(newItem.productId)
                val currentStock = product?.currentStock ?: 0.0
                val appliedStock = currentStock + newItem.quantity
                productDao.updateStock(newItem.productId, newItem.quantity)
                stockAuditDao.insertAudit(
                    StockAuditEntity(
                        productId = newItem.productId,
                        productName = newItem.productName,
                        changeType = "PURCHASE_EDIT_APPLY",
                        quantityChanged = newItem.quantity,
                        previousStock = currentStock,
                        newStock = appliedStock,
                        referenceId = if (updatedPurchase.invoiceReference.isNotBlank()) updatedPurchase.invoiceReference else "PUR-$purchaseId",
                        reason = "Stock Inward Edited: Applied updated stock from ${updatedPurchase.supplierName}"
                    )
                )
            }

            // 4. Update the Purchase record
            purchaseDao.updatePurchase(updatedPurchase.copy(id = purchaseId))
        }
    }

    /**
     * Deletes a Purchase record and reverts the stock incremented by this purchase.
     */
    suspend fun deletePurchase(purchaseId: Long) {
        database.withTransaction {
            val items = purchaseDao.getPurchaseItemsSync(purchaseId)
            val purchase = purchaseDao.getPurchaseById(purchaseId)

            if (purchase != null && purchase.supplierName.isNotBlank()) {
                val isUnpaid = purchase.paymentStatus.equals("UNPAID", true) || purchase.paymentMethod.contains("Credit", true)
                val balanceDelta = if (isUnpaid) -purchase.totalAmount else 0.0
                val existingSupp = supplierDao.getSupplierByName(purchase.supplierName.trim())
                if (existingSupp != null) {
                    supplierDao.recordPurchaseWithBalance(existingSupp.id, -purchase.totalAmount, balanceDelta)
                }
            }

            for (item in items) {
                val product = productDao.getProductById(item.productId)
                val currentStock = product?.currentStock ?: 0.0
                val revertedStock = (currentStock - item.quantity).coerceAtLeast(0.0)
                productDao.updateStock(item.productId, -item.quantity)
                stockAuditDao.insertAudit(
                    StockAuditEntity(
                        productId = item.productId,
                        productName = item.productName,
                        changeType = "PURCHASE_DELETED",
                        quantityChanged = -item.quantity,
                        previousStock = currentStock,
                        newStock = revertedStock,
                        referenceId = if (purchase != null && purchase.invoiceReference.isNotBlank()) purchase.invoiceReference else "PUR-$purchaseId",
                        reason = "Purchase Deleted: Reverted stock"
                    )
                )
            }

            purchaseDao.deletePurchaseItemsByPurchaseId(purchaseId)
            purchaseDao.deletePurchaseById(purchaseId)
        }
    }
}

class SupplierRepository(
    private val supplierDao: SupplierDao
) {
    val allSuppliers: Flow<List<SupplierEntity>> = supplierDao.getAllSuppliers()

    fun searchSuppliers(query: String): Flow<List<SupplierEntity>> = supplierDao.searchSuppliers(query)

    suspend fun getAllSuppliersSync(): List<SupplierEntity> = supplierDao.getAllSuppliersSync()

    suspend fun getSupplierById(id: Long): SupplierEntity? = supplierDao.getSupplierById(id)

    suspend fun getSupplierByName(name: String): SupplierEntity? = supplierDao.getSupplierByName(name.trim())

    suspend fun getSupplierByMobile(mobile: String): SupplierEntity? = supplierDao.getSupplierByMobile(mobile.trim())

    suspend fun addSupplier(supplier: SupplierEntity): Long {
        val existing = if (supplier.name.isNotBlank()) supplierDao.getSupplierByName(supplier.name.trim()) else null
        if (existing != null) {
            val updated = existing.copy(
                mobile = if (supplier.mobile.isNotBlank()) supplier.mobile.trim() else existing.mobile,
                address = if (supplier.address.isNotBlank()) supplier.address.trim() else existing.address,
                gstin = if (supplier.gstin.isNotBlank()) supplier.gstin.trim() else existing.gstin,
                contactPerson = if (supplier.contactPerson.isNotBlank()) supplier.contactPerson.trim() else existing.contactPerson,
                updatedAt = System.currentTimeMillis()
            )
            supplierDao.updateSupplier(updated)
            return existing.id
        }
        return supplierDao.insertSupplier(supplier)
    }

    suspend fun updateSupplier(supplier: SupplierEntity) = supplierDao.updateSupplier(supplier)

    suspend fun updateSupplierBalance(supplierId: Long, balanceDelta: Double) =
        supplierDao.updateOutstandingBalance(supplierId, balanceDelta)

    suspend fun deleteSupplier(id: Long) = supplierDao.deleteSupplierById(id)
}

class CustomerRepository(
    private val customerDao: CustomerDao,
    private val creditPaymentDao: CreditPaymentDao,
    private val saleDao: SaleDao
) {
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val customersWithCredit: Flow<List<CustomerEntity>> = customerDao.getCustomersWithOutstandingCredit()
    val totalCustomersCount: Flow<Int> = customerDao.getTotalCustomersCount()
    val totalOutstandingCredit: Flow<Double?> = customerDao.getTotalOutstandingCredit()
    val allCreditPayments: Flow<List<CreditPaymentEntity>> = creditPaymentDao.getAllCreditPayments()

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = customerDao.searchCustomers(query)

    fun getCustomerByIdFlow(id: Long): Flow<CustomerEntity?> = customerDao.getCustomerByIdFlow(id)

    suspend fun getCustomerById(id: Long): CustomerEntity? = customerDao.getCustomerById(id)

    suspend fun getCustomerByMobile(mobile: String): CustomerEntity? = if (mobile.isNotBlank()) customerDao.getCustomerByMobile(mobile.trim()) else null

    suspend fun getCustomerByName(name: String): CustomerEntity? = if (name.isNotBlank()) customerDao.getCustomerByName(name.trim()) else null

    suspend fun addCustomer(customer: CustomerEntity): Long = customerDao.insertCustomer(customer)

    suspend fun updateCustomer(customer: CustomerEntity) = customerDao.updateCustomer(customer)

    suspend fun deleteCustomer(id: Long) = customerDao.deleteCustomerById(id)

    fun getPaymentsByCustomer(customerId: Long): Flow<List<CreditPaymentEntity>> =
        creditPaymentDao.getPaymentsByCustomer(customerId)

    fun getCreditPaymentsByDateRange(startDate: Long, endDate: Long): Flow<List<CreditPaymentEntity>> =
        creditPaymentDao.getCreditPaymentsByDateRange(startDate, endDate)

    fun getTotalCreditCollectedByDateRange(startDate: Long, endDate: Long): Flow<Double?> =
        creditPaymentDao.getTotalCreditCollectedByDateRange(startDate, endDate)

    suspend fun getPaymentsByCustomerSync(customerId: Long): List<CreditPaymentEntity> =
        creditPaymentDao.getPaymentsByCustomerSync(customerId)

    suspend fun getAllCreditPaymentsSync(): List<CreditPaymentEntity> =
        creditPaymentDao.getAllCreditPaymentsSync()

    /**
     * Records Udhaar repayment:
     * 1. Inserts Payment record in credit_payments table
     * 2. Reduces customer's outstanding balance
     * 3. Allocates payment towards customer's oldest unpaid/partial sales, updating status to PAID or PARTIAL
     */
    suspend fun recordCreditRepayment(
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String
    ): Pair<Long, List<SaleEntity>> {
        val customer = customerDao.getCustomerById(customerId) ?: return Pair(0L, emptyList())
        val payment = CreditPaymentEntity(
            customerId = customerId,
            customerName = customer.name,
            amount = amount,
            paymentMethod = paymentMethod,
            notes = notes,
            paymentDate = System.currentTimeMillis()
        )
        val paymentId = creditPaymentDao.insertPayment(payment)
        customerDao.reduceCustomerCredit(customerId, amount)

        // Allocate repayment across unpaid / partial sales
        val updatedSales = mutableListOf<SaleEntity>()
        var remainingPayment = amount
        val unpaidSales = saleDao.getUnpaidSalesForCustomer(customerId)
        for (sale in unpaidSales) {
            if (remainingPayment <= 0.0) break
            val paymentForSale = minOf(remainingPayment, sale.balanceAmount)
            val newPaid = sale.paidAmount + paymentForSale
            val newBalance = (sale.balanceAmount - paymentForSale).coerceAtLeast(0.0)
            val newStatus = if (newBalance <= 0.0) "PAID" else "PARTIAL"
            val updated = sale.copy(
                paidAmount = newPaid,
                balanceAmount = newBalance,
                paymentStatus = newStatus
            )
            saleDao.updateSale(updated)
            updatedSales.add(updated)
            remainingPayment -= paymentForSale
        }

        return Pair(paymentId, updatedSales)
    }
}

class ExpenseRepository(
    private val expenseDao: ExpenseDao
) {
    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByDateRange(startDate, endDate)

    fun getTotalExpensesByDateRange(startDate: Long, endDate: Long): Flow<Double?> =
        expenseDao.getTotalExpensesByDateRange(startDate, endDate)

    fun getExpensesByCategory(startDate: Long, endDate: Long): Flow<List<CategoryExpenseSummary>> =
        expenseDao.getExpensesByCategory(startDate, endDate)

    suspend fun addExpense(expense: ExpenseEntity): Long = expenseDao.insertExpense(expense)

    suspend fun updateExpense(expense: ExpenseEntity) = expenseDao.updateExpense(expense)

    suspend fun deleteExpense(id: Long) = expenseDao.deleteExpenseById(id)
}
