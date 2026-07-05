package com.niraj.voicereminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.niraj.voicereminder.data.AlarmScheduler
import com.niraj.voicereminder.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnoozeReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_DISMISS        = "extra_dismiss"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1)
        if (reminderId == -1) return

        val dismiss      = intent.getBooleanExtra(EXTRA_DISMISS, false)
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 15)

        CoroutineScope(Dispatchers.IO).launch {
            val repo     = ReminderRepository(context)
            val reminder = repo.getReminderById(reminderId) ?: return@launch

            if (dismiss) {
                AlarmScheduler.cancel(context, reminderId)
                repo.dismiss(reminderId)
            } else {
                val until = System.currentTimeMillis() + snoozeMinutes * 60_000L
                repo.snooze(reminderId, until)
                val snoozed = reminder.copy(triggerAtMillis = until, isSnoozed = true)
                AlarmScheduler.schedule(context, snoozed)
            }
        }
    }
}
