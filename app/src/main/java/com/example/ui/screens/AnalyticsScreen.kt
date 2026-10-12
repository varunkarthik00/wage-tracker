package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WageEntry
import com.example.ui.theme.*
import com.example.ui.viewmodel.WageViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: WageViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allEntries by viewModel.allEntries.collectAsState()
    val scrollState = rememberScrollState()

    // Generate selectable months (Current month and past 11 months, plus any old logged months)
    val selectableMonths = remember(allEntries) {
        val monthsSet = mutableSetOf<String>()
        val cal = Calendar.getInstance()
        // Add last 12 months to guarantee a robust selection list
        for (i in 0 until 12) {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            monthsSet.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        // Include any months from user entries
        allEntries.forEach { entry ->
            if (entry.date.length >= 7) {
                monthsSet.add(entry.date.substring(0, 7))
            }
        }
        monthsSet.toList().sortedDescending() // Newest month first
    }

    // Active selected month (format: "yyyy-MM")
    var selectedMonth by remember {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        mutableStateOf(sdf.format(Date()))
    }

    // Interactive selected day for daily detail view
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // Reset selected day when month changes
    LaunchedEffect(selectedMonth) {
        selectedDay = null
    }

    // Helper to format "yyyy-MM" to "MMM yy" (e.g., "Jul 26")
    val formatYearMonthAbbr = remember {
        { yearMonthStr: String ->
            try {
                val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
                val date = sdf.parse(yearMonthStr) ?: Date()
                val outSdf = SimpleDateFormat("MMM yy", Locale.getDefault())
                outSdf.format(date)
            } catch (e: Exception) {
                yearMonthStr
            }
        }
    }

    // Helper to format "yyyy-MM" to "MMMM yyyy" (e.g., "July 2026")
    val formatYearMonthFull = remember {
        { yearMonthStr: String ->
            try {
                val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
                val date = sdf.parse(yearMonthStr) ?: Date()
                val outSdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                outSdf.format(date)
            } catch (e: Exception) {
                yearMonthStr
            }
        }
    }

    // Generate the last 6 months list for the monthly trend graph
    val last6MonthsForTrend = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -5)
        for (i in 0..5) {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            list.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        list
    }

    // Monthly data for the trend graph (6 months)
    val trendMonthlyData = remember(allEntries, last6MonthsForTrend) {
        last6MonthsForTrend.map { monthStr ->
            val monthEntries = allEntries.filter { it.date.startsWith(monthStr) }
            val totalWages = monthEntries.filter { it.worked }.sumOf { it.wageAmount }
            val workedDays = monthEntries.count { it.worked }
            
            MonthTrendData(
                yearMonth = monthStr,
                totalWages = totalWages,
                workedDays = workedDays
            )
        }
    }

    // Entries for the active selected month
    val selectedMonthEntries = remember(allEntries, selectedMonth) {
        allEntries.filter { it.date.startsWith(selectedMonth) }
    }

    // Metrics for the active selected month
    val selectedMonthWages = remember(selectedMonthEntries) {
        selectedMonthEntries.filter { it.worked }.sumOf { it.wageAmount }
    }
    val selectedMonthExtra = remember(selectedMonthEntries) {
        selectedMonthEntries.count { it.worked && it.extraGiven } * 100
    }
    val selectedMonthWorkedDays = remember(selectedMonthEntries) {
        selectedMonthEntries.count { it.worked }
    }
    val selectedMonthAvgDaily = remember(selectedMonthWages, selectedMonthWorkedDays) {
        if (selectedMonthWorkedDays > 0) selectedMonthWages / selectedMonthWorkedDays else 0
    }

    // Days in selected month
    val daysInMonth = remember(selectedMonth) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            val date = sdf.parse(selectedMonth) ?: Date()
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        } catch (e: Exception) {
            30
        }
    }

    // Daily wage amounts for days 1..N of the selected month
    val dailyWagesList = remember(selectedMonthEntries, daysInMonth) {
        val map = selectedMonthEntries.associate { entry ->
            val day = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = sdf.parse(entry.date) ?: Date()
                val cal = Calendar.getInstance()
                cal.time = date
                cal.get(Calendar.DAY_OF_MONTH)
            } catch (e: Exception) {
                0
            }
            day to (if (entry.worked) entry.wageAmount else 0)
        }
        
        (1..daysInMonth).map { day ->
            day to (map[day] ?: 0)
        }
    }

    // Find if the selected day has an entry
    val selectedDayEntry = remember(selectedMonthEntries, selectedDay) {
        if (selectedDay != null) {
            selectedMonthEntries.find { entry ->
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val date = sdf.parse(entry.date) ?: Date()
                    val cal = Calendar.getInstance()
                    cal.time = date
                    cal.get(Calendar.DAY_OF_MONTH) == selectedDay
                } catch (e: Exception) {
                    false
                }
            }
        } else null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NearBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analytics & Insights",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("analytics_scroll_container"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // MONTH SELECTOR CARD
            Surface(
                color = SurfaceCard,
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentIndex = selectableMonths.indexOf(selectedMonth)
                    val hasPrev = currentIndex != -1 && currentIndex < selectableMonths.size - 1
                    val hasNext = currentIndex != -1 && currentIndex > 0

                    IconButton(
                        onClick = {
                            if (hasPrev) {
                                selectedMonth = selectableMonths[currentIndex + 1]
                            }
                        },
                        enabled = hasPrev,
                        modifier = Modifier.testTag("month_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Month",
                            tint = if (hasPrev) GoldAccent else TextMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Active Month",
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatYearMonthFull(selectedMonth),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            textAlign = TextAlign.Center
                        )
                    }

                    IconButton(
                        onClick = {
                            if (hasNext) {
                                selectedMonth = selectableMonths[currentIndex - 1]
                            }
                        },
                        enabled = hasNext,
                        modifier = Modifier.testTag("month_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Month",
                            tint = if (hasNext) GoldAccent else TextMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // DYNAMIC STATISTICS GRID FOR SELECTED MONTH
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Summary for ${formatYearMonthAbbr(selectedMonth)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Wages
                    Surface(
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("Total Wages", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹$selectedMonthWages", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldAccent)
                        }
                    }

                    // Days Worked
                    Surface(
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("Days Worked", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$selectedMonthWorkedDays Days", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Average Daily Wage
                    Surface(
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("Daily Average", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹$selectedMonthAvgDaily", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    // Early Extra
                    Surface(
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("Extra Earnings", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹$selectedMonthExtra", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SuccessText)
                        }
                    }
                }
            }

            // DAILY PAYMENTS GRAPH OF THAT PARTICULAR MONTH
            Surface(
                color = SurfaceCard,
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Payments",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Day-by-day earnings for ${formatYearMonthAbbr(selectedMonth)}",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val maxDailyWage = remember(dailyWagesList) {
                        val maxVal = dailyWagesList.maxOfOrNull { it.second } ?: 0
                        if (maxVal > 0) maxVal.toFloat() else 500f
                    }

                    // Horizontally scrollable bar chart so days are comfortably readable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .height(180.dp)
                            .testTag("daily_payments_chart"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dailyWagesList.forEach { (day, wage) ->
                            val isSelected = selectedDay == day
                            val barHeightRatio = wage.toFloat() / maxDailyWage
                            val animatedHeightRatio by animateFloatAsState(
                                targetValue = barHeightRatio,
                                animationSpec = tween(600),
                                label = "daily_bar_height"
                            )

                            Column(
                                modifier = Modifier
                                    .width(36.dp)
                                    .fillMaxHeight()
                                    .clickable { selectedDay = if (isSelected) null else day }
                                    .padding(horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (wage > 0) {
                                    Text(
                                        text = "₹$wage",
                                        fontSize = 9.sp,
                                        color = if (isSelected) GoldAccent else TextMuted,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .fillMaxHeight(fraction = animatedHeightRatio.coerceIn(0.04f, 1f))
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = if (isSelected) {
                                                    listOf(GoldAccent, GoldAccent.copy(alpha = 0.4f))
                                                } else if (wage > 0) {
                                                    listOf(Color(0xFFE2B762), Color(0xFF4A3816))
                                                } else {
                                                    listOf(Color(0xFF222222), Color(0xFF111111))
                                                }
                                            )
                                        )
                                        .testTag("daily_bar_$day")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = day.toString().padStart(2, '0'),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GoldAccent else TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // MONTHLY TREND GRAPH (6 MONTHS COMPARISON)
            Surface(
                color = SurfaceCard,
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Payments",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Earnings trend of the last 6 months",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val maxWageTrend = remember(trendMonthlyData) {
                        val maxVal = trendMonthlyData.maxOfOrNull { it.totalWages } ?: 0
                        if (maxVal > 0) maxVal.toFloat() else 1000f
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        trendMonthlyData.forEach { data ->
                            val isSelected = selectedMonth == data.yearMonth
                            val barHeightRatio = data.totalWages.toFloat() / maxWageTrend
                            val animatedHeightRatio by animateFloatAsState(
                                targetValue = barHeightRatio,
                                animationSpec = tween(600),
                                label = "trend_bar_height"
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { selectedMonth = data.yearMonth }
                                    .padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (data.totalWages > 0) {
                                    Text(
                                        text = "₹${data.totalWages}",
                                        fontSize = 10.sp,
                                        color = if (isSelected) GoldAccent else TextMuted,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .fillMaxHeight(fraction = animatedHeightRatio.coerceIn(0.05f, 1f))
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = if (isSelected) {
                                                    listOf(GoldAccent, GoldAccent.copy(alpha = 0.5f))
                                                } else {
                                                    listOf(Color(0xFF554425), Color(0xFF221A0A))
                                                }
                                            )
                                        )
                                        .testTag("trend_bar_${data.yearMonth}")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = formatYearMonthAbbr(data.yearMonth),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GoldAccent else TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // INTERACTIVE DAY DETAILS CARD
            AnimatedVisibility(visible = selectedDay != null) {
                selectedDay?.let { day ->
                    val entry = selectedDayEntry
                    Surface(
                        color = SurfaceCardElevated,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Details: Day %02d".format(day),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldAccent
                                )
                                Text(
                                    text = formatYearMonthAbbr(selectedMonth),
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            HorizontalDivider(color = HairlineBorder, thickness = 1.dp)

                            if (entry != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Status", fontSize = 11.sp, color = TextMuted)
                                        Text(
                                            text = if (entry.worked) "Worked" else "Off Day",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (entry.worked) SuccessText else TextMuted
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Daily Wage", fontSize = 11.sp, color = TextMuted)
                                        Text(
                                            text = "₹${entry.wageAmount}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Wage Paid", fontSize = 11.sp, color = TextMuted)
                                        Text(
                                            text = if (entry.wagePaid) "Yes" else "No",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (entry.wagePaid) SuccessText else WarningText
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Extra ₹100", fontSize = 11.sp, color = TextMuted)
                                        Text(
                                            text = if (entry.extraGiven) "Given" else "None",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (entry.extraGiven) SuccessText else TextMuted
                                        )
                                    }
                                }

                                if (!entry.note.isNullOrBlank()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("Daily Note", fontSize = 11.sp, color = TextMuted)
                                        Text(
                                            text = entry.note,
                                            fontSize = 13.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "No work entry logged for this day.",
                                        fontSize = 13.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

data class MonthTrendData(
    val yearMonth: String,
    val totalWages: Int,
    val workedDays: Int
)
