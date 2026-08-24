package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateFilterRange(val label: String, val hindiLabel: String) {
    TODAY("Today", "आज"),
    YESTERDAY("Yesterday", "कल"),
    THIS_WEEK("This Week", "इस सप्ताह"),
    THIS_MONTH("This Month", "इस महीने"),
    THIS_YEAR("This Year", "इस वर्ष"),
    CUSTOM("Custom", "कस्टम")
}

data class DateRange(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val displayLabel: String
)

object DateUtils {
    private val standardDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    private val dayMonthFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)
    private val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)

    fun formatDate(timestamp: Long): String = standardDateFormat.format(Date(timestamp))
    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))
    fun formatDateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))
    fun formatDayMonth(timestamp: Long): String = dayMonthFormat.format(Date(timestamp))
    fun formatMonthYear(timestamp: Long): String = monthYearFormat.format(Date(timestamp))

    fun getDateRange(filter: DateFilterRange, customStart: Long? = null, customEnd: Long? = null): DateRange {
        val calendar = Calendar.getInstance()
        
        when (filter) {
            DateFilterRange.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                return DateRange(start, end, "Today (${formatDate(start)})")
            }
            DateFilterRange.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                return DateRange(start, end, "Yesterday (${formatDate(start)})")
            }
            DateFilterRange.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_WEEK, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                return DateRange(start, end, "This Week (${formatDayMonth(start)} - ${formatDayMonth(end)})")
            }
            DateFilterRange.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                return DateRange(start, end, "This Month (${formatMonthYear(start)})")
            }
            DateFilterRange.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                return DateRange(start, end, "Year ${calendar.get(Calendar.YEAR)}")
            }
            DateFilterRange.CUSTOM -> {
                val start = customStart ?: (System.currentTimeMillis() - 86400000L * 30)
                val end = customEnd ?: System.currentTimeMillis()
                return DateRange(start, end, "${formatDayMonth(start)} - ${formatDayMonth(end)}")
            }
        }
    }
}
