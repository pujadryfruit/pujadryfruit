package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.entities.SaleEntity
import com.example.data.local.entities.SaleItemEntity
import com.example.data.local.entities.ShopProfile
import java.io.File
import java.io.FileOutputStream

object PdfInvoiceGenerator {

    // Royal Golden Theme Palette
    private val GOLD_DARK = Color.rgb(120, 85, 12)       // #78550C - Royal Dark Golden Bronze
    private val GOLD_PRIMARY = Color.rgb(180, 130, 20)    // #B48214 - Primary Lustrous Gold
    private val GOLD_LIGHT = Color.rgb(222, 180, 68)      // #DEB444 - Bright Accent Gold
    private val GOLD_ACCENT = Color.rgb(250, 218, 122)    // #FADA7A - Champagne Soft Gold
    private val GOLD_BG_LIGHT = Color.rgb(253, 250, 242)  // #FDFDF6 - Light Ivory Cream tint
    private val GOLD_BG_CARD = Color.rgb(248, 242, 226)   // #F8F2E2 - Soft Golden Parchment
    private val GOLD_BORDER = Color.rgb(218, 186, 125)    // #DABA7D - Golden separator line
    private val TEXT_MAIN = Color.rgb(35, 30, 25)         // #231E19 - Deep Charcoal Brown
    private val TEXT_MUTED = Color.rgb(110, 100, 90)      // #6E645A - Warm Muted Gray
    private val COLOR_RED_DUE = Color.rgb(198, 40, 40)    // #C62828 - Red for pending due balance
    private val COLOR_GREEN_PAID = Color.rgb(46, 125, 50) // #2E7D32 - Green for paid status

    /**
     * Generates a premium Royal Golden A4 PDF Invoice for the given sale.
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

        val cleanInvNum = sale.invoiceNumber.replace("/", "_").replace(" ", "_")
        val pdfFile = File(invoicesDir, "Invoice_$cleanInvNum.pdf")

        val document = PdfDocument()
        val pageWidth = 595  // Standard A4 width in points (72 dpi)
        val pageHeight = 842 // Standard A4 height in points
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // 1. Page Background (Crisp White with a subtle gold border edge)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

        // Outer Page Golden Border
        paint.color = GOLD_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(18f, 18f, pageWidth - 18f, pageHeight - 18f, paint)

        // 2. Header Banner (Royal Dark Gold with Golden Accent Bottom Stripe)
        val headerHeight = 104f
        paint.style = Paint.Style.FILL
        paint.color = GOLD_DARK
        canvas.drawRect(18f, 18f, pageWidth - 18f, headerHeight, paint)

        // Golden Accent bottom bar for header
        paint.color = GOLD_LIGHT
        canvas.drawRect(18f, headerHeight - 4f, pageWidth - 18f, headerHeight, paint)

        // 3. Shop Logo on Header
        val logoSize = 60f
        val logoX = 36f
        val logoY = 26f

        // Try decoding shop logo from resources
        val logoBitmap: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_puja_logo)
        } catch (e: Exception) {
            null
        }

        if (logoBitmap != null) {
            // Draw Gold Outer Ring for Logo
            paint.style = Paint.Style.STROKE
            paint.color = GOLD_LIGHT
            paint.strokeWidth = 2f
            canvas.drawCircle(logoX + logoSize / 2f, logoY + logoSize / 2f, (logoSize / 2f) + 1.5f, paint)

            // Draw White Circular Backing
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(logoX + logoSize / 2f, logoY + logoSize / 2f, logoSize / 2f, paint)

            // Clip and draw Bitmap
            canvas.save()
            val clipPath = Path().apply {
                addCircle(logoX + logoSize / 2f, logoY + logoSize / 2f, logoSize / 2f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val srcRect = Rect(0, 0, logoBitmap.width, logoBitmap.height)
            val dstRect = RectF(logoX, logoY, logoX + logoSize, logoY + logoSize)
            canvas.drawBitmap(logoBitmap, srcRect, dstRect, null)
            canvas.restore()
        }

        // 4. Shop Typography in Header
        val textStartX = if (logoBitmap != null) logoX + logoSize + 14f else 36f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT

        // Shop Name
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(shop.shopName.uppercase(), textStartX, 48f, paint)

        // Hindi Subtitle & Tagline
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10.5f
        paint.color = GOLD_ACCENT
        val subHeading = if (shop.shopHindiName.isNotEmpty()) {
            "${shop.shopHindiName} • ${shop.tagline}"
        } else {
            shop.tagline.ifEmpty { "Premium Dry Fruits, Nuts & Berries" }
        }
        canvas.drawText(subHeading, textStartX, 66f, paint)

        // Contact info on header
        paint.textSize = 9f
        paint.color = Color.rgb(240, 235, 220)
        canvas.drawText("📞 ${shop.phone}  |  ✉️ ${shop.email}", textStartX, 82f, paint)

        // 5. Right-Aligned Tax Invoice Badge in Header
        paint.textAlign = Paint.Align.RIGHT
        val rightEdge = pageWidth - 36f

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        canvas.drawText("TAX INVOICE", rightEdge, 46f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11.5f
        paint.color = GOLD_ACCENT
        canvas.drawText("#${sale.invoiceNumber}", rightEdge, 65f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.rgb(240, 235, 220)
        canvas.drawText("Date: ${DateUtils.formatDateTime(sale.saleDate)}", rightEdge, 82f, paint)

        // Reset text alignment
        paint.textAlign = Paint.Align.LEFT

        // 6. Shop Address & GSTIN Bar
        var currentY = 120f
        paint.color = TEXT_MUTED
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("📍 Shop Address: ${shop.address}", 36f, currentY, paint)

        if (shop.gstin.isNotEmpty()) {
            paint.textAlign = Paint.Align.RIGHT
            paint.color = GOLD_DARK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("GSTIN: ${shop.gstin}", rightEdge, currentY, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        currentY += 14f
        // Golden hairline separator
        paint.color = GOLD_BORDER
        paint.strokeWidth = 0.8f
        canvas.drawLine(36f, currentY, rightEdge, currentY, paint)

        // 7. Billed To Customer Card (Golden Light Box)
        currentY += 12f
        val custBoxTop = currentY
        val custBoxHeight = 54f
        val custBoxBottom = custBoxTop + custBoxHeight

        // Box background & border
        paint.style = Paint.Style.FILL
        paint.color = GOLD_BG_CARD
        canvas.drawRoundRect(36f, custBoxTop, rightEdge, custBoxBottom, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = GOLD_BORDER
        paint.strokeWidth = 1f
        canvas.drawRoundRect(36f, custBoxTop, rightEdge, custBoxBottom, 8f, 8f, paint)

        // Customer details text
        paint.style = Paint.Style.FILL
        paint.color = GOLD_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText("BILLED TO (CUSTOMER):", 48f, custBoxTop + 16f, paint)

        paint.color = TEXT_MAIN
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12.5f
        canvas.drawText(sale.customerName.ifEmpty { "Counter Customer" }, 48f, custBoxTop + 32f, paint)

        paint.color = TEXT_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        val phoneStr = if (sale.customerMobile.isNotEmpty()) "Mobile: ${sale.customerMobile}" else "Cash Counter Sale"
        canvas.drawText(phoneStr, 48f, custBoxTop + 46f, paint)

        // Payment status badge on right of customer box
        val isPaid = sale.paymentStatus.equals("PAID", ignoreCase = true)
        val badgeBg = if (isPaid) Color.rgb(232, 245, 233) else Color.rgb(255, 235, 238)
        val badgeTextColor = if (isPaid) COLOR_GREEN_PAID else COLOR_RED_DUE
        val badgeText = if (isPaid) "PAID IN FULL" else "${sale.paymentStatus.uppercase()} (DUE)"

        val badgeWidth = 120f
        val badgeHeight = 22f
        val badgeX = rightEdge - badgeWidth - 12f
        val badgeY = custBoxTop + 16f

        paint.color = badgeBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, 6f, 6f, paint)

        paint.color = badgeTextColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.CENTER
        val badgeBaseline = (badgeY + badgeHeight / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(badgeText, badgeX + badgeWidth / 2f, badgeBaseline, paint)

        // Payment mode text under badge
        paint.color = TEXT_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Mode: ${sale.paymentMethod}", rightEdge - 12f, custBoxTop + 46f, paint)

        paint.textAlign = Paint.Align.LEFT

        // 8. Items Table Header
        currentY = custBoxBottom + 16f
        val tableHeaderHeight = 26f
        val tableTop = currentY
        val tableBottom = currentY + tableHeaderHeight

        // Header background (Rich Gold)
        paint.style = Paint.Style.FILL
        paint.color = GOLD_PRIMARY
        canvas.drawRoundRect(36f, tableTop, rightEdge, tableBottom, 6f, 6f, paint)

        // Table column coordinates
        val colSrCenter = 56f
        val colDescLeft = 82f
        val colRateRight = 365f
        val colQtyCenter = 420f
        val colTotalRight = rightEdge - 14f

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f

        val headerCenterY = (tableTop + tableBottom) / 2f
        val headerTextBaseline = headerCenterY - ((paint.descent() + paint.ascent()) / 2f)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("SR.", colSrCenter, headerTextBaseline, paint)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM DESCRIPTION", colDescLeft, headerTextBaseline, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE / UNIT", colRateRight, headerTextBaseline, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", colQtyCenter, headerTextBaseline, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL (INR)", colTotalRight, headerTextBaseline, paint)

        // 9. Table Item Rows - Mathematically Centered Vertically
        currentY = tableBottom
        val rowHeight = 26f
        val tableStartItemsY = currentY

        items.forEachIndexed { index, item ->
            val rowTop = currentY
            val rowBottom = currentY + rowHeight
            val rowCenterY = (rowTop + rowBottom) / 2f

            // Alternating zebra row background (Light Ivory Cream vs Pure White)
            paint.style = Paint.Style.FILL
            paint.color = if (index % 2 == 0) Color.WHITE else GOLD_BG_LIGHT
            canvas.drawRect(36f, rowTop, rightEdge, rowBottom, paint)

            // Calculate precise vertical baseline for standard row text
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9.5f
            paint.color = TEXT_MAIN
            val rowTextBaseline = rowCenterY - ((paint.descent() + paint.ascent()) / 2f)

            // 1. Serial Number (Centered)
            paint.textAlign = Paint.Align.CENTER
            paint.color = TEXT_MUTED
            canvas.drawText("${index + 1}", colSrCenter, rowTextBaseline, paint)

            // 2. Product Name (Left aligned, vertically centered)
            paint.textAlign = Paint.Align.LEFT
            paint.color = TEXT_MAIN
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val productName = item.productName
            // Truncate if exceptionally long
            val displayDesc = if (productName.length > 34) productName.take(32) + "..." else productName
            canvas.drawText(displayDesc, colDescLeft, rowTextBaseline, paint)

            // 3. Rate / Unit (Right aligned, vertically centered)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = TEXT_MUTED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val rateText = "${CurrencyUtils.formatInr(item.sellingPrice, false)} / ${item.unit}"
            canvas.drawText(rateText, colRateRight, rowTextBaseline, paint)

            // 4. Quantity (Center aligned, vertically centered)
            paint.textAlign = Paint.Align.CENTER
            paint.color = TEXT_MAIN
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val qtyText = CurrencyUtils.formatQuantity(item.quantity, item.unit)
            canvas.drawText(qtyText, colQtyCenter, rowTextBaseline, paint)

            // 5. Total Amount (Right aligned, vertically centered, bold)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = GOLD_DARK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val totalStr = CurrencyUtils.formatInr(item.totalAmount, false)
            canvas.drawText(totalStr, colTotalRight, rowTextBaseline, paint)

            // Row Bottom Divider line
            paint.color = GOLD_BORDER
            paint.strokeWidth = 0.5f
            canvas.drawLine(36f, rowBottom, rightEdge, rowBottom, paint)

            currentY += rowHeight
        }

        // Outer Table border
        val tableFinalBottom = currentY
        paint.style = Paint.Style.STROKE
        paint.color = GOLD_BORDER
        paint.strokeWidth = 1f
        canvas.drawRoundRect(36f, tableTop, rightEdge, tableFinalBottom, 6f, 6f, paint)

        // 10. Summary & Grand Total Block
        currentY += 16f
        val summaryCardWidth = 210f
        val summaryCardLeft = rightEdge - summaryCardWidth
        val summaryCardTop = currentY

        // Calculation rows on Right Side
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f

        // Subtotal
        paint.textAlign = Paint.Align.LEFT
        paint.color = TEXT_MUTED
        canvas.drawText("Subtotal:", summaryCardLeft + 8f, currentY + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.color = TEXT_MAIN
        canvas.drawText(CurrencyUtils.formatInr(sale.subtotal), rightEdge - 8f, currentY + 12f, paint)
        currentY += 16f

        // Discount if any
        if (sale.discount > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.color = COLOR_GREEN_PAID
            canvas.drawText("Discount (-):", summaryCardLeft + 8f, currentY + 12f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-${CurrencyUtils.formatInr(sale.discount)}", rightEdge - 8f, currentY + 12f, paint)
            currentY += 16f
        }

        // Tax if any
        if (sale.taxAmount > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.color = TEXT_MUTED
            canvas.drawText("GST / Tax (+):", summaryCardLeft + 8f, currentY + 12f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = TEXT_MAIN
            canvas.drawText("+${CurrencyUtils.formatInr(sale.taxAmount)}", rightEdge - 8f, currentY + 12f, paint)
            currentY += 16f
        }

        // Grand Total Highlight Banner
        val grandTotalTop = currentY + 6f
        val grandTotalHeight = 28f
        val grandTotalBottom = grandTotalTop + grandTotalHeight

        paint.style = Paint.Style.FILL
        paint.color = GOLD_DARK
        canvas.drawRoundRect(summaryCardLeft, grandTotalTop, rightEdge, grandTotalBottom, 6f, 6f, paint)

        val grandTotalCenterY = (grandTotalTop + grandTotalBottom) / 2f
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11.5f
        val grandTotalBaseline = grandTotalCenterY - ((paint.descent() + paint.ascent()) / 2f)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("GRAND TOTAL:", summaryCardLeft + 12f, grandTotalBaseline, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = GOLD_ACCENT
        canvas.drawText(CurrencyUtils.formatInr(sale.grandTotal), rightEdge - 12f, grandTotalBaseline, paint)

        // Paid & Due amounts breakdown below Grand Total
        currentY = grandTotalBottom + 12f
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9.5f
        paint.color = TEXT_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Amount Paid:", summaryCardLeft + 8f, currentY, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = COLOR_GREEN_PAID
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(CurrencyUtils.formatInr(sale.paidAmount), rightEdge - 8f, currentY, paint)

        if (sale.balanceAmount > 0) {
            currentY += 15f
            paint.textAlign = Paint.Align.LEFT
            paint.color = COLOR_RED_DUE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Balance Due (Udhaar):", summaryCardLeft + 8f, currentY, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(CurrencyUtils.formatInr(sale.balanceAmount), rightEdge - 8f, currentY, paint)
        }

        // 11. Left Side: Bank / UPI Details Card
        val leftCardRight = summaryCardLeft - 18f
        val leftCardTop = summaryCardTop
        val leftCardHeight = (currentY - leftCardTop).coerceAtLeast(70f)
        val leftCardBottom = leftCardTop + leftCardHeight

        paint.style = Paint.Style.FILL
        paint.color = GOLD_BG_CARD
        canvas.drawRoundRect(36f, leftCardTop, leftCardRight, leftCardBottom, 6f, 6f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = GOLD_BORDER
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(36f, leftCardTop, leftCardRight, leftCardBottom, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.color = GOLD_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText("PAYMENT & CONTACT DETAILS", 48f, leftCardTop + 16f, paint)

        var leftLineY = leftCardTop + 32f
        if (shop.upiId.isNotEmpty()) {
            paint.color = TEXT_MAIN
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 9.5f
            canvas.drawText("📲 UPI ID: ${shop.upiId}", 48f, leftLineY, paint)
            leftLineY += 15f
        }

        paint.color = TEXT_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        canvas.drawText("Proprietor: ${shop.ownerName} (${shop.phone})", 48f, leftLineY, paint)
        leftLineY += 13f
        canvas.drawText("Terms: Goods once sold will not be returned without bill.", 48f, leftLineY, paint)

        // 12. Footer (Decorative Golden Bar & Thank You Message)
        val footerTop = pageHeight - 65f
        paint.color = GOLD_BORDER
        paint.strokeWidth = 1f
        canvas.drawLine(36f, footerTop, rightEdge, footerTop, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.color = GOLD_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10.5f
        val thankMsg = shop.thankYouMessage.ifEmpty { "Thank you for shopping with Puja Dry Fruits! Visit Again." }
        canvas.drawText("🙏 $thankMsg", pageWidth / 2f, pageHeight - 44f, paint)

        paint.color = TEXT_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Puja Dry Fruits • Premium Quality Nuts, Berries & Spices • Computer Generated Tax Invoice", pageWidth / 2f, pageHeight - 28f, paint)

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

