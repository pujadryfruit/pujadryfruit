package com.example.data.repository

import com.example.data.local.dao.*
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class ShopRepository(private val shopDao: ShopDao) {
    val shopProfile: Flow<ShopProfile?> = shopDao.getShopProfile()

    suspend fun getShopProfile(): ShopProfile? = shopDao.getShopProfileSync()

    suspend fun saveShopProfile(profile: ShopProfile) = shopDao.insertOrUpdateProfile(profile)
}

class ProductRepository(
    private val productDao: ProductDao,
    private val stockAuditDao: StockAuditDao
) {
    val allActiveProducts: Flow<List<ProductEntity>> = productDao.getAllActiveProducts()
    val lowStockProducts: Flow<List<ProductEntity>> = productDao.getLowStockProducts()
    val totalProductsCount: Flow<Int> = productDao.getTotalProductsCount()
    val lowStockCount: Flow<Int> = productDao.getLowStockCount()
    val totalStockValue: Flow<Double?> = productDao.getTotalStockValue()

    fun searchProducts(query: String): Flow<List<ProductEntity>> = productDao.searchProducts(query)

    fun getProductByIdFlow(id: Long): Flow<ProductEntity?> = productDao.getProductByIdFlow(id)

    suspend fun getProductById(id: Long): ProductEntity? = productDao.getProductById(id)

    suspend fun getProductBySku(sku: String): ProductEntity? = if (sku.isNotBlank()) productDao.getProductBySku(sku.trim()) else null

    suspend fun getProductByName(name: String): ProductEntity? = if (name.isNotBlank()) productDao.getProductByName(name.trim()) else null

    suspend fun addProduct(product: ProductEntity): Long {
        val id = productDao.insertProduct(product)
        if (product.currentStock > 0) {
            stockAuditDao.insertAudit(
                StockAuditEntity(
                    productId = id,
                    productName = product.name,
                    changeType = "ADJUSTMENT",
                    quantityChanged = product.currentStock,
                    previousStock = 0.0,
                    newStock = product.currentStock,
                    reason = "Initial product entry"
                )
            )
        }
        return id
    }

    suspend fun updateProduct(product: ProductEntity) = productDao.updateProduct(product)

    suspend fun adjustStock(productId: Long, quantityDifference: Double, reason: String) {
        val current = productDao.getProductById(productId) ?: return
        val newStock = current.currentStock + quantityDifference
        productDao.updateStock(productId, quantityDifference)
        stockAuditDao.insertAudit(
            StockAuditEntity(
                productId = productId,
                productName = current.name,
                changeType = "ADJUSTMENT",
                quantityChanged = quantityDifference,
                previousStock = current.currentStock,
                newStock = newStock,
                reason = reason
            )
        )
    }

    fun getStockAuditsByDateRange(startDate: Long, endDate: Long): Flow<List<StockAuditEntity>> =
        stockAuditDao.getStockAuditsByDateRange(startDate, endDate)

    suspend fun deleteProduct(id: Long) {
        productDao.archiveProduct(id)
        productDao.deleteProductById(id)
    }
}
