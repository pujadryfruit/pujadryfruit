package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String,
    val address: String = "",
    val totalOrders: Int = 0,
    val totalSpent: Double = 0.0,
    val totalProfitGenerated: Double = 0.0,
    val outstandingBalance: Double = 0.0, // Credit/Udhaar balance
    val lastPurchaseDate: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
