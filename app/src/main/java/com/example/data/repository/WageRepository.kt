package com.example.data.repository

import com.example.data.local.WageDao
import com.example.data.model.WageEntry
import kotlinx.coroutines.flow.Flow

class WageRepository(private val wageDao: WageDao) {
    val allEntries: Flow<List<WageEntry>> = wageDao.getAllEntries()
    val unpaidEntries: Flow<List<WageEntry>> = wageDao.getUnpaidEntriesFlow()

    fun getEntriesInDateRange(startDate: String, endDate: String): Flow<List<WageEntry>> {
        return wageDao.getEntriesInDateRange(startDate, endDate)
    }

    fun getEntriesForMonth(yearMonth: String): Flow<List<WageEntry>> {
        return wageDao.getEntriesForMonthFlow(yearMonth)
    }

    fun getEntryByDateFlow(date: String): Flow<WageEntry?> {
        return wageDao.getEntryByDateFlow(date)
    }

    suspend fun getEntryById(id: Long): WageEntry? {
        return wageDao.getEntryById(id)
    }

    suspend fun getEntryByDate(date: String): WageEntry? {
        return wageDao.getEntryByDate(date)
    }

    suspend fun insertEntry(entry: WageEntry): Long {
        return wageDao.insertEntry(entry)
    }

    suspend fun updateEntry(entry: WageEntry) {
        wageDao.updateEntry(entry)
    }

    suspend fun deleteEntry(entry: WageEntry) {
        wageDao.deleteEntry(entry)
    }
}
