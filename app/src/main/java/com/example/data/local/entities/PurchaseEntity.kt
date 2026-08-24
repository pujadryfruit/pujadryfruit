package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseDate: Long = System.currentTimeMillis(),
    val supplierName: String,
    val supplierPhone: String = "",
    val invoiceReference: String = "",
    val totalAmount: Double = 0.0,
    val paymentStatus: String = "PAID", // PAID, PENDING, PARTIAL
    val paymentMethod: String = "Cash", // Cash, UPI, Bank Transfer, Cheque, Credit
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchase_items")
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productName: String,
    val unit: String,
    val quantity: Double,
    val purchasePrice: Double,
    val totalAmount: Double
)
