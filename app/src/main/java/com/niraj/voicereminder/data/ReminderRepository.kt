package com.niraj.voicereminder.data

import android.content.Context
import androidx.lifecycle.LiveData

class ReminderRepository(context: Context) {

    private val dao: ReminderDao = ReminderDatabase.getDatabase(context).reminderDao()

    val activeReminders: LiveData<List<Reminder>> = dao.getActiveReminders()
    val allReminders: LiveData<List<Reminder>> = dao.getAllReminders()

    suspend fun insert(reminder: Reminder): Long = dao.insert(reminder)

    suspend fun update(reminder: Reminder) = dao.update(reminder)

    suspend fun delete(reminder: Reminder) = dao.delete(reminder)

    suspend fun deleteById(id: Int) = dao.deleteById(id)

    suspend fun getReminderById(id: Int): Reminder? = dao.getReminderById(id)

    suspend fun dismiss(id: Int) = dao.dismissReminder(id)

    suspend fun snooze(id: Int, untilMillis: Long) = dao.snoozeReminder(id, untilMillis)

    suspend fun clearSnooze(id: Int) = dao.clearSnooze(id)

    suspend fun getActiveRemindersSync(): List<Reminder> = dao.getActiveRemindersSync()
}
