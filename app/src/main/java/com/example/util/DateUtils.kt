package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getTodayDateString(): String {
        return dbDateFormat.format(Date())
    }

    fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return dbDateFormat.format(cal.time)
    }

    fun getYearMonthString(): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }

    fun getCurrentDayOfMonth(): Int {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    }

    fun getStartAndEndOfWeek(): Pair<String, String> {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysToSubtract = when (dayOfWeek) {
            Calendar.SUNDAY -> 6
            else -> dayOfWeek - Calendar.MONDAY
        }
        cal.add(Calendar.DAY_OF_YEAR, -daysToSubtract)
        val start = dbDateFormat.format(cal.time)
        
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val end = dbDateFormat.format(cal.time)
        
        return Pair(start, end)
    }

    fun getStartAndEndOfMonth(): Pair<String, String> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = dbDateFormat.format(cal.time)
        
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = dbDateFormat.format(cal.time)
        
        return Pair(start, end)
    }

    fun formatDisplayDate(dateStr: String): String {
        return try {
            val date = dbDateFormat.parse(dateStr) ?: return dateStr
            val todayStr = getTodayDateString()
            val yesterdayStr = getYesterdayDateString()

            when (dateStr) {
                todayStr -> "Today"
                yesterdayStr -> "Yesterday"
                else -> {
                    val outFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    outFormat.format(date)
                }
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatDisplayDateWithYear(dateStr: String): String {
        return try {
            val date = dbDateFormat.parse(dateStr) ?: return dateStr
            val outFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            outFormat.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatShortDateRange(startStr: String, endStr: String): String {
        return try {
            val start = dbDateFormat.parse(startStr) ?: return "$startStr - $endStr"
            val end = dbDateFormat.parse(endStr) ?: return "$startStr - $endStr"
            
            val outFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            "${outFormat.format(start)} - ${outFormat.format(end)}"
        } catch (e: Exception) {
            "$startStr - $endStr"
        }
    }

    fun parseDate(dateStr: String): Date? {
        return try {
            dbDateFormat.parse(dateStr)
        } catch (e: Exception) {
            null
        }
    }

    fun formatDate(date: Date): String {
        return dbDateFormat.format(date)
    }
}
