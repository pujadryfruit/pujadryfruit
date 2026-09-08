package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import com.example.ui.theme.*
import com.example.util.CurrencyUtils
import com.example.util.DateFilterRange
import com.example.util.DateRange
import com.example.util.DateUtils
import java.util.Calendar

@Composable
fun MetricCard(
    title: String,
    hindiTitle: String = "",
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconBgColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTintColor: Color = MaterialTheme.colorScheme.primary,
    cardBgColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = contentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hindiTitle.isNotEmpty()) {
                        Text(
                            text = hindiTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.65f),
                            fontSize = 10.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearPickerDialog(
    initialYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    initialMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int) -> Unit
) {
    var selectedYear by remember { mutableStateOf(initialYear) }
    var selectedMonth by remember { mutableStateOf(initialMonth) }
    var viewMode by remember { mutableStateOf(0) } // 0: Month Grid, 1: Timeline List
    var showYearDropdown by remember { mutableStateOf(false) }

    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val fullMonthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    val maxDays = DateUtils.getMonthDaysCount(selectedYear, selectedMonth)
    val isLeap = DateUtils.isLeapYear(selectedYear)
    val shortMonthName = DateUtils.getShortMonthName(selectedMonth)
    val fullMonthName = DateUtils.getFullMonthName(selectedMonth)
    val endDayStr = String.format(java.util.Locale.ENGLISH, "%02d", maxDays)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Month",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // View mode switcher: Grid vs Timeline
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                    ) {
                        Surface(
                            onClick = { viewMode = 0 },
                            shape = RoundedCornerShape(6.dp),
                            color = if (viewMode == 0) GeometricPrimary else Color.Transparent,
                            contentColor = if (viewMode == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = "Grid",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            onClick = { viewMode = 1 },
                            shape = RoundedCornerShape(6.dp),
                            color = if (viewMode == 1) GeometricPrimary else Color.Transparent,
                            contentColor = if (viewMode == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = "Timeline",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Selected period preview banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = "Period: $fullMonthName $selectedYear (01 $shortMonthName - $endDayStr $shortMonthName)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$maxDays days in month${if (isLeap && selectedMonth == 1) " • Leap Year" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Year Navigator with steppers and dropdown menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedYear -= 1 },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year")
                    }

                    Box {
                        Surface(
                            onClick = { showYearDropdown = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$selectedYear",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Year",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showYearDropdown,
                            onDismissRequest = { showYearDropdown = false }
                        ) {
                            val yearsList = (2020..2032).toList()
                            yearsList.forEach { y ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "$y",
                                            fontWeight = if (y == selectedYear) FontWeight.Bold else FontWeight.Normal,
                                            color = if (y == selectedYear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedYear = y
                                        showYearDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { selectedYear += 1 },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Year")
                    }
                }

                // Quick horizontal Year Chips
                val quickYears = listOf(selectedYear - 2, selectedYear - 1, selectedYear, selectedYear + 1, selectedYear + 2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    quickYears.forEach { y ->
                        FilterChip(
                            selected = (y == selectedYear),
                            onClick = { selectedYear = y },
                            label = {
                                Text(
                                    text = "$y",
                                    fontSize = 11.sp,
                                    fontWeight = if (y == selectedYear) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (viewMode == 0) {
                    // Mode 0: 4 rows x 3 columns month grid
                    // Displays "Jan 2026", "Feb 2026", etc. with exact days and leap year calculation
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0..3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0..2) {
                                    val monthIdx = row * 3 + col
                                    val isSelected = (monthIdx == selectedMonth)
                                    val monthDays = DateUtils.getMonthDaysCount(selectedYear, monthIdx)
                                    val isFebLeap = (monthIdx == 1 && isLeap)

                                    Surface(
                                        onClick = { selectedMonth = monthIdx },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) GeometricPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "${monthNames[monthIdx]} $selectedYear",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = if (isFebLeap) "01-29 • Leap" else "01-${String.format(java.util.Locale.ENGLISH, "%02d", monthDays)}",
                                                fontSize = 9.sp,
                                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Mode 1: Timeline showing months across years (e.g. Jan 2026, Feb 2026 ... Dec 2026, Jan 2027, Feb 2027)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        val timelineMonths = remember(selectedYear) {
                            val list = mutableListOf<Pair<Int, Int>>()
                            for (m in 10..11) list.add(Pair(selectedYear - 1, m))
                            for (m in 0..11) list.add(Pair(selectedYear, m))
                            for (m in 0..5) list.add(Pair(selectedYear + 1, m))
                            list
                        }

                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(timelineMonths.size) { idx ->
                                val (y, m) = timelineMonths[idx]
                                val isSelected = (y == selectedYear && m == selectedMonth)
                                val mDays = DateUtils.getMonthDaysCount(y, m)
                                val mShort = monthNames[m]
                                val isLeapM = (m == 1 && DateUtils.isLeapYear(y))

                                Surface(
                                    onClick = {
                                        selectedYear = y
                                        selectedMonth = m
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) GeometricPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, GeometricPrimary) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "$mShort $y",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) GeometricPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "01 $mShort – ${String.format(java.util.Locale.ENGLISH, "%02d", mDays)} $mShort • $mDays days${if (isLeapM) " (Leap)" else ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = GeometricPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedYear, selectedMonth)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeometricPrimary)
            ) {
                Text("Apply Month")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPickerDialog(
    initialStartMillis: Long? = null,
    initialEndMillis: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit
) {
    var anchorMillis by remember {
        mutableStateOf(initialStartMillis ?: System.currentTimeMillis())
    }
    val currentWeekRange = remember(anchorMillis) {
        DateUtils.getWeekRangeForDate(anchorMillis)
    }

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = anchorMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            anchorMillis = selected
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Select Week",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = currentWeekRange.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Stepper: ◀ Prev Week | Next Week ▶
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = anchorMillis
                                add(Calendar.DAY_OF_YEAR, -7)
                            }
                            anchorMillis = cal.timeInMillis
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prev Week", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = anchorMillis
                                add(Calendar.DAY_OF_YEAR, 7)
                            }
                            anchorMillis = cal.timeInMillis
                        }
                    ) {
                        Text("Next Week", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )

                // Quick presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val now = System.currentTimeMillis()
                    val presets = listOf(
                        "This Week" to 0,
                        "Last Week" to -7,
                        "2 Weeks Ago" to -14,
                        "3 Weeks Ago" to -21
                    )
                    presets.forEach { (label, dayOffset) ->
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = now
                            add(Calendar.DAY_OF_YEAR, dayOffset)
                        }
                        val week = DateUtils.getWeekRangeForDate(cal.timeInMillis)
                        val isCurrent = (week.startTimestamp == currentWeekRange.startTimestamp)
                        OutlinedButton(
                            onClick = { anchorMillis = cal.timeInMillis },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isCurrent) ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                Text(week.displayLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Custom Date picker to jump to any date across any month/year
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Any Date to Select Its Week")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(currentWeekRange.startTimestamp, currentWeekRange.endTimestamp)
                    onDismiss()
                }
            ) {
                Text("Apply Week")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangeDialog(
    initialStartMillis: Long? = null,
    initialEndMillis: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis ?: start
                    if (start != null && end != null) {
                        onConfirm(start, end)
                    }
                    onDismiss()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select Custom Date Range",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            headline = {
                val start = dateRangePickerState.selectedStartDateMillis
                val end = dateRangePickerState.selectedEndDateMillis
                val text = if (start != null && end != null) {
                    "${DateUtils.formatDayMonth(start)} – ${DateUtils.formatDayMonth(end)}"
                } else if (start != null) {
                    "From ${DateUtils.formatDayMonth(start)}"
                } else {
                    "Choose start & end dates"
                }
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}

@Composable
fun DateFilterChips(
    selectedRange: DateFilterRange,
    activeDateRange: DateRange? = null,
    onRangeSelected: (DateFilterRange) -> Unit,
    onSelectMonthYear: ((year: Int, month: Int) -> Unit)? = null,
    onSelectWeek: ((start: Long, end: Long) -> Unit)? = null,
    onCustomRange: ((start: Long, end: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMonthYearPicker by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showCustomRangePicker by remember { mutableStateOf(false) }

    if (showMonthYearPicker) {
        val cal = Calendar.getInstance()
        if (activeDateRange != null) {
            cal.timeInMillis = activeDateRange.startTimestamp
        }
        MonthYearPickerDialog(
            initialYear = cal.get(Calendar.YEAR),
            initialMonth = cal.get(Calendar.MONTH),
            onDismiss = { showMonthYearPicker = false },
            onConfirm = { year, month ->
                onSelectMonthYear?.invoke(year, month)
                onRangeSelected(DateFilterRange.SELECT_MONTH_YEAR)
            }
        )
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            initialStartMillis = activeDateRange?.startTimestamp,
            initialEndMillis = activeDateRange?.endTimestamp,
            onDismiss = { showWeekPicker = false },
            onConfirm = { start, end ->
                onSelectWeek?.invoke(start, end)
                onRangeSelected(DateFilterRange.SELECT_WEEK)
            }
        )
    }

    if (showCustomRangePicker) {
        CustomDateRangeDialog(
            initialStartMillis = activeDateRange?.startTimestamp,
            initialEndMillis = activeDateRange?.endTimestamp,
            onDismiss = { showCustomRangePicker = false },
            onConfirm = { start, end ->
                onCustomRange?.invoke(start, end)
                onRangeSelected(DateFilterRange.CUSTOM)
            }
        )
    }

    // Visible filter ranges: Today, Yesterday, Last 7 Days, Select Month, This Year, Custom
    // "This Week" is removed, replaced by dynamic "Select Month" right after Last 7 Days
    val visibleFilterRanges = listOf(
        DateFilterRange.TODAY,
        DateFilterRange.YESTERDAY,
        DateFilterRange.LAST_7_DAYS,
        DateFilterRange.SELECT_MONTH_YEAR,
        DateFilterRange.THIS_YEAR,
        DateFilterRange.CUSTOM
    )

    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleFilterRanges.forEach { range ->
            val isSelected = range == selectedRange
            val chipText = when (range) {
                DateFilterRange.SELECT_MONTH_YEAR -> {
                    if (isSelected && activeDateRange != null && activeDateRange.filterType == DateFilterRange.SELECT_MONTH_YEAR) {
                        val cal = Calendar.getInstance().apply { timeInMillis = activeDateRange.startTimestamp }
                        val monthLabel = DateUtils.getMonthYearShortLabel(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                        "📅 $monthLabel ▼"
                    } else {
                        "📅 Select Month ▼"
                    }
                }
                DateFilterRange.CUSTOM -> {
                    if (isSelected && activeDateRange != null && activeDateRange.filterType == DateFilterRange.CUSTOM) {
                        "Custom: ${activeDateRange.displayLabel}"
                    } else {
                        range.label
                    }
                }
                else -> range.label
            }

            Surface(
                onClick = {
                    when (range) {
                        DateFilterRange.SELECT_MONTH_YEAR -> {
                            showMonthYearPicker = true
                        }
                        DateFilterRange.CUSTOM -> {
                            showCustomRangePicker = true
                        }
                        else -> {
                            onRangeSelected(range)
                        }
                    }
                },
                shape = RoundedCornerShape(100.dp),
                color = if (isSelected) GeometricPrimary else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shadowElevation = if (isSelected) 2.dp else 0.dp,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("filter_chip_${range.name.lowercase()}")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chipText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status.uppercase()) {
        "PAID" -> CardOrdersBg to CardOrdersText
        "PARTIAL" -> WarningOrangeLight to WarningOrange
        "UNPAID", "OVERDUE" -> CardExpensesBg to CardExpensesText
        "CANCELLED" -> Color(0xFFF1F5F9) to Color(0xFF475569)
        else -> CardProfitBg to CardProfitText
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = 11.sp
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    hindiSubtitle: String = "",
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (hindiSubtitle.isNotEmpty()) {
                Text(
                    text = hindiSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = GeometricPrimary
                )
            }
        }
    }
}

@Composable
fun CustomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String = "Search dry fruits, customers, invoices...",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_input_field"),
        placeholder = {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.outline
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(100.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GeometricPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GeometricPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GeometricPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionButtonText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GeometricPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionButtonText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
