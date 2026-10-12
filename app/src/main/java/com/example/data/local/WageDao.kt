package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.WageEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WageDao {
    @Query("SELECT * FROM wage_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<WageEntry>>

    @Query("SELECT * FROM wage_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getEntriesInDateRange(startDate: String, endDate: String): Flow<List<WageEntry>>

    @Query("SELECT * FROM wage_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): WageEntry?

    @Query("SELECT * FROM wage_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryByDate(date: String): WageEntry?

    @Query("SELECT * FROM wage_entries WHERE date = :date LIMIT 1")
    fun getEntryByDateFlow(date: String): Flow<WageEntry?>

    @Query("SELECT * FROM wage_entries WHERE worked = 1 AND wagePaid = 0 ORDER BY date ASC")
    fun getUnpaidEntriesFlow(): Flow<List<WageEntry>>

    @Query("SELECT * FROM wage_entries WHERE date LIKE :yearMonth || '%'")
    fun getEntriesForMonthFlow(yearMonth: String): Flow<List<WageEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WageEntry): Long

    @Update
    suspend fun updateEntry(entry: WageEntry)

    @Delete
    suspend fun deleteEntry(entry: WageEntry)
}
