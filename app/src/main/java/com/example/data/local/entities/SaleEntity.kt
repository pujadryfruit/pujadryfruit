package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String, // e.g. INV-2026-1001
    val customerId: Long? = null,
    val customerName: String = "Walk-in Customer",
    val customerMobile: String = "",
    val saleDate: Long = System.currentTimeMillis(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val totalCost: Double = 0.0,
    val grossProfit: Double = 0.0,
    val profitMarginPercent: Double = 0.0,
    val paymentMethod: String = "Cash", // Cash, UPI, Card, Bank Transfer, Credit/Udhaar
    val paymentStatus: String = "PAID", // PAID, UNPAID, PARTIAL, CANCELLED
    val paidAmount: Double = 0.0,
    val balanceAmount: Double = 0.0, // For Udhaar/Credit
    val dueDate: Long? = null,
    val notes: String = "",
    val isCancelled: Boolean = false,
    val cancelledAt: Long? = null,
    val cancellationReason: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sale_items")
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val unit: String,
    val quantity: Double,
    val sellingPrice: Double, // Price per unit
    val costPrice: Double,    // Cost per unit at time of sale
    val discount: Double = 0.0,
    val totalAmount: Double,
    val itemProfit: Double
)
