package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shop_profile")
data class ShopProfile(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "Puja Dry Fruits",
    val shopHindiName: String = "पूजा ड्राई फ्रूट्स",
    val tagline: String = "Premium Dry Fruits, Nuts & Healthy Bites",
    val ownerName: String = "Puja Kumari",
    val phone: String = "+91 98765 43210",
    val email: String = "pujadryfruit@gmail.com",
    val address: String = "Shop No. 12, Main Dry Fruit Market, Khari Baoli Road, City Center",
    val gstin: String = "07AAAAA0000A1Z5",
    val upiId: String = "pujadryfruits@upi",
    val invoicePrefix: String = "PDF",
    val nextInvoiceNumber: Int = 1001,
    val taxRatePercent: Double = 0.0, // Optional GST/Tax
    val isTaxEnabled: Boolean = false,
    val thankYouMessage: String = "Thank you for shopping at Puja Dry Fruits! Stay Healthy & Happy!",
    val securityPin: String = "1234",
    val isPinSecurityEnabled: Boolean = false,
    val negativeStockAllowed: Boolean = false,
    val currencySymbol: String = "₹",
    val updatedAt: Long = System.currentTimeMillis()
)
