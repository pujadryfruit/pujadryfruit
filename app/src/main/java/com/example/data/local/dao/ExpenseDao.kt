package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.CreditPaymentEntity
import com.example.data.local.entities.ExpenseEntity
import com.example.data.local.entities.StockAuditEntity
import kotlinx.coroutines.flow.Flow

data class CategoryExpenseSummary(
    val category: String,
    val totalAmount: Double,
    val count: Int
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    suspend fun getAllExpensesSync(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE expenseDate BETWEEN :startDate AND :endDate ORDER BY expenseDate DESC")
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE expenseDate BETWEEN :startDate AND :endDate")
    fun getTotalExpensesByDateRange(startDate: Long, endDate: Long): Flow<Double?>

    @Query("""
        SELECT category, SUM(amount) as totalAmount, COUNT(*) as count
        FROM expenses
        WHERE expenseDate BETWEEN :startDate AND :endDate
        GROUP BY category
        ORDER BY totalAmount DESC
    """)
    fun getExpensesByCategory(startDate: Long, endDate: Long): Flow<List<CategoryExpenseSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)
}

@Dao
interface CreditPaymentDao {
    @Query("SELECT * FROM credit_payments ORDER BY paymentDate DESC")
    fun getAllCreditPayments(): Flow<List<CreditPaymentEntity>>

    @Query("SELECT * FROM credit_payments WHERE customerId = :customerId ORDER BY paymentDate DESC")
    fun getPaymentsByCustomer(customerId: Long): Flow<List<CreditPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: CreditPaymentEntity): Long
}

@Dao
interface StockAuditDao {
    @Query("SELECT * FROM stock_audit ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentStockAudits(limit: Int = 100): Flow<List<StockAuditEntity>>

    @Query("SELECT * FROM stock_audit WHERE productId = :productId ORDER BY timestamp DESC")
    fun getStockAuditsForProduct(productId: Long): Flow<List<StockAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: StockAuditEntity): Long
}
