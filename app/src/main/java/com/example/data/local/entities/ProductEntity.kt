package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sku: String = "",
    val name: String,
    val hindiName: String = "",
    val category: String = "Nuts", // Nuts, Dry Fruits, Seeds, Berries, Spices, Gift Boxes
    val unit: String = "Kilogram", // Gram, Kilogram, Piece, Packet, Box
    val purchasePrice: Double = 0.0, // per unit
    val sellingPrice: Double = 0.0,  // per unit
    val currentStock: Double = 0.0,  // in specified unit
    val minStockLevel: Double = 5.0, // low stock threshold
    val supplierName: String = "",
    val description: String = "",
    val colorHex: String = "#8C4A00",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
