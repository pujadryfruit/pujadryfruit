package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.dao.*
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class SalesRepository(
    private val database: AppDatabase,
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val customerDao: CustomerDao,
    private val stockAuditDao: StockAuditDao,
    private val shopDao: ShopDao
) {
    val allSales: Flow<List<SaleEntity>> = saleDao.getAllSales()

    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>> =
        saleDao.getSalesByDateRange(startDate, endDate)

    fun getSalesByCustomer(customerId: Long): Flow<List<SaleEntity>> =
        saleDao.getSalesByCustomer(customerId)

    fun getSaleByIdFlow(id: Long): Flow<SaleEntity?> = saleDao.getSaleByIdFlow(id)

    suspend fun getSaleById(id: Long): SaleEntity? = saleDao.getSaleById(id)

    fun getSaleItems(saleId: Long): Flow<List<SaleItemEntity>> = saleDao.getSaleItems(saleId)

    suspend fun getSaleItemsSync(saleId: Long): List<SaleItemEntity> = saleDao.getSaleItemsSync(saleId)

    fun getTotalSalesByDateRange(startDate: Long, endDate: Long): Flow<Double?> =
        saleDao.getTotalSalesByDateRange(startDate, endDate)

    fun getTotalGrossProfitByDateRange(startDate: Long, endDate: Long): Flow<Double?> =
        saleDao.getTotalGrossProfitByDateRange(startDate, endDate)

    fun getTotalOrdersCountByDateRange(startDate: Long, endDate: Long): Flow<Int> =
        saleDao.getTotalOrdersCountByDateRange(startDate, endDate)

    fun getTopSellingProducts(startDate: Long, endDate: Long, limit: Int = 5): Flow<List<TopProductSummary>> =
        saleDao.getTopSellingProducts(startDate, endDate, limit)

    fun getPaymentMethodBreakdown(startDate: Long, endDate: Long): Flow<List<PaymentMethodSummary>> =
        saleDao.getPaymentMethodBreakdown(startDate, endDate)

    /**
     * Generates a unique, sequential invoice number formatted as YYYYMMDD001 (e.g., 20260826001, 20260826002).
     * On the next day, resets sequence to 20260827001.
     */
    suspend fun generateNextInvoiceNumber(saleDate: Long = System.currentTimeMillis()): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ENGLISH)
        val datePrefix = sdf.format(java.util.Date(saleDate))
        val latestInvoice = saleDao.getLatestInvoiceNumberForPrefix(datePrefix)

        val nextSeq = if (latestInvoice != null && latestInvoice.startsWith(datePrefix)) {
            val seqPart = latestInvoice.removePrefix(datePrefix).takeWhile { it.isDigit() }
            (seqPart.toIntOrNull() ?: 0) + 1
        } else {
            1
        }
        return String.format(java.util.Locale.ENGLISH, "%s%03d", datePrefix, nextSeq)
    }

    /**
     * Executes atomic sale checkout:
     * 1. Inserts Sale & Sale Items with unique YYYYMMDD001 invoice number
     * 2. Decrements product inventory stock
     * 3. Records stock audit log
     * 4. Updates or Creates customer stats (total spend, orders, credit balance)
     * 5. Advances shop next invoice number
     */
    suspend fun createSale(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        customerAddress: String = ""
    ): SaleEntity {
        var finalResultSale: SaleEntity = sale
        database.withTransaction {
            // Ensure unique sequential invoice number in YYYYMMDD001 format
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ENGLISH)
            val datePrefix = sdf.format(java.util.Date(sale.saleDate))
            val finalInvoiceNumber = if (sale.invoiceNumber.startsWith(datePrefix) && sale.invoiceNumber.length >= 11 && sale.invoiceNumber.all { it.isDigit() }) {
                sale.invoiceNumber
            } else {
                generateNextInvoiceNumber(sale.saleDate)
            }

            // 1. Resolve Customer
            var targetCustomerId = sale.customerId
            if (targetCustomerId == null && sale.customerMobile.isNotBlank() && sale.customerName != "Walk-in Customer") {
                val existing = customerDao.getCustomerByMobile(sale.customerMobile)
                if (existing != null) {
                    targetCustomerId = existing.id
                } else {
                    val newCust = CustomerEntity(
                        name = sale.customerName,
                        mobile = sale.customerMobile,
                        address = customerAddress,
                        totalOrders = 0,
                        totalSpent = 0.0,
                        totalProfitGenerated = 0.0,
                        outstandingBalance = 0.0
                    )
                    targetCustomerId = customerDao.insertCustomer(newCust)
                }
            }

            val finalSale = sale.copy(invoiceNumber = finalInvoiceNumber, customerId = targetCustomerId)
            val createdSaleId = saleDao.insertSale(finalSale)
            finalResultSale = finalSale.copy(id = createdSaleId)

            // 2. Insert items with sale ID
            val mappedItems = items.map { it.copy(saleId = createdSaleId) }
            saleDao.insertSaleItems(mappedItems)

            // 3. Deduct Stock & Audit
            for (item in mappedItems) {
                val product = productDao.getProductById(item.productId)
                val currentStock = product?.currentStock ?: 0.0
                val newStock = currentStock - item.quantity

                productDao.updateStock(item.productId, -item.quantity)
                stockAuditDao.insertAudit(
                    StockAuditEntity(
                        productId = item.productId,
                        productName = item.productName,
                        changeType = "SALE",
                        quantityChanged = -item.quantity,
                        previousStock = currentStock,
                        newStock = newStock,
                        referenceId = finalSale.invoiceNumber,
                        reason = "Sold to ${sale.customerName}"
                    )
                )
            }

            // 4. Update Customer Ledger / Udhaar
            if (targetCustomerId != null && targetCustomerId > 0) {
                customerDao.recordCustomerSale(
                    customerId = targetCustomerId,
                    amount = sale.grandTotal,
                    profit = sale.grossProfit,
                    creditAdded = sale.balanceAmount,
                    purchaseDate = sale.saleDate
                )
            }

            // 5. Increment invoice sequence in shop profile
            shopDao.incrementInvoiceNumber()
        }
        return finalResultSale
    }

    suspend fun deleteSale(saleId: Long) {
        database.withTransaction {
            saleDao.deleteSaleItemsBySaleId(saleId)
            saleDao.deleteSaleById(saleId)
        }
    }

    /**
     * Reverses a sale and restores product stock
     */
    suspend fun cancelSale(saleId: Long, reason: String) {
        database.withTransaction {
            val sale = saleDao.getSaleById(saleId) ?: return@withTransaction
            if (sale.isCancelled) return@withTransaction

            val items = saleDao.getSaleItemsSync(saleId)
            // Restore inventory
            for (item in items) {
                val product = productDao.getProductById(item.productId)
                val currentStock = product?.currentStock ?: 0.0
                val restoredStock = currentStock + item.quantity

                productDao.updateStock(item.productId, item.quantity)
                stockAuditDao.insertAudit(
                    StockAuditEntity(
                        productId = item.productId,
                        productName = item.productName,
                        changeType = "CANCELLATION",
                        quantityChanged = item.quantity,
                        previousStock = currentStock,
                        newStock = restoredStock,
                        referenceId = sale.invoiceNumber,
                        reason = "Sale Cancelled: $reason"
                    )
                )
            }

            // Reverse customer credit if balance was outstanding
            if (sale.customerId != null && sale.balanceAmount > 0) {
                customerDao.reduceCustomerCredit(sale.customerId, sale.balanceAmount)
            }

            saleDao.cancelSale(saleId, System.currentTimeMillis(), reason)
        }
    }
}
