package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_payments")
data class CreditPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val saleId: Long? = null,
    val invoiceNumber: String = "",
    val paymentDate: Long = System.currentTimeMillis(),
    val amount: Double,
    val paymentMethod: String = "Cash", // Cash, UPI, Bank Transfer
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
