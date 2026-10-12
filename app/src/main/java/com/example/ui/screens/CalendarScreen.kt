package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WageEntry
import com.example.ui.theme.*
import com.example.ui.viewmodel.WageViewModel
import com.example.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: WageViewModel,
    onNavigateToAddEdit: (Long, String?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allEntries by viewModel.allEntries.collectAsState()

    // Map entries by date for easy O(1) lookup
    val entriesMap = remember(allEntries) {
        allEntries.associateBy { it.date }
    }

    // Keep track of the selected date
    var selectedDate by remember { mutableStateOf(DateUtils.getYesterdayDateString()) }

    var editAmount by remember { mutableStateOf("") }
    var editWorked by remember { mutableStateOf(true) }
    var editPaid by remember { mutableStateOf(true) }
    var editExtraGiven by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf("") }

    LaunchedEffect(editWorked) {
        editPaid = editWorked
    }

    LaunchedEffect(selectedDate, entriesMap) {
        val entry = entriesMap[selectedDate]
        if (entry != null) {
            editAmount = if (entry.wageAmount > 0) entry.wageAmount.toString() else ""
            editWorked = entry.worked
            editPaid = entry.worked
            editExtraGiven = entry.extraGiven
            editNote = entry.note ?: ""
        } else {
            editAmount = ""
            val calendar = Calendar.getInstance().apply {
                val parts = selectedDate.split("-")
                if (parts.size == 3) {
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                    set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                }
            }
            val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            editWorked = !isSunday
            editPaid = !isSunday
            editExtraGiven = false
            editNote = ""
        }
    }

    // Keep track of the current month and year being viewed in the calendar
    var currentYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0-indexed

    // Calendar helper calculations
    val calendarHelper = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val currentLocale = androidx.compose.ui.platform.LocalLocale.current.platformLocale

    val monthYearDisplay = remember(currentYear, currentMonth, currentLocale) {
        SimpleDateFormat("MMMM yyyy", currentLocale).format(calendarHelper.time)
    }

    // Weekdays list starting with Monday (matching app configuration)
    val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Previous Month navigation
    fun navigateToPreviousMonth() {
        if (currentMonth == 0) {
            currentMonth = 11
            currentYear -= 1
        } else {
            currentMonth -= 1
        }
    }

    // Next Month navigation
    fun navigateToNextMonth() {
        if (currentMonth == 11) {
            currentMonth = 0
            currentYear += 1
        } else {
            currentMonth += 1
        }
    }

    // Calculate dynamic stats for the selected month
    val monthPrefix = remember(currentYear, currentMonth) {
        String.format(Locale.US, "%04d-%02d-", currentYear, currentMonth + 1)
    }
    val monthEntries = remember(allEntries, monthPrefix) {
        allEntries.filter { it.date.startsWith(monthPrefix) }
    }
    val totalWages = remember(monthEntries) {
        monthEntries.sumOf { if (it.worked) it.wageAmount else 0 }
    }
    val totalPaid = remember(monthEntries) {
        monthEntries.filter { it.worked && it.wagePaid }.sumOf { it.wageAmount }
    }
    val totalPending = remember(monthEntries) {
        monthEntries.filter { it.worked && !it.wagePaid }.sumOf { it.wageAmount }
    }
    val daysWorked = remember(monthEntries) {
        monthEntries.count { it.worked }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NearBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Payment Calendar",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NearBlack,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // New Requirement Header: Total Earnings on top-left, Amount Paid on selected date on top-right (aligned to its left corner)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Top Left Corner - Total
                    Column {
                        Text(
                            text = "TOTAL EARNINGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹$totalWages",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            modifier = Modifier.testTag("calendar_total_earnings")
                        )
                    }

                    // Top Right (Right side) Container - left aligned within that right corner
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.widthIn(min = 160.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.Start // Start-aligned inside the right corner box
                        ) {
                            val selectedAmountPaid = if (editWorked && editPaid) {
                                editAmount.toIntOrNull() ?: 0
                            } else {
                                0
                            }
                            Text(
                                text = "PAID ON DATE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "₹$selectedAmountPaid",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldAccent,
                                modifier = Modifier.testTag("calendar_selected_paid_amount")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = DateUtils.formatDisplayDate(selectedDate),
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Month Navigation Row
            item {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navigateToPreviousMonth() },
                            modifier = Modifier.testTag("prev_month_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Month",
                                tint = TextPrimary
                            )
                        }

                        Text(
                            text = monthYearDisplay,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.testTag("month_year_title")
                        )

                        IconButton(
                            onClick = { navigateToNextMonth() },
                            modifier = Modifier.testTag("next_month_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Next Month",
                                tint = TextPrimary,
                                modifier = Modifier.rotate(180f)
                            )
                        }
                    }
                }
            }

            // Weekday Headers
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekdays.forEach { dayName ->
                        Text(
                            text = dayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Calendar Grid Items
            item {
                val firstDayOfWeek = calendarHelper.get(Calendar.DAY_OF_WEEK)
                val leadingDays = when (firstDayOfWeek) {
                    Calendar.SUNDAY -> 6
                    else -> firstDayOfWeek - Calendar.MONDAY
                }
                val daysInMonth = calendarHelper.getActualMaximum(Calendar.DAY_OF_MONTH)
                val totalCells = leadingDays + daysInMonth
                val weeksCount = (totalCells + 6) / 7

                val todayStr = DateUtils.getTodayDateString()

                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (w in 0 until weeksCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (d in 0..6) {
                                    val cellIndex = w * 7 + d
                                    if (cellIndex < leadingDays) {
                                        // Empty cell for leading days from previous month
                                        Box(modifier = Modifier.weight(1f).aspectRatio(0.9f))
                                    } else if (cellIndex < leadingDays + daysInMonth) {
                                        val day = cellIndex - leadingDays + 1
                                        val dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, day)
                                        val isToday = dateStr == todayStr
                                        val entry = entriesMap[dateStr]
                                        val isSelected = dateStr == selectedDate
                                        val isSunday = d == 6

                                        CalendarDayCell(
                                            day = day,
                                            entry = entry,
                                            isToday = isToday,
                                            isSelected = isSelected,
                                            isSunday = isSunday,
                                            onClick = {
                                                selectedDate = dateStr
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("day_cell_$day")
                                        )
                                    } else {
                                        // Empty cell for trailing days from next month
                                        Box(modifier = Modifier.weight(1f).aspectRatio(0.9f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Calendar Legend
            item {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = GoldAccent, label = "Paid", testTag = "legend_paid")
                        LegendItem(color = WarningText, label = "Pending", testTag = "legend_pending")
                        LegendItem(color = NegativeText, label = "Missed", testTag = "legend_missed")
                    }
                }
            }

            // Selected Date Details Card - lets user inline edit log
            item {
                val selectedEntry = entriesMap[selectedDate]
                val formattedSelectedDate = DateUtils.formatDisplayDate(selectedDate)

                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_date_details_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Title row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "SELECTED DATE DETAILS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val dateWithYear = DateUtils.formatDisplayDateWithYear(selectedDate)
                                val displayText = if (formattedSelectedDate == "Today" || formattedSelectedDate == "Yesterday") {
                                    "$formattedSelectedDate ($dateWithYear)"
                                } else {
                                    "$formattedSelectedDate ($dateWithYear)"
                                }
                                Text(
                                    text = displayText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = HairlineBorder)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Inline Option Row: Worked or Off
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { editWorked = true }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = editWorked,
                                    onClick = { editWorked = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldAccent)
                                )
                                Text(
                                    text = "Worked",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (editWorked) TextPrimary else TextMuted
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { editWorked = false }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = !editWorked,
                                    onClick = { editWorked = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldAccent)
                                )
                                Text(
                                    text = "Off / No Work",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (!editWorked) TextPrimary else TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (editWorked) {
                            // Wage Amount Input Field
                            OutlinedTextField(
                                value = editAmount,
                                onValueChange = { newValue ->
                                    // Only allow numbers
                                    if (newValue.all { it.isDigit() }) {
                                        editAmount = newValue
                                    }
                                },
                                label = { Text("Wage Amount (₹)") },
                                placeholder = { Text("e.g. 500") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldAccent,
                                    unfocusedBorderColor = HairlineBorder,
                                    focusedLabelColor = GoldAccent,
                                    unfocusedLabelColor = TextMuted,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = SurfaceCardElevated,
                                    unfocusedContainerColor = SurfaceCardElevated
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inline_wage_amount_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Extra ₹100 Received Row (checkbox/tick box)
                            Surface(
                                color = SurfaceCardElevated,
                                border = BorderStroke(1.dp, HairlineBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { editExtraGiven = !editExtraGiven }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = GoldAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Got extra ₹100?",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "Will not be added to total earnings",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    Checkbox(
                                        checked = editExtraGiven,
                                        onCheckedChange = { editExtraGiven = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = GoldAccent,
                                            uncheckedColor = TextMuted,
                                            checkmarkColor = Color(0xFF211705)
                                        ),
                                        modifier = Modifier.testTag("inline_extra_checkbox")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Optional Note Input Field
                        OutlinedTextField(
                            value = editNote,
                            onValueChange = { editNote = it },
                            label = { Text("Note (Optional)") },
                            placeholder = { Text("e.g. Half-day, late shift, etc.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = HairlineBorder,
                                focusedLabelColor = GoldAccent,
                                unfocusedLabelColor = TextMuted,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = SurfaceCardElevated,
                                unfocusedContainerColor = SurfaceCardElevated
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inline_note_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Save Button and feedback
                        var saveSuccess by remember { mutableStateOf(false) }
                        LaunchedEffect(selectedDate, editAmount, editWorked, editPaid, editExtraGiven, editNote) {
                            saveSuccess = false
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (saveSuccess) {
                                Text(
                                    text = "Saved successfully!",
                                    color = SuccessText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("save_success_feedback")
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            Button(
                                onClick = {
                                    val amt = editAmount.toIntOrNull() ?: 0
                                    val entryId = selectedEntry?.id ?: 0L
                                    val finalEntry = WageEntry(
                                        id = entryId,
                                        date = selectedDate,
                                        worked = editWorked,
                                        wageAmount = if (editWorked) amt else 0,
                                        wagePaid = if (editWorked) editPaid else false,
                                        extraGiven = if (editWorked) editExtraGiven else false,
                                        note = editNote.ifBlank { null }
                                    )
                                    viewModel.saveEntry(finalEntry) {
                                        saveSuccess = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldAccent,
                                    contentColor = Color(0xFF211705)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("save_selected_date_button")
                            ) {
                                Text(
                                    text = "Save",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Monthly Statistics Summary Card
            item {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_summary_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "${monthYearDisplay.uppercase()} SUMMARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.5.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Total Earnings", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    text = "₹$totalWages",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldAccent,
                                    modifier = Modifier.testTag("monthly_earnings_text")
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Days Worked", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    text = "$daysWorked days",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.testTag("monthly_days_worked_text")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = HairlineBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(GoldAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = "Paid", fontSize = 11.sp, color = TextMuted)
                                    Text(
                                        text = "₹$totalPaid",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldAccent,
                                        modifier = Modifier.testTag("monthly_paid_text")
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(WarningText)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "Pending", fontSize = 11.sp, color = TextMuted)
                                    Text(
                                        text = "₹$totalPending",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WarningText,
                                        modifier = Modifier.testTag("monthly_pending_text")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    day: Int,
    entry: WageEntry?,
    isToday: Boolean,
    isSelected: Boolean,
    isSunday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPaid = entry != null && entry.worked && entry.wagePaid
    val dayColor = when {
        entry != null && entry.worked && entry.wagePaid -> GoldAccent
        entry != null && entry.worked && !entry.wagePaid -> TextMuted
        entry != null && !entry.worked -> NegativeText
        isSunday && entry == null -> NegativeText
        else -> TextPrimary
    }

    Surface(
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = when {
            isSelected -> GoldAccent.copy(alpha = 0.15f)
            entry != null && entry.worked -> {
                if (entry.wagePaid) GoldAccent.copy(alpha = 0.2f) else WarningBg.copy(alpha = 0.3f)
            }
            entry != null && !entry.worked -> SurfaceCardElevated
            isSunday && entry == null -> SurfaceCardElevated
            else -> Color.Transparent
        },
        border = when {
            isSelected -> BorderStroke(2.dp, GoldAccent)
            isToday -> BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
            entry != null && entry.worked -> {
                BorderStroke(1.dp, if (entry.wagePaid) GoldAccent.copy(alpha = 0.4f) else WarningText.copy(alpha = 0.4f))
            }
            else -> BorderStroke(1.dp, Color.Transparent)
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp, bottom = 6.dp, start = 2.dp, end = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = day.toString(),
                fontSize = 14.sp,
                fontWeight = if (isPaid || isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = dayColor
            )

            if (entry != null) {
                if (entry.worked) {
                    Text(
                        text = "₹${entry.wageAmount}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.wagePaid) GoldAccent else WarningText, // wage amount is golden if paid!
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Off",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        maxLines = 1
                    )
                }

                // Small dot at the bottom representing status
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                entry.worked && entry.wagePaid -> GoldAccent
                                entry.worked && !entry.wagePaid -> WarningText
                                else -> NegativeText // Missed / Off
                            }
                        )
                )
            } else if (isSunday) {
                Text(
                    text = "Off",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextMuted,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(NegativeText)
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color
) {
    Surface(
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
