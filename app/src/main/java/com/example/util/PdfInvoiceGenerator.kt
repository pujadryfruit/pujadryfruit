package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entities.SaleEntity
import com.example.data.local.entities.SaleItemEntity
import com.example.data.local.entities.ShopProfile
import java.io.File
import java.io.FileOutputStream

object PdfInvoiceGenerator {

    /**
     * Generates a clean A4 PDF Invoice for the given sale and returns the File reference.
     */
    fun generateInvoicePdf(
        context: Context,
        shop: ShopProfile,
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): File {
        val invoicesDir = File(context.cacheDir, "invoices")
        if (!invoicesDir.exists()) {
            invoicesDir.mkdirs()
        }

        val pdfFile = File(invoicesDir, "Invoice_${sale.invoiceNumber.replace("/", "_")}.pdf")

        val document = PdfDocument()
        val pageWidth = 595 // Standard A4 width in points (72 dpi)
        val pageHeight = 842 // Standard A4 height in points
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Background
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

        // Header Background Banner (Deep Purple / Maroon)
        paint.color = Color.rgb(74, 20, 140) // #4A148C
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 95f, paint)

        // Header Text: Shop Name
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        canvas.drawText(shop.shopName.uppercase(), 36f, 40f, paint)

        // Header Text: Hindi Name / Subtitle
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        paint.color = Color.rgb(234, 221, 255)
        val subName = if (shop.shopHindiName.isNotEmpty()) "${shop.shopHindiName} • ${shop.tagline}" else shop.tagline
        canvas.drawText(subName, 36f, 60f, paint)

        // TAX INVOICE tag on top right
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        canvas.drawText("TAX INVOICE", pageWidth - 160f, 42f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(234, 221, 255)
        canvas.drawText("#${sale.invoiceNumber}", pageWidth - 160f, 62f, paint)

        // Shop Contact info bar below header
        var currentY = 118f
        paint.color = Color.DKGRAY
        paint.textSize = 10f
        canvas.drawText("Address: ${shop.address}", 36f, currentY, paint)
        canvas.drawText("Date: ${DateUtils.formatDateTime(sale.saleDate)}", pageWidth - 200f, currentY, paint)

        currentY += 15f
        canvas.drawText("Phone: ${shop.phone}  |  Email: ${shop.email}", 36f, currentY, paint)
        if (shop.gstin.isNotEmpty()) {
            canvas.drawText("GSTIN: ${shop.gstin}", pageWidth - 200f, currentY, paint)
        }

        currentY += 20f
        // Divider line
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawLine(36f, currentY, pageWidth - 36f, currentY, paint)

        // Customer Details Box
        currentY += 20f
        paint.color = Color.rgb(245, 245, 245)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(36f, currentY - 14f, pageWidth - 36f, currentY + 44f, 8f, 8f, paint)

        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("BILL TO (CUSTOMER):", 48f, currentY + 2f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        paint.color = Color.rgb(74, 20, 140)
        canvas.drawText(sale.customerName, 48f, currentY + 20f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = Color.DKGRAY
        val custMobile = if (sale.customerMobile.isNotEmpty()) "Phone: ${sale.customerMobile}" else "Cash Counter Sale"
        canvas.drawText(custMobile, 48f, currentY + 34f, paint)

        val paymentStatusText = "Payment: ${sale.paymentMethod} (${sale.paymentStatus})"
        canvas.drawText(paymentStatusText, pageWidth - 220f, currentY + 20f, paint)

        // Table Header
        currentY += 65f
        paint.color = Color.rgb(74, 20, 140)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(36f, currentY - 14f, pageWidth - 36f, currentY + 14f, 6f, 6f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f

        canvas.drawText("SR", 46f, currentY, paint)
        canvas.drawText("ITEM DESCRIPTION", 75f, currentY, paint)
        canvas.drawText("RATE / UNIT", 300f, currentY, paint)
        canvas.drawText("QTY", 400f, currentY, paint)
        canvas.drawText("TOTAL (INR)", pageWidth - 110f, currentY, paint)

        // Table Items
        currentY += 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.BLACK
        paint.textSize = 10f

        items.forEachIndexed { index, item ->
            // Zebra shading
            if (index % 2 == 1) {
                paint.color = Color.rgb(250, 250, 250)
                paint.style = Paint.Style.FILL
                canvas.drawRect(36f, currentY - 10f, pageWidth - 36f, currentY + 12f, paint)
            }

            paint.color = Color.BLACK
            paint.style = Paint.Style.FILL
            canvas.drawText("${index + 1}", 46f, currentY + 2f, paint)
            canvas.drawText(item.productName, 75f, currentY + 2f, paint)
            canvas.drawText(CurrencyUtils.formatInr(item.sellingPrice, false) + " / " + item.unit, 300f, currentY + 2f, paint)
            canvas.drawText(CurrencyUtils.formatQuantity(item.quantity, item.unit), 400f, currentY + 2f, paint)
            
            val itemTotalStr = CurrencyUtils.formatInr(item.totalAmount, false)
            canvas.drawText(itemTotalStr, pageWidth - 110f, currentY + 2f, paint)

            currentY += 22f
        }

        // Table Bottom Line
        currentY += 5f
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawLine(36f, currentY, pageWidth - 36f, currentY, paint)

        // Calculation & Summary Block
        currentY += 20f
        val summaryX = pageWidth - 220f

        paint.textSize = 10f
        paint.color = Color.DKGRAY
        canvas.drawText("Subtotal:", summaryX, currentY, paint)
        canvas.drawText(CurrencyUtils.formatInr(sale.subtotal), pageWidth - 90f, currentY, paint)

        if (sale.discount > 0) {
            currentY += 16f
            canvas.drawText("Discount:", summaryX, currentY, paint)
            canvas.drawText("-${CurrencyUtils.formatInr(sale.discount)}", pageWidth - 90f, currentY, paint)
        }

        if (sale.taxAmount > 0) {
            currentY += 16f
            canvas.drawText("GST / Tax:", summaryX, currentY, paint)
            canvas.drawText("+${CurrencyUtils.formatInr(sale.taxAmount)}", pageWidth - 90f, currentY, paint)
        }

        // Grand Total Box
        currentY += 22f
        paint.color = Color.rgb(243, 229, 245)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(summaryX - 10f, currentY - 14f, pageWidth - 36f, currentY + 16f, 6f, 6f, paint)

        paint.color = Color.rgb(74, 20, 140)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("GRAND TOTAL:", summaryX, currentY + 2f, paint)
        canvas.drawText(CurrencyUtils.formatInr(sale.grandTotal), pageWidth - 95f, currentY + 2f, paint)

        // Payment status details
        currentY += 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = Color.DKGRAY
        canvas.drawText("Amount Paid: ${CurrencyUtils.formatInr(sale.paidAmount)}", summaryX, currentY, paint)

        if (sale.balanceAmount > 0) {
            currentY += 15f
            paint.color = Color.rgb(183, 28, 28) // Red
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Balance Due: ${CurrencyUtils.formatInr(sale.balanceAmount)}", summaryX, currentY, paint)
        }

        // UPI and Contact Details on the Left
        var leftInfoY = currentY - 45f
        if (shop.upiId.isNotEmpty()) {
            paint.color = Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 10f
            canvas.drawText("UPI Payment ID: ${shop.upiId}", 48f, leftInfoY, paint)
            leftInfoY += 14f
        }

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.DKGRAY
        canvas.drawText("Owner / Contact: ${shop.ownerName} (${shop.phone})", 48f, leftInfoY, paint)

        // Footer Section
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawLine(36f, pageHeight - 65f, pageWidth - 36f, pageHeight - 65f, paint)

        paint.color = Color.DKGRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        val thankMsg = if (shop.thankYouMessage.isNotEmpty()) shop.thankYouMessage else "Thank you for shopping with us! Visit again."
        canvas.drawText("🙏 $thankMsg", 48f, pageHeight - 45f, paint)

        paint.textSize = 8f
        paint.color = Color.GRAY
        canvas.drawText("Computer Generated Tax Invoice • Puja Dry Fruits Management", 48f, pageHeight - 30f, paint)

        document.finishPage(page)

        val outputStream = FileOutputStream(pdfFile)
        document.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        document.close()

        return pdfFile
    }

    /**
     * Shares the generated PDF file via WhatsApp (or default system share if WhatsApp is unavailable).
     */
    fun sharePdfViaWhatsApp(
        context: Context,
        pdfFile: File,
        customerMobile: String,
        captionText: String
    ) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val cleanPhone = customerMobile.replace("[^0-9]".toRegex(), "")
            val internationalPhone = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, captionText)
                if (internationalPhone.isNotEmpty()) {
                    putExtra("jid", "$internationalPhone@s.whatsapp.net")
                }
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            // WhatsApp not installed or failed; fallback to universal share sheet
            sharePdf(context, pdfFile, "Invoice PDF", captionText)
        }
    }

    /**
     * Universal Share sheet for PDF file
     */
    fun sharePdf(
        context: Context,
        pdfFile: File,
        title: String,
        captionText: String
    ) {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, captionText)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
