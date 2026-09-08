package com.example.data.firebase.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Scalable Firestore Models for Puja Dryfruits Management System.
 * Every record includes unique document IDs, shop/user IDs, and createdAt/updatedAt timestamps.
 */

@IgnoreExtraProperties
data class UserDoc(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "OWNER", // OWNER, MANAGER, CASHIER
    val shopId: String = "puja_dryfruits_main",
    val photoUrl: String = "",
    val phone: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class ShopDoc(
    @DocumentId val id: String = "puja_dryfruits_main",
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
    val taxRatePercent: Double = 0.0,
    val isTaxEnabled: Boolean = false,
    val thankYouMessage: String = "Thank you for shopping at Puja Dry Fruits! Stay Healthy & Happy!",
    val logoUrl: String = "",
    val bannerUrl: String = "",
    val ownerUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class ProductDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val sku: String = "",
    val name: String = "",
    val hindiName: String = "",
    val category: String = "Nuts",
    val unit: String = "Kilogram",
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val currentStock: Double = 0.0,
    val minStockLevel: Double = 5.0,
    val supplierName: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val colorHex: String = "#6750A4",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class CategoryDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val name: String = "",
    val hindiName: String = "",
    val iconName: String = "",
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class SupplierDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val name: String = "",
    val contactPerson: String = "",
    val mobile: String = "",
    val email: String = "",
    val address: String = "",
    val gstin: String = "",
    val totalPurchases: Double = 0.0,
    val outstandingBalance: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class CustomerDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val name: String = "",
    val mobile: String = "",
    val address: String = "",
    val totalOrders: Int = 0,
    val totalSpent: Double = 0.0,
    val totalProfitGenerated: Double = 0.0,
    val outstandingBalance: Double = 0.0,
    val lastPurchaseDate: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class SaleDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val invoiceNumber: String = "",
    val customerId: String? = null,
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
    val paymentMethod: String = "Cash",
    val paymentStatus: String = "PAID", // PAID, UNPAID, PARTIAL, CANCELLED
    val paidAmount: Double = 0.0,
    val balanceAmount: Double = 0.0,
    val dueDate: Long? = null,
    val notes: String = "",
    val isCancelled: Boolean = false,
    val cancelledAt: Long? = null,
    val cancellationReason: String = "",
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val items: List<SaleItemDoc> = emptyList()
)

@IgnoreExtraProperties
data class SaleItemDoc(
    @DocumentId val id: String = "",
    val saleId: String = "",
    val shopId: String = "puja_dryfruits_main",
    val productId: String = "",
    val productName: String = "",
    val unit: String = "Kilogram",
    val quantity: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val discount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val itemProfit: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class PurchaseDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val purchaseDate: Long = System.currentTimeMillis(),
    val supplierId: String? = null,
    val supplierName: String = "",
    val supplierPhone: String = "",
    val invoiceReference: String = "",
    val totalAmount: Double = 0.0,
    val paymentStatus: String = "PAID",
    val paymentMethod: String = "Cash",
    val paidAmount: Double = 0.0,
    val balanceAmount: Double = 0.0,
    val notes: String = "",
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class PurchaseItemDoc(
    @DocumentId val id: String = "",
    val purchaseId: String = "",
    val shopId: String = "puja_dryfruits_main",
    val productId: String = "",
    val productName: String = "",
    val unit: String = "Kilogram",
    val quantity: Double = 0.0,
    val purchasePrice: Double = 0.0,
    val totalAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class PaymentDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val referenceType: String = "CUSTOMER_UDHAAR", // CUSTOMER_UDHAAR, SUPPLIER_PAYMENT, EXPENSE_PAYMENT
    val referenceId: String = "",
    val customerId: String? = null,
    val supplierId: String? = null,
    val amount: Double = 0.0,
    val paymentMethod: String = "Cash",
    val transactionRef: String = "",
    val notes: String = "",
    val paymentDate: Long = System.currentTimeMillis(),
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class ExpenseDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val expenseDate: Long = System.currentTimeMillis(),
    val category: String = "Other",
    val description: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = "Cash",
    val notes: String = "",
    val receiptRef: String = "",
    val receiptImageUrl: String = "",
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class StockTransactionDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val productId: String = "",
    val productName: String = "",
    val transactionType: String = "SALE", // SALE, PURCHASE, ADJUSTMENT, RETURN, DAMAGE, INITIAL_STOCK
    val quantityChange: Double = 0.0, // negative for sale/reduction, positive for purchase/addition
    val previousStock: Double = 0.0,
    val newStock: Double = 0.0,
    val unit: String = "Kilogram",
    val unitPrice: Double = 0.0,
    val referenceId: String = "", // saleId, purchaseId, auditId
    val referenceType: String = "",
    val reason: String = "",
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class InvoiceDoc(
    @DocumentId val id: String = "",
    val shopId: String = "puja_dryfruits_main",
    val saleId: String = "",
    val invoiceNumber: String = "",
    val customerName: String = "",
    val customerMobile: String = "",
    val date: Long = System.currentTimeMillis(),
    val grandTotal: Double = 0.0,
    val paymentStatus: String = "PAID",
    val pdfUrl: String = "",
    val shareUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
