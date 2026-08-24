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
    private val stockAuditDao: StockAuditDao
) {
    val allPurchases: Flow<List<PurchaseEntity>> = purchaseDao.getAllPurchases()

    fun getPurchasesByDateRange(startDate: Long, endDate: Long): Flow<List<PurchaseEntity>> =
        purchaseDao.getPurchasesByDateRange(startDate, endDate)

    fun getPurchaseItems(purchaseId: Long): Flow<List<PurchaseItemEntity>> =
        purchaseDao.getPurchaseItems(purchaseId)

    suspend fun getPurchaseItemsSync(purchaseId: Long): List<PurchaseItemEntity> =
        purchaseDao.getPurchaseItemsSync(purchaseId)

    suspend fun createPurchase(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>
    ): Long {
        var createdPurchaseId: Long = 0
        database.withTransaction {
            createdPurchaseId = purchaseDao.insertPurchase(purchase)
            val mappedItems = items.map { it.copy(purchaseId = createdPurchaseId) }
            purchaseDao.insertPurchaseItems(mappedItems)

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
}

class CustomerRepository(
    private val customerDao: CustomerDao,
    private val creditPaymentDao: CreditPaymentDao
) {
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val customersWithCredit: Flow<List<CustomerEntity>> = customerDao.getCustomersWithOutstandingCredit()
    val totalCustomersCount: Flow<Int> = customerDao.getTotalCustomersCount()
    val totalOutstandingCredit: Flow<Double?> = customerDao.getTotalOutstandingCredit()
    val allCreditPayments: Flow<List<CreditPaymentEntity>> = creditPaymentDao.getAllCreditPayments()

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = customerDao.searchCustomers(query)

    fun getCustomerByIdFlow(id: Long): Flow<CustomerEntity?> = customerDao.getCustomerByIdFlow(id)

    suspend fun getCustomerById(id: Long): CustomerEntity? = customerDao.getCustomerById(id)

    suspend fun addCustomer(customer: CustomerEntity): Long = customerDao.insertCustomer(customer)

    suspend fun updateCustomer(customer: CustomerEntity) = customerDao.updateCustomer(customer)

    fun getPaymentsByCustomer(customerId: Long): Flow<List<CreditPaymentEntity>> =
        creditPaymentDao.getPaymentsByCustomer(customerId)

    suspend fun recordCreditRepayment(
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String
    ) {
        val customer = customerDao.getCustomerById(customerId) ?: return
        val payment = CreditPaymentEntity(
            customerId = customerId,
            customerName = customer.name,
            amount = amount,
            paymentMethod = paymentMethod,
            notes = notes
        )
        creditPaymentDao.insertPayment(payment)
        customerDao.reduceCustomerCredit(customerId, amount)
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
