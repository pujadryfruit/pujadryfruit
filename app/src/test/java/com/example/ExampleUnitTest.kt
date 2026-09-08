package com.example

import com.example.util.DateFilterRange
import com.example.util.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {
  @Test
  fun testFebruary2026NonLeapYear() {
    // 2026 is not a leap year -> Feb has 28 days
    val range = DateUtils.getMonthYearRange(2026, 1) // 1 = Feb
    assertEquals("February 2026 (01 Feb - 28 Feb)", range.displayLabel)
    assertEquals(28, DateUtils.getMonthDaysCount(2026, 1))
    assertFalse(DateUtils.isLeapYear(2026))

    val calStart = Calendar.getInstance().apply { timeInMillis = range.startTimestamp }
    assertEquals(2026, calStart.get(Calendar.YEAR))
    assertEquals(1, calStart.get(Calendar.MONTH))
    assertEquals(1, calStart.get(Calendar.DAY_OF_MONTH))

    val calEnd = Calendar.getInstance().apply { timeInMillis = range.endTimestamp }
    assertEquals(2026, calEnd.get(Calendar.YEAR))
    assertEquals(1, calEnd.get(Calendar.MONTH))
    assertEquals(28, calEnd.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun testFebruary2024LeapYear() {
    // 2024 is a leap year -> Feb has 29 days
    val range = DateUtils.getMonthYearRange(2024, 1)
    assertEquals("February 2024 (01 Feb - 29 Feb)", range.displayLabel)
    assertEquals(29, DateUtils.getMonthDaysCount(2024, 1))
    assertTrue(DateUtils.isLeapYear(2024))

    val calEnd = Calendar.getInstance().apply { timeInMillis = range.endTimestamp }
    assertEquals(2024, calEnd.get(Calendar.YEAR))
    assertEquals(1, calEnd.get(Calendar.MONTH))
    assertEquals(29, calEnd.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun testContinuousMonthLabelsAcrossYears() {
    // Jan 2026 .. Dec 2026, Jan 2027, Feb 2027
    assertEquals("Jan 2026", DateUtils.getMonthYearShortLabel(2026, 0))
    assertEquals("Feb 2026", DateUtils.getMonthYearShortLabel(2026, 1))
    assertEquals("Mar 2026", DateUtils.getMonthYearShortLabel(2026, 2))
    assertEquals("Dec 2026", DateUtils.getMonthYearShortLabel(2026, 11))
    assertEquals("Jan 2027", DateUtils.getMonthYearShortLabel(2027, 0))
    assertEquals("Feb 2027", DateUtils.getMonthYearShortLabel(2027, 1))

    val decRange = DateUtils.getMonthYearRange(2026, 11)
    assertEquals("December 2026 (01 Dec - 31 Dec)", decRange.displayLabel)

    val jan27Range = DateUtils.getMonthYearRange(2027, 0)
    assertEquals("January 2027 (01 Jan - 31 Jan)", jan27Range.displayLabel)
  }

  @Test
  fun testResolveDateRangeMonthYear() {
    val resolved = DateUtils.resolveDateRange(
      filter = DateFilterRange.SELECT_MONTH_YEAR,
      selectedYear = 2026,
      selectedMonth = 1
    )
    assertEquals("February 2026 (01 Feb - 28 Feb)", resolved.displayLabel)
    assertEquals(DateFilterRange.SELECT_MONTH_YEAR, resolved.filterType)
  }
}
