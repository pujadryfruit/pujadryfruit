package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.PurchaseEntity
import com.example.data.local.entities.PurchaseItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE purchaseDate BETWEEN :startDate AND :endDate ORDER BY purchaseDate DESC")
    fun getPurchasesByDateRange(startDate: Long, endDate: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchaseById(id: Long): PurchaseEntity?

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    fun getPurchaseItems(purchaseId: Long): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItemsSync(purchaseId: Long): List<PurchaseItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)

    @Query("SELECT SUM(totalAmount) FROM purchases WHERE purchaseDate BETWEEN :startDate AND :endDate")
    fun getTotalPurchaseAmountByDateRange(startDate: Long, endDate: Long): Flow<Double?>
}
