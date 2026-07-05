package com.niraj.voicereminder.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.niraj.voicereminder.receiver.AlarmReceiver

object AlarmScheduler {

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    const val EXTRA_REMINDER_DESC = "extra_reminder_desc"
    const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"
    const val EXTRA_VIBRATE = "extra_vibrate"

    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }

        val pendingIntent = buildPendingIntent(context, reminder)

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(reminder.triggerAtMillis, pendingIntent),
            pendingIntent
        )
    }

    fun cancel(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun buildPendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_DESC, reminder.description)
            putExtra(EXTRA_RINGTONE_URI, reminder.ringtoneUri)
            putExtra(EXTRA_VIBRATE, reminder.vibrate)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
