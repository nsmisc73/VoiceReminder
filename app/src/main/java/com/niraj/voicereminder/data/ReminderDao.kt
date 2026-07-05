package com.niraj.voicereminder.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY triggerAtMillis ASC")
    fun getActiveReminders(): LiveData<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY triggerAtMillis DESC")
    fun getAllReminders(): LiveData<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY triggerAtMillis ASC")
    suspend fun getActiveRemindersSync(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Int): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE reminders SET isActive = 0 WHERE id = :id")
    suspend fun dismissReminder(id: Int)

    @Query("UPDATE reminders SET isSnoozed = 1, snoozeUntilMillis = :until, triggerAtMillis = :until WHERE id = :id")
    suspend fun snoozeReminder(id: Int, until: Long)

    @Query("UPDATE reminders SET isSnoozed = 0, snoozeUntilMillis = 0 WHERE id = :id")
    suspend fun clearSnooze(id: Int)
}
