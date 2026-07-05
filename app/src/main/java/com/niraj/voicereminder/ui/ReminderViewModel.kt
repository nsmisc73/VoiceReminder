package com.niraj.voicereminder.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.niraj.voicereminder.data.AlarmScheduler
import com.niraj.voicereminder.data.Reminder
import com.niraj.voicereminder.data.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReminderRepository(application)

    val activeReminders: LiveData<List<Reminder>> = repository.activeReminders
    val allReminders: LiveData<List<Reminder>> = repository.allReminders

    fun addReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = repository.insert(reminder)
            val saved = reminder.copy(id = newId.toInt())
            AlarmScheduler.schedule(getApplication(), saved)
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.update(reminder)
            AlarmScheduler.cancel(getApplication(), reminder.id)
            if (reminder.isActive && !reminder.isSnoozed) {
                AlarmScheduler.schedule(getApplication(), reminder)
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.cancel(getApplication(), reminder.id)
            repository.delete(reminder)
        }
    }

    fun dismissReminder(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.cancel(getApplication(), id)
            repository.dismiss(id)
        }
    }

    fun snoozeReminder(context: Context, reminder: Reminder, snoozeMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val snoozeUntil = System.currentTimeMillis() + snoozeMinutes * 60_000L
            repository.snooze(reminder.id, snoozeUntil)
            val snoozed = reminder.copy(
                triggerAtMillis = snoozeUntil,
                isSnoozed = true,
                snoozeUntilMillis = snoozeUntil
            )
            AlarmScheduler.schedule(context, snoozed)
        }
    }

    fun getReminderById(id: Int, callback: (Reminder?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val reminder = repository.getReminderById(id)
            launch(Dispatchers.Main) { callback(reminder) }
        }
    }
}
