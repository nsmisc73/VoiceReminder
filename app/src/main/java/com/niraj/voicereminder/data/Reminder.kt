package com.niraj.voicereminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String = "",
    val triggerAtMillis: Long,
    val isActive: Boolean = true,
    val isSnoozed: Boolean = false,
    val snoozeUntilMillis: Long = 0L,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val category: String = "General",
    val createdAt: Long = System.currentTimeMillis()
)
