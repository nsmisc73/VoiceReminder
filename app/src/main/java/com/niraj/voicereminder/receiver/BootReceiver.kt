package com.niraj.voicereminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.niraj.voicereminder.data.AlarmScheduler
import com.niraj.voicereminder.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val repo = ReminderRepository(context)
            val now  = System.currentTimeMillis()
            repo.getActiveRemindersSync()
                .filter { it.triggerAtMillis > now }
                .forEach { AlarmScheduler.schedule(context, it) }
        }
    }
}
