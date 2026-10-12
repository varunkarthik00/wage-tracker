package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.WageEntry
import com.example.data.repository.WageRepository
import com.example.util.DateUtils
import com.example.widget.WageWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WageRepository
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = WageRepository(database.wageDao())
    }

    // Filter type: "This week", "This month", "Custom"
    private val _selectedFilter = MutableStateFlow("This week")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    // Custom date range: Pair(startDate, endDate) formatted as yyyy-MM-dd
    private val _customDateRange = MutableStateFlow<Pair<String, String>?>(null)
    val customDateRange: StateFlow<Pair<String, String>?> = _customDateRange.asStateFlow()

    // Today's entry
    val todayEntry: StateFlow<WageEntry?> = repository.getEntryByDateFlow(DateUtils.getTodayDateString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Cached previous wage placeholder
    private val _previousWagePlaceholder = MutableStateFlow(400)
    val previousWagePlaceholder: StateFlow<Int> = _previousWagePlaceholder.asStateFlow()

    init {
        // Find previous day's wage to use as placeholder
        viewModelScope.launch(Dispatchers.IO) {
            repository.allEntries.collect { entries ->
                val prev = entries.firstOrNull { it.worked && it.date != DateUtils.getTodayDateString() }
                if (prev != null) {
                    _previousWagePlaceholder.value = prev.wageAmount
                }
            }
        }
    }

    // Monthly stats
    private val currentMonthStr = DateUtils.getYearMonthString()
    val monthlyStats: StateFlow<MonthlyStats> = repository.getEntriesForMonth(currentMonthStr)
        .map { entries ->
            val totalWage = entries.sumOf { if (it.worked) it.wageAmount else 0 }
            val daysWorked = entries.count { it.worked }
            val daysElapsed = DateUtils.getCurrentDayOfMonth()
            MonthlyStats(totalWage, daysWorked, daysElapsed)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyStats(0, 0, DateUtils.getCurrentDayOfMonth()))

    // Unpaid/Pending entries
    val unpaidEntries: StateFlow<List<WageEntry>> = repository.unpaidEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered entries for the recent list
    val filteredEntries: StateFlow<List<WageEntry>> = combine(
        _selectedFilter,
        _customDateRange,
        repository.allEntries
    ) { filter, customRange, allEntries ->
        when (filter) {
            "This week" -> {
                val (start, end) = DateUtils.getStartAndEndOfWeek()
                allEntries.filter { it.date in start..end }
            }
            "This month" -> {
                val (start, end) = DateUtils.getStartAndEndOfMonth()
                allEntries.filter { it.date in start..end }
            }
            "Custom" -> {
                if (customRange != null) {
                    allEntries.filter { it.date in customRange.first..customRange.second }
                } else {
                    allEntries
                }
            }
            else -> allEntries
        }
    }
    .flowOn(Dispatchers.IO)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All entries exposed for calendar
    val allEntries: StateFlow<List<WageEntry>> = repository.allEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cache for Undo deletion
    private var recentlyDeletedEntry: WageEntry? = null

    // Set filter
    fun setFilter(filter: String) {
        _selectedFilter.value = filter
        if (filter != "Custom") {
            _customDateRange.value = null
        }
    }

    // Set custom range
    fun setCustomDateRange(start: String, end: String) {
        _customDateRange.value = Pair(start, end)
        _selectedFilter.value = "Custom"
    }

    // Toggle today's worked status
    fun toggleTodayWorked(worked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val todayStr = DateUtils.getTodayDateString()
            val existing = repository.getEntryByDate(todayStr)
            
            if (existing != null) {
                val updated = existing.copy(
                    worked = worked,
                    // If turned off, wageAmount must be 0
                    wageAmount = if (worked) {
                        if (existing.wageAmount > 0) existing.wageAmount else _previousWagePlaceholder.value
                    } else 0,
                    // If worked is true, default to unpaid; if false, default to false
                    wagePaid = if (worked) false else existing.wagePaid
                )
                repository.insertEntry(updated)
            } else {
                val newEntry = WageEntry(
                    date = todayStr,
                    worked = worked,
                    wageAmount = if (worked) _previousWagePlaceholder.value else 0,
                    wagePaid = false,
                    extraGiven = false
                )
                repository.insertEntry(newEntry)
            }
            updateWidget()
        }
    }

    // Update today's wage amount
    fun updateTodayWage(amount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val todayStr = DateUtils.getTodayDateString()
            val existing = repository.getEntryByDate(todayStr)
            
            if (existing != null) {
                val updated = existing.copy(
                    wageAmount = amount,
                    worked = true // If they set a wage amount, they worked
                )
                repository.insertEntry(updated)
            } else {
                val newEntry = WageEntry(
                    date = todayStr,
                    worked = true,
                    wageAmount = amount,
                    wagePaid = false,
                    extraGiven = false
                )
                repository.insertEntry(newEntry)
            }
            updateWidget()
        }
    }

    // Toggle today's extra ₹100
    fun toggleTodayExtra(extra: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val todayStr = DateUtils.getTodayDateString()
            val existing = repository.getEntryByDate(todayStr)
            
            if (existing != null) {
                val updated = existing.copy(extraGiven = extra)
                repository.insertEntry(updated)
            } else {
                val newEntry = WageEntry(
                    date = todayStr,
                    worked = false,
                    wageAmount = 0,
                    wagePaid = false,
                    extraGiven = extra
                )
                repository.insertEntry(newEntry)
            }
            updateWidget()
        }
    }

    // Mark specific entry as paid
    fun markEntryPaid(id: Long, paid: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getEntryById(id)
            if (existing != null) {
                val updated = existing.copy(wagePaid = paid)
                repository.insertEntry(updated)
            }
            updateWidget()
        }
    }

    // Mark all pending entries as paid
    fun markAllPendingPaid() {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = repository.unpaidEntries.first()
            for (entry in pending) {
                val updated = entry.copy(wagePaid = true)
                repository.insertEntry(updated)
            }
            updateWidget()
        }
    }

    // Save/Update any entry (Add/Edit Screen)
    fun saveEntry(entry: WageEntry, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertEntry(entry)
            updateWidget()
            launch(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // Delete an entry with undo cache
    fun deleteEntry(entry: WageEntry, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            recentlyDeletedEntry = entry
            repository.deleteEntry(entry)
            updateWidget()
            launch(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // Undo delete action
    fun undoDelete(onComplete: (WageEntry) -> Unit) {
        val deleted = recentlyDeletedEntry ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertEntry(deleted)
            recentlyDeletedEntry = null
            updateWidget()
            launch(Dispatchers.Main) {
                onComplete(deleted)
            }
        }
    }

    // Get entry by ID for edit pre-fill
    suspend fun getEntryById(id: Long): WageEntry? {
        return repository.getEntryById(id)
    }

    // Get entry by Date to check duplicates
    suspend fun getEntryByDate(date: String): WageEntry? {
        return repository.getEntryByDate(date)
    }

    // Update the desktop widget
    private fun updateWidget() {
        WageWidgetProvider.sendUpdateBroadcast(getApplication())
    }

    // Export database to JSON string and open share sheet
    fun exportData(context: Context, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = repository.allEntries.first()
                val jsonArray = JSONArray()
                for (entry in entries) {
                    val jsonObj = JSONObject().apply {
                        put("id", entry.id)
                        put("date", entry.date)
                        put("worked", entry.worked)
                        put("wageAmount", entry.wageAmount)
                        put("wagePaid", entry.wagePaid)
                        put("extraGiven", entry.extraGiven)
                        put("note", entry.note ?: "")
                    }
                    jsonArray.put(jsonObj)
                }
                
                val jsonStr = jsonArray.toString(4)
                val fileName = "wage_tracker_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                val cacheFile = File(context.cacheDir, fileName)
                cacheFile.writeText(jsonStr)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(intent, "Share Wage Tracker Backup")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                
                launch(Dispatchers.Main) {
                    onSuccess("Backup created and ready to share!")
                }
            } catch (e: Exception) {
                android.util.Log.e("WageViewModel", "Failed to export data", e)
                launch(Dispatchers.Main) {
                    onError("Failed to export data: ${e.message}")
                }
            }
        }
    }

    // Import and merge entries from selected JSON file Uri
    fun importData(context: Context, uri: Uri, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonStr = inputStream?.bufferedReader()?.use { it.readText() }
                
                if (jsonStr.isNullOrEmpty()) {
                    launch(Dispatchers.Main) {
                        onError("Selected backup file is empty or invalid.")
                    }
                    return@launch
                }

                val jsonArray = JSONArray(jsonStr)
                var importCount = 0
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.getJSONObject(i)
                    val date = jsonObj.getString("date")
                    val worked = jsonObj.getBoolean("worked")
                    val wageAmount = jsonObj.getInt("wageAmount")
                    val wagePaid = jsonObj.getBoolean("wagePaid")
                    val extraGiven = jsonObj.getBoolean("extraGiven")
                    val note = jsonObj.optString("note", "")

                    // OnConflictStrategy.REPLACE handles duplicate dates automatically via unique index
                    val entry = WageEntry(
                        date = date,
                        worked = worked,
                        wageAmount = wageAmount,
                        wagePaid = wagePaid,
                        extraGiven = extraGiven,
                        note = if (note.isEmpty()) null else note
                    )
                    repository.insertEntry(entry)
                    importCount++
                }

                updateWidget()
                launch(Dispatchers.Main) {
                    onSuccess("Successfully imported and merged $importCount entries!")
                }
            } catch (e: Exception) {
                android.util.Log.e("WageViewModel", "Failed to import data", e)
                launch(Dispatchers.Main) {
                    onError("Failed to import data: ${e.message}")
                }
            }
        }
    }
}

data class MonthlyStats(
    val totalWage: Int,
    val daysWorked: Int,
    val daysElapsed: Int
)
