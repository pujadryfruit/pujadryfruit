package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ShopProfile::class,
        ProductEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        CustomerEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        CreditPaymentEntity::class,
        ExpenseEntity::class,
        StockAuditEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shopDao(): ShopDao
    abstract fun productDao(): ProductDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun creditPaymentDao(): CreditPaymentDao
    abstract fun stockAuditDao(): StockAuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "puja_dry_fruits_db"
                )
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val database = getDatabase(context)
                    seedDatabase(database)
                }
            }
        }

        suspend fun seedDatabase(database: AppDatabase) {
            val shopDao = database.shopDao()
            val productDao = database.productDao()
            val customerDao = database.customerDao()
            val expenseDao = database.expenseDao()
            val saleDao = database.saleDao()
            val purchaseDao = database.purchaseDao()
            val stockAuditDao = database.stockAuditDao()

            // 1. Initial Shop Profile
            val defaultShop = ShopProfile(
                id = 1,
                shopName = "Puja Dry Fruits",
                shopHindiName = "पूजा ड्राई फ्रूट्स",
                tagline = "Wholesale & Retail Dry Fruits, Premium Nuts & Exotic Berries",
                ownerName = "Puja Kumari",
                phone = "+91 98765 43210",
                email = "pujadryfruit@gmail.com",
                address = "Shop No. 12, Golden Plaza, Dry Fruit Market, Khari Baoli, Delhi - 110006",
                gstin = "07AAAAA1234Z1Z5",
                upiId = "pujadryfruits@upi",
                invoicePrefix = "PDF",
                nextInvoiceNumber = 1004,
                thankYouMessage = "Thank you for shopping at Puja Dry Fruits! We wish you good health and prosperity.",
                isPinSecurityEnabled = false,
                securityPin = "1234"
            )
            shopDao.insertOrUpdateProfile(defaultShop)

            // 2. Initial Dry Fruit Products
            val initialProducts = listOf(
                ProductEntity(
                    sku = "DF-ALM-01",
                    name = "California Almonds (Badam)",
                    hindiName = "कैलिफ़ोर्निया बादाम",
                    category = "Nuts",
                    unit = "Kilogram",
                    purchasePrice = 780.0,
                    sellingPrice = 950.0,
                    currentStock = 45.0,
                    minStockLevel = 10.0,
                    supplierName = "Kashmir Nut Exporters",
                    description = "Premium grade jumbo sweet California almonds rich in vitamin E",
                    colorHex = "#C87D32"
                ),
                ProductEntity(
                    sku = "DF-CSH-02",
                    name = "W320 Royal Cashew (Kaju)",
                    hindiName = "काजू W320",
                    category = "Nuts",
                    unit = "Kilogram",
                    purchasePrice = 750.0,
                    sellingPrice = 980.0,
                    currentStock = 28.0,
                    minStockLevel = 8.0,
                    supplierName = "Goa Cashew Mills",
                    description = "Crisp, whole white kernels with rich buttery texture",
                    colorHex = "#E4B874"
                ),
                ProductEntity(
                    sku = "DF-PST-03",
                    name = "Afghan Green Pistachio (Pista)",
                    hindiName = "अफगानी पिस्ता",
                    category = "Nuts",
                    unit = "Kilogram",
                    purchasePrice = 1100.0,
                    sellingPrice = 1450.0,
                    currentStock = 18.0,
                    minStockLevel = 5.0,
                    supplierName = "Kabul Dry Fruit Imports",
                    description = "Naturally opened, vibrant green salted & roasted pistachios",
                    colorHex = "#6E9943"
                ),
                ProductEntity(
                    sku = "DF-WLN-04",
                    name = "Kashmiri Walnut Kernels (Akhrot)",
                    hindiName = "कश्मीरी अखरोट गिरी",
                    category = "Nuts",
                    unit = "Kilogram",
                    purchasePrice = 850.0,
                    sellingPrice = 1150.0,
                    currentStock = 22.0,
                    minStockLevel = 6.0,
                    supplierName = "Pahalgam Orchards",
                    description = "Extra light snow-white walnut halves with high natural oil",
                    colorHex = "#8D5B34"
                ),
                ProductEntity(
                    sku = "DF-RSN-05",
                    name = "Royal Green Raisins (Kishmish)",
                    hindiName = "हरी किशमish",
                    category = "Dry Fruits",
                    unit = "Kilogram",
                    purchasePrice = 280.0,
                    sellingPrice = 390.0,
                    currentStock = 50.0,
                    minStockLevel = 12.0,
                    supplierName = "Sangli Raisin Traders",
                    description = "Long golden-green sweet raisins, seedless and naturally dried",
                    colorHex = "#9EA038"
                ),
                ProductEntity(
                    sku = "DF-DTE-06",
                    name = "Medjool King Dates (Khajoor)",
                    hindiName = "शाही खजूर (मजदूल)",
                    category = "Dry Fruits",
                    unit = "Box",
                    purchasePrice = 450.0,
                    sellingPrice = 650.0,
                    currentStock = 35.0,
                    minStockLevel = 10.0,
                    supplierName = "Arabian Oasis Imports",
                    description = "500g Premium gift pack of soft, caramel-flavored Medjool dates",
                    colorHex = "#5E301D"
                ),
                ProductEntity(
                    sku = "DF-FIG-07",
                    name = "Dried Turkish Figs (Anjeer)",
                    hindiName = "तुर्की अंजीर",
                    category = "Dry Fruits",
                    unit = "Kilogram",
                    purchasePrice = 1200.0,
                    sellingPrice = 1580.0,
                    currentStock = 14.0,
                    minStockLevel = 5.0,
                    supplierName = "Izmir Export Co.",
                    description = "Soft, honey-sweet whole strung dried figs high in fiber",
                    colorHex = "#A66A44"
                ),
                ProductEntity(
                    sku = "DF-APR-08",
                    name = "Royal Khubani / Apricots (Jardalu)",
                    hindiName = "खुबानी (जरदालू)",
                    category = "Dry Fruits",
                    unit = "Kilogram",
                    purchasePrice = 620.0,
                    sellingPrice = 850.0,
                    currentStock = 4.0, // Low stock on purpose
                    minStockLevel = 8.0,
                    supplierName = "Ladakh Naturals",
                    description = "Sweet sun-dried whole golden apricots with rich aroma",
                    colorHex = "#F49F1C"
                ),
                ProductEntity(
                    sku = "DF-MXD-09",
                    name = "Puja Special Mix Dry Fruits",
                    hindiName = "पूजा स्पेशल मिक्स ड्राई फ्रूट्स",
                    category = "Gift Boxes",
                    unit = "Packet",
                    purchasePrice = 520.0,
                    sellingPrice = 750.0,
                    currentStock = 40.0,
                    minStockLevel = 10.0,
                    supplierName = "Puja In-House Blends",
                    description = "500g signature blend of Almonds, Cashews, Pista, Cranberries & Walnuts",
                    colorHex = "#9C3D2B"
                ),
                ProductEntity(
                    sku = "DF-SED-10",
                    name = "Organic Chia & Pumpkin Seeds",
                    hindiName = "चिया और कद्दू के बीज",
                    category = "Seeds",
                    unit = "Packet",
                    purchasePrice = 220.0,
                    sellingPrice = 340.0,
                    currentStock = 3.0, // Low stock on purpose
                    minStockLevel = 6.0,
                    supplierName = "Pure Organics India",
                    description = "250g raw nutrient-dense seeds for energy and immunity",
                    colorHex = "#446949"
                )
            )
            productDao.insertAllProducts(initialProducts)

            // 3. Initial Sample Customers
            val initialCustomers = listOf(
                CustomerEntity(
                    name = "Rahul Kumar Sharma",
                    mobile = "9876543210",
                    address = "B-42, Model Town, Delhi",
                    totalOrders = 3,
                    totalSpent = 4850.0,
                    totalProfitGenerated = 1120.0,
                    outstandingBalance = 0.0,
                    lastPurchaseDate = System.currentTimeMillis() - 86400000L
                ),
                CustomerEntity(
                    name = "Sunita Verma",
                    mobile = "9811223344",
                    address = "Flat 304, Green Heights, Delhi",
                    totalOrders = 2,
                    totalSpent = 3200.0,
                    totalProfitGenerated = 780.0,
                    outstandingBalance = 800.0, // Has Udhaar
                    lastPurchaseDate = System.currentTimeMillis() - 172800000L
                ),
                CustomerEntity(
                    name = "Amit Gupta (Gupta Sweets)",
                    mobile = "9988776655",
                    address = "Plot 15, Chandni Chowk",
                    totalOrders = 5,
                    totalSpent = 18500.0,
                    totalProfitGenerated = 4200.0,
                    outstandingBalance = 3500.0, // Commercial Udhaar
                    lastPurchaseDate = System.currentTimeMillis() - 43200000L
                ),
                CustomerEntity(
                    name = "Pooja Malhotra",
                    mobile = "9765432190",
                    address = "Sector 14, Rohini",
                    totalOrders = 1,
                    totalSpent = 1550.0,
                    totalProfitGenerated = 380.0,
                    outstandingBalance = 0.0,
                    lastPurchaseDate = System.currentTimeMillis() - 259200000L
                )
            )
            initialCustomers.forEach { customerDao.insertCustomer(it) }

            // 4. Initial Sample Sales
            val sale1 = SaleEntity(
                invoiceNumber = "PDF-1001",
                customerId = 1,
                customerName = "Rahul Kumar Sharma",
                customerMobile = "9876543210",
                saleDate = System.currentTimeMillis() - 86400000L,
                subtotal = 1850.0,
                discount = 50.0,
                taxAmount = 0.0,
                grandTotal = 1800.0,
                totalCost = 1380.0,
                grossProfit = 420.0,
                profitMarginPercent = 23.33,
                paymentMethod = "UPI",
                paymentStatus = "PAID",
                paidAmount = 1800.0,
                balanceAmount = 0.0
            )
            val sale1Id = saleDao.insertSale(sale1)
            saleDao.insertSaleItems(
                listOf(
                    SaleItemEntity(
                        saleId = sale1Id,
                        productId = 1,
                        productName = "California Almonds (Badam)",
                        unit = "Kilogram",
                        quantity = 1.0,
                        sellingPrice = 950.0,
                        costPrice = 780.0,
                        discount = 0.0,
                        totalAmount = 950.0,
                        itemProfit = 170.0
                    ),
                    SaleItemEntity(
                        saleId = sale1Id,
                        productId = 5,
                        productName = "Royal Green Raisins (Kishmish)",
                        unit = "Kilogram",
                        quantity = 1.0,
                        sellingPrice = 390.0,
                        costPrice = 280.0,
                        discount = 0.0,
                        totalAmount = 390.0,
                        itemProfit = 110.0
                    ),
                    SaleItemEntity(
                        saleId = sale1Id,
                        productId = 9,
                        productName = "Puja Special Mix Dry Fruits",
                        unit = "Packet",
                        quantity = 1.0,
                        sellingPrice = 510.0,
                        costPrice = 320.0,
                        discount = 0.0,
                        totalAmount = 510.0,
                        itemProfit = 190.0
                    )
                )
            )

            val sale2 = SaleEntity(
                invoiceNumber = "PDF-1002",
                customerId = 2,
                customerName = "Sunita Verma",
                customerMobile = "9811223344",
                saleDate = System.currentTimeMillis() - 172800000L,
                subtotal = 2100.0,
                discount = 100.0,
                taxAmount = 0.0,
                grandTotal = 2000.0,
                totalCost = 1500.0,
                grossProfit = 500.0,
                profitMarginPercent = 25.0,
                paymentMethod = "Credit / Udhaar",
                paymentStatus = "PARTIAL",
                paidAmount = 1200.0,
                balanceAmount = 800.0,
                dueDate = System.currentTimeMillis() + 86400000L * 7
            )
            val sale2Id = saleDao.insertSale(sale2)
            saleDao.insertSaleItems(
                listOf(
                    SaleItemEntity(
                        saleId = sale2Id,
                        productId = 2,
                        productName = "W320 Royal Cashew (Kaju)",
                        unit = "Kilogram",
                        quantity = 1.0,
                        sellingPrice = 980.0,
                        costPrice = 750.0,
                        discount = 0.0,
                        totalAmount = 980.0,
                        itemProfit = 230.0
                    ),
                    SaleItemEntity(
                        saleId = sale2Id,
                        productId = 4,
                        productName = "Kashmiri Walnut Kernels (Akhrot)",
                        unit = "Kilogram",
                        quantity = 1.0,
                        sellingPrice = 1120.0,
                        costPrice = 850.0,
                        discount = 0.0,
                        totalAmount = 1120.0,
                        itemProfit = 270.0
                    )
                )
            )

            val sale3 = SaleEntity(
                invoiceNumber = "PDF-1003",
                customerId = 3,
                customerName = "Amit Gupta (Gupta Sweets)",
                customerMobile = "9988776655",
                saleDate = System.currentTimeMillis() - 10800000L,
                subtotal = 7500.0,
                discount = 200.0,
                taxAmount = 0.0,
                grandTotal = 7300.0,
                totalCost = 5500.0,
                grossProfit = 1800.0,
                profitMarginPercent = 24.65,
                paymentMethod = "Credit / Udhaar",
                paymentStatus = "PARTIAL",
                paidAmount = 3800.0,
                balanceAmount = 3500.0,
                dueDate = System.currentTimeMillis() + 86400000L * 5
            )
            val sale3Id = saleDao.insertSale(sale3)
            saleDao.insertSaleItems(
                listOf(
                    SaleItemEntity(
                        saleId = sale3Id,
                        productId = 3,
                        productName = "Afghan Green Pistachio (Pista)",
                        unit = "Kilogram",
                        quantity = 3.0,
                        sellingPrice = 1450.0,
                        costPrice = 1100.0,
                        discount = 0.0,
                        totalAmount = 4350.0,
                        itemProfit = 1050.0
                    ),
                    SaleItemEntity(
                        saleId = sale3Id,
                        productId = 2,
                        productName = "W320 Royal Cashew (Kaju)",
                        unit = "Kilogram",
                        quantity = 3.0,
                        sellingPrice = 980.0,
                        costPrice = 750.0,
                        discount = 0.0,
                        totalAmount = 2940.0,
                        itemProfit = 690.0
                    )
                )
            )

            // 5. Initial Sample Expenses
            val initialExpenses = listOf(
                ExpenseEntity(
                    expenseDate = System.currentTimeMillis() - 86400000L * 2,
                    category = "Shop Rent",
                    description = "Monthly Dry Fruit Market Shop Rent",
                    amount = 12000.0,
                    paymentMethod = "Bank Transfer"
                ),
                ExpenseEntity(
                    expenseDate = System.currentTimeMillis() - 86400000L * 3,
                    category = "Electricity",
                    description = "Shop AC and Cold Storage bill",
                    amount = 2850.0,
                    paymentMethod = "UPI"
                ),
                ExpenseEntity(
                    expenseDate = System.currentTimeMillis() - 86400000L * 1,
                    category = "Packaging",
                    description = "Gold embossed dry fruit gift boxes & pouches",
                    amount = 1450.0,
                    paymentMethod = "Cash"
                ),
                ExpenseEntity(
                    expenseDate = System.currentTimeMillis() - 3600000L * 4,
                    category = "Tea & Refreshment",
                    description = "Staff tea and customer refreshments",
                    amount = 220.0,
                    paymentMethod = "Cash"
                )
            )
            initialExpenses.forEach { expenseDao.insertExpense(it) }

            // 6. Initial Purchases / Stock In
            val purchase1 = PurchaseEntity(
                purchaseDate = System.currentTimeMillis() - 86400000L * 4,
                supplierName = "Kashmir Nut Exporters",
                supplierPhone = "+91 94190 12345",
                invoiceReference = "SUP-KASH-882",
                totalAmount = 39000.0,
                paymentStatus = "PAID",
                paymentMethod = "Bank Transfer",
                notes = "Batch 2026-A fresh season crop"
            )
            val p1Id = purchaseDao.insertPurchase(purchase1)
            purchaseDao.insertPurchaseItems(
                listOf(
                    PurchaseItemEntity(
                        purchaseId = p1Id,
                        productId = 1,
                        productName = "California Almonds (Badam)",
                        unit = "Kilogram",
                        quantity = 50.0,
                        purchasePrice = 780.0,
                        totalAmount = 39000.0
                    )
                )
            )

            // Stock audit entries
            stockAuditDao.insertAudit(
                StockAuditEntity(
                    productId = 1,
                    productName = "California Almonds (Badam)",
                    changeType = "PURCHASE",
                    quantityChanged = 50.0,
                    previousStock = 0.0,
                    newStock = 50.0,
                    referenceId = "SUP-KASH-882",
                    reason = "Initial wholesale procurement"
                )
            )
        }
    }
}
