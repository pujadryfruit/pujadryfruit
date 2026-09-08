package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateFilterRange(val label: String, val hindiLabel: String) {
    TODAY("Today", "आज"),
    YESTERDAY("Yesterday", "कल"),
    LAST_7_DAYS("Last 7 Days", "पिछले 7 दिन"),
    SELECT_MONTH_YEAR("Select Month", "महीना चुनें"),
    THIS_YEAR("This Year", "इस वर्ष"),
    CUSTOM("Custom Date Range", "कस्टम तारीख"),
    THIS_WEEK("This Week", "इस सप्ताह"),
    SELECT_WEEK("Select Week", "सप्ताह चुनें");

    companion object {
        // Alias for compatibility
        val THIS_MONTH = SELECT_MONTH_YEAR
    }
}

data class DateRange(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val displayLabel: String,
    val filterType: DateFilterRange = DateFilterRange.TODAY
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

    fun formatWeekRangeLabel(start: Long, end: Long): String {
        val calStart = Calendar.getInstance().apply { timeInMillis = start }
        val calEnd = Calendar.getInstance().apply { timeInMillis = end }
        val startYear = calStart.get(Calendar.YEAR)
        val endYear = calEnd.get(Calendar.YEAR)
        val startMonth = calStart.get(Calendar.MONTH)
        val endMonth = calEnd.get(Calendar.MONTH)

        val dayMonthFmt = SimpleDateFormat("MMM d", Locale.ENGLISH)
        val dayMonthYearFmt = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

        return when {
            startYear != endYear -> {
                "${dayMonthYearFmt.format(Date(start))} – ${dayMonthYearFmt.format(Date(end))}"
            }
            startMonth != endMonth -> {
                "${dayMonthFmt.format(Date(start))} – ${dayMonthFmt.format(Date(end))}, $startYear"
            }
            else -> {
                "${dayMonthFmt.format(Date(start))} – ${dayMonthFmt.format(Date(end))}, $startYear"
            }
        }
    }

    fun getLast7DaysRange(referenceMillis: Long = System.currentTimeMillis()): DateRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val end = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -6)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        val label = "Last 7 Days (${formatDayMonth(start)} – ${formatDayMonth(end)})"
        return DateRange(start, end, label, DateFilterRange.LAST_7_DAYS)
    }

    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    fun getMonthDaysCount(year: Int, month: Int): Int {
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun getFullMonthName(month: Int): String {
        val fullMonthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        return fullMonthNames.getOrElse(month) { "Month" }
    }

    fun getShortMonthName(month: Int): String {
        val shortMonthNames = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        return shortMonthNames.getOrElse(month) { "Month" }
    }

    fun getMonthYearShortLabel(year: Int, month: Int): String {
        return "${getShortMonthName(month)} $year"
    }

    fun getMonthYearRange(year: Int, month: Int): DateRange {
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis

        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, maxDay)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        val fullMonthName = getFullMonthName(month)
        val shortMonthName = getShortMonthName(month)
        val endDayFormatted = String.format(Locale.ENGLISH, "%02d", maxDay)

        // For example: "February 2026 (01 Feb - 28 Feb)" or "February 2024 (01 Feb - 29 Feb)"
        val label = "$fullMonthName $year (01 $shortMonthName - $endDayFormatted $shortMonthName)"
        return DateRange(start, end, label, DateFilterRange.SELECT_MONTH_YEAR)
    }

    fun getWeekRangeForDate(anchorMillis: Long): DateRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anchorMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        val start = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        val label = formatWeekRangeLabel(start, end)
        return DateRange(start, end, label, DateFilterRange.SELECT_WEEK)
    }

    fun getWeekRangeFromTimestamps(startMillis: Long, endMillis: Long): DateRange {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = endMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val start = startCal.timeInMillis
        val end = endCal.timeInMillis
        val label = formatWeekRangeLabel(start, end)
        return DateRange(start, end, label, DateFilterRange.SELECT_WEEK)
    }

    fun resolveDateRange(
        filter: DateFilterRange,
        customStart: Long? = null,
        customEnd: Long? = null,
        selectedYear: Int? = null,
        selectedMonth: Int? = null,
        weekStart: Long? = null,
        weekEnd: Long? = null
    ): DateRange {
        return when (filter) {
            DateFilterRange.TODAY -> getDateRange(DateFilterRange.TODAY)
            DateFilterRange.YESTERDAY -> getDateRange(DateFilterRange.YESTERDAY)
            DateFilterRange.LAST_7_DAYS -> getLast7DaysRange()
            DateFilterRange.THIS_WEEK -> getDateRange(DateFilterRange.THIS_WEEK)
            DateFilterRange.SELECT_WEEK -> {
                if (weekStart != null && weekEnd != null) {
                    getWeekRangeFromTimestamps(weekStart, weekEnd)
                } else if (weekStart != null) {
                    getWeekRangeForDate(weekStart)
                } else {
                    val currentWeek = getDateRange(DateFilterRange.THIS_WEEK)
                    DateRange(
                        currentWeek.startTimestamp,
                        currentWeek.endTimestamp,
                        formatWeekRangeLabel(currentWeek.startTimestamp, currentWeek.endTimestamp),
                        DateFilterRange.SELECT_WEEK
                    )
                }
            }
            DateFilterRange.SELECT_MONTH_YEAR -> {
                val now = Calendar.getInstance()
                val year = selectedYear ?: now.get(Calendar.YEAR)
                val month = selectedMonth ?: now.get(Calendar.MONTH)
                getMonthYearRange(year, month)
            }
            DateFilterRange.THIS_YEAR -> getDateRange(DateFilterRange.THIS_YEAR)
            DateFilterRange.CUSTOM -> {
                val start = customStart ?: (System.currentTimeMillis() - 86400000L * 30)
                val end = customEnd ?: System.currentTimeMillis()
                getDateRange(DateFilterRange.CUSTOM, start, end)
            }
        }
    }

    fun getDateRange(filter: DateFilterRange, customStart: Long? = null, customEnd: Long? = null): DateRange {
        val calendar = Calendar.getInstance()
        
        return when (filter) {
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
                DateRange(start, end, "Today (${formatDate(start)})", DateFilterRange.TODAY)
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
                DateRange(start, end, "Yesterday (${formatDate(start)})", DateFilterRange.YESTERDAY)
            }
            DateFilterRange.LAST_7_DAYS -> getLast7DaysRange()
            DateFilterRange.THIS_WEEK -> {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                calendar.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                DateRange(start, end, "This Week (${formatDayMonth(start)} - ${formatDayMonth(end)})", DateFilterRange.THIS_WEEK)
            }
            DateFilterRange.SELECT_WEEK -> {
                if (customStart != null && customEnd != null) {
                    getWeekRangeFromTimestamps(customStart, customEnd)
                } else if (customStart != null) {
                    getWeekRangeForDate(customStart)
                } else {
                    val currentWeek = getDateRange(DateFilterRange.THIS_WEEK)
                    DateRange(
                        currentWeek.startTimestamp,
                        currentWeek.endTimestamp,
                        formatWeekRangeLabel(currentWeek.startTimestamp, currentWeek.endTimestamp),
                        DateFilterRange.SELECT_WEEK
                    )
                }
            }
            DateFilterRange.SELECT_MONTH_YEAR -> {
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                getMonthYearRange(year, month)
            }
            DateFilterRange.THIS_YEAR -> {
                val year = calendar.get(Calendar.YEAR)
                calendar.clear()
                calendar.set(Calendar.YEAR, year)
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
                DateRange(start, end, "This Year ($year)", DateFilterRange.THIS_YEAR)
            }
            DateFilterRange.CUSTOM -> {
                val start = customStart ?: (System.currentTimeMillis() - 86400000L * 30)
                val end = customEnd ?: System.currentTimeMillis()
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = start
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = end
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val s = startCal.timeInMillis
                val e = endCal.timeInMillis
                DateRange(s, e, "${formatDayMonth(s)} - ${formatDayMonth(e)}", DateFilterRange.CUSTOM)
            }
        }
    }
}
