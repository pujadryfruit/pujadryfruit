package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.ShopProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop_profile WHERE id = 1")
    fun getShopProfile(): Flow<ShopProfile?>

    @Query("SELECT * FROM shop_profile WHERE id = 1")
    suspend fun getShopProfileSync(): ShopProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: ShopProfile)

    @Query("UPDATE shop_profile SET nextInvoiceNumber = nextInvoiceNumber + 1 WHERE id = 1")
    suspend fun incrementInvoiceNumber()
}
