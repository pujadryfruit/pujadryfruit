package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    suspend fun getAllSuppliersSync(): List<SupplierEntity>

    @Query("SELECT * FROM suppliers WHERE name LIKE '%' || :query || '%' OR mobile LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchSuppliers(query: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getSupplierByName(name: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE mobile = :mobile LIMIT 1")
    suspend fun getSupplierByMobile(mobile: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSuppliers(suppliers: List<SupplierEntity>)

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Delete
    suspend fun deleteSupplier(supplier: SupplierEntity)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteSupplierById(id: Long)

    @Query("DELETE FROM suppliers WHERE id NOT IN (:validIds)")
    suspend fun deleteSuppliersNotIn(validIds: List<Long>)

    @Query("DELETE FROM suppliers")
    suspend fun clearAllSuppliers()

    @Query("UPDATE suppliers SET totalPurchases = totalPurchases + :amount, updatedAt = :timestamp WHERE id = :id")
    suspend fun recordPurchase(id: Long, amount: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE suppliers SET totalPurchases = totalPurchases + :amount, outstandingBalance = MAX(0.0, outstandingBalance + :balanceDelta), updatedAt = :timestamp WHERE id = :id")
    suspend fun recordPurchaseWithBalance(id: Long, amount: Double, balanceDelta: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE suppliers SET outstandingBalance = MAX(0.0, outstandingBalance + :balanceDelta), updatedAt = :timestamp WHERE id = :id")
    suspend fun updateOutstandingBalance(id: Long, balanceDelta: Double, timestamp: Long = System.currentTimeMillis())
}
