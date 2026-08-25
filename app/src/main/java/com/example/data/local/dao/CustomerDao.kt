package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    suspend fun getAllCustomersSync(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR mobile LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE outstandingBalance > 0 ORDER BY outstandingBalance DESC")
    fun getCustomersWithOutstandingCredit(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id")
    fun getCustomerByIdFlow(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE mobile = :mobile LIMIT 1")
    suspend fun getCustomerByMobile(mobile: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getCustomerByName(name: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: Long)

    @Query("DELETE FROM customers WHERE mobile = :mobile")
    suspend fun deleteCustomerByMobile(mobile: String)

    @Query("DELETE FROM customers WHERE id NOT IN (:validIds)")
    suspend fun deleteCustomersNotIn(validIds: List<Long>)

    @Query("DELETE FROM customers")
    suspend fun clearAllCustomers()

    @Query("UPDATE customers SET totalOrders = totalOrders + 1, totalSpent = totalSpent + :amount, totalProfitGenerated = totalProfitGenerated + :profit, outstandingBalance = outstandingBalance + :creditAdded, lastPurchaseDate = :purchaseDate, updatedAt = :timestamp WHERE id = :customerId")
    suspend fun recordCustomerSale(customerId: Long, amount: Double, profit: Double, creditAdded: Double, purchaseDate: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET outstandingBalance = outstandingBalance - :amount, updatedAt = :timestamp WHERE id = :customerId")
    suspend fun reduceCustomerCredit(customerId: Long, amount: Double, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM customers")
    fun getTotalCustomersCount(): Flow<Int>

    @Query("SELECT SUM(outstandingBalance) FROM customers WHERE outstandingBalance > 0")
    fun getTotalOutstandingCredit(): Flow<Double?>
}
