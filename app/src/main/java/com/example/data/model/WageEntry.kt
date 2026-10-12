package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wage_entries",
    indices = [Index(value = ["date"], unique = true)]
)
data class WageEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // Format: yyyy-MM-dd
    val worked: Boolean,
    val wageAmount: Int, // Wage earned that day (0 if worked = false)
    val wagePaid: Boolean, // Defaults to false, true when marked paid
    val extraGiven: Boolean, // Extra ₹100 paid same-day (not delayed)
    val note: String? = null
)
