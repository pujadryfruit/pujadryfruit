package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY name ASC")
    fun getAllActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY name ASC")
    suspend fun getAllActiveProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isArchived = 0 AND (name LIKE '%' || :query || '%' OR hindiName LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%') ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 0 AND currentStock <= minStockLevel ORDER BY currentStock ASC")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductByIdFlow(id: Long): Flow<ProductEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET currentStock = currentStock + :quantity, updatedAt = :timestamp WHERE id = :productId")
    suspend fun updateStock(productId: Long, quantity: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET isArchived = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun archiveProduct(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM products WHERE isArchived = 0")
    fun getTotalProductsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE isArchived = 0 AND currentStock <= minStockLevel")
    fun getLowStockCount(): Flow<Int>

    @Query("SELECT SUM(currentStock * purchasePrice) FROM products WHERE isArchived = 0")
    fun getTotalStockValue(): Flow<Double?>
}
