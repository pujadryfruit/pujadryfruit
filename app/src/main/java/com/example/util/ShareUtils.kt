package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.local.entities.SaleEntity
import com.example.data.local.entities.SaleItemEntity
import com.example.data.local.entities.ShopProfile
import java.net.URLEncoder

object ShareUtils {

    fun generateInvoiceText(
        shop: ShopProfile,
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("🌟 *${shop.shopName.uppercase()}* 🌟")
        if (shop.shopHindiName.isNotEmpty()) {
            sb.appendLine("_${shop.shopHindiName}_")
        }
        sb.appendLine("${shop.tagline}")
        sb.appendLine("📍 ${shop.address}")
        sb.appendLine("📞 ${shop.phone}")
        if (shop.gstin.isNotEmpty()) {
            sb.appendLine("GSTIN: ${shop.gstin}")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🧾 *TAX INVOICE / BILL*")
        sb.appendLine("Invoice No: *${sale.invoiceNumber}*")
        sb.appendLine("Date: ${DateUtils.formatDateTime(sale.saleDate)}")
        sb.appendLine("Customer: *${sale.customerName}*")
        if (sale.customerMobile.isNotEmpty()) {
            sb.appendLine("Mobile: ${sale.customerMobile}")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("*ITEMS PURCHASED:*")
        items.forEachIndexed { index, item ->
            val rateStr = CurrencyUtils.formatInr(item.sellingPrice, false)
            val qtyStr = CurrencyUtils.formatQuantity(item.quantity, item.unit)
            val totalStr = CurrencyUtils.formatInr(item.totalAmount)
            sb.appendLine("${index + 1}. *${item.productName}*")
            sb.appendLine("   $qtyStr × $rateStr = $totalStr")
            if (item.discount > 0) {
                sb.appendLine("   (Disc: ${CurrencyUtils.formatInr(item.discount)})")
            }
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Subtotal: ${CurrencyUtils.formatInr(sale.subtotal)}")
        if (sale.discount > 0) {
            sb.appendLine("Discount: -${CurrencyUtils.formatInr(sale.discount)}")
        }
        if (sale.taxAmount > 0) {
            sb.appendLine("GST/Tax: +${CurrencyUtils.formatInr(sale.taxAmount)}")
        }
        sb.appendLine("--------------------")
        sb.appendLine("💰 *GRAND TOTAL: ${CurrencyUtils.formatInr(sale.grandTotal)}*")
        sb.appendLine("Payment Mode: *${sale.paymentMethod}*")
        sb.appendLine("Payment Status: *${sale.paymentStatus}*")
        if (sale.balanceAmount > 0) {
            sb.appendLine("Paid Amount: ${CurrencyUtils.formatInr(sale.paidAmount)}")
            sb.appendLine("⚠️ *Pending Udhaar/Balance: ${CurrencyUtils.formatInr(sale.balanceAmount)}*")
            if (sale.dueDate != null) {
                sb.appendLine("Due Date: ${DateUtils.formatDate(sale.dueDate)}")
            }
        }
        if (shop.upiId.isNotEmpty()) {
            sb.appendLine("📲 Pay via UPI: *${shop.upiId}*")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🙏 ${shop.thankYouMessage}")
        return sb.toString()
    }

    fun shareViaWhatsApp(context: Context, mobileNumber: String, text: String) {
        try {
            val cleanPhone = mobileNumber.replace("[^0-9]".toRegex(), "")
            val internationalPhone = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone
            val url = "https://api.whatsapp.com/send?phone=$internationalPhone&text=" + URLEncoder.encode(text, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard share sheet
            shareText(context, "Invoice - Puja Dry Fruits", text)
        }
    }

    fun shareText(context: Context, title: String, text: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }

    fun generateReportSummaryText(
        shop: ShopProfile,
        period: String,
        sales: Double,
        cost: Double,
        grossProfit: Double,
        expenses: Double,
        netProfit: Double,
        margin: Double,
        ordersCount: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("📊 *${shop.shopName.uppercase()} - BUSINESS REPORT* 📊")
        if (shop.shopHindiName.isNotEmpty()) {
            sb.appendLine("_${shop.shopHindiName}_")
        }
        sb.appendLine("Period: *$period*")
        sb.appendLine("Generated: ${DateUtils.formatDateTime(System.currentTimeMillis())}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("💰 Total Sales: *${CurrencyUtils.formatInr(sales)}* ($ordersCount orders)")
        sb.appendLine("📦 Cost of Goods (COGS): ${CurrencyUtils.formatInr(cost)}")
        sb.appendLine("📈 Gross Profit: *${CurrencyUtils.formatInr(grossProfit)}*")
        sb.appendLine("💸 Total Expenses: ${CurrencyUtils.formatInr(expenses)}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🏆 *NET BUSINESS PROFIT: ${CurrencyUtils.formatInr(netProfit)}*")
        sb.appendLine("🎯 Net Profit Margin: *${CurrencyUtils.formatPercent(margin)}*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📞 ${shop.phone} • ${shop.address}")
        return sb.toString()
    }
}
