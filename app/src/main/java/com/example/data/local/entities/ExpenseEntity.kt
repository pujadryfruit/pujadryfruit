package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseDate: Long = System.currentTimeMillis(),
    val category: String, // Shop Rent, Electricity, Salary, Transport, Packaging, Marketing, Maintenance, Tea/Refreshment, Other
    val description: String,
    val amount: Double,
    val paymentMethod: String = "Cash", // Cash, UPI, Bank Transfer
    val notes: String = "",
    val receiptRef: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
