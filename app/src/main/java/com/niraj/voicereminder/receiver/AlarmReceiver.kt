package com.niraj.voicereminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.niraj.voicereminder.data.AlarmScheduler
import com.niraj.voicereminder.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID,    intent.getIntExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1))
            putExtra(AlarmScheduler.EXTRA_REMINDER_TITLE, intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_TITLE))
            putExtra(AlarmScheduler.EXTRA_REMINDER_DESC,  intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_DESC))
            putExtra(AlarmScheduler.EXTRA_RINGTONE_URI,   intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI))
            putExtra(AlarmScheduler.EXTRA_VIBRATE,        intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
