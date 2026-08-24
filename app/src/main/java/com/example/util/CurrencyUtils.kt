package com.example.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    /**
     * Formats amount to Indian Currency format: ₹1,25,000.00 or ₹1,25,000
     */
    fun formatInr(amount: Double, includeDecimals: Boolean = true): String {
        return try {
            val isNegative = amount < 0
            val absAmount = Math.abs(amount)
            
            val intPart = absAmount.toLong()
            val decimalPart = ((absAmount - intPart) * 100).toInt()

            val intStr = intPart.toString()
            val formattedInt = if (intStr.length > 3) {
                val lastThree = intStr.substring(intStr.length - 3)
                val remaining = intStr.substring(0, intStr.length - 3)
                val sb = StringBuilder()
                var count = 0
                for (i in remaining.length - 1 downTo 0) {
                    sb.append(remaining[i])
                    count++
                    if (count % 2 == 0 && i > 0) {
                        sb.append(',')
                    }
                }
                sb.reverse().toString() + "," + lastThree
            } else {
                intStr
            }

            val result = if (includeDecimals && decimalPart > 0) {
                String.format(Locale.ENGLISH, "₹%s.%02d", formattedInt, decimalPart)
            } else if (includeDecimals) {
                String.format(Locale.ENGLISH, "₹%s.00", formattedInt)
            } else {
                "₹$formattedInt"
            }

            if (isNegative) "-$result" else result
        } catch (e: Exception) {
            "₹" + String.format(Locale.ENGLISH, "%.2f", amount)
        }
    }

    /**
     * Formats quantity with appropriate unit abbreviation
     */
    fun formatQuantity(quantity: Double, unit: String): String {
        val qtyFormatted = if (quantity % 1.0 == 0.0) {
            quantity.toInt().toString()
        } else {
            String.format(Locale.ENGLISH, "%.2f", quantity)
        }

        val unitShort = when (unit.lowercase(Locale.ENGLISH)) {
            "kilogram", "kg" -> "kg"
            "gram", "gm", "g" -> "g"
            "piece", "pcs" -> "pcs"
            "packet", "pkt" -> "pkt"
            "box", "boxes" -> "box"
            else -> unit
        }
        return "$qtyFormatted $unitShort"
    }

    fun formatPercent(percent: Double): String {
        return String.format(Locale.ENGLISH, "%.1f%%", percent)
    }
}
