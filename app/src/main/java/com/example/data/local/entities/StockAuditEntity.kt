package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_audit")
data class StockAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val changeType: String, // SALE, PURCHASE, ADJUSTMENT, RETURN, CANCELLATION
    val quantityChanged: Double, // positive or negative
    val previousStock: Double,
    val newStock: Double,
    val referenceId: String = "", // Sale invoice / Purchase ID
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
