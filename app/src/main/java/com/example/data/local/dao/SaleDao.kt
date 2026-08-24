package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.SaleEntity
import com.example.data.local.entities.SaleItemEntity
import kotlinx.coroutines.flow.Flow

data class TopProductSummary(
    val productId: Long,
    val productName: String,
    val totalQuantitySold: Double,
    val totalRevenue: Double,
    val totalProfit: Double
)

data class PaymentMethodSummary(
    val paymentMethod: String,
    val totalAmount: Double,
    val transactionCount: Int
)

data class DailySalesSummary(
    val dateLabel: String,
    val totalSales: Double,
    val totalProfit: Double,
    val orderCount: Int
)

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY saleDate DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE saleDate BETWEEN :startDate AND :endDate ORDER BY saleDate DESC")
    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY saleDate DESC")
    fun getSalesByCustomer(customerId: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSaleById(id: Long): SaleEntity?

    @Query("SELECT * FROM sales WHERE id = :id")
    fun getSaleByIdFlow(id: Long): Flow<SaleEntity?>

    @Query("SELECT * FROM sales WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getSaleByInvoiceNumber(invoiceNumber: String): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getSaleItems(saleId: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItemsSync(saleId: Long): List<SaleItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Query("UPDATE sales SET isCancelled = 1, cancelledAt = :cancelledAt, cancellationReason = :reason, paymentStatus = 'CANCELLED' WHERE id = :saleId")
    suspend fun cancelSale(saleId: Long, cancelledAt: Long = System.currentTimeMillis(), reason: String = "Cancelled by user")

    @Query("SELECT SUM(grandTotal) FROM sales WHERE isCancelled = 0 AND saleDate BETWEEN :startDate AND :endDate")
    fun getTotalSalesByDateRange(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(grossProfit) FROM sales WHERE isCancelled = 0 AND saleDate BETWEEN :startDate AND :endDate")
    fun getTotalGrossProfitByDateRange(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(totalCost) FROM sales WHERE isCancelled = 0 AND saleDate BETWEEN :startDate AND :endDate")
    fun getTotalCostByDateRange(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM sales WHERE isCancelled = 0 AND saleDate BETWEEN :startDate AND :endDate")
    fun getTotalOrdersCountByDateRange(startDate: Long, endDate: Long): Flow<Int>

    @Query("""
        SELECT si.productId, si.productName, SUM(si.quantity) as totalQuantitySold, SUM(si.totalAmount) as totalRevenue, SUM(si.itemProfit) as totalProfit
        FROM sale_items si
        INNER JOIN sales s ON si.saleId = s.id
        WHERE s.isCancelled = 0 AND s.saleDate BETWEEN :startDate AND :endDate
        GROUP BY si.productId, si.productName
        ORDER BY totalRevenue DESC
        LIMIT :limit
    """)
    fun getTopSellingProducts(startDate: Long, endDate: Long, limit: Int = 5): Flow<List<TopProductSummary>>

    @Query("""
        SELECT paymentMethod, SUM(grandTotal) as totalAmount, COUNT(*) as transactionCount
        FROM sales
        WHERE isCancelled = 0 AND saleDate BETWEEN :startDate AND :endDate
        GROUP BY paymentMethod
    """)
    fun getPaymentMethodBreakdown(startDate: Long, endDate: Long): Flow<List<PaymentMethodSummary>>
}
